package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroHeldTargets;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionS2CPacket;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server half of the Hulkbuster movement (rework spec §5). The client predicts its own impulses (dash
 * burst, jet launch, hover hold, dive push) with the pure rules in {@link HulkbusterMotion}; the server
 * checks every request with the same rules, keeps the state everyone renders, spends the energy, and
 * plays the landings, dive impacts, ram hits, steps and the coughing thrusters. A refused request snaps
 * the owner back (position) and tells the client to drop its prediction.
 */
public final class HulkbusterMovement {
   public static final int OP_DASH = 0;
   public static final int OP_JET_CHARGE = 1;
   public static final int OP_JET_RELEASE = 2;
   public static final int OP_HOVER = 3;
   public static final int OP_DIVE = 4;
   public static final int OP_BACK = 5;
   public static final int OP_STRUGGLE = 6;
   private static final UUID SPEED_ID = UUID.fromString("5f0b7a3e-1c55-4d8e-9a1b-7e2f3c4d5a61");
   private static final double STEP_LENGTH = 1.45;

   private HulkbusterMovement() {
   }

   /** Is a committed move holding the machine (no dash, no jet)? Cancel windows let the dash out. */
   static boolean busy(HulkbusterKit kit) {
      HulkMove move = kit.timeline.move();
      if (move == null) {
         return false;
      }

      MovePhase window = move.cancellableFrom();
      return window == null || !kit.timeline.phase().atLeast(window);
   }

   static boolean rooted(ServerPlayer player) {
      return HeroDamage.isAnchored(player) || HeroHeldTargets.holder(player) != null || !player.isAlive();
   }

   public static void handle(ServerPlayer player, @javax.annotation.Nullable IronManState state, int op, int arg) {
      if (op == OP_STRUGGLE) {
         HulkbusterCombat.struggle(player);
         return;
      }

      if (state == null) {
         return;
      }

      HulkbusterKit kit = state.hulkKit;
      HulkbusterMotion m = kit.motion;
      boolean active = state.hulkbuster.active();

      if (!active) {
         if (op != OP_BACK) {
            refuse(player, op);
         }

         return;
      }

      switch (op) {
         case OP_DASH -> {
            boolean energy = !state.energy.weaponsLocked() && state.energy.value() >= HulkbusterRules.DASH_COST;
            if (arg < 0 || arg > 3 || !HulkbusterMotion.dashAllowed(true, busy(kit), rooted(player), m.state, player.onGround(), m.dashCharges, m.airDashUsed, energy)) {
               refuse(player, op);
               return;
            }

            if (kit.timeline.running()) {
               // Inside a cancel window: the dash ends the recovery.
               kit.timeline.clear();
            }

            boolean air = m.state == HulkbusterMotion.State.HOVER;
            state.energy.spend(HulkbusterRules.DASH_COST);
            m.dashCharges--;
            m.direction = arg;
            m.airDashUsed |= air;
            m.set(HulkbusterMotion.State.DASH_WINDUP);
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.DASH);
         }
         case OP_JET_CHARGE -> {
            if (!player.onGround() || busy(kit) || rooted(player) || m.state == HulkbusterMotion.State.STAGGER || state.energy.weaponsLocked()
               || state.energy.value() < HulkbusterRules.JET_COST) {
               refuse(player, op);
               return;
            }

            m.jetCharge = 0;
            m.set(HulkbusterMotion.State.JET_CHARGE);
         }
         case OP_JET_RELEASE -> {
            if (m.state != HulkbusterMotion.State.JET_CHARGE || !state.energy.spend(HulkbusterRules.JET_COST)) {
               refuse(player, op);
               return;
            }

            m.jetCharge = Math.max(0, Math.min(HulkbusterRules.JET_CHARGE_MAX, arg));
            m.set(HulkbusterMotion.State.JET_RISE);
            m.peakY = player.getY();
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.JET_LAUNCH);
            HeroFx.launch(player, player.position(), 0.6F + 0.6F * m.jetCharge / HulkbusterRules.JET_CHARGE_MAX,
               player.level().getBlockState(player.blockPosition().below()));
         }
         case OP_HOVER -> {
            if (arg == 0) {
               if (m.state == HulkbusterMotion.State.HOVER) {
                  m.set(HulkbusterMotion.State.FALL);
               }

               return;
            }

            if (player.onGround() || m.hoverUsed >= HulkbusterRules.HOVER_MAX || state.energy.empty() || rooted(player)
               || m.state == HulkbusterMotion.State.DIVE || m.state == HulkbusterMotion.State.STAGGER) {
               refuse(player, op);
               if (state.energy.empty()) {
                  HulkbusterSounds.play(player, HulkbusterSounds.Cue.COUGH);
               }

               return;
            }

            m.set(HulkbusterMotion.State.HOVER);
         }
         case OP_DIVE -> {
            if (!HulkbusterMotion.diveAllowed(player.onGround(), heightAboveGround(player), m.state) || rooted(player)) {
               refuse(player, op);
               return;
            }

            m.diveStartY = Double.isNaN(m.peakY) ? player.getY() : Math.max(m.peakY, player.getY());
            m.set(HulkbusterMotion.State.DIVE);
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.JET_LAUNCH, player.position(), 0.8F);
         }
         case OP_BACK -> m.backHeld = arg != 0;
         default -> {
         }
      }
   }

   private static void refuse(ServerPlayer player, int op) {
      if (player.hasDisconnected()) {
         return;
      }

      if ((op == OP_DASH || op == OP_JET_RELEASE || op == OP_DIVE) && !player.isPassenger()) {
         player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
      }

      CoreMessages.sendToPlayer(new HulkbusterMotionS2CPacket(op), player);
   }

   public static double heightAboveGround(ServerPlayer player) {
      Vec3 from = player.position();
      BlockHitResult hit = player.level().clip(new ClipContext(from, from.add(0.0, -64.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
      return hit.getType() == HitResult.Type.MISS ? 64.0 : from.y - hit.getLocation().y;
   }

   // ---------------------------------------------------------------- tick

   public static void tick(ServerPlayer player, IronManState state, boolean controlled) {
      HulkbusterKit kit = state.hulkKit;
      HulkbusterMotion m = kit.motion;
      m.ticks++;
      if (controlled && m.state != HulkbusterMotion.State.NONE && m.state != HulkbusterMotion.State.FALL) {
         m.set(player.onGround() ? HulkbusterMotion.State.NONE : HulkbusterMotion.State.FALL);
      }

      if (!player.onGround()) {
         m.peakY = Double.isNaN(m.peakY) ? player.getY() : Math.max(m.peakY, player.getY());
         if (m.state == HulkbusterMotion.State.NONE || m.state == HulkbusterMotion.State.RAM || m.state == HulkbusterMotion.State.LANDING) {
            m.set(HulkbusterMotion.State.FALL);
         }
      } else if (!Double.isNaN(m.peakY)) {
         double fall = m.peakY - player.getY();
         m.peakY = Double.NaN;
         landed(player, state, fall);
      }

      switch (m.state) {
         case DASH_WINDUP -> {
            if (m.ticks >= HulkbusterRules.DASH_WINDUP) {
               m.set(HulkbusterMotion.State.DASH);
               HeroFx.airTrail(player, player);
            }
         }
         case DASH -> {
            if (m.ticks % 2 == 0 && player.onGround()) {
               HeroFx.launch(player, player.position(), 0.35F, player.level().getBlockState(player.blockPosition().below()));
               Vec3 ahead = player.position().add(HulkbusterMotion.dashVelocity(player.getYRot(), m.direction).normalize().scale(1.2)).add(0.0, 1.4, 0.0);
               HulkbusterDestruction.soft(player.serverLevel(), ahead, 1.3, HulkbusterDestruction.PUNCH_HARDNESS);
            }

            if (m.ticks >= HulkbusterRules.DASH_BURST) {
               m.set(player.onGround() ? HulkbusterMotion.State.NONE : HulkbusterMotion.State.FALL);
            }
         }
         case JET_CHARGE -> {
            m.jetCharge = Math.min(HulkbusterRules.JET_CHARGE_MAX, m.jetCharge + 1);
            if (!player.onGround() || m.ticks > HulkbusterRules.JET_CHARGE_MAX + 40) {
               m.set(HulkbusterMotion.State.NONE);
            }
         }
         case JET_RISE -> {
            if (m.ticks > 4 && player.getDeltaMovement().y <= 0.0) {
               m.set(HulkbusterMotion.State.FALL);
            }
         }
         case HOVER -> {
            m.hoverUsed++;
            player.resetFallDistance();
            if (!state.energy.spend(HulkbusterRules.HOVER_COST_PER_TICK) || m.hoverUsed >= HulkbusterRules.HOVER_MAX) {
               m.set(HulkbusterMotion.State.FALL);
               if (state.energy.empty()) {
                  HulkbusterSounds.play(player, HulkbusterSounds.Cue.COUGH);
               }
            }
         }
         case LANDING -> {
            if (m.ticks >= HulkbusterRules.LANDING_SQUAT) {
               m.set(HulkbusterMotion.State.NONE);
            }
         }
         case STAGGER -> {
            if (--m.staggerTicks <= 0) {
               m.set(HulkbusterMotion.State.NONE);
            }
         }
         default -> {
         }
      }

      ram(player, state);
      steps(player, state);
      speed(player, state);
   }

   private static void landed(ServerPlayer player, IronManState state, double fall) {
      HulkbusterKit kit = state.hulkKit;
      HulkbusterMotion m = kit.motion;
      ServerLevel level = player.serverLevel();
      m.hoverUsed = 0;
      m.airDashUsed = false;
      BlockPos ground = player.blockPosition().below();
      if (m.state == HulkbusterMotion.State.DIVE) {
         double height = Math.max(fall, m.diveStartY - player.getY());
         float damage = HulkbusterRules.diveDamage(height);
         double r = HulkbusterRules.diveRadius(height);
         for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 2.0, r), e -> e != player && IronManTargets.canHit(player, e))) {
            double d = target.position().distanceTo(player.position());
            if (d > r) {
               continue;
            }

            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), (float)(damage * (1.0 - 0.5 * d / r))) && HeroRegistry.allowsImpulse(target)) {
               Vec3 away = target.position().subtract(player.position());
               Vec3 flat = away.horizontalDistanceSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(away.x, 0.0, away.z).normalize();
               target.setDeltaMovement(flat.scale(1.1).add(0.0, 0.7, 0.0));
               target.hurtMarked = true;
            }
         }

         HeroFx.slam(player, player.position(), level.getBlockState(ground), (float)r);
         HeroFx.flash(player, player.position().add(0.0, 0.5, 0.0), 0.8F + (float)Math.min(1.0, height / 20.0));
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.DIVE_IMPACT);
         HulkbusterDestruction.crater(level, player.position(), Math.min(3.0, 1.0 + height / 10.0), HulkbusterDestruction.PUNCH_HARDNESS);
         m.set(HulkbusterMotion.State.LANDING);
      } else if (fall > HulkbusterRules.HEAVY_LANDING) {
         double r = Math.min(5.0, 2.0 + fall / 8.0);
         for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 1.5, r), e -> e != player && IronManTargets.canHit(player, e))) {
            if (HeroRegistry.allowsImpulse(target)) {
               Vec3 away = target.position().subtract(player.position());
               Vec3 flat = away.horizontalDistanceSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(away.x, 0.0, away.z).normalize();
               target.setDeltaMovement(flat.scale(0.8).add(0.0, 0.4, 0.0));
               target.hurtMarked = true;
            }
         }

         HeroFx.slam(player, player.position(), level.getBlockState(ground), (float)r);
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.LAND_HEAVY);
         HulkbusterDestruction.soft(level, player.position(), 1.6, HulkbusterDestruction.STEP_HARDNESS);
         m.set(HulkbusterMotion.State.LANDING);
      } else {
         if (fall > 1.0) {
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.STEP, player.position(), 0.85F);
         }

         if (m.state.airborne()) {
            m.set(HulkbusterMotion.State.NONE);
         }
      }
   }

   private static void ram(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      HulkbusterMotion m = kit.motion;
      boolean running = player.onGround() && player.isSprinting() && !kit.timeline.running()
         && (m.state == HulkbusterMotion.State.NONE || m.state == HulkbusterMotion.State.RAM);
      if (!running) {
         m.sprintTicks = 0;
         if (m.state == HulkbusterMotion.State.RAM) {
            m.set(HulkbusterMotion.State.NONE);
         }

         return;
      }

      if (++m.sprintTicks < HulkbusterRules.RAM_FULL_TICKS) {
         return;
      }

      m.set(HulkbusterMotion.State.RAM);
      Vec3 forward = HulkbusterCombat.flatLook(player);
      Vec3 front = player.position().add(forward.scale(1.1));
      AABB box = new AABB(front, front).inflate(1.0, 0.0, 1.0).expandTowards(0.0, 3.0, 0.0);
      for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && IronManTargets.canHit(player, e))) {
         if (target.invulnerableTime > 0) {
            continue;
         }

         if (target.hurt(player.damageSources().playerAttack(player), 7.0F) && HeroRegistry.allowsImpulse(target)) {
            Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
            double s = Math.signum(target.position().subtract(player.position()).dot(side));
            target.setDeltaMovement(forward.scale(1.3).add(side.scale(s * 0.6)).add(0.0, 0.5, 0.0));
            target.hurtMarked = true;
            HeroFx.shockwave(player, target.getBoundingBox().getCenter(), 0.5F, null, 1.5F);
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.PUNCH, target.position(), 0.85F);
         }
      }

      if (m.ticks % 2 == 0) {
         HulkbusterDestruction.soft(player.serverLevel(), player.position().add(forward.scale(1.4)).add(0.0, 1.6, 0.0), 1.4, HulkbusterDestruction.PUNCH_HARDNESS);
      }

      if (player.horizontalCollision && m.ticks > 4) {
         // Ran into something hard: the machine staggers itself (spec §5).
         m.set(HulkbusterMotion.State.STAGGER);
         m.staggerTicks = HulkbusterRules.RAM_WALL_STAGGER;
         m.sprintTicks = 0;
         player.setSprinting(false);
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.SCREECH);
         HeroFx.shockwave(player, player.position().add(forward.scale(1.0)).add(0.0, 1.5, 0.0), 0.8F, null, 2.0F);
      }
   }

   /** Heavy steps: a thud and soft blocks underfoot (glass, flowers, leaves) every stride (spec §5). */
   private static void steps(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      if (!player.onGround()) {
         return;
      }

      double moved = Math.sqrt(player.getDeltaMovement().horizontalDistanceSqr());
      kit.stepDistance += moved;
      if (kit.stepDistance >= STEP_LENGTH) {
         kit.stepDistance = 0.0;
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.STEP);
         HulkbusterDestruction.soft(player.serverLevel(), player.position().add(0.0, 0.3, 0.0), 1.2, HulkbusterDestruction.STEP_HARDNESS);
      }
   }

   /** One speed modifier: the giant's base pace, the ram, and the slow-downs of heavy moves. */
   private static void speed(ServerPlayer player, IronManState state) {
      double factor = speedFactor(state.hulkKit);
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      AttributeModifier current = speed.getModifier(SPEED_ID);
      if (current != null && Math.abs(current.getAmount() - factor) < 1.0E-4) {
         return;
      }

      if (current != null) {
         speed.removeModifier(SPEED_ID);
      }

      speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Hulkbuster pace", factor, AttributeModifier.Operation.MULTIPLY_TOTAL));
   }

   static double speedFactor(HulkbusterKit kit) {
      HulkbusterMotion m = kit.motion;
      if (m.state == HulkbusterMotion.State.STAGGER || m.state == HulkbusterMotion.State.LANDING || m.state == HulkbusterMotion.State.DASH_WINDUP) {
         return -1.0;
      }

      HulkMove move = kit.timeline.move();
      if (move != null) {
         return switch (move) {
            case BLOCK -> HulkbusterRules.BLOCK_SLOW;
            case CHARGED -> HulkbusterRules.CHARGE_SLOW;
            case HEAVY_REPULSORS, SEISMIC_RIFT, SPARE_ARM, JET_RAM, GRAB_FINISH, GRAB_SLAM, GRAB_THROW, COUNTER -> -1.0;
            case GRAB_HOLD, GRAB_PUNCH -> -0.45;
            default -> -0.35;
         };
      }

      if (m.state == HulkbusterMotion.State.JET_CHARGE) {
         return -0.85;
      }

      return m.state == HulkbusterMotion.State.RAM ? HulkbusterRules.WALK_FACTOR + HulkbusterRules.RAM_BONUS : HulkbusterRules.WALK_FACTOR;
   }

   /** Suit off: the pace modifier goes. */
   public static void clear(ServerPlayer player) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null && speed.getModifier(SPEED_ID) != null) {
         speed.removeModifier(SPEED_ID);
      }
   }
}

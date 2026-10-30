package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.entity.captain.ThrownShieldEntity;
import dev.baranhan.viltrumitecore.hero.captain.assemble.AvengersTeam;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Captain's slot abilities (spec §5.3-5.7): ricochet throw (tap = target chain, hold = bank shot, press while
 * out = recall), shield rush, vibranium slam, tactical roll and the ultimate call. Server only.
 */
public final class CaptainSkills {
   private CaptainSkills() {
   }

   /** Moves that the skills drive; combo moves and short reactions may be cancelled by them. */
   static boolean cancellable(CaptainState state) {
      CaptainMove m = state.move;
      return m == null || m.isCombo() || m == CaptainMove.CATCH || m == CaptainMove.LANDING || m == CaptainMove.PARRY || m == CaptainMove.RECALL;
   }

   public static void input(ServerPlayer player, CaptainState state, CaptainAbility ability, boolean pressed) {
      if (ability == CaptainAbility.THROW) {
         throwInput(player, state, pressed);
         return;
      }
      if (!pressed) {
         return;
      }
      int cd = state.cooldowns[ability.cooldown];
      switch (ability) {
         case RUSH -> {
            if (cd > 0 || !cancellable(state)) {
               refuse(player);
               return;
            }
            startRush(player, state);
         }
         case SLAM -> {
            if (cd > 0 || !state.shieldInHand() || !cancellable(state)) {
               refuse(player);
               return;
            }
            startSlam(player, state);
         }
         case ROLL -> {
            if (cd > 0 || !(cancellable(state) || state.is(CaptainMove.AIM))) {
               refuse(player);
               return;
            }
            startRoll(player, state);
         }
         case ASSEMBLE -> {
            if (cd > 0 || state.scene != null || !cancellable(state)) {
               refuse(player);
               return;
            }
            if (AvengersTeam.assemble(player, state)) {
               state.cooldowns[CaptainRules.CD_ULT] = CaptainRules.ULT_COOLDOWN;
            } else {
               refuse(player);
            }
         }
         default -> {
         }
      }
   }

   private static void refuse(ServerPlayer player) {
      CaptainSounds.play(player, CaptainSounds.REFUSE, 0.6F, 1.0F);
   }

   // --- ricochet throw (spec §5.3) ---

   private static void throwInput(ServerPlayer player, CaptainState state, boolean pressed) {
      if (pressed) {
         if (!state.shieldInHand()) {
            recall(player, state);
            return;
         }
         if (state.cooldowns[CaptainRules.CD_THROW] > 0 || !cancellable(state)) {
            refuse(player);
            return;
         }
         CaptainCombat.endGuard(state);
         state.start(CaptainMove.AIM);
         state.aimHeld = true;
         state.aimTicks = 0;
         state.idle = 0;
      } else if (state.is(CaptainMove.AIM) && state.aimHeld) {
         state.aimHeld = false;
         release(player, state);
      }
   }

   private static void release(ServerPlayer player, CaptainState state) {
      state.throwBank = state.aimTicks >= CaptainRules.AIM_THRESHOLD;
      LivingEntity target = state.throwBank ? null : CaptainTargets.aimed(player, CaptainRules.THROW_RANGE, e -> CaptainTargets.mayHit(player, e));
      if (!state.throwBank && target == null) {
         // no target under the crosshair: the nearest enemy in a forward cone
         List<LivingEntity> cone = CaptainTargets.arc(player, CaptainRules.BOUNCE_RADIUS, 60, e -> CaptainTargets.mayHit(player, e)
            && CaptainTargets.visible(player.level(), player.getEyePosition(), e.getBoundingBox().getCenter()));
         target = cone.isEmpty() ? null : cone.get(0);
      }
      state.throwTarget = target == null ? -1 : target.getId();
      state.aimPoint = null;
      state.start(CaptainMove.THROW);
      CaptainSounds.play(player, CaptainSounds.THROW, 1.0F, state.throwBank ? 0.9F : 1.05F);
   }

   private static void spawnShield(ServerPlayer player, CaptainState state) {
      ServerLevel level = player.serverLevel();
      ThrownShieldEntity shield = ViltrumiteEntities.THROWN_SHIELD.get().create(level);
      if (shield == null) {
         return;
      }
      boolean empowered = CaptainRules.empowered(state.kinetic);
      if (empowered) {
         state.kinetic -= CaptainRules.EMPOWER_COST;
      }
      Vec3 look = player.getLookAngle();
      Vec3 start = player.getEyePosition().add(look.scale(0.8)).add(0, -0.25, 0);
      Entity target = state.throwTarget >= 0 ? level.getEntity(state.throwTarget) : null;
      shield.setup(player, start, look, state.throwBank, empowered, target instanceof LivingEntity l ? l : null);
      level.addFreshEntity(shield);
      state.shieldId = shield.getId();
      state.cooldowns[CaptainRules.CD_THROW] = CaptainRules.THROW_COOLDOWN;
      CaptainFx.around(player, CaptainFx.THROW, empowered ? 1 : 0, start, look);
   }

   private static void recall(ServerPlayer player, CaptainState state) {
      Entity e = player.level().getEntity(state.shieldId);
      if (e instanceof ThrownShieldEntity shield && shield.isAlive()) {
         shield.recall();
         if (cancellable(state)) {
            state.start(CaptainMove.RECALL);
         }
         CaptainSounds.play(player, CaptainSounds.RECALL, 0.9F, 1.0F);
      } else {
         state.shieldId = -1; // lost track of it: back in the hand
      }
   }

   /** The shield came back to the hand (called by the entity). */
   public static void caught(ServerPlayer player, boolean onReturn) {
      CaptainState state = CaptainState.of(player);
      if (state == null) {
         return;
      }
      state.shieldId = -1;
      if (!onReturn) {
         return;
      }
      state.catchLeft = CaptainRules.CATCH_WINDOW;
      if (state.move == null || state.is(CaptainMove.RECALL)) {
         state.start(CaptainMove.CATCH);
      }
      Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0, -0.3, 0);
      CaptainFx.around(player, CaptainFx.CATCH, 0, at, Vec3.ZERO);
      CaptainSounds.play(player, CaptainSounds.CATCH, 1.0F, CaptainSounds.jitter(player, 1.0F));
   }

   /** Kinetic gain from a ricochet hit (called by the entity). */
   public static void ricochetHit(ServerPlayer player) {
      CaptainState state = CaptainState.of(player);
      if (state != null) {
         boolean was = CaptainRules.empowered(state.kinetic);
         state.kinetic = CaptainRules.gain(state.kinetic, CaptainRules.GAIN_RICOCHET);
         state.kineticIdle = 0;
         state.idle = 0;
         if (!was && CaptainRules.empowered(state.kinetic)) {
            CaptainFx.around(player, CaptainFx.CHARGED, 0, player.position(), Vec3.ZERO);
            CaptainSounds.play(player, CaptainSounds.CHARGED, 0.8F, 1.0F);
         }
      }
   }

   // --- rush (spec §5.4) ---

   private static void startRush(ServerPlayer player, CaptainState state) {
      CaptainCombat.endGuard(state);
      state.start(CaptainMove.RUSH);
      state.moveDir = CaptainCombat.flat(player.getLookAngle());
      state.cooldowns[CaptainRules.CD_RUSH] = CaptainRules.RUSH_COOLDOWN;
      state.idle = 0;
      Vec3 v = state.moveDir.scale(CaptainRules.RUSH_SPEED);
      CaptainFx.motion(player, new Vec3(v.x, -0.08, v.z), CaptainRules.RUSH_TICKS, false);
      player.setDeltaMovement(v.x, player.getDeltaMovement().y, v.z);
      player.hurtMarked = true;
      CaptainFx.around(player, CaptainFx.RUSH, state.shieldInHand() ? 0 : 1, player.position(), state.moveDir);
      CaptainSounds.play(player, CaptainSounds.RUSH, 1.0F, 1.0F);
   }

   private static void rushStep(ServerPlayer player, CaptainState state) {
      Vec3 dir = state.moveDir;
      Vec3 front = player.position().add(dir.scale(0.9)).add(0, 0.9, 0);
      List<LivingEntity> hit = CaptainTargets.around(player, front, 1.25, e -> CaptainTargets.mayHit(player, e) && !state.hit.contains(e.getId()));
      if (!hit.isEmpty()) {
         bash(player, state, hit);
         return;
      }
      if (player.horizontalCollision && state.elapsed > 2) {
         // straight into a wall: a short stagger, no damage
         CaptainFx.motion(player, Vec3.ZERO, 1, true);
         state.start(CaptainMove.RUSH_BASH);
         state.rushFollow = CaptainRules.RUSH_FOLLOW_WINDOW;
         CaptainSounds.play(player, CaptainSounds.RUSH_HIT, 0.6F, 0.7F);
         return;
      }
      if (state.elapsed >= CaptainRules.RUSH_TICKS) {
         state.endMove();
         state.rushFollow = CaptainRules.RUSH_FOLLOW_WINDOW;
      }
   }

   private static void bash(ServerPlayer player, CaptainState state, List<LivingEntity> hit) {
      boolean shield = state.shieldInHand();
      float damage = shield ? CaptainRules.RUSH_BASH_DAMAGE : CaptainRules.RUSH_TACKLE_DAMAGE;
      Vec3 dir = state.moveDir;
      int[] ids = new int[Math.min(6, hit.size()) + 1];
      int n = 0;
      for (LivingEntity t : CaptainTargets.around(player, player.position().add(dir.scale(1.2)).add(0, 0.9, 0), 2.0,
         e -> CaptainTargets.mayHit(player, e))) {
         if (!state.hit.add(t.getId()) || !CaptainTargets.strike(player, t, damage)) {
            continue;
         }
         if (n < ids.length - 1) ids[n++] = t.getId();
         CaptainTargets.push(t, dir.scale(1.25).add(0, 0.35, 0));
         if (shield) {
            CaptainTargets.stun(player, t, CaptainRules.RUSH_STUN);
            CaptainFx.around(player, CaptainFx.STUN, CaptainRules.RUSH_STUN, t.getBoundingBox().getCenter(), Vec3.ZERO, t.getId());
         }
         state.kinetic = CaptainRules.gain(state.kinetic, CaptainRules.GAIN_COMBO * 2);
      }
      Vec3 at = player.position().add(dir.scale(1.0)).add(0, 1.0, 0);
      CaptainFx.around(player, CaptainFx.CONTACT, 4, at, player.getEyePosition());
      CaptainSounds.playAt(player, at, CaptainSounds.RUSH_HIT, 1.0F, 1.0F);
      CaptainFx.motion(player, dir.scale(-0.1), 2, true);
      if (n > 0) {
         ids[n++] = player.getId();
         int[] all = new int[n];
         System.arraycopy(ids, 0, all, 0, n);
         HeroFx.hitStop(player, at, 2, all);
      }
      state.start(CaptainMove.RUSH_BASH);
      state.rushFollow = CaptainRules.RUSH_FOLLOW_WINDOW;
   }

   // --- slam (spec §5.5) ---

   private static void startSlam(ServerPlayer player, CaptainState state) {
      CaptainCombat.endGuard(state);
      state.slamEmpowered = CaptainRules.empowered(state.kinetic);
      if (state.slamEmpowered) {
         state.kinetic -= CaptainRules.EMPOWER_COST;
      }
      state.start(CaptainMove.SLAM_LEAP);
      state.moveDir = CaptainCombat.flat(player.getLookAngle());
      state.cooldowns[CaptainRules.CD_SLAM] = CaptainRules.SLAM_COOLDOWN;
      state.idle = 0;
      Vec3 v = state.moveDir.scale(CaptainRules.SLAM_FORWARD).add(0, CaptainRules.SLAM_UP, 0);
      CaptainFx.motion(player, v, 2, true);
      player.setDeltaMovement(v);
      player.hurtMarked = true;
      CaptainSounds.play(player, CaptainSounds.SLAM_LEAP, 1.0F, 1.0F);
   }

   private static void slamStep(ServerPlayer player, CaptainState state) {
      if (state.elapsed == CaptainRules.SLAM_RISE_TICKS) {
         Vec3 v = state.moveDir.scale(0.25).add(0, CaptainRules.SLAM_DIVE, 0);
         CaptainFx.motion(player, v, CaptainRules.SLAM_MAX_TICKS, false);
         player.setDeltaMovement(v);
         player.hurtMarked = true;
      }
      if (state.elapsed > CaptainRules.SLAM_RISE_TICKS && (player.onGround() || player.isInWater()) || state.elapsed >= CaptainRules.SLAM_MAX_TICKS) {
         slamImpact(player, state);
      }
   }

   private static void slamImpact(ServerPlayer player, CaptainState state) {
      boolean emp = state.slamEmpowered;
      double radius = CaptainRules.slamRadius(emp);
      Vec3 at = player.position();
      player.fallDistance = 0.0F;
      CaptainFx.motion(player, Vec3.ZERO, 1, true);
      for (LivingEntity t : CaptainTargets.around(player, at.add(0, 0.5, 0), radius, e -> CaptainTargets.mayHit(player, e))) {
         double d = Math.sqrt(t.getBoundingBox().distanceToSqr(at));
         if (CaptainTargets.strike(player, t, CaptainRules.slamDamage(emp, d, radius))) {
            Vec3 away = t.position().subtract(at);
            Vec3 h = away.horizontalDistanceSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(away.x, 0, away.z).normalize();
            CaptainTargets.push(t, h.scale(0.5).add(0, emp ? 0.75 : 0.55, 0));
            CaptainTargets.stun(player, t, emp ? 30 : 16);
         }
      }
      CaptainFx.around(player, CaptainFx.SLAM, emp ? 1 : 0, at, new Vec3(radius, 0, 0));
      HeroFx.slam(player, at, player.level().getBlockState(player.blockPosition().below()), (float)radius);
      CaptainSounds.play(player, emp ? CaptainSounds.SLAM_EMPOWERED : CaptainSounds.SLAM_IMPACT, 1.2F, 1.0F);
      state.start(CaptainMove.SLAM_IMPACT);
   }

   // --- roll (spec §5.6) ---

   private static void startRoll(ServerPlayer player, CaptainState state) {
      CaptainCombat.endGuard(state);
      if (state.is(CaptainMove.AIM)) {
         state.aimHeld = false;
         state.aimPoint = null;
      }
      Vec3 vel = player.getDeltaMovement();
      Vec3 dir = new Vec3(vel.x, 0, vel.z);
      dir = dir.lengthSqr() > 0.004 ? dir.normalize() : CaptainCombat.flat(player.getLookAngle());
      state.start(CaptainMove.ROLL);
      state.moveDir = dir;
      state.iframes = CaptainRules.ROLL_IFRAMES;
      state.cooldowns[CaptainRules.CD_ROLL] = CaptainRules.ROLL_COOLDOWN;
      Vec3 from = player.position();
      Vec3 v = dir.scale(CaptainRules.ROLL_SPEED);
      CaptainFx.motion(player, new Vec3(v.x, -0.1, v.z), CaptainRules.ROLL_TICKS - 2, false);
      player.setDeltaMovement(v.x, player.getDeltaMovement().y, v.z);
      player.hurtMarked = true;
      CaptainFx.around(player, CaptainFx.ROLL, 0, from, from.add(dir.scale(CaptainRules.ROLL_SPEED * CaptainRules.ROLL_TICKS)));
      CaptainSounds.play(player, CaptainSounds.ROLL, 0.9F, CaptainSounds.jitter(player, 1.0F));
   }

   // --- per tick ---

   /** Drives the dynamic moves; true when it handled the clock end itself. */
   static boolean step(ServerPlayer player, CaptainState state, CaptainMove move) {
      switch (move) {
         case AIM -> {
            if (state.aimHeld) {
               state.aimTicks++;
               if (state.aimTicks >= CaptainRules.AIM_THRESHOLD) {
                  state.aimPoint = bankPreview(player);
               }
               if (state.aimTicks >= CaptainRules.AIM_MAX) {
                  state.aimHeld = false;
                  release(player, state);
               }
            } else {
               state.endMove();
            }
            return true;
         }
         case THROW -> {
            if (state.elapsed == CaptainRules.THROW_RELEASE_TICK) {
               spawnShield(player, state);
            }
            if (state.elapsed >= move.length) {
               state.endMove();
            }
            return true;
         }
         case RUSH -> {
            rushStep(player, state);
            return true;
         }
         case SLAM_LEAP -> {
            slamStep(player, state);
            return true;
         }
         case ROLL -> {
            if (state.elapsed >= move.length) {
               state.endMove();
               state.rollFollow = CaptainRules.ROLL_FOLLOW_WINDOW;
               if (state.comboQueued) {
                  state.comboQueued = false;
                  CaptainCombat.startNext(player, state);
               }
            }
            return true;
         }
         case ASSEMBLE_CALL, ASSEMBLE_WAIT, ASSEMBLE_RAISE -> {
            // the scene (AvengersTeam) owns these moves; a lost scene frees Steve
            if (state.scene == null) {
               state.endMove();
            }
            return true;
         }
         default -> {
            return false;
         }
      }
   }

   static void tick(ServerPlayer player, CaptainState state) {
      if (!state.shieldInHand() && state.tickCount % 10 == 0) {
         Entity e = player.level().getEntity(state.shieldId);
         if (!(e instanceof ThrownShieldEntity) || !e.isAlive()) {
            state.shieldId = -1; // unloaded with its chunk or removed: back in the hand
         }
      }
   }

   /** Where a bank shot along the look would first touch a block (preview for the aim line). */
   static Vec3 bankPreview(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(CaptainRules.THROW_RANGE));
      BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
   }

   static void onLanded(ServerPlayer player, CaptainState state, float fallDistance) {
      if (state.is(CaptainMove.SLAM_LEAP) && state.elapsed > CaptainRules.SLAM_RISE_TICKS) {
         slamImpact(player, state);
      }
   }

   /** Death, logout, dimension or hero change: the shield returns to the hand at once. */
   public static void cleanup(ServerPlayer player, CaptainState state) {
      if (!state.shieldInHand()) {
         Entity e = player.level().getEntity(state.shieldId);
         if (e instanceof ThrownShieldEntity shield) {
            shield.discard();
         }
         state.shieldId = -1;
      }
      state.aimHeld = false;
      state.aimPoint = null;
   }
}

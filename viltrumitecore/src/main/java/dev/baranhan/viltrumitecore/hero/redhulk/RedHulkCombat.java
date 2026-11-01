package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Red Hulk's hands (spec §5.1-5.2): the five-hit heavy chain on LMB (left jab, right cross, hook, rising
 * uppercut, two-hand hammer onto the ground) and grab / hold / throw on RMB; the shared move timeline. Server only.
 */
public final class RedHulkCombat {
   private static final TagKey<EntityType<?>> BOSSES = net.minecraftforge.common.Tags.EntityTypes.BOSSES;

   private RedHulkCombat() {
   }

   // --- LMB ---------------------------------------------------------------------------------------------------

   public static void primary(ServerPlayer player, RedHulkState state) {
      state.idle = 0;
      if (state.holding() && state.is(RedHulkMove.HOLD)) {
         throwHeld(player, state);
         return;
      }
      if (state.running()) {
         RedHulkMove m = state.move;
         if (m.locks() || m == RedHulkMove.THROW && state.elapsed < m.contact) {
            return;
         }
         if (m.isCombo() && RedHulkRules.buffers(state.elapsed, m.length)) {
            state.comboQueued = true;
            return;
         }
         if (!m.cancellable(state.elapsed)) {
            if (m.cancelFrom >= 0) {
               state.comboQueued = true; // late in the wind-down: the punch comes the moment it may
            }
            return;
         }
         interrupt(player, state);
      }
      startNext(player, state);
   }

   static void startNext(ServerPlayer player, RedHulkState state) {
      state.comboQueued = false;
      int step = RedHulkRules.nextStep(state.comboStep, state.comboIdle);
      state.comboStep = step;
      RedHulkMove move = RedHulkMove.combo(step);
      state.start(move);
      state.moveDir = player.getLookAngle();
      boolean heavy = move == RedHulkMove.HAMMER || move == RedHulkMove.UPPERCUT;
      RedHulkSounds.play(player, heavy ? RedHulkSounds.SWING_HEAVY : RedHulkSounds.SWING, heavy ? 1.0F : 0.85F,
         RedHulkSounds.jitter(player, heavy ? 0.85F : 1.0F));
      if (move == RedHulkMove.HOOK || move == RedHulkMove.HAMMER) {
         // the weight shifts in: a short step towards the target
         Vec3 h = RedHulkTargets.flat(player.getLookAngle()).scale(move == RedHulkMove.HAMMER ? 0.45 : 0.3);
         player.setDeltaMovement(player.getDeltaMovement().add(h.x, 0, h.z));
         player.hurtMarked = true;
      }
   }

   /** Contact of a combo move: arc hits, heat, hit-stop on the heavy ones, the hammer's ground wave. */
   static void comboContact(ServerPlayer player, RedHulkState state, RedHulkMove move) {
      int step = move.comboStep();
      List<LivingEntity> targets = RedHulkTargets.arc(player, RedHulkRules.REACH, RedHulkRules.ARC_DEGREES, e -> RedHulkTargets.mayHit(player, e));
      float damage = RedHulkRules.COMBO_DAMAGE[step];
      Vec3 look = RedHulkTargets.flat(player.getLookAngle());
      int landed = 0;
      int[] ids = new int[Math.min(8, targets.size()) + 1];
      for (LivingEntity t : targets) {
         if (move != RedHulkMove.HAMMER && landed >= 2) {
            break; // a punch carries through two bodies at most
         }
         if (!state.hit.add(t.getId()) || !RedHulkTargets.strike(player, t, damage)) {
            continue;
         }
         if (landed < ids.length - 1) ids[landed] = t.getId();
         landed++;
         Vec3 at = RedHulkTargets.center(t);
         RedHulkFx.send(player, RedHulkFx.PUNCH, at, player.getEyePosition(), RedHulkFx.scale(state.heat, step >= 3 ? 1.3F : 1.0F), null, 0);
         RedHulkSounds.playAt(player, at, step >= 3 ? RedHulkSounds.PUNCH_HEAVY : RedHulkSounds.PUNCH,
            RedHulkSounds.heatVolume(state.heat, 1.0F), RedHulkSounds.jitter(player, RedHulkSounds.heatPitch(state.heat, 1.0F)));
         switch (move) {
            case UPPERCUT -> RedHulkTargets.push(t, new Vec3(look.x * 0.25, RedHulkRules.UPPERCUT_LIFT, look.z * 0.25));
            case HOOK -> RedHulkTargets.push(t, look.scale(0.55).add(look.z * 0.25, 0.15, -look.x * 0.25));
            case HAMMER -> {
               RedHulkTargets.push(t, look.scale(0.5).add(0, -0.2, 0));
               RedHulkTargets.stun(player, t, RedHulkRules.HAMMER_STUN);
            }
            default -> RedHulkTargets.push(t, look.scale(0.35).add(0, 0.08, 0));
         }
         if (state.heat >= RedHulkRules.BLAZING) {
            t.setSecondsOnFire(2);
         }
      }
      if (move == RedHulkMove.HAMMER) {
         Vec3 ground = player.position().add(look.scale(RedHulkRules.HAMMER_AHEAD));
         for (LivingEntity t : RedHulkTargets.around(player, ground, RedHulkRules.HAMMER_RADIUS, e -> RedHulkTargets.mayHit(player, e))) {
            if (state.hit.add(t.getId()) && RedHulkTargets.strike(player, t, RedHulkRules.HAMMER_AREA_DAMAGE)) {
               RedHulkTargets.push(t, RedHulkTargets.outward(ground, t).scale(0.5).add(0, 0.35, 0));
               landed++;
            }
         }
         RedHulkFx.send(player, RedHulkFx.HAMMER, ground, RedHulkFx.radius(ground, RedHulkRules.HAMMER_RADIUS), RedHulkFx.scale(state.heat, 1.0F),
            player.level().getBlockState(net.minecraft.core.BlockPos.containing(ground).below()), 0);
         RedHulkSounds.playAt(player, ground, RedHulkSounds.HAMMER, RedHulkSounds.heatVolume(state.heat, 1.2F), RedHulkSounds.heatPitch(state.heat, 1.0F));
      }
      if (landed > 0) {
         state.addHeat(RedHulkRules.COMBO_HEAT[step] + RedHulkRules.HEAT_PER_EXTRA_TARGET * (landed - 1));
         state.idle = 0;
         if (step >= 3) {
            int n = Math.min(landed, ids.length - 1);
            ids[n] = player.getId();
            int[] all = new int[n + 1];
            System.arraycopy(ids, 0, all, 0, n + 1);
            HeroFx.hitStop(player, player.position(), RedHulkRules.HIT_STOP_HEAVY, all);
         }
      }
   }

   // --- RMB: grab / hold / throw ------------------------------------------------------------------------------

   public static void secondary(ServerPlayer player, RedHulkState state) {
      state.idle = 0;
      if (state.holding()) {
         if (state.is(RedHulkMove.HOLD)) {
            throwHeld(player, state);
         }
         return;
      }
      if (state.cooldowns[RedHulkRules.CD_GRAB] > 0) {
         RedHulkSounds.play(player, RedHulkSounds.REFUSE, 0.5F, 1.0F);
         return;
      }
      if (state.running()) {
         RedHulkMove m = state.move;
         if (m.locks() || !m.cancellable(state.elapsed)) {
            return;
         }
         interrupt(player, state);
      }
      state.start(RedHulkMove.GRAB);
      RedHulkSounds.play(player, RedHulkSounds.SWING, 0.7F, 0.8F);
   }

   /** Contact of the reach: the nearest liftable body in front is taken through the shared PULL control. */
   static void tryGrab(ServerPlayer player, RedHulkState state) {
      ControlManager manager = ControlManager.get(player.serverLevel());
      for (LivingEntity t : RedHulkTargets.arc(player, RedHulkRules.GRAB_REACH, RedHulkRules.GRAB_ARC, e -> grabbable(player, e))) {
         UUID effect = UUID.randomUUID();
         long expiry = player.level().getGameTime() + RedHulkRules.HOLD_MAX + 40L;
         if (!manager.tryAcquire(t, player.getUUID(), effect, ControlKind.PULL, expiry)) {
            continue;
         }
         state.heldId = t.getId();
         state.heldUuid = t.getUUID();
         state.holdEffect = effect;
         state.holdTicks = 0;
         state.start(RedHulkMove.HOLD);
         hold(player, t);
         RedHulkSounds.playAt(player, RedHulkTargets.center(t), RedHulkSounds.GRAB, 1.0F, RedHulkSounds.jitter(player, 0.9F));
         return;
      }
      state.start(RedHulkMove.GRAB_MISS);
      state.cooldowns[RedHulkRules.CD_GRAB] = 40;
   }

   static boolean grabbable(ServerPlayer player, LivingEntity e) {
      return RedHulkTargets.mayHit(player, e) && RedHulkRules.liftable(e.getBbWidth(), e.getBbHeight()) && !e.getType().is(BOSSES)
         && HeroRegistry.allowsExternalControl(e, ControlKind.PULL) && !HeroDamage.isAnchored(e) && !e.isPassenger() && !e.isVehicle();
   }

   /** Every tick of the hold (HOLD and the wind-up of THROW): keep the body in the right hand, squeeze it. */
   static void holdTick(ServerPlayer player, RedHulkState state) {
      if (!state.holding()) {
         return;
      }
      Entity entity = player.level().getEntity(state.heldId);
      ControlManager manager = ControlManager.get(player.serverLevel());
      boolean mine = entity instanceof LivingEntity l && state.holdEffect != null && state.holdEffect.equals(manager.effectIdOf(l));
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || !mine || manager.isControlled(player)) {
         release(player, state, ReleaseReason.TARGET_LOST);
         if (state.is(RedHulkMove.HOLD)) {
            state.endMove();
         }
         return;
      }
      if (WorldPause.blocksMovement(player, target)) {
         return;
      }
      if (target.position().distanceToSqr(holdPoint(player, target)) > RedHulkRules.HOLD_LOST_DISTANCE * RedHulkRules.HOLD_LOST_DISTANCE) {
         release(player, state, ReleaseReason.TARGET_LOST);
         state.endMove();
         return;
      }
      hold(player, target);
      if (state.is(RedHulkMove.HOLD)) {
         state.holdTicks++;
         if (state.holdTicks % RedHulkRules.SQUEEZE_INTERVAL == 0 && RedHulkTargets.strike(player, target, RedHulkRules.SQUEEZE_DAMAGE)) {
            state.addHeat(RedHulkRules.SQUEEZE_HEAT);
            RedHulkSounds.playAt(player, RedHulkTargets.center(target), RedHulkSounds.SQUEEZE, 0.8F, RedHulkSounds.jitter(player, 1.0F));
         }
         if (state.holdTicks >= RedHulkRules.HOLD_MAX) {
            throwHeld(player, state);
         }
      }
   }

   static Vec3 holdPoint(ServerPlayer player, LivingEntity target) {
      return anchor(player.position(), player.getYRot(), target.getBbWidth(), target.getBbHeight());
   }

   /** Shared server/client anchor of the held body: in front of the right shoulder, feet hanging at hand height. */
   public static Vec3 anchor(Vec3 position, float yaw, float width, float height) {
      Vec3 flat = Vec3.directionFromRotation(0, yaw);
      Vec3 right = new Vec3(-flat.z, 0, flat.x);
      return position.add(flat.scale(0.95 + width * 0.5)).add(right.scale(0.35))
         .add(0, Math.max(0.3, RedHulkRules.HOLD_HAND_HEIGHT - height * 0.85), 0);
   }

   private static void hold(ServerPlayer player, LivingEntity target) {
      Vec3 at = holdPoint(player, target);
      target.setPos(at.x, at.y, at.z);
      target.setDeltaMovement(Vec3.ZERO);
      target.resetFallDistance();
      target.hurtMarked = true;
   }

   /** RMB / LMB / the hold limit: the throw wind-up starts; the body leaves the hand at the THROW contact. */
   static void throwHeld(ServerPlayer player, RedHulkState state) {
      state.start(RedHulkMove.THROW);
      RedHulkSounds.play(player, RedHulkSounds.SWING_HEAVY, 0.9F, 0.75F);
   }

   static void throwContact(ServerPlayer player, RedHulkState state) {
      Entity entity = player.level().getEntity(state.heldId);
      release(player, state, ReleaseReason.NORMAL_END);
      state.cooldowns[RedHulkRules.CD_GRAB] = RedHulkRules.COOLDOWN_TICKS[RedHulkRules.CD_GRAB];
      if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
         return;
      }
      RedHulkTargets.strike(player, target, RedHulkRules.THROW_DAMAGE);
      state.addHeat(RedHulkRules.THROW_HEAT);
      Vec3 look = player.getLookAngle();
      state.throwVelocity = look.scale(RedHulkRules.THROW_SPEED).add(0, RedHulkRules.THROW_LIFT, 0);
      state.thrownId = target.getId();
      state.thrownTicks = 0;
      if (HeroRegistry.allowsImpulse(target)) {
         target.setDeltaMovement(state.throwVelocity);
         target.hurtMarked = true;
      }
      HeroFx.sonicRing(player, RedHulkTargets.center(target));
      HeroFx.airTrail(player, target);
      RedHulkSounds.playAt(player, RedHulkTargets.center(target), RedHulkSounds.THROW, 1.2F, RedHulkSounds.jitter(player, 0.9F));
   }

   /** The thrown body flies; the first wall or floor it meets bursts around it. */
   static void thrownTick(ServerPlayer player, RedHulkState state) {
      if (state.thrownId < 0) {
         return;
      }
      Entity entity = player.level().getEntity(state.thrownId);
      state.thrownTicks++;
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || state.thrownTicks > RedHulkRules.THROW_FLIGHT) {
         state.thrownId = -1;
         return;
      }
      if (state.thrownTicks <= 3 && HeroRegistry.allowsImpulse(target)) {
         target.setDeltaMovement(state.throwVelocity.scale(1.0 - state.thrownTicks * 0.1));
         target.hurtMarked = true;
         return;
      }
      boolean wall = target.horizontalCollision;
      if (wall || target.onGround() || target.verticalCollision) {
         Vec3 at = RedHulkTargets.center(target);
         for (LivingEntity other : RedHulkTargets.around(player, at, RedHulkRules.THROW_IMPACT_RADIUS, e -> e != target && RedHulkTargets.mayHit(player, e))) {
            if (RedHulkTargets.strike(player, other, RedHulkRules.THROW_IMPACT_DAMAGE)) {
               RedHulkTargets.push(other, RedHulkTargets.outward(at, other).scale(0.6).add(0, 0.3, 0));
            }
         }
         if (wall) {
            RedHulkTargets.strike(player, target, RedHulkRules.THROW_WALL_DAMAGE);
         }
         RedHulkFx.send(player, RedHulkFx.THROW_IMPACT, at, RedHulkFx.radius(at, RedHulkRules.THROW_IMPACT_RADIUS), RedHulkFx.scale(state.heat, 1.0F),
            player.level().getBlockState(target.getOnPos()), 0);
         RedHulkSounds.playAt(player, at, RedHulkSounds.THROW_IMPACT, 1.2F, RedHulkSounds.jitter(player, 1.0F));
         state.thrownId = -1;
      }
   }

   /** Lets go of the held body (control released once, a player's position re-synced). */
   static void release(ServerPlayer player, RedHulkState state, ReleaseReason reason) {
      if (!state.holding()) {
         return;
      }
      if (state.heldUuid != null && state.holdEffect != null) {
         ControlManager.get(player.serverLevel()).release(state.heldUuid, state.holdEffect, reason);
         Entity released = player.serverLevel().getEntity(state.heldUuid);
         if (released instanceof ServerPlayer victim && victim.isAlive()) {
            victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
         }
      }
      if (reason != ReleaseReason.NORMAL_END) {
         state.cooldowns[RedHulkRules.CD_GRAB] = RedHulkRules.COOLDOWN_TICKS[RedHulkRules.CD_GRAB];
      }
      state.heldId = -1;
      state.heldUuid = null;
      state.holdEffect = null;
      state.holdTicks = 0;
   }

   // --- timeline ----------------------------------------------------------------------------------------------

   static void tick(ServerPlayer player, RedHulkState state) {
      if (state.move != null) {
         RedHulkMove move = state.move;
         state.elapsed++;
         if (!RedHulkSkills.step(player, state, move)) {
            if (move.contact > 0 && state.elapsed == move.contact) {
               contact(player, state, move);
            }
            if (state.move == move && !move.loops() && state.elapsed >= move.length) {
               finish(player, state, move);
            }
         }
      }
      if (state.is(RedHulkMove.HOLD) || state.is(RedHulkMove.THROW) && state.elapsed < RedHulkMove.THROW.contact) {
         holdTick(player, state);
      } else if (state.holding() && !state.is(RedHulkMove.THROW)) {
         release(player, state, ReleaseReason.INTERRUPTED); // the hold pose was cut by something else
      }
      thrownTick(player, state);
   }

   static void contact(ServerPlayer player, RedHulkState state, RedHulkMove move) {
      if (move.isCombo()) {
         comboContact(player, state, move);
      } else if (move == RedHulkMove.GRAB) {
         tryGrab(player, state);
      } else if (move == RedHulkMove.THROW) {
         throwContact(player, state);
      } else {
         RedHulkSkills.contact(player, state, move);
      }
   }

   /** End of a timed move: chained moves continue, then a queued punch or ability starts at once. */
   static void finish(ServerPlayer player, RedHulkState state, RedHulkMove move) {
      if (move.isCombo()) {
         state.comboIdle = 0;
      }
      if (RedHulkSkills.chain(player, state, move)) {
         return;
      }
      state.endMove();
      if (state.queuedAbility != null) {
         RedHulkAbility next = state.queuedAbility;
         state.queuedAbility = null;
         RedHulkSkills.input(player, state, next);
      } else if (state.comboQueued) {
         state.comboQueued = false;
         startNext(player, state);
      }
   }

   /** A new input cuts the current move short (only from its cancel tick). */
   static void interrupt(ServerPlayer player, RedHulkState state) {
      if (state.move != null && state.move.isCombo()) {
         state.comboIdle = 0;
      }
      state.endMove();
      state.queuedAbility = null;
   }
}

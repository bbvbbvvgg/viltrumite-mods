package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Grab &amp; Throw (spec §6.2, Knight slot 3): the first press grabs the target in reach
 * through {@link ControlManager} PULL (one fresh {@code UUID} effect id per grab) and
 * holds it at the hand; the second press (or {@link ReinhardRules#GRAB_MAX_TICKS})
 * releases the control with the same effect id, hits for {@link ReinhardRules#THROW_DAMAGE},
 * holds a 3-tick hit-stop and throws the target past the horizon (~120 blocks: the
 * velocity is re-applied for {@link ReinhardRules#THROW_SUSTAIN_TICKS}) with a sonic ring
 * and an air trail. Every other end (death, logout, dimension change, hero or mode change,
 * a control on Reinhard, the target lost) releases the control without a throw. The
 * cooldown starts at the throw or at a forced release. Lion's Heart / anchored targets
 * (no external control or no impulse) are refused: refusal sound, no cooldown.
 */
public final class GrabThrow implements AbilityHandler {
   /** The target's hold point may drift this far from the hand before the grab counts as lost. */
   static final double LOST_DISTANCE = 8.0;

   /** The world side of a grab: {@code ControlManager} PULL on one target (faked in tests). */
   public interface Control {
      boolean acquire(UUID effectId);

      void release(UUID targetId, UUID effectId, ReleaseReason reason);
   }

   /** What the press knows about the entity in reach. */
   public record Candidate(int id, UUID uuid, boolean controlAllowed, boolean impulseAllowed) {
   }

   /** A running grab: the target, the one effect id of this grab and the control that owns it. */
   public static final class Hold {
      private final int targetId;
      private final UUID targetUuid;
      private final UUID effectId;
      private final Control control;
      private int ticks;

      Hold(int targetId, UUID targetUuid, UUID effectId, Control control) {
         this.targetId = targetId;
         this.targetUuid = targetUuid;
         this.effectId = effectId;
         this.control = control;
      }

      public int targetId() {
         return this.targetId;
      }

      public UUID effectId() {
         return this.effectId;
      }

      public int ticks() {
         return this.ticks;
      }
   }

   /** What happens on an elapsed tick of the throw (phase SNAP). */
   public enum Beat {
      NONE,
      HOLD,
      LAUNCH;
   }

   /** Pure: the hit-stop holds the target, then the launch. */
   public static Beat beat(int elapsed) {
      if (elapsed < ReinhardRules.HIT_STOP_TICKS) {
         return Beat.HOLD;
      }

      return elapsed == ReinhardRules.HIT_STOP_TICKS ? Beat.LAUNCH : Beat.NONE;
   }

   /**
    * Pure: ticks the throw velocity must be re-applied so the flight covers {@code distance}
    * blocks: {@code n · speed} while sustained plus the drag tail {@code speed · drag / (1 − drag)}.
    */
   public static int sustainTicks(double distance, double horizontalSpeed, double airDrag) {
      double tail = horizontalSpeed * airDrag / (1.0 - airDrag);
      return Math.max(1, (int) Math.round((distance - tail) / horizontalSpeed));
   }

   /** Pure: the throw velocity along the look raised by {@link ReinhardRules#THROW_UP_DEGREES} (Minecraft pitch, up = negative). */
   public static Vec3 throwVelocity(float pitch, float yaw) {
      float raised = Math.max(-90.0F, pitch - (float) ReinhardRules.THROW_UP_DEGREES);
      return Vec3.directionFromRotation(raised, yaw).scale(ReinhardRules.THROW_SPEED);
   }

   /** Pure: the grab press; false = refused (nothing acquired, nothing spent). */
   public static boolean grab(ReinhardState state, @Nullable Candidate c, Control control) {
      if (c == null || !c.controlAllowed() || !c.impulseAllowed()) {
         return false;
      }

      UUID effectId = UUID.randomUUID();
      if (!control.acquire(effectId)) {
         return false;
      }

      state.grab = new Hold(c.id(), c.uuid(), effectId, control);
      // The hold never ends by its own length: the throw restarts the timeline at GRAB_MAX_TICKS.
      state.action.start(ReinhardAbility.GRAB_THROW, ReinhardRules.GRAB_MAX_TICKS + 1, c.id());
      return true;
   }

   public static boolean holding(ReinhardState state) {
      return state.grab != null;
   }

   /** Pure: one held tick; true = held long enough, throw now. */
   public static boolean holdTick(ReinhardState state) {
      return state.grab != null && ++state.grab.ticks >= ReinhardRules.GRAB_MAX_TICKS;
   }

   /** Pure: the throw releases the control (same effect id) and plays the throw; returns the target id, −1 = no grab. */
   public static int throwNow(ReinhardState state) {
      Hold hold = state.grab;
      if (hold == null) {
         return -1;
      }

      end(state, hold, ReleaseReason.NORMAL_END);
      state.action.start(ReinhardAbility.GRAB_THROW, ReinhardRules.THROW_ACTION_TICKS, hold.targetId, ActionTimeline.PHASE_SNAP);
      return hold.targetId;
   }

   /** Pure: a forced end, no throw: the control is released once, the hold pose ends, the cooldown starts. */
   public static void release(ReinhardState state, ReleaseReason reason) {
      Hold hold = state.grab;
      if (hold == null) {
         return;
      }

      end(state, hold, reason);
      if (state.action.is(ReinhardAbility.GRAB_THROW)) {
         state.action.cancel();
      }
   }

   private static void end(ReinhardState state, Hold hold, ReleaseReason reason) {
      state.grab = null;
      state.cooldowns[ReinhardAbility.GRAB_THROW.ordinal()] = ReinhardRules.GRAB_COOLDOWN;
      hold.control.release(hold.targetUuid, hold.effectId, reason);
   }

   /** The release reason the target sees for a cleanup of Reinhard; null = mode change / dimension change / control. */
   public static ReleaseReason releaseReason(@Nullable CleanupReason reason) {
      return reason == null ? ReleaseReason.INTERRUPTED : ControlManager.cleanupReleaseReason(reason);
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      LivingEntity target = ReinhardTargets.inReach(player, ReinhardRules.GRAB_REACH);
      Candidate candidate = target == null ? null
         : new Candidate(target.getId(), target.getUUID(), HeroRegistry.allowsExternalControl(target, ControlKind.PULL), HeroRegistry.allowsImpulse(target));
      // The manager of the level of the grab: a later dimension change of Reinhard still releases there.
      ControlManager manager = ControlManager.get(player.serverLevel());
      long expiry = player.level().getGameTime() + ReinhardRules.GRAB_MAX_TICKS + 20L;
      Control control = new Control() {
         @Override
         public boolean acquire(UUID effectId) {
            return manager.tryAcquire(target, player.getUUID(), effectId, ControlKind.PULL, expiry);
         }

         @Override
         public void release(UUID targetId, UUID effectId, ReleaseReason reason) {
            manager.release(targetId, effectId, reason);
         }
      };
      if (!grab(state, candidate, control)) {
         return false;
      }

      hold(player, target);
      ReinhardSounds.playAt(player, center(target), ReinhardSounds.GRAB.get(), 1.0F, 0.9F);
      return true;
   }

   @Override
   public boolean claimsPress(ReinhardState state) {
      return holding(state);
   }

   @Override
   public void secondPress(ServerPlayer player, ReinhardState state) {
      throwTarget(player, state);
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      if (state.grab != null) {
         holdTick(player, state);
      } else if (state.action.is(ReinhardAbility.GRAB_THROW) && state.action.phase() == ActionTimeline.PHASE_SNAP) {
         throwTick(player, state);
      }

      if (state.throwSustain > 0) {
         sustainTick(player, state);
      }
   }

   @Override
   public void cancel(ServerPlayer player, ReinhardState state, @Nullable CleanupReason reason) {
      release(state, releaseReason(reason));
      state.throwSustain = 0;
      state.throwTargetId = -1;
   }

   private static void holdTick(ServerPlayer player, ReinhardState state) {
      Hold hold = state.grab;
      Entity entity = player.level().getEntity(hold.targetId);
      ControlManager manager = ControlManager.get(player.serverLevel());
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || !hold.effectId.equals(manager.effectIdOf(target))) {
         // Death, logout, another dimension, or the control ended elsewhere (expiry, a handoff).
         release(state, ReleaseReason.TARGET_LOST);
         return;
      }

      if (WorldPause.blocksMovement(player, target)) {
         return;
      }

      if (manager.isControlled(player)) {
         // A control on Reinhard himself (a PULL; FREEZE / STASIS already cancel every action).
         release(state, ReleaseReason.INTERRUPTED);
         return;
      }

      if (holdTick(state)) {
         throwTarget(player, state);
         return;
      }

      Vec3 at = holdPoint(player, target);
      if (target.position().distanceToSqr(at) > LOST_DISTANCE * LOST_DISTANCE) {
         release(state, ReleaseReason.TARGET_LOST);
         return;
      }

      hold(player, target);
   }

   /** The target hangs from the hand at chest height, in front of him (the Hulkbuster grab's rate: every tick). */
   static Vec3 holdPoint(ServerPlayer player, LivingEntity target) {
      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
      double ahead = 0.7 + target.getBbWidth() * 0.5;
      double lift = Math.max(0.3, ReinhardRules.GRAB_HAND_HEIGHT - target.getBbHeight() * 0.85);
      return player.position().add(flat.scale(ahead)).add(0.0, lift, 0.0);
   }

   private static void hold(ServerPlayer player, LivingEntity target) {
      if (WorldPause.blocksMovement(player, target)) {
         return;
      }
      Vec3 at = holdPoint(player, target);
      if (target instanceof ServerPlayer victim) {
         victim.teleportTo(at.x, at.y, at.z);
      } else {
         target.setPos(at.x, at.y, at.z);
      }

      target.setDeltaMovement(Vec3.ZERO);
      target.resetFallDistance();
      target.hurtMarked = true;
   }

   private static void throwTarget(ServerPlayer player, ReinhardState state) {
      int id = throwNow(state);
      Entity entity = player.level().getEntity(id);
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || !Strikes.hit(player, target, ReinhardRules.THROW_DAMAGE)) {
         // Gone, or the hit did not land: no launch (the cooldown stays spent).
         state.action.cancel();
         return;
      }

      Vec3 at = center(target);
      SkyKick.move(target, Vec3.ZERO);
      HeroFx.hitStop(player, at, ReinhardRules.HIT_STOP_TICKS, player.getId(), target.getId());
      HeroFx.flash(player, at);
      ReinhardSounds.playAt(player, at, ReinhardSounds.THROW.get(), 1.6F, 1.0F);
   }

   private static void throwTick(ServerPlayer player, ReinhardState state) {
      Beat beat = beat(state.action.elapsed());
      if (beat == Beat.NONE) {
         return;
      }

      Entity entity = player.level().getEntity(state.action.targetId());
      if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
         state.action.cancel();
         return;
      }

      if (beat == Beat.HOLD) {
         SkyKick.move(target, Vec3.ZERO);
         return;
      }

      if (!HeroRegistry.allowsImpulse(target)) {
         return;
      }

      state.throwVelocity = throwVelocity(player.getXRot(), player.getYRot());
      state.throwTargetId = target.getId();
      state.throwSustain = ReinhardRules.THROW_SUSTAIN_TICKS;
      state.throwSustained = 0;
      target.resetFallDistance();
      HeroFx.sonicRing(player, center(target));
      HeroFx.airTrail(player, target);
      sustainTick(player, state);
   }

   /** The velocity is re-applied every tick until the throw has covered its distance, it hits a block or loses impulses. */
   private static void sustainTick(ServerPlayer player, ReinhardState state) {
      Entity entity = player.level().getEntity(state.throwTargetId);
      boolean blocked = entity instanceof LivingEntity t && state.throwSustained > 1 && (t.horizontalCollision || t.verticalCollision);
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || blocked || !HeroRegistry.allowsImpulse(target) || state.throwVelocity == null) {
         state.throwSustain = 0;
         state.throwTargetId = -1;
         return;
      }

      SkyKick.move(target, state.throwVelocity);
      state.throwSustain--;
      state.throwSustained++;
   }

   static Vec3 center(Entity e) {
      return SkyKick.center(e);
   }
}

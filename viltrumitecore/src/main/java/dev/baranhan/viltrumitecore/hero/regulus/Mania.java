package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mania of Greed (spec 8): a 19-tick windup, then a held-button pull channel
 * (0.6/t toward the caster, up to 120 ticks). A normal end — release or
 * timeout with the target alive — anchors the target frozen for 80 ticks;
 * any interruption ends the pull with no freeze. The cooldown samples the
 * current heart count at every channel end.
 */
public final class Mania {
   private Mania() {
   }

   /**
    * Windup only: the target is chosen at the 19-tick event, never earlier.
    * The lock runs one tick past the event — tickActionLock sweeps the action
    * at elapsed >= length before Mania.tick, so length must exceed the event
    * tick (same convention as Lion/Debris/Embrace/Counter).
    */
   public static void start(ServerPlayer player, RegulusState state) {
      state.beginAction(RegulusHero.ACTION_MANIA, RegulusRules.MANIA_WINDUP_TICKS + 1, RegulusRules.MANIA_WINDUP_TICKS, RegulusRules.MANIA_WINDUP_TICKS);
   }

   public static void tick(ServerPlayer player, RegulusState state) {
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }

      if (shouldGrab(state)) {
         tryGrab(player, level, state);
      }

      if (state.channelTargetId != null) {
         tickChannel(player, level, state);
      }
   }

   /** The grab fires exactly once, on the windup event tick. */
   static boolean shouldGrab(RegulusState state) {
      return RegulusHero.ACTION_MANIA.equals(state.actionId) && !state.eventFired && state.actionElapsed >= state.actionEventTick;
   }

   /**
    * Event: pick the first living target along the look ray within range and
    * open the pull channel. No eligible target cancels the cast for free.
    */
   private static void tryGrab(ServerPlayer player, ServerLevel level, RegulusState state) {
      ControlManager manager = ControlManager.get(level);
      LivingEntity target = pickTarget(player, level, manager);
      state.eventFired = true;
      state.clearAction();
      if (target == null) {
         return;
      }

      UUID effectId = UUID.randomUUID();
      if (!manager.tryAcquire(target, player.getUUID(), effectId, ControlKind.PULL)) {
         return;
      }

      state.channelTargetId = target.getUUID();
      state.channelEffectId = effectId;
      state.channelTicks = 0;
      // Preserve an external slowness before the channel slow overwrites it.
      RegulusPassives.preserveExternalSlowness(player, state);
      applyCasterDebuffs(player);
      level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.2F);
   }

   /**
    * First living entity along the look ray, wall-clipped at the same range —
    * matches Debris Kick's targeting convention. Entities already controlled
    * by any Regulus or latched by a viltrumite grab are not selectable.
    */
   @Nullable
   private static LivingEntity pickTarget(ServerPlayer player, ServerLevel level, ControlManager manager) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 max = eye.add(look.scale(RegulusRules.MANIA_TARGET_RANGE));
      BlockHitResult blockHit = level.clip(new ClipContext(eye, max, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 end = blockHit.getType() == HitResult.Type.MISS ? max : blockHit.getLocation();

      AABB path = new AABB(eye, end).inflate(1.0);
      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;
      for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, path, e -> e != player && e.isAlive() && eligible(e, manager))) {
         AABB box = entity.getBoundingBox().inflate(0.4);
         double distance = DebrisKick.rayDistance(eye, end, box);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = entity;
         }
      }

      return best;
   }

   /** Selection gate: real creatures only, no stacking controls, no grabbed or control-immune targets. */
   static boolean eligible(LivingEntity target, ControlManager manager) {
      // Decoration entities (armor stands) are not «живое существо» targets — the
      // same Mob/Player rule hearts use (spec 5.1).
      if (!(target instanceof Mob) && !(target instanceof Player)) {
         return false;
      }

      if (target.getTags().contains("ViltrumiteGrabbed") || manager.isControlled(target)) {
         return false;
      }

      // Hero policy: a Lion's-Heart-active Regulus refuses the grab outright.
      return HeroRegistry.allowsExternalControl(target, ControlKind.PULL);
   }

   private static void tickChannel(ServerPlayer player, ServerLevel level, RegulusState state) {
      ControlManager manager = ControlManager.get(level);
      net.minecraft.world.entity.Entity entity = state.channelTargetId == null ? null : level.getEntity(state.channelTargetId);
      LivingEntity target = entity instanceof LivingEntity living ? living : null;
      UUID effectId = state.channelEffectId;

      // Interruptions: target died/vanished, caster got controlled or grabbed.
      if (target == null || !target.isAlive()) {
         endChannel(player, state, ReleaseReason.TARGET_LOST);
         return;
      }

      if (manager.isAnchored(player) || player.getTags().contains("ViltrumiteGrabbed")) {
         endChannel(player, state, ReleaseReason.INTERRUPTED);
         return;
      }

      // A grabbed victim hands the pull off to the legacy grab.
      if (target.getTags().contains("ViltrumiteGrabbed")) {
         endChannel(player, state, ReleaseReason.HANDOFF_TO_GRAB);
         return;
      }

      // The viltrumite counterplay: only the fast flight states outrun the pull.
      if (target instanceof Player && target instanceof ViltrumiteFlightPlayer omni && RegulusRules.pullEscapes(omni.getFlightState())) {
         endChannel(player, state, ReleaseReason.INTERRUPTED);
         return;
      }

      if (player.distanceToSqr(target) > RegulusRules.MANIA_TARGET_RANGE * RegulusRules.MANIA_TARGET_RANGE) {
         endChannel(player, state, ReleaseReason.TARGET_LOST);
         return;
      }

      applyPull(player, target);
      applyCasterDebuffs(player);
      // Grounded for the channel: upward motion is suppressed server-side, so
      // vanilla jumps and the passive jump boost cannot lift the caster.
      Vec3 casterDelta = player.getDeltaMovement();
      if (casterDelta.y > 0.0) {
         player.setDeltaMovement(casterDelta.x, 0.0, casterDelta.z);
         player.hasImpulse = true;
         player.connection.send(new ClientboundSetEntityMotionPacket(player));
      }

      state.channelTicks++;
      if (RegulusRules.maniaChannelDone(state.channelTicks)) {
         endChannel(player, state, ReleaseReason.NORMAL_END);
      }
   }

   /** Additive pull toward the caster's chest height, on top of the target's own motion. */
   private static void applyPull(ServerPlayer player, LivingEntity target) {
      // A World Pause suspends the pull without ending or stealing the channel.
      if (dev.baranhan.viltrumitecore.hero.control.WorldPause.blocksMovement(player, target)) {
         return;
      }

      Vec3 toward = player.getEyePosition().subtract(target.getEyePosition());
      double distance = toward.length();
      if (distance < 1.0) {
         return;
      }

      Vec3 step = toward.normalize().scale(RegulusRules.MANIA_PULL_PER_TICK);
      Vec3 delta = target.getDeltaMovement().add(step.x, step.y, step.z);
      target.setDeltaMovement(delta);
      target.hasImpulse = true;
      if (target instanceof ServerPlayer sp) {
         sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
      }
   }

   /** Caster is slowed for the whole channel (spec 8.2); jumping is clamped in the tick. */
   private static void applyCasterDebuffs(ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 1, true, false, true));
   }

   private static void clearCasterDebuffs(ServerPlayer player, RegulusState state, int elapsedTicks) {
      // Strip only this channel's own (ambient) slow and restore whatever
      // remains of a stashed external one — never clobber foreign slowness.
      RegulusPassives.clearOwnSlowness(player, state, elapsedTicks);
   }

   /**
    * One channel end: release or timeout with a live target transitions the
    * PULL into an 80-tick FREEZE; every other reason just releases the pull.
    * The 500-tick cooldown samples hearts at this moment, interrupt or not.
    */
   public static void endChannel(ServerPlayer player, RegulusState state, ReleaseReason reason) {
      UUID targetId = state.channelTargetId;
      UUID effectId = state.channelEffectId;
      int elapsed = state.channelTicks;
      state.channelTargetId = null;
      state.channelEffectId = null;
      state.channelTicks = 0;
      if (targetId == null) {
         return;
      }

      // Only a real channel-end strips the caster debuff — a cleanup with no
      // open channel must not clear an unrelated Slowness (spec 8.2).
      clearCasterDebuffs(player, state, elapsed);

      if (player.level() instanceof ServerLevel level) {
         ControlManager manager = ControlManager.get(level);
         LivingEntity target = level.getEntity(targetId) instanceof LivingEntity living ? living : null;
         boolean freeze = endsAsFreeze(reason) && target != null && target.isAlive();
         boolean held = freeze && effectId != null && manager.transition(targetId, effectId, ControlKind.FREEZE, level.getGameTime() + RegulusRules.MANIA_FREEZE_TICKS);
         if (!held && effectId != null) {
            manager.release(targetId, effectId, freeze ? ReleaseReason.TARGET_LOST : reason);
         }

         if (held && target != null) {
            level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
         }
      }

      state.startCooldown(RegulusAbilities.MANIA, RegulusRules.MANIA_COOLDOWN);
   }

   /** Pure rule: only a normal channel end (release or the 120-tick timeout) freezes. */
   static boolean endsAsFreeze(ReleaseReason reason) {
      return reason == ReleaseReason.NORMAL_END;
   }
}

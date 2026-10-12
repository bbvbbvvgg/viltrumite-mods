package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sky Kick (spec §6.2, Knight slot 2; canon: Regulus, arc 5): no wind-up — a light
 * toss of the target, a short sharp kick on tick {@link ReinhardRules#KICK_TOSS_TICKS},
 * a 3-tick hit-stop on both, then the launch straight up at the motion packet cap
 * (apex 60+ blocks) with a sonic ring and an air trail. A target that refuses impulses
 * (Lion's Heart, anchored) is refused at the press: refusal sound, no cooldown.
 */
public final class SkyKick implements AbilityHandler {
   /** What happens on an elapsed tick of the kick's timeline. */
   public enum Beat {
      NONE,
      TOSS,
      KICK,
      HOLD,
      LAUNCH;
   }

   /** Pure: the beat of an elapsed tick. */
   public static Beat beat(int elapsed) {
      int kick = ReinhardRules.KICK_TOSS_TICKS;
      int launch = kick + ReinhardRules.HIT_STOP_TICKS;
      if (elapsed == 0) {
         return Beat.TOSS;
      }

      if (elapsed == kick) {
         return Beat.KICK;
      }

      if (elapsed > kick && elapsed < launch) {
         return Beat.HOLD;
      }

      return elapsed == launch ? Beat.LAUNCH : Beat.NONE;
   }

   public static Vec3 launchVelocity() {
      return new Vec3(0.0, ReinhardRules.KICK_VELOCITY, 0.0);
   }

   /** Pure: the press with its target facts; false = refused, nothing spent. */
   public static boolean start(ReinhardState state, boolean hasTarget, boolean impulseAllowed, int targetId) {
      if (!hasTarget || !impulseAllowed) {
         return false;
      }

      state.cooldowns[ReinhardAbility.SKY_KICK.ordinal()] = ReinhardRules.KICK_COOLDOWN;
      state.action.start(ReinhardAbility.SKY_KICK, ReinhardRules.KICK_ACTION_TICKS, targetId);
      return true;
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      LivingEntity target = ReinhardTargets.inReach(player, ReinhardRules.KICK_REACH);
      return start(state, target != null, target != null && HeroRegistry.allowsImpulse(target), target == null ? -1 : target.getId());
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      if (!state.action.is(ReinhardAbility.SKY_KICK)) {
         return;
      }

      Beat beat = beat(state.action.elapsed());
      if (beat == Beat.NONE) {
         return;
      }

      Entity entity = player.level().getEntity(state.action.targetId());
      if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
         // The target is gone (death, logout, another dimension): the kick ends.
         state.action.cancel();
         return;
      }

      switch (beat) {
         case TOSS -> {
            // Straight up: the server's copy of a player's horizontal velocity is stale anyway.
            move(target, new Vec3(0.0, ReinhardRules.KICK_TOSS_VELOCITY, 0.0));
            ReinhardSounds.playAt(player, target.position(), ReinhardSounds.KICK_TOSS.get(), 0.9F, 1.2F);
         }
         case KICK -> kick(player, state, target);
         case HOLD -> move(target, Vec3.ZERO);
         case LAUNCH -> {
            if (!HeroRegistry.allowsImpulse(target)) {
               return;
            }

            target.resetFallDistance();
            move(target, launchVelocity());
            HeroFx.sonicRing(player, center(target));
            HeroFx.airTrail(player, target);
         }
         default -> {
         }
      }
   }

   private static void kick(ServerPlayer player, ReinhardState state, LivingEntity target) {
      double reach = ReinhardRules.KICK_REACH + ReinhardRules.KICK_CONTACT_SLACK + target.getBbWidth();
      if (player.distanceToSqr(target) > reach * reach || !Strikes.hit(player, target, ReinhardRules.KICK_DAMAGE)) {
         // Out of reach after the toss, or the hit did not land: no launch.
         state.action.cancel();
         return;
      }

      Vec3 at = center(target);
      move(target, Vec3.ZERO);
      HeroFx.hitStop(player, at, ReinhardRules.HIT_STOP_TICKS, player.getId(), target.getId());
      HeroFx.flash(player, at);
      ReinhardSounds.playAt(player, at, ReinhardSounds.SKY_KICK.get(), 1.6F, 1.8F);
   }

   /** Authoritative motion: players get it at once (their client owns their movement). */
   static void move(LivingEntity target, Vec3 velocity) {
      target.setDeltaMovement(velocity);
      target.hasImpulse = true;
      target.hurtMarked = true;
      if (target instanceof ServerPlayer hitPlayer) {
         hitPlayer.connection.send(new ClientboundSetEntityMotionPacket(hitPlayer));
      }
   }

   static Vec3 center(Entity e) {
      return e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
   }
}

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

/** Second-pass Sky Kick: controlled approach, knee extension, one contact with a 40-degree launch.
 * The contact timeline owns damage and motion; hit-stop is a visual effect, never a second launch. */
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
      return elapsed==0?Beat.TOSS:elapsed==kick?Beat.KICK:Beat.NONE;
   }

   public static Vec3 launchVelocity() { return launchVelocity(new Vec3(0,0,1)); }
   public static Vec3 launchVelocity(Vec3 attack) {
      Vec3 horizontal=new Vec3(attack.x,0,attack.z);
      if(horizontal.lengthSqr()<1E-6) horizontal=new Vec3(0,0,1);
      double angle=Math.toRadians(40),speed=ReinhardRules.KICK_VELOCITY;
      return horizontal.normalize().scale(speed*Math.cos(angle)).add(0,speed*Math.sin(angle),0);
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
      LivingEntity target = ReinhardTargets.inReach(player, 8.0);
      state.kickDirection=player.getLookAngle();
      return start(state, target != null, target != null && HeroRegistry.allowsImpulse(target), target == null ? -1 : target.getId());
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      if (!state.action.is(ReinhardAbility.SKY_KICK)) {
         return;
      }

      Beat beat = beat(state.action.elapsed());
      Entity entity = player.level().getEntity(state.action.targetId());
      if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
         // The target is gone (death, logout, another dimension): the kick ends.
         state.action.cancel();
         return;
      }

      if(state.action.elapsed()<ReinhardRules.KICK_TOSS_TICKS) approach(player,target);
      if(beat==Beat.TOSS) ReinhardSounds.playAt(player,player.position(),ReinhardSounds.KICK_TOSS.get(),.9F,1.2F);
      if(beat==Beat.KICK) kick(player,state,target);
   }

   /** Velocity approach only. Sweep the player's box so no step tunnels through a wall. */
   private static void approach(ServerPlayer player,LivingEntity target) {
      Vec3 delta=target.position().subtract(player.position());
      Vec3 flat=new Vec3(delta.x,0,delta.z);
      double distance=flat.length();
      if(distance<=2.1 || !player.hasLineOfSight(target)) return;
      Vec3 step=flat.normalize().scale(Math.min(.85,(distance-2.1)/2));
      for(int i=1;i<=4;i++) if(!player.level().noCollision(player,player.getBoundingBox().move(step.scale(i/4.0)))) return;
      move(player,new Vec3(step.x,player.getDeltaMovement().y,step.z));
   }

   private static void kick(ServerPlayer player, ReinhardState state, LivingEntity target) {
      double reach = ReinhardRules.KICK_REACH + target.getBbWidth()*.5;
      if (!player.hasLineOfSight(target) || !ReinhardTargets.canHit(player,target) || !HeroRegistry.allowsImpulse(target)
         || player.distanceToSqr(target) > reach * reach || !Strikes.hit(player, target, ReinhardRules.KICK_DAMAGE)) {
         // Out of reach after the toss, or the hit did not land: no launch.
         state.action.cancel();
         return;
      }

      Vec3 at = center(target);
      target.resetFallDistance();
      move(target, launchVelocity(state.kickDirection));
      HeroFx.sonicRing(player,at);
      HeroFx.airTrail(player,target);
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

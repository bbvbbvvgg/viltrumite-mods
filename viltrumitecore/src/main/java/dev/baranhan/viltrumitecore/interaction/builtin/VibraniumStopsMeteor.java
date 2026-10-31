package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionReaction;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTargets;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor (world event: meteor fall / meteor rain) strikes a raised shield. The shield holds: the meteor bursts on
 * it in the air instead of cratering the ground, the defender is driven back along its flight and the guard is fully
 * charged by the impact.
 */
public final class VibraniumStopsMeteor implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("vibranium_stops_meteor", InteractionTrigger.PROJECTILE, 80, 20, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      if (!(ctx.projectile() instanceof MeteorEntity meteor) || !(ctx.target() instanceof ServerPlayer guard) || !guard.isAlive()) {
         return false;
      }
      Vec3 from = meteor.position().subtract(meteor.getDeltaMovement().scale(3.0));
      return HeroRegistry.get(guard).guardFaces(guard, from);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      MeteorEntity meteor = (MeteorEntity)ctx.projectile();
      ServerPlayer guard = (ServerPlayer)ctx.target();
      if (!HeroRegistry.get(guard).react(guard, InteractionReaction.METEOR_STOPPED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = InteractionTargets.guardPoint(guard);
      Vec3 flight = meteor.getDeltaMovement();
      Vec3 push = new Vec3(flight.x, 0.0, flight.z);
      push = push.lengthSqr() < 1.0E-4 ? InteractionTargets.away(meteor.position(), guard.position(), guard.getLookAngle().reverse()) : push.normalize();
      InteractionTargets.push(guard, push.scale(InteractionTuning.METEOR_PUSH).add(0, 0.2, 0));
      HeroFx.preset(ctx.level(), InteractionFx.METEOR_BURST, at, meteor.position(), 1.4F, null);
      InteractionSounds.playAt(guard, at, InteractionSounds.METEOR_BURST, 3.0F, 1.0F);
      MeteorBursts.burst(meteor, guard, at);
      return InteractionOutcome.CONSUMED;
   }
}

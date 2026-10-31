package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ServerEndTasks;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPhase;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirWorld;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown Mjolnir meets a raised vibranium shield. Nothing breaks: the hammer rings off the shield and flies
 * straight back to Thor's hand (his own recall, next tick), the bearer is driven back a step, the thunder ring
 * shoves everyone close (but the two of them and their allies) and the guard keeps the charge of the blow.
 * The melee strike on the shield is {@code mjolnir_meets_vibranium}; this is the thrown hammer only.
 */
public final class MjolnirReboundsOffShield implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("mjolnir_rebounds_off_shield", InteractionTrigger.HIT, 96, 40, 10);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): only the hammer in its thrown flight rebounds (not one returning, resting or in hand). */
   public static boolean inFlight(MjolnirPhase phase) {
      return phase == MjolnirPhase.THROWN;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      if (!(ctx.direct() instanceof MjolnirEntity hammer) || !inFlight(hammer.phase()) || ctx.friendly()) {
         return false;
      }
      ServerPlayer thor = ctx.attackerPlayer();
      ServerPlayer guard = ctx.targetPlayer();
      return thor != null && guard != null && guard != thor && guard.isAlive() && ctx.targetPresence().has(CombatTags.WEAPON_SHIELD)
         && HeroRegistry.get(guard).guardFaces(guard, from(hammer, thor));
   }

   private static Vec3 from(MjolnirEntity hammer, ServerPlayer thor) {
      Vec3 v = hammer.getDeltaMovement();
      return v.lengthSqr() > 1.0E-4 ? hammer.position().subtract(v.scale(3.0)) : thor.position();
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      MjolnirEntity hammer = (MjolnirEntity)ctx.direct();
      ServerPlayer thor = ctx.attackerPlayer();
      ServerPlayer guard = ctx.targetPlayer();
      if (!HeroRegistry.get(guard).react(guard, InteractionReaction.GUARD_BRACED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = InteractionTargets.guardPoint(guard);
      Vec3 back = InteractionTargets.away(from(hammer, thor), guard.position(), guard.getLookAngle().reverse());
      InteractionTargets.push(guard, back.scale(InteractionTuning.REBOUND_PUSH).add(0, 0.15, 0));
      for (LivingEntity e : InteractionTargets.around(guard, at, InteractionTuning.REBOUND_RING_RADIUS)) {
         if (!InteractionTargets.spared(e, guard, thor)) {
            InteractionTargets.push(e, InteractionTargets.away(at, e.position(), back).scale(InteractionTuning.REBOUND_RING_PUSH).add(0, 0.25, 0));
         }
      }
      HeroFx.preset(ctx.level(), InteractionFx.STORM_REBOUND, at, thor.getEyePosition(), 1.0F, null);
      InteractionSounds.playAt(guard, at, InteractionSounds.STORM_REBOUND, 2.4F, 1.0F);
      // The flight ends on the shield: the hammer answers Thor's own recall (outside this hit, on the tick's end).
      ServerEndTasks.enqueue(ctx.level().getServer(), () -> {
         if (thor.isAlive() && !thor.hasDisconnected()) {
            MjolnirWorld.recall(thor);
         }
      });
      return InteractionOutcome.CONSUMED;
   }
}

package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTargets;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Mad Titan does not fight the summoned: a hit of Thanos on a hero summon (Iron Legion suit, Avenger, Jinwoo's
 * shadow) hurls it away with a ground burst instead of the usual short knockback. Damage unchanged.
 */
public final class TitanScattersSummons implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("titan_scatters_summons", InteractionTrigger.HURT, 40, 10, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.attacker() instanceof ThanosEntity && ctx.target() instanceof LivingEntity && SummonOwners.isSummon(ctx.target()) && ctx.amount() > 0.0F;
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      LivingEntity summon = (LivingEntity)ctx.target();
      Vec3 dir = InteractionTargets.away(ctx.attacker().position(), summon.position(), ctx.attacker().getLookAngle());
      if (!InteractionTargets.push(summon, dir.scale(InteractionTuning.TITAN_FLING).add(0, InteractionTuning.TITAN_LIFT, 0))) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = summon.getBoundingBox().getCenter();
      HeroFx.preset(ctx.level(), InteractionFx.TITAN_FLING, summon.position(), ctx.attacker().getEyePosition(), 1.0F, null);
      InteractionSounds.playAt(summon, at, InteractionSounds.TITAN_FLING, 1.6F, 1.0F);
      return InteractionOutcome.scaled(1.0F);
   }
}

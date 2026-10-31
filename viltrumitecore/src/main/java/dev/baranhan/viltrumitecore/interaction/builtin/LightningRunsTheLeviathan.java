package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Lightning into a Leviathan (Thor's lightning, Mjolnir in its striking window, or the summoned Thor's hammer
 * throw and storm). The armoured beast is one long conductor: the bolt runs down its whole spine segment by
 * segment, every Chitauri riding or flying close to the body is shocked, and the Leviathan convulses — staggered for
 * {@link InteractionTuning#SPINE_STAGGER} ticks, out of its dive. The hit itself lands as usual.
 */
public final class LightningRunsTheLeviathan implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("lightning_runs_the_leviathan", InteractionTrigger.HURT, 56, 200, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.amount() > 0.0F && Leviathans.of(ctx.target()) != null && Leviathans.lightning(ctx);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ChitauriLeviathan lev = Leviathans.of(ctx.target());
      List<Vec3> spine = Leviathans.spine(lev);
      for (int i = 1; i < spine.size(); i++) {
         HeroFx.preset(ctx.level(), InteractionFx.SPINE_ARC, spine.get(i - 1), spine.get(i), 1.0F, null);
      }
      DamageSource source = Leviathans.sourceOf(ctx.attacker());
      for (LivingEntity rider : Leviathans.riders(lev, InteractionTuning.SPINE_REACH, InteractionTuning.SPINE_MAX_TARGETS)) {
         if (source != null) {
            rider.invulnerableTime = 0;
            rider.hurt(source, InteractionTuning.SPINE_DAMAGE);
         }
         HeroFx.electricCrackle(ctx.level(), rider.getBoundingBox().getCenter(), 6);
      }
      lev.staggerBy(InteractionTuning.SPINE_STAGGER);
      InteractionSounds.playAt(lev, lev.centre(), InteractionSounds.SPINE_LIGHTNING, 6.0F, 1.0F);
      return InteractionOutcome.scaled(1.0F);
   }
}

package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Two world events meet: a meteor (meteor fall / meteor rain, or one a hero threw) slams into a Chitauri Leviathan
 * in flight. The beast takes the heaviest hit it can take ({@link ChitauriLeviathan#MAX_HIT}), reels for
 * {@link InteractionTuning#METEOR_LEVIATHAN_STAGGER} ticks (climbing out of a dive) and the rock bursts on its
 * plates instead of cratering the ground; the blast throws the riders off. A meteor thrown by a hero is his hit.
 */
public final class MeteorDownsLeviathan implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("meteor_downs_leviathan", InteractionTrigger.PROJECTILE, 72, 20, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.projectile() instanceof MeteorEntity && Leviathans.of(ctx.target()) != null;
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      MeteorEntity meteor = (MeteorEntity)ctx.projectile();
      ChitauriLeviathan lev = Leviathans.of(ctx.target());
      Vec3 at = meteor.position();
      Entity thrower = meteor.getOwner();
      lev.staggerBy(InteractionTuning.METEOR_LEVIATHAN_STAGGER);
      lev.invulnerableTime = 0;
      lev.hurt(ctx.level().damageSources().explosion(meteor, thrower), InteractionTuning.METEOR_LEVIATHAN_DAMAGE);
      HeroFx.preset(ctx.level(), InteractionFx.LEVIATHAN_IMPACT, at, lev.centre(), 1.6F, null);
      InteractionSounds.playAt(lev, at, InteractionSounds.LEVIATHAN_IMPACT, 6.0F, 1.0F);
      MeteorBursts.burst(meteor, InteractionTargets.ownerOf(thrower), at);
      return InteractionOutcome.CONSUMED;
   }
}

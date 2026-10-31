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
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor strikes a hero holding a lightning hammer (Mjolnir in the hand). Lightning answers from the sky and
 * splits the meteor mid-air: no crater, no magma field, the blast only throws the creatures around.
 */
public final class MjolnirShattersMeteor implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("mjolnir_shatters_meteor", InteractionTrigger.PROJECTILE, 70, 20, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      if (!(ctx.projectile() instanceof MeteorEntity) || !(ctx.target() instanceof ServerPlayer thor) || !thor.isAlive()) {
         return false;
      }
      CombatPresence p = ctx.targetPresence();
      return p.has(CombatTags.WEAPON_HAMMER) && p.has(CombatTags.ELEMENT_LIGHTNING);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      MeteorEntity meteor = (MeteorEntity)ctx.projectile();
      ServerPlayer thor = (ServerPlayer)ctx.target();
      HeroRegistry.get(thor).react(thor, InteractionReaction.METEOR_SHATTERED, ctx);
      Vec3 at = meteor.position().add(0, 1.5, 0);
      HeroFx.electricBolt(ctx.level(), at, 40.0F, 1.0F);
      HeroFx.electricArc(ctx.level(), thor.getEyePosition().add(0, 0.6, 0), at);
      HeroFx.preset(ctx.level(), InteractionFx.METEOR_SPLIT, at, thor.getEyePosition(), 1.4F, null);
      InteractionSounds.playAt(thor, at, InteractionSounds.METEOR_SPLIT, 3.0F, 1.0F);
      MeteorBursts.burst(meteor, thor, at);
      return InteractionOutcome.CONSUMED;
   }
}

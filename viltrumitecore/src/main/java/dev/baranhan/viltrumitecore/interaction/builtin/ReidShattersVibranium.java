package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.combat.HeroInteractions;
import dev.baranhan.viltrumitecore.hero.combat.InteractionKind;
import dev.baranhan.viltrumitecore.hero.combat.InteractionRule;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Mandatory combo A: an ordinary stroke of the drawn Reid (a {@code trait.annihilating} blade, ACTIVE) lands on a
 * raised shield (GUARD). The shield takes the blow but its guard breaks: it cannot be raised again for
 * {@link InteractionTuning#SHATTER_TICKS}, its stored kinetic charge is lost, the defender is thrown back and
 * slowed. The shield item stays (no item is destroyed).
 */
public final class ReidShattersVibranium implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("reid_shatters_vibranium", InteractionTrigger.HIT, 100, 40, 10);
   public static final InteractionRule RULE = new InteractionRule("contextual.reid_shatters_vibranium",
      InteractionRule.Side.of(CombatTags.TRAIT_ANNIHILATING, CombatStance.ACTIVE), InteractionRule.Side.of(CombatTags.WEAPON_SHIELD, CombatStance.GUARD),
      InteractionKind.BLOCK, 20);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public void onRegister() {
      HeroInteractions.register(RULE);
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      ServerPlayer d = Duels.meleeDefender(ctx, false);
      return d != null && Duels.directed(RULE, ctx.attackerPresence(), ctx.targetPresence()) && Duels.guardFaces(d, ctx.attackerPlayer());
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ServerPlayer a = ctx.attackerPlayer();
      ServerPlayer d = ctx.targetPlayer();
      Vec3 at = InteractionTargets.guardPoint(d);
      if (!HeroRegistry.get(d).react(d, InteractionReaction.GUARD_SHATTERED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 dir = InteractionTargets.away(a.position(), d.position(), a.getLookAngle());
      InteractionTargets.push(d, dir.scale(InteractionTuning.SHATTER_PUSH).add(0, 0.3, 0));
      d.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, InteractionTuning.SHATTER_SLOW_TICKS, InteractionTuning.SHATTER_SLOW_LEVEL, false, false, true));
      HeroFx.preset(ctx.level(), InteractionFx.SHIELD_SHATTER, at, a.getEyePosition(), 1.0F, null);
      HeroFx.hitStop(a, at, 4, a.getId(), d.getId());
      InteractionSounds.playAt(d, at, InteractionSounds.SHIELD_SHATTER, 1.8F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}

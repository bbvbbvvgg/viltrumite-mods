package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Mandatory combo B: a lightning hammer strike (Mjolnir, ACTIVE) lands on a raised shield (GUARD), between rivals
 * or between allies (friendly contact from the striker's own strike resolution). Neither of the two takes damage
 * from it: the hit is consumed, the rest of the stroke is swallowed by the aftermath. Both are thrown apart; an
 * electric shockwave throws everyone else around and hurts the ones the striker may hurt.
 */
public final class MjolnirMeetsVibranium implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("mjolnir_meets_vibranium", InteractionTrigger.HIT, 100, 40, 14);
   public static final InteractionRule RULE = new InteractionRule("contextual.mjolnir_meets_vibranium",
      InteractionRule.Side.of(CombatTags.ELEMENT_LIGHTNING, CombatStance.ACTIVE), InteractionRule.Side.of(CombatTags.WEAPON_SHIELD, CombatStance.GUARD),
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
      ServerPlayer d = Duels.meleeDefender(ctx, true);
      if (d == null) {
         return false;
      }
      CombatPresence striker = ctx.attackerPresence();
      return striker.has(CombatTags.WEAPON_HAMMER) && Duels.directed(RULE, striker, ctx.targetPresence()) && Duels.guardFaces(d, ctx.attackerPlayer());
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ServerPlayer thor = ctx.attackerPlayer();
      ServerPlayer guard = ctx.targetPlayer();
      if (!HeroRegistry.get(guard).react(guard, InteractionReaction.GUARD_BRACED, ctx)) {
         return InteractionOutcome.PASS;
      }
      HeroRegistry.get(thor).react(thor, InteractionReaction.STRIKE_RECOILED, ctx);
      Vec3 at = InteractionTargets.guardPoint(guard);
      Vec3 dir = InteractionTargets.away(thor.position(), guard.position(), thor.getLookAngle());
      InteractionTargets.push(guard, dir.scale(InteractionTuning.STORM_PUSH).add(0, InteractionTuning.STORM_LIFT, 0));
      InteractionTargets.push(thor, dir.scale(-InteractionTuning.STORM_PUSH).add(0, InteractionTuning.STORM_LIFT, 0));
      shockwave(ctx, thor, guard, at);
      HeroFx.preset(ctx.level(), InteractionFx.STORM_SHIELD, at, thor.getEyePosition(), 1.0F, null);
      HeroFx.electricRing(ctx.level(), guard.position().add(0, 0.1, 0), InteractionTuning.STORM_RADIUS * 0.8);
      HeroFx.electricArc(ctx.level(), thor.getEyePosition(), at);
      HeroFx.hitStop(thor, at, 5, thor.getId(), guard.getId());
      InteractionSounds.playAt(guard, at, InteractionSounds.STORM_SHIELD, 2.2F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }

   /** Everyone but the two (and their summons, pets, team) is thrown; the striker's legal targets are hurt. */
   private static void shockwave(InteractionContext ctx, ServerPlayer thor, ServerPlayer guard, Vec3 at) {
      DamageSource source = new DamageSource(ctx.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.LIGHTNING_BOLT), thor);
      int n = 0;
      for (LivingEntity e : InteractionTargets.around(guard, at, InteractionTuning.STORM_RADIUS)) {
         if (n >= InteractionTuning.STORM_MAX_TARGETS) {
            break;
         }
         if (InteractionTargets.spared(e, thor, guard)) {
            continue;
         }
         n++;
         double dist = Math.sqrt(e.distanceToSqr(at));
         double fall = Math.max(0.25, 1.0 - dist / InteractionTuning.STORM_RADIUS);
         if (InteractionTargets.mayHurt(thor, e)) {
            e.hurt(source, (float)(InteractionTuning.STORM_DAMAGE * fall));
         }
         Vec3 out = InteractionTargets.away(at, e.position(), thor.getLookAngle());
         InteractionTargets.push(e, out.scale(InteractionTuning.STORM_WAVE_PUSH * fall).add(0, 0.45 * fall, 0));
      }
   }
}

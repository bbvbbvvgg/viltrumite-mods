package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
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
import net.minecraft.world.phys.Vec3;

/**
 * A Viltrumite's punch on a raised vibranium shield. The strongest fist in the universe meets the one metal that
 * drinks kinetic energy: the shield takes the blow whole and stores it (the guard's own brace and charge), the
 * Viltrumite's arm is thrown back and he is left stunned for a moment, wide open to the counter.
 */
public final class ViltrumiteFistMeetsVibranium implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("viltrumite_fist_meets_vibranium", InteractionTrigger.HIT, 88, 30, 10);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): a Viltrumite body striking a raised shield. */
   public static boolean pairs(CombatPresence striker, CombatPresence guard) {
      return striker.has(CombatTags.BODY_VILTRUMITE) && guard.has(CombatTags.WEAPON_SHIELD);
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      ServerPlayer fist = ctx.attackerPlayer();
      ServerPlayer guard = ctx.targetPlayer();
      return fist != null && guard != null && fist != guard && guard.isAlive() && fist.isAlive() && ctx.melee() && !ctx.friendly()
         && pairs(ctx.attackerPresence(), ctx.targetPresence()) && HeroRegistry.get(guard).guardFaces(guard, fist.position());
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ServerPlayer fist = ctx.attackerPlayer();
      ServerPlayer guard = ctx.targetPlayer();
      if (!HeroRegistry.get(guard).react(guard, InteractionReaction.GUARD_BRACED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = InteractionTargets.guardPoint(guard);
      if (HeroRegistry.get(fist).react(fist, InteractionReaction.STRIKE_RECOILED, ctx)) {
         Vec3 back = InteractionTargets.away(guard.position(), fist.position(), fist.getLookAngle().reverse());
         InteractionTargets.push(fist, back.scale(InteractionTuning.FIST_RECOIL_PUSH).add(0, 0.25, 0));
         CaptainTargets.stun(guard, fist, InteractionTuning.FIST_RECOIL_STUN);
      }
      HeroFx.preset(ctx.level(), InteractionFx.VIBRANIUM_RING, at, fist.getEyePosition(), 1.0F, null);
      InteractionSounds.playAt(guard, at, InteractionSounds.VIBRANIUM_RING, 2.2F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}

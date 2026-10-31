package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.DivineTargets;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionReaction;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Reid against Lion's Heart: the sword that cuts what cannot be cut meets a body whose own time is stopped. The
 * stroke still does not wound — the authority holds — but it is not shrugged off either: the stopped heart takes
 * the strain of it ({@link dev.baranhan.viltrumitecore.interaction.InteractionTuning#REID_STRAIN}). Two or three
 * strokes and the heart gives out: the Lion fails, Regulus seizes and is locked out of it. The usual recoil of
 * Lion's Heart does not answer this stroke.
 */
public final class ReidStrainsLionsHeart implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("reid_strains_lions_heart", InteractionTrigger.HIT, 99, 100, 14);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): a Reid stroke in its striking window, not a drawn idle sword or a fist. */
   public static boolean strikes(CombatPresence striker) {
      return striker.has(CombatTags.WEAPON_SWORD) && striker.has(CombatTags.TRAIT_ANNIHILATING) && striker.stance() == CombatStance.ACTIVE;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      ServerPlayer reinhard = ctx.attackerPlayer();
      ServerPlayer stopped = ctx.targetPlayer();
      return reinhard != null && stopped != null && stopped != reinhard && stopped.isAlive() && ctx.melee() && !ctx.friendly()
         && strikes(ctx.attackerPresence()) && DivineTargets.isAbsolutelyInvulnerable(stopped);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ServerPlayer stopped = ctx.targetPlayer();
      if (!HeroRegistry.get(stopped).react(stopped, InteractionReaction.LION_STRAINED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = stopped.getBoundingBox().getCenter().add(0.0, 0.3, 0.0);
      HeroFx.preset(ctx.level(), InteractionFx.LION_STRAIN, at, ctx.attacker().getEyePosition(), 1.0F, null);
      InteractionSounds.playAt(stopped, at, InteractionSounds.LION_STRAIN, 1.8F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}

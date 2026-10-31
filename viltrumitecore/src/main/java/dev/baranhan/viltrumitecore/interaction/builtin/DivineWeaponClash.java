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
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.combat.HeroInteractions;
import dev.baranhan.viltrumitecore.hero.combat.InteractionKind;
import dev.baranhan.viltrumitecore.hero.combat.InteractionRule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Two divine weapons meet mid-strike: a world-severing blade (ACTIVE) and a lightning hammer (ACTIVE), in either
 * direction. Neither blow lands: both strikers are thrown back, the screen holds the clash for a few frames.
 */
public final class DivineWeaponClash implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("divine_weapon_clash", InteractionTrigger.HIT, 90, 30, 10);
   public static final InteractionRule RULE = new InteractionRule("contextual.divine_weapon_clash",
      InteractionRule.Side.of(CombatTags.TRAIT_ANNIHILATING, CombatStance.ACTIVE), InteractionRule.Side.of(CombatTags.ELEMENT_LIGHTNING, CombatStance.ACTIVE),
      InteractionKind.CLASH, 20);

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
      if (Duels.meleeDefender(ctx, false) == null) {
         return false;
      }
      CombatPresence a = ctx.attackerPresence();
      CombatPresence b = ctx.targetPresence();
      boolean hammer = a.has(CombatTags.WEAPON_HAMMER) || b.has(CombatTags.WEAPON_HAMMER);
      return hammer && (Duels.directed(RULE, a, b) || Duels.directed(RULE, b, a));
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ServerPlayer a = ctx.attackerPlayer();
      ServerPlayer b = ctx.targetPlayer();
      Vec3 mid = a.getEyePosition().add(b.getEyePosition()).scale(0.5).add(0, -0.3, 0);
      Vec3 dir = InteractionTargets.away(a.position(), b.position(), a.getLookAngle());
      InteractionTargets.push(b, dir.scale(InteractionTuning.CLASH_PUSH).add(0, 0.3, 0));
      InteractionTargets.push(a, dir.scale(-InteractionTuning.CLASH_PUSH).add(0, 0.3, 0));
      HeroRegistry.get(a).react(a, InteractionReaction.CLASH_RECOIL, ctx);
      HeroRegistry.get(b).react(b, InteractionReaction.CLASH_RECOIL, ctx);
      HeroFx.preset(ctx.level(), InteractionFx.DIVINE_CLASH, mid, a.getEyePosition(), 1.0F, null);
      HeroFx.electricArc(ctx.level(), a.getEyePosition(), b.getEyePosition());
      HeroFx.hitStop(a, mid, InteractionTuning.CLASH_HIT_STOP, a.getId(), b.getId());
      InteractionSounds.playAt(a, mid, InteractionSounds.DIVINE_CLASH, 2.0F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}

package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ghostrider.Hellfire;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossEntity;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionReaction;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote fears fire, and hellfire most of all: a hellfire hit (the Ghost Rider's burn, chain or fist in Rider
 * form, a Red Hulk at heat {@code HOT} or more) on a symbiote body (a player Venom in form, the Venom boss) deals
 * {@link VenomRules#SCALD_DAMAGE} times the damage, stops the regeneration for {@link VenomRules#SCALD_REGEN_BLOCK}
 * ticks and makes the symbiote shriek and recoil (its own recoil rules: not while reeling, transforming or riding a
 * host). Tag rules only: {@link CombatTags#ELEMENT_HELLFIRE} on the attacker, {@link CombatTags#BODY_SYMBIOTE} on the
 * target; the player's side is played by {@code react(SYMBIOTE_SCALDED)}.
 */
public final class HellfireScaldsSymbiote implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("hellfire_scalds_symbiote", InteractionTrigger.HURT, 45, 60, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      if (ctx.amount() <= 0.0F || ctx.attacker() == ctx.target()) {
         return false;
      }
      boolean symbiote = ctx.target() instanceof VenomBossEntity || ctx.targetPresence().has(CombatTags.BODY_SYMBIOTE);
      if (!symbiote) {
         return false;
      }
      boolean hellfire = ctx.source() != null && ctx.source().is(Hellfire.HELLFIRE);
      return hellfire || ctx.attackerPresence().has(CombatTags.ELEMENT_HELLFIRE);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      Entity target = ctx.target();
      if (target instanceof VenomBossEntity boss) {
         boss.scald();
      } else {
         ServerPlayer venom = ctx.targetPlayer();
         if (venom == null || !HeroRegistry.get(venom).react(venom, InteractionReaction.SYMBIOTE_SCALDED, ctx)) {
            return InteractionOutcome.PASS;
         }
      }
      Vec3 at = target.getBoundingBox().getCenter();
      Vec3 from = ctx.attacker() != null ? ctx.attacker().getEyePosition() : at.add(0.0, 1.0, 0.0);
      HeroFx.preset(ctx.level(), InteractionFx.SYMBIOTE_SCALD, at, from, 1.0F, null);
      return InteractionOutcome.scaled(VenomRules.SCALD_DAMAGE);
   }
}

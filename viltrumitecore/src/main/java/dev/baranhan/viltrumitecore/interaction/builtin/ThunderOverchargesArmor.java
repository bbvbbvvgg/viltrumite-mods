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
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.phys.Vec3;

/**
 * Lightning with an attacker (Mjolnir's electric strikes, Thor's storms and called bolts) strikes powered armour with a reactor. The
 * suit soaks the bolt: the hit is absorbed and the reactor gains {@link InteractionTuning#OVERCHARGE_ENERGY}. Once
 * per {@code cooldownTicks} per pair, so lightning still hurts the armour between charges.
 */
public final class ThunderOverchargesArmor implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("thunder_overcharges_armor", InteractionTrigger.HIT, 60, 60, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.source() != null && ctx.source().is(DamageTypeTags.IS_LIGHTNING) && ctx.target() instanceof ServerPlayer armour && armour.isAlive()
         && ctx.targetPresence().has(CombatTags.ARMOR_POWERED);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ServerPlayer armour = (ServerPlayer)ctx.target();
      if (!HeroRegistry.get(armour).react(armour, InteractionReaction.OVERCHARGED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = armour.getBoundingBox().getCenter();
      HeroFx.preset(ctx.level(), InteractionFx.OVERCHARGE, at, ctx.attacker() == null ? null : ctx.attacker().getEyePosition(), 1.0F, null);
      HeroFx.electricCrackle(ctx.level(), at, 10);
      InteractionSounds.playAt(armour, at, InteractionSounds.OVERCHARGE, 1.6F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}

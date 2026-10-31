package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.ContextualInteractions;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;

/**
 * "Battle of New York" protocol: while the Chitauri invasion runs, every hit of an Iron Legion suit (repulsor,
 * missiles) on a Chitauri creature deals {@link InteractionTuning#LEGION_CHITAURI_FACTOR} times its damage; the
 * suits were built for this war. A targeting arc marks the hit at most once a second per suit.
 */
public final class LegionVsChitauri implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("legion_vs_chitauri", InteractionTrigger.HURT, 50, 0, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.attacker() instanceof LegionSuitEntity && ctx.target() instanceof ChitauriMob && ctx.amount() > 0.0F
         && ChitauriInvasion.running(ctx.level().getServer());
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      if (ContextualInteractions.guard().throttle(SPEC.id() + "/fx/" + ctx.attacker().getId(), ctx.now(), InteractionTuning.LEGION_FX_THROTTLE)) {
         HeroFx.preset(ctx.level(), InteractionFx.LEGION_MARK, ctx.target().getBoundingBox().getCenter(), ctx.attacker().getEyePosition(), 1.0F, null);
      }
      return InteractionOutcome.scaled(InteractionTuning.LEGION_CHITAURI_FACTOR);
   }
}

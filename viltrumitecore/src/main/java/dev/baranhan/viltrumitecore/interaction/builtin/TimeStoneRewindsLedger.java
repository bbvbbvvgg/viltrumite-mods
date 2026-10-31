package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.dio.DioTimeStop;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The World against the Time Stone. DIO can stop Thanos like anyone else, and every blow of the stopped seconds
 * goes into the ledger — but when time moves again and the ledger is paid, the Time Stone on the Gauntlet winds
 * those seconds back: the whole payout never lands (not one hit of it) and the rewound seconds throw DIO back.
 * A hit DIO lands in moving time is an ordinary hit.
 */
public final class TimeStoneRewindsLedger implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("time_stone_rewinds_ledger", InteractionTrigger.HIT, 84, 100, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): only the ledger payout on a Thanos still in the fight. */
   public static boolean rewinds(boolean paying, boolean thanos, boolean defeated) {
      return paying && thanos && !defeated;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.attackerPlayer() != null && ctx.target().isAlive()
         && rewinds(DioTimeStop.paying(), ctx.target() instanceof ThanosEntity, ctx.target() instanceof ThanosEntity t && t.defeated());
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ThanosEntity thanos = (ThanosEntity)ctx.target();
      ServerPlayer dio = ctx.attackerPlayer();
      Vec3 at = thanos.getBoundingBox().getCenter();
      HeroFx.preset(ctx.level(), InteractionFx.TIME_REWIND, at, dio.getEyePosition(), 1.4F, null);
      InteractionSounds.playAt(thanos, at, InteractionSounds.TIME_REWIND, 3.0F, 1.0F);
      if (dio.distanceToSqr(thanos) <= InteractionTuning.REWIND_RANGE * InteractionTuning.REWIND_RANGE) {
         Vec3 away = InteractionTargets.away(thanos.position(), dio.position(), dio.getLookAngle().reverse());
         InteractionTargets.push(dio, away.scale(InteractionTuning.REWIND_PUSH).add(0, 0.3, 0));
      }
      return InteractionOutcome.CONSUMED;
   }
}

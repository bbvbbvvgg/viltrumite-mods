package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
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
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * "I can do this all day." Thanos's melee blow (cleave, hammer fist, Power punch) on a raised vibranium shield:
 * the shield takes the whole blow, but the guard is crushed — broken for
 * {@link InteractionTuning#SHATTER_TICKS} like under Reid — and the bearer is thrown back across the field. He
 * gets up and answers; his next block has to wait. Thanos's area and ranged moves are ordinary hits.
 */
public final class TitanBreaksGuard implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("titan_breaks_guard", InteractionTrigger.HIT, 76, 100, 10);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): only Thanos's melee moves crush a guard. */
   public static boolean crushing(@Nullable ThanosMove move) {
      return move != null && move.kind == ThanosMove.Kind.MELEE;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      if (!(ctx.attacker() instanceof ThanosEntity thanos) || !ctx.melee() || !crushing(thanos.move())) {
         return false;
      }
      ServerPlayer guard = ctx.targetPlayer();
      return guard != null && guard.isAlive() && ctx.targetPresence().has(CombatTags.WEAPON_SHIELD)
         && HeroRegistry.get(guard).guardFaces(guard, thanos.position());
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ThanosEntity thanos = (ThanosEntity)ctx.attacker();
      ServerPlayer guard = ctx.targetPlayer();
      if (!HeroRegistry.get(guard).react(guard, InteractionReaction.GUARD_CRUSHED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = InteractionTargets.guardPoint(guard);
      Vec3 away = InteractionTargets.away(thanos.position(), guard.position(), guard.getLookAngle().reverse());
      InteractionTargets.push(guard, away.scale(InteractionTuning.CRUSH_PUSH).add(0, InteractionTuning.CRUSH_LIFT, 0));
      HeroFx.preset(ctx.level(), InteractionFx.SHIELD_CRUSH, at, thanos.getEyePosition(), 1.2F, null);
      InteractionSounds.playAt(guard, at, InteractionSounds.SHIELD_CRUSH, 2.6F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}

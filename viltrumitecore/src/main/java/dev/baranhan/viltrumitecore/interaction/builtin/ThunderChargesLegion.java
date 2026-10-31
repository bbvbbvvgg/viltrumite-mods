package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Thor's lightning on an Iron Legion suit (the drone cousin of {@code thunder_overcharges_armor}). The suit does
 * not burn: it soaks the bolt and its repulsor nodes throw it on — up to {@link InteractionTuning#DISCHARGE_TARGETS}
 * arcs into the enemies of the suit's commander around it (monsters; players only as PvP rivals of the
 * commander). Without a commander online the suit only soaks the charge.
 */
public final class ThunderChargesLegion implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("thunder_charges_legion", InteractionTrigger.HIT, 59, 100, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.target() instanceof LegionSuitEntity suit && suit.isAlive() && Leviathans.lightning(ctx);
   }

   /** Pure rule (tests): who a discharge may strike — only an enemy of a known commander. */
   public static boolean dischargeable(boolean commanderKnown, boolean mayHurt, boolean monsterOrPlayer) {
      return commanderKnown && mayHurt && monsterOrPlayer;
   }

   static List<LivingEntity> discharge(LegionSuitEntity suit, @Nullable ServerPlayer commander, Vec3 at) {
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : InteractionTargets.around(suit, at, InteractionTuning.DISCHARGE_RANGE)) {
         if (out.size() >= InteractionTuning.DISCHARGE_TARGETS) {
            break;
         }
         if (e == suit || e instanceof LegionSuitEntity) {
            continue;
         }
         if (dischargeable(commander != null, commander != null && InteractionTargets.mayHurt(commander, e), e instanceof Enemy || e instanceof Player)
            && suit.hasLineOfSight(e)) {
            out.add(e);
         }
      }
      return out;
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      LegionSuitEntity suit = (LegionSuitEntity)ctx.target();
      ServerPlayer commander = suit.owner();
      Vec3 at = suit.getBoundingBox().getCenter();
      HeroFx.preset(ctx.level(), InteractionFx.OVERCHARGE, at, ctx.attacker() == null ? null : ctx.attacker().getEyePosition(), 0.8F, null);
      for (LivingEntity e : discharge(suit, commander, at)) {
         e.invulnerableTime = 0;
         e.hurt(suit.damageSources().mobAttack(suit), InteractionTuning.DISCHARGE_DAMAGE);
         HeroFx.preset(ctx.level(), InteractionFx.LEGION_DISCHARGE, at, e.getBoundingBox().getCenter(), 1.0F, null);
      }
      InteractionSounds.playAt(suit, at, InteractionSounds.LEGION_DISCHARGE, 1.8F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}

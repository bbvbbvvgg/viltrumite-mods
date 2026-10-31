package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.RepulsorBlastEntity;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A repulsor bolt (Iron Man or an Iron Legion suit) on a raised vibranium shield. The shield does not simply send
 * it back: the bolt splits on the curved face into {@link InteractionTuning#SPLIT_SHARDS} shards that fan out along
 * the bearer's aim and each strike one enemy in that cone (the shooter first, if he is one). Shards never strike
 * the bearer's allies, summons or a player PvP spares — an ally's stray bolt becomes a weapon of the team. The
 * guard counts it as a blocked projectile (charge, parry window).
 */
public final class RepulsorSplitsOnVibranium implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("repulsor_splits_on_vibranium", InteractionTrigger.PROJECTILE, 86, 10, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      if (!(ctx.projectile() instanceof RepulsorBlastEntity bolt) || !(ctx.target() instanceof ServerPlayer guard) || !guard.isAlive()
         || bolt.getOwner() == guard || !ctx.targetPresence().has(CombatTags.WEAPON_SHIELD)) {
         return false;
      }
      return HeroRegistry.get(guard).guardFaces(guard, bolt.position().subtract(bolt.getDeltaMovement().scale(3.0)));
   }

   /** Pure rule (tests): is the direction to a target inside the shard cone around the aim? */
   public static boolean inCone(Vec3 aim, Vec3 toTarget, double cos) {
      if (toTarget.lengthSqr() < 1.0E-6 || aim.lengthSqr() < 1.0E-6) {
         return false;
      }
      return aim.normalize().dot(toTarget.normalize()) >= cos;
   }

   /** Pure rule (tests): damage of one shard. */
   public static float shardDamage(float bolt) {
      return Math.max(InteractionTuning.SPLIT_MIN_DAMAGE, bolt * InteractionTuning.SPLIT_SHARE);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      RepulsorBlastEntity bolt = (RepulsorBlastEntity)ctx.projectile();
      ServerPlayer guard = (ServerPlayer)ctx.target();
      if (!HeroRegistry.get(guard).react(guard, InteractionReaction.GUARD_DEFLECTED, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 at = InteractionTargets.guardPoint(guard);
      Vec3 aim = guard.getLookAngle();
      Entity shooter = bolt.getOwner();
      List<LivingEntity> targets = new ArrayList<>();
      if (shooter instanceof LivingEntity s && eligible(guard, s, at, aim)) {
         targets.add(s);
      }
      for (LivingEntity e : InteractionTargets.around(guard, at, InteractionTuning.SPLIT_RANGE)) {
         if (targets.size() >= InteractionTuning.SPLIT_SHARDS) {
            break;
         }
         if (e != shooter && eligible(guard, e, at, aim)) {
            targets.add(e);
         }
      }
      float damage = shardDamage(bolt.damage());
      for (LivingEntity e : targets) {
         e.invulnerableTime = 0;
         e.hurt(guard.damageSources().mobProjectile(bolt, guard), damage);
         HeroFx.preset(ctx.level(), InteractionFx.REPULSOR_SHARD, at, e.getBoundingBox().getCenter(), 1.0F, null);
      }
      // Shards that found nobody still leave the shield: they fan out into the air.
      for (int i = targets.size(); i < InteractionTuning.SPLIT_SHARDS; i++) {
         double yaw = Math.toRadians((i - 1) * 24.0);
         Vec3 dir = aim.yRot((float)yaw).add(0, 0.08 * i, 0).normalize();
         HeroFx.preset(ctx.level(), InteractionFx.REPULSOR_SHARD, at, at.add(dir.scale(10.0)), 1.0F, null);
      }
      HeroFx.preset(ctx.level(), InteractionFx.REPULSOR_SPLIT, at, bolt.position(), 1.0F, null);
      InteractionSounds.playAt(guard, at, InteractionSounds.REPULSOR_SPLIT, 1.8F, 1.0F);
      bolt.discard();
      return InteractionOutcome.CONSUMED;
   }

   private static boolean eligible(ServerPlayer guard, LivingEntity e, Vec3 at, Vec3 aim) {
      return e != guard && e.isAlive() && InteractionTargets.mayHurt(guard, e) && e.distanceToSqr(at) <= InteractionTuning.SPLIT_RANGE * InteractionTuning.SPLIT_RANGE
         && inCone(aim, e.getBoundingBox().getCenter().subtract(at), InteractionTuning.SPLIT_CONE_COS) && guard.hasLineOfSight(e);
   }
}

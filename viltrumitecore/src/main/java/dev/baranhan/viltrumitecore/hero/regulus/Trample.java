package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * «Попрание» (spec §5.5): he lifts a foot, bored, and puts it down. The ground under a stopped sole
 * cannot give, so the blow runs out of it as a ring of shock over the ground: everything standing
 * in its way is struck and thrown up, soft ground at his feet caves in. In the air he stamps on the
 * stopped air itself (no crater). Counterplay: the ring hugs the ground (jump over it) and walls
 * stop it (line of sight from the centre). Lion's Heart widens and hardens it.
 */
public final class Trample {
   private Trample() {
   }

   static void press(ServerPlayer player, RegulusState state) {
      if (state.trampleCooldown > 0 || state.busy()) {
         return;
      }

      state.startMove(RegulusMove.TRAMPLE);
      state.trampleCooldown = RegulusRules.TRAMPLE_COOLDOWN;
      state.trampleAir = !player.onGround();
      state.trampleLion = state.lion;
      state.trampleCenter = null;
      state.trampleRadius = 0.0;
      state.trampleHit.clear();
      RegulusSounds.play(player.level(), player.position().add(0.0, 0.3, 0.0), RegulusSounds.TRAMPLE_LIFT, 0.7F, 0.95F + player.getRandom().nextFloat() * 0.1F);
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      if (state.move != RegulusMove.TRAMPLE) {
         return;
      }

      if (state.trampleAir && state.moveTick <= RegulusRules.TRAMPLE_IMPACT + 2) {
         // he stands on stopped air while he stamps
         Vec3 v = player.getDeltaMovement();
         player.setDeltaMovement(v.x * 0.5, 0.0, v.z * 0.5);
         player.fallDistance = 0.0F;
         player.hurtMarked = true;
      }

      if (state.moveTick == RegulusRules.TRAMPLE_IMPACT) {
         impact(player, state);
      }

      if (state.trampleCenter != null && state.moveTick > RegulusRules.TRAMPLE_IMPACT) {
         wave(player, state);
      }
   }

   static void impact(ServerPlayer player, RegulusState state) {
      ServerLevel level = (ServerLevel) player.level();
      Vec3 center = player.position();
      state.trampleCenter = center;
      state.trampleRadius = RegulusRules.TRAMPLE_START_RADIUS;
      boolean lion = state.trampleLion || state.lion;
      double max = RegulusRules.trampleRadius(lion);
      BlockPos below = BlockPos.containing(center.x, center.y - 0.2, center.z);
      BlockState ground = level.getBlockState(below);
      int dust = state.trampleAir || ground.isAir() ? 0 : Block.getId(ground);
      RegulusSounds.play(level, center, RegulusSounds.TRAMPLE_STOMP, 1.2F, (lion ? 0.85F : 1.0F) + level.random.nextFloat() * 0.08F);
      // dir = (air flag, ring speed, 0), power = full radius, extra = block state id of the struck ground (dust colour)
      RegulusNet.fx(level, RegulusFxS2CPacket.TRAMPLE_IMPACT, player, -1, center, new Vec3(state.trampleAir ? 1.0 : 0.0, RegulusRules.TRAMPLE_SPEED, 0.0),
         (float) max, dust);
      if (!state.trampleAir) {
         crater(player, level, below, lion);
      }
   }

   /** Soft ground at his feet caves in: a disc of the surface layer, a smaller one below it. */
   static void crater(ServerPlayer player, ServerLevel level, BlockPos below, boolean lion) {
      double r = RegulusRules.trampleCraterRadius(lion);
      float hardness = RegulusRules.trampleHardness(lion);
      int budget = RegulusRules.trampleBlockBudget(lion);
      int reach = (int) Math.ceil(r);
      for (int dy = 0; dy >= -1 && budget > 0; dy--) {
         double layer = dy == 0 ? r : r * 0.55;
         for (int dx = -reach; dx <= reach && budget > 0; dx++) {
            for (int dz = -reach; dz <= reach && budget > 0; dz++) {
               if (dx * dx + dz * dz > layer * layer) {
                  continue;
               }

               BlockPos pos = below.offset(dx, dy, dz);
               if (!level.getBlockState(pos).isAir() && RegulusCombat.breakBlock(player, level, pos, hardness)) {
                  budget--;
               }
            }
         }
      }
   }

   static void wave(ServerPlayer player, RegulusState state) {
      double max = RegulusRules.trampleRadius(state.trampleLion || state.lion);
      double from = state.trampleRadius;
      if (from >= max) {
         return;
      }

      double to = Math.min(max, from + RegulusRules.TRAMPLE_SPEED);
      state.trampleRadius = to;
      ServerLevel level = (ServerLevel) player.level();
      Vec3 c = state.trampleCenter;
      double below = state.trampleAir ? RegulusRules.TRAMPLE_AIR_BAND : RegulusRules.TRAMPLE_BAND_BELOW;
      double above = state.trampleAir ? RegulusRules.TRAMPLE_AIR_BAND : RegulusRules.TRAMPLE_BAND_ABOVE;
      AABB box = new AABB(c.x - to - 1.0, c.y - below - 0.5, c.z - to - 1.0, c.x + to + 1.0, c.y + above + 0.5, c.z + to + 1.0);
      boolean lion = state.trampleLion || state.lion;
      for (Entity entity : level.getEntities(player, box)) {
         if (!RegulusCombat.hittable(player, entity, state) || state.trampleHit.contains(entity.getUUID())) {
            continue;
         }

         double dx = entity.getX() - c.x;
         double dz = entity.getZ() - c.z;
         double d = Math.sqrt(dx * dx + dz * dz);
         double feet = entity.getY() - c.y;
         if (!RegulusRules.trampleCatches(d, feet, from, to, below, above)) {
            continue;
         }

         if (!RegulusCombat.sees(player, c.add(0.0, 0.4, 0.0), entity)) {
            continue; // walls stop the ring
         }

         state.trampleHit.add(entity.getUUID());
         LivingEntity target = (LivingEntity) entity;
         Vec3 out = d < 1.0E-3 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : new Vec3(dx / d, 0.0, dz / d);
         target.invulnerableTime = 0;
         if (RegulusCombat.hit(player, target, RegulusRules.trampleDamage(lion))) {
            RegulusCombat.push(target, out, RegulusRules.TRAMPLE_PUSH, RegulusRules.TRAMPLE_LIFT);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RegulusRules.TRAMPLE_SLOW_TICKS, 1), player);
            Vec3 at = target.position().add(0.0, 0.2, 0.0);
            RegulusSounds.play(level, at, RegulusSounds.TRAMPLE_WAVE, 0.9F, 0.9F + level.random.nextFloat() * 0.2F);
            RegulusNet.fx(level, RegulusFxS2CPacket.TRAMPLE_HIT, player, target.getId(), at, out, 1.0F, 0);
         }
      }
   }
}

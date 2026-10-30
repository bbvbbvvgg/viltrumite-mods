package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * «Рассечение воздуха» (spec §5.4): a sideways sweep of the hand stops a sheet of air and sends it
 * out as a widening blade (3 → 9 blocks over 30); crouching cuts vertically. Server-swept volume
 * each tick; blocks up to hardness 3 inside the sheet are cut within a 48-block budget.
 */
public final class AirSeverance {
   private AirSeverance() {
   }

   public static final class Blade {
      final Vec3 origin;
      final Vec3 forward;
      final Vec3 side;
      final Vec3 up;
      final boolean vertical;
      final float damage;
      double travelled;
      int budget = RegulusRules.SEVER_BLOCK_BUDGET;
      final Set<UUID> hit = new HashSet<>();

      Blade(Vec3 origin, Vec3 forward, boolean vertical, float damage) {
         this.origin = origin;
         this.forward = forward.normalize();
         Vec3 s = this.forward.cross(new Vec3(0.0, 1.0, 0.0));
         this.side = s.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : s.normalize();
         this.up = this.side.cross(this.forward).normalize();
         this.vertical = vertical;
         this.damage = damage;
      }

      /** Half extents across the long and the thin axis at a travelled distance. */
      double halfLong(double travelled) {
         return RegulusRules.severWidth(travelled) * 0.5;
      }
   }

   static void press(ServerPlayer player, RegulusState state) {
      if (state.severCooldown > 0 || state.busy()) {
         return;
      }

      state.startMove(player.isCrouching() ? RegulusMove.AIR_CUT_VERTICAL : RegulusMove.AIR_CUT);
      state.severCooldown = RegulusRules.SEVER_COOLDOWN;
      RegulusSounds.play(player.level(), player.position().add(0.0, 1.2, 0.0), RegulusSounds.SEVER_CHARGE, 0.8F, 1.0F);
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      RegulusMove move = state.move;
      if ((move == RegulusMove.AIR_CUT || move == RegulusMove.AIR_CUT_VERTICAL) && state.moveTick == RegulusRules.SEVER_RELEASE) {
         boolean vertical = move == RegulusMove.AIR_CUT_VERTICAL;
         Vec3 look = player.getLookAngle();
         Vec3 origin = player.getEyePosition().add(0.0, -0.35, 0.0).add(look.scale(0.8));
         Blade blade = new Blade(origin, look, vertical, RegulusRules.severDamage(state.lion));
         state.blades.add(blade);
         RegulusSounds.play(player.level(), origin, RegulusSounds.SEVER_RELEASE, 1.2F, 0.95F + player.getRandom().nextFloat() * 0.1F);
         RegulusNet.fx((ServerLevel) player.level(), RegulusFxS2CPacket.SEVER_SHOT, player, -1, origin, look, 0.0F, vertical ? 1 : 0);
      }
   }

   static void tick(ServerPlayer player, RegulusState state) {
      if (state.blades.isEmpty()) {
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      Iterator<Blade> it = state.blades.iterator();
      while (it.hasNext()) {
         Blade blade = it.next();
         Vec3 front = blade.origin.add(blade.forward.scale(blade.travelled));
         if (RegulusCombat.timeStoppedAt(level, front)) {
            continue;
         }

         double d0 = blade.travelled;
         double d1 = Math.min(RegulusRules.SEVER_RANGE, d0 + RegulusRules.SEVER_SPEED);
         sweep(player, state, level, blade, d0, d1);
         blade.travelled = d1;
         if (d1 >= RegulusRules.SEVER_RANGE) {
            it.remove();
         }
      }
   }

   private static void sweep(ServerPlayer player, RegulusState state, ServerLevel level, Blade blade, double d0, double d1) {
      double halfLong = blade.halfLong(d1);
      double halfThin = RegulusRules.SEVER_THICKNESS * 0.5 + 0.2;
      Vec3 longAxis = blade.vertical ? blade.up : blade.side;
      Vec3 thinAxis = blade.vertical ? blade.side : blade.up;
      Vec3 a = blade.origin.add(blade.forward.scale(d0));
      Vec3 b = blade.origin.add(blade.forward.scale(d1));
      AABB bounds = new AABB(a, b).inflate(halfLong + 1.0);
      for (Entity entity : level.getEntities(player, bounds)) {
         if (blade.hit.contains(entity.getUUID()) || !RegulusCombat.hittable(player, entity, state)) {
            continue;
         }

         if (!overlaps(entity.getBoundingBox(), blade.origin, blade.forward, longAxis, thinAxis, d0 - 0.3, d1, halfLong, halfThin)) {
            continue;
         }

         blade.hit.add(entity.getUUID());
         LivingEntity target = (LivingEntity) entity;
         target.invulnerableTime = 0;
         if (RegulusCombat.hit(player, target, blade.damage)) {
            RegulusCombat.push(target, blade.forward, 0.7, 0.2);
            Vec3 at = target.getBoundingBox().getCenter();
            RegulusSounds.play(level, at, RegulusSounds.SEVER_CUT, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            RegulusNet.fx(level, RegulusFxS2CPacket.SEVER_CUT, player, target.getId(), at, blade.forward, blade.damage, blade.vertical ? 1 : 0);
         }
      }

      if (blade.budget <= 0) {
         return;
      }

      Set<BlockPos> cells = new LinkedHashSet<>();
      for (double d = d0 + 0.5; d <= d1 + 1.0E-6; d += 0.5) {
         double half = blade.halfLong(d);
         for (double w = -half; w <= half + 1.0E-6; w += 0.5) {
            cells.add(BlockPos.containing(blade.origin.add(blade.forward.scale(d)).add(longAxis.scale(w))));
         }
      }

      for (BlockPos pos : cells) {
         if (blade.budget <= 0) {
            break;
         }

         if (!level.getBlockState(pos).isAir() && RegulusCombat.breakBlock(player, level, pos, RegulusRules.SEVER_BLOCK_HARDNESS)) {
            blade.budget--;
         }
      }
   }

   /** Oriented slab test: the box's projections overlap the blade's swept extents on all three axes (pure). */
   static boolean overlaps(AABB box, Vec3 origin, Vec3 forward, Vec3 longAxis, Vec3 thinAxis, double d0, double d1, double halfLong, double halfThin) {
      double[] f = project(box, origin, forward);
      double[] l = project(box, origin, longAxis);
      double[] t = project(box, origin, thinAxis);
      return f[1] >= d0 && f[0] <= d1 && l[1] >= -halfLong && l[0] <= halfLong && t[1] >= -halfThin && t[0] <= halfThin;
   }

   private static double[] project(AABB box, Vec3 origin, Vec3 axis) {
      double min = Double.MAX_VALUE;
      double max = -Double.MAX_VALUE;
      for (int i = 0; i < 8; i++) {
         double x = (i & 1) == 0 ? box.minX : box.maxX;
         double y = (i & 2) == 0 ? box.minY : box.maxY;
         double z = (i & 4) == 0 ? box.minZ : box.maxZ;
         double p = (x - origin.x) * axis.x + (y - origin.y) * axis.y + (z - origin.z) * axis.z;
         min = Math.min(min, p);
         max = Math.max(max, p);
      }

      return new double[]{min, max};
   }
}

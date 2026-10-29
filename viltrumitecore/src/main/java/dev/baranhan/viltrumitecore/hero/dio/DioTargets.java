package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.hero.ServerRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Who DIO's stand, knives and grip may hit. */
public final class DioTargets {
   private DioTargets() {
   }

   public static boolean hittable(ServerPlayer dio, Entity entity) {
      if (!(entity instanceof LivingEntity living) || entity == dio || !living.isAlive() || living.isSpectator() || living instanceof ArmorStand) {
         return false;
      }

      if (dio.isAlliedTo(living) || living.isAlliedTo(dio)) {
         return false;
      }

      if (living instanceof Player other) {
         return !other.isCreative() && ServerRules.pvp(dio) && dio.canHarmPlayer(other);
      }

      return living.isAttackable();
   }

   /** Living targets in a cone in front of DIO's eyes, nearest first. Walls block. */
   public static List<LivingEntity> cone(ServerPlayer dio, double reach, double halfDeg) {
      Vec3 eye = dio.getEyePosition();
      Vec3 look = dio.getLookAngle();
      AABB box = dio.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.6);
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity living : dio.level().getEntitiesOfClass(LivingEntity.class, box, e -> hittable(dio, e))) {
         Vec3 aim = closest(living.getBoundingBox(), eye);
         Vec3 to = aim.subtract(eye);
         if (to.length() > reach + 0.1) {
            continue;
         }

         Vec3 center = living.getBoundingBox().getCenter().subtract(eye);
         if (!DioRules.inCone(look.x, look.y, look.z, to.x, to.y, to.z, halfDeg) && !DioRules.inCone(look.x, look.y, look.z, center.x, center.y, center.z, halfDeg)) {
            continue;
         }

         BlockHitResult wall = dio.level().clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dio));
         if (wall.getType() == HitResult.Type.BLOCK && wall.getLocation().distanceToSqr(eye) + 0.04 < aim.distanceToSqr(eye)) {
            continue;
         }

         out.add(living);
      }

      out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
      return out;
   }

   private static Vec3 closest(AABB box, Vec3 p) {
      return new Vec3(Math.max(box.minX, Math.min(box.maxX, p.x)), Math.max(box.minY, Math.min(box.maxY, p.y)), Math.max(box.minZ, Math.min(box.maxZ, p.z)));
   }

   /** The living target DIO looks straight at within reach, or null. */
   @Nullable
   public static LivingEntity look(ServerPlayer dio, double reach) {
      Vec3 eye = dio.getEyePosition();
      Vec3 end = eye.add(dio.getLookAngle().scale(reach));
      BlockHitResult wall = dio.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dio));
      if (wall.getType() == HitResult.Type.BLOCK) {
         end = wall.getLocation();
      }

      EntityHitResult hit = ProjectileUtil.getEntityHitResult(dio, eye, end, dio.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
         e -> hittable(dio, e), reach * reach);
      if (hit != null && hit.getEntity() instanceof LivingEntity living) {
         return living;
      }

      List<LivingEntity> cone = cone(dio, reach, 20.0);
      return cone.isEmpty() ? null : cone.get(0);
   }

   /** Point DIO aims at for the Road Roller: entity feet, block top, or ground ahead. */
   public static Vec3 rollerPoint(ServerPlayer dio) {
      Vec3 eye = dio.getEyePosition();
      Vec3 end = eye.add(dio.getLookAngle().scale(DioRules.ROLLER_REACH));
      BlockHitResult wall = dio.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dio));
      Vec3 limit = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : end;
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(dio, eye, limit, dio.getBoundingBox().expandTowards(limit.subtract(eye)).inflate(1.5),
         e -> hittable(dio, e), DioRules.ROLLER_REACH * DioRules.ROLLER_REACH);
      Vec3 point;
      if (hit != null) {
         point = hit.getEntity().position();
      } else if (wall.getType() == HitResult.Type.BLOCK) {
         point = wall.getLocation();
      } else {
         Vec3 flat = new Vec3(dio.getLookAngle().x, 0, dio.getLookAngle().z);
         flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
         point = dio.position().add(flat.scale(DioRules.ROLLER_FALLBACK));
      }

      // Settle on the ground below the point.
      BlockHitResult ground = dio.level().clip(new ClipContext(point.add(0, 1.5, 0), point.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dio));
      if (ground.getType() == HitResult.Type.BLOCK) {
         point = ground.getLocation();
      }

      // Never further than the reach from where he stands.
      Vec3 from = dio.position();
      Vec3 d = point.subtract(from);
      if (d.length() > DioRules.ROLLER_REACH) {
         point = from.add(d.normalize().scale(DioRules.ROLLER_REACH));
      }

      return point;
   }
}

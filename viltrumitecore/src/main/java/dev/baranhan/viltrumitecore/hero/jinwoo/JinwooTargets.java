package dev.baranhan.viltrumitecore.hero.jinwoo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server target queries of Jinwoo's abilities (rays, cones, arcs). */
public final class JinwooTargets {
   private JinwooTargets() {
   }

   /** Distance along the look to the first solid block, or {@code range}. */
   public static double wallDistance(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(range));
      BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(eye);
   }

   @Nullable
   public static Vec3 lookBlock(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(range));
      BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
   }

   /** Nearest entity along the look within {@code tolerance} of the ray, not behind a wall. */
   @Nullable
   public static <T extends Entity> T lookEntity(ServerPlayer player, Class<T> type, double range, double tolerance, Predicate<T> filter) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      double wall = wallDistance(player, range);
      AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(tolerance + 2.0);
      T best = null;
      double bestT = wall + 0.5;
      for (T e : player.level().getEntitiesOfClass(type, box, e -> e != player && filter.test(e))) {
         Vec3 c = e.getBoundingBox().getCenter();
         Vec3 rel = c.subtract(eye);
         double t = rel.dot(look);
         if (t < 0 || t > bestT) {
            continue;
         }
         double off = rel.subtract(look.scale(t)).length();
         if (off <= tolerance + e.getBbWidth() * 0.5 + e.getBbHeight() * 0.25) {
            best = e;
            bestT = t;
         }
      }
      return best;
   }

   /** Living entities in a 3D cone along the look, nearest first. */
   public static List<LivingEntity> cone(ServerPlayer player, double range, double degrees, Predicate<LivingEntity> filter) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), e -> e != player && e.isAlive() && filter.test(e))) {
         Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
         if (d.length() <= range && JinwooRules.inCone(look.x, look.y, look.z, d.x, d.y, d.z, degrees) && player.hasLineOfSight(e)) {
            out.add(e);
         }
      }
      out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
      return out;
   }

   /** Living entities in the horizontal melee arc. */
   public static List<LivingEntity> arc(ServerPlayer player, double reach, double arcDegrees, Predicate<LivingEntity> filter) {
      Vec3 look = player.getLookAngle();
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0), e -> e != player && e.isAlive() && filter.test(e))) {
         Vec3 d = e.position().subtract(player.position());
         double gap = Math.sqrt(d.x * d.x + d.z * d.z) - e.getBbWidth() * 0.5;
         double dy = e.getBoundingBox().getCenter().y - player.getEyeY();
         if (gap <= reach && Math.abs(dy) <= reach && JinwooRules.inArc(look.x, look.z, d.x, d.z, arcDegrees)) {
            out.add(e);
         }
      }
      return out;
   }

   /** Living entities within a radius (sphere on the box center). */
   public static List<LivingEntity> around(Entity center, Vec3 at, double radius, Predicate<LivingEntity> filter) {
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : center.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1.0), e -> e != center && e.isAlive() && filter.test(e))) {
         if (e.getBoundingBox().getCenter().distanceToSqr(at) <= (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5)) {
            out.add(e);
         }
      }
      return out;
   }
}

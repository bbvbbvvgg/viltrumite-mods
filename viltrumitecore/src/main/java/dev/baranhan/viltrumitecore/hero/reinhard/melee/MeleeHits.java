package dev.baranhan.viltrumitecore.hero.reinhard.melee;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server hit test of one {@link MeleeArc.Swing} tick: the blade (or fist) path swept this
 * tick is cast as rays from the eye, every hittable body whose box (plus a latency margin)
 * a ray crosses within reach is hit once, nearest first. Blocks between the eye and the
 * body stop the hit (no hits through walls); the path never leaves the front of the view.
 * The look is read every tick, so turning, walking and jumping while striking all count.
 */
public final class MeleeHits {
   /** Latency / interpolation allowance around every target box (blocks). */
   static final double MARGIN = 0.25;
   /** Angular step between two cast rays of a sweep (degrees). */
   static final double RAY_STEP = 3.0;

   private MeleeHits() {
   }

   /** One landed contact: the body and the point on its box where the blade met it. */
   public record Hit(LivingEntity target, Vec3 at, double distance) {
   }

   /** New bodies the swing touches on action tick {@code tick}; adds their ids to {@code already}. */
   public static List<Hit> sweep(ServerPlayer player, MeleeArc.Swing swing, int tick, Set<UUID> already) {
      if (tick < swing.contactStart() || tick > swing.contactEnd() || already.size() >= swing.maxTargets()) {
         return List.of();
      }

      double p0 = swing.progress(tick - 1), p1 = swing.progress(tick);
      Vec3 eye = player.getEyePosition();
      double[][] basis = MeleeArc.basis(player.getYRot(), player.getXRot());
      List<Vec3> rays = rays(swing, basis, p0, p1);
      List<Hit> found = new ArrayList<>();
      Level level = player.level();
      for (Entity e : level.getEntities(player, new AABB(eye, eye).inflate(swing.reach() + 2.0), e -> ReinhardTargets.canHit(player, e))) {
         Entity root = ReinhardTargets.resolve(e);
         if (!(root instanceof LivingEntity target) || already.contains(root.getUUID()) || found.stream().anyMatch(h -> h.target() == target)) {
            continue;
         }

         AABB box = e.getBoundingBox();
         Vec3 at = contact(eye, box, rays, swing.reach(), swing.width());
         if (at != null && visible(level, player, eye, box, at)) {
            found.add(new Hit(target, at, at.distanceTo(eye)));
         }
      }

      found.sort(Comparator.comparingDouble(Hit::distance));
      List<Hit> out = new ArrayList<>();
      for (Hit hit : found) {
         if (already.size() >= swing.maxTargets()) {
            break;
         }

         already.add(hit.target().getUUID());
         out.add(hit);
      }

      return out;
   }

   /** Unit directions of the path swept between progress p0 and p1, about {@link #RAY_STEP} apart. */
   static List<Vec3> rays(MeleeArc.Swing swing, double[][] basis, double p0, double p1) {
      double[] a = swing.at(p0), b = swing.at(p1);
      double arc = Math.hypot(b[0] - a[0], b[1] - a[1]);
      int steps = Math.max(1, (int) Math.ceil(arc / RAY_STEP));
      List<Vec3> out = new ArrayList<>(steps + 1);
      for (int i = 0; i <= steps; i++) {
         double[] v = swing.at(p0 + (p1 - p0) * i / steps);
         double[] d = MeleeArc.direction(basis, v[0], v[1]);
         out.add(new Vec3(d[0], d[1], d[2]));
      }

      return out;
   }

   /**
    * Pure: where the first ray meets the box grown by the margin and the blade's angular
    * width at that distance; null = missed or out of reach. The point is clamped into the box.
    */
   static Vec3 contact(Vec3 eye, AABB box, List<Vec3> rays, double reach, double widthDeg) {
      Vec3 center = box.getCenter();
      double grow = MARGIN + Math.tan(Math.toRadians(widthDeg)) * Math.min(reach, center.distanceTo(eye));
      AABB thick = box.inflate(grow);
      if (closest(box, eye).distanceTo(eye) > reach) {
         return null;
      }

      if (thick.contains(eye)) {
         return closest(box, eye);
      }

      for (Vec3 dir : rays) {
         Optional<Vec3> hit = thick.clip(eye, eye.add(dir.scale(reach + grow)));
         if (hit.isPresent()) {
            return closest(box, hit.get());
         }
      }

      return null;
   }

   /** The point of {@code box} nearest to {@code p}. */
   static Vec3 closest(AABB box, Vec3 p) {
      return new Vec3(Math.max(box.minX, Math.min(box.maxX, p.x)), Math.max(box.minY, Math.min(box.maxY, p.y)),
         Math.max(box.minZ, Math.min(box.maxZ, p.z)));
   }

   /** No hit through walls: the contact, the body centre or its top must be in plain sight of the eye. */
   static boolean visible(Level level, Entity viewer, Vec3 eye, AABB box, Vec3 at) {
      Vec3 top = new Vec3(box.getCenter().x, box.maxY - 0.1, box.getCenter().z);
      for (Vec3 point : new Vec3[]{at, box.getCenter(), top}) {
         BlockHitResult block = level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
         if (block.getType() == HitResult.Type.MISS || block.getLocation().distanceToSqr(eye) >= eye.distanceToSqr(point) - 1.0E-3) {
            return true;
         }
      }

      return false;
   }
}

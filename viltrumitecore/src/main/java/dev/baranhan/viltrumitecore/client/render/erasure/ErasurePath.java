package dev.baranhan.viltrumitecore.client.render.erasure;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** A densely sampled polyline with arclength per sample. Port of the path part of Roundabout's
 * TheHandErasureRenderer.Erasure (Hydraheads, Roundabout License v1.2, see CREDITS.md). Immutable. */
public final class ErasurePath {
   private static final int SAMPLES_PER_SEGMENT = 16;
   private final Vec3[] points;
   private final float[] lengths;

   private ErasurePath(Vec3[] points) {
      if (points.length < 2) throw new IllegalArgumentException("An erasure path needs two points");
      this.points = points;
      this.lengths = new float[points.length];
      for (int i = 1; i < points.length; i++) lengths[i] = lengths[i - 1] + (float) points[i].distanceTo(points[i - 1]);
   }

   /** Already sampled points, used as they are. */
   public static ErasurePath sampled(Vec3[] points) {
      return new ErasurePath(points.clone());
   }

   /** Catmull-Rom curve through control points, 16 samples per segment, like the original. */
   public static ErasurePath smooth(List<Vec3> controlPoints) {
      int segments = controlPoints.size() - 1;
      if (segments < 1) throw new IllegalArgumentException("An erasure path needs two points");
      Vec3[] points = new Vec3[segments * SAMPLES_PER_SEGMENT + 1];
      for (int i = 0; i < points.length; i++) {
         int segment = Math.min(i / SAMPLES_PER_SEGMENT, segments - 1);
         float t = (i - segment * SAMPLES_PER_SEGMENT) / (float) SAMPLES_PER_SEGMENT;
         points[i] = catmullRom(controlPoints.get(Math.max(segment - 1, 0)), controlPoints.get(segment),
            controlPoints.get(segment + 1), controlPoints.get(Math.min(segment + 2, segments)), t);
      }
      return new ErasurePath(points);
   }

   public float length() {
      return lengths[lengths.length - 1];
   }

   /** Point at arclength s; past either end the path continues straight. */
   public Vec3 pointAt(float s) {
      int last = points.length - 1;
      if (s <= 0) return points[0].add(points[1].subtract(points[0]).normalize().scale(s));
      if (s >= lengths[last]) return points[last].add(points[last].subtract(points[last - 1]).normalize().scale(s - lengths[last]));
      int lo = 1, hi = last;
      while (lo < hi) {
         int mid = (lo + hi) >>> 1;
         if (lengths[mid] < s) lo = mid + 1; else hi = mid;
      }
      float span = lengths[lo] - lengths[lo - 1];
      float t = span > 0 ? (s - lengths[lo - 1]) / span : 0F;
      return points[lo - 1].lerp(points[lo], t);
   }

   private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
      float t2 = t * t;
      float t3 = t2 * t;
      return p1.scale(2)
         .add(p2.subtract(p0).scale(t))
         .add(p0.scale(2).subtract(p1.scale(5)).add(p2.scale(4)).subtract(p3).scale(t2))
         .add(p1.scale(3).subtract(p0).subtract(p2.scale(3)).add(p3).scale(t3))
         .scale(0.5);
   }
}

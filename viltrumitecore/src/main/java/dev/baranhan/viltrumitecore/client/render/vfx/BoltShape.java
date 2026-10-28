package dev.baranhan.viltrumitecore.client.render.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry and timing of a drawn lightning channel (no client classes,
 * unit tested). A channel is a main strand from {@code from} to {@code to}
 * made by midpoint displacement, plus forked branches and sub-branches that
 * leave it downward and outward. {@link ElectricVfx} draws each strand as
 * camera-facing ribbons (core, inner glow, outer glow).
 */
public final class BoltShape {
   /** Hard caps (a hostile packet or a long arc cannot ask for unbounded geometry). */
   public static final int MAX_DEPTH = 7;
   public static final int MAX_BRANCHES = 10;
   public static final double MAX_LENGTH = 96.0;

   private BoltShape() {
   }

   /** One polyline of the channel: width multiplier (1 = main strand) and brightness 0..1. */
   public record Strand(Vec3[] points, float width, float brightness, int level) {
   }

   /**
    * The whole channel. {@code roughness} is the sideways jitter as a part of
    * each segment (0.25 = gentle arc, 0.45 = sky bolt). Same seed, same shape.
    */
   public static List<Strand> channel(long seed, Vec3 from, Vec3 to, double roughness, int branches) {
      Random random = new Random(seed);
      List<Strand> out = new ArrayList<>();
      double length = Math.min(MAX_LENGTH, from.distanceTo(to));
      int depth = depthFor(length);
      Vec3[] main = strand(random, from, clampEnd(from, to), depth, roughness);
      out.add(new Strand(main, 1.0F, 1.0F, 0));
      int count = Math.max(0, Math.min(MAX_BRANCHES, branches));
      Vec3 axis = to.subtract(from);
      for (int i = 0; i < count; i++) {
         // Forks start in the upper two thirds and go on down the bolt's way, spreading out.
         int index = 1 + random.nextInt(Math.max(1, (int)(main.length * 0.7)));
         Vec3 start = main[Math.min(main.length - 2, index)];
         double part = 0.18 + random.nextDouble() * 0.22;
         Vec3 end = start.add(spread(random, axis, part, 0.9));
         int branchDepth = Math.max(2, depth - 2);
         Vec3[] branch = strand(random, start, end, branchDepth, roughness * 1.1);
         out.add(new Strand(branch, 0.45F, 0.75F, 1));
         if (random.nextFloat() < 0.6F && branch.length > 3) {
            Vec3 subStart = branch[1 + random.nextInt(branch.length - 2)];
            Vec3 subEnd = subStart.add(spread(random, axis, part * 0.45, 1.1));
            out.add(new Strand(strand(random, subStart, subEnd, Math.max(2, branchDepth - 1), roughness * 1.2), 0.25F, 0.5F, 2));
         }
      }

      return out;
   }

   /** The channel again with every interior point nudged (a re-strike takes a slightly new path). */
   public static List<Strand> perturb(List<Strand> base, long seed, double amount) {
      Random random = new Random(seed);
      List<Strand> out = new ArrayList<>(base.size());
      for (Strand strand : base) {
         Vec3[] points = strand.points().clone();
         for (int i = 1; i < points.length - 1; i++) {
            points[i] = points[i].add((random.nextDouble() - 0.5) * amount, (random.nextDouble() - 0.5) * amount * 0.5, (random.nextDouble() - 0.5) * amount);
         }

         out.add(new Strand(points, strand.width(), strand.brightness(), strand.level()));
      }

      return out;
   }

   /** Segments of a strand: 2^depth, depth from the length (about one kink per 0.6 block). */
   public static int depthFor(double length) {
      int depth = 2;
      while (depth < MAX_DEPTH && (1 << depth) < length / 0.6) {
         depth++;
      }

      return depth;
   }

   private static Vec3 clampEnd(Vec3 from, Vec3 to) {
      Vec3 d = to.subtract(from);
      double length = d.length();
      return length <= MAX_LENGTH ? to : from.add(d.scale(MAX_LENGTH / length));
   }

   private static Vec3[] strand(Random random, Vec3 from, Vec3 to, int depth, double roughness) {
      int n = 1 << depth;
      Vec3[] points = new Vec3[n + 1];
      points[0] = from;
      points[n] = to;
      Vec3 axis = to.subtract(from);
      double length = axis.length();
      Vec3 dir = length < 1.0E-6 ? new Vec3(0.0, -1.0, 0.0) : axis.scale(1.0 / length);
      Vec3 u = perpendicular(dir);
      Vec3 v = dir.cross(u);
      for (int step = n; step > 1; step /= 2) {
         double amplitude = roughness * length * step / n;
         for (int i = step / 2; i < n; i += step) {
            Vec3 mid = points[i - step / 2].lerp(points[i + step / 2], 0.5);
            double a = (random.nextDouble() - 0.5) * 2.0 * amplitude;
            double b = (random.nextDouble() - 0.5) * 2.0 * amplitude;
            points[i] = mid.add(u.scale(a)).add(v.scale(b));
         }
      }

      return points;
   }

   private static Vec3 spread(Random random, Vec3 axis, double part, double sideways) {
      double length = axis.length();
      Vec3 dir = length < 1.0E-6 ? new Vec3(0.0, -1.0, 0.0) : axis.scale(1.0 / length);
      Vec3 u = perpendicular(dir);
      Vec3 v = dir.cross(u);
      double angle = random.nextDouble() * Math.PI * 2.0;
      Vec3 side = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
      return dir.scale(length * part).add(side.scale(length * part * sideways * (0.6 + random.nextDouble() * 0.6)));
   }

   /** A unit vector perpendicular to {@code dir}. */
   public static Vec3 perpendicular(Vec3 dir) {
      Vec3 helper = Math.abs(dir.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
      return dir.cross(helper).normalize();
   }

   // --- timing (ticks) ---

   /** Ticks the stepped leader takes to reach the ground before the return stroke. */
   public static final float LEADER_TICKS = 2.0F;
   /** Ticks of the full-power return stroke. */
   public static final float STROKE_TICKS = 4.0F;

   /**
    * Pure brightness 0..1 of a sky bolt at {@code age} ticks of {@code life}:
    * a dim leader, the full return stroke, then seeded re-strikes that decay
    * over the first half, and a weak flickering afterglow to the end.
    */
   public static float boltBrightness(float age, int life, long seed) {
      if (age < 0.0F || age >= life) {
         return 0.0F;
      }

      if (age < LEADER_TICKS) {
         return 0.45F;
      }

      float strokeEnd = LEADER_TICKS + STROKE_TICKS;
      if (age < strokeEnd) {
         return 1.0F - 0.15F * (age - LEADER_TICKS) / STROKE_TICKS;
      }

      float restrikeEnd = strokeEnd + (life - strokeEnd) * 0.5F;
      int tick = (int)age;
      float noise = hash01(seed, tick);
      if (age < restrikeEnd) {
         float decay = 1.0F - (age - strokeEnd) / Math.max(1.0F, restrikeEnd - strokeEnd);
         return noise > 0.5F ? 0.35F + 0.6F * decay * noise : 0.08F + 0.12F * decay;
      }

      float tail = 1.0F - (age - restrikeEnd) / Math.max(1.0F, life - restrikeEnd);
      return Math.max(0.0F, tail * tail * (0.12F + 0.1F * noise));
   }

   /** Visible part of the main strand while the leader comes down (0..1, from the top). */
   public static float leaderReach(float age) {
      return age >= LEADER_TICKS ? 1.0F : Math.max(0.05F, age / LEADER_TICKS);
   }

   /** Pure brightness of a short arc: a hard start, flicker, a quick fade. */
   public static float arcBrightness(float age, int life, long seed) {
      if (age < 0.0F || age >= life) {
         return 0.0F;
      }

      float fade = 1.0F - age / life;
      float noise = hash01(seed, (int)age);
      return Math.min(1.0F, fade * (0.55F + 0.45F * noise) + (age < 1.0F ? 0.3F : 0.0F));
   }

   /** Deterministic 0..1 noise per (seed, tick). */
   public static float hash01(long seed, int tick) {
      long h = seed * 0x9E3779B97F4A7C15L + tick * 0xC2B2AE3D27D4EB4FL;
      h ^= h >>> 33;
      h *= 0xFF51AFD7ED558CCDL;
      h ^= h >>> 33;
      return (h >>> 40) / (float)(1L << 24);
   }
}

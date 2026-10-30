package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.*;

import java.util.*;

/**
 * Pure model of the shattering "world glass" (no Minecraft classes: unit-testable).
 *
 * <p>Space: the screen is the plane z = -1 seen by a 90° camera at the origin, so a screen point
 * is (x, y) with y in [-1, 1] (up) and x in [-aspect, aspect]. The fracture is seeded per scene:
 * jagged radial rays from the origin (the target on screen) cut by concentric jagged rings make
 * the web; secondary branches only crack. Every line segment carries the crack-phase window in
 * which it grows, so the glass cracks progressively instead of fading in a pattern.
 *
 * <p>The web cells are the shards. Each has its own mass (area), burst velocity, depth velocity,
 * tumble axis/speed, launch delay and a rebuild window (outer rings return first, the last piece
 * snaps home at the centre on {@link ReinhardRules#ERASE_REBUILD_END}).
 */
public final class ErasureGlass {
   /** Ring count, innermost first. */
   public static final int RINGS = 6;
   /** Rebuild snap times, outermost ring first: the last one is the final impact. */
   public static final float[] SNAPS = snaps();
   private static float[] snaps() {
      float[] out = new float[RINGS];
      for (int i = 0; i < RINGS; i++) out[i] = ERASE_DARK_END + 8 + (ERASE_REBUILD_END - ERASE_DARK_END - 8) * i / (float)(RINGS - 1);
      return out;
   }

   public enum Kind { RAY, RING, BRANCH }
   /** A crack line growing from (x0,y0) to (x1,y1) while the crack phase goes c0..c1. */
   public record Segment(float x0, float y0, float x1, float y1, float c0, float c1, float width, Kind kind) {}
   /** One shard: polygon (x,y pairs, screen plane) and its motion parameters. */
   public record Shard(float[] poly, float cx, float cy, float area, int ring,
      float delay, float vx, float vy, float vz, float ax, float ay, float az, float spin,
      float thickness, float depart, float arrive, float micro, float microAngle) {}
   /** A shard pose: centre offset (dx,dy,dz from the rest plane), rotation (axis, angle), brightness. */
   public record Pose(float dx, float dy, float dz, float angle, float alpha, float rim) {
      static final Pose REST = new Pose(0, 0, 0, 0, 1, 0);
   }

   private final float aspect, ox, oy, maxR;
   private final List<Segment> segments = new ArrayList<>();
   private final List<Shard> shards = new ArrayList<>();

   private ErasureGlass(float aspect, float ox, float oy, long seed) {
      this.aspect = aspect; this.ox = ox; this.oy = oy;
      float far = 0;
      for (float cx : new float[]{-aspect, aspect}) for (float cy : new float[]{-1, 1}) far = Math.max(far, (float)Math.hypot(cx - ox, cy - oy));
      this.maxR = far * 1.3F;
      build(new Random(seed));
   }

   /** @param ox,oy origin on the screen plane (clamped inside); deterministic for (aspect, origin, seed). */
   public static ErasureGlass of(float aspect, float ox, float oy, long seed) {
      float a = Float.isFinite(aspect) && aspect > .2F ? Math.min(aspect, 5) : 16 / 9F;
      float x = Float.isFinite(ox) ? Math.max(-a * .6F, Math.min(a * .6F, ox)) : 0;
      float y = Float.isFinite(oy) ? Math.max(-.6F, Math.min(.6F, oy)) : 0;
      return new ErasureGlass(a, x, y, seed);
   }

   public float aspect() {return aspect;}
   public float originX() {return ox;}
   public float originY() {return oy;}
   public List<Segment> segments() {return Collections.unmodifiableList(segments);}
   public List<Shard> shards() {return Collections.unmodifiableList(shards);}

   // ------------------------------------------------------------------ timing (scene ticks)

   /** Crack phase 0..1 over the crack growth and the rupture (20..90); 1 after, until the close. */
   public static float crackPhase(float t) {
      if (!(t >= ERASE_CRACK_START)) return 0;
      if (t >= ERASE_SHATTER_START) return 1;
      return (t - ERASE_CRACK_START) / (ERASE_SHATTER_START - ERASE_CRACK_START);
   }
   /** The phase at which the slow growth hands over to the rupture. */
   public static final float RUPTURE = (ERASE_CRACK_END - ERASE_CRACK_START) / (float)(ERASE_SHATTER_START - ERASE_CRACK_START);
   /** Visible fraction of the crack web while the cracks close (1 = whole web, 0 = healed). */
   public static float closing(float t) {
      if (t < ERASE_REBUILD_END) return 1;
      if (t >= ERASE_LENGTH) return 0;
      float u = (t - ERASE_REBUILD_END) / (ERASE_LENGTH - ERASE_REBUILD_END);
      return 1 - u * u * (3 - 2 * u);
   }
   /** Are the crack lines drawn at t (growth and rupture, then again once the glass is whole). */
   public static boolean linesVisible(float t) {
      return t >= ERASE_CRACK_START && t < ERASE_SHATTER_START || t >= ERASE_REBUILD_END && t < ERASE_LENGTH;
   }
   /** Does the frame need the shard pass (micro-misalignment, flight, void, rebuild)? */
   public static boolean shardsVisible(float t) {
      return t >= ERASE_CRACK_END - 20 && t < ERASE_LENGTH;
   }
   /** The world frame is replaced by the void + shards. */
   public static boolean voidVisible(float t) {return t >= ERASE_SHATTER_START && t < ERASE_REBUILD_END;}
   /** Line brightness: a slow build, critical shimmer in the rupture, fades as the cracks close. */
   public static float lineGlow(float t, float flicker) {
      float c = crackPhase(t);
      if (t >= ERASE_REBUILD_END) return .85F * closing(t);
      float base = .55F + .45F * c;
      if (c > RUPTURE) base *= .8F + .35F * flicker;
      return Math.min(1.2F, base);
   }
   /** Fraction of a segment drawn at crack phase c, healing included. */
   public static float grown(Segment s, float c, float close) {
      if (close < 1) {
         // Heal from the outside in: the newest cracks close first.
         float limit = close;
         if (s.c0 >= limit) return 0;
         return s.c1 <= limit ? 1 : (limit - s.c0) / Math.max(1e-4F, s.c1 - s.c0);
      }
      if (c <= s.c0) return 0;
      if (c >= s.c1) return 1;
      return (c - s.c0) / Math.max(1e-4F, s.c1 - s.c0);
   }

   /** Ease out with a small overshoot: the shard slams past home and settles. */
   static float easeOutBack(float u) {
      float s = .9F, v = u - 1;
      return 1 + v * v * ((s + 1) * v + s);
   }
   /** Flight time (s) with drag: shards slow down as they drift into the void. */
   static float drift(float seconds, float limit) {
      return seconds <= 0 ? 0 : limit * (1 - (float)Math.exp(-seconds / limit));
   }
   static final float FLIGHT_LIMIT = 2.6F;

   /** Pose of a shard at scene time t (ticks). */
   public static Pose pose(Shard s, float t) {
      if (!Float.isFinite(t) || t >= ERASE_LENGTH) return Pose.REST;
      if (t < ERASE_SHATTER_START) {
         // Critical phase: the cells are already loose, a pixel or two out of line.
         float m = t < ERASE_CRACK_END - 20 ? 0 : smooth((t - (ERASE_CRACK_END - 20)) / 30F);
         return new Pose(s.micro * m * (float)Math.cos(s.microAngle), s.micro * m * (float)Math.sin(s.microAngle), -s.micro * m * .5F,
            .018F * m * (s.spin > 0 ? 1 : -1), 1, 0);
      }
      float launch = ERASE_SHATTER_START + s.delay;
      if (t >= s.arrive) {
         // Home; the snap flash fades, the residual misalignment closes with the cracks.
         float flash = (float)Math.exp(-(t - s.arrive) / 3F);
         float m = .35F * closing(t);
         return new Pose(s.micro * m * (float)Math.cos(s.microAngle), s.micro * m * (float)Math.sin(s.microAngle), 0, 0, 1,
            t < ERASE_REBUILD_END + 6 ? Math.max(flash, .35F * (1 - Math.max(0, t - ERASE_REBUILD_END) / 6F)) : flash);
      }
      float seconds;
      if (t < s.depart) {
         seconds = drift(Math.max(0, t - launch) / 20F, FLIGHT_LIMIT);
      } else {
         float far = drift(Math.max(0, s.depart - launch) / 20F, FLIGHT_LIMIT);
         float u = (t - s.depart) / Math.max(1, s.arrive - s.depart);
         seconds = far * (1 - easeOutBack(u * u * (3 - 2 * u) * .35F + u * .65F));
      }
      if (t < launch) seconds = 0;
      // Pre-launch the loose cell holds its critical misalignment.
      float pop = t < launch + 2 ? smooth((t - ERASE_SHATTER_START) / Math.max(1, s.delay + 2)) : 1;
      float mx = s.micro * (float)Math.cos(s.microAngle) * (1 - pop * .5F), my = s.micro * (float)Math.sin(s.microAngle) * (1 - pop * .5F);
      float dx = mx + s.vx * seconds, dy = my + s.vy * seconds - .55F * seconds * seconds;
      float dz = s.vz * seconds - .3F * seconds * seconds;
      float angle = s.spin * seconds + .018F * (s.spin > 0 ? 1 : -1);
      float depth = Math.max(0, -dz);
      float alpha = (float)Math.exp(-depth * .16F);
      return new Pose(dx, dy, dz, angle, alpha, 0);
   }

   // ------------------------------------------------------------------ construction

   private record Ray(float[] xs, float[] ys, float[] rs, float start, float power) {}

   private void build(Random random) {
      int rays = 11 + random.nextInt(5);
      float[] angles = new float[rays];
      float base = random.nextFloat() * 6.2831855F;
      for (int i = 0; i < rays; i++) angles[i] = base + 6.2831855F * (i + (random.nextFloat() - .5F) * .35F) / rays;
      Ray[] list = new Ray[rays];
      // The first hairlines appear at once, the rest join over the first third of the growth.
      for (int i = 0; i < rays; i++) list[i] = ray(random, angles[i], i < 3 ? random.nextFloat() * .05F : .04F + random.nextFloat() * .3F);
      for (Ray ray : list) for (int j = 0; j + 1 < ray.xs.length; j++) {
         float r0 = ray.rs[j], r1 = ray.rs[j + 1];
         float width = Math.max(.9F, 2.6F - 1.7F * Math.min(1, r0 / (maxR * .6F)));
         segments.add(new Segment(ray.xs[j], ray.ys[j], ray.xs[j + 1], ray.ys[j + 1], reach(ray, r0), reach(ray, r1), width, Kind.RAY));
      }
      // Ring radii (fractions of maxR); each ray pair jitters its own crossing.
      float[] fractions = {0, .055F, .13F, .23F, .36F, .53F};
      float[][] cross = new float[RINGS][rays];
      for (int k = 1; k < RINGS; k++) for (int i = 0; i < rays; i++)
         cross[k][i] = maxR * fractions[k] * (.86F + random.nextFloat() * .28F);
      // ring polylines between ray i and i+1: start, jag, end
      float[][][] ring = new float[RINGS][rays][];
      for (int k = 1; k < RINGS; k++) for (int i = 0; i < rays; i++) {
         int n = (i + 1) % rays;
         float[] a = at(list[i], cross[k][i]), b = at(list[n], cross[k][n]);
         float mx = (a[0] + b[0]) * .5F, my = (a[1] + b[1]) * .5F;
         float dx = mx - ox, dy = my - oy, len = (float)Math.hypot(dx, dy);
         float jag = (random.nextFloat() - .5F) * .22F * (maxR * (fractions[k] - fractions[k - 1]));
         // jag: radial, plus a little tangential so it never reads as a perfect polygon
         float tx = -dy / Math.max(1e-4F, len), ty = dx / Math.max(1e-4F, len);
         float tj = (random.nextFloat() - .5F) * .25F * (float)Math.hypot(b[0] - a[0], b[1] - a[1]);
         mx += dx / Math.max(1e-4F, len) * jag + tx * tj; my += dy / Math.max(1e-4F, len) * jag + ty * tj;
         ring[k][i] = new float[]{a[0], a[1], mx, my, b[0], b[1]};
         float c0 = Math.max(reach(list[i], cross[k][i]), reach(list[n], cross[k][n])) + .02F + random.nextFloat() * .1F;
         float c1 = c0 + .05F + random.nextFloat() * .08F;
         // Outer rings belong to the rupture; inner ones to the slow growth.
         if (k >= 4) {c0 = Math.max(c0, RUPTURE + (k - 4) * .04F + random.nextFloat() * .03F); c1 = Math.min(1, c0 + .03F + random.nextFloat() * .04F);}
         c0 = Math.min(c0, .985F); c1 = Math.max(c0 + .01F, Math.min(1, c1));
         boolean flip = random.nextBoolean();
         float w = k >= 4 ? 1.0F : 1.35F;
         float cm = (c0 + c1) * .5F;
         if (flip) {
            segments.add(new Segment(b[0], b[1], mx, my, c0, cm, w, Kind.RING));
            segments.add(new Segment(mx, my, a[0], a[1], cm, c1, w, Kind.RING));
         } else {
            segments.add(new Segment(a[0], a[1], mx, my, c0, cm, w, Kind.RING));
            segments.add(new Segment(mx, my, b[0], b[1], cm, c1, w, Kind.RING));
         }
      }
      // Secondary branches: crack only, they make the web read as stressed glass.
      for (Ray ray : list) {
         int count = 1 + random.nextInt(3);
         for (int b = 0; b < count; b++) {
            float r = maxR * (.08F + random.nextFloat() * .45F);
            float[] p = at(ray, r);
            float c = reach(ray, r) + .03F + random.nextFloat() * .12F;
            if (c > .97F) continue;
            float dir = (float)Math.atan2(p[1] - oy, p[0] - ox) + (random.nextBoolean() ? 1 : -1) * (.45F + random.nextFloat() * .55F);
            float x = p[0], y = p[1], length = maxR * (.04F + random.nextFloat() * .13F);
            int steps = 2 + random.nextInt(3);
            float span = (.08F + random.nextFloat() * .14F) * (c > RUPTURE ? .4F : 1);
            for (int s = 0; s < steps; s++) {
               dir += (random.nextFloat() - .5F) * .5F;
               float nx = x + (float)Math.cos(dir) * length / steps, ny = y + (float)Math.sin(dir) * length / steps;
               float a0 = c + span * s / steps, a1 = c + span * (s + 1) / steps;
               segments.add(new Segment(x, y, nx, ny, Math.min(.99F, a0), Math.min(1, a1), .8F, Kind.BRANCH));
               x = nx; y = ny;
            }
         }
      }
      // Shards: cell (k, i) between rings k, k+1 and rays i, i+1.
      for (int k = 0; k < RINGS; k++) for (int i = 0; i < rays; i++) {
         int n = (i + 1) % rays;
         float inA = k == 0 ? 0 : cross[k][i], inB = k == 0 ? 0 : cross[k][n];
         float outA = k + 1 < RINGS ? cross[k + 1][i] : maxR * 1.1F, outB = k + 1 < RINGS ? cross[k + 1][n] : maxR * 1.1F;
         // ray i (in -> out), outer ring (i -> n), ray n (out -> in), inner ring (n -> i)
         List<float[]> poly = new ArrayList<>();
         addRayPoints(poly, list[i], inA, outA, false);
         if (k + 1 < RINGS) {float[] r = ring[k + 1][i]; poly.add(new float[]{r[2], r[3]});}
         else {float[] a = at(list[i], outA), b = at(list[n], outB); poly.add(new float[]{(a[0] + b[0]) * .5F, (a[1] + b[1]) * .5F});}
         addRayPoints(poly, list[n], inB, outB, true);
         if (k > 0) {float[] r = ring[k][i]; poly.add(new float[]{r[2], r[3]});}
         float[] flat = clip(poly);
         if (flat.length < 6) continue;
         // big outer cells split once more along a seeded chord: variety of sizes
         float area = area(flat);
         if (k >= 3 && area > .1F && random.nextFloat() < .8F) {
            float[] c = centroid(flat);
            float angle = random.nextFloat() * 3.1415927F;
            float nx = (float)Math.cos(angle), ny = (float)Math.sin(angle);
            float off = (random.nextFloat() - .5F) * .3F * (float)Math.sqrt(area);
            float[] left = half(flat, c[0] + nx * off, c[1] + ny * off, nx, ny), right = half(flat, c[0] + nx * off, c[1] + ny * off, -nx, -ny);
            for (float[] part : new float[][]{left, right}) if (part.length >= 6) addShard(part, k, random);
            // the chord itself cracks during the rupture
            float[] chord = chord(flat, c[0] + nx * off, c[1] + ny * off, -ny, nx);
            if (chord != null) {
               float c0 = RUPTURE + random.nextFloat() * .1F;
               segments.add(new Segment(chord[0], chord[1], chord[2], chord[3], c0, Math.min(1, c0 + .04F), .9F, Kind.BRANCH));
            }
         } else addShard(flat, k, random);
      }
      shards.sort(Comparator.comparingDouble(s -> s.ring));
   }

   private void addShard(float[] poly, int ring, Random random) {
      float[] c = centroid(poly);
      float area = area(poly), size = (float)Math.sqrt(area);
      float dx = c[0] - ox, dy = c[1] - oy, len = Math.max(1e-3F, (float)Math.hypot(dx, dy));
      float mass = .55F + 2.4F * size;
      float speed = (.25F + random.nextFloat() * .55F) / mass * (1.2F - .5F * Math.min(1, len / maxR));
      float vx = dx / len * speed + (random.nextFloat() - .5F) * .12F, vy = dy / len * speed + (random.nextFloat() - .5F) * .12F + .1F;
      boolean toward = ring <= 1 && random.nextFloat() < .35F;
      float vz = toward ? .25F + random.nextFloat() * .45F : -(.55F + random.nextFloat() * 1.9F) / (.7F + size);
      float ax = random.nextFloat() - .5F, ay = random.nextFloat() - .5F, az = (random.nextFloat() - .5F) * .6F;
      float al = Math.max(1e-3F, (float)Math.sqrt(ax * ax + ay * ay + az * az));
      float spin = (1.3F + random.nextFloat() * 3.6F) / (.6F + 2.2F * size) * (random.nextBoolean() ? 1 : -1);
      float delay = 4 * Math.min(1, len / maxR) + random.nextFloat() * 1.2F;
      float arrive = SNAPS[RINGS - 1 - ring] - random.nextFloat() * 1.5F * (ring == 0 ? 0 : 1);
      float depart = Math.max(ERASE_DARK_END, arrive - (8 + 2.5F * ring + random.nextFloat() * 4));
      float micro = (.003F + random.nextFloat() * .006F) * (ring == 0 ? .5F : 1);
      shards.add(new Shard(poly, c[0], c[1], area, ring, delay, vx, vy, vz, ax / al, ay / al, az / al, spin,
         .01F + .03F * size, depart, arrive, micro, random.nextFloat() * 6.2831855F));
   }

   private Ray ray(Random random, float angle, float start) {
      List<float[]> points = new ArrayList<>();
      float x = ox, y = oy, dir = angle, r = 0;
      points.add(new float[]{x, y, 0});
      while (r < maxR * 1.15F) {
         dir += (random.nextFloat() - .5F) * .24F;
         dir = angle + Math.max(-.1F, Math.min(.1F, dir - angle));
         float step = maxR * (.035F + random.nextFloat() * .05F);
         float nx = x + (float)Math.cos(dir) * step, ny = y + (float)Math.sin(dir) * step;
         float nr = (float)Math.hypot(nx - ox, ny - oy);
         if (nr <= r + 1e-4F) {nx = x + (float)Math.cos(angle) * step; ny = y + (float)Math.sin(angle) * step; nr = (float)Math.hypot(nx - ox, ny - oy);}
         x = nx; y = ny; r = nr;
         points.add(new float[]{x, y, r});
      }
      float[] xs = new float[points.size()], ys = new float[points.size()], rs = new float[points.size()];
      for (int i = 0; i < xs.length; i++) {xs[i] = points.get(i)[0]; ys[i] = points.get(i)[1]; rs[i] = points.get(i)[2];}
      return new Ray(xs, ys, rs, start, .75F + random.nextFloat() * .7F);
   }

   /** Crack phase at which a ray's tip reaches radius r (slow growth, then the race to the edges). */
   private float reach(Ray ray, float r) {
      float slowReach = maxR * .38F;
      if (r <= slowReach) {
         float u = (float)Math.pow(Math.max(0, r / slowReach), 1 / ray.power);
         return ray.start + (RUPTURE - ray.start) * u;
      }
      float u = Math.min(1, (r - slowReach) / (maxR * 1.15F - slowReach));
      return RUPTURE + (.97F - RUPTURE) * (float)Math.pow(u, 1.4F);
   }

   private static float[] at(Ray ray, float r) {
      for (int j = 0; j + 1 < ray.rs.length; j++) {
         if (ray.rs[j + 1] >= r) {
            float f = (r - ray.rs[j]) / Math.max(1e-5F, ray.rs[j + 1] - ray.rs[j]);
            return new float[]{ray.xs[j] + (ray.xs[j + 1] - ray.xs[j]) * f, ray.ys[j] + (ray.ys[j + 1] - ray.ys[j]) * f};
         }
      }
      int last = ray.rs.length - 1;
      return new float[]{ray.xs[last], ray.ys[last]};
   }

   private static void addRayPoints(List<float[]> poly, Ray ray, float from, float to, boolean reverse) {
      List<float[]> pts = new ArrayList<>();
      pts.add(at(ray, from));
      for (int j = 0; j < ray.rs.length; j++) if (ray.rs[j] > from && ray.rs[j] < to) pts.add(new float[]{ray.xs[j], ray.ys[j]});
      pts.add(at(ray, to));
      if (reverse) Collections.reverse(pts);
      for (float[] p : pts) {
         float[] last = poly.isEmpty() ? null : poly.get(poly.size() - 1);
         if (last == null || Math.abs(last[0] - p[0]) + Math.abs(last[1] - p[1]) > 1e-5F) poly.add(p);
      }
   }

   /** Sutherland–Hodgman against the screen rectangle. */
   private float[] clip(List<float[]> poly) {
      float[] flat = new float[poly.size() * 2];
      for (int i = 0; i < poly.size(); i++) {flat[i * 2] = poly.get(i)[0]; flat[i * 2 + 1] = poly.get(i)[1];}
      flat = half(flat, -aspect, 0, 1, 0);
      flat = half(flat, aspect, 0, -1, 0);
      flat = half(flat, 0, -1, 0, 1);
      flat = half(flat, 0, 1, 0, -1);
      return flat;
   }

   /** Keep the side of the line through (px,py) where dot(p - point, n) >= 0. */
   static float[] half(float[] poly, float px, float py, float nx, float ny) {
      int n = poly.length / 2;
      if (n < 3) return new float[0];
      float[] out = new float[poly.length * 2 + 4];
      int count = 0;
      for (int i = 0; i < n; i++) {
         float ax = poly[i * 2], ay = poly[i * 2 + 1], bx = poly[(i + 1) % n * 2], by = poly[(i + 1) % n * 2 + 1];
         float da = (ax - px) * nx + (ay - py) * ny, db = (bx - px) * nx + (by - py) * ny;
         if (da >= 0) {out[count++] = ax; out[count++] = ay;}
         if (da >= 0 != db >= 0) {
            float f = da / (da - db);
            out[count++] = ax + (bx - ax) * f; out[count++] = ay + (by - ay) * f;
         }
      }
      return Arrays.copyOf(out, count);
   }

   /** Where a line through (px,py) along (dx,dy) crosses the polygon: the visible split crack. */
   private static float[] chord(float[] poly, float px, float py, float dx, float dy) {
      int n = poly.length / 2; float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
      for (int i = 0; i < n; i++) {
         float ax = poly[i * 2], ay = poly[i * 2 + 1], bx = poly[(i + 1) % n * 2], by = poly[(i + 1) % n * 2 + 1];
         float ex = bx - ax, ey = by - ay, den = dx * ey - dy * ex;
         if (Math.abs(den) < 1e-7F) continue;
         float t = ((ax - px) * ey - (ay - py) * ex) / den, u = ((ax - px) * dy - (ay - py) * dx) / den;
         if (u >= 0 && u <= 1) {lo = Math.min(lo, t); hi = Math.max(hi, t);}
      }
      if (lo >= hi) return null;
      return new float[]{px + dx * lo, py + dy * lo, px + dx * hi, py + dy * hi};
   }

   static float area(float[] poly) {
      int n = poly.length / 2; float a = 0;
      for (int i = 0; i < n; i++) {int j = (i + 1) % n; a += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1];}
      return Math.abs(a) * .5F;
   }

   static float[] centroid(float[] poly) {
      int n = poly.length / 2; float a = 0, cx = 0, cy = 0;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         float cross = poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1];
         a += cross; cx += (poly[i * 2] + poly[j * 2]) * cross; cy += (poly[i * 2 + 1] + poly[j * 2 + 1]) * cross;
      }
      if (Math.abs(a) < 1e-8F) {
         float sx = 0, sy = 0; for (int i = 0; i < n; i++) {sx += poly[i * 2]; sy += poly[i * 2 + 1];}
         return new float[]{sx / n, sy / n};
      }
      return new float[]{cx / (3 * a), cy / (3 * a)};
   }

   static float smooth(float v) {float t = Math.max(0, Math.min(1, v)); return t * t * (3 - 2 * t);}
}

package dev.baranhan.viltrumitecore.client.thanos.war;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ShipPath;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Sanctuary II, built in code (own design, no external model): a long armoured hull with a dorsal
 * spine and bridge, four great arms that reach out and hook down like claws, gun pods under the arms
 * and the hub ({@link ShipPath#EMITTERS}), three stern engines, ribs, plating and greebles, rows of lit
 * windows. Ship frame: x across (symmetric), y up, z forward, blocks; about 180 x 60 x 165.
 * <p>Three levels of detail, each a static {@link VertexBuffer} pair (POSITION_COLOR): the hull with
 * baked light and the emissive lights. LOD 0 ~10k quads (close), LOD 1 ~1.5k (mid), LOD 2 ~400 (far).
 * Render thread only; {@link #close} frees the buffers.
 */
final class SanctuaryMesh implements AutoCloseable {
   static final int LODS = 3;
   static final double BOW = 84.0;
   static final double STERN = -72.0;
   static final double SPAN = 90.0;
   private static final Vec3 SUN = new Vec3(0.35, 0.85, 0.3).normalize();
   private static final int HULL = 0x57524C;
   private static final int HULL_DK = 0x3C3834;
   private static final int UNDER = 0x302C29;
   private static final int BRONZE = 0x7C5E36;
   private static final int ARM = 0x4A4642;
   private static final int GREEBLE = 0x4F4B46;
   private static final int ENGINE = 0x2A2826;
   private static final int WINDOW = 0xFFC274;
   private static final int RUNNING = 0xFF5A2A;
   private static final int GUN = 0xFFB060;
   private static final int THRUST = 0xFFD9A0;
   /** Hull stations: z, half width, top, bottom. */
   private static final double[][] STATIONS = {{STERN, 18, 6, -10}, {-60, 22, 9, -13}, {-30, 24, 11, -16}, {0, 24, 12, -18}, {30, 22, 11, -15},
      {55, 16, 9, -11}, {72, 9, 6, -7}, {BOW, 2.5, 2, -3}};
   /** Right arm path in the x/y plane (the left mirrors it): out along the top, then down into the claw. */
   private static final double[][] ARM_PATH = {{16, 3}, {34, 4.5}, {52, 2.5}, {66, -4}, {78, -14}, {86, -28}, {89, -42}};

   private final VertexBuffer[] hull = new VertexBuffer[LODS];
   private final VertexBuffer[] glow = new VertexBuffer[LODS];
   final int[] quads = new int[LODS];

   private SanctuaryMesh() {
   }

   static SanctuaryMesh build() {
      SanctuaryMesh m = new SanctuaryMesh();
      for (int lod = 0; lod < LODS; lod++) {
         Geo h = new Geo(91L + lod);
         Geo g = new Geo(17L + lod);
         shape(h, g, lod);
         m.quads[lod] = h.quads + g.quads;
         m.hull[lod] = h.upload();
         m.glow[lod] = g.upload();
      }

      return m;
   }

   @Nullable
   VertexBuffer hull(int lod) {
      return this.hull[Mth.clamp(lod, 0, LODS - 1)];
   }

   @Nullable
   VertexBuffer glow(int lod) {
      return this.glow[Mth.clamp(lod, 0, LODS - 1)];
   }

   @Override
   public void close() {
      for (int i = 0; i < LODS; i++) {
         if (this.hull[i] != null) {
            this.hull[i].close();
            this.hull[i] = null;
         }

         if (this.glow[i] != null) {
            this.glow[i].close();
            this.glow[i] = null;
         }
      }
   }

   // ---- profile ----------------------------------------------------------------------------------

   /** Hull section at z: {half width, top, bottom}, smooth between the stations. */
   static double[] section(double z) {
      z = Mth.clamp(z, STERN, BOW);
      for (int i = 0; i < STATIONS.length - 1; i++) {
         double[] a = STATIONS[i];
         double[] b = STATIONS[i + 1];
         if (z <= b[0]) {
            double f = (z - a[0]) / (b[0] - a[0]);
            f = f * f * (3.0 - 2.0 * f);
            return new double[]{Mth.lerp(f, a[1], b[1]), Mth.lerp(f, a[2], b[2]), Mth.lerp(f, a[3], b[3])};
         }
      }

      double[] last = STATIONS[STATIONS.length - 1];
      return new double[]{last[1], last[2], last[3]};
   }

   /** Octagonal hull ring at z (flat deck, slab sides, a wide keel). */
   private static Vec3[] ring(double z, double grow) {
      double[] s = section(z);
      double hw = s[0] * grow;
      double top = s[1] * grow;
      double bot = s[2] * grow;
      return new Vec3[]{new Vec3(hw * 0.55, top, z), new Vec3(hw, top * 0.35, z), new Vec3(hw, bot * 0.4, z), new Vec3(hw * 0.6, bot, z),
         new Vec3(-hw * 0.6, bot, z), new Vec3(-hw, bot * 0.4, z), new Vec3(-hw, top * 0.35, z), new Vec3(-hw * 0.55, top, z)};
   }

   /** How much of the ground under ship point (x, z) is in its shadow, 0..1 (soft edges). */
   static float footprint(double x, double z) {
      float c = 0.0F;
      if (z > STERN - 3.0 && z < BOW + 3.0) {
         double hw = section(z)[0] + 2.0;
         c = Math.max(c, soft((hw - Math.abs(x)) / 5.0) * soft((Math.min(z - STERN, BOW - z) + 3.0) / 6.0));
      }

      double ax = Math.abs(x);
      if (ax < 92.0) {
         for (double az : new double[]{30.0, -30.0}) {
            double w = 7.5 - ax * 0.04;
            c = Math.max(c, soft((w - Math.abs(z - az)) / 3.0) * soft((92.0 - ax) / 6.0));
         }
      }

      return c;
   }

   private static float soft(double v) {
      return (float)Mth.clamp(v, 0.0, 1.0);
   }

   // ---- the build --------------------------------------------------------------------------------

   private static void shape(Geo h, Geo g, int lod) {
      int rings = lod == 0 ? 48 : lod == 1 ? 24 : 10;
      // hull
      List<Vec3[]> hullRings = new ArrayList<>();
      for (int i = 0; i <= rings; i++) {
         double z = Mth.lerp(i / (double)rings, STERN, BOW);
         hullRings.add(ring(z, 1.0));
      }

      h.loft(hullRings, HULL, HULL_DK, true, true);
      if (lod < 2) {
         // armour ribs every 12 blocks
         for (double z = -60.0; z <= 66.0; z += 12.0) {
            List<Vec3[]> rib = new ArrayList<>();
            rib.add(ring(z - 0.8, 1.035));
            rib.add(ring(z + 0.8, 1.035));
            h.loft(rib, BRONZE, HULL_DK, false, false);
         }
      }

      // dorsal spine and the bridge tower
      List<Vec3[]> spine = new ArrayList<>();
      int spineRings = lod == 0 ? 16 : 6;
      for (int i = 0; i <= spineRings; i++) {
         double z = Mth.lerp(i / (double)spineRings, -54.0, 62.0);
         double[] s = section(z);
         double f = Math.sin(Math.PI * i / spineRings);
         double w = 3.0 + 3.5 * f;
         double top = s[1] + 1.5 + 5.0 * f;
         spine.add(new Vec3[]{new Vec3(w, s[1] - 0.5, z), new Vec3(w * 0.5, top, z), new Vec3(-w * 0.5, top, z), new Vec3(-w, s[1] - 0.5, z)});
      }

      h.loft(spine, HULL_DK, HULL_DK, true, true);
      h.box(-5.0, 15.0, 34.0, 5.0, 21.0, 48.0, HULL);
      h.box(-3.5, 21.0, 36.0, 3.5, 24.0, 44.0, BRONZE);
      // the bow ram and the keel blade
      h.box(-1.2, -5.0, 70.0, 1.2, 1.0, BOW + 8.0, HULL_DK);
      h.box(-1.0, -22.0, -40.0, 1.0, -16.0, 40.0, HULL_DK);
      // arms
      int armSegs = lod == 0 ? 0 : lod == 1 ? 1 : 2;
      for (int side = -1; side <= 1; side += 2) {
         for (double az : new double[]{30.0, -30.0}) {
            arm(h, g, lod, side, az, armSegs);
         }
      }

      // hub turret under the middle (the main gun, emitter 0)
      pod(h, g, lod, 0.0, 0.0, -15.0, -22.0, 11.0, true);
      // gun pods under the arms (emitters 1..4)
      for (int i = 1; i < ShipPath.EMITTERS.length; i++) {
         double[] e = ShipPath.EMITTERS[i];
         pod(h, g, lod, e[0], e[2], 0.0, e[1] - 1.0, 4.2, false);
      }

      // stern engines
      double[][] engines = {{0.0, -1.0, 5.5}, {11.0, -4.0, 4.0}, {-11.0, -4.0, 4.0}};
      for (double[] en : engines) {
         int segs = lod == 2 ? 6 : 12;
         List<Vec3[]> tube = new ArrayList<>();
         tube.add(circleZ(en[0], en[1], STERN + 2.0, en[2] * 1.15, segs));
         tube.add(circleZ(en[0], en[1], STERN - 5.0, en[2], segs));
         h.loft(tube, ENGINE, ENGINE, false, false);
         g.disc(new Vec3(en[0], en[1], STERN - 4.6), new Vec3(0.0, 0.0, -1.0), en[2] * 0.9, segs, THRUST, 255, 150);
         g.disc(new Vec3(en[0], en[1], STERN - 4.8), new Vec3(0.0, 0.0, -1.0), en[2] * 1.4, segs, 0xFF8A3A, 90, 0);
      }

      if (lod == 0) {
         greebles(h);
      }

      windows(g, lod);
   }

   private static void arm(Geo h, Geo g, int lod, int side, double az, int coarse) {
      List<Vec3[]> rings = new ArrayList<>();
      int n = ARM_PATH.length;
      for (int i = 0; i < n; i += coarse == 2 && i < n - 1 ? 2 : 1) {
         double[] p = ARM_PATH[i];
         double[] prev = ARM_PATH[Math.max(0, i - 1)];
         double[] next = ARM_PATH[Math.min(n - 1, i + 1)];
         double dx = next[0] - prev[0];
         double dy = next[1] - prev[1];
         double len = Math.sqrt(dx * dx + dy * dy);
         double nx = -dy / len;
         double ny = dx / len;
         double f = i / (double)(n - 1);
         double thick = Mth.lerp(f, 10.0, 2.6) * 0.5;
         double wide = Mth.lerp(f, 14.0, 3.2) * 0.5;
         double sweep = f > 0.6 ? (f - 0.6) * 12.0 * Math.signum(az) : 0.0;
         double x = p[0] * side;
         double z = az + sweep;
         rings.add(new Vec3[]{new Vec3(x + nx * thick * side, p[1] + ny * thick, z - wide), new Vec3(x + nx * thick * side, p[1] + ny * thick, z + wide),
            new Vec3(x - nx * thick * side, p[1] - ny * thick, z + wide), new Vec3(x - nx * thick * side, p[1] - ny * thick, z - wide)});
      }

      h.loft(rings, ARM, UNDER, false, true);
      if (lod < 2) {
         // a dorsal fin on the arm root and claw spikes at the hook
         h.box(side * 22.0 - 1.0, 7.0, az - 5.0, side * 22.0 + 1.0, 12.0, az + 5.0, HULL_DK);
         h.box(side * 30.0 - 0.8, 7.5, az - 3.5, side * 30.0 + 0.8, 10.0, az + 3.5, HULL_DK);
         for (int k = 0; k < 3; k++) {
            double x = side * (74.0 + k * 5.0);
            double y = -10.0 - k * 8.0;
            h.box(x - 1.0, y - 5.0, az - 1.0, x + 1.0, y, az + 1.0, BRONZE);
         }
      }

      // running lights along the underside
      int step = lod == 0 ? 6 : 12;
      for (int x = 20; x < 80; x += step) {
         double y = armBottom(x);
         g.quadFacing(new Vec3(side * x, y - 0.05, az), 0.5, RUNNING, 230);
      }
   }

   /** Underside height of the arm at |x| (the path minus half its thickness). */
   private static double armBottom(double x) {
      for (int i = 0; i < ARM_PATH.length - 1; i++) {
         double[] a = ARM_PATH[i];
         double[] b = ARM_PATH[i + 1];
         if (x <= b[0]) {
            double f = Mth.clamp((x - a[0]) / (b[0] - a[0]), 0.0, 1.0);
            double y = Mth.lerp(f, a[1], b[1]);
            double t = Mth.lerp((i + f) / (ARM_PATH.length - 1), 10.0, 2.6) * 0.5;
            return y - t;
         }
      }

      return ARM_PATH[ARM_PATH.length - 1][1];
   }

   /** A gun turret: a tapering octagonal drum from {@code yTop} down to {@code yBottom}, its muzzle lit. */
   private static void pod(Geo h, Geo g, int lod, double x, double z, double yTop, double yBottom, double radius, boolean hub) {
      int segs = lod == 0 ? 16 : lod == 1 ? 10 : 6;
      double top = hub ? yTop : armBottom(Math.abs(x)) + 0.8;
      List<Vec3[]> drum = new ArrayList<>();
      drum.add(circleY(x, top, z, radius * 1.1, segs));
      drum.add(circleY(x, Mth.lerp(0.5, top, yBottom), z, radius, segs));
      drum.add(circleY(x, yBottom + 0.6, z, radius * 0.75, segs));
      drum.add(circleY(x, yBottom, z, radius * 0.55, segs));
      h.loft(drum, hub ? HULL_DK : ARM, UNDER, false, false);
      Vec3 c = new Vec3(x, yBottom - 0.05, z);
      Vec3 down = new Vec3(0.0, -1.0, 0.0);
      g.disc(c, down, radius * 0.5, segs, GUN, 255, 170);
      g.disc(c.add(0.0, -0.05, 0.0), down, radius * 0.95, segs, 0xFF7A2A, 110, 0);
      if (hub && lod < 2) {
         for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            g.quadFacing(new Vec3(x + Math.cos(a) * radius * 0.9, Mth.lerp(0.5, top, yBottom), z + Math.sin(a) * radius * 0.9), 0.6, GUN, 220);
         }
      }
   }

   private static void greebles(Geo h) {
      RandomSource r = RandomSource.create(4242L);
      // the deck: blocks, vents and towers
      for (int i = 0; i < 900; i++) {
         double z = Mth.lerp(r.nextDouble(), -64.0, 70.0);
         double[] s = section(z);
         double x = (r.nextDouble() * 2.0 - 1.0) * s[0] * 0.5;
         double w = 0.8 + r.nextDouble() * 3.0;
         double d = 0.8 + r.nextDouble() * 5.0;
         double ht = 0.3 + r.nextDouble() * r.nextDouble() * 3.5;
         h.box(x - w / 2.0, s[1] - 0.1, z - d / 2.0, x + w / 2.0, s[1] + ht, z + d / 2.0, r.nextInt(5) == 0 ? BRONZE : GREEBLE);
      }

      // the flanks: armour slabs and pipes
      for (int i = 0; i < 500; i++) {
         double z = Mth.lerp(r.nextDouble(), -66.0, 66.0);
         double[] s = section(z);
         int side = r.nextBoolean() ? 1 : -1;
         double y = Mth.lerp(r.nextDouble(), s[2] * 0.4 + 1.0, s[1] * 0.35 - 1.0);
         double out = 0.3 + r.nextDouble() * 1.2;
         double hh = 0.4 + r.nextDouble() * 2.2;
         double d = 1.0 + r.nextDouble() * 6.0;
         double x0 = side * s[0];
         h.box(Math.min(x0, x0 + side * out) - 0.05, y - hh / 2.0, z - d / 2.0, Math.max(x0, x0 + side * out) + 0.05, y + hh / 2.0, z + d / 2.0,
            r.nextInt(6) == 0 ? BRONZE : GREEBLE);
      }

      // the belly: hanging machinery
      for (int i = 0; i < 300; i++) {
         double z = Mth.lerp(r.nextDouble(), -64.0, 64.0);
         double[] s = section(z);
         double x = (r.nextDouble() * 2.0 - 1.0) * s[0] * 0.55;
         double w = 0.8 + r.nextDouble() * 3.5;
         double d = 0.8 + r.nextDouble() * 4.0;
         double ht = 0.4 + r.nextDouble() * 2.0;
         h.box(x - w / 2.0, s[2] - ht, z - d / 2.0, x + w / 2.0, s[2] + 0.1, z + d / 2.0, UNDER);
      }

      // the arms' backs
      for (int i = 0; i < 260; i++) {
         int side = r.nextBoolean() ? 1 : -1;
         double az = r.nextBoolean() ? 30.0 : -30.0;
         double x = Mth.lerp(r.nextDouble(), 20.0, 62.0);
         double top = armBottom(x) + Mth.lerp((x - 16.0) / 73.0, 10.0, 2.6);
         double w = 0.8 + r.nextDouble() * 2.5;
         double d = 0.8 + r.nextDouble() * 3.0;
         double zz = az + (r.nextDouble() - 0.5) * 9.0;
         double ht = 0.3 + r.nextDouble() * 1.6;
         h.box(side * x - w / 2.0, top - 0.2, zz - d / 2.0, side * x + w / 2.0, top + ht, zz + d / 2.0, GREEBLE);
      }
   }

   private static void windows(Geo g, int lod) {
      RandomSource r = RandomSource.create(777L);
      double step = lod == 0 ? 1.4 : lod == 1 ? 4.2 : 0.0;
      if (step <= 0.0) {
         return;
      }

      double[] rows = {3.0, -1.0, -5.0};
      for (int side = -1; side <= 1; side += 2) {
         for (double row : rows) {
            for (double z = -62.0; z < 64.0; z += step) {
               if (r.nextInt(5) < 2) {
                  continue;
               }

               double[] s = section(z);
               if (row > s[1] * 0.35 - 0.8 || row < s[2] * 0.4 + 0.8) {
                  continue;
               }

               double x = side * (s[0] + 0.06);
               int a = 150 + r.nextInt(100);
               int rgb = r.nextInt(9) == 0 ? 0xBFE0FF : WINDOW;
               g.put(new Vec3(x, row - 0.25, z - 0.35), new Vec3(x, row + 0.25, z - 0.35), new Vec3(x, row + 0.25, z + 0.35), new Vec3(x, row - 0.25, z + 0.35),
                  rgb, a);
            }
         }
      }

      // deck lights along the spine
      for (double z = -50.0; z < 60.0; z += step * 3.0) {
         double[] s = section(z);
         g.quadFacing(new Vec3(s[0] * 0.5, s[1] + 0.1, z), 0.35, RUNNING, 200);
         g.quadFacing(new Vec3(-s[0] * 0.5, s[1] + 0.1, z), 0.35, RUNNING, 200);
      }
   }

   private static Vec3[] circleY(double x, double y, double z, double r, int segs) {
      Vec3[] out = new Vec3[segs];
      for (int i = 0; i < segs; i++) {
         double a = Math.PI * 2.0 * i / segs;
         out[i] = new Vec3(x + Math.cos(a) * r, y, z + Math.sin(a) * r);
      }

      return out;
   }

   private static Vec3[] circleZ(double x, double y, double z, double r, int segs) {
      Vec3[] out = new Vec3[segs];
      for (int i = 0; i < segs; i++) {
         double a = Math.PI * 2.0 * i / segs;
         out[i] = new Vec3(x + Math.cos(a) * r, y + Math.sin(a) * r, z);
      }

      return out;
   }

   // ---- geometry sink ----------------------------------------------------------------------------

   /** Quads with baked light into one buffer. */
   static final class Geo {
      final BufferBuilder buf = new BufferBuilder(1 << 16);
      final RandomSource rnd;
      int quads;

      Geo(long seed) {
         this.rnd = RandomSource.create(seed);
         this.buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      }

      /** A lit face; {@code inside} is any point inside the solid (the face normal is turned away from it). */
      void lit(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int under, Vec3 inside) {
         Vec3 n = b.subtract(a).cross(c.subtract(a));
         if (n.lengthSqr() < 1.0E-10) {
            n = c.subtract(a).cross(d.subtract(a));
         }

         if (n.lengthSqr() < 1.0E-10) {
            return;
         }

         n = n.normalize();
         Vec3 mid = a.add(b).add(c).add(d).scale(0.25);
         if (n.dot(mid.subtract(inside)) < 0.0) {
            n = n.reverse();
         }

         float sun = (float)Math.max(0.0, n.dot(SUN));
         float sky = (float)(0.5 + 0.5 * n.y);
         float l = (0.3F + 0.55F * sun + 0.22F * sky) * (0.93F + this.rnd.nextFloat() * 0.14F);
         int base = n.y < -0.55 ? under : rgb;
         this.put(a, b, c, d, scale(base, l), 255);
      }

      void put(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int alpha) {
         int r = rgb >> 16 & 255;
         int g = rgb >> 8 & 255;
         int bl = rgb & 255;
         this.buf.vertex(a.x, a.y, a.z).color(r, g, bl, alpha).endVertex();
         this.buf.vertex(b.x, b.y, b.z).color(r, g, bl, alpha).endVertex();
         this.buf.vertex(c.x, c.y, c.z).color(r, g, bl, alpha).endVertex();
         this.buf.vertex(d.x, d.y, d.z).color(r, g, bl, alpha).endVertex();
         this.quads++;
      }

      /** Connects consecutive rings (same point count) with quads; caps are fans of degenerate quads. */
      void loft(List<Vec3[]> rings, int rgb, int under, boolean capStart, boolean capEnd) {
         for (int i = 0; i < rings.size() - 1; i++) {
            Vec3[] r0 = rings.get(i);
            Vec3[] r1 = rings.get(i + 1);
            Vec3 inside = centre(r0).add(centre(r1)).scale(0.5);
            for (int j = 0; j < r0.length; j++) {
               int k = (j + 1) % r0.length;
               this.lit(r0[j], r0[k], r1[k], r1[j], rgb, under, inside);
            }
         }

         if (capStart && !rings.isEmpty()) {
            this.cap(rings.get(0), rings.size() > 1 ? centre(rings.get(1)) : centre(rings.get(0)), rgb, under);
         }

         if (capEnd && rings.size() > 1) {
            this.cap(rings.get(rings.size() - 1), centre(rings.get(rings.size() - 2)), rgb, under);
         }
      }

      private void cap(Vec3[] ring, Vec3 inside, int rgb, int under) {
         Vec3 c = centre(ring);
         for (int j = 0; j < ring.length; j++) {
            int k = (j + 1) % ring.length;
            this.lit(c, ring[j], ring[k], ring[k], rgb, under, inside);
         }
      }

      void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
         Vec3 in = new Vec3((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0);
         Vec3 a = new Vec3(x0, y0, z0);
         Vec3 b = new Vec3(x1, y0, z0);
         Vec3 c = new Vec3(x1, y0, z1);
         Vec3 d = new Vec3(x0, y0, z1);
         Vec3 e = new Vec3(x0, y1, z0);
         Vec3 f = new Vec3(x1, y1, z0);
         Vec3 g = new Vec3(x1, y1, z1);
         Vec3 hh = new Vec3(x0, y1, z1);
         this.lit(e, f, g, hh, rgb, UNDER, in);
         this.lit(a, b, c, d, rgb, UNDER, in);
         this.lit(a, b, f, e, rgb, UNDER, in);
         this.lit(d, c, g, hh, rgb, UNDER, in);
         this.lit(a, d, hh, e, rgb, UNDER, in);
         this.lit(b, c, g, f, rgb, UNDER, in);
      }

      /** Emissive disc (fan of degenerate quads), centre alpha {@code inner}, rim {@code outer}. */
      void disc(Vec3 c, Vec3 n, double radius, int segs, int rgb, int inner, int outer) {
         Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
         Vec3 s = n.cross(u).normalize();
         Vec3 t = n.cross(s).normalize();
         int r = rgb >> 16 & 255;
         int g = rgb >> 8 & 255;
         int bl = rgb & 255;
         for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2.0 * i / segs;
            double a1 = Math.PI * 2.0 * (i + 1) / segs;
            Vec3 p0 = c.add(s.scale(Math.cos(a0) * radius)).add(t.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(s.scale(Math.cos(a1) * radius)).add(t.scale(Math.sin(a1) * radius));
            this.buf.vertex(c.x, c.y, c.z).color(r, g, bl, inner).endVertex();
            this.buf.vertex(c.x, c.y, c.z).color(r, g, bl, inner).endVertex();
            this.buf.vertex(p0.x, p0.y, p0.z).color(r, g, bl, outer).endVertex();
            this.buf.vertex(p1.x, p1.y, p1.z).color(r, g, bl, outer).endVertex();
            this.quads++;
         }
      }

      /** A small lamp: a cross of two quads, visible from every side. */
      void quadFacing(Vec3 c, double size, int rgb, int alpha) {
         double h = size / 2.0;
         this.put(c.add(-h, -h, 0.0), c.add(h, -h, 0.0), c.add(h, h, 0.0), c.add(-h, h, 0.0), rgb, alpha);
         this.put(c.add(0.0, -h, -h), c.add(0.0, -h, h), c.add(0.0, h, h), c.add(0.0, h, -h), rgb, alpha);
         this.put(c.add(-h, 0.0, -h), c.add(h, 0.0, -h), c.add(h, 0.0, h), c.add(-h, 0.0, h), rgb, alpha);
      }

      @Nullable
      VertexBuffer upload() {
         BufferBuilder.RenderedBuffer rendered = this.buf.endOrDiscardIfEmpty();
         if (rendered == null) {
            return null;
         }

         VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
         vb.bind();
         vb.upload(rendered);
         VertexBuffer.unbind();
         return vb;
      }

      private static Vec3 centre(Vec3[] ring) {
         Vec3 c = Vec3.ZERO;
         for (Vec3 p : ring) {
            c = c.add(p);
         }

         return c.scale(1.0 / ring.length);
      }

      private static int scale(int rgb, float k) {
         int r = Mth.clamp((int)((rgb >> 16 & 255) * k), 0, 255);
         int g = Mth.clamp((int)((rgb >> 8 & 255) * k), 0, 255);
         int b = Mth.clamp((int)((rgb & 255) * k), 0, 255);
         return r << 16 | g << 8 | b;
      }
   }
}

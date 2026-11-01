package dev.baranhan.viltrumitecore.client.collapse;

import dev.baranhan.viltrumitecore.world.collapse.CollapseAnalyzer;
import dev.baranhan.viltrumitecore.world.collapse.CollapseRules;
import dev.baranhan.viltrumitecore.world.collapse.CollapseVariant;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure kinematics of a collapsing section (no Minecraft client classes, unit tested). The section is cut into
 * irregular fragments (seeded, jittered cells; facade slabs for a shear; floor slabs for a pancake) and every
 * fragment gets a precomputed track: position of its pivot and a rotation quaternion per tick from the
 * moment the server removes the blocks. The variant decides the motion: TOPPLE swings the whole section
 * over a hinge at its base until the pieces break away; FACADE_SHEAR peels the outer face top-first;
 * PANCAKE drops the floors through each other; CRUMBLE lets small pieces fall loose. Timing is aimed at the
 * server's impact tick ({@link CollapseRules#impactTicks}), so the slam, the damage and the dust line up.
 */
public final class CollapseMotion {
   /** Fragments rest on the ground this long, then sink into the dust. */
   public static final int REST_TICKS = 40;
   public static final int SINK_TICKS = 20;
   public static final double SINK_DEPTH = 1.3;

   /** Where a fragment comes to rest: the first air block above the ground at a column. */
   @FunctionalInterface
   public interface Ground {
      int surface(int x, int z);
   }

   public static final class Piece {
      public final int[] members;
      public final double ox, oy, oz;
      public final float half;
      public final boolean facade;
      public int delay;
      /** Tick (relative to the collapse) the fragment lands, or -1. */
      public int land = -1;
      public int length;
      public float[] x, y, z, qx, qy, qz, qw;

      Piece(int[] members, double ox, double oy, double oz, float half, boolean facade) {
         this.members = members;
         this.ox = ox;
         this.oy = oy;
         this.oz = oz;
         this.half = half;
         this.facade = facade;
      }

      void alloc(int n) {
         this.length = n;
         this.x = new float[n];
         this.y = new float[n];
         this.z = new float[n];
         this.qx = new float[n];
         this.qy = new float[n];
         this.qz = new float[n];
         this.qw = new float[n];
      }

      /** Track sample, clamped to the last one; after landing the piece rests, then sinks. */
      public int at(int t) {
         return Math.max(0, Math.min(this.length - 1, t));
      }

      public double sink(int t) {
         if (this.land < 0 || t < this.land + REST_TICKS) {
            return 0.0;
         }
         return SINK_DEPTH * Math.min(1.0, (t - this.land - REST_TICKS) / (double)SINK_TICKS);
      }

      /** True once the piece is gone (landed, rested and sunk). */
      public boolean done(int t) {
         return this.land >= 0 && t > this.land + REST_TICKS + SINK_TICKS;
      }
   }

   private CollapseMotion() {
   }

   private static double u(long seed, int a, int b, int c) {
      return (CollapseAnalyzer.hash(seed, a, b, c) >>> 11) / (double)(1L << 52);
   }

   /** Cuts the section into at most {@code maxPieces} fragments and computes their tracks. */
   public static List<Piece> build(int[] bx, int[] by, int[] bz, CollapseVariant variant, long seed, float fdx, float fdz, int surfaceY, int impactTicks,
      int maxPieces, Ground ground) {
      int n = bx.length;
      List<Piece> out = new ArrayList<>();
      if (n == 0) {
         return out;
      }
      int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
      for (int i = 0; i < n; i++) {
         minX = Math.min(minX, bx[i]);
         minY = Math.min(minY, by[i]);
         minZ = Math.min(minZ, bz[i]);
         maxX = Math.max(maxX, bx[i]);
         maxY = Math.max(maxY, by[i]);
         maxZ = Math.max(maxZ, bz[i]);
      }
      double len = Math.hypot(fdx, fdz);
      double dx = len < 1.0E-4 ? 1.0 : fdx / len, dz = len < 1.0E-4 ? 0.0 : fdz / len;
      double cx = (minX + maxX + 1) * 0.5, cz = (minZ + maxZ + 1) * 0.5;
      double extent = 0.5;
      for (int i = 0; i < n; i++) {
         extent = Math.max(extent, (bx[i] + 0.5 - cx) * dx + (bz[i] + 0.5 - cz) * dz + 0.5);
      }
      int target = Math.max(1, Math.min(maxPieces, switch (variant) {
         case TOPPLE -> 90;
         case FACADE_SHEAR -> 84;
         case PANCAKE -> 80;
         case CRUMBLE -> 110;
      }));
      int size = Math.max(1, (int)Math.ceil(Math.cbrt(n / (double)target)));
      List<int[]> groups;
      boolean[] facade = new boolean[n];
      if (variant == CollapseVariant.FACADE_SHEAR) {
         for (int i = 0; i < n; i++) {
            facade[i] = (bx[i] + 0.5 - cx) * dx + (bz[i] + 0.5 - cz) * dz >= extent - 2.0;
         }
      }
      while (true) {
         groups = group(bx, by, bz, facade, variant, seed, size, minX, minY, minZ, dx, dz);
         if (groups.size() <= maxPieces || size >= 12) {
            break;
         }
         size++;
      }
      for (int[] g : groups) {
         double px = 0, py = 0, pz = 0;
         int gx0 = Integer.MAX_VALUE, gy0 = Integer.MAX_VALUE, gz0 = Integer.MAX_VALUE, gx1 = Integer.MIN_VALUE, gy1 = Integer.MIN_VALUE, gz1 = Integer.MIN_VALUE;
         for (int i : g) {
            px += bx[i] + 0.5;
            py += by[i] + 0.5;
            pz += bz[i] + 0.5;
            gx0 = Math.min(gx0, bx[i]);
            gy0 = Math.min(gy0, by[i]);
            gz0 = Math.min(gz0, bz[i]);
            gx1 = Math.max(gx1, bx[i]);
            gy1 = Math.max(gy1, by[i]);
            gz1 = Math.max(gz1, bz[i]);
         }
         float half = 0.5F * (float)Math.max(gy1 - gy0 + 1, Math.min(gx1 - gx0 + 1, gz1 - gz0 + 1)) * 0.6F + 0.25F;
         out.add(new Piece(g, px / g.length, py / g.length, pz / g.length, half, facade[g[0]]));
      }
      Section s = new Section(minX, minY, minZ, maxX, maxY, maxZ, cx, cz, dx, dz, extent, surfaceY, Math.max(4, impactTicks), seed, ground);
      for (int i = 0; i < out.size(); i++) {
         track(out.get(i), i, variant, s);
      }
      return out;
   }

   private static List<int[]> group(int[] bx, int[] by, int[] bz, boolean[] facade, CollapseVariant variant, long seed, int size, int minX, int minY, int minZ,
      double dx, double dz) {
      int sx = size, sy = size, sz = size;
      switch (variant) {
         case PANCAKE -> {
            sy = Math.max(2, size);
            sx = sz = size + 1;
         }
         case CRUMBLE -> {
            sx = sz = Math.max(1, size);
            sy = Math.max(1, size - 1);
         }
         default -> {
         }
      }
      Long2ObjectLinkedOpenHashMap<IntArrayList> cells = new Long2ObjectLinkedOpenHashMap<>();
      for (int i = 0; i < bx.length; i++) {
         int x = bx[i] - minX, y = by[i] - minY, z = bz[i] - minZ;
         long key;
         if (facade[i]) {
            // facade slabs: wide across the face, 2 deep, a little taller than the cells
            int w = size + 1, h = size + 2;
            double across = -x * dz + z * dx;
            int jy = (int)(CollapseAnalyzer.hash(seed, (int)Math.floor(across / w), 5, 0) % h);
            key = pack(Math.floorDiv((int)Math.floor(across) + 4096, w), Math.floorDiv(y + jy, h), 1);
            key |= 1L << 62;
         } else {
            int jx = (int)(CollapseAnalyzer.hash(seed, 1, Math.floorDiv(y, sy), 0) % sx);
            int jz = (int)(CollapseAnalyzer.hash(seed, 2, Math.floorDiv(y, sy), 0) % sz);
            int jy = (int)(CollapseAnalyzer.hash(seed, x >> 2, 3, z >> 2) % sy);
            key = pack(Math.floorDiv(x + jx, sx), Math.floorDiv(y + jy, sy), Math.floorDiv(z + jz, sz));
         }
         IntArrayList list = cells.get(key);
         if (list == null) {
            list = new IntArrayList();
            cells.put(key, list);
         }
         list.add(i);
      }
      List<int[]> groups = new ArrayList<>(cells.size());
      for (IntArrayList l : cells.values()) {
         groups.add(l.toIntArray());
      }
      return groups;
   }

   private static long pack(int a, int b, int c) {
      return ((long)(a & 0xFFFFF) << 40) | ((long)(b & 0xFFFFF) << 20) | (c & 0xFFFFF);
   }

   private record Section(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double cx, double cz, double dx, double dz, double extent,
      int surfaceY, int impact, long seed, Ground ground) {
      int height() {
         return this.maxY - this.minY + 1;
      }
   }

   // ------------------------------------------------------------------ tracks

   private static void track(Piece p, int index, CollapseVariant variant, Section s) {
      int length = Math.min(260, s.impact * 3 + 60);
      p.alloc(length);
      double rel = (p.oy - s.minY) / Math.max(1.0, s.height());
      long seed = s.seed;
      double u0 = u(seed, index, 11, 0), u1 = u(seed, index, 12, 0), u2 = u(seed, index, 13, 0), u3 = u(seed, index, 14, 0);
      // spin axis for the free flight
      double sax = u1 - 0.5, say = (u2 - 0.5) * 0.6, saz = u3 - 0.5;
      double sl = Math.sqrt(sax * sax + say * say + saz * saz);
      if (sl < 1.0E-3) {
         sax = 1.0;
         sl = 1.0;
      }
      sax /= sl;
      say /= sl;
      saz /= sl;
      Motion m = new Motion();
      m.x = p.ox;
      m.y = p.oy;
      m.z = p.oz;
      m.qw = 1.0;
      switch (variant) {
         case TOPPLE -> {
            if (rel < 0.18) {
               dropTrack(p, s, m, 2 + (int)(u0 * 6), 0.025 * (u1 - 0.5), 0.025 * (u3 - 0.5), sax, say, saz, 0.03 + 0.05 * u2);
            } else {
               // the section swings over its base edge on the fall side as one body, the pieces break away on the way
               double tilt = (u(seed, 0, 21, 0) - 0.5) * 0.3;
               double ax = s.dz * Math.cos(tilt) - s.dx * Math.sin(tilt) * 0.0, az = -s.dx * Math.cos(tilt);
               double ay = Math.sin(tilt) * 0.25;
               double al = Math.sqrt(ax * ax + ay * ay + az * az);
               ax /= al;
               ay /= al;
               az /= al;
               double hx = s.cx + s.dx * s.extent, hy = s.minY, hz = s.cz + s.dz * s.extent;
               double breakAt = 0.35 + 0.75 * u0 * (0.6 + 0.4 * rel);
               swingTrack(p, s, m, 0, hx, hy, hz, ax, ay, az, Math.PI * 0.5, s.impact * 0.92, breakAt, sax, say, saz, 0.3 + 0.9 * u2);
            }
         }
         case FACADE_SHEAR -> {
            if (p.facade) {
               int top = 0;
               for (int i : p.members) {
                  top = Math.max(top, i);
               }
               int delay = (int)Math.round((1.0 - rel) * Math.min(14.0, s.impact * 0.35) + u0 * 4.0);
               double hx = p.ox + s.dx * 1.0, hy = p.oy - p.half, hz = p.oz + s.dz * 1.0;
               double ax = s.dz, ay = 0.0, az = -s.dx;
               double breakAt = 0.25 + 0.45 * u1;
               swingTrack(p, s, m, delay, hx, hy, hz, ax, ay, az, Math.PI * 0.45, Math.max(6.0, s.impact * 0.55), breakAt, sax, say, saz, 0.4 + 0.8 * u2);
            } else {
               int delay = 4 + (int)(u0 * 6 + (1.0 - rel) * 3.0);
               dropTrack(p, s, m, delay, s.dx * (0.02 + 0.03 * u1), s.dz * (0.02 + 0.03 * u1), sax, say, saz, 0.03 + 0.06 * u2);
            }
         }
         case PANCAKE -> {
            int delay = (int)Math.round(rel * 4.0 + u0 * 3.0);
            dropTrack(p, s, m, delay, 0.03 * (u1 - 0.5), 0.03 * (u3 - 0.5), sax, say, saz, 0.015 + 0.035 * u2);
         }
         case CRUMBLE -> {
            int delay = (int)Math.round(u0 * 10.0 + (1.0 - rel) * 4.0);
            double ox = p.ox - s.cx, oz = p.oz - s.cz;
            double ol = Math.max(0.5, Math.hypot(ox, oz));
            double out = 0.015 + 0.045 * u1;
            dropTrack(p, s, m, delay, ox / ol * out + s.dx * 0.02, oz / ol * out + s.dz * 0.02, sax, say, saz, 0.08 + 0.22 * u2);
         }
      }
   }

   private static final class Motion {
      double x, y, z, vx, vy, vz;
      double qx, qy, qz, qw;
   }

   private static void store(Piece p, int t, Motion m) {
      p.x[t] = (float)m.x;
      p.y[t] = (float)m.y;
      p.z[t] = (float)m.z;
      p.qx[t] = (float)m.qx;
      p.qy[t] = (float)m.qy;
      p.qz[t] = (float)m.qz;
      p.qw[t] = (float)m.qw;
   }

   /** True (and the motion clamped) when the piece touches the ground at its column. */
   private static boolean grounded(Piece p, Section s, Motion m) {
      int fx = (int)Math.floor(m.x), fz = (int)Math.floor(m.z);
      boolean under = fx >= s.minX && fx <= s.maxX && fz >= s.minZ && fz <= s.maxZ;
      int surface = under ? s.surfaceY : Math.max(s.surfaceY - 8, Math.min(s.surfaceY + 12, s.ground.surface(fx, fz)));
      double floor = surface + p.half * 0.55;
      if (m.y <= floor) {
         m.y = floor;
         return true;
      }
      return false;
   }

   /** Free fall after {@code delay} ticks at rest, with a small drift and spin. */
   private static void dropTrack(Piece p, Section s, Motion m, int delay, double vx, double vz, double sax, double say, double saz, double spin) {
      int t = 0;
      for (; t < Math.min(delay, p.length); t++) {
         store(p, t, m);
      }
      m.vx = vx;
      m.vz = vz;
      m.vy = 0.0;
      int start = t;
      for (; t < p.length; t++) {
         m.vy -= CollapseRules.GRAVITY;
         m.x += m.vx;
         m.y += m.vy;
         m.z += m.vz;
         m.vy *= 0.985;
         setQuat(m, sax, say, saz, spin * (t - start));
         if (grounded(p, s, m)) {
            store(p, t, m);
            land(p, t);
            return;
         }
         store(p, t, m);
      }
   }

   /**
    * Rigid swing about a hinge: the angle grows as (t/T)^2 to {@code maxAngle} at {@code T}; once it passes
    * {@code breakAt} the piece flies free with the swing's velocity and its own spin.
    */
   private static void swingTrack(Piece p, Section s, Motion m, int delay, double hx, double hy, double hz, double ax, double ay, double az, double maxAngle,
      double T, double breakAt, double sax, double say, double saz, double spinScale) {
      int t = 0;
      for (; t < Math.min(delay, p.length); t++) {
         store(p, t, m);
      }
      double rx = p.ox - hx, ry = p.oy - hy, rz = p.oz - hz;
      double prevX = m.x, prevY = m.y, prevZ = m.z;
      double bqx = 0, bqy = 0, bqz = 0, bqw = 1;
      boolean free = false;
      int freeAt = 0;
      double spin = 0.0;
      for (; t < p.length; t++) {
         if (!free) {
            double k = (t - delay + 1) / T;
            double angle = maxAngle * Math.min(1.0, k * k);
            double c = Math.cos(angle * 0.5), sn = Math.sin(angle * 0.5);
            double qx = ax * sn, qy = ay * sn, qz = az * sn, qw = c;
            double[] r = rotate(qx, qy, qz, qw, rx, ry, rz);
            prevX = m.x;
            prevY = m.y;
            prevZ = m.z;
            m.x = hx + r[0];
            m.y = hy + r[1];
            m.z = hz + r[2];
            m.qx = qx;
            m.qy = qy;
            m.qz = qz;
            m.qw = qw;
            if (angle >= breakAt || k >= 1.0) {
               free = true;
               freeAt = t;
               m.vx = m.x - prevX;
               m.vy = m.y - prevY;
               m.vz = m.z - prevZ;
               bqx = qx;
               bqy = qy;
               bqz = qz;
               bqw = qw;
               // keep the swing's angular speed as the starting spin
               spin = spinScale * maxAngle * 2.0 * Math.min(1.0, k) / T;
            }
         } else {
            m.vy -= CollapseRules.GRAVITY;
            m.x += m.vx;
            m.y += m.vy;
            m.z += m.vz;
            m.vx *= 0.99;
            m.vz *= 0.99;
            double a = spin * (t - freeAt);
            double c = Math.cos(a * 0.5), sn = Math.sin(a * 0.5);
            double[] q = mul(sax * sn, say * sn, saz * sn, c, bqx, bqy, bqz, bqw);
            m.qx = q[0];
            m.qy = q[1];
            m.qz = q[2];
            m.qw = q[3];
         }
         if (grounded(p, s, m)) {
            store(p, t, m);
            land(p, t);
            return;
         }
         store(p, t, m);
      }
   }

   private static void land(Piece p, int t) {
      p.land = t;
      // the rest of the track holds the resting pose
      for (int i = t + 1; i < p.length; i++) {
         p.x[i] = p.x[t];
         p.y[i] = p.y[t];
         p.z[i] = p.z[t];
         p.qx[i] = p.qx[t];
         p.qy[i] = p.qy[t];
         p.qz[i] = p.qz[t];
         p.qw[i] = p.qw[t];
      }
   }

   private static void setQuat(Motion m, double ax, double ay, double az, double angle) {
      double c = Math.cos(angle * 0.5), s = Math.sin(angle * 0.5);
      m.qx = ax * s;
      m.qy = ay * s;
      m.qz = az * s;
      m.qw = c;
   }

   /** Hamilton product a*b. */
   static double[] mul(double ax, double ay, double az, double aw, double bx, double by, double bz, double bw) {
      return new double[]{aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx, aw * bz + ax * by - ay * bx + az * bw,
         aw * bw - ax * bx - ay * by - az * bz};
   }

   /** Rotates v by the unit quaternion q. */
   static double[] rotate(double qx, double qy, double qz, double qw, double vx, double vy, double vz) {
      // t = 2 * cross(q.xyz, v); v' = v + w * t + cross(q.xyz, t)
      double tx = 2.0 * (qy * vz - qz * vy), ty = 2.0 * (qz * vx - qx * vz), tz = 2.0 * (qx * vy - qy * vx);
      return new double[]{vx + qw * tx + (qy * tz - qz * ty), vy + qw * ty + (qz * tx - qx * tz), vz + qw * tz + (qx * ty - qy * tx)};
   }
}

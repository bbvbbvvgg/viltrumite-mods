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

   /**
    * Longest free flight a track may simulate (60 s). A track always ends on a real contact before this; the cap
    * only guards against a column with no floor at all (the void), where the piece ends below the world.
    */
   public static final int MAX_FLIGHT_TICKS = 1200;

   /** Where a fragment comes to rest. */
   @FunctionalInterface
   public interface Ground {
      /** A column whose blocks are not known here (unloaded chunk): the piece lands on the section's ground. */
      int UNKNOWN = Integer.MIN_VALUE;

      /** The first air block above the ground at a column. */
      int surface(int x, int z);

      /**
       * The landing surface under {@code fromY}: the first free y above the highest solid block at or below it
       * (a roof, a floor slab, a ledge, the street), or {@link #UNKNOWN}.
       */
      default int surface(int x, int z, int fromY) {
         return this.surface(x, z);
      }

      /** Whether the block is solid (a facade the piece runs into sideways). */
      default boolean solid(int x, int y, int z) {
         return false;
      }
   }

   public static final class Piece {
      public final int[] members;
      public final double ox, oy, oz;
      public final float half;
      public final boolean facade;
      public int delay;
      /** Tick (relative to the collapse) the fragment lands, or -1. */
      public int land = -1;
      /** Samples in the track; the last one is the resting pose (a track always ends on a contact). */
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

      /** Initial capacity of the track; it grows while the piece is still flying (no fixed length). */
      void alloc(int n) {
         int cap = Math.max(8, n);
         this.length = 0;
         this.x = new float[cap];
         this.y = new float[cap];
         this.z = new float[cap];
         this.qx = new float[cap];
         this.qy = new float[cap];
         this.qz = new float[cap];
         this.qw = new float[cap];
      }

      void ensure(int n) {
         if (n <= this.x.length) {
            return;
         }
         int cap = Math.max(n, this.x.length * 2);
         this.x = java.util.Arrays.copyOf(this.x, cap);
         this.y = java.util.Arrays.copyOf(this.y, cap);
         this.z = java.util.Arrays.copyOf(this.z, cap);
         this.qx = java.util.Arrays.copyOf(this.qx, cap);
         this.qy = java.util.Arrays.copyOf(this.qy, cap);
         this.qz = java.util.Arrays.copyOf(this.qz, cap);
         this.qw = java.util.Arrays.copyOf(this.qw, cap);
      }

      /** In the air at track tick {@code t}: started and not yet on a surface. */
      public boolean airborne(int t) {
         return this.land < 0 || t < this.land;
      }

      /** Resting on its surface (before it sinks into the dust): the window in which that surface is watched. */
      public boolean resting(int t) {
         return this.land >= 0 && t >= this.land && t < this.land + REST_TICKS;
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
      p.alloc(Math.min(260, s.impact * 3 + 60));
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
      /** Height before this tick's step: the contact test sweeps from here (a fast piece cannot skip a roof). */
      double py;
   }

   private static void store(Piece p, int t, Motion m) {
      p.ensure(t + 1);
      p.length = Math.max(p.length, t + 1);
      p.x[t] = (float)m.x;
      p.y[t] = (float)m.y;
      p.z[t] = (float)m.z;
      p.qx[t] = (float)m.qx;
      p.qy[t] = (float)m.qy;
      p.qz[t] = (float)m.qz;
      p.qw[t] = (float)m.qw;
   }

   /**
    * True (and the motion clamped) when the piece touches a real surface at its column: the highest solid
    * block under the height it had before this step (roof, floor slab, ledge, street), so a fast piece cannot
    * fall through a roof between two samples. An unknown column (unloaded chunk) lands on the section's ground.
    */
   private static boolean grounded(Piece p, Section s, Motion m) {
      int fx = (int)Math.floor(m.x), fz = (int)Math.floor(m.z);
      int surface = s.ground.surface(fx, fz, (int)Math.floor(Math.max(m.py, m.y)));
      if (surface == Ground.UNKNOWN) {
         surface = s.surfaceY;
      }
      double floor = surface + p.half * 0.55;
      if (m.y <= floor) {
         m.y = floor;
         return true;
      }
      return false;
   }

   /** Free-flight step: a piece flying sideways into a solid facade stops against it and slides down. */
   private static void step(Section s, Motion m) {
      m.py = m.y;
      double nx = m.x + m.vx, nz = m.z + m.vz;
      int by = (int)Math.floor(m.y);
      if (((int)Math.floor(nx) != (int)Math.floor(m.x) || (int)Math.floor(nz) != (int)Math.floor(m.z))
         && s.ground.solid((int)Math.floor(nx), by, (int)Math.floor(nz)) && !s.ground.solid((int)Math.floor(m.x), by, (int)Math.floor(m.z))) {
         m.vx *= -0.15;
         m.vz *= -0.15;
      } else {
         m.x = nx;
         m.z = nz;
      }
      m.y += m.vy;
   }

   /** Free fall after {@code delay} ticks at rest, with a small drift and spin. */
   private static void dropTrack(Piece p, Section s, Motion m, int delay, double vx, double vz, double sax, double say, double saz, double spin) {
      int t = 0;
      for (; t < delay; t++) {
         store(p, t, m);
      }
      m.vx = vx;
      m.vz = vz;
      m.vy = 0.0;
      int start = t;
      for (; t < MAX_FLIGHT_TICKS; t++) {
         m.vy -= CollapseRules.GRAVITY;
         step(s, m);
         m.vy *= 0.985;
         setQuat(m, sax, say, saz, spin * (t - start));
         if (grounded(p, s, m)) {
            store(p, t, m);
            land(p, t);
            return;
         }
         store(p, t, m);
      }
      land(p, p.length - 1);
   }

   /**
    * Rigid swing about a hinge: the angle grows as (t/T)^2 to {@code maxAngle} at {@code T}; once it passes
    * {@code breakAt} the piece flies free with the swing's velocity and its own spin.
    */
   private static void swingTrack(Piece p, Section s, Motion m, int delay, double hx, double hy, double hz, double ax, double ay, double az, double maxAngle,
      double T, double breakAt, double sax, double say, double saz, double spinScale) {
      int t = 0;
      for (; t < delay; t++) {
         store(p, t, m);
      }
      double rx = p.ox - hx, ry = p.oy - hy, rz = p.oz - hz;
      double prevX = m.x, prevY = m.y, prevZ = m.z;
      double bqx = 0, bqy = 0, bqz = 0, bqw = 1;
      boolean free = false;
      int freeAt = 0;
      double spin = 0.0;
      for (; t < MAX_FLIGHT_TICKS; t++) {
         m.py = m.y;
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
            // the swinging piece runs into a neighbour's facade: it breaks off there and falls along it
            boolean hit = s.ground.solid((int)Math.floor(m.x), (int)Math.floor(m.y), (int)Math.floor(m.z))
               && !s.ground.solid((int)Math.floor(prevX), (int)Math.floor(prevY), (int)Math.floor(prevZ));
            if (hit) {
               double hvx = m.x - prevX, hvy = m.y - prevY, hvz = m.z - prevZ;
               m.x = prevX;
               m.y = prevY;
               m.z = prevZ;
               free = true;
               freeAt = t;
               m.vx = -0.15 * hvx;
               m.vz = -0.15 * hvz;
               m.vy = Math.min(0.0, hvy);
               bqx = qx;
               bqy = qy;
               bqz = qz;
               bqw = qw;
               spin = spinScale * maxAngle * 0.5 * Math.min(1.0, k) / T;
            } else if (angle >= breakAt || k >= 1.0) {
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
            step(s, m);
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
      land(p, p.length - 1);
   }

   /** The track ends on the contact: {@link Piece#at} holds that resting pose from here on. */
   private static void land(Piece p, int t) {
      p.land = t;
      p.length = t + 1;
   }

   /**
    * The surface a resting piece lay on is gone (a roof or a slab broken later, another collapse): it falls on
    * from its resting pose at track tick {@code t} until the next real contact, then rests and sinks again.
    */
   public static void refall(Piece p, int t, Ground ground, int fallbackSurface) {
      int last = p.length - 1;
      Motion m = new Motion();
      m.x = p.x[last];
      m.y = p.y[last];
      m.z = p.z[last];
      m.qx = p.qx[last];
      m.qy = p.qy[last];
      m.qz = p.qz[last];
      m.qw = p.qw[last];
      for (int i = p.length; i <= t; i++) {
         store(p, i, m);
      }
      p.length = t + 1;
      p.land = -1;
      Section s = new Section(0, 0, 0, 0, 0, 0, m.x, m.z, 1.0, 0.0, 0.5, fallbackSurface, 4, 0L, ground);
      for (int i = t + 1; i < t + MAX_FLIGHT_TICKS; i++) {
         m.vy -= CollapseRules.GRAVITY;
         step(s, m);
         m.vy *= 0.985;
         if (grounded(p, s, m)) {
            store(p, i, m);
            land(p, i);
            return;
         }
         store(p, i, m);
      }
      land(p, p.length - 1);
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

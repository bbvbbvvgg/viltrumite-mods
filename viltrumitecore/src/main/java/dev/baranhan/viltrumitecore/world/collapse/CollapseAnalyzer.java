package dev.baranhan.viltrumitecore.world.collapse;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;

/**
 * Bounded structural-stability heuristic (pure, unit tested). It never simulates the whole building: it reads
 * one box around real damage (at most 25 x 25 x ~40 blocks) and decides which section, if any, comes down.
 *
 * <ol>
 *   <li><b>Is it a building?</b> Enough structural (non-terrain) blocks above the damage, multi-storey height,
 *   more structure than terrain. A cliff, a cave, a hill or a one-storey hut never collapses.</li>
 *   <li><b>Is the damage real?</b> At least {@link CollapseRules#MIN_DAMAGED} ledger positions are still air.</li>
 *   <li><b>Severed (rule A).</b> Flood fill from the box floor and the four box sides (the building continues
 *   there, so they count as supported). Solid blocks that the fill cannot reach hang in the air: they fall.</li>
 *   <li><b>Overhang (rule B).</b> A hole at least {@link CollapseRules#OVERHANG_MIN_DEPTH} storeys deep and
 *   {@link CollapseRules#OVERHANG_MIN_SPAN} wide drops the floors right above it (a limited band, jagged edge).</li>
 *   <li><b>Local (rule C, safe scenario).</b> Heavy damage where neither rule can show load paths: only loose
 *   blocks hanging over the hole crumble ({@link CollapseRules#LOCAL_MAX_BLOCKS}). The rest stays.</li>
 * </ol>
 * Unloaded columns anywhere in the box reject the analysis. Unbreakable blocks support but never fall.
 */
public final class CollapseAnalyzer {
   public static final byte AIR = 0;
   /** Built blocks: concrete, bricks, glass, planks, metal... */
   public static final byte STRUCTURE = 1;
   /** Terrain: stone, dirt, sand, ores, logs, leaves... */
   public static final byte NATURAL = 2;
   /** Unbreakable (bedrock, barriers): supports, never removed. */
   public static final byte FIXED = 3;
   /** Not loaded: the analysis is refused. */
   public static final byte UNKNOWN = 4;
   /** Water or lava: neither support nor removable. */
   public static final byte FLUID = 5;

   /** Read-only block classification. */
   @FunctionalInterface
   public interface Volume {
      byte at(int x, int y, int z);
   }

   public enum Rule {
      SEVERED,
      OVERHANG,
      LOCAL
   }

   /** Analysis input: damage centre, damaged positions (packed, may contain blocks that were rebuilt). */
   public record Request(int x, int y, int z, long[] damaged, float score, CollapseCause cause, long seed, int maxBlocks, int minY, int maxY) {
   }

   /** Result. {@code blocks} is null when rejected; then {@code reason} says why. */
   public record Plan(String reason, long[] blocks, Rule rule, CollapseVariant variant, CollapseSeverity severity, float fallX, float fallZ,
      int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int groundY, int damaged, boolean truncated, int cells) {
      public boolean accepted() {
         return this.blocks != null;
      }

      static Plan reject(String reason, int damaged, int cells) {
         return new Plan(reason, null, null, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, damaged, false, cells);
      }

      /** Horizontal radius of the section footprint around its centre. */
      public float radius() {
         return (float)Math.max(1.0, 0.5 * Math.hypot(this.maxX - this.minX + 1, this.maxZ - this.minZ + 1));
      }
   }

   private CollapseAnalyzer() {
   }

   public static Plan analyze(Volume volume, Request r) {
      long[] dmg = r.damaged();
      if (dmg == null || dmg.length < CollapseRules.MIN_DAMAGED) {
         return Plan.reject("too little damage", dmg == null ? 0 : dmg.length, 0);
      }
      // damage bounds (only positions near the centre)
      int dMinX = Integer.MAX_VALUE, dMinY = Integer.MAX_VALUE, dMinZ = Integer.MAX_VALUE;
      int dMaxX = Integer.MIN_VALUE, dMaxY = Integer.MIN_VALUE, dMaxZ = Integer.MIN_VALUE;
      for (long k : dmg) {
         int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
         if (Math.abs(x - r.x()) > CollapseRules.MAX_HALF || Math.abs(z - r.z()) > CollapseRules.MAX_HALF) {
            continue;
         }
         dMinX = Math.min(dMinX, x);
         dMinY = Math.min(dMinY, y);
         dMinZ = Math.min(dMinZ, z);
         dMaxX = Math.max(dMaxX, x);
         dMaxY = Math.max(dMaxY, y);
         dMaxZ = Math.max(dMaxZ, z);
      }
      if (dMinX == Integer.MAX_VALUE) {
         return Plan.reject("damage out of range", 0, 0);
      }
      int x0 = Math.max(dMinX - 4, r.x() - CollapseRules.MAX_HALF), x1 = Math.min(dMaxX + 4, r.x() + CollapseRules.MAX_HALF);
      int z0 = Math.max(dMinZ - 4, r.z() - CollapseRules.MAX_HALF), z1 = Math.min(dMaxZ + 4, r.z() + CollapseRules.MAX_HALF);
      int y0 = Math.max(r.minY(), dMinY - CollapseRules.MAX_DOWN), y1 = Math.min(r.maxY(), dMaxY + CollapseRules.MAX_UP);
      if (y1 - y0 < 3) {
         return Plan.reject("no room", 0, 0);
      }
      Grid g = new Grid(x0, y0, z0, x1, y1, z1);
      for (int y = y0; y <= y1; y++) {
         for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
               byte m = volume.at(x, y, z);
               if (m == UNKNOWN) {
                  return Plan.reject("unloaded chunk in the box", 0, g.cells);
               }
               g.m[g.i(x, y, z)] = m;
            }
         }
      }
      // real damage: ledger positions inside the box that are still air
      boolean[] hole = new boolean[g.cells];
      int nd = 0;
      double hx = 0, hy = 0, hz = 0;
      for (long k : dmg) {
         int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
         if (!g.inside(x, y, z)) {
            continue;
         }
         int i = g.i(x, y, z);
         if (g.m[i] == AIR && !hole[i]) {
            hole[i] = true;
            nd++;
            hx += x + 0.5;
            hy += y + 0.5;
            hz += z + 0.5;
         }
      }
      if (nd < CollapseRules.MIN_DAMAGED) {
         return Plan.reject("damage was rebuilt or is too small", nd, g.cells);
      }
      hx /= nd;
      hy /= nd;
      hz /= nd;
      // recompute the real hole bounds
      int hMinX = Integer.MAX_VALUE, hMinY = Integer.MAX_VALUE, hMinZ = Integer.MAX_VALUE, hMaxX = Integer.MIN_VALUE, hMaxY = Integer.MIN_VALUE,
         hMaxZ = Integer.MIN_VALUE;
      for (int i = 0; i < g.cells; i++) {
         if (hole[i]) {
            int x = g.x(i), y = g.y(i), z = g.z(i);
            hMinX = Math.min(hMinX, x);
            hMaxX = Math.max(hMaxX, x);
            hMinY = Math.min(hMinY, y);
            hMaxY = Math.max(hMaxY, y);
            hMinZ = Math.min(hMinZ, z);
            hMaxZ = Math.max(hMaxZ, z);
         }
      }
      // 1. is it a building?
      int structure = 0, natural = 0, top = Integer.MIN_VALUE;
      double bx = 0, bz = 0;
      for (int i = 0; i < g.cells; i++) {
         if (g.y(i) < hMinY) {
            continue;
         }
         if (g.m[i] == STRUCTURE) {
            structure++;
            top = Math.max(top, g.y(i));
            bx += g.x(i) + 0.5;
            bz += g.z(i) + 0.5;
         } else if (g.m[i] == NATURAL) {
            natural++;
         }
      }
      if (structure < CollapseRules.MIN_STRUCTURE) {
         return Plan.reject("no building above the damage", nd, g.cells);
      }
      if (natural > structure) {
         return Plan.reject("terrain, not a building", nd, g.cells);
      }
      if (top - hMinY < CollapseRules.MIN_TOWER_HEIGHT) {
         return Plan.reject("structure too low", nd, g.cells);
      }
      bx /= structure;
      bz /= structure;

      // 2. severed blocks (rule A)
      IntArrayList section = severed(g);
      Rule rule;
      if (section.size() >= CollapseRules.MIN_SEVERED) {
         rule = Rule.SEVERED;
      } else {
         IntArrayList crumbs = section;
         section = overhang(g, hole, hMinX, hMinY, hMinZ, hMaxX, hMaxY, hMaxZ, nd, r);
         if (section != null && section.size() >= CollapseRules.MIN_SECTION) {
            rule = Rule.OVERHANG;
            addAll(section, crumbs);
         } else if (nd >= CollapseRules.LOCAL_MIN_DAMAGED) {
            section = local(g, hole);
            addAll(section, crumbs);
            if (section.size() < CollapseRules.MIN_SEVERED) {
               return Plan.reject("stable: no hanging blocks", nd, g.cells);
            }
            rule = Rule.LOCAL;
         } else {
            return Plan.reject("stable: hole too small to fail", nd, g.cells);
         }
      }
      int cap = Math.min(r.maxBlocks(), rule == Rule.LOCAL ? CollapseRules.LOCAL_MAX_BLOCKS : Integer.MAX_VALUE);
      boolean truncated = false;
      if (section.size() > cap) {
         // safe truncation: keep the blocks nearest the damage, the rest of the structure stays
         final double fx = hx, fy = hy, fz = hz;
         int[] idx = section.toIntArray();
         double[] d = new double[idx.length];
         Integer[] order = new Integer[idx.length];
         for (int j = 0; j < idx.length; j++) {
            double ex = g.x(idx[j]) + 0.5 - fx, ey = g.y(idx[j]) + 0.5 - fy, ez = g.z(idx[j]) + 0.5 - fz;
            d[j] = ex * ex + ey * ey * 0.6 + ez * ez;
            order[j] = j;
         }
         Arrays.sort(order, (a, b) -> Double.compare(d[a], d[b]));
         IntArrayList kept = new IntArrayList(cap);
         for (int j = 0; j < cap; j++) {
            kept.add(idx[order[j]]);
         }
         section = kept;
         truncated = true;
      }
      // output, bounds, fall direction, ground
      long[] blocks = new long[section.size()];
      int sMinX = Integer.MAX_VALUE, sMinY = Integer.MAX_VALUE, sMinZ = Integer.MAX_VALUE, sMaxX = Integer.MIN_VALUE, sMaxY = Integer.MIN_VALUE,
         sMaxZ = Integer.MIN_VALUE;
      boolean[] inSection = new boolean[g.cells];
      for (int j = 0; j < blocks.length; j++) {
         int i = section.getInt(j);
         inSection[i] = true;
         int x = g.x(i), y = g.y(i), z = g.z(i);
         blocks[j] = BlockKey.of(x, y, z);
         sMinX = Math.min(sMinX, x);
         sMaxX = Math.max(sMaxX, x);
         sMinY = Math.min(sMinY, y);
         sMaxY = Math.max(sMaxY, y);
         sMinZ = Math.min(sMinZ, z);
         sMaxZ = Math.max(sMaxZ, z);
      }
      // deterministic order (packed key): server removal, packet bitset and client snapshot agree
      Arrays.sort(blocks);
      float fdx = (float)(hx - bx), fdz = (float)(hz - bz);
      double len = Math.sqrt(fdx * fdx + fdz * fdz);
      if (len < 0.75) {
         double a = (r.seed() >>> 11) * 0x1.0p-53 * Math.PI * 2.0;
         fdx = (float)Math.cos(a);
         fdz = (float)Math.sin(a);
      } else {
         fdx /= (float)len;
         fdz /= (float)len;
      }
      int ground = ground(g, inSection, sMinX, sMinZ, sMaxX, sMaxZ, sMinY);
      CollapseSeverity severity = CollapseSeverity.of(blocks.length, r.cause());
      CollapseVariant variant = variant(g, rule, inSection, sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ, hx, hz, fdx, fdz);
      return new Plan("ok", blocks, rule, variant, severity, fdx, fdz, sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ, ground, nd, truncated, g.cells);
   }

   private static void addAll(IntArrayList into, IntArrayList from) {
      if (from == null || from.isEmpty()) {
         return;
      }
      java.util.BitSet seen = new java.util.BitSet();
      for (int i = 0; i < into.size(); i++) {
         seen.set(into.getInt(i));
      }
      for (int i = 0; i < from.size(); i++) {
         if (!seen.get(from.getInt(i))) {
            into.add(from.getInt(i));
         }
      }
   }

   private static boolean solid(byte m) {
      return m == STRUCTURE || m == NATURAL || m == FIXED;
   }

   private static boolean removable(byte m) {
      return m == STRUCTURE || m == NATURAL;
   }

   /** Rule A: solid cells not connected to the box floor or sides. */
   static IntArrayList severed(Grid g) {
      boolean[] reached = new boolean[g.cells];
      int[] queue = new int[g.cells];
      int head = 0, tail = 0;
      for (int i = 0; i < g.cells; i++) {
         int x = g.x(i), y = g.y(i), z = g.z(i);
         boolean face = y == g.y0 || x == g.x0 || x == g.x1 || z == g.z0 || z == g.z1;
         if (face && solid(g.m[i])) {
            reached[i] = true;
            queue[tail++] = i;
         }
      }
      while (head < tail) {
         int i = queue[head++];
         int x = g.x(i), y = g.y(i), z = g.z(i);
         for (int n = 0; n < 6; n++) {
            int nx = x + DX[n], ny = y + DY[n], nz = z + DZ[n];
            if (!g.inside(nx, ny, nz)) {
               continue;
            }
            int j = g.i(nx, ny, nz);
            if (!reached[j] && solid(g.m[j])) {
               reached[j] = true;
               queue[tail++] = j;
            }
         }
      }
      // an unreached part that touches the box top continues above what we can see: never drop it whole
      head = 0;
      tail = 0;
      for (int i = 0; i < g.cells; i++) {
         if (!reached[i] && g.y(i) == g.y1 && solid(g.m[i])) {
            reached[i] = true;
            queue[tail++] = i;
         }
      }
      while (head < tail) {
         int i = queue[head++];
         int x = g.x(i), y = g.y(i), z = g.z(i);
         for (int n = 0; n < 6; n++) {
            int nx = x + DX[n], ny = y + DY[n], nz = z + DZ[n];
            if (!g.inside(nx, ny, nz)) {
               continue;
            }
            int j = g.i(nx, ny, nz);
            if (!reached[j] && solid(g.m[j])) {
               reached[j] = true;
               queue[tail++] = j;
            }
         }
      }
      IntArrayList out = new IntArrayList();
      for (int i = 0; i < g.cells; i++) {
         if (!reached[i] && removable(g.m[i])) {
            out.add(i);
         }
      }
      return out;
   }

   /** Rule B: the floors right above a deep, wide hole come down (limited band, jagged seeded edge). */
   static IntArrayList overhang(Grid g, boolean[] hole, int hMinX, int hMinY, int hMinZ, int hMaxX, int hMaxY, int hMaxZ, int nd, Request r) {
      int depth = hMaxY - hMinY + 1;
      int span = Math.max(hMaxX - hMinX + 1, hMaxZ - hMinZ + 1);
      if (depth < CollapseRules.OVERHANG_MIN_DEPTH || span < CollapseRules.OVERHANG_MIN_SPAN || nd < CollapseRules.OVERHANG_MIN_DAMAGED) {
         return null;
      }
      int band = Math.max(4, Math.min(14, depth * 2 + (int)(r.score() / 40.0F)));
      int sx = g.x1 - g.x0 + 1, sz = g.z1 - g.z0 + 1;
      int[] topHole = new int[sx * sz];
      Arrays.fill(topHole, Integer.MIN_VALUE);
      for (int i = 0; i < g.cells; i++) {
         if (hole[i]) {
            int c = (g.z(i) - g.z0) * sx + (g.x(i) - g.x0);
            topHole[c] = Math.max(topHole[c], g.y(i));
         }
      }
      IntArrayList out = new IntArrayList();
      for (int cz = 0; cz < sz; cz++) {
         for (int cx = 0; cx < sx; cx++) {
            int c = cz * sx + cx;
            int from = topHole[c];
            boolean ring = false;
            if (from == Integer.MIN_VALUE) {
               // jagged edge: a column next to the hole breaks with it about half the time
               int best = Integer.MIN_VALUE;
               for (int dz = -1; dz <= 1; dz++) {
                  for (int dx = -1; dx <= 1; dx++) {
                     int ax = cx + dx, az = cz + dz;
                     if (ax >= 0 && az >= 0 && ax < sx && az < sz) {
                        best = Math.max(best, topHole[az * sx + ax]);
                     }
                  }
               }
               if (best == Integer.MIN_VALUE || hash(r.seed(), cx + g.x0, 0, cz + g.z0) % 100 >= 50) {
                  continue;
               }
               from = best;
               ring = true;
            }
            int x = g.x0 + cx, z = g.z0 + cz;
            int limit = from + (ring ? Math.max(2, band - 1 - (int)(hash(r.seed(), x, 1, z) % 4)) : band);
            int gap = 0;
            for (int y = from + 1; y <= Math.min(limit, g.y1); y++) {
               int i = g.i(x, y, z);
               byte m = g.m[i];
               if (removable(m)) {
                  out.add(i);
                  gap = 0;
               } else if (m == FIXED) {
                  break;
               } else if (++gap >= 4) {
                  break; // open sky above a lower roof
               }
            }
         }
      }
      return out;
   }

   /** Rule C: loose blocks hanging over the hole (few solid neighbours). */
   static IntArrayList local(Grid g, boolean[] hole) {
      IntArrayList out = new IntArrayList();
      boolean[] taken = new boolean[g.cells];
      for (int i = 0; i < g.cells; i++) {
         if (!hole[i]) {
            continue;
         }
         int x = g.x(i), y = g.y(i), z = g.z(i);
         for (int up = 1; up <= 3; up++) {
            if (!g.inside(x, y + up, z)) {
               break;
            }
            int j = g.i(x, y + up, z);
            if (taken[j] || !removable(g.m[j])) {
               continue;
            }
            int solidNeighbours = 0;
            for (int n = 0; n < 6; n++) {
               int nx = x + DX[n], ny = y + up + DY[n], nz = z + DZ[n];
               if (g.inside(nx, ny, nz) && solid(g.m[g.i(nx, ny, nz)])) {
                  solidNeighbours++;
               }
            }
            if (solidNeighbours <= 3) {
               taken[j] = true;
               out.add(j);
            }
         }
      }
      return out;
   }

   /** Highest solid non-section block under the section footprint (sampled), or the box floor. */
   static int ground(Grid g, boolean[] inSection, int minX, int minZ, int maxX, int maxZ, int fromY) {
      int best = g.y0;
      int stepX = Math.max(1, (maxX - minX) / 4), stepZ = Math.max(1, (maxZ - minZ) / 4);
      int samples = 0;
      long sum = 0;
      for (int x = minX; x <= maxX; x += stepX) {
         for (int z = minZ; z <= maxZ; z += stepZ) {
            int found = g.y0 - 1;
            for (int y = fromY - 1; y >= g.y0; y--) {
               int i = g.i(x, y, z);
               if (!inSection[i] && solid(g.m[i])) {
                  found = y;
                  break;
               }
            }
            sum += found;
            samples++;
            best = Math.max(best, found);
         }
      }
      // land on the mean surface (half-way between the highest and mean samples)
      int mean = samples == 0 ? g.y0 : (int)Math.floor((double)sum / samples);
      return (best + mean) / 2;
   }

   static CollapseVariant variant(Grid g, Rule rule, boolean[] inSection, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double hx, double hz,
      float fdx, float fdz) {
      if (rule == Rule.LOCAL) {
         return CollapseVariant.CRUMBLE;
      }
      int width = Math.max(maxX - minX + 1, maxZ - minZ + 1);
      int height = maxY - minY + 1;
      if (rule == Rule.SEVERED && height >= width * 1.4) {
         return CollapseVariant.TOPPLE;
      }
      // exterior: open air a few blocks from the damage toward the fall direction, at mid section height
      int midY = (minY + maxY) / 2;
      for (int s = 2; s <= 8; s++) {
         int x = (int)Math.floor(hx + fdx * s), z = (int)Math.floor(hz + fdz * s);
         if (!g.inside(x, midY, z)) {
            break;
         }
         int i = g.i(x, midY, z);
         if (!inSection[i] && g.m[i] == AIR && (!g.inside(x, midY + 1, z) || g.m[g.i(x, midY + 1, z)] == AIR)) {
            boolean open = true;
            for (int k = 1; k <= 3 && open; k++) {
               int ox = (int)Math.floor(hx + fdx * (s + k)), oz = (int)Math.floor(hz + fdz * (s + k));
               open = !g.inside(ox, midY, oz) || g.m[g.i(ox, midY, oz)] == AIR;
            }
            if (open) {
               return CollapseVariant.FACADE_SHEAR;
            }
         }
      }
      return rule == Rule.SEVERED ? CollapseVariant.FACADE_SHEAR : CollapseVariant.PANCAKE;
   }

   /** Deterministic non-negative hash of a seed and a position. */
   public static long hash(long seed, int x, int y, int z) {
      long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
      h ^= h >>> 33;
      h *= 0xFF51AFD7ED558CCDL;
      h ^= h >>> 33;
      return h & Long.MAX_VALUE;
   }

   private static final int[] DX = {1, -1, 0, 0, 0, 0};
   private static final int[] DY = {0, 0, 1, -1, 0, 0};
   private static final int[] DZ = {0, 0, 0, 0, 1, -1};

   /** The box as a flat array, x fastest. */
   static final class Grid {
      final int x0, y0, z0, x1, y1, z1, sx, sy, sz, cells;
      final byte[] m;

      Grid(int x0, int y0, int z0, int x1, int y1, int z1) {
         this.x0 = x0;
         this.y0 = y0;
         this.z0 = z0;
         this.x1 = x1;
         this.y1 = y1;
         this.z1 = z1;
         this.sx = x1 - x0 + 1;
         this.sy = y1 - y0 + 1;
         this.sz = z1 - z0 + 1;
         this.cells = this.sx * this.sy * this.sz;
         this.m = new byte[this.cells];
      }

      boolean inside(int x, int y, int z) {
         return x >= this.x0 && x <= this.x1 && y >= this.y0 && y <= this.y1 && z >= this.z0 && z <= this.z1;
      }

      int i(int x, int y, int z) {
         return ((y - this.y0) * this.sz + (z - this.z0)) * this.sx + (x - this.x0);
      }

      int x(int i) {
         return this.x0 + i % this.sx;
      }

      int z(int i) {
         return this.z0 + (i / this.sx) % this.sz;
      }

      int y(int i) {
         return this.y0 + i / (this.sx * this.sz);
      }
   }

   /** Helper for tests and commands: packs a list of positions. */
   public static long[] keys(LongArrayList list) {
      return list.toLongArray();
   }
}

package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Pure braided maze of a MAZE room (design §4.2): a grid of 4-block units, two independent variants. Each variant is
 * a random spanning tree (every unit reachable) with most dead ends opened and a few extra loops, so there are always
 * several routes. Passages next to the room's doorways are open in both variants. The server swaps the variant while
 * nobody is inside: the walls move, the room stays connected.
 */
public final class PocketMaze {
   public static final int UNIT = 4;
   public static final int VARIANTS = 2;
   public final int nx;
   public final int nz;
   /** [variant][unit]: bit 0 = passage to the east neighbour, bit 1 = passage to the south neighbour. */
   private final byte[][] open;

   private PocketMaze(int nx, int nz) {
      this.nx = nx;
      this.nz = nz;
      this.open = new byte[VARIANTS][nx * nz];
   }

   /**
    * @param forced pairs of unit indices that must be joined in every variant (doorway passages), may be empty
    */
   public static PocketMaze generate(long seed, int nx, int nz, int[][] forced) {
      PocketMaze m = new PocketMaze(nx, nz);
      for (int v = 0; v < VARIANTS; v++) {
         m.carve(v, new SplittableRandom(PocketLayout.mix(seed + 0x51ED27L * (v + 1))), forced);
      }
      return m;
   }

   private void carve(int v, SplittableRandom r, int[][] forced) {
      int n = this.nx * this.nz;
      List<int[]> walls = new ArrayList<>();
      for (int z = 0; z < this.nz; z++) {
         for (int x = 0; x < this.nx; x++) {
            if (x + 1 < this.nx) {
               walls.add(new int[]{z * this.nx + x, 0});
            }
            if (z + 1 < this.nz) {
               walls.add(new int[]{z * this.nx + x, 1});
            }
         }
      }
      for (int i = walls.size() - 1; i > 0; i--) {
         int j = r.nextInt(i + 1);
         int[] t = walls.get(i);
         walls.set(i, walls.get(j));
         walls.set(j, t);
      }
      int[] parent = new int[n];
      for (int i = 0; i < n; i++) {
         parent[i] = i;
      }
      for (int[] f : forced) {
         this.join(v, f[0], f[1]);
         union(parent, f[0], f[1]);
      }
      List<int[]> closed = new ArrayList<>();
      for (int[] w : walls) {
         int a = w[0];
         int b = w[1] == 0 ? a + 1 : a + this.nx;
         if (union(parent, a, b)) {
            this.open[v][a] |= (byte)(1 << w[1]);
         } else {
            closed.add(w);
         }
      }
      // braid: open most dead ends into a neighbour, then a few random loops
      for (int u = 0; u < n; u++) {
         if (this.degree(v, u) == 1 && r.nextInt(10) < 6) {
            int[] sides = {0, 1, 2, 3};
            for (int k = 3; k > 0; k--) {
               int j = r.nextInt(k + 1);
               int t = sides[k];
               sides[k] = sides[j];
               sides[j] = t;
            }
            for (int side : sides) {
               int o = this.neighbour(u, side);
               if (o >= 0 && !this.isOpen(v, u, side)) {
                  this.join(v, u, o);
                  break;
               }
            }
         }
      }
      for (int[] w : closed) {
         if (r.nextInt(100) < 7) {
            this.open[v][w[0]] |= (byte)(1 << w[1]);
         }
      }
   }

   private static int find(int[] p, int i) {
      while (p[i] != i) {
         p[i] = p[p[i]];
         i = p[i];
      }
      return i;
   }

   private static boolean union(int[] p, int a, int b) {
      int ra = find(p, a);
      int rb = find(p, b);
      if (ra == rb) {
         return false;
      }
      p[ra] = rb;
      return true;
   }

   /** Neighbour unit on side N/E/S/W, or -1 outside. */
   int neighbour(int u, int side) {
      int x = u % this.nx + PocketLayout.DX[side];
      int z = u / this.nx + PocketLayout.DZ[side];
      return x < 0 || z < 0 || x >= this.nx || z >= this.nz ? -1 : z * this.nx + x;
   }

   private void join(int v, int a, int b) {
      int lo = Math.min(a, b);
      int hi = Math.max(a, b);
      if (hi == lo + 1 && lo / this.nx == hi / this.nx) {
         this.open[v][lo] |= 1;
      } else if (hi == lo + this.nx) {
         this.open[v][lo] |= 2;
      }
   }

   public boolean isOpen(int v, int u, int side) {
      int o = this.neighbour(u, side);
      if (o < 0) {
         return false;
      }
      return switch (side) {
         case PocketLayout.E -> (this.open[v][u] & 1) != 0;
         case PocketLayout.S -> (this.open[v][u] & 2) != 0;
         case PocketLayout.W -> (this.open[v][o] & 1) != 0;
         default -> (this.open[v][o] & 2) != 0;
      };
   }

   /** Open passage between unit (ux, uz) and its neighbour on side, in variant v. */
   public boolean isOpen(int v, int ux, int uz, int side) {
      if (ux < 0 || uz < 0 || ux >= this.nx || uz >= this.nz) {
         return false;
      }
      return this.isOpen(v, uz * this.nx + ux, side);
   }

   public int degree(int v, int u) {
      int d = 0;
      for (int side = 0; side < 4; side++) {
         if (this.isOpen(v, u, side)) {
            d++;
         }
      }
      return d;
   }

   /** True when every unit is reachable from unit 0 in variant v. */
   public boolean connected(int v) {
      int n = this.nx * this.nz;
      boolean[] seen = new boolean[n];
      int[] q = new int[n];
      int qn = 0;
      seen[0] = true;
      q[qn++] = 0;
      for (int h = 0; h < qn; h++) {
         int u = q[h];
         for (int side = 0; side < 4; side++) {
            int o = this.neighbour(u, side);
            if (o >= 0 && !seen[o] && this.isOpen(v, u, side)) {
               seen[o] = true;
               q[qn++] = o;
            }
         }
      }
      return qn == n;
   }

   /** Walls that differ between the variants (how much the architecture moves). */
   public int differences() {
      int d = 0;
      for (int u = 0; u < this.nx * this.nz; u++) {
         d += Integer.bitCount((this.open[0][u] ^ this.open[1][u]) & 3);
      }
      return d;
   }
}

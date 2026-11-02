package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import static dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketPalette.*;

import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Portal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Voxel route check of one painted instance: a body walks (step up 1, falls, 1-2 block gap jumps), swims, climbs
 * ladders, opens doors, walks through thin walls and is moved by the instance's portals and the abyss drop. Used by
 * the tests to prove that every room and every escape is reachable from the arrival and that no reachable spot is a
 * trap (from everywhere the way out is still reachable).
 */
final class PocketWalk {
   static final int S = PocketLayout.SIZE;
   static final int H = PocketPainter.HEIGHT;
   final PocketLayout layout;
   final byte[] vox = new byte[S * S * H];
   private final List<Portal> portals;
   private final List<int[]> landings;
   /** Per column, the portals whose box covers it (null for none). */
   private final Portal[][] portalAt = new Portal[S * S][];
   final BitSet reach = new BitSet(S * S * H);
   int[] nodes = new int[1 << 16];
   int nodeCount;
   private int[] edgeFrom = new int[1 << 18];
   private int[] edgeTo = new int[1 << 18];
   private int edgeCount;

   PocketWalk(PocketLayout layout) {
      this(layout, null);
   }

   /** @param variants per-room variants (maze walls, decor), null for the generated state */
   PocketWalk(PocketLayout layout, int[] variants) {
      this.layout = layout;
      PocketPainter painter = new PocketPainter(layout, variants);
      byte[] col = new byte[H];
      for (int z = 0; z < S; z++) {
         for (int x = 0; x < S; x++) {
            painter.column(x, z, col);
            for (int y = 0; y < H; y++) {
               this.vox[idx(x, y, z)] = col[y];
            }
         }
      }
      this.portals = layout.portals();
      this.landings = layout.abyssLandings();
      for (Portal p : this.portals) {
         for (int z = Math.max(0, p.z0()); z < Math.min(S, p.z1()); z++) {
            for (int x = Math.max(0, p.x0()); x < Math.min(S, p.x1()); x++) {
               Portal[] old = this.portalAt[z * S + x];
               Portal[] add = old == null ? new Portal[1] : Arrays.copyOf(old, old.length + 1);
               add[add.length - 1] = p;
               this.portalAt[z * S + x] = add;
            }
         }
      }
   }

   static int idx(int x, int y, int z) {
      return (y * S + z) * S + x;
   }

   static int ix(int i) {
      return i % S;
   }

   static int iz(int i) {
      return i / S % S;
   }

   static int iy(int i) {
      return i / (S * S);
   }

   byte at(int x, int y, int z) {
      if (x < 0 || z < 0 || x >= S || z >= S) {
         return MASS;
      }
      if (y < 0) {
         return MASS;
      }
      if (y >= H) {
         return AIR;
      }
      return this.vox[idx(x, y, z)];
   }

   boolean free(int x, int y, int z) {
      return !solid(this.at(x, y, z));
   }

   static boolean climb(byte b) {
      return b >= LADDER && b < LADDER + 4 || b == WATER;
   }

   boolean standable(int x, int y, int z) {
      if (y < 1 || !this.free(x, y, z) || !this.free(x, y + 1, z)) {
         return false;
      }
      byte feet = this.at(x, y, z);
      return climb(feet) || support(this.at(x, y - 1, z));
   }

   /** Moves from (x, y, z) without portals; calls back with the target node index. */
   private void moves(int x, int y, int z, IntSink sink) {
      byte feet = this.at(x, y, z);
      boolean headUp = this.free(x, y + 2, z);
      if (climb(feet)) {
         if (this.standable(x, y + 1, z)) {
            sink.accept(idx(x, y + 1, z));
         }
         if (this.standable(x, y - 1, z)) {
            sink.accept(idx(x, y - 1, z));
         }
      }
      for (int dir = 0; dir < 4; dir++) {
         int dx = PocketLayout.DX[dir];
         int dz = PocketLayout.DZ[dir];
         int nx = x + dx;
         int nz = z + dz;
         if (this.standable(nx, y, nz)) {
            sink.accept(idx(nx, y, nz));
         } else if (headUp && this.standable(nx, y + 1, nz) && this.free(nx, y + 2, nz)) {
            sink.accept(idx(nx, y + 1, nz));
         } else if (this.free(nx, y, nz) && this.free(nx, y + 1, nz)) {
            // nothing to stand on: fall straight down
            int fy = y - 1;
            while (fy >= 1 && !this.standable(nx, fy, nz)) {
               if (!this.free(nx, fy, nz)) {
                  fy = -1;
                  break;
               }
               fy--;
            }
            if (fy >= 1) {
               sink.accept(idx(nx, fy, nz));
            }
            // or jump the gap (1 or 2 missing blocks) at the same height
            if (headUp && this.free(nx, y + 2, nz)) {
               for (int gap = 2; gap <= 3; gap++) {
                  int tx = x + dx * gap;
                  int tz = z + dz * gap;
                  int mx = x + dx * (gap - 1);
                  int mz = z + dz * (gap - 1);
                  if (!this.free(mx, y, mz) || !this.free(mx, y + 1, mz) || !this.free(mx, y + 2, mz)) {
                     break;
                  }
                  if (this.standable(tx, y, tz) && this.free(tx, y + 2, tz)) {
                     sink.accept(idx(tx, y, tz));
                     break;
                  }
               }
            }
         }
      }
   }

   interface IntSink {
      void accept(int target);
   }

   /** Outgoing edges of a node including portals and the abyss drop. */
   void edges(int node, IntSink sink) {
      int x = ix(node);
      int y = iy(node);
      int z = iz(node);
      if (y < PocketPainter.ABYSS_TRIGGER) {
         for (int[] l : this.landings) {
            sink.accept(idx(l[0], l[1], l[2]));
         }
         return;
      }
      Portal[] here = this.portalAt[z * S + x];
      boolean forced = false;
      if (here != null) {
         for (Portal p : here) {
            if (p.contains(x + 0.5, y + 0.1, z + 0.5)) {
               int[] t = p.target(x, y, z);
               int tx = t[0];
               int ty = t[1];
               int tz = t[2];
               if (this.standable(tx, ty, tz)) {
                  sink.accept(idx(tx, ty, tz));
               } else {
                  // portal targets are feet positions; settle onto the ground below like the game does
                  int fy = ty;
                  while (fy > 1 && !this.standable(tx, fy, tz) && this.free(tx, fy, tz)) {
                     fy--;
                  }
                  if (this.standable(tx, fy, tz)) {
                     sink.accept(idx(tx, fy, tz));
                  }
               }
               forced |= p.forced();
            }
         }
      }
      if (!forced) {
         this.moves(x, y, z, sink);
      }
   }

   private void addNode(int n) {
      if (this.nodeCount == this.nodes.length) {
         this.nodes = Arrays.copyOf(this.nodes, this.nodes.length * 2);
      }
      this.nodes[this.nodeCount++] = n;
   }

   private void addEdge(int a, int b) {
      if (this.edgeCount == this.edgeFrom.length) {
         this.edgeFrom = Arrays.copyOf(this.edgeFrom, this.edgeFrom.length * 2);
         this.edgeTo = Arrays.copyOf(this.edgeTo, this.edgeTo.length * 2);
      }
      this.edgeFrom[this.edgeCount] = a;
      this.edgeTo[this.edgeCount] = b;
      this.edgeCount++;
   }

   /** Forward flood from a feet position; returns the number of reached nodes. */
   int flood(int x, int y, int z) {
      int start = idx(x, y, z);
      if (!this.standable(x, y, z)) {
         throw new IllegalStateException("start " + x + "," + y + "," + z + " is not standable: " + this.at(x, y - 1, z) + "/" + this.at(x, y, z));
      }
      this.reach.set(start);
      this.addNode(start);
      for (int head = 0; head < this.nodeCount; head++) {
         int n = this.nodes[head];
         this.edges(n, t -> {
            this.addEdge(n, t);
            if (!this.reach.get(t)) {
               this.reach.set(t);
               this.addNode(t);
            }
         });
      }
      return this.nodeCount;
   }

   /** Nodes of the forward flood from which none of the goals can be reached (traps). Call after flood. */
   List<Integer> traps(int[] goals) {
      int[] sorted = Arrays.copyOf(this.nodes, this.nodeCount);
      Arrays.sort(sorted);
      int n = sorted.length;
      int[] deg = new int[n + 1];
      int[] to = new int[this.edgeCount];
      int[] from = new int[this.edgeCount];
      for (int e = 0; e < this.edgeCount; e++) {
         to[e] = Arrays.binarySearch(sorted, this.edgeTo[e]);
         from[e] = Arrays.binarySearch(sorted, this.edgeFrom[e]);
         deg[to[e] + 1]++;
      }
      for (int i = 0; i < n; i++) {
         deg[i + 1] += deg[i];
      }
      int[] fill = Arrays.copyOf(deg, n);
      int[] rev = new int[this.edgeCount];
      for (int e = 0; e < this.edgeCount; e++) {
         rev[fill[to[e]]++] = from[e];
      }
      boolean[] safe = new boolean[n];
      int[] queue = new int[n];
      int qn = 0;
      for (int g : goals) {
         int k = Arrays.binarySearch(sorted, g);
         if (k >= 0 && !safe[k]) {
            safe[k] = true;
            queue[qn++] = k;
         }
      }
      for (int head = 0; head < qn; head++) {
         int k = queue[head];
         for (int e = deg[k]; e < deg[k + 1]; e++) {
            int p = rev[e];
            if (!safe[p]) {
               safe[p] = true;
               queue[qn++] = p;
            }
         }
      }
      List<Integer> out = new ArrayList<>();
      for (int i = 0; i < n; i++) {
         if (!safe[i]) {
            out.add(sorted[i]);
         }
      }
      return out;
   }

   /** True when some reached node has its feet inside the slot-local box [x0, x1) x [y0, y1) x [z0, z1). */
   boolean reachedBox(int x0, int y0, int z0, int x1, int y1, int z1) {
      for (int y = Math.max(0, y0); y < Math.min(H, y1); y++) {
         for (int z = Math.max(0, z0); z < Math.min(S, z1); z++) {
            for (int x = Math.max(0, x0); x < Math.min(S, x1); x++) {
               if (this.reach.get(idx(x, y, z))) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   /** Reached nodes inside a box (for goal sets). */
   int[] nodesIn(int x0, int y0, int z0, int x1, int y1, int z1) {
      List<Integer> list = new ArrayList<>();
      for (int y = Math.max(0, y0); y < Math.min(H, y1); y++) {
         for (int z = Math.max(0, z0); z < Math.min(S, z1); z++) {
            for (int x = Math.max(0, x0); x < Math.min(S, x1); x++) {
               if (this.reach.get(idx(x, y, z))) {
                  list.add(idx(x, y, z));
               }
            }
         }
      }
      return list.stream().mapToInt(Integer::intValue).toArray();
   }
}

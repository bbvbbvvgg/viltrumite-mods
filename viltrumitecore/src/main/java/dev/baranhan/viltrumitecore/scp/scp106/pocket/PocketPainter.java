package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import static dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketPalette.*;

import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Door;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Kind;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Purpose;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Room;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Zone;
import java.util.Arrays;

/**
 * Pure architecture of the pocket dimension: fills one block column (y 0..255) of an instance from its
 * {@link PocketLayout}. Every column is computed on its own from the room it belongs to, so chunks generate in any
 * order on any thread, and the reachability check and the runtime repaint of mutating corridors reuse exactly the
 * same code. Geometry never depends on the decor variant; only stains, lights, streaks and pipes do.
 */
public final class PocketPainter {
   public static final int F = PocketLayout.FLOOR;
   public static final int HEIGHT = 256;
   public static final int MASS_BOTTOM = F - 10;
   public static final int MASS_TOP = F + 12;
   public static final int VOID_CEIL = F + 90;
   public static final int THRONE_CEIL = F + 40;
   public static final int SHAFT_TOP = F + 30;
   public static final int SECRET_TOP = F + 30;
   /** Falling below this in a void or a shaft is the "drop" anomaly, never a death in the abyss. */
   public static final int ABYSS_TRIGGER = F - 30;
   private final PocketLayout layout;
   private final int[] variants;

   public PocketPainter(PocketLayout layout) {
      this(layout, null);
   }

   /** {@code variants[roomId]} selects the decor of mutating corridors (null: all 0, the generated look). */
   public PocketPainter(PocketLayout layout, int[] variants) {
      this.layout = layout;
      this.variants = variants;
   }

   public PocketLayout layout() {
      return this.layout;
   }

   private int variant(Room r) {
      return this.variants == null || r.id >= this.variants.length ? 0 : this.variants[r.id];
   }

   private double h(Room r, int a, int b, int salt) {
      return PocketLayout.unit(r.seed + salt * 0x5851F42D4C957F2DL + this.variant(r) * 0x2545F4914F6CDD1DL, a, b);
   }

   /** Geometry-only hash: never changes with the decor variant. */
   private static double g(Room r, int a, int b, int salt) {
      return PocketLayout.unit(r.seed + salt * 0x5851F42D4C957F2DL, a, b);
   }

   private static void fill(byte[] out, int y0, int y1, byte id) {
      for (int y = Math.max(0, y0); y <= Math.min(HEIGHT - 1, y1); y++) {
         out[y] = id;
      }
   }

   /** Fills {@code out[0..255]} for slot-local column (lx, lz). */
   public void column(int lx, int lz, byte[] out) {
      Arrays.fill(out, AIR);
      Room room = this.layout.roomAtBlock(lx, lz);
      if (room == null) {
         fill(out, MASS_BOTTOM, MASS_TOP, MASS);
         return;
      }
      int rx = lx - room.minX();
      int rz = lz - room.minZ();
      switch (room.kind) {
         case CORRIDOR, FLOODED_CORRIDOR -> this.corridor(room, lx, lz, out);
         case HALL -> this.hall(room, rx, rz, lx, lz, out);
         case VOID -> this.voidHall(room, rx, rz, lx, lz, out);
         case FLOODED -> this.flooded(room, rx, rz, lx, lz, out);
         case APARTMENT -> this.apartment(room, rx, rz, lx, lz, out);
         case STREET -> this.street(room, rx, rz, lx, lz, out);
         case SHAFT -> this.shaft(room, rx, rz, lx, lz, out);
         case THRONE -> this.throne(room, rx, rz, lx, lz, out);
      }
   }

   // ------------------------------------------------------------------ shared pieces

   private static boolean boundary(Room r, int rx, int rz) {
      return rx == 0 || rz == 0 || rx == r.sizeX() - 1 || rz == r.sizeZ() - 1;
   }

   /** True when (lx, lz) is an opening column of one of the room's doorways on its own boundary row. */
   static boolean opening(Room r, int lx, int lz) {
      for (Door d : r.doors) {
         int bx = d.cellX() * 16;
         int bz = d.cellZ() * 16;
         boolean hit = switch (d.side()) {
            case PocketLayout.N -> lx >= bx + 7 && lx <= bx + 9 && (lz == bz - 1 || lz == bz);
            case PocketLayout.S -> lx >= bx + 7 && lx <= bx + 9 && (lz == bz + 15 || lz == bz + 16);
            case PocketLayout.E -> lz >= bz + 7 && lz <= bz + 9 && (lx == bx + 15 || lx == bx + 16);
            default -> lz >= bz + 7 && lz <= bz + 9 && (lx == bx - 1 || lx == bx);
         };
         if (hit) {
            return true;
         }
      }
      return false;
   }

   /** Doorway carved through a boundary wall: floor kept, air above, a rusted lintel; flooded rooms keep a sill. */
   private static void doorway(byte[] out, boolean sill) {
      fill(out, F + 1, F + 4, AIR);
      if (sill) {
         out[F + 1] = TILE;
      }
      out[F + 5] = METAL;
      out[F] = out[F] == AIR || out[F] == WATER ? CONCRETE : out[F];
   }

   /** Rust streak running down a wall face from the ceiling (decor). */
   private void streak(Room r, int lx, int lz, byte[] out, int bottom, int top, byte base) {
      double k = 0.10 + 0.13 * r.corrosion;
      if (this.h(r, lx, lz, 11) < k) {
         int len = 2 + (int)(this.h(r, lx, lz, 12) * (top - bottom));
         fill(out, top - len, top, CORRODED);
      } else if (base != CORRODED && this.h(r, lx, lz, 13) < 0.04 * (1 + r.corrosion)) {
         int y = bottom + (int)(this.h(r, lx, lz, 14) * (top - bottom));
         out[Math.max(bottom, Math.min(top, y))] = CORRODED;
      }
   }

   private void stain(Room r, int lx, int lz, byte[] out, int y) {
      double k = 0.025 + 0.05 * r.corrosion;
      if (out[y] == AIR && PocketPalette.support(out[y - 1]) && out[y - 1] != WATER && this.h(r, lx, lz, 21) < k) {
         out[y] = OOZE_STAIN;
      }
   }

   // ------------------------------------------------------------------ corridors of decay (and flooded / domain corridors)

   private static int corridorWidth(Room r) {
      double u = g(r, 1, 2, 3);
      return r.zone == Zone.DOMAIN ? (u < 0.5 ? 3 : 5) : u < 0.68 ? 3 : 5;
   }

   private static int corridorHeight(Room r) {
      return 4 + (int)(g(r, 2, 3, 4) * (r.zone == Zone.DOMAIN ? 5 : 4));
   }

   private static int corridorStyle(Room r) {
      return r.zone == Zone.DOMAIN ? 2 : (int)(g(r, 3, 4, 5) * 4);
   }

   /** Is (cx, cz) inside the corridor's tubes (cell-local)? */
   private boolean tube(Room r, int cx, int cz) {
      int mask = this.layout.doorMask(r.cx, r.cz);
      int hw = corridorWidth(r) / 2;
      int hub = hw + (Integer.bitCount(mask & 15) <= 1 || r.purpose != Purpose.NONE ? 1 : 0);
      if (Math.abs(cx - 8) <= hub && Math.abs(cz - 8) <= hub) {
         return true;
      }
      boolean alongZ = Math.abs(cx - 8) <= hw;
      boolean alongX = Math.abs(cz - 8) <= hw;
      if ((mask & 1 << PocketLayout.N) != 0 && alongZ && cz <= 8 || (mask & 1 << PocketLayout.S) != 0 && alongZ && cz >= 8) {
         return true;
      }
      if ((mask & 1 << PocketLayout.W) != 0 && alongX && cx <= 8 || (mask & 1 << PocketLayout.E) != 0 && alongX && cx >= 8) {
         return true;
      }
      if (this.layout.wrapCell(r.cx, r.cz) && Math.abs(cx - 8) <= 1) {
         return r.cz == 0 ? cz <= 8 : cz >= 8;
      }
      return false;
   }

   /** Distance from the cell centre along a side and the offset across it. */
   private static int along(int side, int cx, int cz) {
      return switch (side) {
         case PocketLayout.N -> 8 - cz;
         case PocketLayout.S -> cz - 8;
         case PocketLayout.E -> cx - 8;
         default -> 8 - cx;
      };
   }

   private static int across(int side, int cx, int cz) {
      return side == PocketLayout.N || side == PocketLayout.S ? cx - 8 : cz - 8;
   }

   private void corridor(Room r, int lx, int lz, byte[] out) {
      int cx = lx & 15;
      int cz = lz & 15;
      boolean flooded = r.kind == Kind.FLOODED_CORRIDOR;
      int hgt = corridorHeight(r);
      int style = corridorStyle(r);
      int top = F + hgt;
      fill(out, MASS_BOTTOM, MASS_TOP, MASS);
      // the hidden passage (escape route II) or the fallback home door: niche, thin wall, shaft
      int side = r.secretSide;
      if (side >= 0 && (r.purpose == Purpose.SECRET_EXIT || r.purpose == Purpose.HOME_DOOR)) {
         int d = along(side, cx, cz);
         int a = across(side, cx, cz);
         if (d >= 4 && d <= 7 && Math.abs(a) <= 2) {
            fill(out, MASS_TOP, SECRET_TOP + 2, MASS);
         }
         if (Math.abs(a) <= 1 && d >= 1 && d <= 3) {
            fill(out, F + 1, F + 3, AIR);
            out[F] = TILE;
            out[F + 4] = r.purpose == Purpose.SECRET_EXIT ? CONCRETE : METAL;
            return;
         }
         if (r.purpose == Purpose.SECRET_EXIT) {
            if (Math.abs(a) <= 1 && d == 4) {
               fill(out, F + 1, F + 3, THIN_WALL);
               return;
            }
            if (Math.abs(a) <= 1 && (d == 5 || d == 6)) {
               out[F] = CONCRETE;
               fill(out, F + 1, SECRET_TOP - 1, AIR);
               out[SECRET_TOP] = SEAM;
               if (d == 6 && a == 0) {
                  fill(out, F + 1, SECRET_TOP - 2, (byte)(LADDER + ((side + 2) & 3)));
               }
               return;
            }
         } else {
            if (a == 0 && d == 4) {
               int facing = (side + 2) & 3;
               out[F + 1] = (byte)(HOME_LOW + facing);
               out[F + 2] = (byte)(HOME_UP + facing);
               out[F + 3] = METAL;
               return;
            }
            if (Math.abs(a) <= 1 && d == 4) {
               fill(out, F + 1, F + 3, METAL);
               return;
            }
            if (a == 0 && d == 5) {
               fill(out, F + 1, F + 2, AIR);
               out[F] = METAL;
               return;
            }
         }
      }
      if (!this.tube(r, cx, cz)) {
         // wall faces next to the tube get the corridor's material, rust streaks and false doorways
         boolean face = false;
         for (int ox = -1; ox <= 1 && !face; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
               int nx = cx + ox;
               int nz = cz + oz;
               if (nx >= 0 && nz >= 0 && nx < 16 && nz < 16 && this.tube(r, nx, nz)) {
                  face = true;
                  break;
               }
            }
         }
         if (face) {
            byte base = r.zone == Zone.DOMAIN ? CORRODED : style == 2 ? METAL : CONCRETE;
            fill(out, F, top + 1, base);
            if (style == 1) {
               fill(out, F, F + 2, TILE);
            }
            this.streak(r, lx, lz, out, F + 1, top + 1, base);
            if (this.h(r, lx, lz, 31) < 0.012 && hgt >= 5) {
               // a doorway-shaped recess that leads nowhere
               fill(out, F + 1, F + 3, VOID_GLASS);
               out[F + 4] = METAL;
            }
         }
         return;
      }
      // inside the tube
      boolean centreLine = cx == 8 || cz == 8;
      out[F] = style == 1 ? TILE : r.zone == Zone.DOMAIN ? CORRODED : CONCRETE;
      fill(out, F + 1, top, AIR);
      out[top + 1] = CONCRETE;
      if (style == 2 && centreLine && Math.abs(cx - 8) + Math.abs(cz - 8) > 1) {
         out[F] = GRATE;
         out[F - 1] = OOZE;
         out[F - 2] = CORRODED;
      }
      if (style == 3 && g(r, lx, lz, 41) < 0.06) {
         // broken ceiling: a narrow slot going up into the dark
         fill(out, top + 1, top + 8, AIR);
         out[top + 9] = MASS;
      }
      // fallen debris to step over (never in doorways or where a portal puts a traveller)
      if (style == 3 && !centreLine && Math.abs(cx - 8) + Math.abs(cz - 8) >= 3 && cx > 2 && cx < 13 && cz > 2 && cz < 13 && g(r, lx, lz, 42) < 0.07) {
         out[F + 1] = CONCRETE;
      }
      // ceiling lights: one at the hub, then every six blocks along the tubes; many are dead
      int dist = Math.abs(cx - 8) + Math.abs(cz - 8);
      if (centreLine && dist % 6 == 0 && out[top + 1] == CONCRETE) {
         out[top + 1] = this.h(r, lx, lz, 51) < (r.purpose == Purpose.ARRIVAL ? 1.0 : 0.45 - 0.1 * r.corrosion) ? LIGHT : LIGHT_DEAD;
      }
      // pipes along a ceiling corner
      int hw = corridorWidth(r) / 2;
      boolean pipeSide = this.h(r, r.cx, r.cz, 52) < 0.5;
      if ((style == 0 || style == 2) && hgt >= 5 && (cx == 8 + (pipeSide ? hw : -hw) || cz == 8 + (pipeSide ? hw : -hw))) {
         out[top] = METAL;
      }
      if (flooded) {
         out[F + 1] = WATER;
         // sills keep the water inside the cell
         if (cx == 0 || cz == 0 || cx == 15 || cz == 15) {
            out[F + 1] = TILE;
         }
      } else {
         this.stain(r, lx, lz, out, F + 1);
      }
      if (cx == 8 && cz == 8) {
         if (r.purpose == Purpose.NOTE || r.purpose == Purpose.ARRIVAL) {
            out[F + 1] = NOTE;
         }
      }
      if (r.purpose == Purpose.SIGIL && glyph(r.sigil, cx - 6, cz - 7)) {
         out[F] = SIGIL;
      }
   }

   /** Roman numeral glyph (I, II, III) on a 5 x 3 floor patch. */
   static boolean glyph(int n, int gx, int gz) {
      if (gx < 0 || gx > 4 || gz < 0 || gz > 2) {
         return false;
      }
      return switch (n) {
         case 1 -> gx == 2;
         case 2 -> gx == 1 || gx == 3;
         case 3 -> gx == 0 || gx == 2 || gx == 4;
         default -> false;
      };
   }

   // ------------------------------------------------------------------ decay hall

   private void hall(Room r, int rx, int rz, int lx, int lz, byte[] out) {
      int top = F + 9 + (int)(g(r, 5, 6, 7) * 4);
      fill(out, MASS_BOTTOM, Math.max(MASS_TOP, top + 2), MASS);
      if (boundary(r, rx, rz)) {
         fill(out, F, top + 1, CONCRETE);
         this.streak(r, lx, lz, out, F + 1, top, CONCRETE);
         if (opening(r, lx, lz)) {
            doorway(out, false);
         }
         return;
      }
      out[F] = TILE;
      fill(out, F + 1, top, AIR);
      out[top + 1] = CONCRETE;
      int px = rx % 8;
      int pz = rz % 8;
      if ((px == 3 || px == 4) && (pz == 3 || pz == 4) && rx > 2 && rz > 2 && rx < r.sizeX() - 3 && rz < r.sizeZ() - 3) {
         fill(out, F + 1, top, PILLAR);
         if (this.h(r, rx / 8, rz / 8, 61) < 0.3) {
            out[F + 2 + (int)(this.h(r, rx / 8, rz / 8, 62) * 4)] = CORRODED;
         }
         return;
      }
      // the sunken middle holds still water
      double dx = rx - r.sizeX() / 2.0;
      double dz = rz - r.sizeZ() / 2.0;
      if (Math.abs(dx) < 5 && Math.abs(dz) < 5) {
         out[F] = AIR;
         out[F - 1] = TILE;
         out[F] = WATER;
      }
      if (g(r, lx, lz, 63) < 0.05 && out[F] != WATER) {
         out[F + 1] = CONCRETE;
      }
      if ((px == 3 || px == 4) && (pz == 0) && this.h(r, rx / 8, rz / 8, 64) < 0.35) {
         out[top + 1] = LIGHT;
      }
      this.stain(r, lx, lz, out, F + 1);
      if (r.purpose == Purpose.NOTE && rx == r.sizeX() / 2 && rz == r.sizeZ() / 2 - 7) {
         out[F + 1] = NOTE;
      }
   }

   // ------------------------------------------------------------------ colossal voids

   /** Side (N or S) whose wall carries the loop stair, or -1 when a doorway is in the way. */
   static int loopStairSide(Room r) {
      for (int side : new int[]{PocketLayout.N, PocketLayout.S}) {
         int wallZ = side == PocketLayout.N ? r.minZ() : r.minZ() + r.sizeZ();
         boolean clear = r.sizeX() >= 32;
         for (Door d : r.doors) {
            boolean horizontal = d.side() == PocketLayout.N || d.side() == PocketLayout.S;
            int dx = d.blockX() - r.minX();
            if (horizontal && d.blockZ() == wallZ && dx >= 3 && dx <= 29) {
               clear = false;
            }
         }
         if (clear) {
            return side;
         }
      }
      return -1;
   }

   /** Min corner of the loop stair's landing doorway (portal box 3 x 3 x 1), slot-local. */
   public static int[] loopStairLanding(Room r) {
      int side = loopStairSide(r);
      int z = side == PocketLayout.S ? r.minZ() + r.sizeZ() - 2 : r.minZ() + 1;
      return new int[]{r.minX() + 22, F + 17, z};
   }

   /** Feet position just inside a doorway of the room (slot-local x, y, z). */
   public static int[] doorInside(Room r, Door d) {
      int x = d.blockX();
      int z = d.blockZ();
      boolean mine = r.contains(d.cellX(), d.cellZ());
      int dir = mine ? (d.side() + 2) & 3 : d.side();
      // the wall line coordinates are on the south / east edge; step into the room by two blocks
      int ix = switch (d.side()) {
         case PocketLayout.E -> mine ? x - 2 : x + 1;
         case PocketLayout.W -> mine ? x + 1 : x - 2;
         default -> x;
      };
      int iz = switch (d.side()) {
         case PocketLayout.S -> mine ? z - 2 : z + 1;
         case PocketLayout.N -> mine ? z + 1 : z - 2;
         default -> z;
      };
      return new int[]{ix, F + 1, iz, dir};
   }

   private static final int RING = 4;

   private void voidHall(Room r, int rx, int rz, int lx, int lz, byte[] out) {
      int sx = r.sizeX();
      int sz = r.sizeZ();
      if (boundary(r, rx, rz)) {
         fill(out, 0, VOID_CEIL + 1, MASS);
         fill(out, F - 3, VOID_CEIL - 1, CONCRETE);
         this.streak(r, lx, lz, out, F + 1, VOID_CEIL - 1, CONCRETE);
         if (this.h(r, lx, lz, 71) < 0.012) {
            out[F + 10 + (int)(this.h(r, lx, lz, 72) * 60)] = LIGHT;
         }
         if (opening(r, lx, lz)) {
            doorway(out, false);
         }
         int side = r.loopStair ? loopStairSide(r) : -1;
         int wallZ = side == PocketLayout.S ? sz - 1 : 0;
         if (side >= 0 && rz == wallZ && rx >= 22 && rx <= 24) {
            fill(out, F + 17, F + 19, VOID_GLASS);
         }
         if (side >= 0 && rz == wallZ && (rx == 21 || rx == 25)) {
            fill(out, F + 16, F + 20, PILLAR);
         }
         return;
      }
      out[0] = MASS;
      out[VOID_CEIL] = MASS;
      out[VOID_CEIL + 1] = MASS;
      int edge = Math.min(Math.min(rx, rz), Math.min(sx - 1 - rx, sz - 1 - rz));
      boolean ring = edge <= RING;
      // bridges continue each doorway straight across the abyss (with at most one missing slab)
      boolean bridge = false;
      for (Door d : r.doors) {
         if (g(r, d.cellX(), d.cellZ(), 73 + d.side()) >= 0.6) {
            continue;
         }
         if (d.side() == PocketLayout.N || d.side() == PocketLayout.S) {
            if (Math.abs(lx - d.blockX()) <= 1) {
               int gap = 6 + (int)(g(r, d.cellX(), d.cellZ(), 77) * (sz - 12));
               bridge = rz != gap || g(r, d.cellX(), d.cellZ(), 78) < 0.4;
            }
         } else if (Math.abs(lz - d.blockZ()) <= 1) {
            int gap = 6 + (int)(g(r, d.cellX(), d.cellZ(), 77) * (sx - 12));
            bridge = rx != gap || g(r, d.cellX(), d.cellZ(), 78) < 0.4;
         }
         if (bridge) {
            break;
         }
      }
      // giant columns
      int px = Math.floorMod(rx - 12, 16);
      int pz = Math.floorMod(rz - 12, 16);
      boolean pillar = !ring && px <= 4 && pz <= 4 && edge >= RING + 3;
      if (pillar) {
         int id = (rx - 12) / 16 * 7 + (rz - 12) / 16;
         boolean broken = g(r, id, 1, 79) < 0.3;
         int topY = broken ? F - 20 + (int)(g(r, id, 2, 80) * 60) : VOID_CEIL - 1;
         fill(out, 1, topY, PILLAR);
         for (int y = 12; y < topY; y += 12) {
            if (this.h(r, id, y, 81) < 0.5 + 0.15 * r.corrosion) {
               out[y] = CORRODED;
            }
         }
         if (bridge) {
            fill(out, F + 1, F + 4, AIR);
            out[F] = CONCRETE;
         }
         return;
      }
      // platforms around the tops of some broken columns
      if (!ring && edge >= RING + 2) {
         int qx = Math.floorMod(rx - 10, 16);
         int qz = Math.floorMod(rz - 10, 16);
         if (qx <= 8 && qz <= 8) {
            int id = (rx - 10) / 16 * 7 + (rz - 10) / 16;
            if (g(r, id, 1, 79) < 0.3 && g(r, id, 3, 82) < 0.6) {
               int topY = F - 20 + (int)(g(r, id, 2, 80) * 60);
               out[topY] = CONCRETE;
               out[topY - 1] = METAL;
            }
         }
      }
      if (ring) {
         fill(out, F - 3, F, CONCRETE);
         out[F] = edge == RING && this.h(r, lx, lz, 83) < 0.3 ? CORRODED : CONCRETE;
         if (edge == RING && g(r, lx / 3, lz / 3, 84) < 0.12) {
            out[F + 1] = BARS;
         }
         this.stain(r, lx, lz, out, F + 1);
      } else {
         fill(out, 1, 2, OOZE);
         if (bridge) {
            out[F] = CONCRETE;
            out[F - 1] = METAL;
         } else if (this.h(r, lx, lz, 85) < 0.004) {
            int len = 10 + (int)(this.h(r, lx, lz, 86) * 50);
            fill(out, VOID_CEIL - len, VOID_CEIL - 1, CHAIN);
         }
      }
      // the stair that climbs to a doorway in the wall (and comes back through the hall's own door)
      int side = r.loopStair ? loopStairSide(r) : -1;
      if (side >= 0) {
         int row = side == PocketLayout.S ? sz - 1 - rz : rz;
         if (row >= 1 && row <= 2 && rx >= 6 && rx <= 21) {
            int k = rx - 6;
            fill(out, F + 1, F + k, CONCRETE);
            out[F + 1 + k] = (byte)(STAIR + PocketLayout.E);
            fill(out, F + 2 + k, F + 5 + k, AIR);
         }
         if (row >= 1 && row <= 3 && rx >= 22 && rx <= 24) {
            fill(out, F + 1, F + 16, CONCRETE);
            fill(out, F + 17, F + 20, AIR);
         }
      }
      if (r.purpose == Purpose.SIGIL) {
         int cx = sx - 12;
         if (glyph(r.sigil, rx - cx, rz - 1)) {
            out[F] = SIGIL;
         }
      }
      if (r.purpose == Purpose.NOTE && rx == 2 && rz == sz / 2) {
         out[F + 1] = NOTE;
      }
   }

   // ------------------------------------------------------------------ flooded sections

   private void flooded(Room r, int rx, int rz, int lx, int lz, byte[] out) {
      int sx = r.sizeX();
      int sz = r.sizeZ();
      int top = F + 7;
      fill(out, MASS_BOTTOM, MASS_TOP, MASS);
      if (boundary(r, rx, rz)) {
         fill(out, F - 4, top, TILE);
         this.streak(r, lx, lz, out, F + 2, top - 1, TILE);
         if (opening(r, lx, lz)) {
            doorway(out, true);
         }
         return;
      }
      out[top] = CONCRETE;
      out[F] = TILE;
      fill(out, F + 2, top - 1, AIR);
      out[F + 1] = WATER;
      boolean channel = Math.abs(rx - sx / 2) <= 2 || Math.abs(rz - sz / 2) <= 2;
      if (channel) {
         out[F - 3] = TILE;
         fill(out, F - 2, F + 1, WATER);
         int fromMid = Math.max(Math.abs(rx - sx / 2), Math.abs(rz - sz / 2));
         if (fromMid >= 5 && fromMid <= 9) {
            // a low arch: one block of air over the water
            fill(out, F + 3, top - 1, CONCRETE);
         }
         if (this.h(r, lx, lz, 91) < 0.02) {
            out[F - 3] = LIGHT;
         }
      }
      int px = Math.floorMod(rx - 4, 10);
      int pz = Math.floorMod(rz - 4, 10);
      if (!channel && px <= 2 && pz <= 2 && rx > 2 && rz > 2 && rx < sx - 3 && rz < sz - 3) {
         fill(out, F + 1, top - 1, PILLAR);
         if (this.h(r, rx / 10, rz / 10, 92) < 0.5) {
            out[F + 2] = CORRODED;
         }
         return;
      }
      if ((rx == 1 || rz == 1) && this.h(r, lx, lz, 93) < 0.5) {
         out[top - 1] = METAL;
      }
      if (r.purpose == Purpose.SIGIL && Math.abs(rx - sx / 2) <= 2 && Math.abs(rz - sz / 2) <= 2) {
         fill(out, F - 3, F + 1, CONCRETE);
         out[F + 1] = glyph(r.sigil, rx - sx / 2 + 2, rz - sz / 2 + 1) ? SIGIL : CONCRETE;
         fill(out, F + 2, top - 1, AIR);
      }
      if (r.purpose == Purpose.NOTE && rx == 2 && rz == 2) {
         out[F + 1] = CONCRETE;
         out[F + 2] = NOTE;
      }
   }

   // ------------------------------------------------------------------ false safe zones

   /** Closet behind a north-wall door of an apartment's first cell (x, z slot-local): wrong doors use x + 11, the home door x + 4. */
   public static int[] closet(Room r) {
      return new int[]{r.minX() + 11, F + 1, r.minZ() + 1};
   }

   public static int[] homeCloset(Room r) {
      if (r.kind == Kind.CORRIDOR) {
         int side = r.secretSide;
         return new int[]{r.minX() + 8 + PocketLayout.DX[side] * 5, F + 1, r.minZ() + 8 + PocketLayout.DZ[side] * 5};
      }
      return new int[]{r.minX() + 4, F + 1, r.minZ() + 1};
   }

   /** The home door's lower block (x, y, z slot-local). */
   public static int[] homeDoor(Room r) {
      if (r.kind == Kind.CORRIDOR) {
         int side = r.secretSide;
         return new int[]{r.minX() + 8 + PocketLayout.DX[side] * 4, F + 1, r.minZ() + 8 + PocketLayout.DZ[side] * 4};
      }
      return new int[]{r.minX() + 4, F + 1, r.minZ() + 2};
   }

   /** Where the knocking of a listening door comes from, and the door that silences it. */
   public static int[] listeningPoint(Room r) {
      return new int[]{r.minX() + 11, F + 2, r.minZ() + 12};
   }

   public static int[] listeningDoor(Room r) {
      return new int[]{r.minX() + 8, F + 1, r.minZ() + 12};
   }

   /** Centre of the sigil glyph (x, y, z slot-local) for rooms with a sigil. */
   public static double[] sigilCenter(Room r) {
      return switch (r.kind) {
         case VOID -> new double[]{r.minX() + r.sizeX() - 12 + 2.5, F + 1, r.minZ() + 2.5};
         case FLOODED -> new double[]{r.minX() + r.sizeX() / 2 + 0.5, F + 2, r.minZ() + r.sizeZ() / 2 + 0.5};
         case APARTMENT -> new double[]{r.minX() + 4.5, F + 1, r.minZ() + 11.5};
         default -> new double[]{r.minX() + 8.5, F + 1, r.minZ() + 8.5};
      };
   }

   private void apartment(Room r, int rx, int rz, int lx, int lz, byte[] out) {
      int cx = lx & 15;
      int cz = lz & 15;
      boolean firstCell = lx >> 4 == r.cx && lz >> 4 == r.cz;
      fill(out, MASS_BOTTOM, MASS_TOP, MASS);
      if (boundary(r, rx, rz)) {
         fill(out, F, F + 5, PLASTER);
         out[F] = PLANKS;
         if (opening(r, lx, lz)) {
            doorway(out, false);
            out[F] = PLANKS;
            out[F + 5] = PLASTER;
            return;
         }
         // windows with nothing behind them
         boolean along = rz == 0 || rz == r.sizeZ() - 1;
         int c = along ? cx : cz;
         if ((c == 3 || c == 4 || c == 12 || c == 13) && !(firstCell && rz == 0 && cx <= 13)) {
            fill(out, F + 2, F + 3, VOID_GLASS);
         }
         if (firstCell && rz == 0 && cx == 13 && this.h(r, 0, 0, 101) < 0.5) {
            // a door in the outer wall that opens onto concrete
            out[F + 1] = (byte)(DOOR_LOW + PocketLayout.S);
            out[F + 2] = (byte)(DOOR_UP + PocketLayout.S);
         }
         return;
      }
      out[F] = PLANKS;
      fill(out, F + 1, F + 4, AIR);
      out[F + 5] = PLASTER;
      // partitions: north half, south-west and south-east rooms, joined by wooden doors
      if (cz == 8 && cx >= 1 && cx <= 14) {
         fill(out, F + 1, F + 4, PLASTER);
         if (cx == 4 || cx == 12) {
            out[F + 1] = (byte)(DOOR_LOW + PocketLayout.S);
            out[F + 2] = (byte)(DOOR_UP + PocketLayout.S);
         }
         return;
      }
      if (cx == 8 && cz >= 9 && cz <= 14) {
         fill(out, F + 1, F + 4, PLASTER);
         if (cz == 12) {
            out[F + 1] = (byte)(DOOR_LOW + PocketLayout.E);
            out[F + 2] = (byte)(DOOR_UP + PocketLayout.E);
         }
         return;
      }
      // the closets on the north wall of the first cell: a wrong door at x 11, the home door at x 4
      if (firstCell && (r.wrongDoor || r.purpose == Purpose.HOME_DOOR)) {
         int dx = r.purpose == Purpose.HOME_DOOR ? 4 : 11;
         if (cz <= 2 && Math.abs(cx - dx) == 1) {
            fill(out, F + 1, F + 4, r.purpose == Purpose.HOME_DOOR ? METAL : PLASTER);
            return;
         }
         if (cx == dx && cz == 2) {
            byte low = r.purpose == Purpose.HOME_DOOR ? HOME_LOW : DOOR_LOW;
            byte up = r.purpose == Purpose.HOME_DOOR ? HOME_UP : DOOR_UP;
            out[F + 1] = (byte)(low + PocketLayout.S);
            out[F + 2] = (byte)(up + PocketLayout.S);
            fill(out, F + 3, F + 4, r.purpose == Purpose.HOME_DOOR ? METAL : PLASTER);
            return;
         }
         if (cx == dx && cz == 1) {
            out[F] = r.purpose == Purpose.HOME_DOOR ? METAL : PLANKS;
            fill(out, F + 3, F + 4, PLASTER);
            return;
         }
      }
      double wrong = this.h(r, r.cx, r.cz, 102);
      // north half: a table with chairs, a bookcase, a carpet
      if (cz >= 1 && cz <= 7) {
         if (cx >= 5 && cx <= 6 && cz == 5) {
            out[F + 1] = FENCE;
            out[F + 2] = this.h(r, lx, lz, 103) < 0.4 ? POT : PLATE;
            if (wrong < 0.3) {
               out[F + 4] = CHAIR_CEIL;
            }
         } else if (cx >= 5 && cx <= 6 && (cz == 4 || cz == 6)) {
            out[F + 1] = (byte)(CHAIR + (cz == 4 ? PocketLayout.S : PocketLayout.N));
         } else if (cx == 1 && cz >= 3 && cz <= 6) {
            fill(out, F + 1, F + 3, BOOKSHELF);
         } else if (cx >= 3 && cx <= 8 && cz >= 3 && cz <= 7) {
            out[F + 1] = CARPET;
         }
         if (cx == 7 && cz == 1 && this.h(r, lx, lz, 104) < 0.7) {
            out[F + 5] = LIGHT;
         }
      } else if (cz >= 9 && cx <= 7) {
         // south-west: a kitchen corner
         if (cz == 14 && cx >= 2 && cx <= 5) {
            out[F + 1] = cx == 3 ? CAULDRON : FENCE;
            if (cx != 3) {
               out[F + 2] = PLATE;
            }
         } else if (cx == 4 && cz == 9 + (int)(wrong * 3) && wrong > 0.3 && wrong < 0.6) {
            out[F + 1] = (byte)(CHAIR + PocketLayout.W);
         }
         if (cx == 4 && cz == 11) {
            out[F + 5] = this.h(r, lx, lz, 105) < 0.5 ? LIGHT : LIGHT_DEAD;
         }
      } else if (cz >= 9) {
         // south-east: a wardrobe of shelves; a chair facing the wall
         if (cx == 14 && cz >= 10 && cz <= 13) {
            fill(out, F + 1, F + 2, BOOKSHELF);
         } else if (cx == 13 && cz == 10 && wrong >= 0.6) {
            out[F + 1] = (byte)(CHAIR + PocketLayout.E);
         }
      }
      if (r.purpose == Purpose.SIGIL && firstCell && glyph(r.sigil, cx - 2, cz - 10)) {
         out[F] = SIGIL;
      }
      if (r.purpose == Purpose.NOTE && firstCell && cx == 10 && cz == 5) {
         out[F + 1] = NOTE;
      }
   }

   private void street(Room r, int rx, int rz, int lx, int lz, byte[] out) {
      int sx = r.sizeX();
      int sz = r.sizeZ();
      int top = F + 15;
      fill(out, MASS_BOTTOM, top + 1, MASS);
      boolean alongX = sx >= sz;
      int across = alongX ? rz : rx;
      int width = alongX ? sz : sx;
      int run = alongX ? rx : rz;
      if (boundary(r, rx, rz)) {
         fill(out, F, top, FACADE);
         int c = (alongX ? rx : rz) % 6;
         int c2 = (alongX ? rz : rx) % 6;
         int w = across == 0 || across == width - 1 ? c : c2;
         if (w == 2 || w == 3) {
            fill(out, F + 3, F + 4, VOID_GLASS);
            fill(out, F + 8, F + 9, VOID_GLASS);
         }
         if (opening(r, lx, lz)) {
            doorway(out, false);
         }
         return;
      }
      out[top] = MASS;
      int mid = width / 2;
      int fromMid = Math.abs(across - mid);
      fill(out, F + 1, top - 1, AIR);
      if (fromMid <= 3) {
         out[F] = ROAD;
         if (fromMid == 0 && run % 6 < 3) {
            out[F] = LANE;
         }
         if (run == sx / 3 && fromMid == 1 && this.h(r, 1, 1, 111) < 0.5) {
            // a street lamp standing in the road
            fill(out, F + 1, F + 4, BARS);
            out[F + 5] = LIGHT;
         }
      } else {
         out[F] = SIDEWALK;
         out[F + 1] = across == 1 || across == width - 2 ? AIR : SIDEWALK;
         if (across == 1 || across == width - 2) {
            out[F + 1] = AIR;
         }
         if (fromMid == 5 && run % 12 == 6) {
            fill(out, F + 2, F + 5, BARS);
            out[F + 6] = this.h(r, run, across, 112) < 0.6 ? LIGHT : LIGHT_DEAD;
         } else if (fromMid == 6 && run % 12 == 2) {
            out[F + 2] = (byte)(CHAIR + (across < mid ? PocketLayout.N : PocketLayout.S));
         }
      }
      if (r.purpose == Purpose.NOTE && run == 3 && fromMid == 5) {
         out[F + 2] = NOTE;
      }
   }

   // ------------------------------------------------------------------ shafts

   private void shaft(Room r, int rx, int rz, int lx, int lz, byte[] out) {
      fill(out, 0, SHAFT_TOP + 12, MASS);
      if (boundary(r, rx, rz)) {
         fill(out, F, SHAFT_TOP + 10, CONCRETE);
         this.streak(r, lx, lz, out, F + 1, SHAFT_TOP + 8, CONCRETE);
         if (opening(r, lx, lz)) {
            doorway(out, false);
         }
         return;
      }
      boolean well = rx >= 4 && rx <= 11 && rz >= 4 && rz <= 11;
      if (!well) {
         fill(out, F - 2, F, CONCRETE);
         fill(out, F + 1, SHAFT_TOP + 9, AIR);
         boolean railing = (rx == 3 || rx == 12) && rz >= 3 && rz <= 12 || (rz == 3 || rz == 12) && rx >= 3 && rx <= 12;
         if (railing && g(r, lx, lz, 121) < 0.6) {
            out[F + 1] = BARS;
         }
         this.stain(r, lx, lz, out, F + 1);
         if (rx == 1 && rz == 1 && r.purpose == Purpose.NOTE) {
            out[F + 1] = NOTE;
         }
         return;
      }
      fill(out, 2, SHAFT_TOP + 9, AIR);
      fill(out, 0, 1, OOZE);
      if (rx == 8 && rz == 7) {
         out[F - 40] = LIGHT;
         fill(out, F - 39, SHAFT_TOP + 9, CHAIN);
      }
      if (this.h(r, lx, lz, 122) < 0.03) {
         fill(out, SHAFT_TOP - 20, SHAFT_TOP + 9, CHAIN);
      }
   }

   // ------------------------------------------------------------------ the Old Man's domain

   private void throne(Room r, int rx, int rz, int lx, int lz, byte[] out) {
      int sx = r.sizeX();
      int sz = r.sizeZ();
      fill(out, MASS_BOTTOM, THRONE_CEIL + 2, MASS);
      if (boundary(r, rx, rz)) {
         fill(out, F, THRONE_CEIL, CORRODED);
         if (this.h(r, lx, lz, 131) < 0.35) {
            int len = 4 + (int)(this.h(r, lx, lz, 132) * 30);
            fill(out, THRONE_CEIL - len, THRONE_CEIL, OOZE);
         }
         if (opening(r, lx, lz)) {
            doorway(out, false);
         }
         return;
      }
      out[THRONE_CEIL] = MASS;
      fill(out, F + 1, THRONE_CEIL - 1, AIR);
      double dx = rx + 0.5 - sx / 2.0;
      double dz = rz + 0.5 - sz / 2.0;
      double d = Math.sqrt(dx * dx + dz * dz);
      boolean perimeter = rx <= 3 || rz <= 3 || rx >= sx - 4 || rz >= sz - 4;
      boolean spoke = Math.abs(dx) <= 1.5 || Math.abs(dz) <= 1.5;
      boolean ringPath = d >= 10.5 && d <= 13.5;
      out[F] = OOZE;
      out[F - 1] = CORRODED;
      // eight colossal corroded pillars around the dais
      double ang = Math.atan2(dz, dx);
      double sector = Math.round(ang / (Math.PI / 4.0)) * (Math.PI / 4.0) + Math.PI / 8.0;
      double pxc = Math.cos(sector) * 21.0;
      double pzc = Math.sin(sector) * 21.0;
      if (Math.abs(dx - pxc) <= 2.5 && Math.abs(dz - pzc) <= 2.5) {
         fill(out, F, THRONE_CEIL - 1, CORRODED);
         for (int y = F + 3; y < THRONE_CEIL; y += 7) {
            if (this.h(r, (int)pxc, y, 133) < 0.5) {
               out[y] = OOZE;
            }
         }
         return;
      }
      if (d <= 5.5) {
         // the dais and the breach: the real exit of the dimension
         fill(out, F, F + 2, CORRODED);
         if (Math.abs(dx) <= 1.5 && Math.abs(dz) <= 1.5) {
            out[F + 2] = BREACH;
         }
         return;
      }
      if (d <= 7.5) {
         fill(out, F, F + 1, CORRODED);
         return;
      }
      if (perimeter || spoke && d < 30 || ringPath) {
         out[F] = CORRODED;
         out[F + 1] = this.h(r, lx, lz, 134) < 0.25 ? CORRODED : CONCRETE;
         if (this.h(r, lx, lz, 135) < 0.2) {
            out[F + 2] = OOZE_STAIN;
         }
      }
      if (!perimeter && !spoke && !ringPath && this.h(r, lx, lz, 136) < 0.006) {
         int len = 6 + (int)(this.h(r, lx, lz, 137) * 24);
         fill(out, THRONE_CEIL - len, THRONE_CEIL - 1, CHAIN);
      }
   }

   /** Box of the secret shaft above which a player has escaped (slot-local x0, y0, z0, x1, y1, z1). */
   public static int[] secretExitBox(Room r) {
      int side = r.secretSide;
      int ax = PocketLayout.DX[side];
      int az = PocketLayout.DZ[side];
      int x5 = r.minX() + 8 + ax * 5;
      int z5 = r.minZ() + 8 + az * 5;
      int x6 = r.minX() + 8 + ax * 6;
      int z6 = r.minZ() + 8 + az * 6;
      int x0 = Math.min(x5, x6) - (ax == 0 ? 1 : 0);
      int x1 = Math.max(x5, x6) + (ax == 0 ? 1 : 0) + 1;
      int z0 = Math.min(z5, z6) - (az == 0 ? 1 : 0);
      int z1 = Math.max(z5, z6) + (az == 0 ? 1 : 0) + 1;
      return new int[]{x0, SECRET_TOP - 7, z0, x1, SECRET_TOP, z1};
   }

   /** Position of the thin wall's middle block (slot-local), the source of the draft. */
   public static int[] secretWall(Room r) {
      int side = r.secretSide;
      return new int[]{r.minX() + 8 + PocketLayout.DX[side] * 4, F + 2, r.minZ() + 8 + PocketLayout.DZ[side] * 4};
   }

   /** Feet position of the checkpoint (respawn) in the arrival room, slot-local. */
   public static int[] checkpoint(Room arrival) {
      return new int[]{arrival.minX() + 8, F + 1, arrival.minZ() + 9};
   }

   /** Breach box (standing on it means escape route I), slot-local. */
   public static int[] breachBox(Room throne) {
      int cx = throne.minX() + throne.sizeX() / 2;
      int cz = throne.minZ() + throne.sizeZ() / 2;
      return new int[]{cx - 2, F + 3, cz - 2, cx + 2, F + 6, cz + 2};
   }
}

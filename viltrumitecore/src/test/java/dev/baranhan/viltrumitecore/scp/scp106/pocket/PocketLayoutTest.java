package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Kind;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.PortalType;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Purpose;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Room;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Zone;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Generation of the pocket dimension: determinism, the route graph and the voxel walk to every escape. */
class PocketLayoutTest {
   private static final long[] SEEDS = {1L, 777L, -4_242_424_242L};

   private static PocketLayout layout(long seed, int slot) {
      return PocketLayout.generate(PocketLayout.instanceSeed(seed, slot), slot);
   }

   @Test
   void generationIsDeterministicPerSeedAndSlot() {
      PocketLayout a = layout(99L, 3);
      PocketLayout b = layout(99L, 3);
      PocketLayout c = layout(99L, 4);
      assertEquals(a.rooms().size(), b.rooms().size());
      byte[] colA = new byte[PocketPainter.HEIGHT];
      byte[] colB = new byte[PocketPainter.HEIGHT];
      PocketPainter pa = new PocketPainter(a);
      PocketPainter pb = new PocketPainter(b);
      for (int i = 0; i < 4000; i++) {
         int x = (i * 7919) % PocketLayout.SIZE;
         int z = (i * 104_729) % PocketLayout.SIZE;
         pa.column(x, z, colA);
         pb.column(x, z, colB);
         assertArrayEquals(colA, colB, "column " + x + "," + z);
      }
      assertNotEquals(a.seed, c.seed);
      assertEquals(a.arrival().id, b.arrival().id);
   }

   @Test
   void roomGraphIsConnectedAndEveryDeadEndHasAPurpose() {
      for (long seed : SEEDS) {
         PocketLayout l = layout(seed, 0);
         boolean[] seen = new boolean[l.rooms().size()];
         ArrayDeque<Integer> queue = new ArrayDeque<>();
         queue.add(l.arrival().id);
         seen[l.arrival().id] = true;
         while (!queue.isEmpty()) {
            Room room = l.room(queue.poll());
            room.doors.forEach(d -> {
               int o = d.other(room.id);
               if (!seen[o]) {
                  seen[o] = true;
                  queue.add(o);
               }
            });
         }
         for (Room room : l.rooms()) {
            assertTrue(seen[room.id], "room not connected: " + room + " seed " + seed);
            if (room.degree() == 1) {
               assertNotEquals(Purpose.NONE, room.purpose, "pointless dead end " + room + " seed " + seed);
            }
         }
      }
   }

   @Test
   void everyZoneAndEscapeIsGenerated() {
      for (long seed : SEEDS) {
         PocketLayout l = layout(seed, 1);
         EnumSet<Zone> zones = EnumSet.noneOf(Zone.class);
         EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
         for (Room room : l.rooms()) {
            zones.add(room.zone);
            kinds.add(room.kind);
         }
         assertEquals(EnumSet.allOf(Zone.class), zones, "seed " + seed);
         assertTrue(kinds.containsAll(EnumSet.of(Kind.CORRIDOR, Kind.VOID, Kind.FLOODED, Kind.APARTMENT, Kind.THRONE)), kinds + " seed " + seed);
         assertEquals(Kind.THRONE, l.breach().kind);
         assertEquals(Purpose.SECRET_EXIT, l.secret().purpose);
         assertTrue(l.secret().secretSide >= 0);
         assertEquals(Purpose.HOME_DOOR, l.home().purpose);
         assertEquals(Kind.VOID, l.sigil(1).kind);
         assertEquals(Kind.FLOODED, l.sigil(2).kind);
         assertEquals(Kind.APARTMENT, l.sigil(3).kind);
         assertTrue(l.portals().size() >= 3, "too few anomalies");
         assertTrue(l.portals().size() <= 48, "too many anomalies");
      }
   }

   @Test
   void setPiecesAreGeneratedAndSane() {
      for (long seed : SEEDS) {
         for (int slot = 0; slot < 3; slot++) {
            PocketLayout l = layout(seed, slot);
            EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
            EnumSet<PortalType> types = EnumSet.noneOf(PortalType.class);
            int listening = 0;
            int sanctuaries = 0;
            for (Room room : l.rooms()) {
               kinds.add(room.kind);
               listening += room.purpose == Purpose.LISTENING ? 1 : 0;
               sanctuaries += room.kind == Kind.SANCTUARY ? 1 : 0;
               if (room.kind == Kind.MAZE) {
                  for (int v = 0; v < PocketMaze.VARIANTS; v++) {
                     assertTrue(room.maze.connected(v), "maze variant " + v + " split " + room);
                  }
                  assertTrue(room.maze.differences() > 0, "maze never moves " + room);
               }
               if (room.kind == Kind.GALLERY) {
                  assertEquals(2, room.degree(), "gallery must open at both ends " + room);
                  assertTrue(room.w == 1 || room.h == 1);
               }
               if (room.kind == Kind.CATHEDRAL) {
                  assertTrue(room.w >= 5 && room.h >= 5, "cathedral too small " + room);
               }
            }
            for (PortalType t : l.portals().stream().map(PocketLayout.Portal::type).toList()) {
               types.add(t);
            }
            String at = " seed " + seed + " slot " + slot;
            assertTrue(kinds.containsAll(EnumSet.of(Kind.GALLERY, Kind.CATHEDRAL, Kind.CHASM, Kind.MAZE, Kind.ESCHER)), kinds + at);
            assertTrue(sanctuaries >= 1 && sanctuaries <= 3, "sanctuaries " + sanctuaries + at);
            assertTrue(listening >= 1, "no listening door" + at);
            assertTrue(types.containsAll(EnumSet.of(PortalType.ENDLESS, PortalType.PENROSE)), types + at);
            for (PocketLayout.Portal p : l.portals()) {
               if (p.type() == PortalType.ENDLESS || p.type() == PortalType.PENROSE) {
                  assertTrue(p.limit() > 0 && p.dir() >= 0, "an endless anomaly must be outlastable " + p);
               }
            }
         }
      }
   }

   @Test
   void movedMazeWallsKeepEveryEscapeReachable() {
      PocketLayout l = layout(777L, 5);
      int[] variants = new int[l.rooms().size()];
      java.util.Arrays.fill(variants, 1);
      assertWalk(l, new PocketWalk(l, variants), "variant 1");
   }

   @Test
   void voxelWalkReachesEveryRoomAndEveryEscapeWithoutTraps() {
      for (long seed : SEEDS) {
         PocketLayout l = layout(seed, 2);
         assertWalk(l, new PocketWalk(l), "seed " + seed);
      }
   }

   private static void assertWalk(PocketLayout l, PocketWalk w, String seed) {
      {
         int[] cp = PocketPainter.checkpoint(l.arrival());
         w.flood(cp[0], cp[1], cp[2]);
         for (Room r : l.rooms()) {
            assertTrue(w.reachedBox(r.minX() + 1, 0, r.minZ() + 1, r.minX() + r.sizeX() - 1, PocketPainter.HEIGHT, r.minZ() + r.sizeZ() - 1),
               "unreachable room " + r + " seed " + seed);
         }
         int[] b = PocketPainter.breachBox(l.breach());
         int[] s = PocketPainter.secretExitBox(l.secret());
         int[] hc = PocketPainter.homeCloset(l.home());
         assertTrue(w.reachedBox(b[0], b[1], b[2], b[3], b[4], b[5]), "breach (route I) seed " + seed);
         assertTrue(w.reachedBox(s[0], s[1], s[2], s[3], s[4], s[5]), "secret shaft (route II) seed " + seed);
         assertTrue(w.reachedBox(hc[0], hc[1], hc[2], hc[0] + 1, hc[1] + 2, hc[2] + 1), "home closet (route III) seed " + seed);
         for (int i = 1; i <= 3; i++) {
            double[] c = PocketPainter.sigilCenter(l.sigil(i));
            assertTrue(w.reachedBox((int)c[0] - 3, (int)c[1] - 2, (int)c[2] - 3, (int)c[0] + 4, (int)c[1] + 3, (int)c[2] + 4), "sigil " + i + " seed " + seed);
         }
         List<Integer> goals = new ArrayList<>();
         for (int g : w.nodesIn(b[0], b[1], b[2], b[3], b[4], b[5])) {
            goals.add(g);
         }
         for (int g : w.nodesIn(s[0], s[1], s[2], s[3], s[4], s[5])) {
            goals.add(g);
         }
         goals.add(PocketWalk.idx(cp[0], cp[1], cp[2]));
         List<Integer> traps = w.traps(goals.stream().mapToInt(Integer::intValue).toArray());
         assertTrue(traps.isEmpty(), traps.size() + " reachable spots cannot get back, e.g. "
            + (traps.isEmpty() ? "" : PocketWalk.ix(traps.get(0)) + "," + PocketWalk.iy(traps.get(0)) + "," + PocketWalk.iz(traps.get(0))) + " " + seed);
         for (Room r : l.rooms()) {
            if (r.kind == Kind.SANCTUARY) {
               int[] c = PocketPainter.sanctuaryCheckpoint(r);
               assertTrue(w.standable(c[0], c[1], c[2]), "sanctuary checkpoint " + r);
            }
            if (r.kind == Kind.ESCHER) {
               int top = PocketPainter.F + PocketPainter.ESCHER_LOOP * PocketPainter.ESCHER_LOOPS + 1;
               assertTrue(w.reachedBox(r.minX() + 5, top, r.minZ() + 5, r.minX() + 8, top + 1, r.minZ() + 8), "top of the Escher stair " + r);
            }
         }
      }
   }

   @Test
   void abyssLandingsAreStandableRingWalkways() {
      for (long seed : SEEDS) {
         PocketLayout l = layout(seed, 4);
         PocketWalk w = new PocketWalk(l);
         for (int[] at : l.abyssLandings()) {
            assertTrue(w.standable(at[0], at[1], at[2]), "landing " + at[0] + "," + at[1] + "," + at[2] + " seed " + seed);
         }
      }
   }
}

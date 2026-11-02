package dev.baranhan.viltrumitecore.scp.scp106.pocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Kind;
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
         assertTrue(l.portals().size() <= 40, "too many anomalies");
      }
   }

   @Test
   void voxelWalkReachesEveryRoomAndEveryEscapeWithoutTraps() {
      for (long seed : SEEDS) {
         PocketLayout l = layout(seed, 2);
         PocketWalk w = new PocketWalk(l);
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
            + (traps.isEmpty() ? "" : PocketWalk.ix(traps.get(0)) + "," + PocketWalk.iy(traps.get(0)) + "," + PocketWalk.iz(traps.get(0))) + " seed " + seed);
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

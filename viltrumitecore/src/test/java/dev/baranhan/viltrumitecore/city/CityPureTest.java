package dev.baranhan.viltrumitecore.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.city.citizen.CitizenSkinPainter;
import dev.baranhan.viltrumitecore.city.citizen.PathBudget;
import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.city.profile.WalkTile;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CityPureTest {
   private static WalkTile tile(int lx, int lz, int h2, int cls) {
      short[][] chunks = new short[1024][];
      short[] ch = new short[256];
      ch[(lz & 15) * 16 + (lx & 15)] = (short)WalkTile.encode(h2, cls);
      chunks[(lz >> 4) * 32 + (lx >> 4)] = ch;
      return new WalkTile(chunks);
   }

   @Test
   void walkTileRoundTripAndDecode() throws Exception {
      WalkTile t = tile(37, 300, -99, WalkTile.PREFERRED); // standing on a bottom slab at y = -50 (+0.5)
      WalkTile r = WalkTile.read(new ByteArrayInputStream(t.write()));
      int c = r.cell(37, 300);
      assertEquals(-99, WalkTile.h2(c));
      assertEquals(-50, WalkTile.feetY(c));
      assertEquals(WalkTile.PREFERRED, WalkTile.cls(c));
      assertEquals(1, r.cells());
      assertEquals(1, r.preferredCells());
      assertEquals(0, r.cell(36, 300));
      assertTrue(r.hasChunk(37 >> 4, 300 >> 4));
      assertThrows(IllegalArgumentException.class, () -> WalkTile.encode(9000, 1));
   }

   @Test
   void gridNegativeCoordinatesAndQueries() {
      WalkGrid g = new WalkGrid(null);
      // block x = -3, z = -510 lives in region (-1, -1) at local (509, 2)
      g.put(-1, -1, tile(509, 2, -100, WalkTile.WALK));
      assertEquals(-50, g.feetY(-3, -510));
      assertTrue(g.standable(-3, -50, -510));
      assertFalse(g.standable(-3, -49, -510));
      assertFalse(g.preferred(-3, -510));
      assertEquals(WalkGrid.NONE, g.feetY(-4, -510));
      long n = g.nearestCell(0, -508, 4, null);
      assertEquals(-3, WalkGrid.unpackX(n));
      assertEquals(-510, WalkGrid.unpackZ(n));
      assertEquals(Long.MIN_VALUE, g.nearestCell(20, 20, 4, null));
      Random rnd = new Random(1);
      assertEquals(Long.MIN_VALUE, g.randomCell(rnd::nextDouble, -3, -510, 0, 2, true, 50, null)); // only a WALK cell
   }

   @Test
   void dangerZonesMergeExpireAndRank() {
      DangerMap m = new DangerMap();
      m.add(0, 64, 0, DangerMap.FIGHT_RADIUS, 0, 200);
      m.add(1, 64, 1, DangerMap.EXPLOSION_RADIUS, 10, 200); // same place: merges, grows
      assertEquals(1, m.size());
      assertEquals(DangerMap.EXPLOSION_RADIUS, m.zones().get(0).radius);
      assertTrue(m.dangerous(20, 64, 0, 100));
      assertFalse(m.dangerous(40, 64, 0, 100));
      m.add(100, 64, 0, DangerMap.FIGHT_RADIUS, 20, 50);
      assertEquals(2, m.size());
      DangerMap.Zone z = m.threat(95, 64, 0, 30, 4.0);
      assertNotNull(z);
      assertEquals(100.0, z.x, 1.0);
      m.prune(70);
      assertEquals(1, m.size());
      m.prune(211);
      assertEquals(0, m.size());
      assertNull(m.threat(0, 64, 0, 211, 4.0));
      m.add(Double.NaN, 0, 0, 10, 0, 10);
      assertEquals(0, m.size());
   }

   @Test
   void profileParsesDefaultsAndRejectsNewerFormat() {
      CityProfile p = CityProfile.parse(JsonParser.parseString(
         "{\"id\":\"atheria\",\"arena\":{\"minX\":-100,\"minZ\":-100,\"maxX\":100,\"maxZ\":100},"
            + "\"districts\":[{\"name\":\"Centria\",\"minX\":0,\"minZ\":0,\"maxX\":50,\"maxZ\":50}]}").getAsJsonObject());
      assertEquals("atheria", p.name);
      assertEquals("minecraft:overworld", p.dimension);
      assertEquals(40, p.perPlayer);
      assertTrue(p.inArena(0, 0, 0));
      assertFalse(p.inArena(0, 2000, 0));
      assertFalse(p.inArena(101, 0, 0));
      assertEquals("Centria", p.districtAt(10, 10).name());
      assertEquals("Centria", p.districtAt(80, 10).name()); // nearest within 64
      assertNull(p.districtAt(-90, -90));
      assertTrue(p.chunkInArena(-7, 6));
      assertThrows(IllegalArgumentException.class, () -> CityProfile.parse(JsonParser.parseString(
         "{\"format\":99,\"id\":\"x\",\"arena\":{\"minX\":0,\"minZ\":0,\"maxX\":1,\"maxZ\":1}}").getAsJsonObject()));
   }

   @Test
   void populationTargets() {
      assertEquals(40, CityPopulation.targetPerPlayer(40, 1.0));
      assertEquals(0, CityPopulation.targetPerPlayer(40, 0.0));
      assertEquals(100, CityPopulation.targetPerPlayer(40, 2.5));
      assertEquals(160, CityPopulation.levelCap(160, 0.5)); // lowering density never lowers the cap below the profile
      assertEquals(CityManager.ABSOLUTE_MAX, CityPopulation.levelCap(160, 2.5));
      assertEquals(1.0, CityManager.clampDensity(Double.NaN));
      assertEquals(CityManager.MAX_DENSITY, CityManager.clampDensity(9));
      assertEquals(0.0, CityManager.clampDensity(-1));
   }

   @Test
   void pathBudgetPerTick() {
      int normal = 0;
      for (int i = 0; i < 50; i++) {
         if (PathBudget.tryAcquire(1000, false)) {
            normal++;
         }
      }

      assertEquals(PathBudget.NORMAL, normal);
      int urgent = 0;
      for (int i = 0; i < 50; i++) {
         if (PathBudget.tryAcquire(1000, true)) {
            urgent++;
         }
      }

      assertEquals(PathBudget.TOTAL - PathBudget.NORMAL, urgent);
      assertTrue(PathBudget.tryAcquire(1001, false));
   }

   @Test
   void skinsAreDistinctAndOpaqueWhereTheModelNeedsIt() {
      Set<Integer> hashes = new HashSet<>();
      for (int i = 0; i < CitizenSkinPainter.COUNT; i++) {
         int[] px = CitizenSkinPainter.paint(i);
         assertEquals(64 * 64, px.length);
         hashes.add(java.util.Arrays.hashCode(px));
         // head front, body front, both legs and the right arm front must be opaque
         for (int[] p : new int[][]{{8, 8}, {15, 15}, {20, 20}, {27, 31}, {4, 20}, {20, 52}, {44, 20}}) {
            assertNotEquals(0, px[p[1] * 64 + p[0]] >>> 24, "skin " + i + " transparent at " + p[0] + "," + p[1]);
         }

         // the slim arm is 3 texels wide: its back face ends one column earlier than the wide one
         int back4 = px[20 * 64 + 54] >>> 24;
         if ((i & 1) == 1) {
            assertEquals(0, back4, "slim skin " + i);
         } else {
            assertNotEquals(0, back4, "wide skin " + i);
         }
      }

      assertEquals(CitizenSkinPainter.COUNT, hashes.size());
   }
}

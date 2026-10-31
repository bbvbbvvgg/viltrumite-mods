package dev.baranhan.viltrumitecore.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenPainter;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.city.citizen.CitizenTraits;
import dev.baranhan.viltrumitecore.city.danger.ThreatRules;
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
      assertEquals(400, CityPopulation.levelCap(160, 2.5));
      assertEquals(CityManager.ABSOLUTE_MAX, CityPopulation.levelCap(300, 2.5));
      // Living City 2.0: the crowd scale multiplies the target and the cap, the config hard cap wins
      assertEquals(90, CityPopulation.targetPerPlayer(40, 1.0, 2.25));
      assertEquals(40, CityPopulation.targetPerPlayer(40, 1.0, Double.NaN));
      assertEquals(160, CityPopulation.targetPerPlayer(40, 1.0, 99.0)); // scale clamped to 4
      assertEquals(360, CityPopulation.levelCap(160, 1.0, 2.25, 480));
      assertEquals(480, CityPopulation.levelCap(300, 1.0, 2.25, 480));
      assertEquals(CityManager.ABSOLUTE_MAX, CityPopulation.levelCap(300, 1.0, 2.25, 0));
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
   void looksAreVariedAndPaintedOpaqueWhereTheModelNeedsIt() {
      Set<Integer> keys = new HashSet<>();
      Set<CitizenLook.Hair> hair = new HashSet<>();
      Set<CitizenLook.Top> tops = new HashSet<>();
      Set<CitizenLook.Hat> hats = new HashSet<>();
      Set<CitizenLook.Bag> bags = new HashSet<>();
      int female = 0;
      int slim = 0;
      Random rnd = new Random(7);
      for (int i = 0; i < 400; i++) {
         int seed = rnd.nextInt();
         CitizenLook l = CitizenLook.of(seed);
         assertEquals(l, CitizenLook.of(seed), "looks are a pure function of the seed");
         keys.add(l.textureKey());
         hair.add(l.hair());
         tops.add(l.top());
         hats.add(l.hat());
         bags.add(l.bag());
         female += l.female() ? 1 : 0;
         slim += l.slim() ? 1 : 0;
         assertTrue(l.height() > 0.85F && l.height() < 1.15F, "height " + l.height());
         assertTrue(l.width() > 0.85F && l.width() < 1.15F, "width " + l.width());
         if (i < 60) {
            int[] px = CitizenPainter.paint(l);
            assertEquals(CitizenPainter.W * CitizenPainter.H, px.length);
            // head front, body front, both legs and the right arm front must be opaque
            for (int[] p : new int[][]{{8, 8}, {20, 20}, {27, 31}, {4, 20}, {20, 52}, {44, 20}}) {
               assertNotEquals(0, px[p[1] * CitizenPainter.W + p[0]] >>> 24, "look " + seed + " transparent at " + p[0] + "," + p[1]);
            }
            // shoes are always drawn: their part texture must be painted
            assertNotEquals(0, px[(CitizenPainter.SHOE_R[1] + 5) * CitizenPainter.W + CitizenPainter.SHOE_R[0] + 6] >>> 24, "shoe " + seed);
         }
      }

      assertTrue(keys.size() > 380, "distinct looks " + keys.size());
      assertEquals(CitizenLook.Hair.values().length, hair.size());
      assertEquals(CitizenLook.Top.values().length, tops.size());
      assertEquals(CitizenLook.Hat.values().length, hats.size());
      assertEquals(CitizenLook.Bag.values().length, bags.size());
      assertTrue(female > 120 && female < 280, "female " + female);
      assertTrue(slim > 80, "slim " + slim);
   }

   @Test
   void dangerZonesNoticeTrackAndBlast() {
      DangerMap m = new DangerMap();
      // curiosity only: noticed, never a threat, never merged into a danger
      m.add(0, 64, 0, 0.0F, 30.0F, 0, 100);
      assertNull(m.threat(2, 64, 0, 1, 0));
      assertNotNull(m.noticed(20, 64, 0, 1));
      assertTrue(m.noticed(20, 64, 0, 1).alertOnly());
      assertNull(m.noticed(40, 64, 0, 1));
      // a real danger nearby wins the notice over the curiosity
      m.add(5, 64, 0, 10.0F, 0, 100);
      assertEquals(DangerMap.defaultNotice(10.0F), m.noticed(5, 64, 20, 1).notice, 1.0E-4);
      assertFalse(m.noticed(5, 64, 20, 1).alertOnly());
      assertEquals(2, m.size());
      assertEquals(1, m.dangersNear(5, 64, 0, 1, 0));
      // a tracked source moves its zone instead of leaving a trail
      m.track(42, 100, 64, 100, 12.0F, 50.0F, 0, 20);
      m.track(42, 140, 64, 100, 12.0F, 50.0F, 5, 20);
      assertEquals(3, m.size());
      assertNull(m.threat(100, 64, 100, 6, 0));
      assertNotNull(m.threat(140, 64, 100, 6, 0));
      assertNotNull(m.noticed(140, 64, 145, 6));
      // shock waves: strongest at the centre, gone after the window
      m.blast(0, 64, 0, 10.0F, 10);
      assertEquals(1.0F, m.blastAt(0, 64, 0, 10), 1.0E-4);
      assertEquals(0.5F, m.blastAt(5, 64, 0, 11), 1.0E-4);
      assertEquals(0.0F, m.blastAt(11, 64, 0, 11));
      assertEquals(0.0F, m.blastAt(0, 64, 0, 10 + DangerMap.BLAST_WINDOW));
      m.prune(200);
      assertEquals(0, m.size());
      assertEquals(DangerMap.MAX_NOTICE, DangerMap.defaultNotice(500.0F));
      assertEquals(0.0F, DangerMap.defaultNotice(0.0F));
   }

   @Test
   void threatRulesSizeDangersByWhatPeopleSee() {
      // a leviathan low over the street: big danger, seen across the district
      float[] giant = ThreatRules.hostile(5.0F, 4.0F, 10.0);
      assertTrue(giant[ThreatRules.RADIUS] >= 18.0F && giant[ThreatRules.RADIUS] <= 36.0F);
      assertEquals(DangerMap.MAX_NOTICE, giant[ThreatRules.NOTICE]);
      // high in the sky it is only a sight
      assertEquals(0.0F, ThreatRules.hostile(5.0F, 4.0F, 90.0)[ThreatRules.RADIUS]);
      // a soldier on foot: a street-sized danger, noticed further away
      float[] soldier = ThreatRules.hostile(0.6F, 1.95F, 0.0);
      assertTrue(soldier[ThreatRules.RADIUS] >= 9.0F && soldier[ThreatRules.NOTICE] > soldier[ThreatRules.RADIUS]);
      assertEquals(0.0F, ThreatRules.hostile(0.6F, 1.95F, 30.0)[ThreatRules.RADIUS]);
      assertNull(ThreatRules.projectile(0.2, 0.5F));
      assertNotNull(ThreatRules.projectile(1.5, 0.5F));
      assertNull(ThreatRules.flyby(0.5, 5.0));
      assertNull(ThreatRules.flyby(2.0, 60.0));
      assertEquals(0.0F, ThreatRules.flyby(1.5, 10.0)[ThreatRules.RADIUS]);
      assertTrue(ThreatRules.flyby(3.0, 2.0)[ThreatRules.RADIUS] > 0.0F);
      assertTrue(ThreatRules.explosionBlast(400) > ThreatRules.explosionBlast(0));
      assertEquals(0.0F, ThreatRules.noiseBlast(16.0F));
      assertTrue(ThreatRules.noiseBlast(40.0F) > 0.0F);
      assertEquals(0.0F, ThreatRules.hitBlast(8.0F));
      assertTrue(ThreatRules.hitBlast(40.0F) >= 4.0F);
      assertTrue(ThreatRules.lightning()[ThreatRules.RADIUS] > 0.0F);
   }

   @Test
   void traitsAndStatesAreConsistent() {
      Random rnd = new Random(11);
      float minPace = 9;
      float maxPace = 0;
      for (int i = 0; i < 500; i++) {
         CitizenTraits t = CitizenTraits.of(rnd.nextInt());
         assertTrue(t.bravery() >= 0.0F && t.bravery() < 1.0F);
         assertTrue(t.reaction() >= 3 && t.reaction() <= 14, "reaction " + t.reaction());
         assertTrue(t.contagion() > 0.4F && t.contagion() <= 0.95F);
         assertTrue(t.margin() >= 3.0F && t.margin() <= 10.0F);
         assertTrue(t.calmDelay() >= 60 && t.calmDelay() <= 160);
         minPace = Math.min(minPace, t.pace());
         maxPace = Math.max(maxPace, t.pace());
      }

      assertTrue(minPace < 0.9F && maxPace > 1.1F, "pace spread " + minPace + ".." + maxPace);
      for (CitizenState s : CitizenState.values()) {
         assertEquals(s, CitizenState.of(s.ordinal()));
         assertFalse(s.calm() && s.panicked(), s.name());
         if (s.contagious()) {
            assertTrue(s.panicked(), s.name());
         }
      }

      assertEquals(CitizenState.IDLE, CitizenState.of(99));
   }
}

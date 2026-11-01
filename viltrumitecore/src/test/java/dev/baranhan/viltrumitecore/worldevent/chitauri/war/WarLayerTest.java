package dev.baranhan.viltrumitecore.worldevent.chitauri.war;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The pure city-war layer: seeded director, sector planning and control, the captive ledger, the budget rules. */
class WarLayerTest {
   private static WarDirector.Snapshot snap(int players, int airborne, float cover, float kpm, int held, int liberated, int captives, int freed,
         int citizens, float efficiency, float spread, float centre, int budget, int commanders, int phase) {
      return new WarDirector.Snapshot(players, airborne, cover, kpm, 12, held, liberated, captives, freed, citizens, efficiency, spread, centre, budget,
         commanders, phase);
   }

   private static WarDirector.Snapshot quiet() {
      return snap(1, 0, 0.0F, 2.0F, 0, 0, 0, 0, 4, 0.5F, 0.3F, 0.3F, WarRules.BUDGET, 0, 1);
   }

   private static List<WarDirector.Doctrine> war(long seed, WarDirector.Snapshot s, int periods) {
      WarDirector.Personality p = WarDirector.personality(seed);
      SplittableRandom roll = new SplittableRandom(seed);
      List<WarDirector.Doctrine> out = new ArrayList<>();
      WarDirector.Doctrine prev = null;
      int repeats = 0;
      for (int i = 0; i < periods; i++) {
         WarDirector.Plan plan = WarDirector.decide(p, s, prev, repeats, roll);
         repeats = plan.doctrine() == prev ? repeats + 1 : 1;
         prev = plan.doctrine();
         out.add(prev);
      }
      return out;
   }

   @Test
   void sameSeedSameWar() {
      assertEquals(WarDirector.personality(42), WarDirector.personality(42));
      assertEquals(war(42, quiet(), 20), war(42, quiet(), 20));
      WarDirector.Plan a = WarDirector.decide(WarDirector.personality(7), quiet(), null, 0, new SplittableRandom(7));
      WarDirector.Plan b = WarDirector.decide(WarDirector.personality(7), quiet(), null, 0, new SplittableRandom(7));
      assertArrayEquals(a.scores(), b.scores());
      assertEquals(a.mix(), b.mix());
   }

   @Test
   void differentSeedsMakeDifferentHives() {
      assertNotEquals(WarDirector.personality(1), WarDirector.personality(2));
      // over many seeds the opening plans are not all the same doctrine
      HashSet<WarDirector.Doctrine> first = new HashSet<>();
      for (long seed = 1; seed <= 40; seed++) {
         first.add(war(seed, quiet(), 1).get(0));
      }
      assertTrue(first.size() >= 3, "opening doctrines seen: " + first);
   }

   @Test
   void noDoctrineOwnsTheWholeWar() {
      for (long seed = 1; seed <= 25; seed++) {
         List<WarDirector.Doctrine> w = war(seed, quiet(), 24);
         Map<WarDirector.Doctrine, Integer> n = new EnumMap<>(WarDirector.Doctrine.class);
         int run = 1, longest = 1;
         for (int i = 0; i < w.size(); i++) {
            n.merge(w.get(i), 1, Integer::sum);
            if (i > 0) {
               run = w.get(i) == w.get(i - 1) ? run + 1 : 1;
               longest = Math.max(longest, run);
            }
         }
         assertTrue(n.size() >= 3, "seed " + seed + " used " + n);
         assertTrue(longest <= 5, "seed " + seed + " repeated one plan " + longest + " times");
      }
   }

   @Test
   void theHiveAdaptsToHowHeroesFight() {
      WarDirector.Personality p = new WarDirector.Personality(0.5F, 0.5F, 0.5F, 0.5F);
      // fliers → interceptors in the mix whatever the doctrine
      WarDirector.Snapshot fliers = snap(2, 2, 0.0F, 3.0F, 0, 0, 0, 0, 2, 0.5F, 0.4F, 0.4F, WarRules.BUDGET, 0, 1);
      for (WarDirector.Doctrine d : WarDirector.Doctrine.values()) {
         assertTrue(WarDirector.mix(d, fliers, p).interceptors() >= 1, d + " ignores fliers");
      }
      assertEquals(0, WarDirector.mix(WarDirector.Doctrine.CENTRE_ASSAULT, quiet(), p).interceptors());
      // heroes in cover → siege platforms
      WarDirector.Snapshot cover = snap(1, 0, 0.8F, 3.0F, 0, 0, 0, 0, 2, 0.5F, 0.4F, 0.4F, WarRules.BUDGET, 0, 1);
      assertEquals(1, WarDirector.mix(WarDirector.Doctrine.OUTSKIRT_RAID, cover, p).sieges());
      // heroes kill fast → heavies
      WarDirector.Snapshot killers = snap(1, 0, 0.0F, 12.0F, 0, 0, 0, 0, 2, 0.5F, 0.4F, 0.4F, WarRules.BUDGET, 0, 1);
      assertEquals(2, WarDirector.mix(WarDirector.Doctrine.OUTSKIRT_RAID, killers, p).heavies());
      // many citizens → abduction scores up; freed captives → it scores down
      int abd = WarDirector.Doctrine.ABDUCTION.ordinal();
      float few = WarDirector.scores(p, snap(1, 0, 0, 2, 0, 0, 0, 0, 0, 0.5F, 0.3F, 0.3F, 30, 0, 1), null, 0)[abd];
      float many = WarDirector.scores(p, snap(1, 0, 0, 2, 0, 0, 0, 0, 12, 0.5F, 0.3F, 0.3F, 30, 0, 1), null, 0)[abd];
      float rescued = WarDirector.scores(p, snap(1, 0, 0, 2, 0, 0, 1, 6, 12, 0.5F, 0.3F, 0.3F, 30, 0, 1), null, 0)[abd];
      assertTrue(many > few && rescued < many);
      // a war-minded hive (low greed) still sends one snatcher until it has taken somebody, then stops
      WarDirector.Personality warlike = new WarDirector.Personality(0.9F, 0.5F, 0.2F, 0.5F);
      WarDirector.Snapshot crowd = snap(1, 0, 0, 2, 0, 0, 0, 0, 12, 0.5F, 0.3F, 0.3F, 30, 0, 1);
      assertEquals(1, WarDirector.mix(WarDirector.Doctrine.CENTRE_ASSAULT, crowd, warlike).captors());
      WarDirector.Snapshot taken = snap(1, 0, 0, 2, 0, 0, 2, 0, 12, 0.5F, 0.3F, 0.3F, 30, 0, 1);
      assertEquals(0, WarDirector.mix(WarDirector.Doctrine.CENTRE_ASSAULT, taken, warlike).captors());
      assertEquals(0, WarDirector.mix(WarDirector.Doctrine.CENTRE_ASSAULT, snap(1, 0, 0, 2, 0, 0, 0, 0, 2, 0.5F, 0.3F, 0.3F, 30, 0, 1), warlike).captors());
      // losing sectors → hold ground and surprise reinforcements score up
      float[] calm = WarDirector.scores(p, snap(1, 0, 0, 1, 2, 0, 0, 0, 2, 0.5F, 0.3F, 0.3F, 30, 0, 1), null, 0);
      float[] losing = WarDirector.scores(p, snap(1, 0, 0, 14, 2, 3, 0, 0, 2, 0.5F, 0.3F, 0.3F, 30, 0, 1), null, 0);
      assertTrue(losing[WarDirector.Doctrine.HOLD_GROUND.ordinal()] > calm[WarDirector.Doctrine.HOLD_GROUND.ordinal()]);
      assertTrue(losing[WarDirector.Doctrine.SURPRISE_REINFORCE.ordinal()] > calm[WarDirector.Doctrine.SURPRISE_REINFORCE.ordinal()]);
      // a learned penalty steers away from a doctrine that bled the hive
      float[] bias = new float[WarDirector.Doctrine.values().length];
      bias[abd] = 5.0F;
      for (int i = 0; i < 30; i++) {
         assertNotEquals(WarDirector.Doctrine.ABDUCTION, WarDirector.decide(p, snap(1, 0, 0, 2, 0, 0, 0, 0, 12, 0.5F, 0.3F, 0.3F, 30, 0, 1), null, 0,
            new SplittableRandom(i), bias).doctrine());
      }
   }

   @Test
   void reinforcementsNeverExceedTheBudget() {
      WarDirector.Personality p = WarDirector.personality(99);
      for (int budget = 0; budget <= WarRules.BUDGET; budget++) {
         for (WarDirector.Doctrine d : WarDirector.Doctrine.values()) {
            WarDirector.Snapshot s = snap(3, 3, 0.9F, 15.0F, 1, 1, 1, 1, 12, 0.1F, 0.2F, 0.6F, budget, 0, 3);
            WarDirector.Mix m = WarDirector.mix(d, s, p);
            assertTrue(m.cost() <= budget, d + " spends " + m.cost() + " of " + budget);
            assertTrue(m.interceptors() <= WarRules.MAX_INTERCEPTORS && m.commanders() <= WarRules.MAX_COMMANDERS);
         }
      }
      assertEquals(0, WarDirector.mix(WarDirector.Doctrine.SURPRISE_REINFORCE, snap(1, 1, 1, 15, 0, 3, 0, 0, 9, 0, 0, 0, 0, 0, 3),
         p).total(), "no budget, no reinforcements");
      // no budget: surprise reinforcement and commander push are worthless
      float[] u = WarDirector.scores(p, snap(1, 0, 0, 14, 0, 3, 0, 0, 2, 0.1F, 0.3F, 0.3F, 0, 0, 2), null, 0);
      assertEquals(0.0F, u[WarDirector.Doctrine.SURPRISE_REINFORCE.ordinal()]);
      assertEquals(0.0F, u[WarDirector.Doctrine.COMMANDER_PUSH.ordinal()]);
      // the captive bonus is capped: no infinite reinforcements
      assertEquals(WarRules.BUDGET, WarRules.budget(0));
      assertEquals(WarRules.BUDGET + WarRules.CAPTIVE_BONUS_CAP, WarRules.budget(1000));
      for (WarRules.UnitKind k : WarRules.UnitKind.values()) {
         assertTrue(WarRules.cost(k) > 0 && WarRules.cap(k) > 0, k.name());
      }
   }

   @Test
   void ringSectorsCoverTheCity() {
      List<WarSector> s = SectorPlanner.rings(100.0, -50.0, 120.0);
      assertTrue(s.size() > 6 && s.size() <= WarRules.MAX_SECTORS);
      for (int i = 0; i < s.size(); i++) {
         assertEquals(i, s.get(i).id);
      }
      assertEquals(0, s.get(0).ring);
      assertEquals(0, SectorPlanner.locate(s, 100.0, -50.0));
      assertEquals(-1, SectorPlanner.locate(s, 5000.0, 5000.0));
      // every point of the war area lands in some sector
      for (double a = 0; a < Math.PI * 2; a += 0.3) {
         for (double r = 0; r <= 100.0; r += 20.0) {
            assertTrue(SectorPlanner.locate(s, 100.0 + Math.cos(a) * r, -50.0 + Math.sin(a) * r) >= 0, "uncovered at r " + r);
         }
      }
   }

   @Test
   void districtsBecomeSectorsNearestFirst() {
      List<SectorPlanner.District> d = List.of(new SectorPlanner.District("docks", 60, 0, 100, 40), new SectorPlanner.District("centre", -20, -20, 20, 20),
         new SectorPlanner.District("market", -100, -10, -60, 30), new SectorPlanner.District("far", 4000, 4000, 4040, 4040));
      List<WarSector> s = SectorPlanner.districts(d, 0, 0, 150);
      assertEquals(3, s.size(), "the far district is out of the war");
      assertEquals("centre", s.get(0).name);
      // fewer than three districts nearby → ring layout
      assertNull(SectorPlanner.districts(d.subList(0, 1), 0, 0, 150).get(0).name);
   }

   @Test
   void sectorsAreSeizedOnlyThroughAStrongholdAndLiberatedByHeroes() {
      WarSector w = new WarSector(0, null, 0, 0, 20, 1);
      w.groundY = 64;
      assertEquals(WarSector.Event.SCOUTED, w.update(3, 0, 2));
      WarSector.Event e = WarSector.Event.NONE;
      for (int i = 0; i < 100 && e != WarSector.Event.CONTESTED; i++) {
         e = w.update(4, 0, 2);
      }
      assertEquals(WarSector.State.CONTESTED, w.state);
      for (int i = 0; i < 100; i++) {
         w.update(6, 0, 2);
      }
      assertEquals(WarSector.State.CONTESTED, w.state, "no stronghold, no seizure");
      w.stronghold = true;
      e = w.update(6, 0, 2);
      assertEquals(WarSector.Event.SEIZED, e);
      assertTrue(w.hiveOwned());
      // the beacon falls: the claim breaks at once, heroes then push it out
      w.strongholdLost();
      assertEquals(WarSector.State.CONTESTED, w.state);
      for (int i = 0; i < 100 && w.state != WarSector.State.LIBERATED; i++) {
         w.update(0, 1, 2);
      }
      assertEquals(WarSector.State.LIBERATED, w.state);
      assertEquals(1, w.seizedCount);
      assertEquals(1, w.liberatedCount);
      // one hero outweighs two soldiers
      WarSector x = new WarSector(1, null, 0, 0, 20, 1);
      x.control = 0.5F;
      x.update(2, 1, 0);
      assertTrue(x.control < 0.5F);
   }

   @Test
   void theLedgerNeverLosesAPerson() {
      CaptiveLedger l = new CaptiveLedger();
      UUID captor = UUID.randomUUID();
      UUID beacon = UUID.randomUUID();
      List<UUID> people = new ArrayList<>();
      for (int i = 0; i < WarRules.MAX_CAPTIVES + 3; i++) {
         UUID id = UUID.randomUUID();
         boolean took = l.carry(id, "minecraft:villager", captor, i, 64, 0, 10);
         assertEquals(i < WarRules.MAX_CAPTIVES, took, "captive limit");
         if (took) {
            people.add(id);
         }
      }
      assertFalse(l.canTake());
      assertFalse(l.carry(people.get(0), "minecraft:villager", captor, 0, 64, 0, 11), "already taken");
      int delivered = 0;
      for (UUID id : people) {
         if (l.deliver(id, beacon, 20)) {
            delivered++;
         }
      }
      assertEquals(WarRules.CONTAINMENT_CAPACITY, delivered, "field capacity");
      assertFalse(l.hasRoom(beacon));
      assertEquals(delivered, l.heldBy(beacon));
      CaptiveLedger.Entry freed = l.release(people.get(0), true);
      assertNotNull(freed);
      assertEquals(1, l.freed);
      assertTrue(l.hasRoom(beacon));
      // a body that vanished with its chunk keeps its record through releaseAll (restored later)
      l.lose(people.get(1));
      List<CaptiveLedger.Entry> all = l.releaseAll();
      assertEquals(people.size() - 2, all.size());
      assertEquals(1, l.entries().size());
      assertEquals(CaptiveLedger.State.LOST, l.get(people.get(1)).state);
      assertEquals(0, l.active());
      l.forget(people.get(1));
      assertTrue(l.isEmpty());
   }

   @Test
   void targetsAreKnownSectorsAndFrontsSpreadOut() {
      List<WarSector> s = SectorPlanner.rings(0, 0, 150);
      for (WarSector w : s) {
         w.groundY = w.id % 5 == 4 ? WarSector.UNKNOWN_Y : 64;
         w.citizens = w.id % 3;
      }
      for (WarDirector.Doctrine d : WarDirector.Doctrine.values()) {
         int fronts = WarDirector.fronts(d, quiet());
         List<Integer> t = WarDirector.targets(s, d, fronts, new SplittableRandom(3));
         assertFalse(t.isEmpty(), d.name());
         assertEquals(t.size(), new HashSet<>(t).size(), "no duplicates");
         for (int id : t) {
            assertTrue(s.get(id).known(), d + " targets an unloaded sector");
         }
      }
      List<Integer> multi = WarDirector.targets(s, WarDirector.Doctrine.MULTI_FRONT, 3, new SplittableRandom(5));
      assertTrue(multi.size() >= 2);
      for (WarSector w : s) {
         w.groundY = WarSector.UNKNOWN_Y;
      }
      assertTrue(WarDirector.targets(s, WarDirector.Doctrine.CENTRE_ASSAULT, 1, new SplittableRandom(1)).isEmpty());
   }
}

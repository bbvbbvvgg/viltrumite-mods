package dev.baranhan.viltrumitecore.worldevent.thanos.war.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Ebony Maw and Cull Obsidian choose like Thanos: hard rules first (cooldowns, never the same move
 * twice, a gap between moves, no attacks through walls, holds only where the ledger allows), then
 * each general's own character.
 */
class OrderBrainTest {
   /** A mutable situation for the tests. */
   static final class S {
      boolean target = true;
      double distance = 10.0;
      boolean air;
      boolean visible = true;
      boolean ranged;
      boolean hulkbuster;
      int close = 1;
      int projectiles;
      boolean thanosThreatened;
      boolean controllable = true;
      boolean pinned;
      float health = 1.0F;
      boolean structures = true;

      OrderBrain.Situation get() {
         return new OrderBrain.Situation(this.target, this.distance, this.air, this.visible, this.ranged, this.hulkbuster, this.close, this.projectiles,
            this.thanosThreatened, this.controllable, this.pinned, this.health, this.structures);
      }
   }

   static S at(double distance) {
      S s = new S();
      s.distance = distance;
      return s;
   }

   private static <M extends Enum<M> & OrderMove> Map<M, Integer> spread(Class<M> type, OrderBrain.Situation s, int seeds) {
      Map<M, Integer> counts = new EnumMap<>(type);
      for (int seed = 0; seed < seeds; seed++) {
         M move = new OrderBrain<>(type, seed).choose(s);
         if (move != null) {
            counts.merge(move, 1, Integer::sum);
         }
      }

      return counts;
   }

   @Test
   void noTargetOrTheGapMeansMoveIntoPosition() {
      OrderBrain<MawMove> brain = new OrderBrain<>(MawMove.class, 1);
      S none = at(10.0);
      none.target = false;
      assertNull(brain.choose(none.get()));
      MawMove first = brain.choose(at(10.0).get());
      assertNotNull(first);
      brain.commit(first);
      for (int i = 0; i < 6; i++) {
         assertNull(brain.choose(at(10.0).get()), "the gap after a move");
         brain.tick();
      }

      assertNotNull(brain.choose(at(10.0).get()));
      assertEquals(first, brain.last());
      assertEquals(first.cooldown() + first.length(), brain.cooldown(first) + 6, "the cooldown counts from the commit");
   }

   @Test
   void theGatesHoldForEveryMove() {
      for (double distance : new double[]{1.0, 3.0, 6.0, 12.0, 25.0, 40.0}) {
         for (boolean air : new boolean[]{false, true}) {
            for (boolean visible : new boolean[]{false, true}) {
               for (boolean controllable : new boolean[]{false, true}) {
                  S s = at(distance);
                  s.air = air;
                  s.visible = visible;
                  s.controllable = controllable;
                  s.thanosThreatened = true;
                  s.hulkbuster = distance < 5.0;
                  s.health = 0.4F;
                  gates(new OrderBrain<>(MawMove.class, 3), MawMove.values(), s.get());
                  gates(new OrderBrain<>(CullMove.class, 3), CullMove.values(), s.get());
               }
            }
         }
      }

      S pinned = at(3.0);
      pinned.pinned = true;
      assertEquals(0.0, new OrderBrain<>(CullMove.class, 1).weight(CullMove.GRAB, pinned.get()), "an ally already holds him");
      assertEquals(0.0, new OrderBrain<>(MawMove.class, 1).weight(MawMove.GRAB, pinned.get()));
   }

   private static <M extends Enum<M> & OrderMove> void gates(OrderBrain<M> brain, M[] moves, OrderBrain.Situation s) {
      for (M m : moves) {
         if (brain.weight(m, s) > 0.0) {
            assertTrue(s.visible() || m.throughCover(), m + " through a wall");
            assertTrue(!s.airborne() || m.air(), m + " against a flyer");
            assertTrue(s.distance() >= m.minRange() && s.distance() <= m.maxRange(), m + " out of range");
            assertTrue(!m.control() || s.controllable() && !s.pinned(), m + " holds a protected hero");
         }
      }
   }

   @Test
   void theHardRulesHoldOverALongFight() {
      SplittableRandom r = new SplittableRandom(7);
      fight(new OrderBrain<>(MawMove.class, 11), r);
      fight(new OrderBrain<>(CullMove.class, 12), r);
   }

   private static <M extends Enum<M> & OrderMove> void fight(OrderBrain<M> brain, SplittableRandom r) {
      List<M> moves = new ArrayList<>();
      List<Integer> at = new ArrayList<>();
      int tick = 0;
      for (int step = 0; step < 3000; step++) {
         S s = at(1.0 + r.nextDouble() * 35.0);
         s.air = r.nextInt(5) == 0;
         s.visible = r.nextInt(6) != 0;
         s.ranged = r.nextInt(4) == 0;
         s.hulkbuster = r.nextInt(8) == 0;
         s.close = r.nextInt(4);
         s.projectiles = r.nextInt(3);
         s.thanosThreatened = r.nextInt(5) == 0;
         s.controllable = r.nextInt(3) != 0;
         s.health = 0.1F + r.nextFloat() * 0.9F;
         s.structures = r.nextBoolean();
         M move = brain.choose(s.get());
         if (move != null) {
            if (!moves.isEmpty()) {
               assertNotEquals(moves.get(moves.size() - 1), move, "the same move twice");
               assertTrue(tick - at.get(at.size() - 1) >= 6, "the gap between moves");
            }

            for (int i = moves.size() - 1; i >= 0; i--) {
               if (moves.get(i) == move) {
                  assertTrue(tick - at.get(i) >= move.cooldown() + move.length(), move + " before its cooldown");
                  break;
               }
            }

            moves.add(move);
            at.add(tick);
            brain.commit(move);
         }

         brain.tick();
         tick++;
      }

      assertTrue(moves.size() > 40, "it fights: " + moves.size());
      assertTrue(moves.stream().distinct().count() >= 5, "it varies its moves: " + moves.stream().distinct().toList());
   }

   @Test
   void deterministicForASeed() {
      OrderBrain<CullMove> a = new OrderBrain<>(CullMove.class, 99);
      OrderBrain<CullMove> b = new OrderBrain<>(CullMove.class, 99);
      for (int i = 0; i < 400; i++) {
         S s = at(1.0 + (i * 7 % 30));
         CullMove x = a.choose(s.get());
         assertEquals(x, b.choose(s.get()));
         if (x != null) {
            a.commit(x);
            b.commit(x);
         }

         a.tick();
         b.tick();
      }
   }

   // ---- Ebony Maw ------------------------------------------------------------------------------

   @Test
   void mawShieldsThanosUnderFire() {
      S s = at(15.0);
      s.thanosThreatened = true;
      Map<MawMove, Integer> spread = spread(MawMove.class, s.get(), 200);
      assertTrue(spread.getOrDefault(MawMove.BARRIER, 0) > 90, "the barrier " + spread);
      S calm = at(15.0);
      assertEquals(0.0, new OrderBrain<>(MawMove.class, 1).weight(MawMove.BARRIER, calm.get()), "no fire, no barrier");
   }

   @Test
   void mawKeepsHisDistance() {
      Map<MawMove, Integer> spread = spread(MawMove.class, at(3.0).get(), 200);
      assertTrue(spread.getOrDefault(MawMove.BLINK, 0) > 100, "drifts away " + spread);
      assertEquals(0.0, new OrderBrain<>(MawMove.class, 1).weight(MawMove.BLINK, at(12.0).get()), "no one close, no blink");
   }

   @Test
   void mawGrabsFlyersAndTearsTheCityApart() {
      S flyer = at(12.0);
      flyer.air = true;
      OrderBrain<MawMove> brain = new OrderBrain<>(MawMove.class, 1);
      assertTrue(brain.weight(MawMove.GRAB, flyer.get()) > brain.weight(MawMove.GRAB, at(12.0).get()), "the grab is his answer to a flyer");
      assertEquals(0.0, brain.weight(MawMove.TOPPLE, flyer.get()), "no building falls on a flyer");
      S bare = at(12.0);
      bare.structures = false;
      assertEquals(0.0, brain.weight(MawMove.TOPPLE, bare.get()), "nothing to topple in the open");
      assertTrue(brain.weight(MawMove.VOLLEY, at(12.0).get()) > brain.weight(MawMove.VOLLEY, bare.get()), "buildings feed the volley");
   }

   // ---- Cull Obsidian --------------------------------------------------------------------------

   @Test
   void cullChargesThroughWallsAtHiddenTargets() {
      S hidden = at(15.0);
      hidden.visible = false;
      Map<CullMove, Integer> spread = spread(CullMove.class, hidden.get(), 100);
      assertEquals(100, spread.getOrDefault(CullMove.CHARGE, 0), "only the charge goes through a wall " + spread);
   }

   @Test
   void cullChainsFlyersDown() {
      S flyer = at(12.0);
      flyer.air = true;
      Map<CullMove, Integer> spread = spread(CullMove.class, flyer.get(), 100);
      assertEquals(100, spread.getOrDefault(CullMove.CHAIN, 0), "the chain " + spread);
   }

   @Test
   void cullMeetsAHulkbusterHeadOn() {
      S hulk = at(4.0);
      hulk.hulkbuster = true;
      Map<CullMove, Integer> spread = spread(CullMove.class, hulk.get(), 200);
      int answer = spread.getOrDefault(CullMove.CLASH, 0) + spread.getOrDefault(CullMove.ROAR, 0);
      assertTrue(answer > 120, "clash or roar " + spread);
      assertTrue(spread.getOrDefault(CullMove.GRAB, 0) < 20, "no grab of a suit his size " + spread);
      OrderBrain<CullMove> brain = new OrderBrain<>(CullMove.class, 1);
      assertEquals(0.0, brain.weight(CullMove.CLASH, at(4.0).get()), "the clash is only for the Hulkbuster");
   }

   @Test
   void cullSlamsCrowds() {
      S crowd = at(4.0);
      crowd.close = 3;
      OrderBrain<CullMove> brain = new OrderBrain<>(CullMove.class, 1);
      assertTrue(brain.weight(CullMove.SLAM, crowd.get()) > 2.0 * brain.weight(CullMove.SLAM, at(4.0).get()));
   }

   // ---- data -----------------------------------------------------------------------------------

   @Test
   void everyMoveIsReadableAndPunishable() {
      List<OrderMove> all = new ArrayList<>(List.of(MawMove.values()));
      all.addAll(List.of(CullMove.values()));
      for (OrderMove m : all) {
         assertTrue(m.telegraph() >= 10, m + ": a wind-up of at least half a second");
         assertTrue(m.recovery() >= 6, m + ": an opening after it");
         assertTrue(m.active() > 0 && m.cooldown() > 0, m.name());
         assertTrue(m.minRange() < m.maxRange(), m.name());
         assertTrue(m.damage() >= 0.0F && m.damage() <= 30.0F, m + " damage");
      }

      for (MawMove m : MawMove.values()) {
         assertEquals(m, MawMove.byId(m.ordinal()));
      }

      for (CullMove m : CullMove.values()) {
         assertEquals(m, CullMove.byId(m.ordinal()));
      }

      assertNull(MawMove.byId(-1));
      assertNull(CullMove.byId(CullMove.values().length));
   }
}

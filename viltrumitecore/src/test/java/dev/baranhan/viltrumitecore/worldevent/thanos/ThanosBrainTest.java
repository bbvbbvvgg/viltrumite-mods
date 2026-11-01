package dev.baranhan.viltrumitecore.worldevent.thanos;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ThanosBrainTest {
   static ThanosBrain.Situation at(ThanosPhase phase, double distance) {
      return new ThanosBrain.Situation(phase, true, distance, false, true, 1, 1, 0, false, false, 1.0F, 0.0F);
   }

   /** The same, with the target behind a wall. */
   static ThanosBrain.Situation hidden(ThanosBrain.Situation s) {
      return new ThanosBrain.Situation(s.phase(), true, s.distance(), s.targetAirborne(), false, s.heroes(), s.heroesClose(), s.incomingProjectiles(),
         s.recentlyTimeStopped(), s.threatened(), s.healthFraction(), s.recentDamageFraction());
   }

   static ThanosBrain.Situation with(ThanosBrain.Situation s, boolean air, int close, int projectiles, boolean timeStopped, boolean threat,
      float health, float recent) {
      return new ThanosBrain.Situation(s.phase(), true, s.distance(), air, true, Math.max(1, close), close, projectiles, timeStopped, threat, health,
         recent);
   }

   @Test
   void noTargetMeansStalk() {
      ThanosBrain brain = new ThanosBrain(1);
      assertNull(brain.choose(new ThanosBrain.Situation(ThanosPhase.GAUNTLET, false, 3, false, true, 1, 1, 0, false, false, 1, 0)));
   }

   @Test
   void closeRangePrefersMelee() {
      Map<ThanosMove.Kind, Integer> kinds = new EnumMap<>(ThanosMove.Kind.class);
      for (int seed = 0; seed < 400; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(at(ThanosPhase.ARRIVAL, 3.0));
         assertNotNull(move);
         kinds.merge(move.kind, 1, Integer::sum);
      }

      assertTrue(kinds.getOrDefault(ThanosMove.Kind.MELEE, 0) > 190, "melee dominates at 3 blocks: " + kinds);
   }

   @Test
   void farTargetsNeverGetMelee() {
      for (int seed = 0; seed < 300; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(at(ThanosPhase.INVASION, 35.0));
         if (move != null) {
            assertNotEquals(ThanosMove.Kind.MELEE, move.kind, "seed " + seed + " -> " + move);
            assertTrue(move.inRange(35.0) || move.kind == ThanosMove.Kind.DEFENSE, move.toString());
         }
      }
   }

   @Test
   void openingHasNoCombos() {
      ThanosBrain brain = new ThanosBrain(7);
      for (ThanosMove move : ThanosMove.values()) {
         if (move.combo()) {
            assertEquals(0.0, brain.weight(move, at(ThanosPhase.ARRIVAL, 8.0)), move.toString());
         }
      }
   }

   @Test
   void lateFightUsesTwoAndThreeStoneCombos() {
      Set<ThanosMove> seen = EnumSet.noneOf(ThanosMove.class);
      for (int seed = 0; seed < 2000; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(with(at(ThanosPhase.GAUNTLET, 9.0), false, 3, 0, false, false, 0.3F, 0.0F));
         if (move != null && move.combo()) {
            seen.add(move);
         }
      }

      assertTrue(seen.stream().anyMatch(m -> ThanosStone.count(m.stones) == 2), "two-stone combos " + seen);
      assertTrue(seen.stream().anyMatch(m -> ThanosStone.count(m.stones) == 3), "three-stone combos " + seen);
   }

   @Test
   void neverTheSameMoveTwiceInARow() {
      ThanosBrain brain = new ThanosBrain(42);
      ThanosMove last = null;
      for (int i = 0; i < 300; i++) {
         for (int t = 0; t < 2000 && brain.choose(at(ThanosPhase.GAUNTLET, 4.0)) == null; t++) {
            brain.tick();
         }

         ThanosMove move = brain.choose(at(ThanosPhase.GAUNTLET, 4.0));
         assertNotNull(move);
         assertNotEquals(last, move, "repeat at " + i);
         brain.commit(move, ThanosPhase.GAUNTLET);
         last = move;
         for (int t = 0; t < move.length(ThanosPhase.GAUNTLET) + ThanosRules.BREATHER_TICKS; t++) {
            brain.tick();
         }
      }
   }

   @Test
   void cooldownsAndTheStoneGapGate() {
      ThanosBrain brain = new ThanosBrain(3);
      brain.commit(ThanosMove.POWER_PUNCH, ThanosPhase.ARRIVAL);
      assertFalse(brain.ready(ThanosMove.POWER_PUNCH));
      assertFalse(brain.ready(ThanosMove.MIND_GAZE), "stone gap after a stone move");
      assertTrue(brain.ready(ThanosMove.CLEAVE), "melee fills the gap");
      int gap = ThanosMove.POWER_PUNCH.length(ThanosPhase.ARRIVAL) + ThanosRules.STONE_GAP_TICKS;
      for (int i = 0; i < gap; i++) {
         brain.tick();
      }

      assertTrue(brain.ready(ThanosMove.MIND_GAZE));
      assertFalse(brain.ready(ThanosMove.POWER_PUNCH), "own cooldown is longer than the gap");
   }

   @Test
   void projectilesRaiseTheSpaceWard() {
      ThanosBrain calm = new ThanosBrain(1);
      assertEquals(0.0, calm.weight(ThanosMove.SPACE_WARD, at(ThanosPhase.ARRIVAL, 20)));
      int ward = 0;
      for (int seed = 0; seed < 200; seed++) {
         if (new ThanosBrain(seed).choose(with(at(ThanosPhase.ARRIVAL, 20), false, 1, 4, false, false, 1, 0)) == ThanosMove.SPACE_WARD) {
            ward++;
         }
      }

      assertTrue(ward > 60, "ward chosen " + ward + "/200");
   }

   @Test
   void aTimeStopIsAnsweredWithTheTimeStone() {
      ThanosBrain.Situation hit = with(at(ThanosPhase.BLACK_ORDER, 6.0), false, 1, 0, true, false, 0.5F, 0.08F);
      int rewind = 0;
      for (int seed = 0; seed < 300; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(hit);
         if (move == ThanosMove.TIME_REWIND || move == ThanosMove.SPACE_STEP || move == ThanosMove.TIME_DOME) {
            rewind++;
         }
      }

      assertTrue(rewind > 120, "time answers " + rewind + "/300");
   }

   @Test
   void rewindNeedsRealDamage() {
      ThanosBrain brain = new ThanosBrain(5);
      assertEquals(0.0, brain.weight(ThanosMove.TIME_REWIND, with(at(ThanosPhase.INVASION, 5), false, 1, 0, true, true, 0.2F, 0.0F)));
      assertTrue(brain.weight(ThanosMove.TIME_REWIND, with(at(ThanosPhase.INVASION, 5), false, 1, 0, false, false, 0.2F, 0.1F)) > 0.0);
   }

   @Test
   void strongHitsGetDefensiveAnswers() {
      ThanosBrain brain = new ThanosBrain(9);
      ThanosBrain.Situation calm = at(ThanosPhase.BLACK_ORDER, 6);
      ThanosBrain.Situation threat = with(calm, false, 1, 0, false, true, 1, 0);
      assertTrue(brain.weight(ThanosMove.REALITY_MIRAGE, threat) > 4 * brain.weight(ThanosMove.REALITY_MIRAGE, calm));
   }

   @Test
   void flyingTargetsGetMovesThatReachTheAir() {
      int air = 0;
      for (int seed = 0; seed < 300; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(with(at(ThanosPhase.BLACK_ORDER, 12), true, 1, 0, false, false, 1, 0));
         if (move != null && move.air) {
            air++;
         }
      }

      assertTrue(air > 240, "air moves " + air + "/300");
   }

   @Test
   void crowdsGetAreaMoves() {
      ThanosBrain brain = new ThanosBrain(2);
      ThanosBrain.Situation solo = at(ThanosPhase.BLACK_ORDER, 5);
      ThanosBrain.Situation crowd = with(solo, false, 4, 0, false, false, 1, 0);
      assertEquals(2.0 * brain.weight(ThanosMove.SOUL_NOVA, solo), brain.weight(ThanosMove.SOUL_NOVA, crowd), 1.0E-9);
   }

   @Test
   void followUpsChainEffectively() {
      ThanosBrain brain = new ThanosBrain(4);
      double before = brain.weight(ThanosMove.POWER_PUNCH, at(ThanosPhase.ARRIVAL, 3));
      brain.commit(ThanosMove.SPACE_PULL, ThanosPhase.ARRIVAL);
      for (int i = 0; i < ThanosMove.SPACE_PULL.length(ThanosPhase.ARRIVAL) + ThanosRules.STONE_GAP_TICKS; i++) {
         brain.tick();
      }

      assertEquals(3.0 * before, brain.weight(ThanosMove.POWER_PUNCH, at(ThanosPhase.ARRIVAL, 3)), 1.0E-9);
   }

   private static void idle(ThanosBrain brain, int ticks) {
      for (int i = 0; i < ticks; i++) {
         brain.tick();
      }
   }

   @Test
   void oneStoneIsNeverUsedThreeTimesInARow() {
      ThanosBrain brain = new ThanosBrain(4);
      brain.commit(ThanosMove.POWER_WAVE, ThanosPhase.GAUNTLET);
      brain.commit(ThanosMove.POWER_PUNCH, ThanosPhase.GAUNTLET);
      idle(brain, 2000);
      ThanosBrain.Situation s = at(ThanosPhase.GAUNTLET, 8);
      // every move with the Power stone is out, combos included; the others are not
      for (ThanosMove move : ThanosMove.values()) {
         if (move.uses(ThanosStone.POWER)) {
            assertEquals(0.0, brain.weight(move, s), move.toString());
            assertFalse(brain.allowed(move, ThanosPhase.GAUNTLET), move.toString());
         }
      }

      assertTrue(brain.weight(ThanosMove.COMBO_ETERNITY, s) > 0.0);
      // a plain strike in between breaks the streak
      brain.commit(ThanosMove.CLEAVE, ThanosPhase.GAUNTLET);
      idle(brain, 2000);
      assertTrue(brain.weight(ThanosMove.COMBO_TRINITY, s) > 0.0);
   }

   @Test
   void teleportsKeepTheirGap() {
      ThanosBrain brain = new ThanosBrain(12);
      ThanosBrain.Situation far = at(ThanosPhase.INVASION, 20);
      brain.commit(ThanosMove.SPACE_STEP, ThanosPhase.INVASION);
      brain.commit(ThanosMove.CLEAVE, ThanosPhase.INVASION);
      idle(brain, ThanosRules.TELEPORT_GAP_TICKS - 1);
      assertEquals(0.0, brain.weight(ThanosMove.COMBO_MIND_STEP, far), "a second teleport inside the gap");
      brain.tick();
      assertTrue(brain.weight(ThanosMove.COMBO_MIND_STEP, far) > 0.0, "after the gap");
      assertTrue(ThanosRules.teleportGap(ThanosPhase.GAUNTLET) < ThanosRules.teleportGap(ThanosPhase.INVASION));
      assertTrue(ThanosMove.SPACE_STEP.teleports() && ThanosMove.COMBO_MIND_STEP.teleports() && !ThanosMove.SPACE_PULL.teleports());
   }

   @Test
   void noAttacksThroughWalls() {
      for (ThanosPhase phase : ThanosPhase.values()) {
         for (double distance : new double[]{3, 8, 15, 30}) {
            ThanosBrain brain = new ThanosBrain(3);
            ThanosBrain.Situation s = hidden(with(at(phase, distance), false, 2, 2, false, true, 0.5F, 0.05F));
            for (ThanosMove move : ThanosMove.values()) {
               if (brain.weight(move, s) > 0.0) {
                  assertTrue(move.throughCover(), move + " chosen against a hidden target at " + distance + " in " + phase);
               }
            }
         }
      }

      // the step to a hidden target is much likelier than to a visible one
      ThanosBrain brain = new ThanosBrain(3);
      ThanosBrain.Situation open = at(ThanosPhase.BLACK_ORDER, 20);
      assertTrue(brain.weight(ThanosMove.SPACE_STEP, hidden(open)) > 3.5 * brain.weight(ThanosMove.SPACE_STEP, open));
      int step = 0;
      for (int seed = 0; seed < 200; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(hidden(open));
         assertTrue(move == null || move.throughCover(), String.valueOf(move));
         if (move == ThanosMove.SPACE_STEP) {
            step++;
         }
      }

      assertTrue(step > 110, "steps to the hidden target " + step + "/200");
   }

   @Test
   void afterATimeStopRewindOrStepLead() {
      // released from a time stop at 10 blocks with a little damage taken meanwhile
      ThanosBrain.Situation released = with(at(ThanosPhase.BLACK_ORDER, 10.0), false, 1, 0, true, false, 0.8F, 0.015F);
      int answers = 0;
      for (int seed = 0; seed < 300; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(released);
         if (move == ThanosMove.TIME_REWIND || move == ThanosMove.SPACE_STEP) {
            answers++;
         }
      }

      assertTrue(answers > 180, "rewind or step " + answers + "/300");
      // the same little damage without a time stop is not worth a rewind
      ThanosBrain brain = new ThanosBrain(1);
      assertEquals(0.0, brain.weight(ThanosMove.TIME_REWIND, with(at(ThanosPhase.BLACK_ORDER, 10.0), false, 1, 0, false, false, 0.8F, 0.015F)));
   }

   /** Thousands of choices over random situations: the hard rules hold every time. */
   @Test
   void hardRulesHoldOverALongFight() {
      java.util.SplittableRandom r = new java.util.SplittableRandom(99);
      for (int fight = 0; fight < 20; fight++) {
         ThanosBrain brain = new ThanosBrain(fight);
         java.util.List<ThanosMove> moves = new java.util.ArrayList<>();
         java.util.List<Integer> at = new java.util.ArrayList<>();
         int tick = 0;
         for (int step = 0; step < 400; step++) {
            ThanosPhase phase = ThanosPhase.byIndex(step * 4 / 400);
            ThanosBrain.Situation s = new ThanosBrain.Situation(phase, true, 2 + r.nextDouble() * 40, r.nextInt(5) == 0, r.nextInt(6) != 0,
               1 + r.nextInt(5), r.nextInt(4), r.nextInt(4), r.nextInt(8) == 0, r.nextInt(6) == 0, 0.2F + r.nextFloat() * 0.8F, r.nextFloat() * 0.1F);
            ThanosMove move = brain.choose(s);
            if (move != null) {
               if (!s.targetVisible()) {
                  assertTrue(move.throughCover(), move + " through a wall");
               }

               if (!moves.isEmpty()) {
                  ThanosMove last = moves.get(moves.size() - 1);
                  assertNotEquals(last, move, "the same move twice");
                  if (moves.size() >= 2) {
                     assertEquals(0, move.stones & last.stones & moves.get(moves.size() - 2).stones, "one stone three times: " + moves.subList(moves.size() - 2,
                        moves.size()) + " + " + move);
                  }
               }

               if (move.teleports()) {
                  for (int i = moves.size() - 1; i >= 0; i--) {
                     if (moves.get(i).teleports()) {
                        assertTrue(tick - at.get(i) >= ThanosRules.teleportGap(phase), "teleport spam " + moves.get(i) + " -> " + move);
                        break;
                     }
                  }
               }

               brain.commit(move, phase);
               moves.add(move);
               at.add(tick);
               int busy = move.length(phase) + ThanosRules.BREATHER_TICKS;
               idle(brain, busy);
               tick += busy;
            } else {
               idle(brain, 10);
               tick += 10;
            }
         }

         assertTrue(moves.size() > 120, "he keeps fighting: " + moves.size());
         assertTrue(new java.util.HashSet<>(moves).size() >= 12, "variety " + new java.util.HashSet<>(moves));
      }
   }

   @Test
   void deterministicForASeed() {
      ThanosBrain a = new ThanosBrain(77);
      ThanosBrain b = new ThanosBrain(77);
      for (int i = 0; i < 50; i++) {
         ThanosBrain.Situation s = at(ThanosPhase.byIndex(i % 4), 2 + i % 30);
         ThanosMove ma = a.choose(s);
         assertEquals(ma, b.choose(s));
         if (ma != null) {
            a.commit(ma, s.phase());
            b.commit(ma, s.phase());
         }

         for (int t = 0; t < 40; t++) {
            a.tick();
            b.tick();
         }
      }
   }

   @ParameterizedTest
   @EnumSource(ThanosStone.class)
   void everyStoneHasItsOwnMoves(ThanosStone stone) {
      long own = java.util.Arrays.stream(ThanosMove.values()).filter(m -> m.stones == stone.bit()).count();
      assertTrue(own >= 2 || stone == ThanosStone.SPACE && own >= 3, stone + " has " + own + " single-stone moves");
      assertTrue(java.util.Arrays.stream(ThanosMove.values()).anyMatch(m -> m.combo() && m.uses(stone)), stone + " is in a combo");
   }

   @Test
   void moveIdsRoundTrip() {
      for (ThanosMove move : ThanosMove.values()) {
         assertEquals(move, ThanosMove.byId(move.ordinal()));
         assertNotNull(move.lead() == null ? (Object)"melee" : move.lead());
         assertEquals(move.usesStones(), move.lead() != null);
      }

      assertNull(ThanosMove.byId(-1));
      assertNull(ThanosMove.byId(999));
   }
}

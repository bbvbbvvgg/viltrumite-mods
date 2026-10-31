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
      return new ThanosBrain.Situation(phase, true, distance, false, 1, 1, 0, false, false, 1.0F, 0.0F);
   }

   static ThanosBrain.Situation with(ThanosBrain.Situation s, boolean air, int close, int projectiles, boolean timeStopped, boolean threat,
      float health, float recent) {
      return new ThanosBrain.Situation(s.phase(), true, s.distance(), air, Math.max(1, close), close, projectiles, timeStopped, threat, health, recent);
   }

   @Test
   void noTargetMeansStalk() {
      ThanosBrain brain = new ThanosBrain(1);
      assertNull(brain.choose(new ThanosBrain.Situation(ThanosPhase.FINAL, false, 3, false, 1, 1, 0, false, false, 1, 0)));
   }

   @Test
   void closeRangePrefersMelee() {
      Map<ThanosMove.Kind, Integer> kinds = new EnumMap<>(ThanosMove.Kind.class);
      for (int seed = 0; seed < 400; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(at(ThanosPhase.OPENING, 3.0));
         assertNotNull(move);
         kinds.merge(move.kind, 1, Integer::sum);
      }

      assertTrue(kinds.getOrDefault(ThanosMove.Kind.MELEE, 0) > 190, "melee dominates at 3 blocks: " + kinds);
   }

   @Test
   void farTargetsNeverGetMelee() {
      for (int seed = 0; seed < 300; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(at(ThanosPhase.ONSLAUGHT, 35.0));
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
            assertEquals(0.0, brain.weight(move, at(ThanosPhase.OPENING, 8.0)), move.toString());
         }
      }
   }

   @Test
   void lateFightUsesTwoAndThreeStoneCombos() {
      Set<ThanosMove> seen = EnumSet.noneOf(ThanosMove.class);
      for (int seed = 0; seed < 2000; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(with(at(ThanosPhase.FINAL, 9.0), false, 3, 0, false, false, 0.3F, 0.0F));
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
         for (int t = 0; t < 2000 && brain.choose(at(ThanosPhase.FINAL, 4.0)) == null; t++) {
            brain.tick();
         }

         ThanosMove move = brain.choose(at(ThanosPhase.FINAL, 4.0));
         assertNotNull(move);
         assertNotEquals(last, move, "repeat at " + i);
         brain.commit(move, ThanosPhase.FINAL);
         last = move;
         for (int t = 0; t < move.length(ThanosPhase.FINAL) + ThanosRules.BREATHER_TICKS; t++) {
            brain.tick();
         }
      }
   }

   @Test
   void cooldownsAndTheStoneGapGate() {
      ThanosBrain brain = new ThanosBrain(3);
      brain.commit(ThanosMove.POWER_PUNCH, ThanosPhase.OPENING);
      assertFalse(brain.ready(ThanosMove.POWER_PUNCH));
      assertFalse(brain.ready(ThanosMove.MIND_GAZE), "stone gap after a stone move");
      assertTrue(brain.ready(ThanosMove.CLEAVE), "melee fills the gap");
      int gap = ThanosMove.POWER_PUNCH.length(ThanosPhase.OPENING) + ThanosRules.STONE_GAP_TICKS;
      for (int i = 0; i < gap; i++) {
         brain.tick();
      }

      assertTrue(brain.ready(ThanosMove.MIND_GAZE));
      assertFalse(brain.ready(ThanosMove.POWER_PUNCH), "own cooldown is longer than the gap");
   }

   @Test
   void projectilesRaiseTheSpaceWard() {
      ThanosBrain calm = new ThanosBrain(1);
      assertEquals(0.0, calm.weight(ThanosMove.SPACE_WARD, at(ThanosPhase.OPENING, 20)));
      int ward = 0;
      for (int seed = 0; seed < 200; seed++) {
         if (new ThanosBrain(seed).choose(with(at(ThanosPhase.OPENING, 20), false, 1, 4, false, false, 1, 0)) == ThanosMove.SPACE_WARD) {
            ward++;
         }
      }

      assertTrue(ward > 60, "ward chosen " + ward + "/200");
   }

   @Test
   void aTimeStopIsAnsweredWithTheTimeStone() {
      ThanosBrain.Situation hit = with(at(ThanosPhase.ESCALATION, 6.0), false, 1, 0, true, false, 0.5F, 0.08F);
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
      assertEquals(0.0, brain.weight(ThanosMove.TIME_REWIND, with(at(ThanosPhase.ONSLAUGHT, 5), false, 1, 0, true, true, 0.2F, 0.0F)));
      assertTrue(brain.weight(ThanosMove.TIME_REWIND, with(at(ThanosPhase.ONSLAUGHT, 5), false, 1, 0, false, false, 0.2F, 0.1F)) > 0.0);
   }

   @Test
   void strongHitsGetDefensiveAnswers() {
      ThanosBrain brain = new ThanosBrain(9);
      ThanosBrain.Situation calm = at(ThanosPhase.ESCALATION, 6);
      ThanosBrain.Situation threat = with(calm, false, 1, 0, false, true, 1, 0);
      assertTrue(brain.weight(ThanosMove.REALITY_MIRAGE, threat) > 4 * brain.weight(ThanosMove.REALITY_MIRAGE, calm));
   }

   @Test
   void flyingTargetsGetMovesThatReachTheAir() {
      int air = 0;
      for (int seed = 0; seed < 300; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(with(at(ThanosPhase.ESCALATION, 12), true, 1, 0, false, false, 1, 0));
         if (move != null && move.air) {
            air++;
         }
      }

      assertTrue(air > 240, "air moves " + air + "/300");
   }

   @Test
   void crowdsGetAreaMoves() {
      ThanosBrain brain = new ThanosBrain(2);
      ThanosBrain.Situation solo = at(ThanosPhase.ESCALATION, 5);
      ThanosBrain.Situation crowd = with(solo, false, 4, 0, false, false, 1, 0);
      assertEquals(2.0 * brain.weight(ThanosMove.SOUL_NOVA, solo), brain.weight(ThanosMove.SOUL_NOVA, crowd), 1.0E-9);
   }

   @Test
   void followUpsChainEffectively() {
      ThanosBrain brain = new ThanosBrain(4);
      double before = brain.weight(ThanosMove.POWER_PUNCH, at(ThanosPhase.OPENING, 3));
      brain.commit(ThanosMove.SPACE_PULL, ThanosPhase.OPENING);
      for (int i = 0; i < ThanosMove.SPACE_PULL.length(ThanosPhase.OPENING) + ThanosRules.STONE_GAP_TICKS; i++) {
         brain.tick();
      }

      assertEquals(3.0 * before, brain.weight(ThanosMove.POWER_PUNCH, at(ThanosPhase.OPENING, 3)), 1.0E-9);
   }

   @Test
   void sameStoneThreeTimesIsDamped() {
      ThanosBrain brain = new ThanosBrain(4);
      brain.commit(ThanosMove.POWER_WAVE, ThanosPhase.FINAL);
      brain.commit(ThanosMove.POWER_PUNCH, ThanosPhase.FINAL);
      for (int i = 0; i < 2000; i++) {
         brain.tick();
      }

      ThanosBrain fresh = new ThanosBrain(4);
      fresh.commit(ThanosMove.CLEAVE, ThanosPhase.FINAL);
      for (int i = 0; i < 2000; i++) {
         fresh.tick();
      }

      ThanosBrain.Situation s = at(ThanosPhase.FINAL, 8);
      assertEquals(0.25 * fresh.weight(ThanosMove.COMBO_TRINITY, s), brain.weight(ThanosMove.COMBO_TRINITY, s), 1.0E-9);
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

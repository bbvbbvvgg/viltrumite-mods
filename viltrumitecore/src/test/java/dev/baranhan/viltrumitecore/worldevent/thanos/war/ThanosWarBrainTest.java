package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosBrain;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Thanos in the war: he reads the battlefield (his ship over the city, flyers, snipers, heavy fire,
 * an ally holding the target, heroes the ledger protects) and each phase shows what it brought.
 */
class ThanosWarBrainTest {
   /** A neutral war (every factor at 1 but the phase's own moves). */
   private static ThanosBrain.War war(boolean ship, boolean ranged, int flyers, boolean defensive, boolean pinned, int allies, boolean controllable) {
      return new ThanosBrain.War(ship, ranged, flyers, defensive, pinned, allies, controllable);
   }

   private static final ThanosBrain.War CALM = war(false, false, 0, false, false, 0, true);

   private static ThanosBrain.Situation at(ThanosPhase phase, double distance, boolean air, float health, ThanosBrain.War war) {
      return new ThanosBrain.Situation(phase, true, distance, air, true, 1, 1, 0, false, false, health, 0.0F, war);
   }

   private static ThanosBrain.Situation at(ThanosPhase phase, double distance, ThanosBrain.War war) {
      return at(phase, distance, false, 1.0F, war);
   }

   private static double ratio(ThanosMove move, ThanosBrain.Situation with, ThanosBrain.Situation without) {
      double base = new ThanosBrain(1).weight(move, without);
      assertTrue(base > 0.0, move + " has a weight in the baseline");
      return new ThanosBrain(1).weight(move, with) / base;
   }

   private static Map<ThanosMove, Integer> spread(ThanosBrain.Situation s, int seeds) {
      Map<ThanosMove, Integer> counts = new EnumMap<>(ThanosMove.class);
      for (int seed = 0; seed < seeds; seed++) {
         ThanosMove move = new ThanosBrain(seed).choose(s);
         if (move != null) {
            counts.merge(move, 1, Integer::sum);
         }
      }

      return counts;
   }

   private static int sum(Map<ThanosMove, Integer> spread, Set<ThanosMove> moves) {
      return moves.stream().mapToInt(m -> spread.getOrDefault(m, 0)).sum();
   }

   @Test
   void heCallsTheShipOnlyWhenItHoversOverTheCity() {
      ThanosBrain brain = new ThanosBrain(1);
      ThanosBrain.War ready = war(true, false, 0, false, false, 0, true);
      assertEquals(0.0, brain.weight(ThanosMove.SHIP_CALL, at(ThanosPhase.BLACK_ORDER, 30.0, CALM)), "no ship, no lance");
      assertEquals(0.0, brain.weight(ThanosMove.SHIP_CALL, at(ThanosPhase.BLACK_ORDER, 30.0, ThanosBrain.War.NONE)));
      assertEquals(0.0, brain.weight(ThanosMove.SHIP_CALL, at(ThanosPhase.ARRIVAL, 30.0, ready)), "phase I: the ship only arrives");
      assertTrue(brain.weight(ThanosMove.SHIP_CALL, at(ThanosPhase.BLACK_ORDER, 30.0, ready)) > 0.0);
      assertTrue(brain.weight(ThanosMove.SHIP_CALL, at(ThanosPhase.GAUNTLET, 90.0, ready)) > 0.0, "the lance reaches far");
   }

   @Test
   void flyersAreBroughtDown() {
      ThanosBrain.War flyers = war(true, false, 1, false, false, 0, true);
      ThanosBrain.War none = war(true, false, 0, false, false, 0, true);
      assertEquals(2.0, ratio(ThanosMove.MIND_COMPULSION, at(ThanosPhase.INVASION, 15.0, true, 1.0F, flyers), at(ThanosPhase.INVASION, 15.0, true, 1.0F, none)),
         1.0E-9);
      assertEquals(1.7, ratio(ThanosMove.TIME_ANOMALY, at(ThanosPhase.INVASION, 15.0, true, 1.0F, flyers), at(ThanosPhase.INVASION, 15.0, true, 1.0F, none)),
         1.0E-9);
      Set<ThanosMove> bringDown = Set.of(ThanosMove.MIND_COMPULSION, ThanosMove.SPACE_BARRAGE, ThanosMove.TIME_ANOMALY, ThanosMove.SPACE_PULL,
         ThanosMove.REALITY_REWRITE, ThanosMove.SHIP_CALL);
      Map<ThanosMove, Integer> spread = spread(at(ThanosPhase.INVASION, 15.0, true, 1.0F, flyers), 300);
      Map<ThanosMove, Integer> plain = spread(at(ThanosPhase.INVASION, 15.0, true, 1.0F, none), 300);
      int ground = spread.entrySet().stream().filter(e -> !e.getKey().air).mapToInt(Map.Entry::getValue).sum();
      assertTrue(ground < 15, "moves that cannot reach him stay rare: " + spread);

      int down = sum(spread, bringDown);
      assertTrue(down > 150, "drag, snap, portals, the slam, the lance: " + spread);
      assertTrue(down > 1.25 * sum(plain, bringDown), "more than without flyers around him: " + plain);
   }

   @Test
   void snipersArePulledIn() {
      ThanosBrain.War sniper = war(false, true, 0, false, false, 0, true);
      assertEquals(2.5, ratio(ThanosMove.MIND_COMPULSION, at(ThanosPhase.BLACK_ORDER, 20.0, sniper), at(ThanosPhase.BLACK_ORDER, 20.0, CALM)), 1.0E-9);
      assertEquals(1.8, ratio(ThanosMove.SPACE_STEP, at(ThanosPhase.BLACK_ORDER, 20.0, sniper), at(ThanosPhase.BLACK_ORDER, 20.0, CALM)), 1.0E-9);
      Map<ThanosMove, Integer> spread = spread(at(ThanosPhase.BLACK_ORDER, 20.0, sniper), 300);
      int closing = sum(spread, Set.of(ThanosMove.MIND_COMPULSION, ThanosMove.SPACE_PULL, ThanosMove.SPACE_STEP, ThanosMove.COMBO_MIND_STEP,
         ThanosMove.COMBO_PORTAL_PUNCH, ThanosMove.SPACE_BARRAGE));
      assertTrue(closing > 180, "pull him in or go to him: " + spread);
   }

   @Test
   void heavyFireMakesHimGuardAndLetTheWarWork() {
      ThanosBrain.War heavy = war(true, false, 0, true, false, 0, true);
      ThanosBrain.War ready = war(true, false, 0, false, false, 0, true);
      assertEquals(3.0, ratio(ThanosMove.REALITY_MIRAGE, at(ThanosPhase.BLACK_ORDER, 10.0, heavy), at(ThanosPhase.BLACK_ORDER, 10.0, ready)), 1.0E-9);
      assertEquals(2.0, ratio(ThanosMove.SPACE_STEP, at(ThanosPhase.BLACK_ORDER, 10.0, heavy), at(ThanosPhase.BLACK_ORDER, 10.0, ready)), 1.0E-9);
      assertEquals(0.6, ratio(ThanosMove.CLEAVE, at(ThanosPhase.BLACK_ORDER, 3.0, heavy), at(ThanosPhase.BLACK_ORDER, 3.0, ready)), 1.0E-9);
      assertEquals(1.5, ratio(ThanosMove.SHIP_CALL, at(ThanosPhase.INVASION, 30.0, heavy), at(ThanosPhase.INVASION, 30.0, ready)), 1.0E-9);
   }

   @Test
   void aPinnedTargetGetsTheHeavyFollowUp() {
      ThanosBrain.War pinned = war(false, false, 0, false, true, 1, true);
      assertEquals(3.0, ratio(ThanosMove.POWER_PUNCH, at(ThanosPhase.BLACK_ORDER, 4.0, pinned), at(ThanosPhase.BLACK_ORDER, 4.0, CALM)), 1.0E-9);
      assertEquals(0.3, ratio(ThanosMove.MIND_COMPULSION, at(ThanosPhase.BLACK_ORDER, 12.0, pinned), at(ThanosPhase.BLACK_ORDER, 12.0, CALM)), 1.0E-9,
         "no second hold on a held hero");
      ThanosBrain.War army = war(false, false, 0, false, false, 3, true);
      assertEquals(1.3, ratio(ThanosMove.POWER_WAVE, at(ThanosPhase.INVASION, 10.0, army), at(ThanosPhase.INVASION, 10.0, CALM)), 1.0E-9,
         "the army on the target: support from range");
   }

   @Test
   void heroesTheLedgerProtectsAreNotHeldAgain() {
      ThanosBrain.War immune = war(false, false, 0, false, false, 0, false);
      for (ThanosMove move : new ThanosMove[]{ThanosMove.SPACE_PULL, ThanosMove.MIND_COMPULSION, ThanosMove.TIME_ANOMALY, ThanosMove.REALITY_REWRITE}) {
         assertTrue(ThanosBrain.control(move), move + " holds");
         assertEquals(0.15, ratio(move, at(ThanosPhase.INVASION, 14.0, immune), at(ThanosPhase.INVASION, 14.0, CALM)), 1.0E-9, move.name());
      }

      assertEquals(1.0, ratio(ThanosMove.POWER_WAVE, at(ThanosPhase.INVASION, 10.0, immune), at(ThanosPhase.INVASION, 10.0, CALM)), 1.0E-9, "the rest is open");
   }

   @Test
   void eachPhaseShowsWhatItBrought() {
      // a move of phase II weighs more in phase II than later
      assertEquals(1.4, ratio(ThanosMove.SPACE_BARRAGE, at(ThanosPhase.BLACK_ORDER, 20.0, CALM), at(ThanosPhase.INVASION, 20.0, CALM)), 1.0E-9);
      assertEquals(1.4, ratio(ThanosMove.SOUL_HARVEST, at(ThanosPhase.INVASION, 20.0, CALM), at(ThanosPhase.GAUNTLET, 20.0, CALM)), 1.0E-9);
      ThanosBrain brain = new ThanosBrain(1);
      for (ThanosMove move : ThanosMove.values()) {
         if (move.war() && !move.scripted()) {
            for (ThanosPhase phase : ThanosPhase.values()) {
               if (!phase.atLeast(move.minPhase)) {
                  assertEquals(0.0, brain.weight(move, at(phase, 10.0, war(true, false, 0, false, false, 0, true))), move + " before " + move.minPhase);
               }
            }
         }
      }
   }

   @Test
   void theGauntletBringsTheMultiStageCombos() {
      ThanosBrain brain = new ThanosBrain(1);
      for (ThanosMove combo : new ThanosMove[]{ThanosMove.COMBO_TIME_VOLLEY, ThanosMove.COMBO_MOON_FALL, ThanosMove.COMBO_INFINITY}) {
         assertEquals(ThanosPhase.GAUNTLET, combo.minPhase, combo.name());
         assertTrue(Integer.bitCount(combo.stones) >= 2, combo + " joins stones");
         assertEquals(0.0, brain.weight(combo, at(ThanosPhase.INVASION, 12.0, CALM)), combo + " before the Gauntlet");
         assertTrue(brain.weight(combo, at(ThanosPhase.GAUNTLET, 12.0, CALM)) > 0.0, combo + " in the Gauntlet");
      }

      assertEquals(6, Integer.bitCount(ThanosMove.COMBO_INFINITY.stones), "all six");
      assertEquals(2.0, ratio(ThanosMove.COMBO_INFINITY, at(ThanosPhase.GAUNTLET, 12.0, false, 0.1F, CALM), at(ThanosPhase.GAUNTLET, 12.0, false, 0.5F, CALM)),
         1.0E-9, "the last stand");
      Map<ThanosMove, Integer> spread = spread(at(ThanosPhase.GAUNTLET, 12.0, false, 0.4F, war(true, false, 0, false, false, 0, true)), 400);
      int combos = spread.entrySet().stream().filter(e -> e.getKey().combo()).mapToInt(Map.Entry::getValue).sum();
      assertTrue(combos > 80, "the Gauntlet's fight is full of combos: " + spread);
   }

   @Test
   void neverThroughAWallInTheWarEither() {
      for (ThanosPhase phase : ThanosPhase.values()) {
         for (double distance : new double[]{4.0, 12.0, 30.0, 70.0}) {
            ThanosBrain.Situation hidden = new ThanosBrain.Situation(phase, true, distance, false, false, 3, 2, 0, false, false, 0.5F, 0.0F,
               war(true, true, 1, true, false, 2, true));
            ThanosBrain brain = new ThanosBrain(3);
            for (ThanosMove move : ThanosMove.values()) {
               if (brain.weight(move, hidden) > 0.0) {
                  assertTrue(move.throughCover(), move + " through a wall in " + phase + " at " + distance);
               }
            }
         }
      }
   }
}

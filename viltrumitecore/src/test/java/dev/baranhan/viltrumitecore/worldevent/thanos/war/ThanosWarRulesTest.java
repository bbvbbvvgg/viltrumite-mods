package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import org.junit.jupiter.api.Test;

/** The war's numbers: what each phase brings, the army's caps, the ship's strikes and glide. */
class ThanosWarRulesTest {
   @Test
   void eachPhaseBringsItsPartOfTheWar() {
      assertFalse(ThanosWarRules.orderPhase(ThanosPhase.ARRIVAL), "phase I: Thanos and his ship alone");
      assertFalse(ThanosWarRules.armyPhase(ThanosPhase.ARRIVAL));
      assertFalse(ThanosWarRules.bombardPhase(ThanosPhase.ARRIVAL));
      assertTrue(ThanosWarRules.orderPhase(ThanosPhase.BLACK_ORDER), "phase II: the Black Order");
      assertFalse(ThanosWarRules.armyPhase(ThanosPhase.BLACK_ORDER));
      assertFalse(ThanosWarRules.bombardPhase(ThanosPhase.BLACK_ORDER));
      for (ThanosPhase late : new ThanosPhase[]{ThanosPhase.INVASION, ThanosPhase.GAUNTLET}) {
         assertTrue(ThanosWarRules.orderPhase(late), late + ": the generals keep fighting");
         assertTrue(ThanosWarRules.armyPhase(late), late + ": the army");
         assertTrue(ThanosWarRules.bombardPhase(late), late + ": the bombardment");
      }

      // the Gauntlet is Thanos's own fight: fewer waves, fewer volleys, but less time to dodge them
      assertTrue(ThanosWarRules.waveEvery(ThanosPhase.GAUNTLET) > ThanosWarRules.waveEvery(ThanosPhase.INVASION));
      assertTrue(ThanosWarRules.bombardEvery(ThanosPhase.GAUNTLET) > ThanosWarRules.bombardEvery(ThanosPhase.INVASION));
      assertTrue(ThanosWarRules.strikeWarning(ThanosPhase.GAUNTLET) < ThanosWarRules.strikeWarning(ThanosPhase.INVASION));
   }

   @Test
   void everyStrikeIsWarnedAndStaysSmall() {
      for (ThanosPhase phase : ThanosPhase.values()) {
         assertTrue(ThanosWarRules.strikeWarning(phase) >= 30, phase + ": at least 1.5 s to leave the ring");
      }

      assertTrue(ThanosWarRules.STRIKE_RADIUS <= 6.0, "a lance hits a small ring, not a district");
      assertTrue(ThanosWarRules.STRIKE_CRATER < ThanosWarRules.STRIKE_RADIUS, "the crater stays inside the ring");
      assertTrue(ThanosWarRules.MAX_STRIKES <= 6, "a bounded number of rings at once");
      assertTrue(ThanosWarRules.BOMBARD_SAFE > ThanosWarRules.STRIKE_RADIUS, "the bombardment never aims on top of a hero");
      assertTrue(ThanosWarRules.BLOCK_BUDGET <= 4000, "the war's own block damage is budgeted");
   }

   @Test
   void theLanceFallsOffToTheRim() {
      double r = ThanosWarRules.STRIKE_RADIUS;
      assertEquals(1.0F, ThanosWarRules.strikeFalloff(0.0, r), 1.0E-6F);
      assertEquals(0.35F, ThanosWarRules.strikeFalloff(r, r), 1.0E-6F);
      assertEquals(0.0F, ThanosWarRules.strikeFalloff(r + 0.01, r), 0.0F, "nothing outside the ring");
      float last = 2.0F;
      for (double d = 0.0; d <= r; d += 0.1) {
         float f = ThanosWarRules.strikeFalloff(d, r);
         assertTrue(f <= last && f > 0.0F, "falls off outwards");
         last = f;
      }
   }

   @Test
   void theBlackOrderScalesWithTheHeroesUpToTheCountedMax() {
      assertEquals(ThanosWarRules.MAW_HEALTH, ThanosWarRules.orderHealth(ThanosWarRules.MAW_HEALTH, 1), 1.0E-3F);
      assertEquals(ThanosWarRules.MAW_HEALTH, ThanosWarRules.orderHealth(ThanosWarRules.MAW_HEALTH, 0), 1.0E-3F, "no heroes counts as one");
      float last = 0.0F;
      for (int heroes = 1; heroes <= ThanosRules.MAX_COUNTED_HEROES; heroes++) {
         float h = ThanosWarRules.orderHealth(ThanosWarRules.CULL_HEALTH, heroes);
         assertTrue(h > last, "more heroes, more health");
         last = h;
      }

      assertEquals(last, ThanosWarRules.orderHealth(ThanosWarRules.CULL_HEALTH, ThanosRules.MAX_COUNTED_HEROES + 10), 1.0E-3F, "capped");
      assertTrue(ThanosWarRules.CULL_HEALTH > ThanosWarRules.MAW_HEALTH, "the brute outlasts the sorcerer");
      assertTrue(ThanosWarRules.ORDER_HIT_CAP > 0.0F && ThanosWarRules.ORDER_HIT_CAP < 0.1F, "no one-shot of a general");
   }

   @Test
   void theArmyIsCappedForTheServer() {
      int last = 0;
      for (int heroes = 0; heroes <= 20; heroes++) {
         int cap = ThanosWarRules.armyCap(heroes);
         assertTrue(cap >= ThanosWarRules.ARMY_BASE_CAP && cap <= ThanosWarRules.ARMY_MAX_CAP, "cap " + cap);
         assertTrue(cap >= last, "never fewer for more heroes");
         last = cap;
         for (int wave = 0; wave < 40; wave++) {
            int size = ThanosWarRules.waveSize(heroes, wave);
            assertTrue(size >= 4 && size <= 10, "wave size " + size);
         }
      }

      assertEquals(0, ThanosWarRules.room(ThanosWarRules.armyCap(1), 0, 1), "full");
      assertEquals(0, ThanosWarRules.room(0, ThanosWarRules.ARMY_TOTAL, 1), "the event total is spent");
      assertEquals(0, ThanosWarRules.room(ThanosWarRules.ARMY_MAX_CAP + 5, 0, 8), "never negative");

      // a whole war of waves, soldiers dying in between, never passes the caps
      for (int heroes = 1; heroes <= 8; heroes++) {
         int alive = 0;
         int spawned = 0;
         for (int wave = 0; wave < 100; wave++) {
            int n = Math.min(ThanosWarRules.waveSize(heroes, wave), ThanosWarRules.room(alive, spawned, heroes));
            alive += n;
            spawned += n;
            assertTrue(alive <= ThanosWarRules.armyCap(heroes), "alive " + alive);
            alive -= alive / 2;
         }

         assertTrue(spawned <= ThanosWarRules.ARMY_TOTAL, "spawned " + spawned);
      }
   }

   @Test
   void theGlideEasesOutCompletely() {
      assertEquals(0.0, ThanosWarRules.glide(0.0), 1.0E-12);
      assertEquals(1.0, ThanosWarRules.glide(1.0), 1.0E-12);
      assertEquals(0.0, ThanosWarRules.glide(-1.0), 1.0E-12, "clamped");
      assertEquals(1.0, ThanosWarRules.glide(2.0), 1.0E-12, "clamped");
      double last = 0.0;
      double lastStep = Double.MAX_VALUE;
      for (int i = 1; i <= 1000; i++) {
         double g = ThanosWarRules.glide(i / 1000.0);
         assertTrue(g > last, "always forward");
         assertTrue(g - last <= lastStep + 1.0E-12, "never faster than before");
         lastStep = g - last;
         last = g;
      }

      assertTrue(lastStep < 1.0E-6, "no speed left at the end");
      assertTrue(ThanosWarRules.glide(0.1) > 0.25, "in from the horizon at speed");
   }

   @Test
   void theShipIsAThirdOfTheSkyAboveTheCity() {
      // its span seen from under the hover point: about a third of the sky's 180 degrees
      double halfAngle = Math.toDegrees(Math.atan2(ThanosWarRules.SHIP_SPAN / 2.0, ThanosWarRules.SHIP_ALTITUDE));
      assertTrue(halfAngle * 2.0 >= 60.0 && halfAngle * 2.0 <= 110.0, "spans " + halfAngle * 2.0 + " degrees");
      for (long t = 0; t < 2L * ThanosWarRules.SHIP_BOB_PERIOD; t++) {
         assertTrue(Math.abs(ThanosWarRules.bob(t)) <= ThanosWarRules.SHIP_BOB + 1.0E-9);
      }

      assertEquals(0.0, ThanosWarRules.bob(0), 1.0E-12);
      assertEquals(ThanosWarRules.bob(17), ThanosWarRules.bob(17 + ThanosWarRules.SHIP_BOB_PERIOD), 1.0E-9, "periodic");
   }
}

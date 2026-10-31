package dev.baranhan.viltrumitecore.worldevent.chitauri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.WorldEventConflicts;
import org.junit.jupiter.api.Test;

/** Pure invasion timeline: phase order, beats, portal curve, wave plans against the caps. */
class ChitauriRulesTest {
   private static final ChitauriPhase[] FIGHTING = {ChitauriPhase.GROUND_ASSAULT, ChitauriPhase.AIR_ASSAULT, ChitauriPhase.LEVIATHAN, ChitauriPhase.CLIMAX};

   @Test
   void phasesRunInOrderAndRoundTripTheirIds() {
      ChitauriPhase p = ChitauriPhase.OPENING;
      int steps = 0;
      while (p != ChitauriPhase.ENDED) {
         ChitauriPhase next = p.next();
         assertEquals(p.ordinal() + 1, next.ordinal());
         p = next;
         steps++;
      }
      assertEquals(6, steps);
      assertSame(ChitauriPhase.ENDED, ChitauriPhase.ENDED.next());
      for (ChitauriPhase phase : ChitauriPhase.values()) {
         assertSame(phase, ChitauriPhase.byId(phase.id()));
         assertSame(phase, ChitauriPhase.byKey(phase.key()));
      }
      assertSame(ChitauriPhase.ENDED, ChitauriPhase.byId(-1));
      assertSame(ChitauriPhase.ENDED, ChitauriPhase.byId(99));
      assertNull(ChitauriPhase.byKey("nope"));
   }

   @Test
   void onlyTheFourMiddlePhasesFight() {
      for (ChitauriPhase phase : ChitauriPhase.values()) {
         boolean expected = phase == ChitauriPhase.GROUND_ASSAULT || phase == ChitauriPhase.AIR_ASSAULT || phase == ChitauriPhase.LEVIATHAN
            || phase == ChitauriPhase.CLIMAX;
         assertEquals(expected, phase.fighting(), phase.name());
      }
   }

   @Test
   void everyPhaseButEndedHasALength() {
      int total = 0;
      for (ChitauriPhase phase : ChitauriPhase.values()) {
         int len = ChitauriRules.length(phase);
         if (phase == ChitauriPhase.ENDED) {
            assertEquals(0, len);
         } else {
            assertTrue(len > 0, phase.name());
         }
         total += len;
      }
      // the whole invasion runs about seven minutes (plan: opening ~35 s, closing ~15 s)
      assertEquals(8620, total);
      assertEquals(35 * 20, ChitauriRules.OPENING_TICKS);
      assertTrue(ChitauriRules.CLOSING_TICKS >= 15 * 20);
   }

   @Test
   void openingAndClosingBeatsAreOrdered() {
      assertTrue(ChitauriRules.BEAT_DARKEN < ChitauriRules.BEAT_BEAM);
      assertTrue(ChitauriRules.BEAT_BEAM < ChitauriRules.BEAT_PORTAL_FULL);
      assertTrue(ChitauriRules.BEAT_PORTAL_FULL <= ChitauriRules.BEAT_STORM);
      assertTrue(ChitauriRules.BEAT_STORM < ChitauriRules.BEAT_FIRST_CHARIOTS);
      assertTrue(ChitauriRules.BEAT_FIRST_CHARIOTS < ChitauriRules.BEAT_FIRST_LEVIATHAN);
      assertTrue(ChitauriRules.BEAT_FIRST_LEVIATHAN < ChitauriRules.OPENING_TICKS);
      assertTrue(ChitauriRules.BEAT_SEVER < ChitauriRules.BEAT_PORTAL_GONE);
      assertTrue(ChitauriRules.BEAT_PORTAL_GONE < ChitauriRules.CLOSING_TICKS);
   }

   @Test
   void portalOpensSmoothlyAndCloses() {
      assertEquals(0.0F, ChitauriRules.portalOpen(ChitauriPhase.OPENING, 0.0F));
      assertEquals(0.0F, ChitauriRules.portalOpen(ChitauriPhase.OPENING, ChitauriRules.BEAT_BEAM - 1));
      float last = 0.0F;
      for (int t = 0; t <= ChitauriRules.OPENING_TICKS; t++) {
         float o = ChitauriRules.portalOpen(ChitauriPhase.OPENING, t);
         assertTrue(o >= last - 1.0E-6F, "opening never shrinks at " + t);
         assertTrue(Math.abs(o - last) < 0.02F, "no jump at " + t);
         last = o;
      }
      assertEquals(1.0F, ChitauriRules.portalOpen(ChitauriPhase.OPENING, ChitauriRules.BEAT_PORTAL_FULL), 1.0E-6F);
      for (ChitauriPhase phase : FIGHTING) {
         assertEquals(1.0F, ChitauriRules.portalOpen(phase, 123.0F));
         assertEquals(1.0F, ChitauriRules.skyDark(phase, 5.0F));
      }
      last = 1.0F;
      for (int t = 0; t <= ChitauriRules.CLOSING_TICKS; t++) {
         float o = ChitauriRules.portalOpen(ChitauriPhase.CLOSING, t);
         assertTrue(o <= last + 1.0E-6F, "closing never grows at " + t);
         last = o;
      }
      assertEquals(1.0F, ChitauriRules.portalOpen(ChitauriPhase.CLOSING, 0.0F), 1.0E-6F);
      assertEquals(0.0F, ChitauriRules.portalOpen(ChitauriPhase.CLOSING, ChitauriRules.BEAT_PORTAL_GONE), 1.0E-6F);
      assertEquals(0.0F, ChitauriRules.portalOpen(ChitauriPhase.ENDED, 10.0F));
   }

   @Test
   void skyDarkensFirstAndClearsLast() {
      assertEquals(0.0F, ChitauriRules.skyDark(ChitauriPhase.OPENING, 0.0F));
      assertEquals(1.0F, ChitauriRules.skyDark(ChitauriPhase.OPENING, ChitauriRules.BEAT_BEAM), 1.0E-6F);
      assertEquals(1.0F, ChitauriRules.skyDark(ChitauriPhase.CLOSING, ChitauriRules.BEAT_SEVER), 1.0E-6F);
      assertEquals(0.0F, ChitauriRules.skyDark(ChitauriPhase.CLOSING, ChitauriRules.CLOSING_TICKS), 1.0E-6F);
      assertEquals(0.0F, ChitauriRules.skyDark(ChitauriPhase.ENDED, 0.0F));
   }

   @Test
   void portalIsQuietOnlyBeforeTheFirstChariotsAndWhileClosing() {
      assertTrue(ChitauriRules.quietPortal(ChitauriPhase.OPENING, 0));
      assertTrue(ChitauriRules.quietPortal(ChitauriPhase.OPENING, ChitauriRules.BEAT_FIRST_CHARIOTS - 1));
      assertFalse(ChitauriRules.quietPortal(ChitauriPhase.OPENING, ChitauriRules.BEAT_FIRST_CHARIOTS));
      for (ChitauriPhase phase : FIGHTING) {
         assertFalse(ChitauriRules.quietPortal(phase, 0));
      }
      assertTrue(ChitauriRules.quietPortal(ChitauriPhase.CLOSING, 0));
      assertTrue(ChitauriRules.quietPortal(ChitauriPhase.ENDED, 0));
   }

   @Test
   void wavePlansFitTheCapsAndThePhase() {
      int leviathans = 1; // the opening beat sends the first one
      for (ChitauriPhase phase : new ChitauriPhase[]{ChitauriPhase.GROUND_ASSAULT, ChitauriPhase.AIR_ASSAULT, ChitauriPhase.LEVIATHAN}) {
         int pauses = 0;
         int i = 0;
         for (ChitauriRules.Wave w = ChitauriRules.wave(phase, 0); w != null; w = ChitauriRules.wave(phase, ++i)) {
            checkWave(phase, w);
            pauses += w.pause();
            leviathans += w.leviathans();
         }
         assertTrue(i >= 2, phase + " plans several waves");
         assertTrue(pauses <= ChitauriRules.length(phase), phase + " waves fit in the phase");
      }
      // the climax repeats its mixed wave and sends one replacement Leviathan (wave 2)
      int climaxLeviathans = 0;
      for (int i = 0; i < 40; i++) {
         ChitauriRules.Wave w = ChitauriRules.wave(ChitauriPhase.CLIMAX, i);
         assertNotNull(w);
         checkWave(ChitauriPhase.CLIMAX, w);
         climaxLeviathans += w.leviathans();
      }
      assertEquals(1, climaxLeviathans);
      assertTrue(leviathans + climaxLeviathans <= ChitauriRules.MAX_LEVIATHANS_TOTAL);
      assertNull(ChitauriRules.wave(ChitauriPhase.OPENING, 0));
      assertNull(ChitauriRules.wave(ChitauriPhase.CLOSING, 0));
      assertNull(ChitauriRules.wave(ChitauriPhase.ENDED, 0));
   }

   private static void checkWave(ChitauriPhase phase, ChitauriRules.Wave w) {
      assertTrue(w.pause() > 0, phase + " pause");
      assertTrue(w.carriers() + w.gunships() <= ChitauriRules.MAX_CHARIOTS, phase + " chariots");
      assertTrue(w.soldiers() <= ChitauriRules.MAX_SOLDIERS, phase + " soldiers");
      assertTrue(w.leviathans() <= ChitauriRules.MAX_LEVIATHANS, phase + " leviathans");
      assertEquals(w.carriers() * 3 + w.gunships() * 2, w.soldiers());
   }

   @Test
   void fitNeverOverfillsACap() {
      for (int cap = 0; cap <= 30; cap++) {
         for (int live = 0; live <= cap + 5; live++) {
            for (int wanted = 0; wanted <= 12; wanted++) {
               int n = ChitauriRules.fit(wanted, live, cap);
               assertTrue(n >= 0 && n <= wanted);
               assertTrue(live + n <= Math.max(cap, live));
            }
         }
      }
      assertEquals(3, ChitauriRules.fit(5, 21, ChitauriRules.MAX_SOLDIERS));
      assertEquals(0, ChitauriRules.fit(5, 24, ChitauriRules.MAX_SOLDIERS));
   }

   @Test
   void portalFacesDownAtItsTilt() {
      for (float yaw = 0.0F; yaw < 360.0F; yaw += 15.0F) {
         double[] n = ChitauriRules.portalNormal(yaw);
         assertEquals(1.0, Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]), 1.0E-9);
         assertTrue(n[1] < 0.0, "points down to the city");
         assertEquals(ChitauriRules.PORTAL_TILT, Math.toDegrees(Math.acos(-n[1])), 1.0E-4);
      }
   }

   @Test
   void smoothIsClampedAndSymmetric() {
      assertEquals(0.0F, ChitauriRules.smooth(-1.0F));
      assertEquals(1.0F, ChitauriRules.smooth(2.0F));
      assertEquals(0.5F, ChitauriRules.smooth(0.5F), 1.0E-6F);
      for (float t = 0.0F; t <= 1.0F; t += 0.05F) {
         assertEquals(1.0F, ChitauriRules.smooth(t) + ChitauriRules.smooth(1.0F - t), 1.0E-5F);
      }
   }

   @Test
   void capsMatchThePlan() {
      assertEquals(24, ChitauriRules.MAX_SOLDIERS);
      assertEquals(8, ChitauriRules.MAX_CHARIOTS);
      assertEquals(2, ChitauriRules.MAX_LEVIATHANS);
      assertEquals(40000, ChitauriRules.MAX_DESTROYED_BLOCKS);
      assertTrue(ChitauriRules.EMPTY_LEVEL_TICKS > 0);
   }

   @Test
   void startsAreRefusedWhileAnotherEventRunsUnlessForced() {
      assertTrue(WorldEventConflicts.mayStart(0, false));
      assertTrue(WorldEventConflicts.mayStart(0, true));
      assertFalse(WorldEventConflicts.mayStart(1, false));
      assertTrue(WorldEventConflicts.mayStart(1, true));
      assertFalse(WorldEventConflicts.mayStart(3, false));
   }
}

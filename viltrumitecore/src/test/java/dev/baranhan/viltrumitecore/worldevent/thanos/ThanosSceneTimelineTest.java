package dev.baranhan.viltrumitecore.worldevent.thanos;

import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThanosSceneTimelineTest {
   @Test
   void beatsRunInTheSpecifiedOrder() {
      Stage previous = null;
      for (int t = 0; t <= LENGTH; t++) {
         Stage stage = stage(t);
         if (previous != null) {
            assertTrue(stage.ordinal() >= previous.ordinal(), "stage went back at " + t);
            assertTrue(stage.ordinal() - previous.ordinal() <= 1, "stage skipped at " + t);
         }

         previous = stage;
      }

      assertEquals(Stage.DONE, stage(LENGTH));
      assertEquals(Stage.SNAP, stage(SNAP));
   }

   @Test
   void stonesLightOneByOneBeforeTheSnap() {
      assertEquals(0, stonesLit(STONES_START - 1));
      int last = 0;
      for (int t = STONES_START; t < STONES_END; t++) {
         int lit = stonesLit(t);
         assertTrue(lit >= last && lit - last <= 1);
         last = lit;
      }

      assertEquals(6, stonesLit(STONES_END));
      assertTrue(STONES_START + 5 * STONE_STEP < STONES_END);
   }

   @Test
   void fingersFoldSlowlyThenSnap() {
      assertEquals(0.0F, snapHand(FACE_END - 1));
      float previous = 0.0F;
      for (int t = FACE_END; t < SNAP; t++) {
         float h = snapHand(t);
         assertTrue(h >= previous && h <= 1.0F);
         previous = h;
      }

      assertTrue(FOLD_END - FACE_END >= 30, "the fold is slow (1.5 s or more)");
      assertEquals(1.0F, snapHand(SNAP));
      assertEquals(2.0F, snapHand(SNAP + 2));
   }

   @Test
   void dissolveFollowsTheSnapAndTheLightDies() {
      assertEquals(0.0F, dissolve(SNAP));
      assertEquals(1.0F, light(SNAP));
      assertEquals(1.0F, dissolve(DISSOLVE_END), 1.0E-6F);
      assertTrue(light(DISSOLVE_END) <= 0.06F);
      float d = 0.0F;
      for (int t = DISSOLVE_START; t <= DISSOLVE_END; t++) {
         assertTrue(dissolve(t) >= d);
         d = dissolve(t);
      }

      assertEquals(1.0F, dissolve(SPEECH_END - 1), 1.0E-6F, "the void holds during the speech");
   }

   @Test
   void odLagnaSpeaksSixFiveSecondLines() {
      assertEquals(LINE_TICKS * LINES, SPEECH_END - SPEECH_START);
      assertEquals(600, SPEECH_END - SPEECH_START, "30 seconds");
      assertEquals(-1, line(SPEECH_START - 1));
      for (int i = 0; i < LINES; i++) {
         assertEquals(i, line(SPEECH_START + i * LINE_TICKS));
         assertEquals(i, line(SPEECH_START + i * LINE_TICKS + LINE_TICKS - 1));
      }

      assertEquals(-1, line(SPEECH_END));
      assertEquals(1.0F, odLagna(SPEECH_START));
      assertEquals(0.0F, odLagna(VOID_END - 1));
      assertTrue(lineReveal(SPEECH_START + 1) < 0.1F, "text appears gradually");
      assertEquals(1.0F, lineReveal(SPEECH_START + LINE_TICKS - 1));
   }

   @Test
   void restoreReturnsTheWorldWithoutThanos() {
      assertEquals(SPEECH_END, THANOS_GONE);
      assertEquals(0.0F, restore(SPEECH_END - 1));
      assertEquals(1.0F, restore(RESTORE_END - 20), 1.0E-6F);
      assertEquals(0.0F, dissolve(RESTORE_END), 1.0E-6F);
      assertEquals(1.0F, light(RESTORE_END), 1.0E-6F);
      assertTrue(gold((SPEECH_END + RESTORE_END) / 2.0F) > 0.9F, "golden light at the middle of the restore");
      assertEquals(0.0F, gold(RESTORE_END));
      assertTrue(PAUSE_TICKS > LENGTH, "the pause outlives the scene, the event ends it");
   }

   @Test
   void victoryDimsTheStonesAndCrumbles() {
      assertEquals(6, victoryStonesLit(0));
      assertEquals(5, victoryStonesLit(VICTORY_KNEEL_END));
      assertEquals(0, victoryStonesLit(VICTORY_CRUMBLE_START));
      assertEquals(0.0F, victoryCrumble(VICTORY_CRUMBLE_START - 1));
      assertEquals(1.0F, victoryCrumble(VICTORY_LENGTH - 20), 1.0E-6F);
   }
}

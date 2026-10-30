package dev.baranhan.viltrumitecore.hero.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyScene;
import org.junit.jupiter.api.Test;

/** The copy cinematic timeline and its start gate (design 2026-11-02 §2). */
class CopyCinematicTest {
   @Test
   void lastsAboutThreeSecondsAndIsAMove() {
      assertEquals(60, CopyCinematic.LENGTH);
      assertEquals(CopyCinematic.LENGTH, SinclairMove.COPY.length());
      assertEquals("copy", SinclairMove.COPY.clip());
      assertTrue(SinclairMove.COPY.locksCasting(), "no casting while the world is stopped");
      assertFalse(SinclairMove.COPY.isAct());
   }

   @Test
   void beatsAreOrdered() {
      assertTrue(CopyCinematic.CAMERA_IN <= CopyCinematic.UNFOLD_START);
      assertTrue(CopyCinematic.UNFOLD_START < CopyCinematic.TEXT_IN && CopyCinematic.TEXT_IN < CopyCinematic.UNFOLD_END + 4);
      assertTrue(CopyCinematic.UNFOLD_END < CopyCinematic.FOLD_START);
      assertTrue(CopyCinematic.FOLD_START < CopyCinematic.FOLD_END && CopyCinematic.FOLD_END <= CopyCinematic.CAMERA_OUT);
      assertTrue(CopyCinematic.CAMERA_OUT < CopyCinematic.LENGTH);
      // the card is readable for at least 1.2 s
      assertTrue(CopyCinematic.FOLD_START - CopyCinematic.TEXT_IN >= 24);
      int beats = 0;
      for (int t = -5; t < 70; t++) {
         if (CopyCinematic.beat(t)) {
            beats++;
         }
      }

      assertEquals(4, beats);
   }

   @Test
   void cameraBlendsInAndOutSmoothly() {
      assertEquals(0.0, CopyCinematic.cameraWeight(-1), 1.0E-9);
      assertEquals(0.0, CopyCinematic.cameraWeight(0), 1.0E-9);
      assertEquals(1.0, CopyCinematic.cameraWeight(CopyCinematic.CAMERA_IN), 1.0E-9);
      assertEquals(1.0, CopyCinematic.cameraWeight(30), 1.0E-9);
      assertEquals(0.0, CopyCinematic.cameraWeight(CopyCinematic.LENGTH), 1.0E-9);
      double prev = 0.0;
      double maxStep = 0.0;
      for (double t = 0.0; t < CopyCinematic.LENGTH; t += 0.25) {
         double w = CopyCinematic.cameraWeight(t);
         assertTrue(w >= 0.0 && w <= 1.0);
         maxStep = Math.max(maxStep, Math.abs(w - prev));
         prev = w;
      }

      // no jump cut: at most ~5 % per quarter tick (≈ 60 fps at 20 TPS)
      assertTrue(maxStep < 0.06, "camera step " + maxStep);
   }

   @Test
   void cardUnfoldsHoldsAndFolds() {
      assertEquals(0.0, CopyCinematic.cardScale(CopyCinematic.UNFOLD_START - 1), 1.0E-9);
      assertEquals(90.0, CopyCinematic.cardTurn(0), 1.0E-9);
      assertEquals(0.0, CopyCinematic.cardTurn(30), 1.0E-9);
      assertEquals(-90.0, CopyCinematic.cardTurn(CopyCinematic.FOLD_END), 1.0E-9);
      assertTrue(CopyCinematic.cardScale(30) >= 1.0);
      assertEquals(0.0, CopyCinematic.cardScale(CopyCinematic.FOLD_END), 1.0E-9);
      assertEquals(0.0, CopyCinematic.textAlpha(CopyCinematic.UNFOLD_START), 1.0E-9);
      assertEquals(1.0, CopyCinematic.textAlpha(30), 1.0E-9);
      assertEquals(0.0, CopyCinematic.textAlpha(CopyCinematic.FOLD_START + 2), 1.0E-9);
      assertEquals(0.0, CopyCinematic.cardFlight(30), 1.0E-9);
      assertEquals(1.0, CopyCinematic.cardFlight(CopyCinematic.FOLD_END), 1.0E-9);
      assertTrue(CopyCinematic.glow(CopyCinematic.UNFOLD_END) > CopyCinematic.glow(40));
      assertEquals(0.0, CopyCinematic.glow(CopyCinematic.FOLD_END), 1.0E-9);
   }

   @Test
   void startGateRefusesEveryUnsafeCase() {
      assertTrue(CopyScene.mayStart(true, true, true, false, false));
      assertFalse(CopyScene.mayStart(false, true, true, false, false), "/sinclair cinematic off");
      assertFalse(CopyScene.mayStart(true, false, true, false, false), "a dead participant");
      assertFalse(CopyScene.mayStart(true, true, false, false, false), "another dimension");
      assertFalse(CopyScene.mayStart(true, true, true, true, false), "already held (DIO stop, another hero's grab)");
      assertFalse(CopyScene.mayStart(true, true, true, false, true), "world pause / body isolation");
   }
}

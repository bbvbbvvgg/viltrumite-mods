package dev.baranhan.viltrumitecore.client.rohan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.rohan.RohanMove;
import dev.baranhan.viltrumitecore.hero.rohan.RohanRules;
import org.junit.jupiter.api.Test;

/** Pure client math of Heaven's Door: clip choice, alpha curves, book fan-out and manga accents. */
class RohanClientMathTest {
   @Test
   void clipWrapLoopsOrClamps() {
      HeavensDoorClips.Clip idle = HeavensDoorClips.Clip.IDLE;
      assertEquals(5.0, idle.wrap(idle.length + 5.0), 1e-9);
      assertEquals(idle.length - 1.0, idle.wrap(-1.0), 1e-9);
      HeavensDoorClips.Clip touch = HeavensDoorClips.Clip.TOUCH;
      assertEquals(touch.length, touch.wrap(999.0), 1e-9);
      assertEquals(0.0, touch.wrap(-3.0), 1e-9);
   }

   @Test
   void doorMoveAppearsThenTouches() {
      HeavensDoorClips.Choice a = HeavensDoorClips.choose(RohanMove.DOOR, 0.0, false, 0.0, 0.0);
      assertEquals(HeavensDoorClips.Clip.APPEAR, a.clip());
      HeavensDoorClips.Choice b = HeavensDoorClips.choose(RohanMove.DOOR, RohanRules.DOOR_SUMMON + 1.0, false, 50.0, 0.0);
      assertEquals(HeavensDoorClips.Clip.TOUCH, b.clip());
      assertEquals(HeavensDoorClips.Place.LUNGE, b.place());
   }

   @Test
   void idleStandsAtBookWhileReading() {
      HeavensDoorClips.Choice c = HeavensDoorClips.choose(null, 0.0, true, 1000.0, 7.0);
      assertEquals(HeavensDoorClips.Clip.IDLE, c.clip());
      assertEquals(HeavensDoorClips.Place.AT_BOOK, c.place());
      assertEquals(HeavensDoorClips.Clip.WRITE, HeavensDoorClips.choose(RohanMove.WRITE, 0.0, true, 1000.0, 3.0).clip());
      assertEquals(HeavensDoorClips.Clip.GUARD, HeavensDoorClips.choose(RohanMove.COUNTER, 2.0, false, 1000.0, 3.0).clip());
   }

   @Test
   void everyClipHasAnAnimationFile() {
      for (HeavensDoorClips.Clip c : HeavensDoorClips.Clip.values()) {
         assertNotNull(RohanClientMathTest.class.getResource("/assets/viltrumitecore/" + c.file()), c.file());
      }
   }

   @Test
   void alphaCurvesAreBoundedAndMonotonic() {
      float prevA = -1.0F, prevD = 2.0F;
      for (int i = 0; i <= 40; i++) {
         float a = HeavensDoorClips.appearAlpha(i);
         float d = HeavensDoorClips.dismissAlpha(i * 0.5);
         assertTrue(a >= prevA && a >= 0.25F && a <= 1.0F);
         assertTrue(d <= prevD && d >= 0.0F && d <= 1.0F);
         prevA = a;
         prevD = d;
      }
      assertEquals(1.0F, HeavensDoorClips.appearAlpha(1000.0), 1e-6F);
      assertEquals(0.0F, HeavensDoorClips.dismissAlpha(HeavensDoorClips.DISMISS_TICKS), 1e-6F);
   }

   @Test
   void bookOpensAndFolds() {
      assertEquals(0.0F, BookPagesRenderer.progress(10.0, -1.0, 10.0), 1e-6F);
      assertEquals(1.0F, BookPagesRenderer.progress(10.0, -1.0, 10.0 + BookPagesRenderer.OPEN_TICKS), 1e-6F);
      assertEquals(0.0F, BookPagesRenderer.progress(0.0, 20.0, 20.0 + BookPagesRenderer.CLOSE_TICKS), 1e-6F);
      assertEquals(0.0F, BookPagesRenderer.pageAngle(3, 0.0F, 12.0), 1e-6F, "closed book is flat");
      assertTrue(BookPagesRenderer.pageAngle(BookPagesRenderer.PAGES - 1, 1.0F, 0.0) > BookPagesRenderer.pageAngle(0, 1.0F, 0.0));
   }

   @Test
   void mangaScaleAndEnvelope() {
      assertEquals(0.0F, MangaFx.scale(0.0F, 1.0F), 1e-6F);
      assertEquals(0.0F, MangaFx.scale(1.0F, 0.0F), "Screen Effects 0 disables accents");
      assertEquals(1.0F, MangaFx.scale(5.0F, 1.0F), 1e-6F);
      assertEquals(0.0F, MangaFx.envelope(-1.0, 10), 1e-6F);
      assertEquals(0.0F, MangaFx.envelope(10.0, 10), 1e-6F);
      for (int i = 0; i < 20; i++) {
         float e = MangaFx.envelope(i, 20);
         assertTrue(e >= 0.0F && e <= 1.0F);
      }
      assertTrue(MangaFx.envelope(3.0, 20) > MangaFx.envelope(18.0, 20));
   }
}

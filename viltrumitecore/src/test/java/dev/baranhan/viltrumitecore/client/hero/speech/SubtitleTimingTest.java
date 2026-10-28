package dev.baranhan.viltrumitecore.client.hero.speech;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SubtitleTimingTest {
   @Test
   void readingTimeFollowsTheTextLength() {
      int reference = SubtitleTiming.autoPageTicks(101, 1.0);
      assertTrue(reference >= 120 && reference <= 150, "the reference line reads in 6-7.5 s: " + reference);
      assertEquals(SubtitleTiming.MIN_PAGE, SubtitleTiming.autoPageTicks(3, 1.0));
      assertEquals(SubtitleTiming.MAX_PAGE, SubtitleTiming.autoPageTicks(1000, 1.0));
      assertTrue(SubtitleTiming.autoPageTicks(60, 1.5) > SubtitleTiming.autoPageTicks(60, 1.0), "player reading-speed scale");
      assertEquals(SubtitleTiming.autoPageTicks(60, 2.5), SubtitleTiming.autoPageTicks(60, 99.0), "scale is clamped");
   }

   @Test
   void anExplicitDurationIsSpreadByLength() {
      int[] ticks = SubtitleTiming.pageTicks(new int[]{100, 50, 50}, 400, 1.0);
      assertArrayEquals(new int[]{200, 100, 100}, ticks);
      assertArrayEquals(new int[]{SubtitleTiming.MIN_SPREAD_PAGE, 89}, SubtitleTiming.pageTicks(new int[]{1, 99}, 90, 1.0));
   }

   @Test
   void enterHoldCrossfadeAndExit() {
      int[] pages = {100, 60};
      SubtitleTiming.Frame start = SubtitleTiming.frame(pages, 0, Double.NaN);
      assertEquals(0.0F, start.alpha(), 1e-6);
      assertEquals(SubtitleTiming.SLIDE, start.slide(), 1e-6);
      SubtitleTiming.Frame shown = SubtitleTiming.frame(pages, 50, Double.NaN);
      assertEquals(1.0F, shown.alpha(), 1e-6);
      assertEquals(0.0F, shown.slide(), 1e-6);
      assertEquals(0, shown.page());
      assertEquals(1.0F, shown.textAlpha(), 1e-6);
      SubtitleTiming.Frame leaving = SubtitleTiming.frame(pages, 98, Double.NaN);
      assertEquals(0, leaving.page());
      assertTrue(leaving.textAlpha() < 1.0F && leaving.alpha() == 1.0F, "only the text crossfades, the panel stays");
      SubtitleTiming.Frame next = SubtitleTiming.frame(pages, 101, Double.NaN);
      assertEquals(1, next.page());
      assertTrue(next.textAlpha() < 1.0F && next.textDrift() > 0.0F);
      SubtitleTiming.Frame exiting = SubtitleTiming.frame(pages, 165, Double.NaN);
      assertTrue(exiting.alpha() < 1.0F && exiting.slide() > 0.0F);
      assertFalse(exiting.finished());
      assertTrue(SubtitleTiming.frame(pages, SubtitleTiming.length(pages), Double.NaN).finished());
   }

   @Test
   void aForcedExitIsShortAndAfterTheEndChangesNothing() {
      int[] pages = {100};
      assertFalse(SubtitleTiming.frame(pages, 43, 40).finished());
      assertTrue(SubtitleTiming.frame(pages, 40 + SubtitleTiming.FORCED_EXIT, 40).finished());
      assertEquals(SubtitleTiming.frame(pages, 105, Double.NaN).alpha(), SubtitleTiming.frame(pages, 105, 103).alpha(), 1e-6);
   }
}

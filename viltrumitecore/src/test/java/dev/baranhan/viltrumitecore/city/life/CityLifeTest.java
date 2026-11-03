package dev.baranhan.viltrumitecore.city.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The living city's server logic (issue #136), pure parts: the mood map of the streets (calm, wary, panic,
 * aftermath and the slow way back), the choice and timing of voiced chats, the voices of citizens and the call
 * limiter.
 */
class CityLifeTest {
   @Test
   void moodFollowsDangerAndComesBackSlowly() {
      CityMood m = new CityMood();
      assertEquals(CityMood.Mood.CALM, m.mood(0, 0, 0));
      // a blast in the middle of a street: panic at the centre, wary at the edge, nothing far away
      m.hit(0, 0, 12, 1.0F, 0.4F, 0);
      assertEquals(CityMood.Mood.PANIC, m.mood(5, 5, 0));
      assertEquals(CityMood.Mood.WARY, m.mood(40, 0, 0), "the blast is felt a little past its radius");
      assertEquals(CityMood.Mood.CALM, m.mood(200, 200, 0));
      // panic fades into wariness, then the aftermath (trauma lasts longer), then calm
      CityMood.Mood last = CityMood.Mood.PANIC;
      int stage = 0;
      CityMood.Mood[] order = {CityMood.Mood.PANIC, CityMood.Mood.WARY, CityMood.Mood.AFTER, CityMood.Mood.CALM};
      long panicEnds = -1;
      long calmAt = -1;
      for (long t = 0; t <= 40000; t += 100) {
         CityMood.Mood now = m.mood(0, 0, t);
         if (now != last) {
            stage++;
            assertEquals(order[stage], now, "stage " + stage + " at " + t);
            if (now == CityMood.Mood.WARY) {
               panicEnds = t;
            }
            if (now == CityMood.Mood.CALM) {
               calmAt = t;
            }
            last = now;
         }
      }
      assertEquals(3, stage, "panic -> wary -> aftermath -> calm");
      assertTrue(panicEnds >= 800 && panicEnds <= 1100, "panic for about 45 s: " + panicEnds);
      assertTrue(calmAt > 4000 && calmAt < 12000, "back to calm within minutes, not at once: " + calmAt);
   }

   @Test
   void moodHitsAddUpAndPrune() {
      CityMood m = new CityMood();
      m.hit(0, 0, 0, 0.1F, 0.0F, 0);
      assertEquals(CityMood.Mood.CALM, m.mood(0, 0, 0));
      m.hit(0, 0, 0, 0.1F, 0.0F, 0);
      m.hit(0, 0, 0, 0.1F, 0.0F, 0);
      assertEquals(CityMood.Mood.WARY, m.mood(0, 0, 0), "small dangers add up");
      assertTrue(m.size() > 0);
      m.prune(200000, 0.01F);
      assertEquals(0, m.size(), "calm cells are forgotten");
      assertEquals(1.0F, CityMood.falloff(3, 10));
      assertEquals(0.0F, CityMood.falloff(10 + CityMood.SPREAD, 10));
      assertTrue(CityMood.falloff(20, 10) < 1.0F && CityMood.falloff(20, 10) > CityMood.falloff(30, 10));
      assertEquals(0.75F, CityMood.union(0.5F, 0.5F), 1e-6F);
      assertEquals(CityMood.Mood.AFTER, CityMood.of(0.1F, 0.3F));
      assertEquals(CityMood.Mood.WARY, CityMood.of(0.2F, 0.9F), "fresh alarm wins over the aftermath");
      assertNotEquals(CityMood.key(1, 2), CityMood.key(2, 1));
      assertEquals(-1, CityMood.cell(-0.5));
   }

   @Test
   void timeOfDayClasses() {
      assertEquals(CityLifeCatalog.MORNING, CityConversation.timeClass(0));
      assertEquals(CityLifeCatalog.MORNING, CityConversation.timeClass(23500));
      assertEquals(CityLifeCatalog.DAY, CityConversation.timeClass(6000));
      assertEquals(CityLifeCatalog.EVENING, CityConversation.timeClass(12500));
      assertEquals(-1, CityConversation.timeClass(18000));
      assertEquals(CityLifeCatalog.DAY, CityConversation.timeClass(24000L * 5 + 6000), "any day");
   }

   @Test
   void dialoguesFitMoodTimeAndWeather() {
      int weather = index("weather");
      int rain = index("rain");
      int morning = index("morning");
      int evening = index("evening");
      int uneasy = index("uneasy");
      assertTrue(CityConversation.fits(weather, CityLifeCatalog.CALM, CityLifeCatalog.DAY, false));
      assertFalse(CityConversation.fits(weather, CityLifeCatalog.CALM, CityLifeCatalog.DAY, true), "no 'nice weather' in the rain");
      assertFalse(CityConversation.fits(weather, CityLifeCatalog.CALM, -1, false), "nor at night");
      assertTrue(CityConversation.fits(rain, CityLifeCatalog.CALM, -1, true));
      assertFalse(CityConversation.fits(rain, CityLifeCatalog.CALM, CityLifeCatalog.DAY, false));
      assertTrue(CityConversation.fits(morning, CityLifeCatalog.CALM, CityLifeCatalog.MORNING, false));
      assertFalse(CityConversation.fits(morning, CityLifeCatalog.CALM, CityLifeCatalog.EVENING, false));
      assertTrue(CityConversation.fits(evening, CityLifeCatalog.CALM, -1, false), "good night at night");
      assertFalse(CityConversation.fits(uneasy, CityLifeCatalog.CALM, CityLifeCatalog.DAY, false), "wary talk only when wary");
      assertTrue(CityConversation.fits(uneasy, CityLifeCatalog.WARY, CityLifeCatalog.DAY, false));
      // every mood and time has something to say, and pick never returns a recent or unfitting dialogue
      for (int mood : new int[]{CityLifeCatalog.CALM, CityLifeCatalog.WARY, CityLifeCatalog.AFTER}) {
         for (int time : new int[]{CityLifeCatalog.MORNING, CityLifeCatalog.DAY, CityLifeCatalog.EVENING, -1}) {
            for (boolean wet : new boolean[]{false, true}) {
               Set<Integer> seen = new HashSet<>();
               for (int i = 0; i < 200; i++) {
                  int d = CityConversation.pick(mood, time, wet, x -> false, i / 200.0F);
                  assertTrue(d >= 0 && CityConversation.fits(d, mood, time, wet), mood + "/" + time + "/" + wet + " -> " + d);
                  seen.add(d);
               }
               assertTrue(seen.size() >= 2, "more than one dialogue for " + mood + "/" + time + "/" + wet);
               int first = CityConversation.pick(mood, time, wet, x -> false, 0.0F);
               for (int i = 0; i < 50; i++) {
                  assertNotEquals(first, CityConversation.pick(mood, time, wet, x -> x == first, i / 50.0F), "anti-repeat");
               }
               assertEquals(-1, CityConversation.pick(mood, time, wet, x -> true, 0.5F));
            }
         }
      }
   }

   @Test
   void conversationTimingSwapsSpeakers() {
      int greet = index("greet");
      int[] gaps = {6, 9};
      CityConversation c = new CityConversation(greet, 0, 2, 1.0F, 1.05F, 100, gaps);
      int len = CityConversation.length(greet, c.voices, c.pitches, gaps);
      assertEquals(110 + len + CityConversation.AFTER_LAST, c.endsAt());
      int[] turns = CityLifeCatalog.TURNS[greet];
      long t = 100;
      int said = 0;
      int lastRole = -1;
      long lastStart = -1;
      while (!c.done() && t < 1000) {
         int n = c.next(t);
         if (n >= 0) {
            assertEquals(turns[said], n);
            int role = n >>> 16;
            assertNotEquals(lastRole, role, "they take turns");
            if (lastStart >= 0) {
               int prev = turns[said - 1];
               int prevRole = prev >>> 16;
               long need = CityConversation.take(c.voices[prevRole], prev & 0xFFFF, c.pitches[prevRole]) + gaps[(said - 1) % gaps.length];
               assertEquals(need, t - lastStart, "the next line waits for the previous take and a pause");
            }
            lastRole = role;
            lastStart = t;
            said++;
         }
         t++;
      }
      assertEquals(turns.length, said);
      assertTrue(lastStart < c.endsAt(), "the chat ends after the last line");
      assertTrue(CityConversation.take(0, 0, 1.06F) <= CityConversation.take(0, 0, 0.94F), "a higher pitch plays faster");
   }

   @Test
   void voicesAndPitchesOfCitizens() {
      Set<Integer> men = new HashSet<>();
      Set<Integer> women = new HashSet<>();
      for (int seed = 0; seed < 400; seed++) {
         int m = CityLife.voiceOf(seed, false);
         int f = CityLife.voiceOf(seed, true);
         assertTrue(CityLifeCatalog.VOICES[m].startsWith("m") && CityLifeCatalog.VOICES[f].startsWith("f"));
         men.add(m);
         women.add(f);
         float p = CityLife.pitchOf(seed);
         assertTrue(p >= 0.94F && p <= 1.06F, "pitch " + p);
         assertEquals(m, CityLife.voiceOf(seed, false), "stable per citizen");
      }
      assertEquals(2, men.size(), "two men's voices");
      assertEquals(2, women.size(), "two women's voices");
      assertEquals("city_life.voice.f2.greet_1", CityLife.lineEvent(CityLifeCatalog.voice("f2"), CityLifeCatalog.line("greet_1")));
   }

   @Test
   void chatFactorAndCallLimiter() {
      assertEquals(1.0F, CityLife.chatFactor(CityMood.Mood.CALM, CityLifeCatalog.DAY, false));
      assertEquals(0.0F, CityLife.chatFactor(CityMood.Mood.PANIC, CityLifeCatalog.DAY, false), "nobody chats in a panic");
      assertTrue(CityLife.chatFactor(CityMood.Mood.WARY, CityLifeCatalog.DAY, false) < CityLife.chatFactor(CityMood.Mood.AFTER, CityLifeCatalog.DAY, false));
      assertTrue(CityLife.chatFactor(CityMood.Mood.CALM, -1, false) < 0.5F, "fewer chats at night");
      assertTrue(CityLife.chatFactor(CityMood.Mood.CALM, CityLifeCatalog.DAY, true) < 1.0F, "and in the rain");
      long[] empty = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
      assertTrue(CityLife.cellAllows(empty, 0));
      assertFalse(CityLife.cellAllows(new long[]{Long.MIN_VALUE, Long.MIN_VALUE, 100}, 100 + CityLife.CALL_SPACING - 1), "calls are spaced");
      assertTrue(CityLife.cellAllows(new long[]{Long.MIN_VALUE, Long.MIN_VALUE, 100}, 100 + CityLife.CALL_SPACING));
      assertFalse(CityLife.cellAllows(new long[]{100, 113, 126}, 139), "at most three calls in two seconds per street cell");
      assertTrue(CityLife.cellAllows(new long[]{100, 113, 126}, 100 + CityLife.CALL_WINDOW));
   }

   private static int index(String dialogue) {
      for (int i = 0; i < CityLifeCatalog.DIALOGUES.length; i++) {
         if (CityLifeCatalog.DIALOGUES[i].equals(dialogue)) {
            return i;
         }
      }
      throw new AssertionError(dialogue);
   }
}

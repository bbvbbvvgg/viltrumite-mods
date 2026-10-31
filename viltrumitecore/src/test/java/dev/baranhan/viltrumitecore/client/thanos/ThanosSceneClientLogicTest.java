package dev.baranhan.viltrumitecore.client.thanos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneCues.Cue;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneCues.Kind;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure client logic of the Thanos event: scene sound cues, HUD ability warnings, typed subtitles. */
class ThanosSceneClientLogicTest {
   @Test
   void theCueListFollowsTheTimeline() {
      Map<Kind, Integer> count = new EnumMap<>(Kind.class);
      int last = -1;
      for (Cue c : ThanosSceneCues.ALL) {
         assertTrue(c.tick() >= last, "sorted");
         last = c.tick();
         count.merge(c.kind(), 1, Integer::sum);
         assertTrue(c.tick() >= 0 && c.tick() < ThanosSceneTimeline.LENGTH, c.toString());
      }

      for (Kind k : Kind.values()) {
         int expected = k == Kind.STONE_LIGHT ? 6 : k == Kind.LINE ? ThanosSceneTimeline.LINES : 1;
         assertEquals(expected, count.getOrDefault(k, 0), k.toString());
      }

      int stone = 0;
      int line = 0;
      for (Cue c : ThanosSceneCues.ALL) {
         if (c.kind() == Kind.STONE_LIGHT) {
            assertEquals(stone++, c.index(), "stones light in order");
            assertTrue(c.tick() < ThanosSceneTimeline.SNAP, "before the snap");
         } else if (c.kind() == Kind.LINE) {
            assertEquals(ThanosSceneTimeline.SPEECH_START + line * ThanosSceneTimeline.LINE_TICKS, c.tick(), "line " + line);
            assertEquals(line++, c.index());
         }
      }

      assertTrue(ThanosSceneCues.FINGERS < ThanosSceneTimeline.SNAP, "the fingers fold before the click");
      assertTrue(ThanosSceneCues.voidWanted(ThanosSceneTimeline.SPEECH_START));
      assertFalse(ThanosSceneCues.voidWanted(ThanosSceneTimeline.SNAP));
      assertFalse(ThanosSceneCues.voidWanted(ThanosSceneTimeline.RESTORE_END));
   }

   /** Whatever the clock's step (frame rate, lag), every cue plays exactly once. */
   @Test
   void everyCueFiresExactlyOnceAtAnyStep() {
      for (float step : new float[]{0.17F, 0.5F, 1.0F, 2.75F, 9.0F, 31.0F}) {
         Map<Cue, Integer> fired = new HashMap<>();
         float prev = -1.0F;
         for (float t = 0.0F; prev < ThanosSceneTimeline.LENGTH; t += step) {
            for (Cue c : ThanosSceneCues.between(prev, t, 40.0F)) {
               fired.merge(c, 1, Integer::sum);
            }

            prev = t;
         }

         for (Cue c : ThanosSceneCues.ALL) {
            assertEquals(1, fired.getOrDefault(c, 0), c + " at step " + step);
         }
      }
   }

   @Test
   void aLateJoinSkipsTheCuesBehindIt() {
      // joined at tick 600: only the last 40 ticks' cues, not the whole scene at once
      List<Cue> late = ThanosSceneCues.between(-1.0F, 600.0F, 40.0F);
      for (Cue c : late) {
         assertTrue(c.tick() > 560 && c.tick() <= 600, c.toString());
      }

      assertTrue(ThanosSceneCues.between(500.0F, 500.0F, 40.0F).isEmpty(), "a clock that did not move plays nothing");
      assertTrue(ThanosSceneCues.between(510.0F, 500.0F, 40.0F).isEmpty(), "nor one that went back");
      List<Kind> start = new ArrayList<>();
      for (Cue c : ThanosSceneCues.between(-1.0F, 0.0F, 40.0F)) {
         start.add(c.kind());
      }

      assertEquals(List.of(Kind.WORLD_STOP), start, "the stop sounds at tick 0");
   }

   @Test
   void warningsCoverTheDangerousMovesOnly() {
      for (ThanosMove m : ThanosMove.values()) {
         boolean expected = m.kind != ThanosMove.Kind.MELEE && m != ThanosMove.SPACE_STEP && m != ThanosMove.SPACE_WARD && m != ThanosMove.TIME_REWIND;
         assertEquals(expected, ThanosHud.warns(m), m.toString());
         if (m.combo()) {
            assertTrue(ThanosHud.warns(m), "every combo warns: " + m);
         }
      }

      assertFalse(ThanosHud.warns(null));
   }

   @Test
   void aWarningLastsThroughTheWindUpAndTheStrike() {
      for (ThanosPhase phase : ThanosPhase.values()) {
         for (ThanosMove m : ThanosMove.values()) {
            if (!ThanosHud.warns(m)) {
               assertEquals(0.0F, ThanosHud.warning(m, phase, 5.0F));
               continue;
            }

            int tel = ThanosRules.telegraph(m.telegraph, phase);
            assertTrue(tel >= (phase == ThanosPhase.FINAL ? ThanosRules.MIN_TELEGRAPH_FINAL : ThanosRules.MIN_TELEGRAPH), m + " telegraph " + tel);
            assertEquals(0.0F, ThanosHud.warning(m, phase, -1.0F));
            assertTrue(ThanosHud.warning(m, phase, 1.0F) > 0.2F, "it appears at once");
            for (float age = 3.0F; age <= tel + m.active; age += 1.0F) {
               assertEquals(1.0F, ThanosHud.warning(m, phase, age), 1.0E-6F, m + " fully on at " + age);
            }

            assertEquals(0.0F, ThanosHud.warning(m, phase, tel + m.active + 4.0F), m + " gone after the strike");
         }
      }
   }

   @Test
   void typedSubtitlesRevealByCodePoints() {
      List<String> lines = List.of("Enough.", "Ты сжал", "星辰熄灭😀");
      assertEquals(List.of(), ThanosSceneHud.reveal(lines, 0));
      assertEquals(List.of(), ThanosSceneHud.reveal(lines, -3));
      assertEquals(List.of("Eno"), ThanosSceneHud.reveal(lines, 3));
      assertEquals(List.of("Enough.", "Ты"), ThanosSceneHud.reveal(lines, 9));
      // the emoji is one character (a surrogate pair), never cut in half
      assertEquals(List.of("Enough.", "Ты сжал", "星辰熄灭"), ThanosSceneHud.reveal(lines, 18));
      assertEquals(lines, ThanosSceneHud.reveal(lines, 19));
      assertEquals(lines, ThanosSceneHud.reveal(lines, 1000));
   }
}

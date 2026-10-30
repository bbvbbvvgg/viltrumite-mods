package dev.baranhan.viltrumitecore.hero.speech;

import static dev.baranhan.viltrumitecore.hero.speech.HeroLine.Priority.*;
import static dev.baranhan.viltrumitecore.hero.speech.HeroSpeechRules.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HeroSpeechRulesTest {
   @Test
   void theSameLineIsNeverRestartedWhilePlaying() {
      assertEquals(ALREADY_ACTIVE, HeroSpeechRules.decide("r:cut", ULTIMATE, "r:cut", ULTIMATE, 10, Long.MIN_VALUE, false));
      assertEquals(REPLACE, HeroSpeechRules.decide("r:cut", ULTIMATE, "r:cut", ULTIMATE, 10, Long.MIN_VALUE, true), "operator preview restarts");
   }

   @Test
   void cooldownAndPriorityGateOtherLines() {
      assertEquals(PLAY, HeroSpeechRules.decide(null, null, "r:cut", ULTIMATE, 100, 99, false));
      assertEquals(COOLDOWN, HeroSpeechRules.decide(null, null, "r:cut", ULTIMATE, 100, 101, false));
      assertEquals(PLAY, HeroSpeechRules.decide(null, null, "r:cut", ULTIMATE, 100, 101, true));
      assertEquals(REPLACE, HeroSpeechRules.decide("r:taunt", ABILITY, "r:cut", ULTIMATE, 100, 0, false));
      assertEquals(REPLACE, HeroSpeechRules.decide("r:taunt", ABILITY, "r:other", ABILITY, 100, 0, false), "equal priority: the newer line wins");
      assertEquals(OUTRANKED, HeroSpeechRules.decide("r:cut", ULTIMATE, "r:taunt", ABILITY, 100, 0, false));
      assertTrue(PLAY.starts() && REPLACE.starts() && !COOLDOWN.starts() && !OUTRANKED.starts() && !ALREADY_ACTIVE.starts());
   }

   @Test
   void lifetimeUsesTheExplicitDurationOrAnEstimate() {
      assertEquals(200 + HeroSpeechRules.FADE_GRACE_TICKS, HeroSpeechRules.lifetimeTicks(200, new int[]{10, 20, 30}));
      assertEquals(3 * HeroSpeechRules.ESTIMATE_TICKS_PER_FRAGMENT + HeroSpeechRules.FADE_GRACE_TICKS, HeroSpeechRules.lifetimeTicks(0, new int[]{-1, -1, -1}),
         "unknown text keeps the flat per-fragment estimate");
      assertEquals(HeroSpeechRules.ESTIMATE_TICKS_PER_FRAGMENT + HeroSpeechRules.FADE_GRACE_TICKS, HeroSpeechRules.lifetimeTicks(0, new int[0]));
   }

   @Test
   void textEstimateCoversTheClientPageWithAMarginButFreesShortQuips() {
      // "Mjolnir!": the client shows one 40-tick page; the server no longer holds the speaker for 9 s.
      assertEquals(60, HeroSpeechRules.estimateFragmentTicks(8));
      assertTrue(HeroSpeechRules.lifetimeTicks(0, new int[]{8}) < 100, "a kill remark right after a quip is not skipped as BUSY for long");
      for (int chars = 0; chars <= 400; chars += 7) {
         int clientPage = (int)Math.max(40, Math.min(240, Math.round(18.0 + 1.15 * chars)));
         assertTrue(HeroSpeechRules.estimateFragmentTicks(chars) >= clientPage, "never shorter than the client's own page at scale 1: " + chars);
         assertTrue(HeroSpeechRules.estimateFragmentTicks((int)Math.round(chars * 1.3)) >= clientPage, "covers a 1.3x longer translation: " + chars);
      }

      assertEquals(360, HeroSpeechRules.estimateFragmentTicks(1000), "capped like the client page");
   }

   @Test
   void variantsRotateAndSkipLinesOnCooldown() {
      assertEquals(0, HeroSpeechRules.nextVariant(-1, 3, i -> true));
      assertEquals(1, HeroSpeechRules.nextVariant(0, 3, i -> true));
      assertEquals(0, HeroSpeechRules.nextVariant(2, 3, i -> true));
      assertEquals(2, HeroSpeechRules.nextVariant(0, 3, i -> i != 1));
      assertEquals(0, HeroSpeechRules.nextVariant(0, 1, i -> true), "a single line repeats after its cooldown");
      assertEquals(-1, HeroSpeechRules.nextVariant(0, 3, i -> false));
      assertEquals("subtitle.viltrumitecore.speaker.reinhard", HeroSpeechRules.defaultSpeakerKey("reinhard"));
   }

   @Test
   void ambientRemarksNeverCutALineAndAbilityLinesDoNotFlicker() {
      // An ambient remark (kill, low health) never interrupts, whatever is on screen.
      assertEquals(BUSY, HeroSpeechRules.decide("d:knives", ABILITY, 200, "d:kill", AMBIENT, 1000, Long.MIN_VALUE, false));
      assertEquals(BUSY, HeroSpeechRules.decide("d:other", AMBIENT, 200, "d:kill", AMBIENT, 1000, Long.MIN_VALUE, false));
      assertEquals(PLAY, HeroSpeechRules.decide(null, null, 0, "d:kill", AMBIENT, 1000, Long.MIN_VALUE, false));
      // Combat lines never override an ultimate line.
      assertEquals(OUTRANKED, HeroSpeechRules.decide("d:roller", ULTIMATE, 5, "d:knives", ABILITY, 1000, Long.MIN_VALUE, false));
      // An ability line replaces another ability line only once that one was readable for a moment.
      assertEquals(BUSY, HeroSpeechRules.decide("d:knives", ABILITY, HeroSpeechRules.MIN_SHOWN_BEFORE_REPLACE - 1, "d:drain", ABILITY, 1000,
         Long.MIN_VALUE, false));
      assertEquals(REPLACE, HeroSpeechRules.decide("d:knives", ABILITY, HeroSpeechRules.MIN_SHOWN_BEFORE_REPLACE, "d:drain", ABILITY, 1000,
         Long.MIN_VALUE, false));
      // Ultimate phases hand over at once; a higher rank interrupts at once.
      assertEquals(REPLACE, HeroSpeechRules.decide("d:roller", ULTIMATE, 0, "d:roller_appear", ULTIMATE, 1000, Long.MIN_VALUE, false));
      assertEquals(REPLACE, HeroSpeechRules.decide("d:knives", ABILITY, 0, "d:roller", ULTIMATE, 1000, Long.MIN_VALUE, false));
      // The rest is the legacy decision: same line, cooldown, operator force.
      assertEquals(ALREADY_ACTIVE, HeroSpeechRules.decide("d:knives", ABILITY, 3, "d:knives", ABILITY, 1000, Long.MIN_VALUE, false));
      assertEquals(COOLDOWN, HeroSpeechRules.decide(null, null, 0, "d:kill", AMBIENT, 1000, 1001, false));
      assertEquals(REPLACE, HeroSpeechRules.decide("d:roller", ULTIMATE, 0, "d:kill", AMBIENT, 1000, 1001, true));
   }

   @Test
   void onlyNotableKillsAndRealLowHealthCrossingsSpeak() {
      assertTrue(HeroSpeechRules.notableKill(true, false, 20.0F), "another player");
      assertTrue(HeroSpeechRules.notableKill(false, true, 20.0F), "a zombie-sized hostile");
      assertFalse(HeroSpeechRules.notableKill(false, true, 8.0F), "a silverfish");
      assertFalse(HeroSpeechRules.notableKill(false, false, 20.0F), "a villager or a civilian");
      assertTrue(HeroSpeechRules.notableKill(false, false, 100.0F), "a big creature");
      assertTrue(HeroSpeechRules.crossedLowHealth(20.0F, 15.0F, 20.0F));
      assertFalse(HeroSpeechRules.crossedLowHealth(20.0F, 20.0F, 20.0F), "a lethal hit is not a remark");
      assertFalse(HeroSpeechRules.crossedLowHealth(5.0F, 1.0F, 20.0F), "already low: no repeat on every hit");
      assertFalse(HeroSpeechRules.crossedLowHealth(20.0F, 2.0F, 20.0F));
      assertFalse(HeroSpeechRules.crossedLowHealth(20.0F, 0.0F, 20.0F));
   }
}

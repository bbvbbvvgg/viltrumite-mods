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
      assertEquals(200 + HeroSpeechRules.FADE_GRACE_TICKS, HeroSpeechRules.lifetimeTicks(200, 3));
      assertEquals(3 * HeroSpeechRules.ESTIMATE_TICKS_PER_FRAGMENT + HeroSpeechRules.FADE_GRACE_TICKS, HeroSpeechRules.lifetimeTicks(0, 3));
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
}

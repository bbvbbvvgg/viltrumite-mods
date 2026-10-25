package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The one rule set both sides use (client prediction, server counter). */
class AirJumpRulesTest {
   @Test
   void sameResetsBothSides() {
      assertFalse(AirJumpRules.resets(false, false, false, false, false, false));
      assertTrue(AirJumpRules.resets(true, false, false, false, false, false), "ground");
      assertTrue(AirJumpRules.resets(false, true, false, false, false, false), "water");
      assertTrue(AirJumpRules.resets(false, false, true, false, false, false), "lava");
      assertTrue(AirJumpRules.resets(false, false, false, true, false, false), "ladder");
      assertTrue(AirJumpRules.resets(false, false, false, false, true, false), "flying");
      assertTrue(AirJumpRules.resets(false, false, false, false, false, true), "riding");
   }

   @Test
   void allow() {
      assertTrue(AirJumpRules.allow(0, 5, false, false, true));
      assertTrue(AirJumpRules.allow(4, 5, false, false, true));
      assertFalse(AirJumpRules.allow(5, 5, false, false, true));
      assertFalse(AirJumpRules.allow(0, 5, true, false, true));
      assertFalse(AirJumpRules.allow(0, 5, false, true, true));
      assertFalse(AirJumpRules.allow(0, 5, false, false, false));
      assertFalse(AirJumpRules.allow(0, 0, false, false, true));
   }
}

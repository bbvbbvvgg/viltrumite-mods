package dev.baranhan.viltrumitecore.client.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import org.junit.jupiter.api.Test;

/** Pure client rules: the engine loop's volume curve and the HUD's colours, layout and cooldown countdown. */
class LegionClientRulesTest {
   @Test
   void engineIsSilentInACrashAndLouderWithThrottle() {
      for (float throttle : new float[]{0.0F, 0.5F, 1.0F}) {
         assertEquals(0.0F, LegionEngineSound.targetVolume(LegionPhase.CRASH, throttle, false, false));
      }

      float idle = LegionEngineSound.targetVolume(LegionPhase.ACTIVE, 0.0F, false, false);
      float full = LegionEngineSound.targetVolume(LegionPhase.ACTIVE, 1.0F, false, false);
      assertTrue(full > idle, "throttle raises the loop");
      assertEquals(full, LegionEngineSound.targetVolume(LegionPhase.ACTIVE, 5.0F, false, false), 1.0E-6F, "throttle is clamped");
      assertEquals(idle, LegionEngineSound.targetVolume(LegionPhase.ACTIVE, -2.0F, false, false), 1.0E-6F, "throttle is clamped");
      assertTrue(LegionEngineSound.targetVolume(LegionPhase.APPROACH, 1.0F, false, false) >= full, "the arrival is the loudest pass");
      for (LegionPhase phase : LegionPhase.values()) {
         float v = LegionEngineSound.targetVolume(phase, 1.0F, false, false);
         assertTrue(v >= 0.0F && v <= 1.0F, phase.name());
      }
   }

   @Test
   void criticalEngineCoughs() {
      float steady = LegionEngineSound.targetVolume(LegionPhase.ACTIVE, 0.7F, true, false);
      float cough = LegionEngineSound.targetVolume(LegionPhase.ACTIVE, 0.7F, true, true);
      assertTrue(cough < steady * 0.5F, "a critical suit's engine drops out on a cough");
      assertEquals(LegionEngineSound.targetVolume(LegionPhase.ACTIVE, 0.7F, false, false),
         LegionEngineSound.targetVolume(LegionPhase.ACTIVE, 0.7F, false, true), 1.0E-6F, "healthy engines never cough");
   }

   @Test
   void durabilityColours() {
      int healthy = LegionHud.barColor(0.9F, false);
      int worn = LegionHud.barColor(0.4F, false);
      assertNotEquals(healthy, worn);
      assertEquals(worn, LegionHud.barColor(0.2F, false), "low but not blinking stays amber");
      assertNotEquals(LegionHud.barColor(0.2F, false), LegionHud.barColor(0.2F, true), "critical blinks red");
      assertEquals(healthy, LegionHud.barColor(0.5F, true), "blink only below a quarter");
   }

   @Test
   void columnStaysLeftOfTheAbilityBarAndItsKeyLabels() {
      for (int guiWidth : new int[]{320, 427, 480, 640, 960}) {
         for (int label : new int[]{0, 6, 18, 40}) {
            int x = LegionHud.columnX(guiWidth, label);
            int panelRight = x + 96 + 3;
            int slotX = guiWidth - 5 - 22 + 3;
            int labelLeft = slotX - label - 6;
            assertTrue(panelRight < labelLeft, "w=" + guiWidth + " label=" + label);
            assertTrue(LegionHud.columnX(guiWidth, label + 10) < x, "a wider key label pushes the column left");
         }
      }
   }

   @Test
   void cooldownCountsDownFromTheLastPacket() {
      assertEquals(200, LegionHud.cooldownLeft(200, 0L));
      assertEquals(120, LegionHud.cooldownLeft(200, 80L));
      assertEquals(0, LegionHud.cooldownLeft(200, 400L), "never negative");
      assertEquals(0, LegionHud.cooldownLeft(200, Long.MAX_VALUE), "no level: nothing to show");
      assertEquals(200, LegionHud.cooldownLeft(200, -5L), "a clock going back does not add time");
   }

   @Test
   void bothMenusFitTheSmallestCommonGuiHeight() {
      // 1280x720 at auto scale 3 and the default 854x480 window are 240 scaled px high; the call grid used to be 254 and lost its top edge.
      assertTrue(LegionScreen.callPanelHeight() <= LegionScreen.MIN_GUI_HEIGHT, "call menu " + LegionScreen.callPanelHeight());
      assertTrue(LegionScreen.commandPanelHeight() <= LegionScreen.MIN_GUI_HEIGHT, "orders menu " + LegionScreen.commandPanelHeight());
   }
}

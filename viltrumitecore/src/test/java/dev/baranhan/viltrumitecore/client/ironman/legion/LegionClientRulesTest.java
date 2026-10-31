package dev.baranhan.viltrumitecore.client.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import org.junit.jupiter.api.Test;

/** Pure client rules: the engine loop's volume curve and the HUD's durability colours. */
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
}

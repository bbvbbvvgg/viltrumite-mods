package dev.baranhan.viltrumitecore.worldevent.aftermath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Stage clock, effect channels, merging and citizen distances of the aftermath (pure rules). */
class AftermathRulesTest {
   private static final int METEOR = AftermathCause.METEOR.bit();
   private static final int CITY = AftermathRules.FLAG_CITY;

   @Test
   void stagesFollowTheClockAndStretchWithTheFactor() {
      assertEquals(AftermathStage.IMPACT, AftermathRules.stage(0, 1.0F));
      assertEquals(AftermathStage.IMPACT, AftermathRules.stage(AftermathRules.IMPACT_TICKS - 1, 1.0F));
      assertEquals(AftermathStage.SMOLDER, AftermathRules.stage(AftermathRules.IMPACT_TICKS, 1.0F));
      assertEquals(AftermathStage.LATE, AftermathRules.stage(AftermathRules.SMOLDER_TICKS, 1.0F));
      assertEquals(AftermathStage.SCAR, AftermathRules.stage(AftermathRules.LATE_TICKS, 1.0F));
      assertEquals(AftermathStage.GONE, AftermathRules.stage(AftermathRules.LATE_TICKS + AftermathRules.SCAR_TICKS, 1.0F));
      // a twice as long zone is still fresh where a normal one already smoulders
      assertEquals(AftermathStage.IMPACT, AftermathRules.stage(AftermathRules.IMPACT_TICKS + 10, 2.0F));
      for (AftermathStage s : AftermathStage.values()) {
         for (float f : new float[]{0.3F, 1.0F, 1.37F, 4.0F}) {
            assertEquals(s, AftermathRules.stage(AftermathRules.stageStart(s, f), f), s + " at " + f);
         }
      }
   }

   @Test
   void durationGrowsWithSeverityAndScale() {
      assertEquals(0.5F, AftermathRules.durationFactor(0.0F, 1.0), 1.0E-6);
      assertEquals(1.5F, AftermathRules.durationFactor(1.0F, 1.0), 1.0E-6);
      assertEquals(3.0F, AftermathRules.durationFactor(1.0F, 2.0), 1.0E-6);
      assertTrue(AftermathRules.durationFactor(0.0F, 0.0) >= 0.05F, "never zero");
      assertTrue(AftermathRules.durationFactor(5.0F, 1.0) <= 1.5F, "severity clamped");
   }

   @Test
   void severityOfBlocksIsMonotonicAndBounded() {
      float last = 0.0F;
      for (int n : new int[]{0, 4, 6, 40, 200, 1600, 100000}) {
         float s = AftermathRules.severityOf(n);
         assertTrue(s >= last && s >= 0.05F && s <= 1.0F, n + " -> " + s);
         last = s;
      }
      assertEquals(1.0F, AftermathRules.severityOf(1600), 1.0E-6);
   }

   @Test
   void mergingKeepsTheStrongerHitAndAddsAShare() {
      assertEquals(0.8F + 0.3F * 0.15F, AftermathRules.mergeSeverity(0.3F, 0.8F), 1.0E-6);
      assertEquals(AftermathRules.mergeSeverity(0.8F, 0.3F), AftermathRules.mergeSeverity(0.3F, 0.8F), 1.0E-6);
      assertEquals(1.0F, AftermathRules.mergeSeverity(1.0F, 1.0F), 1.0E-6);
      assertTrue(AftermathRules.merges(10.0, 12.0F, 6.0F), "overlapping hit merges");
      assertTrue(AftermathRules.merges(3.0, 4.0F, 4.0F), "very close always merges");
      assertFalse(AftermathRules.merges(80.0, 10.0F, 10.0F), "far hit is its own zone");
      assertFalse(AftermathRules.merges(50.0, 40.0F, 40.0F), "merge would pass the radius cap");
   }

   @Test
   void boundCoversBothSpheres() {
      float[] inside = AftermathRules.bound(2.0, 10.0F, 3.0F);
      assertEquals(10.0F, inside[0], 1.0E-6);
      assertEquals(0.0F, inside[1], 1.0E-6);
      float[] swallow = AftermathRules.bound(2.0, 3.0F, 10.0F);
      assertEquals(10.0F, swallow[0], 1.0E-6);
      assertEquals(2.0F, swallow[1], 1.0E-6);
      float[] apart = AftermathRules.bound(20.0, 5.0F, 5.0F);
      assertEquals(15.0F, apart[0], 1.0E-6);
      assertEquals(10.0F, apart[1], 1.0E-6);
      // the shifted sphere reaches both far edges: 10 + 15 = 25 = 20 + 5 and 10 - 15 = -5
      assertEquals(20.0 + 5.0, apart[1] + apart[0], 1.0E-6);
   }

   @Test
   void freshRuinIsLoudSmokyAndDustyThenCalmsDown() {
      float[] fresh = AftermathRules.mix(200, 1.0F, 0.9F, METEOR, CITY);
      float[] smoulder = AftermathRules.mix(3000, 1.0F, 0.9F, METEOR, CITY);
      float[] late = AftermathRules.mix(15000, 1.0F, 0.9F, METEOR, CITY);
      float[] scar = AftermathRules.mix(60000, 1.0F, 0.9F, METEOR, CITY);
      assertTrue(fresh[AftermathRules.SMOKE] > smoulder[AftermathRules.SMOKE] && smoulder[AftermathRules.SMOKE] > late[AftermathRules.SMOKE]);
      assertTrue(fresh[AftermathRules.DUST] > 0.3F && late[AftermathRules.DUST] < 0.05F);
      assertTrue(fresh[AftermathRules.FIRE] > 0.5F && late[AftermathRules.FIRE] == 0.0F);
      assertTrue(fresh[AftermathRules.FOG] > smoulder[AftermathRules.FOG] && late[AftermathRules.FOG] == 0.0F);
      assertTrue(fresh[AftermathRules.RUMBLE] > smoulder[AftermathRules.RUMBLE] && late[AftermathRules.RUMBLE] == 0.0F);
      assertTrue(late[AftermathRules.WIND] > fresh[AftermathRules.WIND], "wind takes over the old ruin");
      assertEquals(0.0F, scar[AftermathRules.SMOKE], 1.0E-6);
      assertEquals(0.0F, scar[AftermathRules.SMOLDER], 1.0E-6);
      for (float v : AftermathRules.mix(AftermathRules.LATE_TICKS + AftermathRules.SCAR_TICKS, 1.0F, 1.0F, METEOR, CITY)) {
         assertEquals(0.0F, v, 1.0E-6, "a gone zone is silent");
      }
      for (long age = 0; age < 200000; age += 997) {
         for (float v : AftermathRules.mix(age, 1.3F, 1.0F, -1, -1)) {
            assertTrue(v >= 0.0F && v <= 1.0F, "channels stay 0..1 at " + age);
         }
      }
   }

   @Test
   void firesAlwaysBurnOut() {
      long out = (long)Math.ceil((AftermathRules.IMPACT_TICKS + (AftermathRules.SMOLDER_TICKS - AftermathRules.IMPACT_TICKS) * 0.6) * 1.5);
      assertEquals(0.0F, AftermathRules.mix(out + 1, 1.5F, 1.0F, METEOR, CITY)[AftermathRules.FIRE], 1.0E-6);
      long ember = (long)Math.ceil((AftermathRules.SMOLDER_TICKS + (AftermathRules.LATE_TICKS - AftermathRules.SMOLDER_TICKS) * 0.6) * 1.5);
      assertEquals(0.0F, AftermathRules.mix(ember + 1, 1.5F, 1.0F, METEOR, CITY)[AftermathRules.SMOLDER], 1.0E-6);
   }

   @Test
   void powerCutKillsTheWiresAndSirensNeedTimeAndATown() {
      float[] live = AftermathRules.mix(3000, 1.0F, 0.8F, AftermathCause.CHITAURI.bit(), CITY);
      float[] dead = AftermathRules.mix(3000, 1.0F, 0.8F, AftermathCause.CHITAURI.bit(), CITY | AftermathRules.FLAG_POWER_OUT);
      assertTrue(dead[AftermathRules.SPARKS] < live[AftermathRules.SPARKS] * 0.1F);
      assertEquals(0.0F, AftermathRules.mix(100, 1.0F, 1.0F, METEOR, CITY)[AftermathRules.SIREN], 1.0E-6, "no siren in the first seconds");
      float town = AftermathRules.mix(3000, 1.0F, 1.0F, METEOR, CITY)[AftermathRules.SIREN];
      float wild = AftermathRules.mix(3000, 1.0F, 1.0F, METEOR, 0)[AftermathRules.SIREN];
      assertTrue(town > 0.5F && wild < town * 0.5F);
   }

   @Test
   void citizensComeBackStepByStep() {
      assertEquals(0.0F, AftermathRules.returnFactor(100, 1.0F), 1.0E-6);
      float last = -1.0F;
      for (long age = 0; age < 25000; age += 500) {
         float r = AftermathRules.returnFactor(age, 1.0F);
         assertTrue(r >= last, "return never goes back at " + age);
         last = r;
      }
      assertEquals(1.0F, AftermathRules.returnFactor(AftermathRules.LATE_TICKS, 1.0F), 1.0E-6);
      assertEquals(28.0F, AftermathRules.hazardRadius(20.0F, 0, 1.0F), 1.0E-6, "fresh ruin plus margin");
      assertTrue(AftermathRules.hazardRadius(20.0F, 9000, 1.0F) < 10.0F);
      assertEquals(0.0F, AftermathRules.hazardRadius(20.0F, AftermathRules.LATE_TICKS, 1.0F), 1.0E-6);
   }

   @Test
   void burstRadiusIsClamped() {
      assertEquals(AftermathRules.MIN_RADIUS, AftermathRules.burstRadius(1, 1, 1), 1.0E-6);
      assertEquals(16.0F, AftermathRules.burstRadius(200, 50, 200), 1.0E-6);
      float mid = AftermathRules.burstRadius(10, 6, 8);
      assertTrue(mid > AftermathRules.MIN_RADIUS && mid < 16.0F);
   }

   @Test
   void ruinsStayInsideTheWorld() {
      assertTrue(AftermathRules.inWorld(0.0, -60.0, 0.0, -64, 320));
      assertTrue(AftermathRules.inWorld(0.0, -78.0, 0.0, -64, 320));
      assertTrue(AftermathRules.inWorld(0.0, 350.0, 0.0, -64, 320));
      assertFalse(AftermathRules.inWorld(51.0, -1971.0, 160.0, -64, 320));
      assertFalse(AftermathRules.inWorld(0.0, 400.0, 0.0, -64, 320));
      assertFalse(AftermathRules.inWorld(Double.NaN, 0.0, 0.0, -64, 320));
      assertFalse(AftermathRules.inWorld(0.0, Double.POSITIVE_INFINITY, 0.0, -64, 320));
   }
}

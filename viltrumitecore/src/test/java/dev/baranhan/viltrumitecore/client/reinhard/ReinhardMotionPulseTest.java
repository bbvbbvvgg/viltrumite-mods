package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReinhardMotionPulseTest {
   private static final long S = 1_000_000_000L;

   private static ReinhardPoses.Pose walking(float walk) {
      return new ReinhardPoses.Pose("idle_holy", 0, walk, 0, null, ReinhardPoses.BodyState.STAND, 0);
   }

   @Test
   void landingPulseGrowsWithFlightTimeAndIgnoresHops() {
      assertEquals(0, ReinhardMotion.landWeight(0.2), "a stair hop is not a landing");
      assertEquals(0.45F, ReinhardMotion.landWeight(ReinhardMotion.LAND_MIN_AIR), 1.0E-6F);
      assertEquals(1.0F, ReinhardMotion.landWeight(1.15), 1.0E-6F);
      assertEquals(1.0F, ReinhardMotion.landWeight(9), 1.0E-6F);
      float last = 0;
      for (double t = ReinhardMotion.LAND_MIN_AIR; t < 1.3; t += 0.05) {
         assertTrue(ReinhardMotion.landWeight(t) >= last);
         last = ReinhardMotion.landWeight(t);
      }
   }

   @Test
   void pulsesTrackOneFlightAndItsLanding() {
      Object owner = new Object();
      long t0 = 100 * S;
      float[] p = ReinhardMotion.pulses(owner, false, -1, t0);
      assertEquals(-1, p[1], "never landed");
      ReinhardMotion.pulses(owner, true, -1, t0 + S / 10);
      p = ReinhardMotion.pulses(owner, true, 0.05, t0 + S / 2);
      assertEquals(0.05F, p[0], 1.0E-6F, "air-step seconds pass through");
      assertEquals(-1, p[1], "no landing while airborne");
      p = ReinhardMotion.pulses(owner, false, -1, t0 + S + S / 10);
      assertEquals(0, p[1], 1.0E-6F, "landing pulse starts on touchdown");
      assertEquals(ReinhardMotion.landWeight(1.0), p[2], 1.0E-6F);
      p = ReinhardMotion.pulses(owner, false, -1, t0 + 2 * S);
      assertEquals(0.9F, p[1], 1.0E-3F, "landing clock runs on");
      // A short hop afterwards does not restart the landing.
      ReinhardMotion.pulses(owner, true, -1, t0 + 3 * S);
      p = ReinhardMotion.pulses(owner, false, -1, t0 + 3 * S + S / 10);
      assertEquals(1.9F + 0.1F, p[1], 1.0E-3F);
      ReinhardMotion.reset(owner);
      assertEquals(-1, ReinhardMotion.pulses(owner, false, -1, t0 + 4 * S)[1], "reset forgets the landing");
   }

   @Test
   void pulsesAppendAfterTheMotionWeights() {
      float[] out = ReinhardMotion.withPulses(new float[]{.1F, .2F, .3F, .4F}, new float[]{-1, .5F, .7F});
      assertArrayEquals(new float[]{.1F, .2F, .3F, .4F, -1, .5F, .7F}, out);
   }

   @Test
   void oneHandedRunCarryOnlyWhileSprintingOnTheGround() {
      assertEquals(1.0F, ReinhardRenderer.runCarry(walking(1), new float[]{0, 1, 0, 0}), 1.0E-6F);
      assertEquals(0.0F, ReinhardRenderer.runCarry(walking(1), new float[]{0, 0, 0, 0}), 1.0E-6F, "walking keeps the two-handed guard");
      assertEquals(0.0F, ReinhardRenderer.runCarry(walking(1), new float[]{0, 1, 1, 0}), 1.0E-6F, "airborne poses own the arms");
      assertEquals(0.0F, ReinhardRenderer.runCarry(walking(0), new float[]{0, 1, 0, 0}), 1.0E-6F, "standing still is the stance");
      assertEquals(0.25F, ReinhardRenderer.runCarry(walking(.5F), new float[]{0, .5F, 0, 0}), 1.0E-6F);
   }
}

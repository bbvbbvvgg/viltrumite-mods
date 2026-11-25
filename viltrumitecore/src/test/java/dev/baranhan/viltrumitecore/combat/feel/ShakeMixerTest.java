package dev.baranhan.viltrumitecore.combat.feel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShakeMixerTest {
   @Test
   void emptyMixerIsStill() {
      ShakeMixer mixer = new ShakeMixer();
      float[] out = new float[]{9, 9, 9, 9};
      mixer.sample(123.4F, out);
      assertArrayEquals(new float[4], out, 0.0F);
   }

   @Test
   void traumaDecaysMonotonicallyAndTheLayerEnds() {
      ShakeMixer mixer = new ShakeMixer();
      mixer.add(ShakeProfile.LIGHT_HIT, 1.0F);
      float last = mixer.trauma(0);
      int ticks = 0;
      while (mixer.layers() > 0 && ticks < 100) {
         mixer.tick();
         ticks++;
         if (mixer.layers() > 0) {
            assertTrue(mixer.trauma(0) < last, "trauma must only go down");
            last = mixer.trauma(0);
         }
      }
      assertEquals(0, mixer.layers());
      assertEquals(7, ticks, "a full light hit lasts 7 ticks (decay 0.16 per tick)");
   }

   @Test
   void theSameProfileMergesIntoOneLayer() {
      ShakeMixer mixer = new ShakeMixer();
      mixer.add(ShakeProfile.HEAVY_HIT, 0.5F);
      mixer.add(ShakeProfile.HEAVY_HIT, 0.5F);
      assertEquals(1, mixer.layers());
      assertEquals(0.675F, mixer.trauma(0), 1.0E-5F);
      for (int i = 0; i < 10; i++) {
         mixer.add(ShakeProfile.HEAVY_HIT, 1.0F);
      }
      assertEquals(1.0F, mixer.trauma(0), 0.0F);
   }

   @Test
   void stackedMaximumShakesStayInsideTheLimits() {
      ShakeMixer mixer = new ShakeMixer();
      for (ShakeProfile profile : ShakeProfile.values()) {
         mixer.add(profile, 1.0F);
      }
      assertTrue(mixer.layers() <= ShakeMixer.MAX_LAYERS);
      float[] out = new float[4];
      for (int i = 0; i < 2000; i++) {
         mixer.sample(i * 0.37F, out);
         for (int axis = 0; axis < 3; axis++) {
            assertTrue(Math.abs(out[axis]) <= ShakeMixer.LIMIT_DEG, "axis " + axis + " = " + out[axis]);
         }
         assertTrue(out[3] >= 0.0F && out[3] <= ShakeMixer.LIMIT_FOV);
      }
   }

   @Test
   void garbageInputIsIgnored() {
      ShakeMixer mixer = new ShakeMixer();
      mixer.add(null, 1.0F);
      mixer.add(ShakeProfile.SLAM, Float.NaN);
      mixer.add(ShakeProfile.SLAM, -1.0F);
      mixer.add(ShakeProfile.SLAM, 0.0F);
      mixer.add(ShakeProfile.SLAM, Float.POSITIVE_INFINITY);
      assertEquals(0, mixer.layers());
   }

   @Test
   void smallHitsStaySmall() {
      ShakeMixer small = new ShakeMixer();
      ShakeMixer big = new ShakeMixer();
      small.add(ShakeProfile.EXPLOSION, 0.3F);
      big.add(ShakeProfile.EXPLOSION, 1.0F);
      float[] a = new float[4];
      float[] b = new float[4];
      float peakSmall = 0.0F;
      float peakBig = 0.0F;
      for (int i = 0; i < 200; i++) {
         small.sample(i * 0.25F, a);
         big.sample(i * 0.25F, b);
         peakSmall = Math.max(peakSmall, Math.abs(a[0]));
         peakBig = Math.max(peakBig, Math.abs(b[0]));
      }
      // trauma squared: 0.3 trauma gives at most ~9 % of the full shake
      assertTrue(peakSmall < peakBig * 0.12F, peakSmall + " vs " + peakBig);
   }

   @Test
   void sampleIsDeterministic() {
      ShakeMixer a = new ShakeMixer();
      ShakeMixer b = new ShakeMixer();
      a.add(ShakeProfile.ULTIMATE, 0.8F);
      b.add(ShakeProfile.ULTIMATE, 0.8F);
      float[] x = new float[4];
      float[] y = new float[4];
      a.sample(42.5F, x);
      b.sample(42.5F, y);
      assertArrayEquals(x, y, 0.0F);
   }

   @Test
   void unknownWireIdsFallBack() {
      assertEquals(ShakeProfile.LIGHT_HIT, ShakeProfile.byId(-1));
      assertEquals(ShakeProfile.LIGHT_HIT, ShakeProfile.byId(99));
      assertEquals(ShakeProfile.SLAM, ShakeProfile.byId(ShakeProfile.SLAM.ordinal()));
   }

   @Test
   void hitWeightsClimbAndRespectTheHitStopCap() {
      HitWeight previous = null;
      for (HitWeight weight : HitWeight.values()) {
         assertTrue(weight.hitStopTicks >= 0 && weight.hitStopTicks <= 10, weight + " hit-stop");
         if (previous != null) {
            assertTrue(weight.hitStopTicks >= previous.hitStopTicks, weight + " must not stop shorter than " + previous);
            assertTrue(weight.wave >= previous.wave);
         }
         previous = weight;
      }
   }
}

package dev.baranhan.viltrumitecore.client.city.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * One-shot gain clamp of {@link CityPowerAudio} (T10 of reports/city-map.md): a city-wide disaster stacks
 * several epicentre one-shots (transformer blow + low impact, arcs, cascade layers), and before the manual
 * 0.7 / 0.45 tuning the stacked voices peaked over full scale (0 dBFS) next to the hit. The clamp is pure
 * code here: whatever the cues ask, the sum of the voices played at one moment never exceeds full scale
 * and keeps its shape, so the tuning cannot drift back into clipping.
 */
class CityPowerAudioTest {
   /** The measured worst cases: what actually stacked in game near the epicentre. */
   private static final float[][] BLOW_STACK = {{0.7F, 0.45F}};
   private static final float[][] BLOW_AND_ARCS = {{0.7F, 0.45F}, {0.7F, 0.7F}, {0.7F, 0.7F}};
   private static final float[][] CASCADE_STACK = {{1.0F, 0.8F}, {0.7F, 0.7F}};

   @Test
   void twoVoicesStayUnderFullScale() {
      for (float[][] stack : new float[][][]{BLOW_STACK, BLOW_AND_ARCS, CASCADE_STACK}) {
         float[] requests = new float[stack.length * stack[0].length];
         for (int i = 0; i < stack.length; i++) {
            for (int j = 0; j < stack[i].length; j++) {
               requests[i * stack[i].length + j] = stack[i][j];
            }
         }

         float sum = 0.0F;
         for (float g : CityPowerAudio.oneshotGain(requests)) {
            sum += g;
         }

         assertTrue(sum <= 1.0F + 1.0E-6F, "stacked gain " + sum + " clips");
      }
   }

   @Test
   void scaleIsFairAndOrderIndependent() {
      float[] blow = CityPowerAudio.oneshotGain(0.7F, 0.45F);
      assertEquals(0.60869565F, blow[0], 1.0E-5F);
      assertEquals(0.39130435F, blow[1], 1.0E-5F);
      assertEquals(1.0F, blow[0] + blow[1], 1.0E-5F);
      float[] arcsFirst = CityPowerAudio.oneshotGain(0.7F, 0.7F, 0.7F, 0.45F);
      assertEquals(1.0F, arcsFirst[0] + arcsFirst[1] + arcsFirst[2] + arcsFirst[3], 1.0E-4F);
      assertEquals(arcsFirst[0], arcsFirst[1], 1.0E-6F);
      assertEquals(arcsFirst[0], arcsFirst[2], 1.0E-6F);
      // the loudest voice keeps its share, the quiet one scales down with it
      assertTrue(arcsFirst[3] < arcsFirst[0]);
      assertTrue(arcsFirst[3] > 0.1F);
      float[] blowFirst = CityPowerAudio.oneshotGain(0.45F, 0.7F, 0.7F, 0.7F);
      assertEquals(arcsFirst[3], blowFirst[0], 1.0E-6F);
      assertEquals(arcsFirst[0], blowFirst[1], 1.0E-6F);
   }

   @Test
   void quietVoicesAreQuietAndLoudOnesLead() {
      float[] scaled = CityPowerAudio.oneshotGain(0.9F, 0.05F, 0.95F);
      assertEquals(0.9F * (1.0F / 1.9F), scaled[0], 1.0E-5F);
      assertEquals(0.05F * (1.0F / 1.9F), scaled[1], 1.0E-5F);
      assertEquals(0.95F * (1.0F / 1.9F), scaled[2], 1.0E-5F);
      float[] single = CityPowerAudio.oneshotGain(0.8F);
      assertEquals(0.8F, single[0], 1.0E-5F);
      float[] nothing = CityPowerAudio.oneshotGain(0.0F, 0.0F);
      assertEquals(0.0F, nothing[0], 1.0E-5F);
      assertEquals(0.0F, nothing[1], 1.0E-5F);
   }

   @Test
   void stackedMaxNeverExceedsOne() {
      float worst = 0.0F;
      for (int n = 1; n <= 12; n++) {
         float[] gains = new float[n];
         java.util.Arrays.fill(gains, 1.0F);
         float sum = 0.0F;
         for (float g : CityPowerAudio.oneshotGain(gains)) {
            sum += g;
         }

         assertTrue(sum <= 1.0F + 1.0E-6F, "n=" + n + " sums to " + sum);
         worst = Math.max(worst, sum);
      }

      assertEquals(1.0F, worst, 1.0E-6F);
   }

   @Test
   void curveHalfLoudnessAndClamp() {
      assertEquals(1.0F, CityPowerAudio.curve(0.0, 26.0), 1.0E-5F);
      assertEquals(0.5F, CityPowerAudio.curve(26.0, 26.0), 1.0E-4F);
      assertTrue(CityPowerAudio.curve(60.0, 26.0) < CityPowerAudio.curve(30.0, 26.0));
      // a negative distance clamps to 0, which is the loudest case, not silence
      assertEquals(1.0F, CityPowerAudio.curve(-3.0, 26.0), 1.0E-5F);
      assertTrue(CityPowerAudio.curve(260.0, 26.0) >= 0.0F && CityPowerAudio.curve(260.0, 26.0) <= 1.0F);
   }
}

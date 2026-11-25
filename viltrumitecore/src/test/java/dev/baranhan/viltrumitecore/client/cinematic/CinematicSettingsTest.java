package dev.baranhan.viltrumitecore.client.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Serialisation of the cinematic comfort settings (wave1-22). No Minecraft or config directory involved. */
class CinematicSettingsTest {
   @Test
   void roundTripKeepsEveryField() {
      CinematicSettings original = new CinematicSettings();
      original.reduceCameraMotion = true;
      original.disableCinematicCamera = true;
      original.watchOthersCinematics = true;
      original.impactFrames = false;
      original.flashIntensity = 0.42F;

      CinematicSettings back = CinematicSettings.read(original.write());

      assertEquals(original.reduceCameraMotion, back.reduceCameraMotion);
      assertEquals(original.disableCinematicCamera, back.disableCinematicCamera);
      assertEquals(original.watchOthersCinematics, back.watchOthersCinematics);
      assertEquals(original.impactFrames, back.impactFrames);
      assertEquals(original.flashIntensity, back.flashIntensity, 1.0E-6F);
   }

   @Test
   void garbageAndEmptyInputFallBackToDefaults() {
      CinematicSettings fromGarbage = CinematicSettings.read("not json at all");
      CinematicSettings defaults = new CinematicSettings();
      assertNotNull(fromGarbage);
      assertEquals(defaults.reduceCameraMotion, fromGarbage.reduceCameraMotion);
      assertEquals(defaults.disableCinematicCamera, fromGarbage.disableCinematicCamera);
      assertEquals(defaults.watchOthersCinematics, fromGarbage.watchOthersCinematics);
      assertEquals(defaults.impactFrames, fromGarbage.impactFrames);
      assertEquals(defaults.flashIntensity, fromGarbage.flashIntensity, 0.0F);

      CinematicSettings fromNull = CinematicSettings.read("null");
      assertNotNull(fromNull);
      assertEquals(defaults.impactFrames, fromNull.impactFrames);

      CinematicSettings fromEmptyObject = CinematicSettings.read("{}");
      assertEquals(defaults.flashIntensity, fromEmptyObject.flashIntensity, 0.0F);
   }

   @Test
   void missingFieldsKeepJavaDefaults() {
      CinematicSettings partial = CinematicSettings.read("{\"reduceCameraMotion\": true}");
      assertTrue(partial.reduceCameraMotion);
      assertFalse(partial.disableCinematicCamera);
      assertFalse(partial.watchOthersCinematics);
      assertTrue(partial.impactFrames);
      assertEquals(1.0F, partial.flashIntensity, 0.0F);
   }

   @Test
   void unknownFieldsAreIgnored() {
      CinematicSettings parsed = CinematicSettings.read("{\"reduceCameraMotion\": true, \"nonexistent\": 7}");
      assertTrue(parsed.reduceCameraMotion);
   }

   @Test
   void flashIsClampedToZeroOne() {
      CinematicSettings high = new CinematicSettings();
      high.flashIntensity = 5.0F;
      assertEquals(1.0F, high.flash(), 0.0F);
      CinematicSettings negative = new CinematicSettings();
      negative.flashIntensity = -3.0F;
      assertEquals(0.0F, negative.flash(), 0.0F);
      CinematicSettings nan = new CinematicSettings();
      nan.flashIntensity = Float.NaN;
      assertEquals(0.0F, nan.flash(), 0.0F);
      CinematicSettings half = new CinematicSettings();
      half.flashIntensity = 0.5F;
      assertEquals(0.5F, half.flash(), 1.0E-6F);
   }

   @Test
   void gettersReflectTheFields() {
      CinematicSettings s = new CinematicSettings();
      assertTrue(s.cinematicCameraEnabled());
      assertFalse(s.watchOthers());
      assertFalse(s.reducedMotion());
      assertTrue(s.impactFramesEnabled());

      s.disableCinematicCamera = true;
      s.watchOthersCinematics = true;
      s.reduceCameraMotion = true;
      s.impactFrames = false;
      assertFalse(s.cinematicCameraEnabled());
      assertTrue(s.watchOthers());
      assertTrue(s.reducedMotion());
      assertFalse(s.impactFramesEnabled());
   }
}

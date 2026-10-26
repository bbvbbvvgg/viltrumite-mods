package dev.baranhan.viltrumitecore.client.reinhard.saw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography;
import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;

/** Item 19, client side: the camera always comes back, the hum rises with the shell and dies with it. */
class SawSphereClientTest {
   @Test void theSavedCameraIsRestoredAndNothingIsInventedWithoutOne() {
      for (CameraType saved : CameraType.values()) assertEquals(saved, SawSphereClient.restored(saved, CameraType.THIRD_PERSON_BACK));
      assertEquals(CameraType.THIRD_PERSON_FRONT, SawSphereClient.restored(null, CameraType.THIRD_PERSON_FRONT), "a scene that never forced the view leaves it");
   }

   @Test void humRisesHoldsAndReleasesAtTheShatter() {
      assertTrue(SawHumSound.volume(0) < .01F, "starts silent");
      assertEquals(.9F, SawHumSound.volume(100), 1E-6F, "full through the cut");
      assertTrue(SawHumSound.volume(SawChoreography.SHATTER + 2) < .01F, "gone with the shell");
      assertEquals(.6F, SawHumSound.pitch(0), 1E-6F);
      assertTrue(SawHumSound.pitch(100) > 1.0F && SawHumSound.pitch(100) <= 2.0F, "risen and breathing with the spin");
      float previous = 0;
      for (int t = 0; t <= 30; t++) {
         assertTrue(SawHumSound.volume(t) >= previous, "a rising swell " + t);
         previous = SawHumSound.volume(t);
      }
   }
}

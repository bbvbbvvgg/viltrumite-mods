package dev.baranhan.viltrumitecore.client.cinematic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Truth table of {@link CinematicVisibility}: who may ever take the camera (wave1-22). */
class CinematicVisibilityTest {
   private static boolean expect(boolean isCaster, boolean watchOthers, boolean cameraEnabled, boolean otherOwner) {
      return (isCaster || watchOthers) && cameraEnabled && !otherOwner;
   }

   @Test
   void everyCombinationFollowsTheRule() {
      for (boolean isCaster : new boolean[]{false, true}) {
         for (boolean watchOthers : new boolean[]{false, true}) {
            for (boolean cameraEnabled : new boolean[]{false, true}) {
               for (boolean otherOwner : new boolean[]{false, true}) {
                  boolean got = CinematicVisibility.shouldPlay(isCaster, watchOthers, cameraEnabled, otherOwner);
                  assertTrue(got == expect(isCaster, watchOthers, cameraEnabled, otherOwner),
                     "isCaster=" + isCaster + " watchOthers=" + watchOthers + " cameraEnabled=" + cameraEnabled
                        + " otherOwner=" + otherOwner);
               }
            }
         }
      }
   }

   @Test
   void theCasterPlaysWhenTheCameraIsFree() {
      assertTrue(CinematicVisibility.shouldPlay(true, false, true, false));
   }

   @Test
   void aNonCasterWithoutOptInNeverSeesTheScene() {
      assertFalse(CinematicVisibility.shouldPlay(false, false, true, false));
   }

   @Test
   void theComfortSettingDisablesTheCameraEvenForTheCaster() {
      assertFalse(CinematicVisibility.shouldPlay(true, true, false, false));
   }

   @Test
   void anotherHeroCameraWinsOverBoth() {
      assertFalse(CinematicVisibility.shouldPlay(true, true, true, true));
   }
}

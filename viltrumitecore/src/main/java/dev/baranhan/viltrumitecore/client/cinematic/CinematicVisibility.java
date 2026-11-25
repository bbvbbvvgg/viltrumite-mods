package dev.baranhan.viltrumitecore.client.cinematic;

/**
 * The whole visibility rule of the cinematic camera as one pure function (wave1-22). No Minecraft
 * classes, so the truth table is unit tested. The camera plays only when the local player is either
 * the caster or has opted in to watch other players' scenes, his comfort setting allows the camera,
 * and no other hero's own camera is already holding the view. A non-caster without the opt-in never
 * sees the scene: we never steal an opponent's camera in a fight.
 */
public final class CinematicVisibility {
   private CinematicVisibility() {
   }

   public static boolean shouldPlay(boolean isCaster, boolean watchOthers, boolean cameraEnabled, boolean otherCameraOwnerActive) {
      if (otherCameraOwnerActive || !cameraEnabled) {
         return false;
      }
      return isCaster || watchOthers;
   }
}

package dev.baranhan.viltrumitecore.client.cinematic;

/**
 * The pure decision rules of the cinematic camera (wave1-22), as static functions on plain booleans.
 * No Minecraft classes, so both are unit tested:
 * <ul>
 *   <li>{@link #shouldPlay} — may the scene take the view at all: the local player is the caster or
 *   opted in to watch other players' scenes, his comfort setting allows the camera, and no other
 *   hero's own camera already holds the view. A non-caster without the opt-in never sees the scene:
 *   we never steal an opponent's camera in a fight.</li>
 *   <li>{@link #capturesCameraType} — does this frame take the view for the first time, so the
 *   player's own camera type must be kept for the return. A second scene packet mid-scene must not
 *   capture the FIRST_PERSON we forced, or the player would come back in the wrong person.</li>
 * </ul>
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

   /** True when the player's current camera type must be captured for the return. */
   public static boolean capturesCameraType(boolean alreadyHoldingView, boolean hasSavedType) {
      return !alreadyHoldingView && !hasSavedType;
   }
}

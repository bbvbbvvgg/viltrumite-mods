package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Riding camera of the Hell Cycle: the third-person camera pulls back to frame the long bike (HellCycleCameraMixin
 * asks {@link #zoom}), the field of view opens a little with the speed and the Hell Dash, and the horizon rolls a
 * touch with the lean (more in first person, where it sells the corner).
 */
public final class GhostRiderCamera {
   /** Third-person distance on the bike (vanilla 4). */
   public static final double RIDE_DISTANCE = 5.6;
   private static float roll;
   private static float fovBoost;

   private GhostRiderCamera() {
   }

   @Nullable
   static HellCycleEntity bikeOf(@Nullable Entity e) {
      return e != null && e.getVehicle() instanceof HellCycleEntity bike ? bike : null;
   }

   /** Camera.getMaxZoom argument: further back while the camera entity rides a Hell Cycle. */
   public static double zoom(double vanilla) {
      Minecraft mc = Minecraft.getInstance();
      return bikeOf(mc.getCameraEntity()) != null ? Math.max(vanilla, RIDE_DISTANCE) : vanilla;
   }

   /** Pure: FOV multiplier for a speed fraction and the dash. */
   public static float fovFactor(float speedF, boolean dashing) {
      return 1.0F + 0.08F * Mth.clamp(speedF, 0.0F, 1.0F) + (dashing ? 0.07F : 0.0F);
   }

   /** Pure: camera roll (deg) for a lean (deg, left positive) in first or third person. */
   public static float rollFor(float lean, boolean firstPerson) {
      return -lean * (firstPerson ? 0.3F : 0.12F);
   }

   public static void onFov(ViewportEvent.ComputeFov event) {
      HellCycleEntity bike = bikeOf(event.getCamera().getEntity());
      float target = bike == null ? 0.0F : fovFactor((float)(bike.visualSpeed / HellCycleRules.MAX_SPEED), bike.dashing()) - 1.0F;
      fovBoost += (target - fovBoost) * 0.15F;
      if (Math.abs(fovBoost) > 1.0E-3F && event.usedConfiguredFov()) {
         event.setFOV(event.getFOV() * (1.0F + fovBoost));
      }
   }

   public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
      HellCycleEntity bike = bikeOf(event.getCamera().getEntity());
      float target = 0.0F;
      if (bike != null) {
         float lean = Mth.lerp((float)event.getPartialTick(), bike.leanO, bike.lean);
         target = rollFor(lean, !event.getCamera().isDetached());
      }
      roll += (target - roll) * 0.2F;
      if (Math.abs(roll) > 0.01F) {
         event.setRoll(event.getRoll() + roll);
      }
   }
}

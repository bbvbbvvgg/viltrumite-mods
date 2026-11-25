package dev.baranhan.viltrumitecore.client.cinematic;

import dev.baranhan.viltrumitecore.client.dio.RollerCinema;
import dev.baranhan.viltrumitecore.client.reinhard.finalpunch.FinalPunchClient;
import dev.baranhan.viltrumitecore.client.sinclair.stage.StageCamera;
import dev.baranhan.viltrumitecore.client.thor.scene.ThorSceneCamera;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * The one place where the cinematic camera asks the existing hero cameras whether they already hold
 * the view (wave1-22). Two kinds exist, and the cinematic must never fight either:
 * <ul>
 *   <li><b>entity owners</b> replace the camera entity (Jinwoo, Captain, Regulus, Thanos, the wish
 *   scene, Venom's host possession): detected generically, without naming a hero;</li>
 *   <li><b>angle owners</b> rewrite {@code Camera.setup} through a mixin and never touch the camera
 *   entity (DIO's Road Roller, Thor's fatality/teleport, Reinhard's Final Punch, Sinclair's stage,
 *   the Hell Cycle ride), so each is asked through its own public predicate.</li>
 * </ul>
 * Keeping the coupling here means the rest of the cinematic core never names a hero.
 */
public final class CameraOwners {
   private CameraOwners() {
   }

   /** True when some other camera system holds this frame's view. */
   public static boolean anyActive(Minecraft mc) {
      Entity cam = mc.getCameraEntity();
      if (cam != null && cam != mc.player) {
         return true;
      }
      if (cam != null && cam.getVehicle() instanceof HellCycleEntity) {
         return true;
      }
      return RollerCinema.active()
         || ThorSceneCamera.active()
         || FinalPunchClient.cinematic()
         || StageCamera.staged();
   }
}

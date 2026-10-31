package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * The caster's screen during the scene: letterbox bars on the clean cinematic HUD
 * (CinematicHud), and the shared dash_impact post pass fed by {@link #grade}: the cold grade and
 * vignette of the silence (visual slow time), and the screen shock band (space distortion) of
 * every Phase IV ring pulse around the fist and of the impact around the jaw.
 */
public final class FinalPunchScreen {
   private FinalPunchScreen() {
   }

   /** CinematicHud layer: black bars in and out with the camera. */
   public static void bars(GuiGraphics graphics, float partial, int width, int height) {
      FinalPunchClient.Scene scene = FinalPunchClient.local();
      if (scene == null) return;
      double k = FinalPunchCues.letterbox(scene.time(partial)) * scene.fade(partial);
      int bar = (int) Math.round(height * 0.105 * k);
      if (bar <= 0) return;
      graphics.fill(0, 0, width, bar, 0xFF000000);
      graphics.fill(0, height - bar, width, height, 0xFF000000);
   }

   /**
    * Post-pass values for this frame: {grey, shock radius, shock centre x, y, vignette}; all zero
    * without a scene. Read by GameRendererDashMixin next to DIO's and Thanos' values (max).
    */
   public static float[] grade(float partial) {
      float[] out = new float[5];
      if (!FinalPunchClient.any()) return out;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return out;
      Vec3 view = mc.gameRenderer.getMainCamera().getPosition();
      for (FinalPunchClient.Scene scene : FinalPunchClient.all()) {
         double distance = view.distanceTo(scene.feet());
         boolean own = scene.isLocalCaster() || mc.player.getId() == scene.targetId;
         float near = own ? 1.0F : (float) Math.max(0.0, 1.0 - distance / 32.0);
         if (near <= 0) continue;
         double t = scene.time(partial);
         float fade = scene.fade(partial) * near;
         out[0] = Math.max(out[0], FinalPunchCues.grey(t) * fade);
         out[4] = Math.max(out[4], FinalPunchCues.vignette(t) * fade);
         float shock = FinalPunchCues.shock(t) * (fade > 0.01F ? 1 : 0);
         if (shock > 0 && out[1] == 0) {
            Vec3 at = t >= dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules.CONTACT && scene.impactAt != null ? scene.impactAt : scene.fist(partial);
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            ScreenProjector.Point p = ScreenProjector.project(at, w, h);
            if (p != null && !p.behind() && w > 0 && h > 0) {
               out[1] = shock;
               out[2] = p.x() / w;
               out[3] = 1.0F - p.y() / h;
            }
         }
      }
      return out;
   }
}

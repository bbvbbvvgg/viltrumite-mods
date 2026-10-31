package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * The caster's cinematic camera ({@code ReinhardSceneCameraMixin} on Camera.setup TAIL): blends
 * from the player's own view into the {@link FinalPunchShots} plan, clips every shot against
 * walls, and blends back to the player's view (first or third person, whatever he had) at the
 * end. A cancel blends back at once. Only the local caster's view is ever moved.
 */
public final class FinalPunchCamera {
   /** One camera placement. */
   public record View(Vec3 pos, float yaw, float pitch) {
   }

   private FinalPunchCamera() {
   }

   @Nullable
   public static View apply(Entity cameraEntity, float partial, View vanilla) {
      Minecraft mc = Minecraft.getInstance();
      FinalPunchClient.Scene scene = FinalPunchClient.local();
      if (scene == null || mc.level == null || cameraEntity != mc.player) return null;
      double t = scene.time(partial);
      float fade = scene.fade(partial);
      double w = FinalPunchCues.smooth(t / FinalPunchCues.BLEND_IN) * (1.0 - FinalPunchCues.smooth((t - FinalPunchRules.CAMERA_OUT) / FinalPunchCues.BLEND_OUT));
      w *= FinalPunchCues.smooth(fade);
      if (w <= 0.001 || t >= FinalPunchRules.LENGTH) return null;

      Vec3 feet = mc.player.getPosition(partial);
      LivingEntity target = scene.target();
      FinalPunchShots.Frame frame = new FinalPunchShots.Frame(feet, scene.yaw, scene.fistSmooth(), jaw(scene, target, partial), target == null ? null
         : target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0));
      FinalPunchShots.Shot shot = FinalPunchShots.at(t, frame);
      Vec3 pos = clip(mc.level, shot.focus(), shot.pos());
      float yaw = FinalPunchShots.yaw(pos, shot.focus());
      float pitch = FinalPunchShots.pitch(pos, shot.focus());
      if (w >= 0.999) return new View(pos, yaw, pitch);
      return new View(vanilla.pos().lerp(pos, w), vanilla.yaw() + Mth.wrapDegrees(yaw - vanilla.yaw()) * (float) w,
         Mth.lerp((float) w, vanilla.pitch(), pitch));
   }

   private static Vec3 jaw(FinalPunchClient.Scene scene, LivingEntity target, float partial) {
      if (target == null || scene.time(partial) < FinalPunchRules.CONTACT) return scene.jawAt;
      return scene.impactAt != null ? scene.impactAt : scene.jawAt;
   }

   static Vec3 clip(Level level, Vec3 focus, Vec3 want) {
      BlockHitResult hit = level.clip(new ClipContext(focus, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
      if (hit.getType() != HitResult.Type.BLOCK) return want;
      Vec3 dir = want.subtract(focus);
      double length = dir.length();
      double keep = Math.max(0.35, focus.distanceTo(hit.getLocation()) - 0.25);
      return length < 1.0E-4 ? want : focus.add(dir.scale(Math.min(length, keep) / length));
   }

   /** FOV: tight in the silence, a punch-out at the impact (caster only). */
   public static void onFov(ViewportEvent.ComputeFov event) {
      FinalPunchClient.Scene scene = FinalPunchClient.local();
      if (scene == null) return;
      double t = scene.time((float) event.getPartialTick());
      if (t >= FinalPunchRules.LENGTH) return;
      double w = FinalPunchCues.smooth(t / FinalPunchCues.BLEND_IN) * (1.0 - FinalPunchCues.smooth((t - FinalPunchRules.CAMERA_OUT) / FinalPunchCues.BLEND_OUT)) * scene.fade((float) event.getPartialTick());
      double scale = 1.0 + (FinalPunchCues.fov(t) - 1.0) * w;
      event.setFOV(event.getFOV() * scale);
   }
}

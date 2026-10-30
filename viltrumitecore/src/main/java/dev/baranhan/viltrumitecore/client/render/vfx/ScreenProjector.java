package dev.baranhan.viltrumitecore.client.render.vfx;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import com.mojang.math.Axis;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World → GUI coordinates for HUD hints (brackets, edge arrows). Captures the
 * camera pose and projection of the last rendered level frame; the GUI pass
 * of the same frame projects with them.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class ScreenProjector {
   private static final Matrix4f VIEW_PROJ = new Matrix4f();
   private static Vec3 camera = Vec3.ZERO;
   private static boolean valid;
   /** Final camera roll of the current frame (shakes/jolts/leans add it in ComputeCameraAngles). */
   private static float roll;
   /** The level the matrices belong to: a dimension change or relog must not reuse them. */
   @Nullable
   private static Object capturedLevel;

   private ScreenProjector() {
   }

   /** GUI position; behind = the point is behind the camera (x/y keep its side for edge arrows). */
   public record Point(float x, float y, boolean behind) {
      public boolean onScreen(int width, int height) {
         return !this.behind && this.x >= 0 && this.y >= 0 && this.x <= width && this.y <= height;
      }
   }

   /** LOWEST: runs after every roll contributor (CameraShake, WorldCutJolt, JinwooUltScene). */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      roll = event.getRoll();
   }

   /** Camera roll of the frame being rendered, degrees. */
   public static float roll() {
      return roll;
   }

   /**
    * Camera view rotation exactly as GameRenderer builds it (roll, pitch, yaw+180). Needed
    * because Forge fires AFTER_LEVEL with the projection pose stack, not the view stack.
    */
   public static Matrix4f viewRotation(float roll, float pitch, float yaw) {
      return new Matrix4f().rotate(Axis.ZP.rotationDegrees(roll)).rotate(Axis.XP.rotationDegrees(pitch)).rotate(Axis.YP.rotationDegrees(yaw + 180.0F));
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      // Only the main view: a shader shadow pass (Oculus) or another off-screen level render would
      // leave its sun/ortho matrices here and the GUI brackets would point at empty space.
      if (!captures(ShaderCompat.isShadowPass(), event.getCamera() == mc.gameRenderer.getMainCamera())) {
         return;
      }

      // AFTER_LEVEL's pose stack is the projection (+bob) stack, not the view: build the view here.
      VIEW_PROJ.set(event.getProjectionMatrix()).mul(viewRotation(roll, event.getCamera().getXRot(), event.getCamera().getYRot()));
      camera = event.getCamera().getPosition();
      capturedLevel = mc.level;
      valid = true;
   }

   /** Pure: the frame that may feed the GUI projection. */
   static boolean captures(boolean shadowPass, boolean mainCamera) {
      return !shadowPass && mainCamera;
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      valid = false;
      capturedLevel = null;
   }

   /**
    * Pure: GUI rectangle [x0, y0, x1, y1] around projected corners, or null when a corner is
    * behind the camera (the caller falls back to an edge arrow).
    */
   @Nullable
   public static float[] bounds(Point... corners) {
      float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
      for (Point p : corners) {
         if (p == null || p.behind()) {
            return null;
         }
         x0 = Math.min(x0, p.x());
         y0 = Math.min(y0, p.y());
         x1 = Math.max(x1, p.x());
         y1 = Math.max(y1, p.y());
      }
      return corners.length == 0 ? null : new float[]{x0, y0, x1, y1};
   }

   /** The eight corners of a world box projected with this frame's matrices (null if no frame yet). */
   @Nullable
   public static Point[] corners(net.minecraft.world.phys.AABB box, int guiWidth, int guiHeight) {
      Point[] out = new Point[8];
      for (int i = 0; i < 8; i++) {
         out[i] = project(new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ),
            guiWidth, guiHeight);
         if (out[i] == null) {
            return null;
         }
      }
      return out;
   }

   @Nullable
   public static Point project(Vec3 world, int guiWidth, int guiHeight) {
      Minecraft mc = Minecraft.getInstance();
      if (!valid || mc.level == null || mc.level != capturedLevel) {
         return null;
      }

      Vector4f v = new Vector4f((float)(world.x - camera.x), (float)(world.y - camera.y), (float)(world.z - camera.z), 1.0F);
      VIEW_PROJ.transform(v);
      boolean behind = v.w <= 1.0E-3F;
      float w = Math.abs(v.w) < 1.0E-3F ? 1.0E-3F : Math.abs(v.w);
      // Dividing by |w| keeps the side of a point behind the camera (signed w would mirror it).
      float nx = v.x / w;
      float ny = v.y / w;
      return new Point((nx * 0.5F + 0.5F) * guiWidth, (1.0F - (ny * 0.5F + 0.5F)) * guiHeight, behind);
   }
}

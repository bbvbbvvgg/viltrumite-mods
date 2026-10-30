package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Pixel-style shape helpers shared by the Regulus VFX managers. Everything is
 * POSITION_COLOR quads in camera-relative space — the same "paint pixel"
 * vocabulary the existing managers use (skill §8).
 */
public final class PixelVfx {
   private PixelVfx() {
   }

   /**
    * The exact view rotation of the level frame (roll included: CameraShake, WorldCutJolt and
    * the Jinwoo lean add roll in ComputeCameraAngles, which pitch/yaw alone miss, so world
    * markers drawn with {@link #rotateCamera} slid off their entities during shakes).
    */
   public static void levelView(PoseStack stack, net.minecraftforge.client.event.RenderLevelStageEvent event) {
      stack.mulPoseMatrix(event.getPoseStack().last().pose());
   }

   public static void rotateCamera(PoseStack stack, float pitch, float yaw) {
      stack.mulPose(Axis.XP.rotationDegrees(pitch));
      stack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
   }

   /** The same white/coloured paint-pixel style in a GUI scene, batched by GuiGraphics. */
   public static void guiPixel(net.minecraft.client.gui.GuiGraphics gui, float x, float y, float radius, int r, int g, int b, int alpha) {
      if(alpha<=0) return;
      int color=(Math.min(255,alpha)<<24)|(r<<16)|(g<<8)|b;
      gui.fill((int)Math.floor(x-radius),(int)Math.floor(y-radius),(int)Math.ceil(x+radius),(int)Math.ceil(y+radius),color);
   }

   /** Billboard pixel at a world position, facing the camera via its basis. */
   public static void billboardPixel(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Vec3 pos, float size, int r, int g, int b, int alpha) {
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      float cx = (float)(pos.x - cameraPos.x);
      float cy = (float)(pos.y - cameraPos.y);
      float cz = (float)(pos.z - cameraPos.z);
      float lx = left.x * size;
      float ly = left.y * size;
      float lz = left.z * size;
      float ux = up.x * size;
      float uy = up.y * size;
      float uz = up.z * size;
      buffer.vertex(cx - lx - ux, cy - ly - uy, cz - lz - uz).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx + lx - ux, cy + ly - uy, cz + lz - uz).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx + lx + ux, cy + ly + uy, cz + lz + uz).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx - lx + ux, cy - ly + uy, cz - lz + uz).color(r, g, b, alpha).endVertex();
   }

   /** Dotted beam between two world points. */
   public static void beamDots(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Vec3 from, Vec3 to, float spacing, float dotSize, int r, int g, int b, int alpha) {
      double length = from.distanceTo(to);
      int steps = Math.max(2, (int)(length / (double)spacing));
      for (int i = 0; i <= steps; i++) {
         Vec3 point = from.lerp(to, (double)i / (double)steps);
         billboardPixel(buffer, cameraPos, camera, point, dotSize, r, g, b, alpha);
      }
   }

   /** Dotted wireframe box around an entity bounds. */
   public static void boxOutline(BufferBuilder buffer, Vec3 cameraPos, Camera camera, AABB box, float dotSize, int r, int g, int b, int alpha) {
      Vec3[] corners = {
         new Vec3(box.minX, box.minY, box.minZ),
         new Vec3(box.maxX, box.minY, box.minZ),
         new Vec3(box.maxX, box.minY, box.maxZ),
         new Vec3(box.minX, box.minY, box.maxZ),
         new Vec3(box.minX, box.maxY, box.minZ),
         new Vec3(box.maxX, box.maxY, box.minZ),
         new Vec3(box.maxX, box.maxY, box.maxZ),
         new Vec3(box.minX, box.maxY, box.maxZ)
      };
      int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
      for (int[] edge : edges) {
         Vec3 a = corners[edge[0]];
         Vec3 c = corners[edge[1]];
         int steps = Math.max(2, (int)(a.distanceTo(c) / 0.25));
         for (int i = 0; i <= steps; i++) {
            billboardPixel(buffer, cameraPos, camera, a.lerp(c, (double)i / (double)steps), dotSize, r, g, b, alpha);
         }
      }
   }

   /** Static hemisphere motes; shimmer changes opacity, not world position. */
   public static void domeShell(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Vec3 center, float radius, float timeSeconds, int r, int g, int b, int alpha) {
      shell(buffer, cameraPos, camera, center, radius, timeSeconds, 0, r, g, b, alpha);
   }

   public static void sphereShell(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Vec3 center, float radius, float timeSeconds, int r, int g, int b, int alpha) {
      shell(buffer, cameraPos, camera, center, radius, timeSeconds, -7, r, g, b, alpha);
   }

   /**
    * Body-hugging ellipsoid of billboard dots (Lion's Heart aura): horizontal
    * and vertical radii are given separately so the shell sits ~0.5 block
    * off the player's silhouette instead of being a 4-block bubble.
    */
   public static void bodyShell(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Vec3 center, float radiusXZ, float radiusY, float timeSeconds, int r, int g, int b, int alpha) {
      for (int band = -6; band <= 6; band++) {
         double theta = (Math.PI / 2.0) * (double)band / 7.0;
         double bandY = Math.sin(theta) * radiusY;
         double bandRadius = Math.cos(theta) * radiusXZ;
         int steps = Math.max(6, (int)(bandRadius * 18.0));
         for (int i = 0; i < steps; i++) {
            double az = (Math.PI * 2.0) * (double)i / (double)steps + band * 0.35;
            Vec3 pos = new Vec3(center.x + Math.cos(az) * bandRadius, center.y + bandY, center.z + Math.sin(az) * bandRadius);
            int fade = (int)(alpha * (0.7 + 0.3 * Math.sin(timeSeconds * 1.5 + band * 1.7 + i * 0.9)));
            billboardPixel(buffer, cameraPos, camera, pos, 0.035F, r, g, b, fade);
         }
      }
   }

   private static void shell(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Vec3 center, float radius, float timeSeconds, int firstBand, int r, int g, int b, int alpha) {
      for (int band = firstBand; band <= 7; band++) {
         double theta = (Math.PI / 2.0) * (double)band / 7.0;
         double bandY = Math.sin(theta) * radius;
         double bandRadius = Math.cos(theta) * radius;
         int steps = Math.max(1, (int)(bandRadius * 8.0));
         for (int i = 0; i < steps; i++) {
            double az = (Math.PI * 2.0) * (double)i / (double)steps;
            double x = center.x + Math.cos(az) * bandRadius;
            double y = center.y + bandY;
            double z = center.z + Math.sin(az) * bandRadius;
            int fade = (int)(alpha * (0.8 + 0.2 * Math.sin(timeSeconds * 1.5 + band * 1.7 + i * 0.9)));
            billboardPixel(buffer, cameraPos, camera, new Vec3(x, y, z), 0.06F, r, g, b, fade);
         }
      }
   }

   /** Soft 3x3 cross glow — the white highlight on a frozen projectile. */
   public static void crossGlow(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Vec3 pos, float size, int r, int g, int b, int alpha) {
      billboardPixel(buffer, cameraPos, camera, pos, size, r, g, b, alpha);
      billboardPixel(buffer, cameraPos, camera, pos.add(size, 0.0, 0.0), size * 0.5F, r, g, b, alpha / 2);
      billboardPixel(buffer, cameraPos, camera, pos.subtract(size, 0.0, 0.0), size * 0.5F, r, g, b, alpha / 2);
      billboardPixel(buffer, cameraPos, camera, pos.add(0.0, size, 0.0), size * 0.5F, r, g, b, alpha / 2);
      billboardPixel(buffer, cameraPos, camera, pos.subtract(0.0, size, 0.0), size * 0.5F, r, g, b, alpha / 2);
   }
}

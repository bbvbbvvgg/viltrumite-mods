package dev.baranhan.viltrumitecore.client.ghostrider;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pixel-art hellfire sprites (sheet: 8 looping frames x 2 rows, tools/assets/ghost_rider_fx.py): each flame stands
 * on a base point, grows along an axis (world up, swept back by speed) and turns round that axis to face the camera.
 * Used for the Hell Cycle's wheels, exhausts and road trail, burning bodies and the skull. Render thread only.
 */
public final class HellfireDraw {
   public static final int FRAMES = 8;
   public static final int TONGUE = 0;
   public static final int BLAZE = 1;
   private static final Vector3f RIGHT = new Vector3f();

   private HellfireDraw() {
   }

   public static RenderType type() {
      return RenderType.entityTranslucentEmissive(GhostRiderAssets.HELLFIRE);
   }

   /** Pure: sprite frame for an age in ticks (about 12 frames a second) and a per-flame phase. */
   public static int frame(float age, int phase) {
      return Math.floorMod((int)(age * 0.6F) + phase, FRAMES);
   }

   /** Pure: a cheap per-flame flicker factor around 1 (0.8 - 1.2). */
   public static float flicker(float age, int phase) {
      return 1.0F + 0.12F * Mth.sin(age * 0.9F + phase * 1.7F) + 0.08F * Mth.sin(age * 2.3F + phase * 0.6F);
   }

   /**
    * One flame: base point and unit axis in the matrix's space, {@code toCamX/Y/Z} any vector from the flame towards
    * the eye; width and height in blocks, alpha 0-255, tint multiplies the sheet colours.
    */
   public static void flame(VertexConsumer vc, Matrix4f m, float bx, float by, float bz, Vector3f axis, float toCamX, float toCamY, float toCamZ,
         float width, float height, int frame, int row, int r, int g, int b, int alpha) {
      if (alpha <= 2 || height <= 0.01F) {
         return;
      }
      // right = axis x toCam (the sprite turns round its own axis towards the eye)
      float rx = axis.y * toCamZ - axis.z * toCamY;
      float ry = axis.z * toCamX - axis.x * toCamZ;
      float rz = axis.x * toCamY - axis.y * toCamX;
      float len = Mth.sqrt(rx * rx + ry * ry + rz * rz);
      if (len < 1.0E-5F) {
         // looking straight along the axis: any perpendicular will do
         RIGHT.set(axis.y, -axis.x, 0.0F);
         if (RIGHT.lengthSquared() < 1.0E-6F) {
            RIGHT.set(1.0F, 0.0F, 0.0F);
         }
         RIGHT.normalize();
         rx = RIGHT.x;
         ry = RIGHT.y;
         rz = RIGHT.z;
      } else {
         rx /= len;
         ry /= len;
         rz /= len;
      }
      float hw = width * 0.5F;
      float u0 = frame / (float)FRAMES;
      float u1 = (frame + 1) / (float)FRAMES;
      float v0 = row * 0.5F;
      float v1 = v0 + 0.5F;
      float tx = bx + axis.x * height;
      float ty = by + axis.y * height;
      float tz = bz + axis.z * height;
      int a = Math.min(255, alpha);
      put(vc, m, bx - rx * hw, by - ry * hw, bz - rz * hw, u0, v1, r, g, b, a);
      put(vc, m, bx + rx * hw, by + ry * hw, bz + rz * hw, u1, v1, r, g, b, a);
      put(vc, m, tx + rx * hw, ty + ry * hw, tz + rz * hw, u1, v0, r, g, b, a);
      put(vc, m, tx - rx * hw, ty - ry * hw, tz - rz * hw, u0, v0, r, g, b, a);
   }

   private static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
      vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F)
         .endVertex();
   }
}

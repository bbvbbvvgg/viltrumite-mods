package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.ghostrider.HellfireDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The burning tyre track: flames left standing on the road where the Hell Cycle's tyres passed, shrinking away over
 * two seconds. One shared ring buffer (oldest points are overwritten), drawn after the particles. Client only.
 */
public final class HellCycleTrails {
   static final int CAPACITY = 1024;
   /** Life of a trail flame (ticks). */
   static final int LIFE = 40;
   private static final float[] X = new float[CAPACITY];
   private static final float[] Y = new float[CAPACITY];
   private static final float[] Z = new float[CAPACITY];
   private static final double[] ORIGIN = new double[3];
   private static final float[] SIZE = new float[CAPACITY];
   private static final long[] BORN = new long[CAPACITY];
   private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
   private static int head;
   private static int count;
   private static boolean originSet;

   private HellCycleTrails() {
   }

   /** Adds one flame at a world point (stored relative to a floating origin to keep float precision). */
   static void add(double x, double y, double z, float size, long now) {
      if (!originSet || Math.abs(x - ORIGIN[0]) > 4096.0 || Math.abs(z - ORIGIN[2]) > 4096.0) {
         clear();
         ORIGIN[0] = x;
         ORIGIN[1] = y;
         ORIGIN[2] = z;
         originSet = true;
      }
      X[head] = (float)(x - ORIGIN[0]);
      Y[head] = (float)(y - ORIGIN[1]);
      Z[head] = (float)(z - ORIGIN[2]);
      SIZE[head] = size;
      BORN[head] = now;
      head = (head + 1) % CAPACITY;
      count = Math.min(CAPACITY, count + 1);
   }

   /** Pure: height factor of a trail flame by age (ticks): flares up, then shrinks to nothing at LIFE. */
   static float shrink(float age) {
      if (age < 0.0F || age >= LIFE) {
         return 0.0F;
      }
      float t = age / LIFE;
      return Math.min(1.0F, age / 3.0F + 0.35F) * (1.0F - t) * (1.0F - t * 0.5F);
   }

   public static void clear() {
      head = 0;
      count = 0;
      originSet = false;
   }

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || count == 0) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      float partial = event.getPartialTick();
      float now = mc.level.getGameTime() + partial;
      Vec3 cam = event.getCamera().getPosition();
      float ox = (float)(ORIGIN[0] - cam.x);
      float oy = (float)(ORIGIN[1] - cam.y);
      float oz = (float)(ORIGIN[2] - cam.z);
      PoseStack stack = event.getPoseStack();
      Matrix4f m = stack.last().pose();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      VertexConsumer vc = buffers.getBuffer(HellfireDraw.type());
      int drawn = 0;
      for (int i = 0; i < count; i++) {
         int idx = Math.floorMod(head - 1 - i, CAPACITY);
         float age = now - BORN[idx];
         float k = shrink(age);
         if (k <= 0.0F) {
            continue;
         }
         float x = X[idx] + ox;
         float y = Y[idx] + oy;
         float z = Z[idx] + oz;
         if (x * x + y * y + z * z > 96.0F * 96.0F) {
            continue;
         }
         float s = SIZE[idx];
         float h = s * k * HellfireDraw.flicker(age, idx);
         int alpha = (int)(230 * Mth.clamp(k * 1.6F, 0.0F, 1.0F));
         HellfireDraw.flame(vc, m, x, y, z, UP, -x, -y, -z, s * 0.75F, h, HellfireDraw.frame(age, idx * 3), HellfireDraw.TONGUE, 255, 255, 255, alpha);
         drawn++;
      }
      buffers.endBatch(HellfireDraw.type());
      if (drawn == 0 && count > 0 && now - BORN[Math.floorMod(head - 1, CAPACITY)] > LIFE) {
         clear();
      }
   }
}

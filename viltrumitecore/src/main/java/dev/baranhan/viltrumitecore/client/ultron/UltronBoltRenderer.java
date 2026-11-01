package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.entity.ultron.UltronBolt;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Ultron's energy shots, additive and untextured: the repulsor bolt (red-orange streak, hot head), the sentry
 * laser (thin long crimson line, white core) and Ultron Prime's palm blast (a fat orange-white ball with a
 * crackling halo and a heavy wake). Each trails a fading wake along its recorded client positions.
 */
public class UltronBoltRenderer extends EntityRenderer<UltronBolt> {
   private final Vector3f head = new Vector3f();
   private final Vector3f tail = new Vector3f();
   private final Vector3f toCam = new Vector3f();
   private final Vector3f p0 = new Vector3f();
   private final Vector3f p1 = new Vector3f();

   public UltronBoltRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(UltronBolt entity) {
      return TextureAtlas.LOCATION_BLOCKS;
   }

   @Override
   public void render(UltronBolt e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      if (ShaderCompat.isShadowPass()) {
         return;
      }
      Vec3 v = e.getDeltaMovement();
      double speed = v.length();
      if (speed < 1.0E-4) {
         return;
      }
      int style = e.style();
      float age = e.tickCount + partial;
      Camera camera = this.entityRenderDispatcher.camera;
      double x = Mth.lerp(partial, e.xo, e.getX());
      double y = Mth.lerp(partial, e.yo, e.getY());
      double z = Mth.lerp(partial, e.zo, e.getZ());
      Vec3 cam = camera.getPosition();
      this.toCam.set((float)(cam.x - x), (float)(cam.y - y), (float)(cam.z - z));
      float dx = (float)(v.x / speed), dy = (float)(v.y / speed), dz = (float)(v.z / speed);
      float maxLen = style == UltronBolt.STYLE_LASER ? 3.4F : style == UltronBolt.STYLE_PALM ? 1.6F : 1.5F;
      float length = (float)Math.min(maxLen, speed * (age + 0.5));
      float width = style == UltronBolt.STYLE_LASER ? 0.045F : style == UltronBolt.STYLE_PALM ? 0.24F : 0.09F;
      this.head.set(0.0F, 0.0F, 0.0F);
      this.tail.set(-dx * length, -dy * length, -dz * length);
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      float flicker = 0.88F + 0.12F * Mth.sin(age * 3.7F + e.getId());
      int[] outer = style == UltronBolt.STYLE_LASER ? new int[]{255, 30, 40} : style == UltronBolt.STYLE_PALM ? new int[]{255, 110, 40} : new int[]{255, 60, 35};
      int[] mid = style == UltronBolt.STYLE_LASER ? new int[]{255, 80, 80} : style == UltronBolt.STYLE_PALM ? new int[]{255, 180, 90} : new int[]{255, 140, 70};
      GlowDraw.ribbon(vc, m, this.tail, this.head, this.toCam, width * 3.6F, width * 4.2F, outer[0], outer[1], outer[2], 0, (int)(130 * flicker));
      GlowDraw.ribbon(vc, m, this.tail, this.head, this.toCam, width * 1.5F, width * 1.8F, mid[0], mid[1], mid[2], 20, (int)(215 * flicker));
      GlowDraw.ribbon(vc, m, this.tail, this.head, this.toCam, width * 0.55F, width * 0.7F, 255, 238, 225, 60, 255);
      int n = Math.min(e.trailCount, style == UltronBolt.STYLE_PALM ? 10 : style == UltronBolt.STYLE_LASER ? 4 : 6);
      float prevX = this.tail.x, prevY = this.tail.y, prevZ = this.tail.z;
      for (int i = 1; i < n; i++) {
         float tx = (float)(e.trail[i * 3] - x), ty = (float)(e.trail[i * 3 + 1] - y), tz = (float)(e.trail[i * 3 + 2] - z);
         if ((tx - this.tail.x) * dx + (ty - this.tail.y) * dy + (tz - this.tail.z) * dz > 0.0F) {
            continue; // only the part of the wake behind the streak
         }
         float f0 = 1.0F - (i - 1) / (float)n;
         float f1 = 1.0F - i / (float)n;
         this.p0.set(tx, ty, tz);
         this.p1.set(prevX, prevY, prevZ);
         GlowDraw.ribbon(vc, m, this.p0, this.p1, this.toCam, width * 2.2F * f1, width * 2.2F * f0, outer[0], outer[1], outer[2], (int)(70 * f1),
            (int)(70 * f0));
         prevX = tx;
         prevY = ty;
         prevZ = tz;
      }
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      float r = width * (style == UltronBolt.STYLE_PALM ? 3.2F : style == UltronBolt.STYLE_LASER ? 3.0F : 3.4F) * flicker;
      GlowDraw.billboard(vc, m, this.head, left, up, r * 2.0F, 0.12F, outer[0], outer[1], outer[2], 95);
      GlowDraw.billboard(vc, m, this.head, left, up, r, 0.45F, 255, 225, 205, 235);
      if (style == UltronBolt.STYLE_PALM) {
         // crackling halo: two counter-turning broken rings facing the camera
         float spin = age * 0.35F;
         for (int k = 0; k < 2; k++) {
            float s = k == 0 ? spin : -spin * 1.3F;
            Vector3f ax = new Vector3f(left).mul(Mth.cos(s)).add(new Vector3f(up).mul(Mth.sin(s)));
            Vector3f bx = new Vector3f(up).mul(Mth.cos(s)).sub(new Vector3f(left).mul(Mth.sin(s)));
            GlowDraw.ring(vc, m, this.head, ax, bx, r * 1.1F, r * 1.5F, 0.5F, 8, 255, 120, 50, (int)(110 * flicker));
         }
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }
}

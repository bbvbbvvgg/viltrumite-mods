package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriBolt;
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
 * Chitauri energy shot: a camera-facing streak along the flight (white-blue core inside a wide blue glow, longer and
 * thicker for a chariot cannon), a hot head and a fading trail along the recorded client positions. Additive, no
 * texture. Client only.
 */
public class ChitauriBoltRenderer extends EntityRenderer<ChitauriBolt> {
   private final Vector3f head = new Vector3f();
   private final Vector3f tail = new Vector3f();
   private final Vector3f toCam = new Vector3f();
   private final Vector3f p0 = new Vector3f();
   private final Vector3f p1 = new Vector3f();

   public ChitauriBoltRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(ChitauriBolt entity) {
      return TextureAtlas.LOCATION_BLOCKS;
   }

   @Override
   public void render(ChitauriBolt e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      if (ShaderCompat.isShadowPass()) {
         return;
      }
      Vec3 v = e.getDeltaMovement();
      double speed = v.length();
      if (speed < 1.0E-4) {
         return;
      }
      boolean cannon = e.power() >= 2.0F;
      float age = e.tickCount + partial;
      Camera camera = this.entityRenderDispatcher.camera;
      double x = Mth.lerp(partial, e.xo, e.getX());
      double y = Mth.lerp(partial, e.yo, e.getY());
      double z = Mth.lerp(partial, e.zo, e.getZ());
      Vec3 cam = camera.getPosition();
      this.toCam.set((float)(cam.x - x), (float)(cam.y - y), (float)(cam.z - z));
      float length = (float)Math.min(cannon ? 2.8 : 1.7, speed * (age + 0.5));
      float width = cannon ? 0.2F : 0.085F;
      float dx = (float)(v.x / speed), dy = (float)(v.y / speed), dz = (float)(v.z / speed);
      this.head.set(0, 0, 0);
      this.tail.set(-dx * length, -dy * length, -dz * length);
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      float flicker = 0.9F + 0.1F * Mth.sin(age * 3.3F);
      GlowDraw.ribbon(vc, m, this.tail, this.head, this.toCam, width * 3.6F, width * 4.2F, 60, 125, 255, 0, (int)(120 * flicker));
      GlowDraw.ribbon(vc, m, this.tail, this.head, this.toCam, width * 1.5F, width * 1.8F, 110, 175, 255, 20, (int)(210 * flicker));
      GlowDraw.ribbon(vc, m, this.tail, this.head, this.toCam, width * 0.6F, width * 0.7F, 230, 242, 255, 60, 255);
      // fading wake along the recorded positions (one per client tick)
      int n = Math.min(e.trailCount, cannon ? 8 : 5);
      float prevX = this.tail.x, prevY = this.tail.y, prevZ = this.tail.z;
      for (int i = 1; i < n; i++) {
         float tx = (float)(e.trail[i * 3] - x), ty = (float)(e.trail[i * 3 + 1] - y), tz = (float)(e.trail[i * 3 + 2] - z);
         // only the part of the wake behind the streak
         if ((tx - this.tail.x) * dx + (ty - this.tail.y) * dy + (tz - this.tail.z) * dz > 0.0F) {
            continue;
         }
         float f0 = 1.0F - (i - 1) / (float)n;
         float f1 = 1.0F - i / (float)n;
         this.p0.set(tx, ty, tz);
         this.p1.set(prevX, prevY, prevZ);
         GlowDraw.ribbon(vc, m, this.p0, this.p1, this.toCam, width * 2.2F * f1, width * 2.2F * f0, 70, 140, 255, (int)(60 * f1), (int)(60 * f0));
         prevX = tx;
         prevY = ty;
         prevZ = tz;
      }
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      float r = width * (cannon ? 4.5F : 3.4F) * flicker;
      GlowDraw.billboard(vc, m, this.head, left, up, r * 2.0F, 0.12F, 70, 140, 255, 90);
      GlowDraw.billboard(vc, m, this.head, left, up, r, 0.45F, 200, 230, 255, 230);
      super.render(e, yaw, partial, stack, buffers, light);
   }
}

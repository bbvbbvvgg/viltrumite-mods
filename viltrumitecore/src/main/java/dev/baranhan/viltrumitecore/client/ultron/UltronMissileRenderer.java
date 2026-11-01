package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMissile;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The brute's micro-missile: the model noses along its flight (heading from the interpolated motion), spins on
 * its axis with the fly loop, its exhaust plume drawn only in the emissive pass, a flame cone and a fading
 * smoke-lit wake along the recorded positions. The smoke itself comes from {@link UltronVfx}.
 */
public class UltronMissileRenderer extends EntityRenderer<UltronMissile> {
   private static final float CENTRE = 3.0F / 16.0F;
   private final Vector3f p0 = new Vector3f();
   private final Vector3f p1 = new Vector3f();

   public UltronMissileRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(UltronMissile e) {
      return UltronAssets.MISSILE.texture();
   }

   @Override
   public void render(UltronMissile e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(UltronAssets.MISSILE.geo());
      if (model == null) {
         return;
      }
      Vec3 v = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
      if (v.lengthSqr() < 1.0E-6) {
         v = e.getDeltaMovement();
      }
      if (v.lengthSqr() < 1.0E-8) {
         v = new Vec3(0.0, 0.0, 1.0);
      }
      Vec3 dir = v.normalize();
      float age = e.tickCount + partial;
      boolean shadow = ShaderCompat.isShadowPass();
      model.resetBones();
      Animation fly = AnimCache.animation(UltronAssets.MISSILE.anim(), "fly");
      if (fly != null) {
         AnimationController.overlay(fly, model, (age / 20.0F + e.getId() * 0.17F) % Math.max(0.05, fly.lengthSeconds()));
      }
      GeoBone flame = model.getBone("flame");
      double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
      stack.pushPose();
      stack.translate(0.0F, CENTRE, 0.0F);
      stack.mulPose(Axis.YP.rotation((float)Math.atan2(-dir.x, -dir.z)));
      stack.mulPose(Axis.XP.rotation((float)Math.atan2(dir.y, flat)));
      stack.mulPose(Axis.ZP.rotationDegrees(age * 24.0F));
      stack.translate(0.0F, -CENTRE, 0.0F);
      if (flame != null) {
         flame.hidden = true;
      }
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(UltronAssets.MISSILE.texture())), light,
         OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, null);
      if (!shadow) {
         if (flame != null) {
            flame.hidden = false;
            float f = 0.85F + 0.15F * Mth.sin(age * 3.1F);
            flame.scaleZ *= f;
         }
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(UltronAssets.MISSILE.glow())), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, null);
      }
      stack.popPose();
      if (!shadow) {
         this.exhaust(e, dir, partial, age, stack.last().pose(), buffers.getBuffer(RenderType.lightning()));
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void exhaust(UltronMissile e, Vec3 dir, float partial, float age, Matrix4f m, VertexConsumer vc) {
      Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
      double x = Mth.lerp(partial, e.xo, e.getX());
      double y = Mth.lerp(partial, e.yo, e.getY());
      double z = Mth.lerp(partial, e.zo, e.getZ());
      Vector3f toCam = new Vector3f((float)(cam.x - x), (float)(cam.y - y - CENTRE), (float)(cam.z - z));
      Vector3f back = new Vector3f((float)-dir.x, (float)-dir.y, (float)-dir.z);
      Vector3f tail = new Vector3f(back).mul(0.3F).add(0.0F, CENTRE, 0.0F);
      float flicker = 0.85F + 0.15F * Mth.sin(age * 2.9F);
      Vector3f tip = new Vector3f(back).mul(0.9F * flicker).add(tail);
      GlowDraw.ribbon(vc, m, tail, tip, toCam, 0.16F, 0.03F, 255, 120, 50, 200, 0);
      GlowDraw.ribbon(vc, m, tail, new Vector3f(back).mul(0.4F).add(tail), toCam, 0.07F, 0.02F, 255, 236, 210, 255, 60);
      Vector3f left = this.entityRenderDispatcher.camera.getLeftVector();
      Vector3f up = this.entityRenderDispatcher.camera.getUpVector();
      GlowDraw.billboard(vc, m, tail, left, up, 0.38F * flicker, 0.25F, 255, 90, 40, 150);
      // glowing wake along the recorded positions (one per client tick), thinning out
      int n = Math.min(e.trailCount, 12);
      float px = tail.x, py = tail.y, pz = tail.z;
      for (int i = 1; i < n; i++) {
         float tx = (float)(e.trail[i * 3] - x), ty = (float)(e.trail[i * 3 + 1] - y) + CENTRE, tz = (float)(e.trail[i * 3 + 2] - z);
         float f0 = 1.0F - (i - 1) / (float)n;
         float f1 = 1.0F - i / (float)n;
         this.p0.set(tx, ty, tz);
         this.p1.set(px, py, pz);
         GlowDraw.ribbon(vc, m, this.p0, this.p1, toCam, 0.12F * f1 + 0.03F, 0.12F * f0 + 0.03F, 255, 90, 40, (int)(70 * f1), (int)(70 * f0));
         px = tx;
         py = ty;
         pz = tz;
      }
   }
}

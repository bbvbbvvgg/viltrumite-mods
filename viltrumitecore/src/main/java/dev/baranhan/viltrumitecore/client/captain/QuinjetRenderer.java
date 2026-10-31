package dev.baranhan.viltrumitecore.client.captain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.avengers.QuinjetEntity;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Quinjet: quarter-scale mesh drawn x4, faces its travel yaw, pitches gently, engines tilt to hover with
 * the thrust value, the rear ramp opens on the synced flag (20 t clip), engine/cockpit glow and jet wash. Client only.
 */
public class QuinjetRenderer extends EntityRenderer<QuinjetEntity> {
   static final ResourceLocation GEO = CaptainView.rl("geo/avengers/quinjet.geo.json");
   static final ResourceLocation TEX = CaptainView.rl("textures/entity/avengers/quinjet.png");
   static final ResourceLocation GLOW = CaptainView.rl("textures/entity/avengers/quinjet_glow.png");
   static final ResourceLocation ANIM = CaptainView.rl("animations/avengers/quinjet.animation.json");
   static final float SCALE = 4.0F;

   public QuinjetRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(QuinjetEntity entity) {
      return TEX;
   }

   @Override
   public boolean shouldRender(QuinjetEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
      return true; // big and fast: never culled by its small hit box
   }

   @Override
   public void render(QuinjetEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(GEO);
      if (model == null) {
         return;
      }
      float age = e.tickCount + partial;
      model.resetBones();
      Animation flight = AnimCache.animation(ANIM, "flight");
      if (flight != null) {
         AnimationController.overlay(flight, model, (age / 20.0) % flight.lengthSeconds(), 1.0F);
      }
      Animation hover = AnimCache.animation(ANIM, "hover");
      float thrust = Mth.clamp(e.thrust(), 0.0F, 1.0F);
      if (hover != null && thrust > 0.01F) {
         AnimationController.overlay(hover, model, 0.0, thrust);
      }
      Animation ramp = AnimCache.animation(ANIM, "ramp_open");
      if (ramp != null) {
         double since = Math.max(0.0, (age - e.clientRampStart) / 20.0);
         double t = e.clientRamp ? Math.min(since, ramp.lengthSeconds()) : Math.max(0.0, ramp.lengthSeconds() - since);
         if (t > 0.0) {
            AnimationController.overlay(ramp, model, t, 1.0F);
         }
      }
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yRotO, e.getYRot())));
      stack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.xRotO, e.getXRot())));
      stack.scale(SCALE, SCALE, SCALE);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, null);
      if (!ShaderCompat.isShadowPass()) {
         float g = 0.75F + 0.25F * Mth.sin(age * 0.8F);
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            g, g, g, 1.0F, null);
      }
      stack.popPose();
      if (e.tickCount != this.lastWash) {
         this.lastWash = e.tickCount;
         wash(e, thrust);
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private int lastWash = -1;

   /** Jet exhaust behind the engines and, while hovering, the downwash dust under them. */
   private static void wash(QuinjetEntity e, float thrust) {
      var level = e.level();
      double yaw = Math.toRadians(e.getYRot());
      Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
      Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
      for (int s = -1; s <= 1; s += 2) {
         Vec3 eng = e.position().add(side.scale(s * 1.9)).add(0, 1.4, 0);
         Vec3 back = eng.subtract(fwd.scale(1.6));
         level.addParticle(ParticleTypes.SMALL_FLAME, back.x, back.y, back.z, -fwd.x * 0.2, 0, -fwd.z * 0.2);
         if (thrust > 0.3F) {
            level.addParticle(ParticleTypes.CLOUD, eng.x, eng.y - 0.8, eng.z, 0, -0.35 * thrust, 0);
         }
      }
   }
}

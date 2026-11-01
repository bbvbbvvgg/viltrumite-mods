package dev.baranhan.viltrumitecore.client.scp096;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.scp.scp096.Scp096Entity;
import dev.baranhan.viltrumitecore.scp.scp096.Scp096State;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * SCP-096: sixteen full-body clip layers eased in real time and blended as a weighted average (see {@link Scp096Pose}),
 * the sobbing as an additive layer, the upper-body smash and claw strike over the run, and the head turned toward
 * what it looks at. The shuffle and the run are driven by the gait phase (distance), so the feet do not slide at any
 * speed. Client only.
 */
public class Scp096Renderer extends EntityRenderer<Scp096Entity> {
   static final ResourceLocation GEO = rl("geo/scp096/scp096.geo.json");
   static final ResourceLocation TEX = rl("textures/entity/scp096/scp096.png");
   static final ResourceLocation[] ANIM = {rl("animations/scp096/scp096_calm.animation.json"), rl("animations/scp096/scp096_rage.animation.json"),
      rl("animations/scp096/scp096_chase.animation.json")};
   private final float[] targets = new float[Scp096Pose.SLOTS];

   public Scp096Renderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.5F;
   }

   @Override
   public ResourceLocation getTextureLocation(Scp096Entity entity) {
      return TEX;
   }

   @Override
   public void render(Scp096Entity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(GEO);
      if (model == null) {
         return;
      }
      this.shadowRadius = e.clientState == Scp096State.SIT ? 0.65F : 0.5F;
      // the shader shadow pass renders the same frame again: pose it, but do not advance the easing twice
      this.pose(model, e, partial, !ShaderCompat.isShadowPass());
      stack.pushPose();
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
      stack.popPose();
      super.render(e, yaw, partial, stack, buffers, light);
   }

   /** Poses the model for this frame (the layer weights live on the entity, so each instance eases on its own). */
   void pose(BakedGeoModel model, Scp096Entity e, float partial, boolean advance) {
      float age = e.tickCount + partial;
      float ageSec = age / 20.0F;
      float since = (age - e.clientStateStart) / 20.0F;
      Scp096State state = e.clientState;
      float move = Mth.lerp(partial, e.clientMoveO, e.clientMove);
      Scp096Pose.targets(state, e.clientPrevState, since, e.clientFromSit, e.flag(Scp096Entity.FLAG_FACE_OPEN), e.flag(Scp096Entity.FLAG_COVER),
         e.flag(Scp096Entity.FLAG_CLIMB), e.flag(Scp096Entity.FLAG_LEAP), move, e.clientAirTicks + (e.clientAirTicks > 0 ? partial : 0.0F),
         this.targets);
      if (advance) {
         float now = RealTime.seconds();
         float dt = now - e.clientWeightAge;
         boolean snap = e.clientWeightAge < 0.0F || dt > 1.0F;
         if (snap || dt > 0.0F) {
            Scp096Pose.ease(e.clientWeights, this.targets, state, Math.max(dt, 0.0F), snap);
            e.clientWeightAge = now;
         }
      }
      float[] w = e.clientWeights;
      double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
      model.resetBones();
      float sum = 0.0F;
      for (int i = 0; i < Scp096Pose.LAYERS; i++) {
         if (w[i] < 0.002F) {
            continue;
         }
         Animation clip = AnimCache.animation(ANIM[Scp096Pose.FILE[i]], Scp096Pose.CLIP[i]);
         if (clip == null) {
            continue;
         }
         float share = Scp096Pose.shareOf(w[i], sum);
         sum += w[i];
         AnimationController.overlay(clip, model, Scp096Pose.time(i, since, ageSec, gait, e.clientFromSit, e.getId()), share);
      }
      // the shoulders still heave while it cries, whatever the base pose
      Animation sob = AnimCache.animation(ANIM[0], "sob");
      if (sob != null && w[Scp096Pose.SOB] > 0.01F) {
         AnimationController.additive(sob, model, (ageSec + e.getId() * 0.23) % sob.lengthSeconds(), w[Scp096Pose.SOB]);
      }
      // upper-body strikes over the chase pose: smashing through a wall, the claw at a target
      if (state == Scp096State.CHASE) {
         strike(model, "smash", (age - e.clientSmashStart) / 20.0);
         strike(model, "attack", (age - e.clientAttackStart) / 20.0);
      }
      float look = w[Scp096Pose.LOOK];
      if (look > 0.01F) {
         float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
         float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw), -60.0F, 60.0F) * look;
         float pitch = Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -40.0F, 40.0F) * look;
         deg(model.getBone("chest"), 0.0F, headYaw * 0.2F, 0.0F);
         deg(model.getBone("neck"), pitch * 0.35F, headYaw * 0.3F, 0.0F);
         deg(model.getBone("head"), pitch * 0.65F, headYaw * 0.5F, 0.0F);
      }
   }

   private static void strike(BakedGeoModel model, String name, double t) {
      Animation clip = AnimCache.animation(ANIM[2], name);
      if (clip != null) {
         float k = Scp096Pose.strike(t, clip.lengthSeconds());
         if (k > 0.0F) {
            AnimationController.overlay(clip, model, t, k);
         }
      }
   }

   /** Adds Blockbench-convention degrees (the clip convention) to a bone. */
   static void deg(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone != null) {
         bone.rotX += (float)Math.toRadians(-x);
         bone.rotY += (float)Math.toRadians(-y);
         bone.rotZ += (float)Math.toRadians(z);
      }
   }

   static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }
}

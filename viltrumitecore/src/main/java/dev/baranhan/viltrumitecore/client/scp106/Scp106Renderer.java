package dev.baranhan.viltrumitecore.client.scp106;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.QuatPoseBlend;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.scp.scp106.Scp106Entity;
import dev.baranhan.viltrumitecore.scp.scp106.Scp106State;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * SCP-106: seven full-body clips (idle, walk, stare, phase, emerge, sink, capture) eased per instance and blended
 * in quaternions, the backhand swipe as an upper-body overlay, the head turned toward its prey, and the pale eyes
 * as an emissive pass. The walk runs on the gait phase (distance walked), so the slow limp never slides. Emerging,
 * sinking and the capture move the whole body under the floor, which hides it by depth. Client only.
 */
public class Scp106Renderer extends EntityRenderer<Scp106Entity> {
   static final ResourceLocation GEO = rl("geo/scp106/scp106.geo.json");
   static final ResourceLocation TEX = rl("textures/entity/scp106/scp106.png");
   static final ResourceLocation GLOW = rl("textures/entity/scp106/scp106_glow.png");
   static final ResourceLocation ANIM = rl("animations/scp106/scp106.animation.json");
   static final String[] CLIPS = {"idle", "walk", "stare", "phase", "emerge", "sink", "capture"};
   static final int IDLE = 0;
   static final int WALK = 1;
   static final int STARE = 2;
   static final int PHASE = 3;
   static final int EMERGE = 4;
   static final int SINK = 5;
   static final int CAPTURE = 6;
   private final float[] targets = new float[CLIPS.length];
   private final QuatPoseBlend blend = new QuatPoseBlend();

   public Scp106Renderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.5F;
   }

   @Override
   public ResourceLocation getTextureLocation(Scp106Entity entity) {
      return TEX;
   }

   @Override
   public void render(Scp106Entity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      if (e.isInvisible() || e.clientState == Scp106State.SUBMERGED) {
         return;
      }
      BakedGeoModel model = AnimCache.model(GEO);
      if (model == null) {
         return;
      }
      this.pose(model, e, partial, !ShaderCompat.isShadowPass());
      stack.pushPose();
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         0.85F, 0.85F, 0.85F, 1.0F, null);
      stack.popPose();
      super.render(e, yaw, partial, stack, buffers, light);
   }

   /** Target weight of each clip for the synced state (STALK splits between idle and walk by speed). */
   static void targets(Scp106State s, float move, float[] out) {
      java.util.Arrays.fill(out, 0.0F);
      switch (s) {
         case EMERGE -> out[EMERGE] = 1.0F;
         case SINK, RETREAT -> out[SINK] = 1.0F;
         case CAPTURE -> out[CAPTURE] = 1.0F;
         case PHASE -> out[PHASE] = 1.0F;
         case STARE -> out[STARE] = 1.0F;
         default -> {
            float walk = Mth.clamp(move / 0.03F, 0.0F, 1.0F);
            out[WALK] = walk;
            out[IDLE] = 1.0F - walk;
         }
      }
   }

   void pose(BakedGeoModel model, Scp106Entity e, float partial, boolean advance) {
      float age = e.tickCount + partial;
      float ageSec = age / 20.0F;
      float since = Math.max(0.0F, (age - e.clientStateStart) / 20.0F);
      Scp106State state = e.clientState;
      float move = Mth.lerp(partial, e.clientMoveO, e.clientMove);
      targets(state, move, this.targets);
      float[] w = e.clientWeights;
      if (advance) {
         float now = RealTime.seconds();
         float dt = now - e.clientWeightAge;
         boolean snap = e.clientWeightAge < 0.0F || dt > 1.0F;
         if (snap || dt > 0.0F) {
            // one-shot clips come in almost at once (they start from the stance); loops crossfade slowly
            boolean oneShot = state == Scp106State.EMERGE || state == Scp106State.SINK || state == Scp106State.RETREAT || state == Scp106State.CAPTURE;
            float rate = oneShot ? 14.0F : 5.0F;
            for (int i = 0; i < w.length && i < this.targets.length; i++) {
               w[i] = snap ? this.targets[i] : w[i] + (this.targets[i] - w[i]) * Math.min(1.0F, Math.max(dt, 0.0F) * rate);
            }
            e.clientWeightAge = now;
         }
      }
      double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
      model.resetBones();
      this.blend.begin(model);
      for (int i = 0; i < CLIPS.length; i++) {
         if (w[i] < 0.002F) {
            continue;
         }
         Animation clip = AnimCache.animation(ANIM, CLIPS[i]);
         if (clip == null) {
            continue;
         }
         double len = clip.lengthSeconds();
         double t = switch (i) {
            case WALK -> (gait % 1.0) * len;
            case EMERGE, SINK, CAPTURE -> Math.min(since, len - 1.0E-3);
            default -> (ageSec + e.getId() * 0.37) % len;
         };
         this.blend.add(clip, t, w[i]);
      }
      this.blend.apply();
      float swipeT = (age - e.clientSwipeStart) / 20.0F;
      Animation swipe = AnimCache.animation(ANIM, "swipe");
      if (swipe != null && swipeT >= 0.0F && swipeT < swipe.lengthSeconds()) {
         float k = (float)Math.min(1.0, Math.min(swipeT / 0.15, (swipe.lengthSeconds() - swipeT) / 0.25));
         this.blend.overlay(swipe, swipeT, k);
      }
      float look = w[IDLE] + w[WALK] + w[STARE] * 0.6F;
      if (look > 0.01F) {
         float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
         float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw), -55.0F, 55.0F) * look;
         float pitch = Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -35.0F, 35.0F) * look;
         deg(model.getBone("neck"), pitch * 0.35F, headYaw * 0.35F, 0.0F);
         deg(model.getBone("head"), pitch * 0.65F, headYaw * 0.55F, 0.0F);
      }
   }

   static void deg(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone != null) {
         bone.rotX += (float)Math.toRadians(-x);
         bone.rotY += (float)Math.toRadians(-y);
         bone.rotZ += (float)Math.toRadians(z);
      }
   }

   private static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }
}

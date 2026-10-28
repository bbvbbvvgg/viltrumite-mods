package dev.baranhan.viltrumitecore.client.jinwoo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shadow soldiers: own geo + clips per kind (idle/walk by gait, attack/special on the entity event,
 * rise from the ground 24 t, dissolve 14 t), elite textures (Igris) and a brighter glow. LOD: beyond
 * 48 blocks no glow pass (beyond 32 the VFX drop the smoke). Client only.
 */
public class ShadowSoldierRenderer extends EntityRenderer<ShadowSoldierEntity> {
   public static final double GLOW_LOD = 48.0;
   private static final ResourceLocation[] GEO = new ResourceLocation[ShadowKind.values().length];
   private static final ResourceLocation[] ANIM = new ResourceLocation[ShadowKind.values().length];
   private static final ResourceLocation[] TEX = new ResourceLocation[ShadowKind.values().length];
   private static final ResourceLocation[] GLOW = new ResourceLocation[ShadowKind.values().length];
   private static final ResourceLocation IGRIS = JinwooView.rl("textures/entity/jinwoo/shadow_igris.png");
   private static final ResourceLocation IGRIS_GLOW = JinwooView.rl("textures/entity/jinwoo/shadow_igris_glow.png");

   static {
      for (ShadowKind kind : ShadowKind.values()) {
         int i = kind.ordinal();
         GEO[i] = JinwooView.rl("geo/jinwoo/shadow_" + kind.key() + ".geo.json");
         ANIM[i] = JinwooView.rl("animations/jinwoo/shadow_" + kind.key() + ".animation.json");
         TEX[i] = JinwooView.rl("textures/entity/jinwoo/shadow_" + kind.key() + ".png");
         GLOW[i] = JinwooView.rl("textures/entity/jinwoo/shadow_" + kind.key() + "_glow.png");
      }
   }

   public ShadowSoldierRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(ShadowSoldierEntity entity) {
      return texture(entity.kind(), entity.elite());
   }

   public static ResourceLocation texture(ShadowKind kind, boolean elite) {
      return elite && kind == ShadowKind.KNIGHT ? IGRIS : TEX[kind.ordinal()];
   }

   public static ResourceLocation glow(ShadowKind kind, boolean elite) {
      return elite && kind == ShadowKind.KNIGHT ? IGRIS_GLOW : GLOW[kind.ordinal()];
   }

   @Override
   public void render(ShadowSoldierEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      if (e.isInvisible()) {
         return; // a hidden sentinel: only its puddle shows (JinwooVfx)
      }
      ShadowKind kind = e.kind();
      BakedGeoModel model = AnimCache.model(GEO[kind.ordinal()]);
      if (model == null) {
         return;
      }
      float age = e.tickCount + partial;
      float speed = e.walkAnimation.speed(partial);
      double gait = e.walkAnimation.position(partial) / 9.4315;
      float attack = e.clientAttackStart > -1000 ? age - e.clientAttackStart : 1.0E9F;
      float rise = age - e.clientRiseStart;
      float dissolve = e.clientDissolveStart >= 0 ? age - e.clientDissolveStart : -1;
      pose(model, kind, age, speed, gait, attack, e.clientSpecial, rise);
      GeoBone head = model.getBone("head");
      if (head != null) {
         float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
         float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw);
         float limit = kind == ShadowKind.BEAST ? 30 : 60;
         head.rotY += (float)Math.toRadians(-Mth.clamp(headYaw, -limit, limit));
         head.rotX += (float)Math.toRadians(-Mth.lerp(partial, e.xRotO, e.getXRot()) * 0.6F);
      }
      float scale = kind.scale(e.elite());
      float height = e.getBbHeight();
      float sink = sink(rise, dissolve, height);
      float alpha = dissolve >= 0 ? 1.0F - (float)smooth(dissolve / JinwooRules.SHADOW_DISSOLVE) : 1.0F;
      if (alpha <= 0.01F) {
         return;
      }
      double dist = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(e));
      float eyes = eyes(age - e.clientEyesStart, e.elite(), e.empowered());
      stack.pushPose();
      stack.translate(0.0, -sink, 0.0);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
      if (e.deathTime > 0) {
         float dying = Math.min(1.0F, Mth.sqrt((e.deathTime + partial - 1.0F) / 20.0F * 1.6F));
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
      }
      stack.scale(scale, scale, scale);
      draw(model, kind, e.elite(), stack, buffers, light, LivingEntityRenderer.getOverlayCoords(e, 0.0F), alpha,
         dist <= GLOW_LOD && !ShaderCompat.isShadowPass() ? eyes : 0.0F);
      stack.popPose();
      super.render(e, yaw, partial, stack, buffers, light);
   }

   /** Body (cutout or translucent while dissolving) and the glow pass at {@code glow} intensity (0 = none). */
   public static void draw(BakedGeoModel model, ShadowKind kind, boolean elite, PoseStack stack, MultiBufferSource buffers, int light, int overlay,
      float alpha, float glow) {
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(texture(kind, elite)) : RenderType.entityCutoutNoCull(texture(kind, elite));
      AnimRenderer.render(model, stack, null, buffers.getBuffer(type), light, overlay, 1, 1, 1, alpha, null);
      if (glow > 0.0F) {
         float g = glow * alpha;
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(glow(kind, elite))), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
      }
   }

   public static BakedGeoModel model(ShadowKind kind) {
      return AnimCache.model(GEO[kind.ordinal()]);
   }

   /** Clip pose: idle/walk by gait, the attack or special clip, the rise clip on top. */
   public static void pose(BakedGeoModel model, ShadowKind kind, float age, float speed, double gait, float attackAge, boolean special, float rise) {
      ResourceLocation file = ANIM[kind.ordinal()];
      model.resetBones();
      Animation idle = AnimCache.animation(file, "idle");
      Animation walk = AnimCache.animation(file, "walk");
      if (idle != null) {
         AnimationController.overlay(idle, model, (age / 20.0) % idle.lengthSeconds(), 1.0F);
      }
      float w = Mth.clamp(speed * 1.8F, 0.0F, 1.0F);
      if (walk != null && w > 0.01F) {
         AnimationController.overlay(walk, model, (gait - Math.floor(gait)) * walk.lengthSeconds(), w);
      }
      if (attackAge < 40) {
         Animation clip = special ? AnimCache.animation(file, "special") : null;
         if (clip == null) {
            clip = AnimCache.animation(file, "attack");
         }
         if (clip != null) {
            double sec = attackAge / 20.0;
            if (sec < clip.lengthSeconds()) {
               float out = (float)smooth((clip.lengthSeconds() - sec) / 0.1);
               AnimationController.overlay(clip, model, sec, out);
            }
         }
      }
      if (rise >= 0 && rise < JinwooRules.SHADOW_RISE) {
         Animation clip = AnimCache.animation(file, "rise");
         if (clip != null) {
            AnimationController.overlay(clip, model, rise / 20.0, (float)smooth((JinwooRules.SHADOW_RISE - rise) / 3.0));
         }
      }
      // cape / plume: lag behind the stride
      float sway = (float)Math.sin(age * 0.12) * 3.0F;
      JinwooPose.addDeg(model.getBone("cape"), 6 + speed * 35 + sway, 0, 0);
      JinwooPose.addDeg(model.getBone("cape_lower"), speed * 15 + sway * 0.5F, 0, 0);
      JinwooPose.addDeg(model.getBone("plume"), speed * 18 + sway, 0, 0);
      JinwooPose.addDeg(model.getBone("wing_right"), 0, 0, (float)Math.sin(age * 0.5) * 4 * (1 + speed * 4));
      JinwooPose.addDeg(model.getBone("wing_left"), 0, 0, -(float)Math.sin(age * 0.5) * 4 * (1 + speed * 4));
   }

   /** Pure: blocks below the ground — rise: under (0-6 t), grows up ease-out (6-20 t); dissolve sinks 80 %. */
   public static float sink(float rise, float dissolve, float height) {
      float s = 0;
      if (rise >= 0 && rise < 20) {
         float p = rise < 6 ? 0 : (rise - 6) / 14.0F;
         p = 1 - (1 - p) * (1 - p);
         s = (1 - p) * height;
      }
      if (dissolve >= 0) {
         s = Math.max(s, (float)smooth(dissolve / JinwooRules.SHADOW_DISSOLVE) * height * 0.8F);
      }
      return s;
   }

   /** Pure: glow intensity; the eye flash (20 t after a rise/command) and the domain boost brighten it. */
   public static float eyes(float sinceFlash, boolean elite, boolean empowered) {
      float base = elite ? 1.0F : 0.72F;
      if (empowered) {
         base = Math.min(1.0F, base + 0.2F);
      }
      if (sinceFlash >= 0 && sinceFlash < 20) {
         base = Math.min(1.0F, base + 0.6F * (1 - sinceFlash / 20.0F));
      }
      return base;
   }

   static double smooth(double t) {
      t = Math.max(0, Math.min(1, t));
      return t * t * (3 - 2 * t);
   }
}

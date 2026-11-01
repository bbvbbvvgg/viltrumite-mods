package dev.baranhan.viltrumitecore.client.thanos.war;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.thanos.ThanosPose;
import dev.baranhan.viltrumitecore.client.thanos.ThanosView;
import dev.baranhan.viltrumitecore.entity.thanos.BlackOrderEntity;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.CullMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.MawMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.OrderMove;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Black Order generals on their own rigs (geo/thanos/ebony_maw, cull_obsidian; clips in
 * animations/black_order/). Base loop + locomotion by the walk factor (Maw glides, Cull walks by gait),
 * the move clip from the synced move id at its unscaled length, stagger and additive hurt, the beam
 * in/out fades and the death clip (this renderer is not a LivingEntityRenderer, so the fall is the
 * clip's). The eye / telekinesis glow brightens during moves and when enraged; Cull's weapon head is
 * hidden while his chain carries it.
 */
public class BlackOrderRenderer<T extends BlackOrderEntity> extends EntityRenderer<T> {
   public static final double GLOW_LOD = 96.0;
   public static final Look MAW = new Look("ebony_maw", names(MawMove.values()), lengths(MawMove.values()), "glide", true, 0.55F, -1, 0, 0);
   public static final Look CULL = new Look("cull_obsidian", names(CullMove.values()), lengths(CullMove.values()), "walk", false, 1.15F,
      CullMove.CHAIN.ordinal(), CullMove.CHAIN.telegraph(), CullMove.CHAIN.telegraph() + CullMove.CHAIN.active());
   private final Look look;

   /**
    * One general's assets: clip names indexed by move ordinal (the synced id), their lengths, the locomotion clip,
    * whether it is sampled by time (gliding) or by gait, and the move/window that hides weapon_head.
    */
   public record Look(String name, String[] moves, int[] lengths, String locomotion, boolean glides, float shadow, int hideMove, int hideFrom,
      int hideTo) {
      public ResourceLocation geo() {
         return ThanosView.rl("geo/thanos/" + this.name + ".geo.json");
      }

      public ResourceLocation texture() {
         return ThanosView.rl("textures/entity/thanos/" + this.name + ".png");
      }

      public ResourceLocation glow() {
         return ThanosView.rl("textures/entity/thanos/" + this.name + "_glow.png");
      }

      public ResourceLocation baseClips() {
         return ThanosView.rl("animations/black_order/" + this.name + ".animation.json");
      }

      public ResourceLocation moveClips() {
         return ThanosView.rl("animations/black_order/" + this.name + "_moves.animation.json");
      }

      /** A clip by name from the general's two files, or null (resources not loaded). */
      @Nullable
      public Animation clip(String clip) {
         Animation a = AnimCache.animation(this.baseClips(), clip);
         return a != null ? a : AnimCache.animation(this.moveClips(), clip);
      }

      @Nullable
      public String move(int id) {
         return id >= 0 && id < this.moves.length ? this.moves[id] : null;
      }
   }

   public BlackOrderRenderer(EntityRendererProvider.Context context, Look look) {
      super(context);
      this.look = look;
      this.shadowRadius = look.shadow;
      this.shadowStrength = 0.7F;
   }

   static String[] names(OrderMove[] moves) {
      String[] out = new String[moves.length];
      for (int i = 0; i < moves.length; i++) {
         out[i] = ((Enum<?>)moves[i]).name().toLowerCase(java.util.Locale.ROOT);
      }

      return out;
   }

   static int[] lengths(OrderMove[] moves) {
      int[] out = new int[moves.length];
      for (int i = 0; i < moves.length; i++) {
         out[i] = moves[i].length();
      }

      return out;
   }

   @Override
   public ResourceLocation getTextureLocation(T entity) {
      return this.look.texture();
   }

   @Override
   public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
      if (!entity.shouldRender(x, y, z)) {
         return false;
      }

      return frustum.isVisible(entity.getBoundingBoxForCulling().inflate(2.0, 1.0, 2.0));
   }

   @Override
   public void render(T e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(this.look.geo());
      if (model == null) {
         return;
      }

      Pose p = pose(this.look, model, e, partial);
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      double dist = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(e));
      boolean glow = dist <= GLOW_LOD && !ShaderCompat.isShadowPass();
      GeoBone weaponHead = p.hideWeaponHead ? model.getBone("weapon_head") : null;
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
      if (weaponHead != null) {
         weaponHead.hidden = true;
      }

      try {
         RenderType type = p.alpha < 0.999F ? RenderType.entityTranslucent(this.look.texture()) : RenderType.entityCutoutNoCull(this.look.texture());
         AnimRenderer.render(model, stack, null, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, p.alpha, null);
         float g = p.glow * p.alpha;
         if (glow && g > 0.01F) {
            AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(this.look.glow())), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
         }
      } finally {
         if (weaponHead != null) {
            weaponHead.hidden = false;
         }
      }

      stack.popPose();
   }

   /** Result of posing: opacity (beam fades, death), glow level, whether the weapon head is out on the chain. */
   public static final class Pose {
      public float alpha = 1.0F;
      public float glow = 0.75F;
      public float moveWeight;
      public boolean hideWeaponHead;
   }

   private static void play(Look look, BakedGeoModel model, String name, float ticks, float weight, boolean loop) {
      Animation clip = look.clip(name);
      if (clip == null || weight <= 0.001F) {
         return;
      }

      double seconds = ticks / 20.0;
      double length = Math.max(0.05, clip.lengthSeconds());
      seconds = loop ? seconds % length : Math.min(seconds, length - 0.001);
      AnimationController.overlay(clip, model, Math.max(0.0, seconds), weight);
   }

   /** Poses the shared model for this general (also used by the portraits with a null entity: idle only). */
   public static Pose pose(Look look, BakedGeoModel model, @Nullable BlackOrderEntity e, float partial) {
      Pose p = new Pose();
      model.resetBones();
      if (e == null) {
         play(look, model, "idle", partial, 1.0F, true);
         return p;
      }

      float age = e.tickCount + partial;
      if (e.deathTime > 0 || !e.isAlive()) {
         float t = e.deathTime + partial;
         play(look, model, "idle", age, 1.0F, true);
         play(look, model, "death", t, 1.0F, false);
         p.alpha = 1.0F - 0.75F * ThanosPose.smooth((t - 14.0F) / 6.0F);
         p.glow = 0.75F * (1.0F - ThanosPose.smooth(t / 10.0F));
         return p;
      }

      byte state = e.state();
      float sinceState = Math.max(0.0F, age - e.clientStateStart);
      if (state == BlackOrderEntity.STATE_BEAM_IN) {
         play(look, model, "beam_in", sinceState, 1.0F, false);
         p.alpha = Mth.clamp(0.15F + ThanosPose.smooth(sinceState / 14.0F), 0.0F, 1.0F);
         p.glow = 1.0F;
         return p;
      }

      if (state == BlackOrderEntity.STATE_BEAM_OUT) {
         play(look, model, "beam_out", sinceState, 1.0F, false);
         p.alpha = 1.0F - 0.95F * ThanosPose.smooth((sinceState - 8.0F) / 16.0F);
         p.glow = 1.0F;
         return p;
      }

      play(look, model, "idle", age, 1.0F, true);
      float walk = Mth.clamp(Mth.lerp(partial, e.clientWalkO, e.clientWalk) * 1.6F, 0.0F, 1.0F);
      if (walk > 0.02F) {
         Animation loco = look.clip(look.locomotion());
         if (loco != null) {
            double t;
            if (look.glides()) {
               t = (age / 20.0) % loco.lengthSeconds();
            } else {
               double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
               t = (gait - Math.floor(gait)) * loco.lengthSeconds();
            }

            AnimationController.overlay(loco, model, t, walk);
         }
      }

      float sinceReact = age - e.clientReactStart;
      boolean staggered = e.reaction() == BlackOrderEntity.REACT_STAGGER && sinceReact >= 0.0F && sinceReact < 30.0F;
      String move = look.move(e.moveId());
      if (move != null && !staggered) {
         int id = e.moveId();
         float elapsed = age - e.clientMoveStart;
         float w = ThanosPose.clipWeight(elapsed, look.lengths()[id]);
         if (w > 0.0F) {
            p.moveWeight = w;
            play(look, model, move, elapsed, w, false);
            p.hideWeaponHead = id == look.hideMove() && elapsed >= look.hideFrom() && elapsed < look.hideTo();
         }
      }

      if (staggered) {
         play(look, model, "stagger", sinceReact, ThanosPose.clipWeight(sinceReact, 30.0F), false);
      } else if (e.reaction() == BlackOrderEntity.REACT_HURT && sinceReact >= 0.0F && sinceReact < 8.0F) {
         Animation hurt = look.clip("hurt");
         if (hurt != null) {
            AnimationController.additive(hurt, model, sinceReact / 20.0, 1.0F);
         }
      }

      lookAt(model, e, partial, (1.0F - 0.8F * p.moveWeight) * (staggered ? 0.0F : 1.0F));
      boolean enraged = e.flag(BlackOrderEntity.FLAG_ENRAGED);
      p.glow = Math.min(1.0F, (enraged ? 0.9F : 0.7F) + 0.3F * p.moveWeight + (enraged ? 0.1F * (float)Math.sin(age * 0.4F) : 0.0F));
      return p;
   }

   /** Head toward the target (runtime bone angles as the shared rigs: rotY - turns right, rotX - looks down). */
   private static void lookAt(BakedGeoModel model, BlackOrderEntity e, float partial, float weight) {
      GeoBone head = model.getBone("head");
      if (head == null || weight <= 0.02F || e.level() == null) {
         return;
      }

      Entity target = e.level().getEntity(e.targetId());
      if (target == null) {
         return;
      }

      Vec3 from = e.getEyePosition(partial);
      Vec3 d = target.getEyePosition(partial).subtract(from);
      double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
      if (horizontal < 0.5) {
         return;
      }

      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      float want = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
      float rel = Mth.clamp(Mth.wrapDegrees(want - bodyYaw), -45.0F, 45.0F);
      float down = Mth.clamp((float)(-Math.toDegrees(Math.atan2(d.y, horizontal))), -35.0F, 35.0F);
      head.rotY += (float)Math.toRadians(-rel) * weight;
      head.rotX += (float)Math.toRadians(-down * 0.6F) * weight;
   }
}

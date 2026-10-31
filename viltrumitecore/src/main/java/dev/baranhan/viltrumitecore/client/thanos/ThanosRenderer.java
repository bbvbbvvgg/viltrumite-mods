package dev.baranhan.viltrumitecore.client.thanos;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Thanos: own geo (38 bones, the Infinity Gauntlet on the left hand), 34 clips. Idle/walk by gait,
 * turns by the body's turn rate, the move clip with the phase's telegraph, hurt (additive) and stun,
 * intro / triumph / snap (scene clock) / defeat; every stone has its own glow pass and intensity.
 * The defeat ends in World Erasure's cell dissolve turned to ash ({@link ThanosCells}). World
 * anchors of the hands, head and stones are kept per frame for the effects and cameras.
 */
public class ThanosRenderer extends EntityRenderer<ThanosEntity> {
   public static final double GLOW_LOD = 112.0;
   public static final ResourceLocation WHITE = ThanosView.rl("textures/entity/thanos/white.png");
   private static final Map<Integer, Anchors> ANCHORS = new HashMap<>();
   private static final Map<Integer, List<ThanosCells.Mote>> MOTES = new HashMap<>();
   private static long frame;

   /** World points of one Thanos this frame (absolute world coordinates). */
   public record Anchors(Vec3 gauntlet, Vec3 fist, Vec3 head, Vec3 chest, Vec3[] stones, float[] glow, long frame) {
   }

   /** Pose result: stone intensities, eye glow and the defeat's ash front. */
   public static final class Look {
      public final float[] stones = new float[ThanosStone.values().length];
      public float eyes = 0.8F;
      public float crumble;
      public float moveWeight;
   }

   public ThanosRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 1.3F;
      this.shadowStrength = 0.8F;
   }

   @Override
   public ResourceLocation getTextureLocation(ThanosEntity entity) {
      return ThanosView.TEXTURE;
   }

   @Override
   public boolean shouldRender(ThanosEntity entity, Frustum frustum, double x, double y, double z) {
      if (ThanosSceneClient.ownsThanos(entity)) {
         return false; // the scene draws him after the dissolve pass
      }

      if (!entity.shouldRender(x, y, z)) {
         return false;
      }

      return frustum.isVisible(entity.getBoundingBoxForCulling().inflate(2.6, 1.2, 2.6));
   }

   @Override
   public void render(ThanosEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(ThanosView.GEO);
      if (model == null) {
         return;
      }

      Look look = pose(model, e, partial);
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      Vec3 at = e.getPosition(partial);
      capture(model, e.getId(), at, bodyYaw, look);
      double dist = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(e));
      boolean glow = dist <= GLOW_LOD && !ShaderCompat.isShadowPass();
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
      if (look.crumble > 0.0F) {
         Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
         List<ThanosCells.Mote> motes = new ArrayList<>();
         float feet = (float)at.y;
         ThanosCells.Rule rule = (wx, wy, wz, noise) -> 0.6F * (1.0F - Mth.clamp((float)(wy - feet) / 3.8F, 0.0F, 1.0F)) + 0.4F * noise;
         drawCells(model, stack, buffers, light, overlay, look, RenderSystem.getInverseViewRotationMatrix(), cam, rule, look.crumble * 1.1F, 0.09F,
            0xFF7A30, 0.0F, 0.8F, false, e.getUUID().getLeastSignificantBits(), motes);
         MOTES.put(e.getId(), motes);
      } else {
         MOTES.remove(e.getId());
         draw(model, stack, buffers, light, overlay, 1.0F, look, glow);
      }

      stack.popPose();
   }

   /** Ash motes released by the last crumble frame of this Thanos (absolute world positions). */
   public static List<ThanosCells.Mote> motes(int id) {
      List<ThanosCells.Mote> out = MOTES.get(id);
      return out == null ? List.of() : out;
   }

   @Nullable
   public static Anchors anchors(int id) {
      return ANCHORS.get(id);
   }

   /** Called once per frame by the effects: anchors older than a second are dropped. */
   public static void newFrame() {
      frame++;
      if ((frame & 63) == 0) {
         ANCHORS.values().removeIf(a -> frame - a.frame() > 120);
         MOTES.clear();
      }
   }

   // ---- drawing -------------------------------------------------------------------------------

   /** Body (cutout, or translucent while faded) and the glow passes: eyes, then each stone alone. */
   public static void draw(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, int light, int overlay, float alpha, Look look, boolean glow) {
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(ThanosView.TEXTURE) : RenderType.entityCutoutNoCull(ThanosView.TEXTURE);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, null);
      if (glow) {
         drawGlow(model, stack, buffers, look, alpha, 1.0F, 1.0F, 1.0F);
      }
   }

   /** Ghost of his body (Reality mirages, Soul nova silhouettes): translucent, tinted, glowing. */
   public static void drawGhost(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, float alpha, int rgb, Look look) {
      float r = ThanosView.r(rgb);
      float g = ThanosView.g(rgb);
      float b = ThanosView.b(rgb);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityTranslucent(ThanosView.TEXTURE)), LightTexture.FULL_BRIGHT,
         OverlayTexture.NO_OVERLAY, r, g, b, alpha, null);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(WHITE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         r * alpha * 0.22F, g * alpha * 0.22F, b * alpha * 0.22F, 1.0F, null);
      drawGlow(model, stack, buffers, look, alpha, r, g, b);
   }

   private static void drawGlow(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, Look look, float alpha, float r, float g, float b) {
      GeoBone[] stones = stoneBones(model);
      VertexConsumer glowBuffer = buffers.getBuffer(RenderType.eyes(ThanosView.GLOW));
      for (GeoBone bone : stones) {
         if (bone != null) {
            bone.hidden = true;
         }
      }

      float eyes = look.eyes * alpha;
      if (eyes > 0.01F) {
         AnimRenderer.render(model, stack, null, glowBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, eyes * r, eyes * g, eyes * b, 1.0F, null);
      }

      List<GeoBone> all = new ArrayList<>();
      for (GeoBone bone : model.allBones()) {
         all.add(bone);
         bone.hidden = true;
      }

      try {
         for (int i = 0; i < stones.length; i++) {
            GeoBone bone = stones[i];
            float v = look.stones[i] * alpha;
            if (bone == null || v <= 0.01F) {
               continue;
            }

            bone.hidden = false;
            // above 1 the stone blazes: a second additive pass
            for (float left = v; left > 0.01F; left -= 1.0F) {
               float k = Math.min(1.0F, left);
               AnimRenderer.render(model, stack, null, glowBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, k * r, k * g, k * b, 1.0F, null);
            }

            bone.hidden = true;
         }
      } finally {
         for (GeoBone bone : all) {
            bone.hidden = false;
         }
      }
   }

   /** Body through the cell dissolve ({@link ThanosCells}) with its hot band; glow passes while it is still whole enough. */
   public static void drawCells(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, int light, int overlay, Look look, Matrix3f toWorld,
      Vec3 cam, ThanosCells.Rule rule, float front, float edge, int edgeRgb, float hot, float ash, boolean forming, long seed,
      List<ThanosCells.Mote> motes) {
      VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(ThanosView.TEXTURE));
      VertexConsumer shell = buffers.getBuffer(RenderType.eyes(WHITE));
      ThanosCells cells = new ThanosCells(body, shell, toWorld, cam.x, cam.y, cam.z, rule, front, edge, edgeRgb, hot, ash, forming, seed, motes);
      AnimRenderer.render(model, stack, null, cells, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
      float whole = 1.0F - Mth.clamp(front * 1.6F, 0.0F, 1.0F);
      if (!forming && whole > 0.02F) {
         drawGlow(model, stack, buffers, look, whole, 1.0F, 1.0F, 1.0F);
      }
   }

   private static final Map<BakedGeoModel, GeoBone[]> STONE_BONES = new HashMap<>();

   private static GeoBone[] stoneBones(BakedGeoModel model) {
      return STONE_BONES.computeIfAbsent(model, m -> {
         GeoBone[] out = new GeoBone[ThanosView.STONE_BONES.length];
         for (int i = 0; i < out.length; i++) {
            out[i] = m.getBone(ThanosView.STONE_BONES[i]);
         }

         return out;
      });
   }

   // ---- posing --------------------------------------------------------------------------------

   private static void play(BakedGeoModel model, String name, float ticks, float weight, boolean loop) {
      Animation clip = ThanosView.clip(name);
      if (clip == null || weight <= 0.001F) {
         return;
      }

      double seconds = ticks / 20.0;
      double length = Math.max(0.05, clip.lengthSeconds());
      seconds = loop ? seconds % length : Math.min(seconds, length - 0.001);
      AnimationController.overlay(clip, model, Math.max(0.0, seconds), weight);
   }

   /** Poses the shared model for this Thanos and returns the light of his stones. */
   public static Look pose(BakedGeoModel model, ThanosEntity e, float partial) {
      Look look = new Look();
      model.resetBones();
      float age = e.tickCount + partial;
      float scene = ThanosSceneClient.snapTime(partial);
      byte state = e.state();
      if (scene >= 0.0F) {
         snapPose(model, scene, look);
         return look;
      }

      if (state == ThanosEntity.STATE_SNAP) {
         // the timer ran out: triumph until the world stops
         play(model, "victory", age, 1.0F, true);
         for (int i = 0; i < look.stones.length; i++) {
            look.stones[i] = 1.0F + 0.2F * (float)Math.sin(age * 0.5F + i);
         }

         return look;
      }

      if (state == ThanosEntity.STATE_INTRO) {
         float t = Math.max(0.0F, age - e.clientStateStart);
         play(model, "intro", t, 1.0F, false);
         for (int i = 0; i < look.stones.length; i++) {
            look.stones[i] = ThanosPose.introGlow(i, t);
         }

         return look;
      }

      if (state == ThanosEntity.STATE_VICTORY) {
         float t = ThanosClient.victoryTime(e, partial);
         play(model, "defeat", t, 1.0F, false);
         for (int i = 0; i < look.stones.length; i++) {
            look.stones[i] = ThanosPose.victoryGlow(i, t);
         }

         look.eyes = 0.8F * (1.0F - ThanosPose.smooth((t - ThanosSceneTimeline.VICTORY_KNEEL_END) / 60.0F));
         look.crumble = ThanosSceneTimeline.victoryCrumble(t);
         return look;
      }

      fightPose(model, e, partial, age, look);
      return look;
   }

   private static void snapPose(BakedGeoModel model, float t, Look look) {
      if (t < 165.0F) {
         play(model, "snap", t, 1.0F, false);
      } else {
         play(model, "snap", 164.9F, 1.0F, false);
         play(model, "snap_hold", t - 165.0F, ThanosPose.smooth((t - 165.0F) / 20.0F), true);
      }

      for (int i = 0; i < look.stones.length; i++) {
         look.stones[i] = ThanosPose.snapGlow(i, t);
      }

      look.eyes = t < ThanosSceneTimeline.SNAP ? 0.9F : 0.5F;
   }

   private static void fightPose(BakedGeoModel model, ThanosEntity e, float partial, float age, Look look) {
      play(model, "idle_fight", age, 1.0F, true);
      float walk = Mth.clamp(Mth.lerp(partial, e.clientWalkO, e.clientWalk) * 1.7F, 0.0F, 1.0F);
      if (walk > 0.02F) {
         double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
         Animation clip = ThanosView.clip("walk_fight");
         if (clip != null) {
            AnimationController.overlay(clip, model, (gait - Math.floor(gait)) * clip.lengthSeconds(), walk);
         }
      }

      float turn = e.clientTurn;
      float turnWeight = Mth.clamp((Math.abs(turn) - 0.8F) / 5.0F, 0.0F, 1.0F) * (1.0F - walk);
      if (turnWeight > 0.02F) {
         play(model, turn > 0.0F ? "turn_right" : "turn_left", age, turnWeight, true);
      }

      ThanosPhase phase = e.phase();
      ThanosMove move = e.move();
      float elapsed = age - e.clientMoveStart;
      float sinceReact = age - e.clientReactStart;
      boolean stunned = e.reaction() == ThanosEntity.REACT_STUN && sinceReact < 40.0F;
      int tel = move == null ? 1 : ThanosRules.telegraph(move.telegraph, phase);
      boolean moving = false;
      if (move != null && !stunned) {
         float length = tel + move.active + move.recovery;
         float w = ThanosPose.clipWeight(elapsed, length);
         if (w > 0.0F) {
            moving = true;
            look.moveWeight = w;
            play(model, move.name().toLowerCase(Locale.ROOT), ThanosPose.clipTicks(elapsed, move.telegraph, tel), w, false);
         }
      }

      if (stunned) {
         play(model, "stun", sinceReact, ThanosPose.clipWeight(sinceReact, 40.0F), false);
      } else if (e.reaction() == ThanosEntity.REACT_HURT && sinceReact < 8.0F) {
         Animation hurt = ThanosView.clip("hurt");
         if (hurt != null) {
            AnimationController.additive(hurt, model, sinceReact / 20.0, 1.0F);
         }
      }

      look(model, e, partial, 1.0F - 0.7F * look.moveWeight - (stunned ? 0.8F : 0.0F));
      boolean late = phase == ThanosPhase.FINAL;
      for (int i = 0; i < look.stones.length; i++) {
         look.stones[i] = ThanosPose.fightGlow(i, age, late, moving ? move : null, elapsed, tel);
      }

      look.eyes = late ? 1.0F : 0.8F;
      if (stunned) {
         for (int i = 0; i < look.stones.length; i++) {
            look.stones[i] *= 0.6F + 0.4F * (float)Math.abs(Math.sin(age * 0.9F + i));
         }
      }
   }

   /** Head toward his target (runtime bone angles: rotY - turns right, rotX - looks down, as the shared rigs). */
   private static void look(BakedGeoModel model, ThanosEntity e, float partial, float weight) {
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
      float rel = Mth.clamp(Mth.wrapDegrees(want - bodyYaw), -50.0F, 50.0F);
      float down = Mth.clamp((float)(-Math.toDegrees(Math.atan2(d.y, horizontal))), -30.0F, 35.0F);
      head.rotY += (float)Math.toRadians(-rel) * weight;
      head.rotX += (float)Math.toRadians(-down * 0.6F) * weight;
   }

   // ---- anchors -------------------------------------------------------------------------------

   private static void capture(BakedGeoModel model, int id, Vec3 at, float bodyYaw, Look look) {
      Matrix4f base = new Matrix4f().translation((float)at.x, (float)at.y, (float)at.z).rotateY((float)Math.toRadians(180.0F - bodyYaw));
      Vec3[] stones = new Vec3[ThanosView.STONE_BONES.length];
      GeoBone[] bones = stoneBones(model);
      for (int i = 0; i < stones.length; i++) {
         stones[i] = bones[i] == null ? null : point(bones[i], base);
      }

      GeoBone hand = model.getBone("left_hand");
      GeoBone fist = model.getBone("right_hand");
      GeoBone head = model.getBone("head");
      GeoBone torso = model.getBone("torso");
      Vec3 gauntlet = hand == null ? at.add(0, 2.6, 0) : point(hand, base);
      ANCHORS.put(id, new Anchors(gauntlet, fist == null ? gauntlet : point(fist, base), head == null ? at.add(0, 3.4, 0) : point(head, base),
         torso == null ? at.add(0, 2.4, 0) : point(torso, base), stones, look.stones.clone(), frame));
   }

   /** World point of a bone's pivot after the current pose (the AnimRenderer transform chain). */
   public static Vec3 point(GeoBone bone, Matrix4f base) {
      ArrayDeque<GeoBone> chain = new ArrayDeque<>();
      for (GeoBone b = bone; b != null; b = b.parent) {
         chain.push(b);
      }

      Matrix4f m = new Matrix4f(base);
      for (GeoBone b : chain) {
         if (b.posX != 0.0F || b.posY != 0.0F || b.posZ != 0.0F) {
            m.translate(-b.posX / 16.0F, b.posY / 16.0F, b.posZ / 16.0F);
         }

         m.translate(b.pivotX / 16.0F, b.pivotY / 16.0F, b.pivotZ / 16.0F);
         if (b.rotZ != 0.0F) {
            m.rotateZ(b.rotZ);
         }

         if (b.rotY != 0.0F) {
            m.rotateY(b.rotY);
         }

         if (b.rotX != 0.0F) {
            m.rotateX(b.rotX);
         }

         if (b.scaleX != 1.0F || b.scaleY != 1.0F || b.scaleZ != 1.0F) {
            m.scale(b.scaleX, b.scaleY, b.scaleZ);
         }

         m.translate(-b.pivotX / 16.0F, -b.pivotY / 16.0F, -b.pivotZ / 16.0F);
      }

      Vector4f p = m.transform(new Vector4f(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F, 1.0F));
      return new Vec3(p.x, p.y, p.z);
   }

   /** Poses and draws Thanos at a fixed world transform without an entity (the scene after his removal). */
   public static void drawStandalone(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, Vec3 at, float bodyYaw, Look look, int light) {
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      draw(model, stack, buffers, light, OverlayTexture.NO_OVERLAY, 1.0F, look, true);
      stack.popPose();
   }

   /** Scene posing without an entity: the snap clip at scene time t. */
   public static Look poseSnap(BakedGeoModel model, float t) {
      Look look = new Look();
      model.resetBones();
      snapPose(model, t, look);
      return look;
   }

   static Minecraft mc() {
      return Minecraft.getInstance();
   }
}

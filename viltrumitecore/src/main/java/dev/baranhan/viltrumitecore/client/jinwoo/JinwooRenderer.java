package dev.baranhan.viltrumitecore.client.jinwoo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.mixin.EntityRendererNameTagInvoker;
import dev.baranhan.viltrumitecore.client.reinhard.NameTagRules;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFlags;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Jinwoo replaces the player model: the Steve-shaped body wearing the Jinwoo skin plus the gear mesh
 * (hair, collar, coat tails, eye glow, the two daggers as children of the hands), one skeleton and
 * one clip set. First person draws the arms model (skin sleeves + daggers) in camera space at
 * {@link #FP_OFFSET} while the daggers are out or a move runs; otherwise the vanilla hand (with the
 * Jinwoo skin through HeroSkins) shows the held item. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class JinwooRenderer {
   static final float PLAYER_SCALE = 0.9375F;
   static final float TRANSLUCENT_ALPHA = 0.15F;
   /** Camera-space offset of the first-person arms model (blocks); keep in sync with tools/assets/jinwoo_fp_clips.py. */
   public static final Vector3f FP_OFFSET = new Vector3f(0.0F, -1.66F, -0.30F);
   /** Dagger tip/base in model space (blocks), found once per baked model from the blade cubes. */
   private static final Map<GeoBone, Vector3f[]> BLADES = new IdentityHashMap<>();

   private JinwooRenderer() {
   }

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }
      HeroPublicSnapshot s = JinwooView.of(player);
      if (s == null) {
         JinwooPose.reset(player);
         return;
      }
      BakedGeoModel body = AnimCache.model(JinwooView.BODY);
      BakedGeoModel gear = AnimCache.model(JinwooView.GEAR);
      if (body == null) {
         return;
      }
      event.setCanceled(true);
      Minecraft mc = Minecraft.getInstance();
      float partial = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      nameTag(player, event.getRenderer(), stack, buffers, light, partial);
      ReinhardPoses.Passes passes = ReinhardPoses.passes(player.isInvisible(), mc.player != null && !player.isInvisibleTo(mc.player),
         mc.shouldEntityAppearGlowing(player));
      if (!passes.body() && !passes.outline()) {
         return;
      }

      JinwooPose.thirdPerson(body, player, s, partial);
      applyHead(body, player, partial);
      ItemStack held = player.getMainHandItem();
      boolean showItem = !held.isEmpty() && JinwooPose.daggerScale(s, JinwooPose.actionTicks(player, s, partial)) <= 0.0F && s.actionId() < 0;
      if (showItem) {
         // Hands out of the pockets: the right hand carries the item a little forward.
         JinwooPose.addDeg(body.getBone("right_arm"), -18, 0, 4);
         JinwooPose.addDeg(body.getBone("right_forearm"), -30, 0, 0);
      }
      if (gear != null) {
         JinwooPose.copyPose(body, gear);
         JinwooPose.hair(gear, player);
      }
      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      float swim = player.getSwimAmount(partial);
      stack.pushPose();
      setupRotations(player, stack, partial, swim);
      stack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
      Matrix4f[] blades = new Matrix4f[2];
      Matrix4f[] hand = new Matrix4f[1];
      AnimRenderer.BoneHook hook = (bone, pose, b, l, o) -> {
         if (bone.name.equals("right_dagger")) {
            blades[0] = new Matrix4f(pose.last().pose());
         } else if (bone.name.equals("left_dagger")) {
            blades[1] = new Matrix4f(pose.last().pose());
         } else if (bone.name.equals("right_hand")) {
            hand[0] = new Matrix4f(pose.last().pose());
         }
      };
      boolean shadowPass = ShaderCompat.isShadowPass();
      if (passes.body()) {
         float alpha = passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F;
         RenderType skin = passes.translucent() ? RenderType.entityTranslucent(JinwooView.SKIN) : RenderType.entityCutoutNoCull(JinwooView.SKIN);
         AnimRenderer.render(body, stack, null, buffers.getBuffer(skin), light, overlay, 1, 1, 1, alpha, null);
         if (gear != null) {
            RenderType g = passes.translucent() ? RenderType.entityTranslucent(JinwooView.GEAR_TEX) : RenderType.entityCutoutNoCull(JinwooView.GEAR_TEX);
            AnimRenderer.render(gear, stack, buffers, buffers.getBuffer(g), light, overlay, 1, 1, 1, alpha, hook);
         }
      }
      if (passes.outline() && !shadowPass) {
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(JinwooView.SKIN)), light, overlay, 1, 1, 1, 1, null);
      }
      if (passes.extras() && gear != null && !shadowPass) {
         float glow = JinwooView.flag(s, JinwooFlags.ULT) || JinwooView.domainLeft(s) > 0 ? 1.0F : 0.85F;
         AnimRenderer.render(gear, stack, null, buffers.getBuffer(RenderType.eyes(JinwooView.GEAR_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F, null);
      }
      if (passes.extras() && showItem && hand[0] != null) {
         stack.pushPose();
         stack.last().pose().set(hand[0]);
         GeoBone h = body.getBone("right_hand");
         // hand bone frame -> palm: pivot (blocks), then vanilla's held-item orientation
         stack.translate(h.pivotX / 16.0F, h.pivotY / 16.0F - 0.12F, h.pivotZ / 16.0F - 0.02F);
         stack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(180.0F));
         stack.scale(1.0F / PLAYER_SCALE, 1.0F / PLAYER_SCALE, 1.0F / PLAYER_SCALE);
         mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
            stack, buffers, light);
         stack.popPose();
      }
      stack.popPose();
      if (!shadowPass && gear != null && passes.extras()) {
         recordTrails(player, s, gear, blades, hand[0]);
      }
   }

   /** Dagger tips to world space for the ribbon trails, palm for the authority threads. */
   private static void recordTrails(AbstractClientPlayer player, HeroPublicSnapshot s, BakedGeoModel gear, Matrix4f[] blades, @Nullable Matrix4f hand) {
      var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      Vec3 cam = camera.getPosition();
      for (int i = 0; i < 2; i++) {
         GeoBone bone = gear.getBone(i == 0 ? "right_dagger" : "left_dagger");
         if (blades[i] == null || bone == null || bone.hidden) {
            continue;
         }
         Vector3f[] tb = blade(bone);
         Vector3f tip = blades[i].transformPosition(new Vector3f(tb[0]));
         Vector3f base = blades[i].transformPosition(new Vector3f(tb[1]));
         DaggerTrails.record(player.getId(), i, toWorld(cam, camera, tip), toWorld(cam, camera, base), DaggerTrails.emitting(s));
      }
      if (hand != null) {
         GeoBone h = gear.getBone("right_hand");
         if (h != null) {
            Vector3f palm = hand.transformPosition(new Vector3f(h.pivotX / 16.0F, h.pivotY / 16.0F - 0.15F, h.pivotZ / 16.0F));
            JinwooVfx.palm(player.getId(), toWorld(cam, camera, palm));
         }
      }
   }

   static Vec3 toWorld(Vec3 cam, net.minecraft.client.Camera camera, Vector3f view) {
      return cam.add(dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.viewToWorld(new Vec3(view.x, view.y, view.z), camera.rotation()));
   }

   /** [tip, base] of a dagger bone: the blade vertex farthest from the bone pivot, base a quarter along. */
   static Vector3f[] blade(GeoBone bone) {
      return BLADES.computeIfAbsent(bone, b -> {
         Vector3f pivot = new Vector3f(b.pivotX / 16.0F, b.pivotY / 16.0F, b.pivotZ / 16.0F);
         Vector3f best = new Vector3f(pivot);
         float bestD = -1;
         for (GeoBone.Cube cube : b.cubes) {
            for (GeoBone.Quad q : cube.quads()) {
               for (GeoBone.Vertex v : q.vertices()) {
                  Vector3f p = new Vector3f(v.x(), v.y(), v.z());
                  float d = p.distanceSquared(pivot);
                  if (d > bestD) {
                     bestD = d;
                     best = p;
                  }
               }
            }
         }
         Vector3f base = new Vector3f(pivot).lerp(best, 0.22F);
         return new Vector3f[]{best, base};
      });
   }

   /**
    * First person. Calm with an item: vanilla (the item shows). Daggers out, blocking or any move:
    * both vanilla hands are cancelled and the arms model is drawn once, in camera space.
    */
   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      AbstractClientPlayer player = mc.player;
      HeroPublicSnapshot s = JinwooView.of(player);
      if (s == null) {
         return;
      }
      if (JinwooUltScene.cameraActive()) {
         event.setCanceled(true);
         return;
      }
      float partial = event.getPartialTick();
      double ticks = JinwooPose.actionTicks(player, s, partial);
      boolean ours = JinwooPose.daggerScale(s, ticks) > 0.0F || s.actionId() >= 0 || JinwooView.flag(s, JinwooFlags.BLOCKING)
         || (player.getMainHandItem().isEmpty() && !player.swinging);
      if (!ours) {
         return;
      }
      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND || player.isInvisible()) {
         return;
      }
      BakedGeoModel arms = AnimCache.model(JinwooView.ARMS);
      BakedGeoModel armsGear = AnimCache.model(JinwooView.ARMS_GEAR);
      if (arms == null) {
         return;
      }
      JinwooPose.firstPerson(arms, player, s, partial);
      if (armsGear != null) {
         JinwooPose.copyPose(arms, armsGear);
      }
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      stack.pushPose();
      stack.translate(FP_OFFSET.x, FP_OFFSET.y, FP_OFFSET.z);
      Matrix4f[] blades = new Matrix4f[2];
      AnimRenderer.BoneHook hook = (bone, pose, b, l, o) -> {
         if (bone.name.equals("right_dagger")) {
            blades[0] = new Matrix4f(pose.last().pose());
         } else if (bone.name.equals("left_dagger")) {
            blades[1] = new Matrix4f(pose.last().pose());
         }
      };
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(JinwooView.SKIN)), light, OverlayTexture.NO_OVERLAY,
         1, 1, 1, 1, null);
      if (armsGear != null) {
         AnimRenderer.render(armsGear, stack, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(JinwooView.GEAR_TEX)), light,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, hook);
         AnimRenderer.render(armsGear, stack, null, buffers.getBuffer(RenderType.eyes(JinwooView.GEAR_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, null);
      }
      stack.popPose();
      if (armsGear != null) {
         for (int i = 0; i < 2; i++) {
            GeoBone bone = armsGear.getBone(i == 0 ? "right_dagger" : "left_dagger");
            if (blades[i] == null || bone == null || bone.hidden) {
               continue;
            }
            Vector3f[] tb = blade(bone);
            DaggerTrails.recordView(i, blades[i].transformPosition(new Vector3f(tb[0])), blades[i].transformPosition(new Vector3f(tb[1])),
               DaggerTrails.emitting(s));
         }
         DaggerTrails.renderView(stack, buffers);
      }
   }

   static void setupRotations(AbstractClientPlayer player, PoseStack stack, float partial, float swim) {
      float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
      if (player.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
         Direction bed = player.getBedOrientation();
         if (bed != null) {
            float shift = player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) - 0.1F;
            stack.translate(-bed.getStepX() * shift, 0.0F, -bed.getStepZ() * shift);
         }
         stack.mulPose(Axis.YP.rotationDegrees(bed != null ? ReinhardPoses.sleepYaw(bed) : bodyYaw));
         stack.mulPose(Axis.ZP.rotationDegrees(90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(270.0F));
         return;
      }
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      if (player.deathTime > 0) {
         float dying = Math.min(1.0F, Mth.sqrt((player.deathTime + partial - 1.0F) / 20.0F * 1.6F));
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
      }
      if (player.isFallFlying()) {
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.elytraPitch(player.getFallFlyingTicks() + partial, player.getXRot(), player.isAutoSpinAttack())));
         Vec3 view = player.getViewVector(partial);
         Vec3 motion = player.getDeltaMovementLerped(partial);
         double motionSqr = motion.horizontalDistanceSqr();
         double viewSqr = view.horizontalDistanceSqr();
         if (motionSqr > 0.0 && viewSqr > 0.0) {
            double cos = (motion.x * view.x + motion.z * view.z) / Math.sqrt(motionSqr * viewSqr);
            double side = motion.x * view.z - motion.z * view.x;
            stack.mulPose(Axis.YP.rotation((float)(Math.signum(side) * Math.acos(Mth.clamp(cos, -1.0, 1.0)))));
         }
      } else if (swim > 0.0F) {
         boolean inWater = player.isInWater() || player.isInFluidType((fluid, height) -> player.canSwimInFluidType(fluid));
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.swimPitch(swim, inWater, player.getXRot())));
         if (player.isVisuallySwimming()) {
            stack.translate(0.0F, -1.0F, 0.3F);
         }
      }
   }

   static void applyHead(BakedGeoModel body, AbstractClientPlayer player, float partial) {
      GeoBone head = body.getBone("head");
      if (head == null) {
         return;
      }
      float yaw = Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
      float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
      head.rotY += (float)Math.toRadians(-Mth.clamp(yaw, -75, 75));
      head.rotX += (float)Math.toRadians(-pitch);
   }

   private static void nameTag(AbstractClientPlayer player, PlayerRenderer renderer, PoseStack stack, MultiBufferSource buffers, int light, float partial) {
      EntityRendererNameTagInvoker invoker = (EntityRendererNameTagInvoker)renderer;
      RenderNameTagEvent event = new RenderNameTagEvent(player, player.getDisplayName(), renderer, stack, buffers, light, partial);
      MinecraftForge.EVENT_BUS.post(event);
      Event.Result result = event.getResult();
      if (NameTagRules.show(result == Event.Result.DENY, result == Event.Result.ALLOW, invoker.viltrumitecore$shouldShowName(player))) {
         invoker.viltrumitecore$renderNameTag(player, event.getContent(), stack, buffers, light);
      }
   }
}

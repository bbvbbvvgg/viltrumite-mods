package dev.baranhan.viltrumitecore.client.captain;

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
import dev.baranhan.viltrumitecore.hero.captain.CaptainFlags;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Captain America replaces the player model: the Steve-shaped body in the HD suit skin, the gear mesh (helmet
 * wings, straps, pouches, gloves, boots) and the vibranium shield drawn on the empty bone {@code left_shield}
 * of the left forearm (hidden while thrown, glowing once the kinetic charge empowers it). First person draws
 * the arms model with the shield in camera space at {@link #FP_OFFSET}. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class CaptainRenderer {
   static final float PLAYER_SCALE = 0.9375F;
   static final float TRANSLUCENT_ALPHA = 0.15F;
   /** Camera-space offset of the first-person arms model (blocks); keep in sync with tools/assets/captain_fp_clips.py. */
   public static final org.joml.Vector3f FP_OFFSET = new org.joml.Vector3f(0.0F, -1.72F, -0.38F);

   private CaptainRenderer() {
   }

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }
      HeroPublicSnapshot s = CaptainView.of(player);
      if (s == null) {
         CaptainPose.reset(player);
         return;
      }
      BakedGeoModel body = AnimCache.model(CaptainView.BODY);
      BakedGeoModel gear = AnimCache.model(CaptainView.GEAR);
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
      CaptainPose.thirdPerson(body, player, s, partial);
      applyHead(body, player, partial);
      ItemStack held = player.getMainHandItem();
      boolean showItem = !held.isEmpty() && s.actionId() < 0 && !CaptainView.flag(s, CaptainFlags.GUARDING);
      if (showItem) {
         CaptainPose.addDeg(body.getBone("right_arm"), -18, 0, 4);
         CaptainPose.addDeg(body.getBone("right_forearm"), -30, 0, 0);
      }
      if (gear != null) {
         CaptainPose.copyPose(body, gear);
         CaptainPose.shield(gear, s, CaptainPose.actionTicks(player, s, partial));
      }
      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      float swim = player.getSwimAmount(partial);
      stack.pushPose();
      setupRotations(player, stack, partial, swim);
      stack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
      Matrix4f[] shieldPose = new Matrix4f[1];
      Matrix3f[] shieldNormal = new Matrix3f[1];
      Matrix4f[] hand = new Matrix4f[1];
      AnimRenderer.BoneHook hook = (bone, pose, b, l, o) -> {
         if (bone.name.equals("left_shield")) {
            shieldPose[0] = new Matrix4f(pose.last().pose());
            shieldNormal[0] = new Matrix3f(pose.last().normal());
         } else if (bone.name.equals("right_hand")) {
            hand[0] = new Matrix4f(pose.last().pose());
         }
      };
      boolean shadowPass = ShaderCompat.isShadowPass();
      float alpha = passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F;
      GeoBone shieldBone = gear == null ? null : gear.getBone("left_shield");
      boolean shieldShown = shieldBone != null && !shieldBone.hidden;
      boolean empowered = CaptainView.flag(s, CaptainFlags.EMPOWERED);
      if (passes.body()) {
         RenderType skin = passes.translucent() ? RenderType.entityTranslucent(CaptainView.SKIN) : RenderType.entityCutoutNoCull(CaptainView.SKIN);
         AnimRenderer.render(body, stack, null, buffers.getBuffer(skin), light, overlay, 1, 1, 1, alpha, null);
         if (gear != null) {
            RenderType g = passes.translucent() ? RenderType.entityTranslucent(CaptainView.GEAR_TEX) : RenderType.entityCutoutNoCull(CaptainView.GEAR_TEX);
            AnimRenderer.render(gear, stack, buffers, buffers.getBuffer(g), light, overlay, 1, 1, 1, alpha, hook);
         }
      }
      if (passes.outline() && !shadowPass) {
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(CaptainView.SKIN)), light, overlay, 1, 1, 1, 1, null);
      }
      if (passes.extras() && gear != null && !shadowPass) {
         AnimRenderer.render(gear, stack, null, buffers.getBuffer(RenderType.eyes(CaptainView.GEAR_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 0.8F, 0.8F, 0.8F, 1.0F, null);
      }
      if (passes.body() && shieldShown && shieldPose[0] != null) {
         stack.pushPose();
         stack.last().pose().set(shieldPose[0]);
         stack.last().normal().set(shieldNormal[0]);
         drawShieldOnBone(stack, buffers, shieldBone, light, overlay, alpha, empowered && passes.extras() && !shadowPass,
            (player.tickCount + partial));
         stack.popPose();
      }
      if (passes.extras() && showItem && hand[0] != null) {
         stack.pushPose();
         stack.last().pose().set(hand[0]);
         GeoBone h = body.getBone("right_hand");
         stack.translate(h.pivotX / 16.0F, h.pivotY / 16.0F - 0.12F, h.pivotZ / 16.0F - 0.02F);
         stack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(180.0F));
         stack.scale(1.0F / PLAYER_SCALE, 1.0F / PLAYER_SCALE, 1.0F / PLAYER_SCALE);
         mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
            stack, buffers, light);
         stack.popPose();
      }
      stack.popPose();
   }

   /** Shield model at the bone frame: pivot, quarter turn so the star faces outward, optional kinetic glow. */
   static void drawShieldOnBone(PoseStack stack, MultiBufferSource buffers, GeoBone bone, int light, int overlay, float alpha, boolean glow, float age) {
      BakedGeoModel shield = AnimCache.model(CaptainView.SHIELD);
      if (shield == null) {
         return;
      }
      shield.resetBones();
      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      stack.mulPose(Axis.YP.rotationDegrees(CaptainView.SHIELD_YAW));
      drawShield(stack, buffers, shield, light, overlay, alpha, glow ? 0.6F + 0.4F * Mth.sin(age * 0.25F) : 0.0F);
   }

   /** Shield mesh + glow pass (glow 0 = none). */
   public static void drawShield(PoseStack stack, MultiBufferSource buffers, BakedGeoModel shield, int light, int overlay, float alpha, float glow) {
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(CaptainView.SHIELD_TEX) : RenderType.entityCutoutNoCull(CaptainView.SHIELD_TEX);
      AnimRenderer.render(shield, stack, null, buffers.getBuffer(type), light, overlay, 1, 1, 1, alpha, null);
      if (glow > 0.01F) {
         AnimRenderer.render(shield, stack, null, buffers.getBuffer(RenderType.eyes(CaptainView.SHIELD_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F, null);
      }
   }

   /**
    * First person: the arms model (gloves, sleeves, shield on the left forearm) whenever the hand is empty, a move
    * runs or Steve guards; with an item in the calm hand the vanilla hand (suit skin via HeroSkins) shows it.
    */
   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      AbstractClientPlayer player = mc.player;
      HeroPublicSnapshot s = CaptainView.of(player);
      if (s == null) {
         return;
      }
      boolean ours = s.actionId() >= 0 || CaptainView.flag(s, CaptainFlags.GUARDING) || player.getMainHandItem().isEmpty();
      if (!ours) {
         return;
      }
      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND || player.isInvisible()) {
         return;
      }
      BakedGeoModel arms = AnimCache.model(CaptainView.ARMS);
      BakedGeoModel armsGear = AnimCache.model(CaptainView.ARMS_GEAR);
      if (arms == null) {
         return;
      }
      float partial = event.getPartialTick();
      CaptainPose.firstPerson(arms, player, s, partial);
      if (armsGear != null) {
         CaptainPose.copyPose(arms, armsGear);
         CaptainPose.shield(armsGear, s, CaptainPose.actionTicks(player, s, partial));
      }
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      stack.pushPose();
      stack.translate(FP_OFFSET.x, FP_OFFSET.y, FP_OFFSET.z);
      Matrix4f[] shieldPose = new Matrix4f[1];
      Matrix3f[] shieldNormal = new Matrix3f[1];
      AnimRenderer.BoneHook hook = (bone, pose, b, l, o) -> {
         if (bone.name.equals("left_shield")) {
            shieldPose[0] = new Matrix4f(pose.last().pose());
            shieldNormal[0] = new Matrix3f(pose.last().normal());
         }
      };
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(CaptainView.SKIN)), light, OverlayTexture.NO_OVERLAY,
         1, 1, 1, 1, null);
      if (armsGear != null) {
         AnimRenderer.render(armsGear, stack, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(CaptainView.GEAR_TEX)), light,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, hook);
         GeoBone sb = armsGear.getBone("left_shield");
         if (sb != null && !sb.hidden && shieldPose[0] != null) {
            stack.pushPose();
            stack.last().pose().set(shieldPose[0]);
            stack.last().normal().set(shieldNormal[0]);
            drawShieldOnBone(stack, buffers, sb, light, OverlayTexture.NO_OVERLAY, 1.0F, CaptainView.flag(s, CaptainFlags.EMPOWERED),
               player.tickCount + partial);
            stack.popPose();
         }
      }
      stack.popPose();
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

   @Nullable
   static GeoBone bone(@Nullable BakedGeoModel model, String name) {
      return model == null ? null : model.getBone(name);
   }
}

package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.mixin.EntityRendererNameTagInvoker;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Reinhard's whole-body model replaces the player in third person (spec §8, stage 1b Task 3).
 * The vanilla render is always cancelled for Reinhard, invisible included, so a glowing invisible
 * Reinhard outlines as the knight, never as a vanilla player. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReinhardRenderer {
   /** Vanilla PlayerRenderer.scale: a 32 px model stands 1.875 blocks like a player. */
   static final float PLAYER_SCALE = 0.9375F;
   /** Vanilla alpha of an invisible player seen by a spectator or teammate. */
   static final float TRANSLUCENT_ALPHA = 0.15F;
   static final String WALK = "walk";
   /** Fallback stance while a stance clip is missing: every bone at rest. */
   private static final Animation REST = new Animation("rest", 1.0, Animation.LoopType.LOOP, Map.of(), List.of());

   private ReinhardRenderer() {
   }

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }

      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (snapshot == null) {
         return;
      }

      BakedGeoModel body = AnimCache.model(ReinhardAssets.BODY);
      if (body == null) {
         return;
      }

      event.setCanceled(true);
      Minecraft mc = Minecraft.getInstance();
      float partialTick = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      nameTag(player, event.getRenderer(), stack, buffers, light, partialTick);
      ReinhardPoses.Passes passes = ReinhardPoses.passes(player.isInvisible(), mc.player != null && !player.isInvisibleTo(mc.player),
         mc.shouldEntityAppearGlowing(player));
      if (!passes.body() && !passes.outline()) {
         return;
      }

      ReinhardPoses.Pose pose = pose(player, snapshot, partialTick);
      applyClips(body, pose);
      applyHead(body, player, partialTick);
      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)));
      stack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
      if (passes.body()) {
         RenderType type = passes.translucent() ? RenderType.entityTranslucent(ReinhardAssets.TEXTURE) : RenderType.entityCutoutNoCull(ReinhardAssets.TEXTURE);
         float alpha = passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F;
         AnimRenderer.render(body, stack, null, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, null);
      }

      if (passes.outline()) {
         // Vanilla draws an invisible glowing entity with RenderType.outline only (the buffer source is the outline source).
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(ReinhardAssets.TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
      }

      if (passes.extras()) {
         if (!ShaderCompat.isShadowPass()) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.eyes(ReinhardAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               1.0F, 1.0F, 1.0F, 1.0F, null);
         }

         heldItems(player, body, ReinhardView.reid(snapshot), stack, buffers, light);
      }

      stack.popPose();
   }

   /** Stance/walk blend by swing, then the action overlay on the bones it animates. */
   static void applyClips(BakedGeoModel model, ReinhardPoses.Pose pose) {
      Animation stance = AnimCache.animation(ReinhardAssets.ANIMATIONS, pose.stance());
      Animation walk = AnimCache.animation(ReinhardAssets.ANIMATIONS, WALK);
      Animation from = stance != null ? stance : REST;
      if (walk != null) {
         AnimationController.blend(model, from, pose.stanceSeconds(), walk, pose.walkSeconds(), pose.walk());
      } else {
         AnimationController.seek(from, model, pose.stanceSeconds());
      }

      ReinhardPoses.Clip overlay = pose.overlay();
      if (overlay != null) {
         Animation clip = AnimCache.animation(ReinhardAssets.ANIMATIONS, overlay.animation());
         if (clip != null) {
            AnimationController.overlay(clip, model, overlay.seconds());
         }
      }
   }

   /** The head follows the look (vanilla netHeadYaw / headPitch); BakedGeoModel turns are negated. */
   static void applyHead(BakedGeoModel body, AbstractClientPlayer player, float partialTick) {
      GeoBone head = body.getBone(ReinhardAssets.HEAD);
      if (head == null) {
         return;
      }

      float yaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
      float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
      head.rotY += (float) Math.toRadians(-yaw);
      head.rotX += (float) Math.toRadians(-pitch);
   }

   static ReinhardPoses.Pose pose(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick) {
      boolean sitting = player.isPassenger() && player.getVehicle() != null && player.getVehicle().shouldRiderSit();
      float swing = sitting ? 0.0F : player.walkAnimation.speed(partialTick);
      ReinhardPoses.Input input = new ReinhardPoses.Input(ReinhardView.mode(snapshot), snapshot.heroFlags(), snapshot.actionId(), snapshot.actionElapsed(),
         snapshot.actionLength(), partialTick, swing, player.walkAnimation.position(partialTick), AnimRenderer.time(partialTick) / 20.0, player.getPose(), sitting);
      return ReinhardPoses.pose(input);
   }

   /** Moves the stack from the model frame to a top-level bone's pivot frame (same order as AnimRenderer). */
   static void boneFrame(PoseStack stack, GeoBone bone) {
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         stack.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }

      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      stack.mulPose(Axis.ZP.rotation(bone.rotZ));
      stack.mulPose(Axis.YP.rotation(bone.rotY));
      stack.mulPose(Axis.XP.rotation(bone.rotX));
      stack.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
   }

   private static void heldItems(AbstractClientPlayer player, BakedGeoModel body, ReinhardPoses.ReidPhase reid, PoseStack stack, MultiBufferSource buffers, int light) {
      for (HumanoidArm arm : HumanoidArm.values()) {
         ItemStack item = player.getItemInHand(player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
         GeoBone bone = body.getBone(arm == HumanoidArm.RIGHT ? ReinhardAssets.RIGHT_ARM : ReinhardAssets.LEFT_ARM);
         if (item.isEmpty() || bone == null || !ReinhardPoses.drawsHeldItem(arm, reid)) {
            continue;
         }

         stack.pushPose();
         boneFrame(stack, bone);
         stack.mulPoseMatrix(HeldItemAnchor.local(arm));
         boolean left = arm == HumanoidArm.LEFT;
         Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, item,
            left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, stack, buffers, light);
         stack.popPose();
      }
   }

   /** Forge's name tag step of EntityRenderer.render (skipped by the cancel), through vanilla renderNameTag. */
   private static void nameTag(AbstractClientPlayer player, PlayerRenderer renderer, PoseStack stack, MultiBufferSource buffers, int light, float partialTick) {
      EntityRendererNameTagInvoker invoker = (EntityRendererNameTagInvoker) renderer;
      RenderNameTagEvent event = new RenderNameTagEvent(player, player.getDisplayName(), renderer, stack, buffers, light, partialTick);
      MinecraftForge.EVENT_BUS.post(event);
      Event.Result result = event.getResult();
      if (NameTagRules.show(result == Event.Result.DENY, result == Event.Result.ALLOW, invoker.viltrumitecore$shouldShowName(player))) {
         invoker.viltrumitecore$renderNameTag(player, event.getContent(), stack, buffers, light);
      }
   }
}

package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerBoneMap;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.sinclair.stage.StageCamera;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * First person (spec §3): Sinclair's own arms (sleeve, cuff, wooden hand with jointed fingers,
 * Prestige) replace vanilla's hands with an empty main hand or during any move; fp_ clips carry the
 * finger work of the shot, reload, snap and card fan. Holding an item without a move keeps vanilla's
 * item rendering, but every bare arm vanilla would draw (the always-visible empty off hand, map hands)
 * is Sinclair's sleeve and wooden hand ({@link #onRenderArm}), never the human skin arm.
 * Both hands are drawn in the main-hand pass; the off-hand pass is cancelled.
 */
public final class SinclairFirstPerson {
   /** View-space placement of the eye point of the arms model (blocks): slightly right and forward. */
   static final float FP_X = 0.06F;
   static final float FP_Y = 0.02F;
   static final float FP_Z = 0.08F;

   /** Props of the camera arms model that a bare vanilla-placed arm does not carry. */
   private static final String[] PROPS = {"wand", "card", "held_hat"};

   private SinclairFirstPerson() {
   }

   /**
    * Vanilla's bare first-person arm (RenderArmEvent fires inside renderPlayerArm, before its setupAnim):
    * for a Sinclair it is replaced by the same arm of the camera arms model, at vanilla's rest pose, without
    * the wand / card / hat. Playtest 2026-10-30: a human arm showed up while an item was held.
    */
   public static void onRenderArm(RenderArmEvent event) {
      AbstractClientPlayer player = event.getPlayer();
      HeroPublicSnapshot snapshot = SinclairView.of(player);
      if (snapshot == null) {
         return;
      }

      event.setCanceled(true);
      BakedGeoModel arms = AnimCache.model(SinclairAssets.ARMS);
      if (arms == null) {
         return;
      }

      boolean right = event.getArm() == HumanoidArm.RIGHT;
      GeoBone root = arms.getBone(right ? "right_arm" : "left_arm");
      if (root == null) {
         return;
      }

      PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      ModelPart arm = right ? model.rightArm : model.leftArm;
      float oldX = arm.x, oldY = arm.y, oldZ = arm.z, oldXRot = arm.xRot, oldYRot = arm.yRot, oldZRot = arm.zRot;
      // The shared model still holds the last third-person pose: set the camera rest pose, never read it.
      arm.x = right ? -5.0F : 5.0F;
      arm.y = 2.0F;
      arm.z = 0.0F;
      arm.xRot = 0.0F;
      arm.yRot = 0.0F;
      arm.zRot = right ? 0.1F : -0.1F;
      arms.resetBones();
      for (String prop : PROPS) {
         SinclairPoser.hide(arms, prop, true);
      }

      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      boolean veil = SinclairView.flag(snapshot, SinclairFlags.VEIL);
      stack.pushPose();
      PlayerGeoLayer.partFrame(stack, arm, right ? PlayerBoneMap.Part.RIGHT_ARM : PlayerBoneMap.Part.LEFT_ARM);
      // The arms model's shoulder sits at (±4.9, 21.4) px; vanilla's arm pivot is (±5, 22).
      stack.translate((right ? -0.1F : 0.1F) / 16.0F, 0.6F / 16.0F, 0.0F);
      int light = event.getPackedLight();
      if (veil) {
         AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.entityTranslucent(SinclairAssets.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
            0.78F, 0.66F, 1.0F, 0.45F);
      } else {
         AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.entityCutoutNoCull(SinclairAssets.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, 1.0F);
      }

      float glow = veil ? 0.45F : 1.0F;
      AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.eyes(SinclairAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         glow, glow, glow, 1.0F);
      stack.popPose();
      for (String prop : PROPS) {
         SinclairPoser.hide(arms, prop, false);
      }

      arm.x = oldX;
      arm.y = oldY;
      arm.z = oldZ;
      arm.xRot = oldXRot;
      arm.yRot = oldYRot;
      arm.zRot = oldZRot;
   }

   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (StageCamera.staged()) {
         // The copy cinematic or an act camera moved the lens behind the body (also for a volunteer who is
         // not Sinclair): no first-person arms or held item in front of it.
         event.setCanceled(true);
         return;
      }

      LocalPlayer player = mc.player;
      HeroPublicSnapshot snapshot = SinclairView.of(player);
      if (snapshot == null) {
         return;
      }

      if (mc.getCameraEntity() != player) {
         // Looking through another entity (spectating, a camera rig): no arms in front of that lens.
         event.setCanceled(true);
         return;
      }

      boolean own = player.getMainHandItem().isEmpty() || snapshot.actionId() >= 0;
      if (!own) {
         return;
      }

      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }

      BakedGeoModel arms = AnimCache.model(SinclairAssets.ARMS);
      if (arms == null) {
         return;
      }

      float partialTick = event.getPartialTick();
      SinclairPoser.poseArms(arms, player, snapshot, partialTick);
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      boolean veil = SinclairView.flag(snapshot, SinclairFlags.VEIL);
      stack.pushPose();
      stack.translate(FP_X, FP_Y - SinclairAssets.EYE_PX / 16.0F, FP_Z);
      int light = event.getPackedLight();
      if (veil) {
         AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.entityTranslucent(SinclairAssets.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
            0.78F, 0.66F, 1.0F, 0.45F, null);
      } else {
         AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(SinclairAssets.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, 1.0F, null);
      }

      float glow = veil ? 0.45F : 1.0F;
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.eyes(SinclairAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         glow, glow, glow, 1.0F, null);
      stack.popPose();
   }
}

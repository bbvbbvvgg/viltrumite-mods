package dev.baranhan.viltrumitecore.client.regulus;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * First person: Regulus' own sleeves and hands (white coat, gold cuff, jointed fingers) replace the
 * vanilla hands with an empty main hand or during any move; the fp_ clips carry the backhand, the
 * flick, the scoop and both-handed cuts. Holding an item without a move keeps vanilla. Both hands
 * are drawn in the main-hand pass; the off-hand pass is cancelled.
 */
public final class RegulusFirstPerson {
   static final float FP_X = 0.04F;
   static final float FP_Y = 0.02F;
   static final float FP_Z = 0.06F;

   private RegulusFirstPerson() {
   }

   /** Pure: draw Regulus' own arms (empty main hand, or any move playing). */
   static boolean ownArms(boolean mainHandEmpty, int actionId) {
      return mainHandEmpty || actionId >= 0;
   }

   /**
    * AlwaysVisibleOffhandMixin hook: Regulus never forces the vanilla empty off hand. With his own
    * arms both hands are drawn here (the vanilla pass is cancelled); with an item in hand and no
    * move he is drawn like any player.
    */
   public static boolean wantsOffhand(@Nullable Player player) {
      return false;
   }

   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      HeroPublicSnapshot snapshot = RegulusView.of(player);
      if (snapshot == null) {
         return;
      }

      if (mc.getCameraEntity() != player) {
         event.setCanceled(true);
         return;
      }

      if (!ownArms(player.getMainHandItem().isEmpty(), snapshot.actionId())) {
         return;
      }

      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }

      BakedGeoModel arms = AnimCache.model(RegulusAssets.ARMS);
      if (arms == null) {
         return;
      }

      float partialTick = event.getPartialTick();
      RegulusPoser.poseArms(arms, player, snapshot, partialTick);
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      stack.pushPose();
      stack.translate(FP_X, FP_Y - RegulusAssets.EYE_PX / 16.0F, FP_Z);
      int light = event.getPackedLight();
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(RegulusAssets.FP_TEXTURE)), light, OverlayTexture.NO_OVERLAY,
         1.0F, 1.0F, 1.0F, 1.0F, null);
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.eyes(RegulusAssets.FP_GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         1.0F, 1.0F, 1.0F, 1.0F, null);
      stack.popPose();
   }
}

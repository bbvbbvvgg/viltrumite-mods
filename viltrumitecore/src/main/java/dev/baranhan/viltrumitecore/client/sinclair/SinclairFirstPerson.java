package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * First person (spec §3): Sinclair's own arms (sleeve, cuff, wooden hand with jointed fingers,
 * Prestige) replace vanilla's hands with an empty main hand or during any move; fp_ clips carry the
 * finger work of the shot, reload, snap and card fan. Holding an item without a move keeps vanilla.
 * Both hands are drawn in the main-hand pass; the off-hand pass is cancelled.
 */
public final class SinclairFirstPerson {
   /** View-space placement of the eye point of the arms model (blocks): slightly right and forward. */
   static final float FP_X = 0.06F;
   static final float FP_Y = 0.02F;
   static final float FP_Z = 0.08F;

   private SinclairFirstPerson() {
   }

   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      HeroPublicSnapshot snapshot = SinclairView.of(player);
      if (snapshot == null) {
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

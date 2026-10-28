package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.mixin.EntityRendererNameTagInvoker;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

/**
 * Sinclair's whole-body model replaces the player in third person (spec §3): the vanilla render is
 * always cancelled. Misdirection's veil hides him from everyone but himself and his allies, who see
 * a translucent violet silhouette (§5.5). Hexed players are drawn as the rabbit by {@link HexClient}
 * (higher priority) before this runs. Client only; registered from {@link SinclairClient}.
 */
public final class SinclairRenderer {
   /** Vanilla alpha of an invisible player seen by a teammate or spectator. */
   static final float TRANSLUCENT_ALPHA = 0.15F;
   /** The veil silhouette seen by Sinclair himself and his allies. */
   static final float VEIL_ALPHA = 0.38F;

   private SinclairRenderer() {
   }

   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }

      HeroPublicSnapshot snapshot = SinclairView.of(player);
      if (snapshot == null) {
         SinclairPoser.forget(player);
         return;
      }

      BakedGeoModel body = AnimCache.model(SinclairAssets.BODY);
      if (body == null) {
         return;
      }

      event.setCanceled(true);
      Minecraft mc = Minecraft.getInstance();
      Player viewer = mc.player;
      boolean veil = SinclairView.flag(snapshot, SinclairFlags.VEIL);
      boolean ally = viewer != null && (viewer == player || viewer.isSpectator() || player.isAlliedTo(viewer));
      if (veil && !ally) {
         return;
      }

      float partialTick = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      if (!veil) {
         nameTag(player, event.getRenderer(), stack, buffers, light, partialTick);
      }

      boolean invisible = player.isInvisible() && !veil;
      boolean seeInvisible = viewer != null && !player.isInvisibleTo(viewer);
      boolean outline = invisible && !seeInvisible && mc.shouldEntityAppearGlowing(player);
      if (invisible && !seeInvisible && !outline) {
         return;
      }

      float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
      SinclairPoser.posePlayer(body, player, snapshot, partialTick, bodyYaw);
      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      stack.pushPose();
      setupRotations(player, stack, partialTick, bodyYaw);
      stack.scale(SinclairAssets.PLAYER_SCALE, SinclairAssets.PLAYER_SCALE, SinclairAssets.PLAYER_SCALE);
      boolean shadow = ShaderCompat.isShadowPass();
      AnimRenderer.BoneHook hook = shadow ? null : SinclairAnchors.recorder(player);
      if (outline) {
         if (!shadow) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(SinclairAssets.TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, hook);
         }
      } else if (veil || invisible) {
         float alpha = veil ? VEIL_ALPHA : TRANSLUCENT_ALPHA;
         float r = veil ? 0.78F : 1.0F;
         float g = veil ? 0.66F : 1.0F;
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.entityTranslucent(SinclairAssets.TEXTURE)), light, overlay, r, g, 1.0F, alpha, hook);
         if (!shadow && veil) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.eyes(SinclairAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               alpha, alpha, alpha, 1.0F, null);
         }
      } else {
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(SinclairAssets.TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, hook);
         if (!shadow) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.eyes(SinclairAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               1.0F, 1.0F, 1.0F, 1.0F, null);
         }
      }

      stack.popPose();
   }

   /** Vanilla LivingEntityRenderer/PlayerRenderer body rotations: yaw, death tilt, sleep, swim and elytra pitch. */
   static void setupRotations(LivingEntity entity, PoseStack stack, float partialTick, float bodyYaw) {
      if (entity.hasPose(Pose.SLEEPING)) {
         Direction bed = entity.getBedOrientation();
         if (bed != null) {
            float shift = entity.getEyeHeight(Pose.STANDING) - 0.1F;
            stack.translate(-bed.getStepX() * shift, 0.0F, -bed.getStepZ() * shift);
         }

         float yaw = bed == null ? bodyYaw : switch (bed) {
            case SOUTH -> 90.0F;
            case NORTH -> 270.0F;
            case EAST -> 180.0F;
            default -> 0.0F;
         };
         stack.mulPose(Axis.YP.rotationDegrees(yaw));
         stack.mulPose(Axis.ZP.rotationDegrees(90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(270.0F));
         return;
      }

      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      if (entity.deathTime > 0) {
         float dying = Math.min(1.0F, Mth.sqrt((entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F));
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
      }

      if (entity.isFallFlying()) {
         float t = entity.getFallFlyingTicks() + partialTick;
         float k = Mth.clamp(t * t / 100.0F, 0.0F, 1.0F);
         stack.mulPose(Axis.XP.rotationDegrees(k * (-90.0F - entity.getXRot())));
      } else {
         float swim = entity.getSwimAmount(partialTick);
         if (swim > 0.0F) {
            float pitch = entity.isInWater() ? -90.0F - entity.getXRot() : -90.0F;
            stack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swim, 0.0F, pitch)));
            if (entity.isVisuallySwimming()) {
               stack.translate(0.0F, -1.0F, 0.3F);
            }
         }
      }
   }

   /** Forge's name tag step of EntityRenderer.render (skipped by the cancel), through vanilla renderNameTag. */
   private static void nameTag(AbstractClientPlayer player, PlayerRenderer renderer, PoseStack stack, MultiBufferSource buffers, int light, float partialTick) {
      EntityRendererNameTagInvoker invoker = (EntityRendererNameTagInvoker) renderer;
      RenderNameTagEvent event = new RenderNameTagEvent(player, player.getDisplayName(), renderer, stack, buffers, light, partialTick);
      MinecraftForge.EVENT_BUS.post(event);
      Event.Result result = event.getResult();
      if (result != Event.Result.DENY && (result == Event.Result.ALLOW || invoker.viltrumitecore$shouldShowName(player))) {
         invoker.viltrumitecore$renderNameTag(player, event.getContent(), stack, buffers, light);
      }
   }
}

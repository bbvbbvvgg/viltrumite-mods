package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.sinclair.HexMarks;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.HexMarkEntity;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Rabbit Hex on the client (spec §5.4): any hexed living entity (player of any hero, or mob) is
 * drawn as Sinclair's stage rabbit with a tiny top hat and spring ears; one's own hex shows a
 * violet vignette with the ears and the seconds left, hides the hands and swallows attack/use
 * keys (client half of the shared ActionLocks; the server enforces it).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HexClient {
   /** rab_appear plays over the first ticks of the mark. */
   static final int APPEAR_TICKS = 8;

   private HexClient() {
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
      HexMarkEntity mark = HexMarks.markOf(event.getEntity());
      if (mark != null) {
         event.setCanceled(true);
         drawRabbit(event.getEntity(), mark, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
      HexMarkEntity mark = HexMarks.markOf(event.getEntity());
      if (mark != null) {
         event.setCanceled(true);
         drawRabbit(event.getEntity(), mark, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRenderHand(RenderHandEvent event) {
      if (HexMarks.hexed(Minecraft.getInstance().player)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      if (HexMarks.hexed(Minecraft.getInstance().player)) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         HexMarks.clearClient();
         SinclairAnchors.clear();
      }
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      HexMarkEntity mark = HexMarks.markOf(mc.player);
      if (mark == null || mc.options.hideGui) {
         return;
      }

      GuiGraphics g = event.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      float age = mark.age() + event.getPartialTick();
      float in = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
      int band = Math.max(12, h / 6);
      int edge = (int) (0x70 * in) << 24 | 0x2A1650;
      g.fillGradient(0, 0, w, band, edge, 0x002A1650);
      g.fillGradient(0, h - band, w, h, 0x002A1650, edge);
      // The rabbit's own ears at the top of the view, swaying with the hop.
      float sway = Mth.sin(RealTime.seconds() * 5.0F) * 2.0F;
      int ear = (int) (0xA0 * in) << 24 | 0xF3EBDD;
      int inner = (int) (0xA0 * in) << 24 | 0xE7A9C0;
      int cx = w / 2;
      for (int side = -1; side <= 1; side += 2) {
         int x = cx + side * 34 + (int) (sway * side);
         g.fill(x - 7, 0, x + 7, 30, ear);
         g.fill(x - 3, 0, x + 3, 25, inner);
      }

      int left = Math.max(0, mark.duration() - mark.age());
      Component text = Component.translatable("hud.viltrumitecore.sinclair.hexed", String.format("%.1f", left / 20.0F));
      g.drawCenteredString(mc.font, text, cx, 36, 0xFFC9B6FF);
   }

   /** The stage rabbit at the hexed entity's place and heading. */
   static void drawRabbit(LivingEntity entity, HexMarkEntity mark, PoseStack stack, MultiBufferSource buffers, int light, float partialTick) {
      BakedGeoModel model = AnimCache.model(SinclairAssets.RABBIT);
      if (model == null) {
         return;
      }

      float age = mark.age() + partialTick;
      boolean moving = entity.walkAnimation.speed(partialTick) > 0.05F || !entity.onGround();
      Animation idle = SinclairAssets.clip("rab_idle");
      if (idle != null) {
         AnimationController.seek(idle, model, SinclairPoser.loop(idle, RealTime.seconds()));
      } else {
         model.resetBones();
      }

      if (moving) {
         Animation hop = SinclairAssets.clip("rab_hop");
         if (hop != null) {
            AnimationController.overlay(hop, model, SinclairPoser.loop(hop, entity.walkAnimation.position(partialTick) / (SinclairPoser.STRIDE * 0.5F) * hop.lengthSeconds()), 1.0F);
         }
      }

      if (age < APPEAR_TICKS) {
         SinclairPoser.overlay(model, "rab_appear", age / 20.0, 1.0F);
      }

      float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
      GeoBone head = model.getBone("head");
      if (head != null) {
         float yaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw);
         head.rotY += (float) Math.toRadians(-Mth.clamp(yaw, -60.0F, 60.0F));
         head.rotX += (float) Math.toRadians(-entity.getViewXRot(partialTick) * 0.5F);
      }

      CoatPhysics.rabbit(model, entity, bodyYaw, partialTick);
      float pop = age < 6.0F ? 0.3F + 0.7F * (float) Math.sin(Math.min(1.0F, age / 6.0F) * Math.PI * 0.6) / (float) Math.sin(Math.PI * 0.6) : 1.0F;
      stack.pushPose();
      SinclairRenderer.setupRotations(entity, stack, partialTick, bodyYaw);
      stack.scale(pop, pop, pop);
      int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
      boolean invisible = entity.isInvisible();
      if (invisible) {
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityTranslucent(SinclairAssets.RABBIT_TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 0.15F, null);
      } else {
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(SinclairAssets.RABBIT_TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
         if (!ShaderCompat.isShadowPass()) {
            AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(SinclairAssets.RABBIT_GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               1.0F, 1.0F, 1.0F, 1.0F, null);
         }
      }

      stack.popPose();
   }
}

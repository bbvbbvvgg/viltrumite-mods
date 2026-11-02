package dev.baranhan.viltrumitecore.client.venom;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The takeover's screen layer (works with the post shader off too): for the host the crawling veins, the breathing
 * slit of sight with violet-black lids (never closing below VenomHostFx.MIN_OPEN), the scary flashes (white eyes and
 * teeth), the resistance bar and the hint; for the possessor the host's name, the time left and the controls.
 * Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class VenomHostHud {
   static final ResourceLocation VEINS = VenomView.rl("textures/gui/venom/veins.png");
   static final ResourceLocation FLASH = VenomView.rl("textures/gui/venom/flash.png");
   static final ResourceLocation LID = VenomView.rl("textures/gui/venom/lid.png");
   private static long struggleAt = -100;

   private VenomHostHud() {
   }

   static void struggle() {
      struggleAt = VenomPossessionClient.ticks();
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return;
      }
      GuiGraphics g = event.getGuiGraphics();
      float partial = event.getPartialTick();
      float[] post = VenomPossessionClient.post(partial);
      VenomPossessionClient.Session host = VenomPossessionClient.localSession();
      if (host != null || post[1] > 0.01F) {
         drawView(g, post, mc.player.tickCount + partial);
      }
      if (mc.options.hideGui) {
         return;
      }
      if (host != null && host.phase == VenomPossessionClient.CONTROL) {
         drawResist(mc, g, host);
      }
      VenomPossessionClient.Session mine = VenomPossessionClient.localPossession();
      if (mine != null) {
         drawPossessor(mc, g, mine);
      }
   }

   /** Veins, lids and flashes. */
   static void drawView(GuiGraphics g, float[] post, float ticks) {
      int w = g.guiWidth(), h = g.guiHeight();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      float veins = post[1];
      if (veins > 0.01F) {
         // the veins texture creeps in from the edges: drawn larger than the screen and shrinking as they take hold
         float grow = 1.0F + 0.6F * (1.0F - veins);
         int vw = Math.round(w * grow), vh = Math.round(h * grow);
         float wob = (float)Math.sin(ticks * 0.15) * 0.015F;
         g.setColor(1, 1, 1, Mth.clamp(veins * (0.85F + wob * 4), 0, 1));
         g.blit(VEINS, (w - vw) / 2, (h - vh) / 2, vw, vh, 0, 0, 256, 256, 256, 256);
      }
      float open = post[3];
      if (post[0] > 0.01F && open < 0.999F) {
         // the lids: violet-black, soft-edged, leaving a slit of height open * h (the lid texture is translucent at its
         // edge and never fully opaque, so the world shows through even the darkest part)
         int slit = Math.round(h * open);
         int lid = (h - slit) / 2 + 12;
         g.setColor(1, 1, 1, Math.min(1.0F, post[0]));
         g.blit(LID, 0, -12, w, lid, 0, 0, 64, 32, 64, 64);
         g.blit(LID, 0, h - lid + 12, w, lid, 0, 32, 64, 32, 64, 64);
      }
      float flash = post[2];
      if (flash > 0.02F) {
         g.setColor(1, 1, 1, Math.min(0.8F, flash * 0.8F));
         int fw = Math.round(w * 0.9F), fh = fw / 2;
         g.blit(FLASH, (w - fw) / 2, (h - fh) / 2, fw, fh, 0, 0, 256, 128, 256, 128);
      }
      g.setColor(1, 1, 1, 1);
      RenderSystem.enableDepthTest();
   }

   static void drawResist(Minecraft mc, GuiGraphics g, VenomPossessionClient.Session s) {
      int w = g.guiWidth(), h = g.guiHeight();
      int bw = 120, x = (w - bw) / 2, y = h / 2 + 34;
      float resist = Mth.clamp(s.resist / (float)VenomRules.RESIST_TO_BREAK, 0, 1);
      boolean shake = VenomPossessionClient.ticks() - struggleAt < 3;
      int dx = shake ? (int)((VenomPossessionClient.ticks() % 2) * 2 - 1) : 0;
      g.fill(x - 1 + dx, y - 1, x + bw + 1 + dx, y + 5, 0xC0100816);
      g.fill(x + dx, y, x + dx + Math.round(bw * resist), y + 4, 0xFFE8E4F0);
      g.fill(x + dx, y, x + dx + Math.round(bw * resist), y + 1, 0x60FFFFFF);
      Component hint = Component.translatable("hud.viltrumitecore.venom.resist");
      g.drawCenteredString(mc.font, hint, w / 2 + dx, y + 8, 0xFFD8D0E8);
      int left = Math.max(0, s.remaining / 20);
      g.drawCenteredString(mc.font, Component.translatable("hud.viltrumitecore.venom.held", left), w / 2, y - 12, 0xFF9A88B8);
   }

   static void drawPossessor(Minecraft mc, GuiGraphics g, VenomPossessionClient.Session s) {
      int w = g.guiWidth();
      Entity host = mc.level.getEntity(s.hostId);
      Component name = host == null ? Component.literal("?") : host.getDisplayName();
      int y = 12;
      g.drawCenteredString(mc.font, Component.translatable("hud.viltrumitecore.venom.riding", name), w / 2, y, 0xFFE8E4F0);
      int bw = 140, x = (w - bw) / 2;
      float left = VenomPossessionClient.remainingFraction(s);
      float resist = Mth.clamp(s.resist / (float)VenomRules.RESIST_TO_BREAK, 0, 1);
      g.fill(x - 1, y + 11, x + bw + 1, y + 16, 0xC0100816);
      g.fill(x, y + 12, x + Math.round(bw * left), y + 15, 0xFF6E5A9A);
      if (resist > 0) {
         // the host's struggle eats in from the right
         g.fill(x + bw - Math.round(bw * resist), y + 12, x + bw, y + 15, 0xFFD04050);
      }
      String key = s.spider() ? "hud.viltrumitecore.venom.controls_spider" : "hud.viltrumitecore.venom.controls";
      g.drawCenteredString(mc.font, Component.translatable(key), w / 2, y + 20, 0xFFB8A8D8);
   }
}

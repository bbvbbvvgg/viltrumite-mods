package dev.baranhan.viltrumitecore.client.ironman.scan;

import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.ironman.jarvis.JarvisVoice;
import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Scan reticle (spec §11.2): rotating brackets on the scanned entity
 * (snapshot controlTargetId) that close in as the 30 t aim fills, plus a
 * "SCANNING n %" label. Without a target: "SCAN — aim at a target".
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class ScanReticle {
   private static final int CYAN = 0xFF60E0FF;

   private ScanReticle() {
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (client.options.hideGui || player == null || client.level == null) {
         return;
      }

      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (!JarvisVoice.online(snapshot) || !IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.SCAN_ACTIVE)) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      int width = graphics.guiWidth();
      int height = graphics.guiHeight();
      float partialTick = event.getPartialTick();
      Entity target = snapshot.controlTargetId() < 0 ? null : client.level.getEntity(snapshot.controlTargetId());
      if (target != null && (target.isRemoved() || !target.isAlive())) {
         target = null;
      }
      float progress = IronManView.progress(snapshot, HeroAction.SCAN, partialTick);
      if (target == null) {
         Component idle = Component.translatable("hud.viltrumitecore.ironman.scan_idle");
         graphics.drawString(client.font, idle, (width - client.font.width(idle)) / 2, height / 2 + 14, 0x9FE8FF, true);
         return;
      }

      Vec3 centre = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
      ScreenProjector.Point point = ScreenProjector.project(centre, width, height);
      ScreenProjector.Point top = ScreenProjector.project(centre.add(0.0, target.getBbHeight() * 0.5, 0.0), width, height);
      if (point == null || top == null || !point.onScreen(width, height)) {
         return;
      }

      float radius = Math.max(8.0F, Math.abs(point.y() - top.y()) * 1.3F) * (1.6F - 0.6F * progress);
      float spin = (player.tickCount + partialTick) * 0.08F;
      for (int corner = 0; corner < 4; corner++) {
         float angle = spin + corner * Mth.HALF_PI;
         // Each bracket: a short arc of dots, longer as the scan fills.
         int dots = 3 + Math.round(progress * 6.0F);
         for (int i = 0; i < dots; i++) {
            float a = angle + (i - dots * 0.5F) * 0.06F;
            int x = Math.round(point.x() + Mth.cos(a) * radius);
            int y = Math.round(point.y() + Mth.sin(a) * radius);
            graphics.fill(x, y, x + 1, y + 1, CYAN);
         }
      }

      Component label = Component.translatable("hud.viltrumitecore.ironman.scanning", Math.round(progress * 100.0F));
      int lx = Math.round(point.x() - client.font.width(label) / 2.0F);
      int ly = Math.round(point.y() + radius + 4.0F);
      graphics.drawString(client.font, label, lx, ly, 0x9FE8FF, true);
      int barWidth = client.font.width(label);
      graphics.fill(lx, ly + 10, lx + barWidth, ly + 11, 0x6060E0FF);
      graphics.fill(lx, ly + 10, lx + Math.round(barWidth * progress), ly + 11, CYAN);
   }
}

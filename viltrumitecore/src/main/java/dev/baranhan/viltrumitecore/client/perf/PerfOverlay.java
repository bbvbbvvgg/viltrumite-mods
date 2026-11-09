package dev.baranhan.viltrumitecore.client.perf;

import dev.baranhan.viltrumitecore.network.packet.PerfStatsS2CPacket;
import dev.baranhan.viltrumitecore.perf.MsptWindow;
import dev.baranhan.viltrumitecore.perf.PerfRegistry;
import dev.baranhan.viltrumitecore.perf.ServerPerf;
import dev.baranhan.viltrumitecore.perf.TickBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The performance overlay (top right; {@code /vfx overlay} or {@code /vperf overlay}): FPS and frame time with the
 * last 240 frames, particles against their caps, entities, server TPS and tick time, and every work budget's fill.
 */
public final class PerfOverlay {
   private static final int WIDTH = 252;
   private static final int GRAPH_H = 36;
   private static final double GRAPH_MS = 100.0;
   private static final long[] FRAMES = new long[ClientFrameStats.SIZE];

   private PerfOverlay() {
   }

   static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
      if (!PerfClient.overlay()) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.options.hideGui || mc.level == null) {
         return;
      }

      Font font = mc.font;
      FrameRing ring = ClientFrameStats.ring();
      double frameMs = Math.max(0.01, ring.emaMs());
      List<String> lines = new ArrayList<>();
      lines.add(tr("frame", "FPS %s · frame %s ms · p99 %s ms · load %s%%",
         f0(1000.0 / frameMs), f1(frameMs), f1(ring.percentileMs(0.99)), pct(ParticleBudget.pressure())));
      lines.add(tr("particles", "Particles %s / %s · +%s/s · thinned %s/s · over %s/s",
         ParticleBudget.live(), ParticleBudget.liveLimit(), ParticleBudget.admittedPerSecond(),
         ParticleBudget.thinnedPerSecond(), ParticleBudget.overBudgetPerSecond()));
      List<String> names = new ArrayList<>();
      List<int[]> fills = new ArrayList<>();
      PerfStatsS2CPacket remote = PerfClient.server();
      if (mc.hasSingleplayerServer()) {
         MsptWindow.Stats stats = ServerPerf.stats();
         lines.add(tr("server", "Server %s TPS · tick %s ms (p95 %s, max %s) · load %s%%",
            f1(ServerPerf.tps()), f1(stats.avgMs()), f1(stats.p95Ms()), f1(stats.maxMs()), pct(ServerPerf.pressure())));
         lines.add(tr("entities", "Entities %s", mc.level.getEntityCount()));
      } else if (remote != null) {
         lines.add(tr("server", "Server %s TPS · tick %s ms (p95 %s, max %s) · load %s%%",
            f1(remote.tps), f1(remote.avgMs), f1(remote.p95Ms), f1(remote.maxMs), pct(remote.pressure)));
         lines.add(tr("entities.remote", "Entities %s here · %s on the server", mc.level.getEntityCount(), remote.entities));
         for (int i = 0; i < remote.budgets(); i++) {
            names.add(remote.names[i]);
            fills.add(new int[]{remote.used[i], remote.capacity[i], remote.denied[i]});
         }
      } else {
         lines.add(tr("server.none", "Server numbers: /vperf overlay (op)"));
         lines.add(tr("entities", "Entities %s", mc.level.getEntityCount()));
      }

      for (TickBudget budget : PerfRegistry.all()) {
         names.add(budget.name());
         fills.add(new int[]{budget.lastUsed(), budget.lastCapacity(), budget.lastDenied()});
      }

      String title = tr("title", "Viltrumite performance · %s%s", ParticleBudget.quality().id().toUpperCase(Locale.ROOT),
         ParticleBudget.adaptive() ? "" : tr("fixed", " · fixed"));
      int lineH = 10;
      int barH = 9;
      int panelH = (lines.size() + 1) * lineH + names.size() * barH + GRAPH_H + 8;
      int x = width - WIDTH - 6;
      int y = 6;
      g.fill(x - 4, y - 4, x + WIDTH + 4, y + panelH, 0xB0101016);
      g.drawString(font, title, x, y, 0xFFF0C45A, false);
      y += lineH;
      for (String line : lines) {
         g.drawString(font, line, x, y, 0xFFE8E8E8, false);
         y += lineH;
      }

      for (int i = 0; i < names.size(); i++) {
         int[] f = fills.get(i);
         String label = names.get(i) + " " + f[0] + "/" + f[1] + (f[2] > 0 ? " -" + f[2] : "");
         g.drawString(font, font.plainSubstrByWidth(label, 150), x, y, 0xFFBFC7D5, false);
         int barX = x + 154;
         int barW = WIDTH - 154;
         g.fill(barX, y + 1, barX + barW, y + 7, 0x40FFFFFF);
         double fill = f[1] <= 0 ? (f[0] > 0 ? 1.0 : 0.0) : Math.min(1.0, (double)f[0] / f[1]);
         int color = f[2] > 0 ? 0xFFE0504A : (fill > 0.75 ? 0xFFE8C040 : 0xFF58C878);
         g.fill(barX, y + 1, barX + (int)Math.round(barW * fill), y + 7, color);
         y += barH;
      }

      y += 4;
      int base = y + GRAPH_H;
      g.fill(x, y, x + ClientFrameStats.SIZE, base, 0x30FFFFFF);
      int n = ring.copyOldestFirst(FRAMES);
      int offset = ClientFrameStats.SIZE - n;
      for (int i = 0; i < n; i++) {
         double ms = FRAMES[i] / 1.0E6;
         int h = Math.max(1, (int)Math.min(GRAPH_H, Math.round(ms / GRAPH_MS * GRAPH_H)));
         int color = ms <= 16.7 ? 0xFF58C878 : (ms <= 33.4 ? 0xFFE8C040 : 0xFFE0504A);
         g.fill(x + offset + i, base - h, x + offset + i + 1, base, color);
      }

      int line60 = base - (int)Math.round(16.7 / GRAPH_MS * GRAPH_H);
      int line30 = base - (int)Math.round(33.3 / GRAPH_MS * GRAPH_H);
      g.fill(x, line60, x + ClientFrameStats.SIZE, line60 + 1, 0x7058C878);
      g.fill(x, line30, x + ClientFrameStats.SIZE, line30 + 1, 0x70E8C040);
   }

   private static String tr(String key, String fallback, Object... args) {
      return Component.translatableWithFallback("viltrumitecore.perf.hud." + key, fallback, args).getString();
   }

   private static String f0(double value) {
      return Integer.toString((int)Math.round(value));
   }

   private static String f1(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private static String pct(double share) {
      return Integer.toString((int)Math.round(Math.max(0.0, Math.min(1.0, share)) * 100.0));
   }

   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
         event.registerAboveAll("perf_overlay", PerfOverlay::render);
      }
   }
}

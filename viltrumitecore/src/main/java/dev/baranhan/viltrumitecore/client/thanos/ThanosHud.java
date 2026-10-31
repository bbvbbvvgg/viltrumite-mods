package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.client.hero.CinematicHud;
import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * HUD of the Thanos fight for everyone who sees it, under the boss bar (his health): the timer to
 * the snap, the six stone pips (the stones of his current move flare), the FINAL minute (the panel
 * and the timer pulse red, the title changes), the last ten seconds' pop, and a warning strip for
 * every dangerous ability while it winds up - its name in its stone's colour and how to survive
 * it. Nothing is drawn outside the fight, while a cinematic owns the screen, or with F1; the strip
 * goes as soon as the move's active part is over. On a small GUI both keep left of the hero's own HUD
 * ({@link ThanosHudLayout}); the strip then wraps its hint.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ThanosHud {
   private static float bossBottom = 31.0F;
   private static float bossSeen = -1.0F;

   private ThanosHud() {
   }

   /** Moves that put up a warning while they wind up (everything that can kill or trap a hero). */
   public static boolean warns(ThanosMove m) {
      return m != null && m.kind != ThanosMove.Kind.MELEE && m != ThanosMove.SPACE_STEP && m != ThanosMove.SPACE_WARD && m != ThanosMove.TIME_REWIND;
   }

   public static String moveKey(ThanosMove m) {
      return "hud.viltrumitecore.thanos.move." + m.name().toLowerCase(Locale.ROOT);
   }

   public static String hintKey(ThanosMove m) {
      return "hud.viltrumitecore.thanos.hint." + m.name().toLowerCase(Locale.ROOT);
   }

   /** Warning strength 0..1 at a move age: on through the telegraph and the active part, then gone. */
   public static float warning(ThanosMove m, ThanosPhase phase, float age) {
      if (!warns(m) || age < 0.0F) {
         return 0.0F;
      }

      int tel = ThanosRules.telegraph(m.telegraph, phase);
      float end = tel + m.active;
      if (age >= end + 4.0F) {
         return 0.0F;
      }

      float in = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
      float out = age > end ? 1.0F - (age - end) / 4.0F : 1.0F;
      return Math.max(0.0F, Math.min(in, out));
   }

   @SubscribeEvent
   public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
      bossSeen = Math.max(bossSeen, event.getY() + event.getIncrement());
   }

   static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (bossSeen > 0.0F) {
         bossBottom = bossSeen;
      }

      bossSeen = -1.0F;
      if (!ThanosClient.active() || mc.options.hideGui || CinematicHud.hiding()) {
         return;
      }

      ThanosFx.renderHud(g, partial, width, height);
      int stage = ThanosClient.stage();
      if (!ThanosClient.hud() || stage != ThanosStateS2CPacket.FIGHT && stage != ThanosStateS2CPacket.INTRO) {
         return;
      }

      int left = ThanosClient.ticksLeft();
      boolean late = left <= ThanosRules.FINAL_TICKS && stage == ThanosStateS2CPacket.FIGHT;
      float time = ThanosClient.clientTicks() + partial;
      Font font = mc.font;
      int w = 118;
      int h = 40;
      int y = Math.round(Math.max(bossBottom, 12.0F)) + 2;
      boolean heroPanel = heroPanel(mc.player);
      PanelStyle style = heroPanel ? PanelStyles.of(mc.player) : null;
      int reachLeft = style == null ? 0 : style.attachmentsLeft();
      int reachUp = style == null ? 0 : style.attachmentsUp();
      int x = ThanosHudLayout.panelX(width, w, ThanosHudLayout.maxRight(width, height, y + h, heroPanel, reachLeft, reachUp));
      float pulse = late ? 0.5F + 0.5F * (float)Math.sin(time * 0.32F) : 0.0F;
      panel(g, x, y, w, h, late, pulse);
      Component title = Component.translatable(late ? "hud.viltrumitecore.thanos.final" : "hud.viltrumitecore.thanos.until_snap");
      int titleColor = late ? mix(0xFF8A7A, 0xFFFFFF, pulse * 0.5F) : 0xE9C46A;
      g.pose().pushPose();
      g.pose().translate(x + w / 2.0F, y + 4.0F, 0.0F);
      g.pose().scale(0.75F, 0.75F, 1.0F);
      g.drawString(font, title, -font.width(title) / 2, 0, titleColor, true);
      g.pose().popPose();
      String clock = ThanosRules.clock(left);
      int seconds = ThanosRules.secondsLeft(left);
      float pop = seconds <= 10 && stage == ThanosStateS2CPacket.FIGHT ? 1.0F + 0.18F * Math.max(0.0F, 1.0F - (left % 20 + (1.0F - partial)) / 6.0F) : 1.0F;
      float scale = 2.0F * pop;
      int clockColor = late ? mix(0xFF4A3A, 0xFFE0D8, pulse * 0.6F) : 0xF4F1EA;
      g.pose().pushPose();
      g.pose().translate(x + w / 2.0F, y + 20.0F, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.drawString(font, clock, -font.width(clock) / 2, -4, clockColor, true);
      g.pose().popPose();
      ThanosEntity thanos = ThanosClient.thanos();
      pips(g, thanos, x + w / 2, y + h - 6, time, late);
      if (stage == ThanosStateS2CPacket.FIGHT) {
         int stripY = y + h + 3;
         int stripRight = ThanosHudLayout.maxRight(width, height, stripY + ThanosHudLayout.maxStripHeight(), heroPanel, reachLeft, reachUp);
         warningStrip(g, font, thanos, x + w / 2, stripY, width, stripRight, time);
      }
   }

   /** The local player shows a hero ability panel (the same test as the hero HUD itself). */
   private static boolean heroPanel(Player player) {
      return player != null && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).hasAbilityPanel(player);
   }

   private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean late, float pulse) {
      int edge = late ? mix(0x6E1414, 0xB02A20, pulse) : 0x4B2E6B;
      int gold = late ? mix(0xC24A2A, 0xFF8A50, pulse) : 0xB8913A;
      g.fill(x + 1, y, x + w - 1, y + h, 0xC00B0810);
      g.fill(x, y + 1, x + w, y + h - 1, 0xC00B0810);
      g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, 0xD0211A2E, 0xD0120E18);
      g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF000000 | gold);
      g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0xFF000000 | edge);
      g.fill(x + 1, y + 2, x + 2, y + h - 2, 0xFF000000 | edge);
      g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0xFF000000 | edge);
      g.fill(x + 3, y + 3, x + 5, y + 5, 0xFF000000 | gold);
      g.fill(x + w - 5, y + 3, x + w - 3, y + 5, 0xFF000000 | gold);
   }

   /** Six stone pips; the stones of his current move flare. */
   private static void pips(GuiGraphics g, ThanosEntity thanos, int cx, int cy, float time, boolean late) {
      ThanosMove move = thanos == null ? null : thanos.move();
      float moveAge = thanos == null ? 1.0E9F : thanos.tickCount - thanos.clientMoveStart;
      boolean charging = move != null && moveAge >= 0 && moveAge < move.length(ThanosClient.phase());
      ThanosStone[] stones = ThanosStone.values();
      int step = 12;
      int start = cx - step * (stones.length - 1) / 2;
      for (int i = 0; i < stones.length; i++) {
         ThanosStone s = stones[i];
         int px = start + i * step;
         boolean hot = charging && move.uses(s);
         float glow = hot ? 0.7F + 0.3F * (float)Math.sin(time * 0.9F + i) : late ? 0.35F + 0.25F * (float)Math.sin(time * 0.3F + i) : 0.2F;
         int halo = (int)(glow * 150.0F) << 24 | s.rgb & 0xFFFFFF;
         g.fill(px - 4, cy - 1, px + 5, cy + 2, halo);
         g.fill(px - 1, cy - 4, px + 2, cy + 5, halo);
         diamond(g, px, cy, 3, 0xFF000000 | mix(s.rgb, 0x000000, hot ? 0.0F : 0.25F));
         g.fill(px - 1, cy - 2, px, cy - 1, 0xC0FFFFFF);
      }
   }

   /** The dangerous-ability strip: the name in the lead stone's colour and the counterplay. */
   private static void warningStrip(GuiGraphics g, Font font, ThanosEntity thanos, int cx, int y, int width, int maxRight, float time) {
      if (thanos == null) {
         return;
      }

      ThanosMove move = thanos.move();
      float age = thanos.tickCount - thanos.clientMoveStart;
      float k = move == null ? 0.0F : warning(move, ThanosClient.phase(), age);
      if (k <= 0.02F) {
         return;
      }

      ThanosStone lead = move.lead();
      int rgb = lead == null ? 0xE9C46A : lead.rgb & 0xFFFFFF;
      Component name = Component.translatable(moveKey(move));
      Component hint = Component.translatable(hintKey(move));
      int nameWidth = font.width(name);
      int want = Math.max(nameWidth + 24, Math.round(font.width(hint) * 0.75F) + 16);
      int w = ThanosHudLayout.stripWidth(want, width, maxRight);
      List<FormattedCharSequence> lines = w >= want ? List.of(hint.getVisualOrderText()) : font.split(hint, Math.max(40, (int)((w - 18) / 0.75F)));
      if (lines.size() > ThanosHudLayout.MAX_HINT_LINES) {
         lines = lines.subList(0, ThanosHudLayout.MAX_HINT_LINES);
      }

      int h = ThanosHudLayout.stripHeight(lines.size());
      int x = ThanosHudLayout.stripX(cx, w, maxRight);
      int a = Math.round(k * 255.0F);
      boolean winding = age < ThanosRules.telegraph(move.telegraph, ThanosClient.phase());
      float blink = winding ? 0.65F + 0.35F * (float)Math.sin(time * 0.8F) : 1.0F;
      g.fill(x, y, x + w, y + h, Math.round(k * 0xC0) << 24 | 0x0B0810);
      g.fill(x, y, x + w, y + 1, Math.round(a * blink) << 24 | rgb);
      g.fill(x, y + h - 1, x + w, y + h, Math.round(a * 0.6F) << 24 | rgb);
      // the warning glyph: a small filled triangle with a bar
      int gx = x + 6;
      int gy = y + 3;
      for (int i = 0; i < 7; i++) {
         g.fill(gx + 3 - i / 2, gy + i, gx + 4 + i / 2, gy + i + 1, Math.round(a * blink) << 24 | rgb);
      }

      float nameScale = nameWidth + 22 > w ? Math.max(0.6F, (w - 22) / (float)nameWidth) : 1.0F;
      g.pose().pushPose();
      g.pose().translate(x + 16, y + 2 + (1.0F - nameScale) * 4.0F, 0.0F);
      g.pose().scale(nameScale, nameScale, 1.0F);
      g.drawString(font, name, 0, 0, a << 24 | mix(rgb, 0xFFFFFF, 0.35F), true);
      g.pose().popPose();
      for (int i = 0; i < lines.size(); i++) {
         g.pose().pushPose();
         g.pose().translate(x + 16, y + 12.5F + 7.0F * i, 0.0F);
         g.pose().scale(0.75F, 0.75F, 1.0F);
         g.drawString(font, lines.get(i), 0, 0, a << 24 | 0xD8D2C8, true);
         g.pose().popPose();
      }
   }

   private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
      for (int dy = -r; dy <= r; dy++) {
         int span = r - Math.abs(dy);
         g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
      }
   }

   static int mix(int a, int b, float t) {
      float k = Mth.clamp(t, 0.0F, 1.0F);
      int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
      int gg = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
      int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
      return r << 16 | gg << 8 | bl;
   }

   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
         event.registerAboveAll("thanos_hud", ThanosHud::render);
      }
   }
}

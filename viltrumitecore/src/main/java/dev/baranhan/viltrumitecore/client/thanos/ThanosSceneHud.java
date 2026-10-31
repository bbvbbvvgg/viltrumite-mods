package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.client.render.ViltrumiteShaders;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * The clean screen of the snap scene (drawn by {@link dev.baranhan.viltrumitecore.client.hero.CinematicHud}
 * instead of the game HUD and the chat): the letterbox, the snap's flash when the post pass is
 * off, and Od Lagna's six lines - his portrait, his name and the line typed out over the first
 * 60 % of its five seconds ({@link ThanosSceneTimeline#lineReveal}), every line from the language
 * files.
 */
final class ThanosSceneHud {
   static final String NAME_KEY = "scene.viltrumitecore.thanos.od_lagna";
   static final String LINE_KEY = "scene.viltrumitecore.thanos.line.";
   private static final UUID SPEAKER = new UUID(0x7A11A5L, 0x0D1A6AL);
   private static final int PORTRAIT = 46;

   private ThanosSceneHud() {
   }

   static String lineKey(int line) {
      return LINE_KEY + (line + 1);
   }

   static void render(GuiGraphics g, float partial, int width, int height) {
      float t = ThanosSceneClient.snapTime(partial);
      if (t < 0.0F) {
         return;
      }

      float weight = ThanosSceneShots.weight(t);
      int bar = Math.round(height * 0.1F * weight);
      if (bar > 0) {
         g.fill(0, 0, width, bar, 0xFF000000);
         g.fill(0, height - bar, width, height, 0xFF000000);
      }

      if (ViltrumiteShaders.get() == null) {
         float flash = ThanosSceneClient.screen(partial)[0];
         if (flash > 0.01F) {
            g.fill(0, 0, width, height, Math.round(flash * 230.0F) << 24 | 0xFFF8EC);
         }
      }

      subtitles(g, t, width, height, bar);
   }

   /** Panel opacity over the whole speech (in over 8 ticks, out over 8 ticks). */
   static float panelAlpha(float t) {
      float in = ThanosSceneTimeline.smooth((t - (ThanosSceneTimeline.SPEECH_START - 6)) / 8.0F);
      float out = 1.0F - ThanosSceneTimeline.smooth((t - (ThanosSceneTimeline.SPEECH_END - 4)) / 8.0F);
      return Math.min(in, out);
   }

   /** Text opacity of the current line (it fades in its first 5 ticks and out in its last 6). */
   static float textAlpha(float t) {
      int line = ThanosSceneTimeline.line(t);
      if (line < 0) {
         return 0.0F;
      }

      float lt = ThanosSceneTimeline.lineTime(t);
      return Math.min(ThanosSceneTimeline.smooth(lt / 5.0F), 1.0F - ThanosSceneTimeline.smooth((lt - (ThanosSceneTimeline.LINE_TICKS - 6)) / 6.0F));
   }

   /** The first n characters (code points) across the wrapped lines. */
   static List<String> reveal(List<String> lines, int n) {
      List<String> out = new ArrayList<>(lines.size());
      int left = Math.max(0, n);
      for (String line : lines) {
         int count = line.codePointCount(0, line.length());
         if (left >= count) {
            out.add(line);
            left -= count;
         } else {
            if (left > 0) {
               out.add(line.substring(0, line.offsetByCodePoints(0, left)));
            }

            break;
         }
      }

      return out;
   }

   private static void subtitles(GuiGraphics g, float t, int width, int height, int bar) {
      float panel = panelAlpha(t);
      if (panel <= 0.01F) {
         return;
      }

      int line = ThanosSceneTimeline.line(t);
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      int w = Math.min(width - 24, 380);
      int textW = w - PORTRAIT - 22;
      String text = line < 0 ? "" : Component.translatable(lineKey(line)).getString();
      List<String> wrapped = new ArrayList<>();
      for (FormattedText part : font.getSplitter().splitLines(text, textW, Style.EMPTY)) {
         wrapped.add(part.getString());
      }

      int h = Math.max(PORTRAIT + 8, 18 + Math.max(2, wrapped.size()) * 10 + 6);
      int x = (width - w) / 2;
      int y = height - Math.max(bar, 10) - h - 6;
      int a = Math.round(panel * 255.0F);
      // panel: deep violet, a white-gold rule on top
      g.fill(x, y, x + w, y + h, Math.round(panel * 0xC8) << 24 | 0x0C0816);
      g.fill(x, y, x + w, y + 1, a << 24 | 0xFFE2A6);
      g.fill(x, y + h - 1, x + w, y + h, Math.round(panel * 0x80) << 24 | 0x6E5A9A);
      int px = x + 4;
      int py = y + (h - PORTRAIT) / 2;
      g.fill(px - 1, py - 1, px + PORTRAIT + 1, py + PORTRAIT + 1, a << 24 | 0xC9A55A);
      g.fill(px, py, px + PORTRAIT, py + PORTRAIT, Math.round(panel * 0xF0) << 24 | 0x1A1430);
      ThanosOdLagna.bust(g, new HeroPortraits.Frame(px, py, PORTRAIT, PORTRAIT, panel, t / 20.0, SPEAKER, null));
      int tx = px + PORTRAIT + 10;
      int ty = y + 6;
      g.drawString(font, Component.translatable(NAME_KEY), tx, ty, a << 24 | 0xF2C46B, true);
      float textAlpha = panel * textAlpha(t);
      if (line < 0 || textAlpha <= 0.02F) {
         return;
      }

      int total = text.codePointCount(0, text.length());
      int shown = Mth.clamp(Mth.ceil(total * ThanosSceneTimeline.lineReveal(t)), 0, total);
      int ta = Math.max(4, Math.round(textAlpha * 255.0F));
      int row = ty + 13;
      for (String s : reveal(wrapped, shown)) {
         g.drawString(font, s, tx, row, ta << 24 | 0xF6F0E4, true);
         row += 10;
      }
   }
}

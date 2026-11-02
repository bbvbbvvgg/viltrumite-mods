package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * JoJo-style screen accents for the local player only: focus speed lines and a screentone border when a book opens in
 * front of them, menacing "ゴゴゴ" glyphs at the edges (the manuscript, the counter), a brief ink vignette when their own
 * body becomes a book. Respectful of the camera and of accessibility: never moves the camera, no full-screen flashes,
 * slow motion only, hidden with F1 and in screens, scaled by vanilla "Screen Effects" and by
 * {@link ViltrumitePostProcessingConfig#mangaEffectsMultiplier} (0 turns it off).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class MangaFx {
   public enum Kind {
      /** A book opened in front of you (reader or witness): focus lines, screentone. */
      OPEN(16),
      /** Faster than a punch: a short burst of focus lines. */
      COUNTER(10),
      /** The manuscript: glyphs and lines. */
      MENACE(36),
      /** Your own body opened: ink vignette and tone. */
      VICTIM(24);

      final int ticks;

      Kind(int ticks) {
         this.ticks = ticks;
      }
   }

   private static Kind kind;
   private static double startedAt = -1000.0;
   private static float strength;

   private MangaFx() {
   }

   /** Overall scale 0..1: config x Screen Effects (pure given the two). */
   public static float scale(float config, float screenEffects) {
      return Math.min(1.0F, Mth.clamp(config, 0.0F, 2.0F) * Mth.clamp(screenEffects, 0.0F, 1.0F));
   }

   static float configScale() {
      Minecraft mc = Minecraft.getInstance();
      float screen = mc.options == null ? 1.0F : mc.options.screenEffectScale().get().floatValue();
      return scale(ViltrumitePostProcessingConfig.INSTANCE.mangaEffectsMultiplier, screen);
   }

   public static void trigger(Kind k, float power) {
      if (configScale() <= 0.0F) {
         return;
      }
      double now = BookPagesRenderer.now();
      // a stronger accent replaces a weaker one; the same accent restarts
      if (kind != null && now - startedAt < kind.ticks && strength > power && k != kind) {
         return;
      }
      kind = k;
      startedAt = now;
      strength = Mth.clamp(power, 0.0F, 1.0F);
   }

   /** Envelope 0..1 of an accent at age/length: quick in, slow out (pure). */
   public static float envelope(double age, int length) {
      if (age < 0.0 || age >= length) {
         return 0.0F;
      }
      double in = Math.min(1.0, age / 3.0), out = 1.0 - age / length;
      return (float)(in * out * (2.0 - out));
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (kind == null || mc.options.hideGui || mc.screen != null && !(mc.screen instanceof HeavensDoorScreen)) {
         return;
      }
      double age = BookPagesRenderer.now() - startedAt;
      float env = envelope(age, kind.ticks) * strength * configScale();
      if (env <= 0.01F) {
         if (age > kind.ticks) {
            kind = null;
         }
         return;
      }
      draw(event.getGuiGraphics(), kind, env, age);
   }

   static void draw(GuiGraphics g, Kind k, float env, double age) {
      int w = g.guiWidth(), h = g.guiHeight();
      int cx = w / 2, cy = h / 2;
      Minecraft mc = Minecraft.getInstance();
      if (k == Kind.OPEN || k == Kind.COUNTER || k == Kind.MENACE) {
         focusLines(g, cx, cy, w, h, env, age, k == Kind.COUNTER ? 56 : 40);
      }
      if (k == Kind.OPEN || k == Kind.VICTIM || k == Kind.MENACE) {
         screentone(g, w, h, env * (k == Kind.VICTIM ? 1.0F : 0.6F));
      }
      if (k == Kind.MENACE || k == Kind.COUNTER) {
         int a = (int)(env * 220) & 0xFF;
         int color = a << 24 | 0x7A2FA0;
         float s = 2.0F;
         g.pose().pushPose();
         g.pose().scale(s, s, 1.0F);
         Component go = Component.literal("ゴ ゴ ゴ");
         int drift = (int)(Math.sin(age * 0.15) * 3);
         g.drawString(mc.font, go, (int)(12 / s), (int)((h * 0.25 + drift) / s), color, false);
         g.drawString(mc.font, go, (int)((w - 12 - mc.font.width(go) * s) / s), (int)((h * 0.62 - drift) / s), color, false);
         g.pose().popPose();
      }
      if (k == Kind.VICTIM) {
         int a = (int)(env * 120) & 0xFF;
         int ink = a << 24 | 0x14121C;
         g.fill(0, 0, w, 10, ink);
         g.fill(0, h - 10, w, h, ink);
      }
   }

   /** Thin dark strokes converging on the centre; they keep clear of the crosshair area (aim stays readable). */
   private static void focusLines(GuiGraphics g, int cx, int cy, int w, int h, float env, double age, int count) {
      int alpha = (int)(env * 150) & 0xFF;
      int color = alpha << 24 | 0x101018;
      double rot = age * 0.004;
      float outer = (float)Math.hypot(w, h) * 0.5F;
      float inner = Math.min(w, h) * (0.38F + 0.08F * (1.0F - env));
      long seed = 9157L;
      for (int i = 0; i < count; i++) {
         seed = seed * 6364136223846793005L + 1442695040888963407L;
         double jitter = ((seed >>> 33) % 1000) / 1000.0;
         double a = (i + jitter * 0.6) / count * Math.PI * 2.0 + rot;
         float len = inner + (float)(jitter * 0.12 * outer);
         float x0 = cx + (float)Math.cos(a) * len, y0 = cy + (float)Math.sin(a) * len;
         float x1 = cx + (float)Math.cos(a) * outer, y1 = cy + (float)Math.sin(a) * outer;
         line(g, x0, y0, x1, y1, color, 1 + (int)(jitter * 2));
      }
   }

   private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int color, int thick) {
      int steps = (int)Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 3 + 1;
      for (int s = 0; s <= steps; s++) {
         float u = s / (float)steps;
         int x = (int)Mth.lerp(u, x0, x1), y = (int)Mth.lerp(u, y0, y1);
         int th = Math.max(1, Math.round(thick * u + 0.5F));
         g.fill(x, y, x + th, y + th, color);
      }
   }

   /** A dotted screentone band along the edges. */
   private static void screentone(GuiGraphics g, int w, int h, float env) {
      int alpha = (int)(env * 110) & 0xFF;
      int color = alpha << 24 | 0x1A1A28;
      int band = (int)(Math.min(w, h) * 0.09F);
      for (int y = 0; y < h; y += 4) {
         boolean edgeY = y < band || y > h - band;
         for (int x = ((y / 4) & 1) * 2; x < w; x += 4) {
            if (edgeY || x < band || x > w - band) {
               g.fill(x, y, x + 1, y + 1, color);
            }
         }
      }
   }
}

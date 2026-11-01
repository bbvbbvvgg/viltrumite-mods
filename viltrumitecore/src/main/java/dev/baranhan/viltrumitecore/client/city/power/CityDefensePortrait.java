package dev.baranhan.viltrumitecore.client.city.power;

import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Subtitle portrait of the city's civil defence announcements: a siren on its mast, the horn turning (the
 * bright face sweeps around) over a dark panel, red while the city is in an emergency and amber otherwise.
 * Flat GUI fills only, no texture.
 */
final class CityDefensePortrait implements HeroPortraits.Portrait {
   static final CityDefensePortrait INSTANCE = new CityDefensePortrait();

   private CityDefensePortrait() {
   }

   @Override
   public void render(GuiGraphics g, HeroPortraits.Frame f) {
      int a = Mth.clamp((int)(f.alpha() * 255), 0, 255);
      if (a <= 4) {
         return;
      }
      int x0 = Mth.floor(f.x()), y0 = Mth.floor(f.y()), w = Math.max(8, Mth.floor(f.width())), h = Math.max(8, Mth.floor(f.height()));
      boolean emergency = CityPowerClient.stage().emergency();
      int hot = emergency ? 0xE8321E : 0xF0A020;
      g.fill(x0, y0, x0 + w, y0 + h, argb(a, 0x15181E));
      // mast and housing
      int cx = x0 + w / 2;
      int mastW = Math.max(1, w / 14);
      g.fill(cx - mastW, y0 + h * 9 / 20, cx + mastW, y0 + h - 2, argb(a, 0x5A606A));
      int bodyW = w * 5 / 12, bodyH = h / 4;
      int by = y0 + h / 4;
      g.fill(cx - bodyW / 2, by, cx + bodyW / 2, by + bodyH, argb(a, 0x3A3F48));
      // the rotating horn: its lit face slides across the housing
      double t = f.seconds() * 2.6;
      float sweep = (float)(Math.sin(t) * 0.5 + 0.5);
      int hornW = Math.max(2, bodyW / 3);
      int hx = cx - bodyW / 2 + Math.round((bodyW - hornW) * sweep);
      float facing = (float)Math.max(0.0, Math.cos(t * 0.5));
      g.fill(hx, by + 1, hx + hornW, by + bodyH - 1, argb(a, mix(0x6A2018, hot, 0.4F + 0.6F * facing)));
      // the beam: a soft fan when the horn faces the viewer
      int beamA = (int)(a * 0.35F * facing);
      if (beamA > 4) {
         g.fill(x0 + 1, by - bodyH / 3, x0 + w - 1, by + bodyH + bodyH / 3, argb(beamA, hot));
      }
      // lamp on top
      int lampA = (int)(a * (0.5F + 0.5F * (float)Math.abs(Math.sin(f.seconds() * 4.0))));
      g.fill(cx - mastW - 1, by - h / 10, cx + mastW + 1, by, argb(lampA, hot));
   }

   private static int argb(int a, int rgb) {
      return Mth.clamp(a, 0, 255) << 24 | rgb & 0xFFFFFF;
   }

   private static int mix(int c0, int c1, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int r = (int)Mth.lerp(t, c0 >> 16 & 0xFF, c1 >> 16 & 0xFF);
      int gr = (int)Mth.lerp(t, c0 >> 8 & 0xFF, c1 >> 8 & 0xFF);
      int b = (int)Mth.lerp(t, c0 & 0xFF, c1 & 0xFF);
      return r << 16 | gr << 8 | b;
   }
}

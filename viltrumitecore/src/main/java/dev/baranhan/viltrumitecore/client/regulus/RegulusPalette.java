package dev.baranhan.viltrumitecore.client.regulus;

/**
 * Regulus' VFX language: stopped time, not energy. Ivory motes that halt mid-air, pale glass sheets
 * and hairline scars, gold glints at the instant something stops, ink for the stuttering heart.
 * No lightning, no fire.
 */
final class RegulusPalette {
   static final int IVORY = 0xF4F1EA;
   static final int GOLD = 0xE9C866;
   static final int PALE_GOLD = 0xF3E3B0;
   static final int GLASS = 0xCFE6F2;
   static final int INK = 0x2A2633;
   static final int HEART = 0xF0B2A6;
   static final int GREY = 0xB9B6AE;

   private RegulusPalette() {
   }

   static int r(int rgb) {
      return rgb >> 16 & 0xFF;
   }

   static int g(int rgb) {
      return rgb >> 8 & 0xFF;
   }

   static int b(int rgb) {
      return rgb & 0xFF;
   }

   static int mix(int a, int b, float t) {
      float k = Math.max(0.0F, Math.min(1.0F, t));
      int r = Math.round(r(a) + (r(b) - r(a)) * k);
      int g = Math.round(g(a) + (g(b) - g(a)) * k);
      int bl = Math.round(b(a) + (b(b) - b(a)) * k);
      return r << 16 | g << 8 | bl;
   }
}

package dev.baranhan.viltrumitecore.client.sinclair;

/** Spec §3 palette (RGB). */
public final class SinclairPalette {
   public static final int GHOST = 0x9B6BFF;
   public static final int LAVENDER = 0xC9B6FF;
   public static final int GOLD = 0xF2C14E;
   public static final int VELVET = 0x8E1B2C;
   public static final int IVORY = 0xF3EBDD;
   public static final int INK = 0x15121A;
   public static final int SAVANNAH = 0xB07CFF;

   private SinclairPalette() {
   }

   public static int r(int rgb) {
      return rgb >> 16 & 0xFF;
   }

   public static int g(int rgb) {
      return rgb >> 8 & 0xFF;
   }

   public static int b(int rgb) {
      return rgb & 0xFF;
   }

   /** Linear mix of two RGB colours. */
   public static int mix(int a, int b, float t) {
      float k = Math.max(0.0F, Math.min(1.0F, t));
      return Math.round(r(a) + (r(b) - r(a)) * k) << 16 | Math.round(g(a) + (g(b) - g(a)) * k) << 8 | Math.round(b(a) + (b(b) - b(a)) * k);
   }
}

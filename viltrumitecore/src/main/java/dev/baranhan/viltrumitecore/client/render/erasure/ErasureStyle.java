package dev.baranhan.viltrumitecore.client.render.erasure;

/** Colours of one erasure stroke, 0xRRGGBB. Port of Roundabout's TheHandErasureStyle (Hydraheads, Roundabout
 * License v1.2, see CREDITS.md); the values are the original ones, measured from the anime by its authors.
 *
 * @param head       glass colours at the head of the stroke
 * @param tail       glass colours at the tail; the tube shifts from these to the head colours along its length
 * @param edge       thin outline on both edges
 * @param decayStart colour the tube cools to while space closes
 * @param decayEnd   colour of its thin, nearly shut part */
public record ErasureStyle(Ramp head, Ramp tail, int edge, int decayStart, int decayEnd) {
   /** Five colours from darkest to brightest. */
   public record Ramp(int darkest, int dark, int body, int light, int brightest) {
      public Ramp mix(Ramp other, float t) {
         return new Ramp(mixColor(darkest, other.darkest, t), mixColor(dark, other.dark, t),
            mixColor(body, other.body, t), mixColor(light, other.light, t), mixColor(brightest, other.brightest, t));
      }

      public int get(int i) {
         return switch (i) {
            case 0 -> darkest;
            case 1 -> dark;
            case 2 -> body;
            case 3 -> light;
            default -> brightest;
         };
      }
   }

   public static final Ramp CYAN_RAMP = new Ramp(0x0e1a4a, 0x145c95, 0x08bad6, 0x8fdde2, 0xbff8fc);
   public static final Ramp GREEN_RAMP = new Ramp(0x003629, 0x065c66, 0x18c4b8, 0x7ff0f8, 0xe6fbfc);
   private static final int MINT = 0x01f3a8;
   private static final int LIME = 0xb8f25c;

   /** The original default: cyan, a little greener toward the tail, cooling to mint and lime. */
   public static final ErasureStyle CYAN = new ErasureStyle(CYAN_RAMP, CYAN_RAMP.mix(GREEN_RAMP, 0.4F), 0x002543, MINT, LIME);

   static int mixColor(int a, int b, float t) {
      int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
      int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
      int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
      return (r << 16) | (g << 8) | bl;
   }
}

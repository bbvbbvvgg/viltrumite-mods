package dev.baranhan.viltrumitecore.worldevent.aftermath;

import java.util.Locale;

/**
 * What wrecked a place. The id is the stable string of the shared event contract
 * ({@code docs/city-aftermath-integration.md}); the weights tint what the ruin does afterwards:
 * a meteor leaves a hot glowing crater and heavy smoke, a collapse mostly dust and falling
 * debris, a hero fight dust and broken stone, alien or robot weapons sparks and burning metal.
 * Weights are 0..1 per channel; zones hit by several causes take the strongest of each channel.
 */
public enum AftermathCause {
   //                 smoke  dust   fire   hot    sparks debris metal
   METEOR(       "meteor",          1.00F, 0.70F, 0.85F, 1.00F, 0.25F, 0.80F, 0.30F),
   CHITAURI(     "chitauri",        0.85F, 0.60F, 0.70F, 0.45F, 0.80F, 0.70F, 0.70F),
   LEVIATHAN(    "leviathan",       0.90F, 1.00F, 0.60F, 0.35F, 0.70F, 1.00F, 1.00F),
   HERO(         "hero",            0.45F, 1.00F, 0.25F, 0.10F, 0.20F, 0.90F, 0.40F),
   EXPLOSION(    "explosion",       0.95F, 0.65F, 0.80F, 0.40F, 0.30F, 0.70F, 0.30F),
   DESTRUCTION(  "destruction",     0.55F, 0.90F, 0.30F, 0.05F, 0.20F, 0.80F, 0.40F),
   COLLAPSE(     "collapse",        0.40F, 1.00F, 0.20F, 0.00F, 0.35F, 1.00F, 0.80F),
   ULTRON(       "ultron",          0.80F, 0.70F, 0.60F, 0.30F, 1.00F, 0.80F, 1.00F),
   INFRASTRUCTURE("infrastructure", 0.70F, 0.45F, 0.55F, 0.20F, 1.00F, 0.40F, 0.80F),
   OTHER(        "other",           0.60F, 0.70F, 0.40F, 0.20F, 0.30F, 0.70F, 0.40F);

   public static final int SMOKE = 0;
   public static final int DUST = 1;
   public static final int FIRE = 2;
   public static final int HOT = 3;
   public static final int SPARKS = 4;
   public static final int DEBRIS = 5;
   public static final int METAL = 6;
   public static final int CHANNELS = 7;

   private static final AftermathCause[] VALUES = values();

   public final String id;
   private final float[] weights;

   AftermathCause(String id, float smoke, float dust, float fire, float hot, float sparks, float debris, float metal) {
      this.id = id;
      this.weights = new float[]{smoke, dust, fire, hot, sparks, debris, metal};
   }

   public float weight(int channel) {
      return this.weights[channel];
   }

   public int bit() {
      return 1 << this.ordinal();
   }

   /** The cause of a contract id ("meteor", "collapse", ...); unknown ids are {@link #OTHER}. */
   public static AftermathCause of(String id) {
      if (id != null) {
         String k = id.trim().toLowerCase(Locale.ROOT);
         for (AftermathCause c : VALUES) {
            if (c.id.equals(k)) {
               return c;
            }
         }
      }

      return OTHER;
   }

   /** Strongest weight of one channel over every cause in the bit mask (OTHER when the mask is empty). */
   public static float weight(int mask, int channel) {
      if (mask == 0) {
         return OTHER.weight(channel);
      }

      float w = 0.0F;
      for (AftermathCause c : VALUES) {
         if ((mask & c.bit()) != 0) {
            w = Math.max(w, c.weight(channel));
         }
      }

      return w;
   }

   /** The cause with the most weight in the mask on a channel, for sound picking and status lines. */
   public static AftermathCause dominant(int mask) {
      AftermathCause best = OTHER;
      for (AftermathCause c : VALUES) {
         if ((mask & c.bit()) != 0) {
            best = c; // the latest declared cause wins ties: specific causes come before generic ones
            if (c != DESTRUCTION && c != EXPLOSION && c != OTHER) {
               return c;
            }
         }
      }

      return best;
   }
}

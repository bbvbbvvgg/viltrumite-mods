package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * The six Infinity Stones of the gauntlet (pure). The order is the socket order on the left
 * gauntlet, thumb first, the back-of-hand socket last: Time (thumb), Space (index), Reality
 * (middle), Power (ring), Soul (little finger), Mind (back of the hand). The snap lights them
 * in this order.
 */
public enum ThanosStone {
   TIME(0x35F07A, "time"),
   SPACE(0x3C8CFF, "space"),
   REALITY(0xF0283A, "reality"),
   POWER(0xA83CFF, "power"),
   SOUL(0xFF9426, "soul"),
   MIND(0xFFD630, "mind");

   /** Every stone set. */
   public static final int ALL = (1 << values().length) - 1;
   private static final ThanosStone[] VALUES = values();
   /** Emissive colour, 0xRRGGBB. */
   public final int rgb;
   /** Lang/sound key part: {@code thanos.stone.<key>}. */
   public final String key;

   ThanosStone(int rgb, String key) {
      this.rgb = rgb;
      this.key = key;
   }

   public int bit() {
      return 1 << this.ordinal();
   }

   public boolean in(int mask) {
      return (mask & this.bit()) != 0;
   }

   public float red() {
      return (this.rgb >> 16 & 255) / 255.0F;
   }

   public float green() {
      return (this.rgb >> 8 & 255) / 255.0F;
   }

   public float blue() {
      return (this.rgb & 255) / 255.0F;
   }

   /** Number of stones in a mask. */
   public static int count(int mask) {
      return Integer.bitCount(mask & ALL);
   }

   /** The first stone of a mask (socket order), or null for none. */
   public static ThanosStone first(int mask) {
      for (ThanosStone stone : VALUES) {
         if (stone.in(mask)) {
            return stone;
         }
      }

      return null;
   }

   public static ThanosStone byIndex(int index) {
      return index >= 0 && index < VALUES.length ? VALUES[index] : null;
   }
}

package dev.baranhan.viltrumitecore.world.collapse;

/**
 * Packed block positions with exactly the layout of {@code BlockPos.asLong} (x 26 bits, z 26, y 12), so
 * keys convert with {@code BlockPos.of}. Kept here so the pure analyzer and its tests need no Minecraft class.
 */
public final class BlockKey {
   private BlockKey() {
   }

   public static long of(int x, int y, int z) {
      return ((long)x & 0x3FFFFFFL) << 38 | ((long)y & 0xFFFL) | ((long)z & 0x3FFFFFFL) << 12;
   }

   public static int x(long k) {
      return (int)(k >> 38);
   }

   public static int y(long k) {
      return (int)(k << 52 >> 52);
   }

   public static int z(long k) {
      return (int)(k << 26 >> 38);
   }
}

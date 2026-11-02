package dev.baranhan.viltrumitecore.client.scp106;

/** Client state of the pocket dimension, fed by Scp106S2CPacket (filled in by the client session). */
public final class PocketClient {
   public static int slot = -1;
   public static int flags;
   public static long seed;
   public static int captureEntity = -1;
   public static long captureStart = -1000;

   private PocketClient() {
   }

   public static void onPacket(byte kind, int a, int b, long seed) {
      switch (kind) {
         case 0 -> {
            slot = a;
            flags = b;
            PocketClient.seed = seed;
         }
         default -> {
         }
      }
   }
}

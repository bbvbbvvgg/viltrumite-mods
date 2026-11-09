package dev.baranhan.viltrumitecore.client.perf;

import dev.baranhan.viltrumitecore.network.packet.PerfStatsS2CPacket;

/** Client state of the performance overlay: on/off and the latest server numbers (multiplayer). */
public final class PerfClient {
   private static boolean overlay = Boolean.getBoolean("viltrumitecore.perf.overlay");
   private static volatile PerfStatsS2CPacket server;
   private static volatile long serverAtMs;

   private PerfClient() {
   }

   public static boolean overlay() {
      return overlay;
   }

   public static void setOverlay(boolean on) {
      overlay = on;
   }

   public static void onPacket(PerfStatsS2CPacket packet) {
      if (packet.kind == PerfStatsS2CPacket.SHOW) {
         overlay = true;
      } else if (packet.kind == PerfStatsS2CPacket.HIDE) {
         overlay = false;
      }

      server = packet;
      serverAtMs = System.currentTimeMillis();
   }

   /** The server numbers if they are fresh (under 5 s old), else null. */
   public static PerfStatsS2CPacket server() {
      return System.currentTimeMillis() - serverAtMs < 5000L ? server : null;
   }
}

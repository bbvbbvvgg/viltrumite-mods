package dev.baranhan.viltrumitecore.perf;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server load: MSPT from the server's own 100-tick history, real TPS, and the 0..1 pressure every server budget
 * scales with (0 up to {@link #PRESSURE_START_MS}, 1 at {@link #PRESSURE_FULL_MS}).
 */
@Mod.EventBusSubscriber(modid = ViltrumiteCore.MOD_ID)
public final class ServerPerf {
   public static final double PRESSURE_START_MS = doubleProperty("viltrumitecore.perf.msptStart", 35.0);
   public static final double PRESSURE_FULL_MS = doubleProperty("viltrumitecore.perf.msptFull", 50.0);
   private static final MsptWindow STARTS = new MsptWindow(100);
   private static volatile MsptWindow.Stats stats = MsptWindow.Stats.EMPTY;
   private static volatile double tps = 20.0;
   private static volatile double pressure;

   private ServerPerf() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.START) {
         STARTS.tickStarted(System.nanoTime());
         tps = STARTS.tps();
         MinecraftServer server = event.getServer();
         if (server != null) {
            stats = MsptWindow.of(server.tickTimes);
            pressure = Degrade.ramp(stats.avgMs(), PRESSURE_START_MS, PRESSURE_FULL_MS);
         }
      }
   }

   public static MsptWindow.Stats stats() {
      return stats;
   }

   public static double tps() {
      return tps;
   }

   /** 0 = healthy, 1 = the server is well behind (MSPT at or over {@link #PRESSURE_FULL_MS}). */
   public static double pressure() {
      return Boolean.getBoolean("viltrumitecore.perf.noServerPressure") ? 0.0 : pressure;
   }

   public static void reset() {
      STARTS.clear();
      stats = MsptWindow.Stats.EMPTY;
      tps = 20.0;
      pressure = 0.0;
   }

   static double doubleProperty(String key, double fallback) {
      String raw = System.getProperty(key);
      if (raw == null) {
         return fallback;
      }

      try {
         return Double.parseDouble(raw.trim());
      } catch (NumberFormatException e) {
         return fallback;
      }
   }
}

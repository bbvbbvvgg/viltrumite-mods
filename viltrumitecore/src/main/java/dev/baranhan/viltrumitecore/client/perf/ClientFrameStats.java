package dev.baranhan.viltrumitecore.client.perf;

import dev.baranhan.viltrumitecore.perf.Degrade;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Frame time of the client, measured between render-tick starts. Its pressure (0 at 30 FPS or better, 1 at 10 FPS
 * or worse; -Dviltrumitecore.perf.frameStart / frameFull in ms) is what thins cosmetic effects on a struggling
 * machine.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ClientFrameStats {
   public static final int SIZE = 240;
   public static final double START_MS = property("viltrumitecore.perf.frameStart", 33.3);
   public static final double FULL_MS = property("viltrumitecore.perf.frameFull", 100.0);
   private static final FrameRing RING = new FrameRing(SIZE, 16.7);

   private ClientFrameStats() {
   }

   @SubscribeEvent
   public static void onRenderTick(TickEvent.RenderTickEvent event) {
      if (event.phase == TickEvent.Phase.START) {
         RING.frameStarted(System.nanoTime());
      }
   }

   public static FrameRing ring() {
      return RING;
   }

   public static double pressure() {
      return RING.count() < 20 ? 0.0 : Degrade.ramp(RING.emaMs(), START_MS, FULL_MS);
   }

   private static double property(String key, double fallback) {
      String value = System.getProperty(key);
      if (value == null) {
         return fallback;
      }

      try {
         return Double.parseDouble(value.trim());
      } catch (NumberFormatException ignored) {
         return fallback;
      }
   }
}

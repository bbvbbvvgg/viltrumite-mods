package dev.baranhan.viltrumitecore.client.perf;

import dev.baranhan.viltrumitecore.perf.Degrade;
import dev.baranhan.viltrumitecore.perf.DistanceLod;
import dev.baranhan.viltrumitecore.perf.PerfPriority;

/**
 * How likely one cosmetic particle is to be spawned, from the quality setting, its priority, its distance to the
 * camera and the frame-time pressure. At HIGH without load the answer is always 1: the game looks as designed.
 * Pure.
 */
public final class ParticleLod {
   /** Full detail up to this distance (blocks, scaled by {@link EffectQuality#lod}). */
   public static final double NEAR = 96.0;
   /** The priority's floor from this distance on. */
   public static final double FAR = 320.0;

   private ParticleLod() {
   }

   public static double keep(EffectQuality quality, PerfPriority priority, double distanceSq, double pressure) {
      if (priority.critical()) {
         return 1.0;
      }

      double load = Math.max(0.0, Math.min(1.0, pressure));
      double blend = quality.lodAlways ? 1.0 : load;
      double lod = 1.0;
      if (blend > 0.0) {
         double factor = DistanceLod.factorSq(priority, distanceSq, NEAR * quality.lod, FAR * quality.lod);
         lod = 1.0 - blend * (1.0 - factor);
      }

      double floor = Math.max(priority.lodFloor, quality.pressureFloor * priority.share);
      return Math.min(1.0, quality.spawn * lod * Degrade.scale(load, floor));
   }

   /** Live cosmetic particles allowed at once (vanilla keeps up to 16384 per render type). */
   public static int liveLimit(int base, EffectQuality quality, double pressure) {
      return Math.max(256, (int)Math.round(base * quality.live * Degrade.scale(pressure, quality.pressureFloor)));
   }

   /** New cosmetic particles allowed per client tick. */
   public static int rateLimit(int base, EffectQuality quality, double pressure) {
      return Math.max(64, (int)Math.round(base * quality.live * Degrade.scale(pressure, quality.pressureFloor)));
   }
}

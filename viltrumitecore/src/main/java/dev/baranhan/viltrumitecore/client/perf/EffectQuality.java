package dev.baranhan.viltrumitecore.client.perf;

import java.util.Locale;

/**
 * The player's effect-quality setting ({@code effectQuality} in viltrumitecore_client.json, {@code /vfx quality}).
 * HIGH is the game as designed: nothing is thinned until the frame time says the machine is struggling.
 * LOW and MEDIUM always thin by distance and spawn less; ULTRA draws further, allows more live particles and
 * never adapts to load. Pure.
 */
public enum EffectQuality {
   LOW(0.35, 0.5, 0.35, 0.35, true, true),
   MEDIUM(0.65, 0.75, 0.65, 0.5, true, true),
   HIGH(1.0, 1.0, 1.0, 0.6, false, true),
   ULTRA(1.0, 1.5, 1.5, 0.8, false, false);

   /** Share of cosmetic particles that is spawned at all. */
   public final double spawn;
   /** Scale of the full-detail and floor distances of the distance LOD. */
   public final double lod;
   /** Scale of the live-particle cap and the per-tick spawn rate. */
   public final double live;
   /** Lowest share kept when the frame time is at its worst. */
   public final double pressureFloor;
   /** True: the distance LOD is always on; false: it grows in only with load. */
   public final boolean lodAlways;
   /** True: the frame time thins effects (the -Dviltrumitecore.perf.adaptive=false switch turns it off everywhere). */
   public final boolean adaptive;

   EffectQuality(double spawn, double lod, double live, double pressureFloor, boolean lodAlways, boolean adaptive) {
      this.spawn = spawn;
      this.lod = lod;
      this.live = live;
      this.pressureFloor = pressureFloor;
      this.lodAlways = lodAlways;
      this.adaptive = adaptive;
   }

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static EffectQuality parse(String value, EffectQuality fallback) {
      if (value == null) {
         return fallback;
      }

      String wanted = value.trim();
      for (EffectQuality quality : values()) {
         if (quality.id().equalsIgnoreCase(wanted)) {
            return quality;
         }
      }

      return fallback;
   }
}

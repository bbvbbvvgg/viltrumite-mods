package dev.baranhan.viltrumitecore.client.sinclair;

import net.minecraft.world.phys.Vec3;

/**
 * The two spectral threads between Sinclair and Savannah while she is on stage (playtest 2026-10-30, after
 * Deadlock's look: two ghostly lines run from the magician to the assistant). Pure geometry: both threads
 * leave Sinclair's chest a little apart, sag under their own weight, slowly wind around each other and
 * meet Savannah's chest; the ends are pinned. {@link SinclairVfx} draws them as camera-facing pixels.
 */
public final class SpectralTether {
   /** Chest height of Sinclair above his feet and of Savannah above her floating base, blocks. */
   public static final double SINCLAIR_CHEST = 1.2;
   public static final double SAVANNAH_CHEST = 1.15;
   /** Half distance between the two threads at both ends, blocks. */
   public static final double SPREAD = 0.15;
   /** Widest winding radius (mid-thread), blocks. */
   public static final double WIND = 0.14;
   /** Sag per block of length and its cap, blocks. */
   public static final double SAG_PER_BLOCK = 0.035;
   public static final double MAX_SAG = 0.7;
   /** Longest thread drawn (Savannah leaves past the 48-block leash anyway). */
   public static final double MAX_LENGTH = 48.0;

   private SpectralTether() {
   }

   /** End point of thread {@code strand} (0 or 1) at a chest {@code chest}, facing {@code yawDeg}. */
   public static Vec3 end(Vec3 feet, double chest, float yawDeg, int strand) {
      double yaw = Math.toRadians(yawDeg);
      Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
      return feet.add(0.0, chest, 0.0).add(right.scale(strand == 0 ? SPREAD : -SPREAD));
   }

   /** Point {@code u} (0 at Sinclair, 1 at Savannah) of thread {@code strand} at time {@code seconds}. */
   public static Vec3 point(Vec3 from, Vec3 to, double u, int strand, double seconds) {
      Vec3 d = to.subtract(from);
      double len = d.length();
      Vec3 base = from.add(d.scale(u));
      if (len < 1.0E-4) {
         return base;
      }

      double pin = Math.sin(Math.PI * u);
      double sag = Math.min(MAX_SAG, len * SAG_PER_BLOCK) * 4.0 * u * (1.0 - u);
      Vec3 n = d.scale(1.0 / len);
      Vec3 side = Math.abs(n.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 up = side.cross(n);
      double phase = u * len * 1.4 - seconds * 1.8 + strand * Math.PI;
      double r = WIND * pin;
      return base.add(0.0, -sag, 0.0).add(side.scale(Math.cos(phase) * r)).add(up.scale(Math.sin(phase) * r));
   }

   /** Dots per thread for a length: dense enough to read as a line, bounded. */
   public static int dots(double len) {
      return (int) Math.max(12, Math.min(220, Math.round(len * 9.0)));
   }
}

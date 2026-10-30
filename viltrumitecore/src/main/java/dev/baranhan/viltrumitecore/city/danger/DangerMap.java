package dev.baranhan.viltrumitecore.city.danger;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Short-lived danger zones of one level (pure; times are game ticks). A zone is a sphere
 * with a lifetime: fights mark about 16 blocks, explosions and mass destruction about 24,
 * both for about ten seconds. Close zones merge, so a long fight is one growing zone and
 * the per-citizen check stays a scan over a handful of entries.
 */
public final class DangerMap {
   public static final float FIGHT_RADIUS = 16.0F;
   public static final float EXPLOSION_RADIUS = 24.0F;
   public static final int DEFAULT_LIFETIME = 200;
   public static final float MAX_RADIUS = 64.0F;
   public static final int MAX_ZONES = 256;

   public static final class Zone {
      public double x;
      public double y;
      public double z;
      public float radius;
      public long expires;
      public long created;

      Zone(double x, double y, double z, float radius, long created, long expires) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.radius = radius;
         this.created = created;
         this.expires = expires;
      }

      public double distanceSq(double px, double py, double pz) {
         double dx = px - this.x;
         double dy = (py - this.y) * 0.5; // vertical distance matters less (fights in the air above a street)
         double dz = pz - this.z;
         return dx * dx + dy * dy + dz * dz;
      }
   }

   private final List<Zone> zones = new ArrayList<>();

   /** Adds (or merges into a close zone) a danger sphere alive until now + lifetime. */
   public void add(double x, double y, double z, float radius, long now, int lifetime) {
      if (!(radius > 0.0F) || Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
         return;
      }

      radius = Math.min(radius, MAX_RADIUS);
      long expires = now + Math.max(1, lifetime);
      for (Zone zn : this.zones) {
         double d = Math.sqrt(zn.distanceSq(x, y, z));
         if (d <= Math.max(4.0, Math.min(zn.radius, radius) * 0.5)) {
            // same place: keep the bigger sphere, extend the life, drift the centre a little
            zn.x = zn.x * 0.75 + x * 0.25;
            zn.y = zn.y * 0.75 + y * 0.25;
            zn.z = zn.z * 0.75 + z * 0.25;
            zn.radius = Math.max(zn.radius, radius);
            zn.expires = Math.max(zn.expires, expires);
            return;
         }
      }

      if (this.zones.size() >= MAX_ZONES) {
         int oldest = 0;
         for (int i = 1; i < this.zones.size(); i++) {
            if (this.zones.get(i).expires < this.zones.get(oldest).expires) {
               oldest = i;
            }
         }

         this.zones.remove(oldest);
      }

      this.zones.add(new Zone(x, y, z, radius, now, expires));
   }

   public void prune(long now) {
      this.zones.removeIf(zn -> zn.expires <= now);
   }

   /** The zone threatening a position the most (deepest inside, relative to its radius), or null. */
   @Nullable
   public Zone threat(double x, double y, double z, long now, double margin) {
      Zone best = null;
      double bestScore = Double.MAX_VALUE;
      for (Zone zn : this.zones) {
         if (zn.expires <= now) {
            continue;
         }

         double r = zn.radius + margin;
         double ds = zn.distanceSq(x, y, z);
         if (ds < r * r) {
            double score = Math.sqrt(ds) / r;
            if (score < bestScore) {
               bestScore = score;
               best = zn;
            }
         }
      }

      return best;
   }

   /** True when the point is inside any live zone (used to keep flee targets out of danger). */
   public boolean dangerous(double x, double y, double z, long now) {
      return this.threat(x, y, z, now, 0.0) != null;
   }

   public int size() {
      return this.zones.size();
   }

   public List<Zone> zones() {
      return this.zones;
   }

   public void clear() {
      this.zones.clear();
   }
}

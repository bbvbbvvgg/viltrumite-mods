package dev.baranhan.viltrumitecore.city.danger;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Short-lived danger zones of one level (pure; times are game ticks). A zone is a sphere
 * with a lifetime: fights mark about 16 blocks, explosions and mass destruction about 24,
 * both for about ten seconds. Close zones merge, so a long fight is one growing zone and
 * the per-citizen check stays a scan over a handful of entries.
 *
 * <p>Living City 2.0: every zone also has a <b>notice</b> radius (people further away
 * than the danger radius still hear and see it: they stop and look), zones can follow a
 * moving source (a soldier, a chariot, a leviathan, a projectile: {@link #track}), a zone
 * with radius 0 is a pure curiosity (a hero flying past) and {@link #blast} keeps the
 * shock waves of the last ticks (people close to one are knocked down).
 */
public final class DangerMap {
   public static final float FIGHT_RADIUS = 16.0F;
   public static final float EXPLOSION_RADIUS = 24.0F;
   public static final int DEFAULT_LIFETIME = 200;
   public static final float MAX_RADIUS = 64.0F;
   public static final int MAX_ZONES = 256;
   public static final float MAX_NOTICE = 112.0F;
   /** Ticks a shock wave can knock people down after it happened. */
   public static final int BLAST_WINDOW = 6;

   public static final class Zone {
      public double x;
      public double y;
      public double z;
      public float radius;
      public long expires;
      public long created;
      /** People inside this sphere notice the zone (look, stop, get nervous); always >= radius. */
      public float notice;
      /** Entity id of a moving source ({@link #track}), or -1. */
      public int source = -1;
      /** Follows its source ({@link #track}) instead of merging with neighbours. */
      public boolean tracked;

      Zone(double x, double y, double z, float radius, long created, long expires) {
         this(x, y, z, radius, defaultNotice(radius), created, expires);
      }

      Zone(double x, double y, double z, float radius, float notice, long created, long expires) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.radius = radius;
         this.notice = Math.max(radius, notice);
         this.created = created;
         this.expires = expires;
      }

      /** A curiosity only (radius 0): never makes anybody flee by itself. */
      public boolean alertOnly() {
         return this.radius <= 0.0F;
      }

      public double distanceSq(double px, double py, double pz) {
         double dx = px - this.x;
         double dy = (py - this.y) * 0.5; // vertical distance matters less (fights in the air above a street)
         double dz = pz - this.z;
         return dx * dx + dy * dy + dz * dz;
      }
   }

   private final List<Zone> zones = new ArrayList<>();
   private final List<double[]> blasts = new ArrayList<>();

   /** Notice radius of a zone added without one: twice the danger plus a street width. */
   public static float defaultNotice(float radius) {
      return radius <= 0.0F ? 0.0F : Math.min(MAX_NOTICE, radius * 2.0F + 8.0F);
   }

   /** Adds (or merges into a close zone) a danger sphere alive until now + lifetime. */
   public void add(double x, double y, double z, float radius, long now, int lifetime) {
      this.add(x, y, z, radius, defaultNotice(Math.min(radius, MAX_RADIUS)), now, lifetime);
   }

   /** Adds (or merges) a zone with an explicit notice radius; radius 0 = curiosity only. */
   public void add(double x, double y, double z, float radius, float notice, long now, int lifetime) {
      if (!valid(x, y, z, radius, notice)) {
         return;
      }

      radius = Math.max(0.0F, Math.min(radius, MAX_RADIUS));
      notice = Math.min(Math.max(notice, radius), MAX_NOTICE);
      long expires = now + Math.max(1, lifetime);
      for (Zone zn : this.zones) {
         if (zn.tracked || zn.alertOnly() != radius <= 0.0F) {
            continue; // moving sources keep their own zone; curiosities never merge into dangers
         }

         double d = Math.sqrt(zn.distanceSq(x, y, z));
         if (d <= Math.max(4.0, Math.min(zn.radius, radius) * 0.5)) {
            // same place: keep the bigger sphere, extend the life, drift the centre a little
            zn.x = zn.x * 0.75 + x * 0.25;
            zn.y = zn.y * 0.75 + y * 0.25;
            zn.z = zn.z * 0.75 + z * 0.25;
            zn.radius = Math.max(zn.radius, radius);
            zn.notice = Math.max(zn.notice, notice);
            zn.expires = Math.max(zn.expires, expires);
            return;
         }
      }

      this.insert(new Zone(x, y, z, radius, notice, now, expires));
   }

   /**
    * A zone that follows a moving source (entity id): the existing zone of that source jumps
    * to the new position (no drift, no lag behind a flying chariot), else a new one starts.
    */
   public void track(int source, double x, double y, double z, float radius, float notice, long now, int lifetime) {
      if (!valid(x, y, z, radius, notice)) {
         return;
      }

      radius = Math.max(0.0F, Math.min(radius, MAX_RADIUS));
      notice = Math.min(Math.max(notice, radius), MAX_NOTICE);
      long expires = now + Math.max(1, lifetime);
      for (Zone zn : this.zones) {
         if (zn.tracked && zn.source == source) {
            zn.x = x;
            zn.y = y;
            zn.z = z;
            zn.radius = radius;
            zn.notice = notice;
            zn.expires = Math.max(zn.expires, expires);
            return;
         }
      }

      Zone zn = new Zone(x, y, z, radius, notice, now, expires);
      zn.source = source;
      zn.tracked = true;
      this.insert(zn);
   }

   private static boolean valid(double x, double y, double z, float radius, float notice) {
      return !Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z) && !Double.isInfinite(x) && !Double.isInfinite(z)
         && (radius > 0.0F || notice > 0.0F) && !Float.isNaN(radius) && !Float.isNaN(notice);
   }

   private void insert(Zone zone) {
      if (this.zones.size() >= MAX_ZONES) {
         int oldest = 0;
         for (int i = 1; i < this.zones.size(); i++) {
            if (this.zones.get(i).expires < this.zones.get(oldest).expires) {
               oldest = i;
            }
         }

         this.zones.remove(oldest);
      }

      this.zones.add(zone);
   }

   public void prune(long now) {
      this.zones.removeIf(zn -> zn.expires <= now);
      this.blasts.removeIf(b -> b[4] + BLAST_WINDOW <= now);
   }

   /** A shock wave (explosion, slam, lightning) of this radius happened now. */
   public void blast(double x, double y, double z, float radius, long now) {
      if (radius > 0.0F && !Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
         if (this.blasts.size() >= 64) {
            this.blasts.remove(0);
         }

         this.blasts.add(new double[]{x, y, z, Math.min(radius, MAX_RADIUS), now});
      }
   }

   /** Strength 0..1 of the strongest fresh shock wave at a point (1 = at its centre). */
   public float blastAt(double x, double y, double z, long now) {
      float best = 0.0F;
      for (double[] b : this.blasts) {
         if (b[4] + BLAST_WINDOW <= now || b[4] > now) {
            continue;
         }

         double dx = x - b[0];
         double dy = (y - b[1]) * 0.7;
         double dz = z - b[2];
         double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (d < b[3]) {
            best = Math.max(best, (float)(1.0 - d / b[3]));
         }
      }

      return best;
   }

   /**
    * The zone a person at this point notices the most (smallest distance relative to its
    * notice radius), dangers before curiosities at the same score; null when nothing is near.
    */
   @Nullable
   public Zone noticed(double x, double y, double z, long now) {
      Zone best = null;
      double bestScore = Double.MAX_VALUE;
      for (Zone zn : this.zones) {
         if (zn.expires <= now || zn.notice <= 0.0F) {
            continue;
         }

         double ds = zn.distanceSq(x, y, z);
         if (ds < (double)zn.notice * zn.notice) {
            double score = Math.sqrt(ds) / zn.notice + (zn.alertOnly() ? 1.0 : 0.0);
            if (score < bestScore) {
               bestScore = score;
               best = zn;
            }
         }
      }

      return best;
   }

   /** Live dangerous zones (not curiosities) whose danger sphere comes within margin of the point. */
   public int dangersNear(double x, double y, double z, long now, double margin) {
      int n = 0;
      for (Zone zn : this.zones) {
         if (zn.expires > now && !zn.alertOnly()) {
            double r = zn.radius + margin;
            if (zn.distanceSq(x, y, z) < r * r) {
               n++;
            }
         }
      }

      return n;
   }

   /** The zone threatening a position the most (deepest inside, relative to its radius), or null. */
   @Nullable
   public Zone threat(double x, double y, double z, long now, double margin) {
      Zone best = null;
      double bestScore = Double.MAX_VALUE;
      for (Zone zn : this.zones) {
         if (zn.expires <= now || zn.alertOnly()) {
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
      this.blasts.clear();
   }
}

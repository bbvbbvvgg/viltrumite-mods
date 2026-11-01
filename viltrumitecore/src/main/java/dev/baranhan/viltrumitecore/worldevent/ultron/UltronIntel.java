package dev.baranhan.viltrumitecore.worldevent.ultron;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * What the machines know about the players (pure, unit-tested): last sightings (only from a unit's or a
 * sensor's real line of sight), a threat score from the damage they took and the kills, and where the
 * machines die (sector losses, for SECTOR_FOCUS). Old knowledge fades.
 */
public final class UltronIntel {
   public static final int SECTORS = 8;
   public static final float THREAT_HALF_LIFE_S = 90.0F;
   public static final float LOSS_HALF_LIFE_S = 60.0F;
   /** Sightings older than this are forgotten. */
   public static final int FORGET_TICKS = 120 * UltronRules.TPS;
   /** A sector holding at least this many recent losses and this share of all of them is "focused". */
   public static final float FOCUS_MIN = 4.0F;
   public static final float FOCUS_SHARE = 0.6F;

   public static final class Sighting {
      public final UUID id;
      public double x;
      public double y;
      public double z;
      public int tick;
      public boolean flying;
      public float threat;

      Sighting(UUID id) {
         this.id = id;
      }
   }

   private final Map<UUID, Sighting> sightings = new HashMap<>();
   private final float[] losses = new float[SECTORS];

   public void saw(UUID id, double x, double y, double z, int tick, boolean flying) {
      Sighting s = this.sightings.computeIfAbsent(id, Sighting::new);
      s.x = x;
      s.y = y;
      s.z = z;
      s.tick = tick;
      s.flying = flying;
   }

   /** A known attacker hurt a machine (the threat grows even when nobody saw where he stood). */
   public void hurtBy(UUID id, float amount) {
      Sighting s = this.sightings.get(id);
      if (s != null) {
         s.threat += Math.max(0.0F, amount);
      }
   }

   public void killedBy(UUID id, float weight) {
      Sighting s = this.sightings.get(id);
      if (s != null) {
         s.threat += weight;
      }
   }

   public void lost(int sector) {
      if (sector >= 0 && sector < SECTORS) {
         this.losses[sector] += 1.0F;
      }
   }

   public float losses(int sector) {
      return sector >= 0 && sector < SECTORS ? this.losses[sector] : 0.0F;
   }

   public float totalLosses() {
      float t = 0.0F;
      for (float l : this.losses) {
         t += l;
      }
      return t;
   }

   /** The sector where the machines keep dying (losses concentrated there), or -1. */
   public int focus() {
      float total = this.totalLosses();
      int best = -1;
      float bestL = 0.0F;
      for (int i = 0; i < SECTORS; i++) {
         if (this.losses[i] > bestL) {
            bestL = this.losses[i];
            best = i;
         }
      }
      return best >= 0 && bestL >= FOCUS_MIN && bestL >= total * FOCUS_SHARE ? best : -1;
   }

   /** One second: threat and losses fade, old sightings go. */
   public void second(int now) {
      float k = (float)Math.pow(0.5, 1.0 / THREAT_HALF_LIFE_S);
      float l = (float)Math.pow(0.5, 1.0 / LOSS_HALF_LIFE_S);
      this.sightings.values().removeIf(s -> now - s.tick > FORGET_TICKS);
      for (Sighting s : this.sightings.values()) {
         s.threat *= k;
      }
      for (int i = 0; i < SECTORS; i++) {
         this.losses[i] *= l;
      }
   }

   @Nullable
   public Sighting get(UUID id) {
      return this.sightings.get(id);
   }

   public Collection<Sighting> all() {
      return Collections.unmodifiableCollection(this.sightings.values());
   }

   /** Most dangerous player seen within {@code window} ticks (ties: the most recent), or null. */
   @Nullable
   public Sighting top(int now, int window) {
      Sighting best = null;
      for (Sighting s : this.sightings.values()) {
         if (now - s.tick > window) {
            continue;
         }
         if (best == null || s.threat > best.threat || s.threat == best.threat && s.tick > best.tick) {
            best = s;
         }
      }
      return best;
   }

   public float topThreat(int now, int window) {
      Sighting s = this.top(now, window);
      return s == null ? 0.0F : s.threat;
   }

   public boolean aerial(int now, int window) {
      for (Sighting s : this.sightings.values()) {
         if (s.flying && now - s.tick <= window) {
            return true;
         }
      }
      return false;
   }

   public int seen(int now, int window) {
      int n = 0;
      for (Sighting s : this.sightings.values()) {
         if (now - s.tick <= window) {
            n++;
         }
      }
      return n;
   }

   /** Pure: sector (0..7) of a point around a centre (east = 0, counter-clockwise seen from above in Minecraft x/z). */
   public static int sector(double cx, double cz, double x, double z) {
      double a = Math.atan2(z - cz, x - cx);
      if (a < 0) {
         a += Math.PI * 2.0;
      }
      return Math.min(SECTORS - 1, (int)(a / (Math.PI * 2.0) * SECTORS));
   }

   /** Pure: the angle (radians) through the middle of a sector. */
   public static double sectorAngle(int sector) {
      return (sector + 0.5) * Math.PI * 2.0 / SECTORS;
   }
}

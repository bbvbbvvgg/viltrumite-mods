package dev.baranhan.viltrumitecore.worldevent.chitauri.war;

import javax.annotation.Nullable;

/**
 * One part of the city the Chitauri fight for. Pure state and rules: the server fills the presence counts from loaded
 * chunks and applies the returned transitions (subtitles, strongholds, orders).
 */
public final class WarSector {
   public static final int UNKNOWN_Y = Integer.MIN_VALUE;

   public enum State {
      UNKNOWN,
      SCOUTED,
      CONTESTED,
      HELD,
      LIBERATED
   }

   public enum Event {
      NONE,
      SCOUTED,
      CONTESTED,
      SEIZED,
      LIBERATED
   }

   public final int id;
   /** District name from the city profile, or null for a ring cell. */
   @Nullable
   public final String name;
   public final double x;
   public final double z;
   public final double radius;
   /** 0 = the portal's own sector, then rings outwards (districts: by distance). */
   public final int ring;
   public State state = State.UNKNOWN;
   public int groundY = UNKNOWN_Y;
   /** -1 (heroes own it) .. 1 (the hive owns it). */
   public float control;
   public int chitauri;
   public int heroes;
   public int citizens;
   public boolean stronghold;
   public boolean containment;
   /** Ticks since the last fighting inside (heroes and Chitauri both present). */
   public int quiet;
   /** Lifetime counters for the director and the report. */
   public int seizedCount;
   public int liberatedCount;

   public WarSector(int id, @Nullable String name, double x, double z, double radius, int ring) {
      this.id = id;
      this.name = name;
      this.x = x;
      this.z = z;
      this.radius = radius;
      this.ring = ring;
   }

   public boolean known() {
      return this.groundY != UNKNOWN_Y;
   }

   public boolean contains(double px, double pz) {
      double dx = px - this.x;
      double dz = pz - this.z;
      return dx * dx + dz * dz <= this.radius * this.radius;
   }

   public double distanceSq(double px, double pz) {
      double dx = px - this.x;
      double dz = pz - this.z;
      return dx * dx + dz * dz;
   }

   public boolean hiveOwned() {
      return this.state == State.HELD;
   }

   /**
    * One presence update: the control drifts towards the side with more weight (heroes count {@link WarRules#HERO_WEIGHT}
    * times), a stronghold anchors the hive's claim, and the state machine reports at most one transition.
    */
   public Event update(int chitauri, int heroes, int citizens) {
      this.chitauri = chitauri;
      this.heroes = heroes;
      this.citizens = citizens;
      float pressure = Math.min(6, chitauri) - Math.min(4, heroes) * WarRules.HERO_WEIGHT;
      if (this.stronghold && chitauri > 0) {
         pressure += 1.5F;
      }
      float step = Math.max(-0.2F, Math.min(0.12F, pressure * WarRules.CONTROL_STEP));
      if (chitauri == 0 && heroes == 0) {
         // an empty sector slowly returns to its owner: the hive keeps a stronghold, the city keeps the rest
         step = this.stronghold ? 0.01F : -0.01F;
      }
      this.control = Math.max(-1.0F, Math.min(1.0F, this.control + step));
      this.quiet = chitauri > 0 && heroes > 0 ? 0 : this.quiet + 1;
      return this.transition();
   }

   private Event transition() {
      switch (this.state) {
         case UNKNOWN:
            if (this.chitauri > 0) {
               this.state = State.SCOUTED;
               return Event.SCOUTED;
            }
            return Event.NONE;
         case SCOUTED:
         case LIBERATED:
            if (this.control >= WarRules.CONTEST_CONTROL && this.chitauri > 0) {
               this.state = State.CONTESTED;
               return Event.CONTESTED;
            }
            return Event.NONE;
         case CONTESTED:
            if (this.control >= WarRules.SEIZE_CONTROL && this.stronghold) {
               this.state = State.HELD;
               this.seizedCount++;
               return Event.SEIZED;
            }
            if (this.control <= WarRules.LIBERATE_CONTROL && this.chitauri == 0) {
               this.state = State.LIBERATED;
               this.liberatedCount++;
               return Event.LIBERATED;
            }
            return Event.NONE;
         case HELD:
            if (this.control <= WarRules.LIBERATE_CONTROL || !this.stronghold && this.chitauri == 0 && this.heroes > 0) {
               this.state = State.LIBERATED;
               this.liberatedCount++;
               return Event.LIBERATED;
            }
            return Event.NONE;
         default:
            return Event.NONE;
      }
   }

   /** The stronghold of this sector fell (heroes destroyed the beacon): the claim breaks at once. */
   public Event strongholdLost() {
      this.stronghold = false;
      this.control = Math.min(this.control, 0.1F);
      if (this.state == State.HELD) {
         this.state = State.CONTESTED;
      }
      return this.transition();
   }

   /** Value for the hive: citizens to take, remoteness from heroes, closeness to the portal. */
   public float value() {
      float v = 1.0F + Math.min(6, this.citizens) * 0.35F;
      if (this.heroes == 0) {
         v += 0.6F;
      }
      return v / (1.0F + this.ring * 0.15F);
   }
}

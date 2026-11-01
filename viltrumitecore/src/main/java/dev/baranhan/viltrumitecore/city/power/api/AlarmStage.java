package dev.baranhan.viltrumitecore.city.power.api;

/**
 * Stage of the city emergency (Living City 3.0). Ordered by urgency; {@link #ALL_CLEAR} follows an active or
 * critical emergency for a while (the all-clear siren, power coming back) and then falls back to {@link #NONE}.
 */
public enum AlarmStage {
   NONE,
   /** A threat is coming (a meteor shower starts, a portal opens): the warning siren rises and falls. */
   WARNING,
   /** The city is under attack or hit: the attack siren, shelters. */
   ACTIVE,
   /** Cascading failures, the heaviest phase of an invasion: the critical siren and klaxons. */
   CRITICAL,
   /** The danger is over: the all-clear tone, the grid is restored district by district. */
   ALL_CLEAR;

   private static final AlarmStage[] VALUES = values();

   /** Urgency for picking the strongest lease (ALL_CLEAR is never leased). */
   public int urgency() {
      return this == ALL_CLEAR ? 0 : this.ordinal();
   }

   public boolean emergency() {
      return this == ACTIVE || this == CRITICAL;
   }

   public String key() {
      return this.name().toLowerCase(java.util.Locale.ROOT);
   }

   public static AlarmStage byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
   }

   public static AlarmStage max(AlarmStage a, AlarmStage b) {
      return a.urgency() >= b.urgency() ? a : b;
   }
}

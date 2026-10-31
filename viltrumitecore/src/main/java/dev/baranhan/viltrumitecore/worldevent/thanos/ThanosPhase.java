package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * Fight phases (pure). Health and time both push the phase forward and the later one wins, so a
 * slow fight still escalates and a fast one does not stay in the opening. FINAL is only the last
 * minute of the timer: everything ONSLAUGHT allows, sharper (shorter telegraphs and cooldowns,
 * brighter stones, the final music).
 */
public enum ThanosPhase {
   OPENING,
   ESCALATION,
   ONSLAUGHT,
   FINAL;

   private static final ThanosPhase[] VALUES = values();

   public boolean atLeast(ThanosPhase other) {
      return this.ordinal() >= other.ordinal();
   }

   public static ThanosPhase byIndex(int index) {
      return VALUES[Math.max(0, Math.min(VALUES.length - 1, index))];
   }

   /** Phase reached by health alone (fraction of max health left, 0..1). */
   public static ThanosPhase ofHealth(float healthFraction) {
      if (!(healthFraction <= ThanosRules.ESCALATION_HEALTH)) {
         return OPENING;
      }

      return healthFraction > ThanosRules.ONSLAUGHT_HEALTH ? ESCALATION : ONSLAUGHT;
   }

   /** Phase reached by the elapsed active ticks alone. */
   public static ThanosPhase ofTime(int elapsed, int duration) {
      if (elapsed >= duration - ThanosRules.FINAL_TICKS) {
         return FINAL;
      }

      if (elapsed >= ThanosRules.ONSLAUGHT_TICK) {
         return ONSLAUGHT;
      }

      return elapsed >= ThanosRules.ESCALATION_TICK ? ESCALATION : OPENING;
   }

   /** The later of the health and time phases. */
   public static ThanosPhase of(float healthFraction, int elapsed, int duration) {
      ThanosPhase health = ofHealth(healthFraction);
      ThanosPhase time = ofTime(elapsed, duration);
      return time.ordinal() >= health.ordinal() ? time : health;
   }
}

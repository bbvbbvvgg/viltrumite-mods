package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * The four phases of the war for Atheria (pure). The ordinal is synced (entity data, state packet):
 * <ol>
 * <li>{@link #ARRIVAL}: the Sanctuary II arrives, Thanos fights alone and shows the stones;</li>
 * <li>{@link #BLACK_ORDER}: Ebony Maw and Cull Obsidian land and fight with him;</li>
 * <li>{@link #INVASION}: the Chitauri pour out of the ship's portals, the ship bombards the city;</li>
 * <li>{@link #GAUNTLET}: the full Gauntlet, stronger stones and the multi-stage combos.</li>
 * </ol>
 * A phase advances when its time gate or its health gate is reached <b>and</b> the current phase
 * has lasted its minimum stay; one phase at a time. Until then the health floor of the current
 * phase holds (see {@link #floor}), so every phase is seen and he can only fall in the last one.
 */
public enum ThanosPhase {
   ARRIVAL,
   BLACK_ORDER,
   INVASION,
   GAUNTLET;

   private static final ThanosPhase[] VALUES = values();

   public boolean atLeast(ThanosPhase other) {
      return this.ordinal() >= other.ordinal();
   }

   public static ThanosPhase byIndex(int index) {
      return VALUES[Math.max(0, Math.min(VALUES.length - 1, index))];
   }

   /** The phase after this one (the last one stays). */
   public ThanosPhase next() {
      return VALUES[Math.min(VALUES.length - 1, this.ordinal() + 1)];
   }

   public boolean last() {
      return this == GAUNTLET;
   }

   /** Elapsed active ticks from which this phase starts by time (0 for the first). */
   public int timeGate() {
      return switch (this) {
         case ARRIVAL -> 0;
         case BLACK_ORDER -> ThanosRules.BLACK_ORDER_TICK;
         case INVASION -> ThanosRules.INVASION_TICK;
         case GAUNTLET -> ThanosRules.GAUNTLET_TICK;
      };
   }

   /** Health fraction at or below which this phase starts (1 for the first). */
   public float healthGate() {
      return switch (this) {
         case ARRIVAL -> 1.0F;
         case BLACK_ORDER -> ThanosRules.BLACK_ORDER_HEALTH;
         case INVASION -> ThanosRules.INVASION_HEALTH;
         case GAUNTLET -> ThanosRules.GAUNTLET_HEALTH;
      };
   }

   /** Ticks this phase lasts at least before the next one may start (0 for the last). */
   public int minStay() {
      return switch (this) {
         case ARRIVAL -> ThanosRules.ARRIVAL_MIN_TICKS;
         case BLACK_ORDER -> ThanosRules.BLACK_ORDER_MIN_TICKS;
         case INVASION -> ThanosRules.INVASION_MIN_TICKS;
         case GAUNTLET -> 0;
      };
   }

   /**
    * Health fraction he cannot be brought under while in this phase: the next phase's health gate
    * (the gauntlet absorbs the rest until the phase changes); 0 in the last phase.
    */
   public float floor() {
      return this.last() ? 0.0F : this.next().healthGate();
   }

   /** Phase reached by health alone (fraction of max health left, 0..1), ignoring the stays. */
   public static ThanosPhase ofHealth(float healthFraction) {
      ThanosPhase out = ARRIVAL;
      for (ThanosPhase p : VALUES) {
         if (healthFraction <= p.healthGate()) {
            out = p;
         }
      }

      return out;
   }

   /** Phase reached by the elapsed active ticks alone, ignoring the stays. */
   public static ThanosPhase ofTime(int elapsed) {
      ThanosPhase out = ARRIVAL;
      for (ThanosPhase p : VALUES) {
         if (elapsed >= p.timeGate()) {
            out = p;
         }
      }

      return out;
   }

   /**
    * The phase for this tick: {@code current}, or the next one when its time or health gate is
    * reached and {@code current} has lasted its minimum stay ({@code inPhase} ticks). Never more
    * than one step per call.
    */
   public static ThanosPhase advance(ThanosPhase current, float healthFraction, int elapsed, int inPhase) {
      if (current.last() || inPhase < current.minStay()) {
         return current;
      }

      ThanosPhase next = current.next();
      return elapsed >= next.timeGate() || healthFraction <= next.healthGate() ? next : current;
   }
}

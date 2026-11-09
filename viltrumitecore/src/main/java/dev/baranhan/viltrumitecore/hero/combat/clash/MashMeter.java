package dev.baranhan.viltrumitecore.hero.combat.clash;

import java.util.ArrayDeque;

/**
 * Pure, server-owned mash input for one side of a struggle. Pulses are counted in a sliding window with a hard cap
 * (bunched packets after a lag spike still count, macros gain nothing past the cap). Hitting the beat grants a short
 * surge; lateness is forgiven by the sender's one-way latency so a laggy player can still hit the beat.
 */
public final class MashMeter {
   public enum Pulse {
      IGNORED,
      PUSH,
      SURGE
   }

   private final ArrayDeque<Integer> pulses = new ArrayDeque<>();
   private int surgeUntil = Integer.MIN_VALUE;
   private int lastBeat = Integer.MIN_VALUE;
   private int accepted;
   private int surges;

   /**
    * Registers one pulse.
    *
    * @param tick         struggle age when the packet arrived
    * @param beatOrigin   struggle age of beat 0 (beats repeat every {@link ClashRules#BEAT_PERIOD})
    * @param latencyTicks the sender's one-way latency in ticks
    */
   public Pulse pulse(int tick, int beatOrigin, int latencyTicks) {
      this.drop(tick);
      if (this.pulses.size() >= ClashRules.MAX_PULSES_PER_WINDOW) {
         return Pulse.IGNORED;
      }

      this.pulses.addLast(tick);
      this.accepted++;
      int since = tick - beatOrigin;
      int index = Math.floorDiv(since + ClashRules.BEAT_EARLY, ClashRules.BEAT_PERIOD);
      if (index >= 0 && index != this.lastBeat) {
         int offset = since - index * ClashRules.BEAT_PERIOD;
         int late = ClashRules.BEAT_LATE + Math.max(0, Math.min(latencyTicks, ClashRules.MAX_LATENCY_TICKS));
         if (offset <= late) {
            this.lastBeat = index;
            this.surgeUntil = tick + ClashRules.SURGE_TICKS;
            this.surges++;
            return Pulse.SURGE;
         }
      }

      return Pulse.PUSH;
   }

   public int pulsesInWindow(int tick) {
      this.drop(tick);
      return this.pulses.size();
   }

   public boolean surging(int tick) {
      return tick < this.surgeUntil;
   }

   /** Current pressure for a beam of {@code power}. */
   public double pressure(int tick, double power) {
      double pressure = power * (1.0 + ClashRules.PULSE_BONUS * this.pulsesInWindow(tick));
      return this.surging(tick) ? pressure * ClashRules.SURGE_MULTIPLIER : pressure;
   }

   public int accepted() {
      return this.accepted;
   }

   public int surges() {
      return this.surges;
   }

   private void drop(int tick) {
      while (!this.pulses.isEmpty() && tick - this.pulses.peekFirst() >= ClashRules.PULSE_WINDOW) {
         this.pulses.removeFirst();
      }
   }
}

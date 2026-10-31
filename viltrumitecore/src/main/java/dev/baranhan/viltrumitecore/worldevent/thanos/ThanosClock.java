package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * The event timer (pure). It counts ACTIVE ticks: one per server tick while the fight runs and
 * the world is not stopped by another pause (a stopped world stops Thanos too, so the heroes do
 * not lose time to it). Stopping it (victory, snap, /thanos stop) freezes the value.
 */
public final class ThanosClock {
   private final int duration;
   private int elapsed;
   private boolean stopped;

   public ThanosClock(int duration) {
      if (duration <= 0) {
         throw new IllegalArgumentException("duration " + duration);
      }

      this.duration = duration;
   }

   /** One server tick. Returns true when this tick advanced the clock. */
   public boolean advance(boolean worldStopped) {
      if (this.stopped || worldStopped || this.elapsed >= this.duration) {
         return false;
      }

      this.elapsed++;
      return true;
   }

   public void stop() {
      this.stopped = true;
   }

   public boolean stopped() {
      return this.stopped;
   }

   public boolean expired() {
      return this.elapsed >= this.duration;
   }

   public int elapsed() {
      return this.elapsed;
   }

   public int duration() {
      return this.duration;
   }

   public int remaining() {
      return Math.max(0, this.duration - this.elapsed);
   }

   public boolean finalMinute() {
      return this.remaining() <= ThanosRules.FINAL_TICKS;
   }

   /** Debug/QA: jump so that {@code ticksLeft} remain (never past the end, never backwards past 0). */
   public void skipTo(int ticksLeft) {
      if (!this.stopped) {
         this.elapsed = Math.max(0, Math.min(this.duration, this.duration - Math.max(0, ticksLeft)));
      }
   }
}

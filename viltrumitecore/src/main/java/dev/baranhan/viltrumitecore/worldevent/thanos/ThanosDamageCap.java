package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * Per-hit and per-second damage caps (pure). Every health loss of Thanos goes through
 * {@link #allow}: one-shot paths (clean damage that sets the health, MAX-damage annihilation,
 * setHealth(0)) are clamped like ordinary hits, so the fight cannot be skipped. The window is a
 * ring of the last {@link ThanosRules#TPS} ticks.
 */
public final class ThanosDamageCap {
   private final float[] window = new float[ThanosRules.TPS];
   private int index;
   private float sum;
   private float recentWindow;
   private final float[] recent = new float[ThanosRules.REWIND_WINDOW];
   private int recentIndex;
   private float total;
   private long last = Long.MIN_VALUE;

   /** How much of {@code requested} may be taken now, given the max health. Never negative. */
   public float allow(float requested, float maxHealth) {
      if (!(requested > 0.0F)) {
         return 0.0F;
      }

      float room = Math.max(0.0F, ThanosRules.secondCap(maxHealth) - this.sum);
      return Math.min(Math.min(requested, ThanosRules.hitCap(maxHealth)), room);
   }

   /** Damage actually taken this tick. */
   public void record(float taken) {
      if (taken > 0.0F) {
         this.window[this.index] += taken;
         this.sum += taken;
         this.recent[this.recentIndex] += taken;
         this.recentWindow += taken;
         this.total += taken;
      }
   }

   /**
    * Rolls both windows forward to server tick {@code now}. The body and the event both call it
    * every tick (the second call of a tick does nothing), so the windows keep real time while a
    * time stop holds the body: old hits still leave the window and the caps never lock him.
    */
   public void advanceTo(long now) {
      if (this.last == Long.MIN_VALUE || now < this.last) {
         this.last = now;
         return;
      }

      long steps = Math.min(now - this.last, (long)Math.max(this.window.length, this.recent.length));
      for (long i = 0; i < steps; i++) {
         this.tick();
      }

      this.last = now;
   }

   /** One server tick passed: the oldest tick leaves both windows. */
   public void tick() {
      this.index = (this.index + 1) % this.window.length;
      this.sum = Math.max(0.0F, this.sum - this.window[this.index]);
      this.window[this.index] = 0.0F;
      this.recentIndex = (this.recentIndex + 1) % this.recent.length;
      this.recentWindow = Math.max(0.0F, this.recentWindow - this.recent[this.recentIndex]);
      this.recent[this.recentIndex] = 0.0F;
   }

   /** Damage within the last second. */
   public float lastSecond() {
      return this.sum;
   }

   /** Damage within the rewind window (last 4 s). */
   public float recent() {
      return this.recentWindow;
   }

   /** Everything taken since the spawn (channel interrupts compare two readings). */
   public float total() {
      return this.total;
   }

   /** Time Rewind consumed the window. */
   public void clearRecent() {
      java.util.Arrays.fill(this.recent, 0.0F);
      this.recentWindow = 0.0F;
   }
}

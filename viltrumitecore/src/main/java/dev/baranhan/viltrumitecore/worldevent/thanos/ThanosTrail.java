package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * Ring of Thanos's recent positions for Time Rewind (pure, one sample per tick).
 */
public final class ThanosTrail {
   private final double[] xs;
   private final double[] ys;
   private final double[] zs;
   private int head;
   private int size;

   public ThanosTrail(int capacity) {
      this.xs = new double[capacity];
      this.ys = new double[capacity];
      this.zs = new double[capacity];
   }

   public void push(double x, double y, double z) {
      this.head = (this.head + 1) % this.xs.length;
      this.xs[this.head] = x;
      this.ys[this.head] = y;
      this.zs[this.head] = z;
      this.size = Math.min(this.size + 1, this.xs.length);
   }

   public int size() {
      return this.size;
   }

   /** Position {@code ticksAgo} ticks back (clamped to the oldest sample), or null when empty. */
   public double[] back(int ticksAgo) {
      if (this.size == 0) {
         return null;
      }

      int back = Math.max(0, Math.min(this.size - 1, ticksAgo));
      int i = Math.floorMod(this.head - back, this.xs.length);
      return new double[]{this.xs[i], this.ys[i], this.zs[i]};
   }

   public void clear() {
      this.size = 0;
   }
}

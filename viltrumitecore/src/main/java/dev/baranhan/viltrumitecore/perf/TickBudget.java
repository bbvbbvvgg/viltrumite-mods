package dev.baranhan.viltrumitecore.perf;

/**
 * A per-tick allowance of work units with priority reserves. A priority and everything below it together may
 * fill at most {@link PerfPriority#share} of the capacity, so the priorities above always find their reserve
 * free no matter who asked first; while the budget has room, everyone gets everything. CRITICAL is never denied.
 * Work that does not fit is the caller's to defer (block queues) or thin (cosmetic effects). One budget belongs
 * to one thread. Pure.
 */
public final class TickBudget {
   private static final PerfPriority[] PRIORITIES = PerfPriority.values();
   private final String name;
   private final int[] usedBy = new int[PRIORITIES.length];
   private int capacity;
   private int used;
   private int denied;
   private int lastCapacity;
   private int lastUsed;
   private int lastDenied;
   private long grantedTotal;
   private long deniedTotal;

   public TickBudget(String name, int capacity) {
      this.name = name;
      this.capacity = Math.max(0, capacity);
      this.lastCapacity = this.capacity;
   }

   /** Starts a new tick; the finished one becomes the "last" snapshot shown by the overlay. */
   public void reset(int capacity) {
      this.lastCapacity = this.capacity;
      this.lastUsed = this.used;
      this.lastDenied = this.denied;
      this.capacity = Math.max(0, capacity);
      this.used = 0;
      this.denied = 0;
      java.util.Arrays.fill(this.usedBy, 0);
   }

   /** Units {@code priority} may still take this tick (CRITICAL: unbounded). */
   public int remaining(PerfPriority priority) {
      if (priority.critical()) {
         return Integer.MAX_VALUE;
      }

      int atOrBelow = 0;
      for (int i = priority.ordinal(); i < PRIORITIES.length; i++) {
         atOrBelow += this.usedBy[i];
      }

      int share = (int)Math.floor(this.capacity * priority.share) - atOrBelow;
      return Math.max(0, Math.min(this.capacity - this.used, share));
   }

   /** Grants up to {@code wanted} units and returns how many (0..wanted). */
   public int take(PerfPriority priority, int wanted) {
      if (wanted <= 0) {
         return 0;
      }

      int grant = priority.critical() ? wanted : Math.min(wanted, this.remaining(priority));
      this.used += grant;
      this.usedBy[priority.ordinal()] += grant;
      this.denied += wanted - grant;
      this.grantedTotal += grant;
      this.deniedTotal += wanted - grant;
      return grant;
   }

   public boolean tryTake(PerfPriority priority) {
      return this.take(priority, 1) == 1;
   }

   /**
    * Marks units as already in use at the lowest priority, outside the granted totals: a live count (entities
    * alive, particles on screen) that the cap must include. Higher priorities still get their reserve on top.
    */
   public void occupy(int units) {
      int n = Math.max(0, units);
      this.used += n;
      this.usedBy[PRIORITIES.length - 1] += n;
   }

   /** Gives back units of {@code priority} that were granted but not spent. */
   public void refund(PerfPriority priority, int units) {
      int back = Math.max(0, Math.min(units, this.usedBy[priority.ordinal()]));
      this.usedBy[priority.ordinal()] -= back;
      this.used -= back;
      this.grantedTotal -= back;
   }

   public String name() {
      return this.name;
   }

   public int capacity() {
      return this.capacity;
   }

   public int used() {
      return this.used;
   }

   public int lastCapacity() {
      return this.lastCapacity;
   }

   public int lastUsed() {
      return this.lastUsed;
   }

   public int lastDenied() {
      return this.lastDenied;
   }

   public long grantedTotal() {
      return this.grantedTotal;
   }

   public long deniedTotal() {
      return this.deniedTotal;
   }

   /** Fill of the last finished tick (may exceed 1 when CRITICAL work overfilled it). */
   public double lastFill() {
      if (this.lastCapacity <= 0) {
         return this.lastUsed > 0 ? 1.0 : 0.0;
      }

      return (double)this.lastUsed / this.lastCapacity;
   }
}

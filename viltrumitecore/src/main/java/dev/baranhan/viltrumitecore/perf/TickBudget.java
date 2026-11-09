package dev.baranhan.viltrumitecore.perf;

/**
 * A per-tick allowance of work units with priority reserves (see {@link PerfPriority#share}). Work that does
 * not fit is the caller's to defer (block queues) or thin (cosmetic effects). One budget belongs to one thread.
 * Pure.
 */
public final class TickBudget {
   private final String name;
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
   }

   /** The fill level up to which {@code priority} may take (CRITICAL: unbounded). */
   public int limit(PerfPriority priority) {
      return priority.critical() ? Integer.MAX_VALUE : (int)Math.floor(this.capacity * priority.share);
   }

   public int remaining(PerfPriority priority) {
      return priority.critical() ? Integer.MAX_VALUE : Math.max(0, this.limit(priority) - this.used);
   }

   /** Grants up to {@code wanted} units and returns how many (0..wanted). CRITICAL always gets everything. */
   public int take(PerfPriority priority, int wanted) {
      if (wanted <= 0) {
         return 0;
      }

      int grant = priority.critical() ? wanted : Math.min(wanted, this.remaining(priority));
      this.used += grant;
      this.denied += wanted - grant;
      this.grantedTotal += grant;
      this.deniedTotal += wanted - grant;
      return grant;
   }

   public boolean tryTake(PerfPriority priority) {
      return this.take(priority, 1) == 1;
   }

   /** Gives back units that were granted but not spent. */
   public void refund(int units) {
      int back = Math.max(0, Math.min(units, this.used));
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

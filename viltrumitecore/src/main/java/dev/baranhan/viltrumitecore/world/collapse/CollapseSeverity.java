package dev.baranhan.viltrumitecore.world.collapse;

/** How big a collapse is. Ordinal order is part of the public contract and the network packet. */
public enum CollapseSeverity {
   /** A broken corner, a slab of facade: up to ~250 blocks. */
   MINOR(0.35F),
   /** Several floors of a section: up to ~800 blocks. */
   MAJOR(0.7F),
   /** A tower top or a whole wing: the largest allowed section. */
   CATASTROPHIC(1.0F);

   private final float strength;

   CollapseSeverity(float strength) {
      this.strength = strength;
   }

   /** 0..1 scale for damage, shake, dust and volume. */
   public float strength() {
      return this.strength;
   }

   public static CollapseSeverity of(int blocks, CollapseCause cause) {
      CollapseSeverity s = blocks < CollapseRules.MINOR_MAX_BLOCKS ? MINOR : blocks < CollapseRules.MAJOR_MAX_BLOCKS ? MAJOR : CATASTROPHIC;
      if (s == MINOR && cause.weight() >= 1.0F && blocks >= CollapseRules.MINOR_MAX_BLOCKS * 3 / 4) {
         s = MAJOR;
      }
      return s;
   }

   public static CollapseSeverity byOrdinal(int i) {
      CollapseSeverity[] v = values();
      return v[Math.max(0, Math.min(v.length - 1, i))];
   }
}

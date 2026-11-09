package dev.baranhan.viltrumitecore.perf;

/**
 * How much a piece of work (a particle, an entity, a block update) matters to what the player sees and plays.
 * Budgets serve higher priorities first: a priority may only fill {@link #share} of a budget, the rest stays
 * reserved for the priorities above it. {@link #CRITICAL} is never denied or thinned.
 */
public enum PerfPriority {
   /** Gameplay-readable feedback: the hit that landed, a telegraph, a boss silhouette. Never denied. */
   CRITICAL(1.0, 1.0),
   /** Signature effects of a hero or an event close to the camera. */
   HIGH(1.0, 0.5),
   /** Regular event effects. */
   NORMAL(0.85, 0.25),
   /** Secondary detail: dust, sparks, embers, block-break crumbs. */
   LOW(0.6, 0.1),
   /** Pure atmosphere; the first to go, may fade out completely far away. */
   AMBIENT(0.4, 0.0);

   /** Share of a budget this priority may fill. */
   public final double share;
   /** Distance LOD factor at (and beyond) the far distance. */
   public final double lodFloor;

   PerfPriority(double share, double lodFloor) {
      this.share = share;
      this.lodFloor = lodFloor;
   }

   public boolean critical() {
      return this == CRITICAL;
   }
}

package dev.baranhan.viltrumitecore.t1000;

/** The AI states of the T-1000 (docs/design/2026-11-06-t1000-design.md §5). */
public enum T1000State {
   /** An ordinary citizen. */
   DISGUISE,
   /** Walking to the target at a citizen's pace. */
   SEARCH,
   /** Following at a distance, never stared at for long. */
   STALK,
   /** The metal shows, an arm becomes a blade. */
   REVEAL,
   /** Running, leaping, flowing through bars, fighting. */
   CHASE,
   /** A puddle: crawling away and reforming. */
   RECOVER,
   /** Out of sight, a new face, back to the crowd. */
   CONCEAL,
   /** Gone for good. */
   DESTROYED;

   public boolean hunting() {
      return this != DISGUISE && this != DESTROYED;
   }
}

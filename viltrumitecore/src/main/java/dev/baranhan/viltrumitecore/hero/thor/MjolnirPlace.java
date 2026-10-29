package dev.baranhan.viltrumitecore.hero.thor;

/** Where clients draw Mjölnir. Pure, from the synced hammer state and the right hand. */
public enum MjolnirPlace {
   HAND,
   BELT,
   /** Away from Thor (thrown / returning / lying in the world): drawn by its own entity (MjolnirEntity). */
   HIDDEN;

   /** An item in the right hand always wins over the hammer, which then shows on the belt. */
   public static MjolnirPlace of(HammerState hammer, boolean handFree) {
      return of(hammer, handFree, false);
   }

   /** {@code detached}: the world hammer lies / falls elsewhere (ThorFlags.hammerDetached). */
   public static MjolnirPlace of(HammerState hammer, boolean handFree, boolean detached) {
      if (hammer.away() || detached) {
         return HIDDEN;
      }

      return hammer == HammerState.IN_HAND && handFree ? HAND : BELT;
   }
}

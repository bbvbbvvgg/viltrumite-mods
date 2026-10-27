package dev.baranhan.viltrumitecore.hero.thor;

/** Where clients draw Mjölnir. Pure, from the synced hammer state and the right hand. */
public enum MjolnirPlace {
   HAND,
   BELT,
   /** Away from Thor (thrown / returning): drawn by its own entity in stage 3. */
   HIDDEN;

   /** An item in the right hand always wins over the hammer, which then shows on the belt. */
   public static MjolnirPlace of(HammerState hammer, boolean handFree) {
      if (hammer.away()) {
         return HIDDEN;
      }

      return hammer == HammerState.IN_HAND && handFree ? HAND : BELT;
   }
}

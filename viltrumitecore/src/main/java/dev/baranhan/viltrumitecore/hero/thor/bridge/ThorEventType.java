package dev.baranhan.viltrumitecore.hero.thor.bridge;

/**
 * Bridge events between sessions. Append only. Publisher in brackets; the
 * foundation publishes none of them.
 */
public enum ThorEventType {
   /** A Thor melee hit landed (B). */
   MELEE_HIT,
   /**
    * A Thor hit would kill (B): posted before the death is final (from the
    * damage path, e.g. HeroDefinition.clampFinalDamage / onHurt), so a
    * fatality scene (C) can hold the victim. hammerInHand is the hit-time
    * snapshot.
    */
   LETHAL_HIT,
   /**
    * A combo chain ended; success = it completed (B). A successful electric
    * combo (move ELECTRIC_COMBO) triggers B's own automatic sky lightning;
    * other sessions may listen too.
    */
   COMBO_END,
   /** Mjölnir left the hand (A). */
   THROW,
   /** Mjölnir was called back (A). */
   RECALL,
   /** Mjölnir is back in the hand (A). */
   CATCH,
   /** Any server-side change of the hammer phase (A). */
   HAMMER_STATE_CHANGED
}

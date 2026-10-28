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
    * other sessions may listen too. amount = 0 (no damage of its own);
    * detail = the ElectricCombo ordinal of a successful electric combo, 0
    * otherwise.
    */
   COMBO_END,
   /** Mjölnir left the hand (A). */
   THROW,
   /** Mjölnir was called back (A). */
   RECALL,
   /** Mjölnir is back in the hand (A). */
   CATCH,
   /** Any server-side change of the hammer phase (A). */
   HAMMER_STATE_CHANGED,
   /**
    * The world hammer's slot owner changed (A): attackerId = the new holder;
    * targetId = the previous one (null when the slot was free).
    */
   HAMMER_OWNER_CHANGED,
   /** A striking move entered its effective part (MAIN) (B): attackId = the swing. */
   STRIKE_START,
   /** A striking move left its effective part or was stopped (B): attackId = the swing. */
   STRIKE_END,
   /** A paired scene began (C): move = GRAB / FATALITY, targetId = the partner. */
   SCENE_START,
   /** A paired scene ended (C): move = GRAB / FATALITY, targetId = the partner, success = it ran to its end. */
   SCENE_END
}

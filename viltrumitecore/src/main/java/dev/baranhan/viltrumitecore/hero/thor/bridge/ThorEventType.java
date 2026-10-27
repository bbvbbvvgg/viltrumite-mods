package dev.baranhan.viltrumitecore.hero.thor.bridge;

/** Bridge events between sessions. Append only. */
public enum ThorEventType {
   /** A Thor strike landed. */
   HIT,
   /** A Thor strike was lethal (the fatality trigger: a hammer-in-hand kill of a player / hero). */
   LETHAL,
   /** A combo chain ended (the automatic sky lightning listens for a successful electric combo). */
   COMBO_END,
   /** Mjölnir left the hand. */
   THROW,
   /** Mjölnir was called back. */
   RECALL,
   /** Mjölnir is back in the hand. */
   CATCH
}

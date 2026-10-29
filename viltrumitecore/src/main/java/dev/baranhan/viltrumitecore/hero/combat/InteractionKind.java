package dev.baranhan.viltrumitecore.hero.combat;

/** Outcome kinds of a resolved weapon interaction (effects are owned by the heroes, later stages). */
public enum InteractionKind {
   /** Two active strikes meet. */
   CLASH,
   /** An active strike meets a guard. */
   BLOCK,
   /** A guard timed against a strike (window-based). */
   PARRY,
   /** A thrown weapon turned away. */
   DEFLECT,
   /** A weapon knocked out of its owner's hand. */
   DISARM,
   /** A two-character scene starts (both timelines locked to one clock). */
   PAIRED_SCENE
}

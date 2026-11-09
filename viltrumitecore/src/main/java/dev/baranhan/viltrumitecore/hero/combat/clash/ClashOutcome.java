package dev.baranhan.viltrumitecore.hero.combat.clash;

/** How a clash ended for one participant. */
public enum ClashOutcome {
   WON,
   LOST,
   DRAW,
   /** Ended without a winner: death, logout, dimension change, the other side vanished. */
   CANCELLED
}

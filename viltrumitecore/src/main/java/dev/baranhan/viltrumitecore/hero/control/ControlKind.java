package dev.baranhan.viltrumitecore.hero.control;

/**
 * Control kinds the world manager tracks. VILTRUMITE_GRAB and IMPULSE are
 * policy queries over the legacy grab system, not anchored records: a grab
 * latches through the old tag path and IMPULSE guards knock/push/throw.
 */
public enum ControlKind {
   PULL,
   FREEZE,
   STASIS,
   VILTRUMITE_GRAB,
   IMPULSE,
   /** Policy query: may a symbiote take this body over (Venom's host takeover); the hold itself is a PULL record. */
   POSSESS
}

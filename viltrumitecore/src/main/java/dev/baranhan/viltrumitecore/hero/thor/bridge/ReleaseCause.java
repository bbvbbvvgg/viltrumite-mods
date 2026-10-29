package dev.baranhan.viltrumitecore.hero.thor.bridge;

/**
 * Why a held key reached its move's release. Only {@link #PLAYER} is the
 * player's intent: a {@link #FORCED} release comes from the lifecycle or a
 * hold that can no longer continue (death, logout, hero change, erasure,
 * external control / anchoring, suit off) and is always followed by the
 * lifecycle stop or the control interrupt. A charged action must not fire on
 * it.
 */
public enum ReleaseCause {
   PLAYER,
   FORCED
}

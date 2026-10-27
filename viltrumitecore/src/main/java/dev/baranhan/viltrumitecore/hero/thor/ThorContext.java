package dev.baranhan.viltrumitecore.hero.thor;

/**
 * Everything the context controls read. Built from synced data on the client
 * (claims) and from the live state on the server; the same pure resolver
 * runs on both. {@code hasTarget}, {@code flying}, {@code guarding} and
 * {@code grabbing} stay false until the stages that produce them.
 */
public record ThorContext(
   HammerState hammer,
   ThorMode mode,
   boolean handFree,
   boolean sneaking,
   boolean airborne,
   boolean flying,
   boolean hasTarget,
   boolean guarding,
   boolean grabbing
) {
   /** Standing on the ground, nothing held, no target. */
   public static ThorContext grounded(HammerState hammer, ThorMode mode) {
      return new ThorContext(hammer, mode, true, false, false, false, false, false, false);
   }
}

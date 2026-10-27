package dev.baranhan.viltrumitecore.hero.thor;

/**
 * Everything the context controls read. Built from synced data on the client
 * (claims) and from the live state on the server; the same pure resolver
 * runs on both. {@code flying} stays false until flight exists.
 * {@code enemyNear}: any enemy within ThorRules.MELEE_ENEMY_RADIUS.
 * {@code grabWindow}: the bridge grab window is open (ThorBridge).
 */
public record ThorContext(
   HammerState hammer,
   ThorMode mode,
   boolean handFree,
   boolean sneaking,
   boolean airborne,
   boolean flying,
   boolean enemyNear,
   boolean grabWindow
) {
   /** Standing on the ground, nothing held, no enemy, no window. */
   public static ThorContext grounded(HammerState hammer, ThorMode mode) {
      return new ThorContext(hammer, mode, true, false, false, false, false, false);
   }
}

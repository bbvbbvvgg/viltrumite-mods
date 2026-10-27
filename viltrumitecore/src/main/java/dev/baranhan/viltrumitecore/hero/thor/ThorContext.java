package dev.baranhan.viltrumitecore.hero.thor;

/**
 * Everything the context controls read. Built from synced data on the client
 * (claims) and from the live state on the server; the same pure resolver
 * runs on both. {@code flying} stays false until flight exists.
 * {@code enemyNear}: any enemy within ThorRules.MELEE_ENEMY_RADIUS.
 * {@code grabWindow}: the bridge grab window is open (ThorBridge).
 * {@code fistChainOpen}: an unarmed combo chain is still open (session B's
 * combo window, ThorBridge): LMB / RMB finish it even when Mjölnir is back
 * in the hand (decision #166). False on the client claim (it changes no claim).
 */
public record ThorContext(
   HammerState hammer,
   ThorMode mode,
   boolean handFree,
   boolean sneaking,
   boolean airborne,
   boolean flying,
   boolean enemyNear,
   boolean grabWindow,
   boolean fistChainOpen
) {
   /** The foundation shape: no open unarmed chain. */
   public ThorContext(HammerState hammer, ThorMode mode, boolean handFree, boolean sneaking, boolean airborne, boolean flying, boolean enemyNear,
      boolean grabWindow) {
      this(hammer, mode, handFree, sneaking, airborne, flying, enemyNear, grabWindow, false);
   }

   /** Standing on the ground, nothing held, no enemy, no window. */
   public static ThorContext grounded(HammerState hammer, ThorMode mode) {
      return new ThorContext(hammer, mode, true, false, false, false, false, false);
   }
}

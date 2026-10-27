package dev.baranhan.viltrumitecore.hero.thor;

/**
 * Everything the context controls read. Built from synced data on the client
 * (claims) and from the live state on the server; the same pure resolver
 * runs on both. {@code flying} stays false until flight exists.
 * {@code enemyNear}: any enemy within ThorRules.MELEE_ENEMY_RADIUS.
 * {@code grabWindow}: the bridge grab window is open (ThorBridge).
 * {@code hammerDetached}: the world Mjölnir rests / falls away from Thor
 * (session A); {@code hammer} then reads HOLSTERED.
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
   boolean hammerDetached
) {
   /** Hammer with Thor or flying (the world hammer is not lying elsewhere). */
   public ThorContext(HammerState hammer, ThorMode mode, boolean handFree, boolean sneaking, boolean airborne, boolean flying, boolean enemyNear,
      boolean grabWindow) {
      this(hammer, mode, handFree, sneaking, airborne, flying, enemyNear, grabWindow, false);
   }

   /** Mjölnir is away from the hand: flying, returning or lying in the world. */
   public boolean hammerAway() {
      return this.hammer.away() || this.hammerDetached;
   }

   /** Standing on the ground, nothing held, no enemy, no window. */
   public static ThorContext grounded(HammerState hammer, ThorMode mode) {
      return new ThorContext(hammer, mode, true, false, false, false, false, false);
   }
}

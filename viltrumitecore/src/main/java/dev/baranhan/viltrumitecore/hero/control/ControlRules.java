package dev.baranhan.viltrumitecore.hero.control;

/**
 * Shared deferred-damage math of the world control manager (formerly kept in the first Regulus
 * kit's rules; the values are unchanged): damage queued under FREEZE pays out at most 40 % of max
 * health, under a dome's STASIS at most 35 %; the release push of a held target is 1.2.
 */
public final class ControlRules {
   public static final double RELEASE_IMPULSE = 1.2;

   private ControlRules() {
   }

   public static float freezeDeferredCap(float maxHealth) {
      return 0.4F * maxHealth;
   }

   public static float domeDeferredCap(float maxHealth) {
      return 0.35F * maxHealth;
   }

   /** Deferred queue math: damage accumulates but never crosses the cap. */
   public static float deferredAccumulate(float queued, float incoming, float cap) {
      return Math.min(cap, Math.max(0.0F, queued + incoming));
   }
}

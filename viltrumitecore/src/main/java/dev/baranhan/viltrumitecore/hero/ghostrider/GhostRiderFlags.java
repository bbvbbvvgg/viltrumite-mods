package dev.baranhan.viltrumitecore.hero.ghostrider;

/** Bits of {@code HeroPublicSnapshot.heroFlags} for the Ghost Rider (append only). */
public final class GhostRiderFlags {
   /** The Spirit of Vengeance is out: skull, hellfire, the Rider's body. */
   public static final int RIDER = 0;
   /** Riding his own Hell Cycle. */
   public static final int RIDING = 1;
   /** The chain is on a body: its id is the snapshot controlTargetId. */
   public static final int CHAIN = 2;
   /** The hooked body is towed behind the bike (held-target seam places it on the clients). */
   public static final int TOW = 3;
   /** Penance Stare locked on a soul: controlTargetId is the victim. */
   public static final int STARE = 4;
   /** The last verdict found no sin. */
   public static final int INNOCENT = 5;
   /** Fought recently. */
   public static final int IN_COMBAT = 6;
   /** His Hell Cycle exists (parked or ridden). */
   public static final int BIKE_OUT = 7;

   private GhostRiderFlags() {
   }

   public static boolean has(int flags, int bit) {
      return (flags & 1 << bit) != 0;
   }

   public static int set(int flags, int bit, boolean on) {
      return on ? flags | 1 << bit : flags & ~(1 << bit);
   }
}

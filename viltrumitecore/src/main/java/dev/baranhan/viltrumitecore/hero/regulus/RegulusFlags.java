package dev.baranhan.viltrumitecore.hero.regulus;

/**
 * Wire layout of the Regulus snapshot (hero-owned, append only): {@code heroFlags} bits, the six
 * {@code cooldowns} and the {@code extraCooldowns} indices. {@code resource} carries the heart
 * strain (0..1000), {@code controlTargetId} the heart host's entity id and {@code resource2} the
 * ultimate's target entity id (-1 without).
 */
public final class RegulusFlags {
   public static final int LION = 0;
   public static final int HOST_ALIVE = 1;
   public static final int SEIZED = 2;
   public static final int REPLY_WINDOW = 3;
   public static final int PROCESSION = 4;
   public static final int ULT = 5;
   public static final int TARGET_HELD = 6;
   public static final int STRAIN_WARN = 7;
   public static final int LION_LOCKED = 8;
   public static final int REPLY_ARMED = 9;

   public static final int COOLDOWN_LION = 0;
   public static final int COOLDOWN_SAND = 1;
   public static final int COOLDOWN_SEVER = 2;
   public static final int COOLDOWN_PROCESSION = 3;
   public static final int COOLDOWN_REPLY = 4;
   public static final int COOLDOWN_ULT = 5;

   /** Combo step of the last touch (0..2). */
   public static final int EXTRA_COMBO = 0;
   /** Ticks left to chain the next touch. */
   public static final int EXTRA_CHAIN = 1;
   /** Number of bound subjects (Little King). */
   public static final int EXTRA_SUBJECTS = 2;
   /** Ticks until the heart hops to the next subject. */
   public static final int EXTRA_HOP = 3;
   /** Ticks of the Lion lock after a seizure. */
   public static final int EXTRA_LION_LOCK = 4;
   /** Counter bumped on every recoil / stopped projectile (client edge for VFX + sound). */
   public static final int EXTRA_RECOIL = 5;
   /** Per-cast seed (animation / VFX variant). */
   public static final int EXTRA_SEED = 6;
   /** Ticks since Lion's Heart came up (0 while off). */
   public static final int EXTRA_LION_TICKS = 7;
   /** Counter bumped on every heart hop (client edge). */
   public static final int EXTRA_HOPS = 8;
   public static final int EXTRA_COUNT = 9;

   private RegulusFlags() {
   }

   public static int with(int flags, int bit, boolean on) {
      return on ? flags | 1 << bit : flags & ~(1 << bit);
   }

   public static boolean has(int flags, int bit) {
      return (flags & 1 << bit) != 0;
   }
}

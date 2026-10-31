package dev.baranhan.viltrumitecore.hero.captain;

/** Bits of {@code HeroPublicSnapshot.heroFlags} for Captain America (append only). */
public final class CaptainFlags {
   /** The shield is in flight (a ThrownShieldEntity exists); its id is the snapshot controlTargetId. */
   public static final int SHIELD_OUT = 0;
   public static final int GUARDING = 1;
   public static final int PARRY_WINDOW = 2;
   public static final int COUNTER_READY = 3;
   public static final int CATCH_BONUS = 4;
   public static final int EMPOWERED = 5;
   public static final int IN_COMBAT = 6;
   /** The Avengers are on the field (ceremony or fight). */
   public static final int TEAM_ACTIVE = 7;
   public static final int AIMING = 8;
   public static final int IFRAMES = 9;
   /** The guard is broken (contextual interaction reid_shatters_vibranium): the shield cannot be raised. */
   public static final int SHIELD_BROKEN = 10;

   private CaptainFlags() {
   }

   public static boolean has(int flags, int bit) {
      return (flags & 1 << bit) != 0;
   }

   public static int set(int flags, int bit, boolean on) {
      return on ? flags | 1 << bit : flags & ~(1 << bit);
   }
}

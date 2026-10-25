package dev.baranhan.viltrumitecore.hero.reinhard;

/**
 * Bit layout of {@code HeroPublicSnapshot.heroFlags} for Reinhard (all
 * stages) and his snapshot indexes. Later stages only use reserved bits.
 */
public final class ReinhardFlags {
   /** Bits 0-1: mode ordinal. */
   public static final int MODE_MASK = 0b11;
   public static final int REID_DRAWN = 2;
   public static final int REID_DRAWING = 3;
   public static final int REID_SHEATHING = 4;
   public static final int PARRY_POSE = 5;
   public static final int CATCH_WINDOW = 6;
   public static final int REFLECT_WINDOW = 7;
   public static final int AIR_STEP_PULSE = 8;
   public static final int GRAB_HOLDING = 9;
   public static final int HIT_STOP = 10;
   public static final int ERASURE_CASTER = 11;
   public static final int CHOP_LEFT_FORCED = 12;
   public static final int WORLD_CUT_ACTIVE = 13;
   public static final int WORLD_ERASURE_ACTIVE = 14;
   // 15-31 reserved.

   /** extraCooldowns: 0-13 = ability cooldowns by ReinhardAbility ordinal, then these. */
   public static final int EXTRA_MODE_CD = 14;
   /** Phoenix cooldown in seconds. */
   public static final int EXTRA_PHOENIX = 15;

   private ReinhardFlags() {
   }

   public static int mode(int flags) {
      return flags & MODE_MASK;
   }

   public static int withMode(int flags, ReinhardMode mode) {
      return (flags & ~MODE_MASK) | (mode.ordinal() & MODE_MASK);
   }

   public static boolean has(int flags, int bit) {
      return (flags & (1 << bit)) != 0;
   }
}

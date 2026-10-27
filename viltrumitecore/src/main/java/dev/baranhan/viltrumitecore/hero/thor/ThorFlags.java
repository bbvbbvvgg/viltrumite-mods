package dev.baranhan.viltrumitecore.hero.thor;

/**
 * HeroPublicSnapshot.heroFlags layout for Thor.
 * <ul>
 *   <li>bits 0-1: {@link HammerState} ordinal (0 = on the belt);</li>
 *   <li>bit 2: electric mode (stage 3).</li>
 * </ul>
 * Reserved for later stages, not produced yet: bit 3 guard held (stage 2),
 * bit 4 flying (stage 4), bit 5 grab holding (stage 5). The running move is
 * not a flag: it travels as actionId / variant (phase | step &lt;&lt; 2).
 */
public final class ThorFlags {
   public static final int HAMMER_MASK = 0b11;
   public static final int ELECTRIC = 2;

   private ThorFlags() {
   }

   public static int compose(HammerState hammer, ThorMode mode) {
      int flags = hammer.ordinal() & HAMMER_MASK;
      if (mode == ThorMode.ELECTRIC) {
         flags |= 1 << ELECTRIC;
      }

      return flags;
   }

   public static HammerState hammer(int flags) {
      return HammerState.byOrdinal(flags & HAMMER_MASK);
   }

   public static ThorMode mode(int flags) {
      return (flags & 1 << ELECTRIC) != 0 ? ThorMode.ELECTRIC : ThorMode.NORMAL;
   }
}

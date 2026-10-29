package dev.baranhan.viltrumitecore.hero.jinwoo;

/** Bits of the public snapshot {@code heroFlags} owned by Jinwoo. */
public final class JinwooFlags {
   public static final int DAGGERS = 0;
   public static final int BLOCKING = 1;
   public static final int PARRY_WINDOW = 2;
   public static final int DOMAIN = 3;
   public static final int GUARD_MODE = 4;
   public static final int AUTHORITY_HOLD = 5;
   public static final int UNSTOPPABLE = 6;
   public static final int REMNANT_NEAR = 7;
   public static final int ULT = 8;
   public static final int DRAWING = 9;
   public static final int SHEATHING = 10;
   public static final int IN_COMBAT = 11;

   private JinwooFlags() {
   }

   public static boolean has(int flags, int bit) {
      return (flags & (1 << bit)) != 0;
   }

   public static int set(int flags, int bit, boolean on) {
      return on ? flags | (1 << bit) : flags & ~(1 << bit);
   }
}

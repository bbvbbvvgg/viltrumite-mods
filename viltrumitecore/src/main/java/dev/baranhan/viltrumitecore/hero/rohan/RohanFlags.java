package dev.baranhan.viltrumitecore.hero.rohan;

/** Bits of {@code HeroPublicSnapshot.heroFlags} for Rohan. */
public final class RohanFlags {
   /** Heaven's Door hovers beside him. */
   public static final int STAND_OUT = 1;
   /** The Air Sketch hangs in the air (sight trap). */
   public static final int SKETCH = 2;
   /** The Manuscript is shown (mass opening phase). */
   public static final int MANUSCRIPT = 4;
   /** A book session is open (reading pose). */
   public static final int READING = 8;
   /** Faster than a Punch is ready. */
   public static final int COUNTER = 16;
   public static final int IN_COMBAT = 32;
   /** Flying backwards (Self-Edit). */
   public static final int FLYING = 64;

   private RohanFlags() {
   }

   public static boolean has(int flags, int bit) {
      return (flags & bit) != 0;
   }

   public static int set(int flags, int bit, boolean on) {
      return on ? flags | bit : flags & ~bit;
   }
}

package dev.baranhan.viltrumitecore.t1000;

/** The shape one forearm has flowed into (2 bits each in the synced arms byte). */
public enum T1000Arm {
   HAND, BLADE, HOOK, SPIKE;

   private static final T1000Arm[] ALL = values();

   public static T1000Arm of(int bits) {
      return ALL[bits & 3];
   }

   /**
    * Packed arms byte: bits 0-1 right (main) now, 2-3 left now, 4-5 right before, 6-7 left before (the client
    * morphs from "before" to "now" over the arm's morph time).
    */
   public static int pack(T1000Arm right, T1000Arm left, T1000Arm rightBefore, T1000Arm leftBefore) {
      return right.ordinal() | left.ordinal() << 2 | rightBefore.ordinal() << 4 | leftBefore.ordinal() << 6;
   }

   public static T1000Arm right(int packed) {
      return of(packed);
   }

   public static T1000Arm left(int packed) {
      return of(packed >> 2);
   }

   public static T1000Arm rightBefore(int packed) {
      return of(packed >> 4);
   }

   public static T1000Arm leftBefore(int packed) {
      return of(packed >> 6);
   }

   public boolean weapon() {
      return this != HAND;
   }
}

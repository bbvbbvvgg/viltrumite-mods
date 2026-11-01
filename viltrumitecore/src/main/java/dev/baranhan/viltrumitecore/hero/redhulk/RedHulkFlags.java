package dev.baranhan.viltrumitecore.hero.redhulk;

/** Bits of {@code HeroPublicSnapshot.heroFlags} for the Red Hulk (append only). */
public final class RedHulkFlags {
   /** Recently fought (fighting stance, angry face). */
   public static final int IN_COMBAT = 0;
   /** A body is held in the right hand; its id is the snapshot controlTargetId. */
   public static final int HOLDING = 1;
   /** Overheated: heat abilities locked, venting. */
   public static final int OVERHEAT = 2;
   /** Thermal Fury runs (resource2 = ticks left). */
   public static final int FURY = 3;
   /** The heat aura scorches the air around him (HOT and above). */
   public static final int AURA = 4;
   /** Heat Blast can be fired (enough heat, not overheated). */
   public static final int BLAST_READY = 5;
   /** Critical Overheat can be started (enough heat, not overheated). */
   public static final int ULT_READY = 6;
   /** Heat Blast firing window (beam on). */
   public static final int BEAM = 7;

   private RedHulkFlags() {
   }

   public static boolean has(int flags, int bit) {
      return (flags & 1 << bit) != 0;
   }

   public static int set(int flags, int bit, boolean on) {
      return on ? flags | 1 << bit : flags & ~(1 << bit);
   }
}

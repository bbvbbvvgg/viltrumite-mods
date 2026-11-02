package dev.baranhan.viltrumitecore.t1000;

/**
 * One-shot body actions (synced byte + start tick): the attack timelines and the hit reactions. The server owns
 * the timing (damage lands on {@link #hitTick}); the client poses from the same numbers.
 */
public enum T1000Action {
   NONE(0, 0),
   /** Fast forward thrust of the blade arm. */
   STAB(12, 5),
   /** Wide horizontal slash, hits everything in the arc. */
   SLASH(16, 8),
   /** The hook arm shoots out, catches and pulls. */
   HOOK(22, 9),
   /** Grabs by the throat, lifts, throws. */
   GRAB(30, 6),
   /** Parries with the blade and answers at once. */
   COUNTER(14, 6),
   /** Crouch and leap. */
   LEAP(14, 4),
   /** Squeezes through bars, fences, panes or a door. */
   FLOW(18, 0),
   /** Ordinary hit: ripple. */
   HIT(10, 0),
   /** Heavy hit: deep dent, stagger, springs back. */
   HEAVY(26, 0),
   /** A shot to the head: the head splits open and flows back together. */
   FACE_SPLIT(40, 0),
   /** On fire: writhes. */
   BURN(30, 0),
   /** Frozen solid for a moment. */
   FREEZE(40, 0),
   /** The reveal tilt of the head and the first look at the target. */
   TILT(30, 0),
   /** Shakes off loose metal after a puddle or the reveal (droplets return). */
   SHAKE(16, 0);

   private static final T1000Action[] ALL = values();
   /** Length in ticks. */
   public final int ticks;
   /** Tick of the impact (attacks); 0 for reactions. */
   public final int hitTick;

   T1000Action(int ticks, int hitTick) {
      this.ticks = ticks;
      this.hitTick = hitTick;
   }

   public static T1000Action of(int ordinal) {
      return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : NONE;
   }

   public boolean attack() {
      return this == STAB || this == SLASH || this == HOOK || this == GRAB || this == COUNTER;
   }

   /** Reactions may interrupt an attack wind-up (a heavy hit, a head shot, freezing). */
   public boolean interrupts() {
      return this == HEAVY || this == FACE_SPLIT || this == FREEZE;
   }
}

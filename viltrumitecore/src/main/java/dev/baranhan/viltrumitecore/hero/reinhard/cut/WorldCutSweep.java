package dev.baranhan.viltrumitecore.hero.reinhard.cut;

/**
 * REINHARD OVERHAUL item 13: the shoulder the normal World Cut comes down from.
 *
 * The choice is automatic and effectively random: one well-mixed bit of the cast seed (the server tick of the
 * cast, already in {@link WorldCutShape} and the packet). The server puts it in the action phase (third-person
 * clip and first-person pose of the caster on every client); every client derives the same value from the packet
 * shape for the giant blade, the VFX and the blade ring. No extra network field, no protocol change. The trench,
 * the damage and the carve do not depend on it.
 */
public final class WorldCutSweep {
   /** Raised over the right shoulder, cut right to left in the caster's view (clip {@code world_cut}). */
   public static final int FROM_RIGHT = 0;
   /** Raised over the left shoulder, cut left to right (clip {@code world_cut_from_left}). */
   public static final int FROM_LEFT = 1;

   private WorldCutSweep() {
   }

   /** The sweep of a cast with this seed. */
   public static int of(long seed) {
      return (mix(seed) >>> 63) == 0 ? FROM_RIGHT : FROM_LEFT;
   }

   /** A synced action phase read as a sweep; anything unknown is the right-shoulder cut. */
   public static int ofPhase(int phase) {
      return phase == FROM_LEFT ? FROM_LEFT : FROM_RIGHT;
   }

   /** Side of the raised blade in the caster's frame: +1 right, -1 left. The stroke exits on the other side. */
   public static int side(int sweep) {
      return sweep == FROM_LEFT ? -1 : 1;
   }

   /** SplitMix64 finaliser: neighbouring cast ticks give independent bits. */
   static long mix(long z) {
      z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
      z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
      return z ^ (z >>> 31);
   }
}

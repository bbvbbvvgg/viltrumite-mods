package dev.baranhan.viltrumitecore.city.life;

/**
 * When a citizen uses its phone (#151), as a pure function of its seed and the game time (unit tested): the
 * server (a short stop during a call) and every client (pose, the phone model in the hand) compute the same
 * timeline without any packet, so all players see the same person reading or calling at the same moment.
 *
 * <p>Time runs in cycles of {@link #CYCLE} ticks, shifted per person so nobody moves in step. Only phone owners
 * ({@link #owner}) use one, in a share of their cycles, with a duration drawn per cycle: reading (take it out of
 * the pocket, raise it, look and scroll, put it away) or a call (take it out, dial, raise it to the ear, talk and
 * listen, lower it, hang up, put it away). A danger cuts the current cycle short (see {@link #cut}).
 */
public final class PhoneRoutine {
   public static final int CYCLE = 900;
   /** Out of the pocket and up; down and into the pocket (ticks). */
   public static final int DRAW = 14;
   public static final int STOW = 14;
   /** Up to the ear, down from it, a last look to hang up (ticks). */
   public static final int TO_EAR = 10;
   public static final int FROM_EAR = 10;
   public static final int HANG_UP = 12;
   /** The phone is in the hand from this tick of {@link Phase#DRAW} and until this tick of {@link Phase#STOW}. */
   public static final int IN_HAND = 6;
   public static final int OUT_OF_HAND = 9;
   /** Share (percent) of people who carry and use a phone, and of their cycles with a phone moment. */
   public static final int OWNERS = 60;
   public static final int USE = 38;

   public enum Kind {
      NONE,
      READ,
      CALL
   }

   public enum Phase {
      NONE,
      DRAW,
      LOOK,
      DIAL,
      TO_EAR,
      TALK,
      FROM_EAR,
      HANG_UP,
      STOW
   }

   /** One cycle: what, from which tick of the cycle, how long it looks/dials and talks, and a small style. */
   public record Plan(Kind kind, int start, int look, int talk, int variant) {
      public static final Plan NONE = new Plan(Kind.NONE, 0, 0, 0, 0);

      public int length() {
         return switch (this.kind) {
            case NONE -> 0;
            case READ -> DRAW + this.look + STOW;
            case CALL -> DRAW + this.look + TO_EAR + this.talk + FROM_EAR + HANG_UP + STOW;
         };
      }
   }

   /** Where a person is in its phone timeline: phase, ticks into it, its length, the cycle. */
   public record Moment(Phase phase, float t, float len, Kind kind, int variant, long cycle, float elapsed) {
      public static final Moment NONE = new Moment(Phase.NONE, 0.0F, 0.0F, Kind.NONE, 0, Long.MIN_VALUE, 0.0F);

      public boolean active() {
         return this.phase != Phase.NONE;
      }

      /** The phone model is in the hand (not yet / no longer in the pocket). */
      public boolean inHand() {
         return switch (this.phase) {
            case NONE -> false;
            case DRAW -> this.t >= IN_HAND;
            case STOW -> this.t < OUT_OF_HAND;
            default -> true;
         };
      }

      /** The screen is lit (looking at it, dialling, hanging up). */
      public boolean screenOn() {
         return this.phase == Phase.LOOK || this.phase == Phase.DIAL || this.phase == Phase.HANG_UP || this.phase == Phase.DRAW && this.t >= IN_HAND
            || this.phase == Phase.STOW && this.t < 4.0F;
      }

      /** Stops for the call (half the callers stand still while dialling and talking, the others walk on). */
      public boolean holdsStill() {
         return (this.variant & 1) == 0 && (this.phase == Phase.DIAL || this.phase == Phase.TALK || this.phase == Phase.TO_EAR);
      }
   }

   private PhoneRoutine() {
   }

   static int mix(int seed, long salt) {
      long h = seed * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
      h ^= h >>> 31;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 29;
      return (int)(h ^ h >>> 32);
   }

   private static int pick(int seed, long salt, int n) {
      return Math.floorMod(mix(seed, salt), n);
   }

   /** This person carries a phone and uses it now and then. */
   public static boolean owner(int seed) {
      return pick(seed, 0x51L, 100) < OWNERS;
   }

   /** Ticks this person's cycles are shifted by (nobody in step). */
   static int offset(int seed) {
      return pick(seed, 0x0FF5E7L, CYCLE);
   }

   /** The cycle a moment of the game time falls in, for this person. */
   public static long cycle(int seed, long gameTime) {
      return Math.floorDiv(gameTime + offset(seed), CYCLE);
   }

   /** What this person does with its phone in one cycle. */
   public static Plan plan(int seed, long cycle) {
      if (!owner(seed) || pick(seed, cycle * 31L + 7L, 100) >= USE) {
         return Plan.NONE;
      }
      boolean call = pick(seed, cycle * 31L + 11L, 100) < 42;
      int variant = pick(seed, cycle * 31L + 13L, 8);
      Plan p = call
         ? new Plan(Kind.CALL, 0, 28 + pick(seed, cycle * 31L + 17L, 24), 110 + pick(seed, cycle * 31L + 19L, 290), variant)
         : new Plan(Kind.READ, 0, 70 + pick(seed, cycle * 31L + 23L, 200), 0, variant);
      int room = CYCLE - p.length() - 30;
      return new Plan(p.kind(), 10 + pick(seed, cycle * 31L + 29L, Math.max(1, room)), p.look(), p.talk(), variant);
   }

   /** The moment of this person at a game time (with the partial tick for smooth poses). */
   public static Moment at(int seed, long gameTime, float partial) {
      long cycle = cycle(seed, gameTime);
      Plan p = plan(seed, cycle);
      if (p.kind() == Kind.NONE) {
         return Moment.NONE;
      }
      float local = Math.floorMod(gameTime + offset(seed), CYCLE) + partial - p.start();
      if (local < 0.0F || local >= p.length()) {
         return Moment.NONE;
      }
      float elapsed = local;
      Phase[] phases = p.kind() == Kind.READ ? new Phase[]{Phase.DRAW, Phase.LOOK, Phase.STOW}
         : new Phase[]{Phase.DRAW, Phase.DIAL, Phase.TO_EAR, Phase.TALK, Phase.FROM_EAR, Phase.HANG_UP, Phase.STOW};
      for (Phase ph : phases) {
         int len = switch (ph) {
            case DRAW -> DRAW;
            case LOOK, DIAL -> p.look();
            case TO_EAR -> TO_EAR;
            case TALK -> p.talk();
            case FROM_EAR -> FROM_EAR;
            case HANG_UP -> HANG_UP;
            case STOW -> STOW;
            default -> 0;
         };
         if (local < len) {
            return new Moment(ph, local, len, p.kind(), p.variant(), cycle, elapsed);
         }
         local -= len;
      }
      return Moment.NONE;
   }

   /**
    * The moment for a person calm for {@code calmFor} ticks: a phone moment that began before (during a danger, or
    * before this client saw the person) is skipped, so the phone is put away at once on an alarm, never comes back
    * out in the middle of that call when the person calms down and never pops into a hand mid-call.
    */
   public static Moment calm(Moment m, float calmFor) {
      return m.active() && m.elapsed() > calmFor ? Moment.NONE : m;
   }
}

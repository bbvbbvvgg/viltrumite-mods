package dev.baranhan.viltrumitecore.hero;

/**
 * Pure air-jump rules, the SAME on client (prediction, {@code client.hero.AirJumpClient})
 * and server ({@link HeroAirJump}), so both counters reset on the same states.
 */
public final class AirJumpRules {
   private AirJumpRules() {
   }

   /** Any of these states refills the air jumps (and no air jump is taken in them). */
   public static boolean resets(boolean onGround, boolean inWater, boolean inLava, boolean onClimbable, boolean flying, boolean passenger) {
      return onGround || inWater || inLava || onClimbable || flying || passenger;
   }

   /** One more air jump: airborne (no reset state), not anchored, the hero may act and jumps are left. */
   public static boolean allow(int used, int max, boolean resetState, boolean anchored, boolean canAct) {
      return !resetState && !anchored && canAct && used < max;
   }
}

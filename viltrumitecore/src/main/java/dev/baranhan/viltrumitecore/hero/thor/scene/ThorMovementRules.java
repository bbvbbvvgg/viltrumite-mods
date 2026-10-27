package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumiteflight.util.FlightProfile;
import dev.baranhan.viltrumiteflight.util.FlightState;
import javax.annotation.Nullable;

/**
 * Flight, hover and dashes on the shared Viltrumite Flight module (decisions
 * 5, 13, 20-22, 34, 48): with Mjölnir in the hand Thor gets the full original
 * flight; without it only HOVER (a profile with no thrust can never reach
 * CRUISE) and dashes. No flight while a scene holds Thor. Double-WASD dashes
 * on the ground and in HOVER, never in full flight. Pure.
 */
public final class ThorMovementRules {
   /**
    * Hover-only tuning: no throttle gain and no speed lock, so the flight
    * state stays HOVER; a fast throttle loss brings an ongoing cruise down to
    * HOVER when Mjölnir leaves the hand mid-flight.
    */
   public static final FlightProfile HOVER_ONLY = new FlightProfile(1.0F, 0.0F, 0.08F, 0.0F, 0.85F, 12.0F, 12.0F, 0.2F, false, 0.0F);

   private ThorMovementRules() {
   }

   public static boolean fullFlight(@Nullable FlightState state) {
      return state == FlightState.CRUISE || state == FlightState.SONIC;
   }

   /** Decisions 22 / 34: no attack at all in full flight (also no vanilla hit); none of Thor's own while a scene owns the mouse. */
   public static boolean attacksAllowed(boolean fullFlight, boolean sceneLocked) {
      return !fullFlight && !sceneLocked;
   }

   public static boolean allowsFlight(boolean thor, boolean sceneLocked) {
      return thor && !sceneLocked;
   }

   /** Null = the original full flight. */
   @Nullable
   public static FlightProfile profile(boolean hammerInHand) {
      return hammerInHand ? null : HOVER_ONLY;
   }

   public static boolean dashAllowed(boolean sceneLocked, boolean anchored, @Nullable FlightState state, boolean onGround) {
      if (sceneLocked || anchored) {
         return false;
      }

      return state == FlightState.HOVER || (state == null || state == FlightState.NONE) && onGround;
   }

   /** Direction 0 forward (W), 1 left (A), 2 back (S), 3 right (D) to a yaw offset in degrees. */
   public static float dashYawOffset(int direction) {
      return switch (direction) {
         case 1 -> -90.0F;
         case 2 -> 180.0F;
         case 3 -> 90.0F;
         default -> 0.0F;
      };
   }
}

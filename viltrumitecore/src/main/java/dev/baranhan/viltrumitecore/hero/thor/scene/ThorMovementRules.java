package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumiteflight.util.FlightState;
import javax.annotation.Nullable;

/**
 * Flight and dashes on the shared Viltrumite Flight module (decisions 5, 13,
 * 20-22, 34, 48; rework spec 2026-10-28 §5): Thor flies only with Mjölnir
 * really in the hand (ThorHands.holds: attached and the right hand free)
 * and gets the original flight then. Without it there is no flight at all:
 * no CRUISE / SONIC, no HOVER, no dash in the air. When Mjölnir leaves the
 * hand mid-flight (throw, drop, belt, a scene) the flight permission goes
 * false and the shared flight module resets the flight on both sides, so
 * Thor falls like anyone else (vanilla fall damage). No flight while a
 * scene holds Thor. Double-WASD dashes on the ground always; in HOVER only
 * with Mjölnir in the hand; never in full flight. Pure.
 */
public final class ThorMovementRules {
   private ThorMovementRules() {
   }

   public static boolean fullFlight(@Nullable FlightState state) {
      return state == FlightState.CRUISE || state == FlightState.SONIC;
   }

   /** Decisions 22 / 34: no attack at all in full flight (also no vanilla hit); none of Thor's own while a scene owns the mouse. */
   public static boolean attacksAllowed(boolean fullFlight, boolean sceneLocked) {
      return !fullFlight && !sceneLocked;
   }

   /** Spec §5: any mod flight (HOVER included) needs Mjölnir in the hand; none while a scene holds Thor. */
   public static boolean allowsFlight(boolean thor, boolean sceneLocked, boolean hammerInHand) {
      return thor && !sceneLocked && hammerInHand;
   }

   /**
    * Double-WASD dash: on the ground (not flying), or in HOVER with Mjölnir
    * in the hand; never mid-jump / falling, never in full flight.
    */
   public static boolean dashAllowed(boolean sceneLocked, boolean anchored, @Nullable FlightState state, boolean onGround, boolean hammerInHand) {
      if (sceneLocked || anchored) {
         return false;
      }

      return state == FlightState.HOVER && hammerInHand || (state == null || state == FlightState.NONE) && onGround;
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

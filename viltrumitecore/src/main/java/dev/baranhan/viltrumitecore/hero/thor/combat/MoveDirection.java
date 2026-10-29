package dev.baranhan.viltrumitecore.hero.thor.combat;

/** Movement direction relative to the view at the moment of an action (electric combos read it, #50). Pure. */
public enum MoveDirection {
   NONE,
   FORWARD,
   BACK,
   LEFT,
   RIGHT;

   /** Below this horizontal speed (blocks / tick) the player stands still. */
   public static final double STILL = 0.04;

   /**
    * Classify a horizontal velocity against the view yaw (degrees, Minecraft:
    * 0 = +Z, 90 = -X). Sideways wins only when clearly sideways.
    */
   public static MoveDirection classify(double vx, double vz, float yawDegrees) {
      double speed = Math.sqrt(vx * vx + vz * vz);
      if (speed < STILL) {
         return NONE;
      }

      double yaw = Math.toRadians(yawDegrees);
      double forwardX = -Math.sin(yaw);
      double forwardZ = Math.cos(yaw);
      double forward = (vx * forwardX + vz * forwardZ) / speed;
      // Minecraft's right of the view: (-forwardZ, forwardX).
      double right = (vx * -forwardZ + vz * forwardX) / speed;
      if (Math.abs(forward) >= Math.abs(right)) {
         return forward > 0 ? FORWARD : BACK;
      }

      return right > 0 ? RIGHT : LEFT;
   }

   public boolean sideways() {
      return this == LEFT || this == RIGHT;
   }
}

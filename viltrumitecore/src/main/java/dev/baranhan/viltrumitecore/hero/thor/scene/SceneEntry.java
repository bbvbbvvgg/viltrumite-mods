package dev.baranhan.viltrumitecore.hero.thor.scene;

/**
 * The victim's state when a fatality begins; picks the short contextual
 * intro (decision 89 / spec §10: back turned, running, falling, airborne,
 * otherwise placed). Pure.
 */
public enum SceneEntry {
   FALLING,
   AIRBORNE,
   RUNNING,
   BEHIND,
   FACING;

   private static final SceneEntry[] VALUES = values();
   /** PLACEHOLDER thresholds. */
   public static final double FALL_SPEED = 0.25;
   public static final double RUN_SPEED = 0.18;
   public static final float BACK_ANGLE = 110.0F;

   /**
    * @param victimYaw where the victim faces
    * @param yawToThor yaw from the victim to Thor (NaN when on top of each other)
    */
   public static SceneEntry classify(boolean onGround, double heightAboveGround, double vy, double horizontalSpeed, float victimYaw, float yawToThor) {
      if (!onGround && vy < -FALL_SPEED) {
         return FALLING;
      } else if (!onGround && heightAboveGround > 0.6) {
         return AIRBORNE;
      } else if (horizontalSpeed > RUN_SPEED) {
         return RUNNING;
      } else if (!Float.isNaN(yawToThor) && Math.abs(SceneFrame.wrap(yawToThor - victimYaw)) > BACK_ANGLE) {
         return BEHIND;
      }

      return FACING;
   }

   public static SceneEntry byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : FACING;
   }

   /** Does the intro carry the victim's own momentum on (instead of a plain haul)? */
   public boolean keepsMomentum() {
      return this == FALLING || this == RUNNING || this == AIRBORNE;
   }
}

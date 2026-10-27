package dev.baranhan.viltrumitecore.hero.thor.hammer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure flight math of the world hammer (throw, return, catch, transfer). Server and tests. */
public final class MjolnirFlight {
   private MjolnirFlight() {
   }

   /** №159 adaptive return speed: faster the farther away, capped (PLACEHOLDER numbers in MjolnirTuning). */
   public static double returnSpeed(double distance) {
      double speed = MjolnirTuning.RETURN_MIN_SPEED + Math.max(0.0, distance) * MjolnirTuning.RETURN_SPEED_PER_BLOCK;
      return Mth.clamp(speed, MjolnirTuning.RETURN_MIN_SPEED, MjolnirTuning.RETURN_MAX_SPEED);
   }

   /** Simplified far return through unloaded chunks: no physics, a share of the distance per tick (PLACEHOLDER). */
   public static double remoteSpeed(double distance) {
      return Mth.clamp(Math.max(0.0, distance) * MjolnirTuning.REMOTE_SPEED_SHARE, MjolnirTuning.RETURN_MAX_SPEED, MjolnirTuning.REMOTE_MAX_SPEED);
   }

   /** The palm is reached this tick: closer than the catch distance or than one step. */
   public static boolean reaches(double distance, double speed) {
      return distance <= Math.max(MjolnirTuning.CATCH_DISTANCE, speed);
   }

   /** One straight step towards a point at {@code speed} (never overshooting). */
   public static Vec3 step(Vec3 from, Vec3 to, double speed) {
      Vec3 delta = to.subtract(from);
      double length = delta.length();
      return length <= speed || length < 1.0E-6 ? to : from.add(delta.scale(speed / length));
   }

   /** Homing: turn the velocity part of the way to the target direction, keeping the speed. */
   public static Vec3 homing(Vec3 velocity, Vec3 toTarget, double speed) {
      if (toTarget.lengthSqr() < 1.0E-8) {
         return velocity;
      }

      Vec3 want = toTarget.normalize();
      Vec3 have = velocity.lengthSqr() < 1.0E-8 ? want : velocity.normalize();
      Vec3 turned = have.add(want.subtract(have).scale(MjolnirTuning.HOMING_TURN));
      return turned.lengthSqr() < 1.0E-8 ? want.scale(speed) : turned.normalize().scale(speed);
   }

   /** №55: no target, a ballistic arc under gravity. */
   public static Vec3 ballistic(Vec3 velocity) {
      return velocity.add(0.0, -MjolnirTuning.GRAVITY, 0.0);
   }

   /** Free fall (lost support, released from the hand): heavier than a throw, capped. */
   public static Vec3 falling(Vec3 velocity) {
      double vy = Math.max(-MjolnirTuning.FREE_FALL_MAX, velocity.y - MjolnirTuning.FALL_GRAVITY);
      return new Vec3(velocity.x * 0.9, vy, velocity.z * 0.9);
   }

   /**
    * How far the handle has turned to the palm, 0 (spinning) .. 1 (handle
    * first), over the last metres of the return (№157). Client orientation.
    */
   public static float handleTurn(double distance) {
      if (distance >= MjolnirTuning.TURN_DISTANCE) {
         return 0.0F;
      }

      double span = MjolnirTuning.TURN_DISTANCE - MjolnirTuning.CATCH_DISTANCE;
      return (float)Mth.clamp((MjolnirTuning.TURN_DISTANCE - distance) / span, 0.0, 1.0);
   }

   /** №205 appear point in the Thor's dimension: behind him and above, in the air (PLACEHOLDER offsets). */
   public static Vec3 transferPoint(Vec3 thor, float yawDegrees) {
      float yaw = yawDegrees * Mth.DEG_TO_RAD;
      Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
      return thor.add(back.scale(MjolnirTuning.TRANSFER_BACK)).add(0.0, MjolnirTuning.TRANSFER_UP, 0.0);
   }

   /** The right palm of a body with this eye point and yaw (catch point, PLACEHOLDER offsets). */
   public static Vec3 palm(Vec3 eye, float yawDegrees) {
      float yaw = yawDegrees * Mth.DEG_TO_RAD;
      Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
      Vec3 look = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
      return eye.add(right.scale(0.38)).add(look.scale(0.25)).add(0.0, -0.55, 0.0);
   }

   /**
    * A-m12: a charged throw that met a block it may break, spent this tick's
    * break budget but not its total, waits at the block instead of sticking
    * in it (it breaks on next tick). A protected block or a spent total, or
    * a normal throw, sticks.
    */
   public static boolean pausesAtBlock(boolean charged, boolean refused, int brokenTotal, int brokenThisTick) {
      return charged && !refused && brokenTotal < MjolnirTuning.CHARGED_BREAK_TOTAL && brokenThisTick >= MjolnirTuning.CHARGED_BREAK_PER_TICK;
   }
}

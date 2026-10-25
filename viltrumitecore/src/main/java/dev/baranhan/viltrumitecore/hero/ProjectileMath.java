package dev.baranhan.viltrumitecore.hero;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** Pure rules of {@link HeroProjectiles}: vectors and the catch yield (unit tested). */
public final class ProjectileMath {
   /** Deflect: speed factor and the turn range in degrees (spec §4.3). */
   public static final double DEFLECT_SCALE = 0.6;
   public static final double DEFLECT_MIN_DEG = 60.0;
   public static final double DEFLECT_MAX_DEG = 90.0;
   /** Lateral offset (blocks) of the body centre from the flight line above which the side is "away from the body", not random. */
   public static final double SIDE_PICK_OFFSET = 0.1;
   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
   private static final Vec3 FALLBACK_AXIS = new Vec3(1.0, 0.0, 0.0);

   private ProjectileMath() {
   }

   /**
    * Turn axis of a sideways turn: world up made perpendicular to {@code v}
    * (so the turn swings {@code v} to the side, not up or down); x when {@code v}
    * is vertical.
    */
   public static Vec3 sideAxis(Vec3 v) {
      double lengthSqr = v.lengthSqr();
      if (lengthSqr < 1.0E-12) {
         return UP;
      }

      Vec3 axis = UP.subtract(v.scale(UP.dot(v) / lengthSqr));
      return axis.lengthSqr() < 1.0E-8 ? FALLBACK_AXIS : axis.normalize();
   }

   /** Rodrigues rotation of {@code v} by {@code yawDeg} around {@link #sideAxis}({@code v}), then scaled. */
   public static Vec3 rotateAndScale(Vec3 v, double yawDeg, double scale) {
      return rotateAndScale(v, sideAxis(v), yawDeg, scale);
   }

   /** Rodrigues rotation around a unit axis, then scaled: the same call turns a velocity and its power vector alike. */
   public static Vec3 rotateAndScale(Vec3 v, Vec3 axis, double yawDeg, double scale) {
      double rad = Math.toRadians(yawDeg);
      double cos = Math.cos(rad);
      double sin = Math.sin(rad);
      Vec3 rotated = v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1.0 - cos)));
      return rotated.scale(scale);
   }

   /**
    * Signed turn of a deflect: the side away from the body centre when the
    * centre is off the flight line ({@code toBody} = projectile → body centre),
    * else {@code left}; magnitude {@code degrees}.
    */
   public static double deflectYaw(Vec3 v, Vec3 toBody, boolean left, double degrees) {
      Vec3 axis = sideAxis(v);
      Vec3 sideLeft = axis.cross(v);
      if (sideLeft.lengthSqr() > 1.0E-12) {
         double offset = toBody.dot(sideLeft.normalize());
         if (Math.abs(offset) > SIDE_PICK_OFFSET) {
            // A positive yaw swings v toward sideLeft: turn away from the body.
            return offset > 0.0 ? -degrees : degrees;
         }
      }

      return left ? degrees : -degrees;
   }

   /** Deflected velocity at a fixed turn (degrees in [60, 90] by the caller), × {@link #DEFLECT_SCALE}. */
   public static Vec3 deflected(Vec3 v, Vec3 toBody, boolean left, double degrees) {
      return rotateAndScale(v, deflectYaw(v, toBody, left, degrees), DEFLECT_SCALE);
   }

   /** Deflected velocity at the middle of the turn range. */
   public static Vec3 deflected(Vec3 v, Vec3 toBody, boolean left) {
      return deflected(v, toBody, left, (DEFLECT_MIN_DEG + DEFLECT_MAX_DEG) * 0.5);
   }

   /** Turn for a random roll in [0, 1): [60, 90] degrees. */
   public static double deflectDegrees(double roll) {
      return DEFLECT_MIN_DEG + (DEFLECT_MAX_DEG - DEFLECT_MIN_DEG) * Math.max(0.0, Math.min(1.0, roll));
   }

   /** Reflected velocity: same speed toward the owner's eyes from {@code from}; straight back without an owner. */
   public static Vec3 reflected(Vec3 v, Vec3 from, @Nullable Vec3 ownerEye) {
      double speed = v.length();
      if (ownerEye != null) {
         Vec3 to = ownerEye.subtract(from);
         if (to.lengthSqr() > 1.0E-8) {
            return to.normalize().scale(speed);
         }
      }

      return v.scale(-1.0);
   }

   /** A power vector (fireball acceleration) re-aimed along a new velocity, magnitude kept. */
   public static Vec3 alongVelocity(Vec3 power, Vec3 velocity) {
      double length = power.length();
      if (length < 1.0E-12 || velocity.lengthSqr() < 1.0E-12) {
         return power;
      }

      return velocity.normalize().scale(length);
   }

   /** Angle between two vectors in degrees (0 for a zero vector). */
   public static double angleDeg(Vec3 a, Vec3 b) {
      double la = a.length();
      double lb = b.length();
      if (la < 1.0E-12 || lb < 1.0E-12) {
         return 0.0;
      }

      return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b) / (la * lb)))));
   }

   /**
    * What a caught arrow gives: its own pickup item (tipped and spectral keep
    * theirs) unless it is {@code CREATIVE_ONLY} (Infinity, multishot side arrows,
    * creative shooters: no free ammo). Mob arrows are {@code DISALLOWED} and still
    * give the item. Null = nothing.
    */
   @Nullable
   public static <T> T caughtItem(AbstractArrow.Pickup pickup, Supplier<T> pickupItem) {
      return pickup == AbstractArrow.Pickup.CREATIVE_ONLY ? null : pickupItem.get();
   }
}

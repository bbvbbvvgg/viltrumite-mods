package dev.baranhan.viltrumitecore.entity.chitauri;

import net.minecraft.world.phys.Vec3;

/**
 * Pure steering math of the Chitauri chariot: seek with a limited steering force, arrival, speed
 * clamp, bank angle from the turn rate and attack-run approach points from an azimuth. Shared by
 * the entity and the tests.
 */
public final class ChariotSteering {
   public static final double CRUISE = 0.62;
   public static final double ATTACK = 0.78;
   public static final double EVADE = 0.95;
   public static final double MAX_ACCEL = 0.06;
   public static final float MAX_BANK = 50.0F;

   private ChariotSteering() {
   }

   /** Desired velocity toward {@code goal} at {@code speed}, slowing inside {@code arrive} blocks. */
   public static Vec3 seek(Vec3 pos, Vec3 goal, double speed, double arrive) {
      Vec3 d = goal.subtract(pos);
      double len = d.length();
      if (len < 1.0E-4) {
         return Vec3.ZERO;
      }
      double s = arrive > 0.0 && len < arrive ? speed * Math.max(0.15, len / arrive) : speed;
      return d.scale(s / len);
   }

   /** New velocity: steer from {@code vel} toward {@code desired} by at most {@code maxAccel}, then clamp to {@code maxSpeed}. */
   public static Vec3 steer(Vec3 vel, Vec3 desired, double maxAccel, double maxSpeed) {
      Vec3 f = desired.subtract(vel);
      double fl = f.length();
      if (fl > maxAccel) {
         f = f.scale(maxAccel / fl);
      }
      return clamp(vel.add(f), maxSpeed);
   }

   public static Vec3 clamp(Vec3 v, double max) {
      double l = v.length();
      return l > max ? v.scale(max / l) : v;
   }

   /** Bank (roll, degrees, positive = right wing down) for a yaw change per tick (degrees). */
   public static float bank(float yawDelta) {
      return Math.max(-MAX_BANK, Math.min(MAX_BANK, -yawDelta * 7.0F));
   }

   /** Rotates a horizontal direction by {@code deg} around the up axis. */
   public static Vec3 yawed(Vec3 dir, double deg) {
      double a = Math.toRadians(deg);
      double c = Math.cos(a), s = Math.sin(a);
      return new Vec3(dir.x * c - dir.z * s, dir.y, dir.x * s + dir.z * c);
   }

   /** Start of an attack run on {@code target} coming in from {@code azimuthDeg}: {@code dist} out, {@code height} up. */
   public static Vec3 approachPoint(Vec3 target, double azimuthDeg, double dist, double height) {
      double a = Math.toRadians(azimuthDeg);
      return new Vec3(target.x + Math.cos(a) * dist, target.y + height, target.z + Math.sin(a) * dist);
   }

   /** Push away from a neighbour closer than {@code radius} (zero beyond). */
   public static Vec3 separation(Vec3 pos, Vec3 other, double radius, double strength) {
      Vec3 away = pos.subtract(other);
      double d = away.length();
      if (d >= radius || d < 1.0E-4) {
         return Vec3.ZERO;
      }
      return away.scale((radius - d) / radius * strength / d);
   }

   /** Next azimuth of a repeated attack run: a different side each pass (90-180 degrees away). */
   public static double nextAzimuth(double current, double random01) {
      return (current + 90.0 + random01 * 90.0) % 360.0;
   }
}

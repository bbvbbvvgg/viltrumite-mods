package dev.baranhan.viltrumitecore.hero.ironman.legion;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Flight math of a Legion suit (design §6), pure. The server adapter casts the
 * feeler rays and applies the result with {@code move(MoverType.SELF)}; no
 * teleports here. Yaw follows Minecraft: forward = (-sin yaw, 0, cos yaw).
 */
public final class LegionSteering {
   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
   /** Local (right, up, back) offsets of the FOLLOW wedge. */
   private static final double[][] WEDGE = {{-2.6, 1.6, 2.8}, {2.6, 1.6, 2.8}, {0.0, 3.2, 4.6}};
   private static final double RING_RADIUS = 4.5;
   /** One turn of the GUARD ring in 40 s. */
   private static final double RING_SPIN = Math.PI * 2.0 / 800.0;
   private static final double HOLD_RADIUS = 3.5;

   private LegionSteering() {
   }

   public static Vec3 forward(float yawDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
   }

   public static Vec3 right(Vec3 forward) {
      Vec3 right = forward.cross(UP);
      return right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
   }

   /** Formation slot {@code index} of {@code count} around the anchor (Tony or the held point). */
   public static Vec3 slot(LegionOrder order, int index, int count, Vec3 anchor, float yawDegrees, double time) {
      int n = Math.max(1, count);
      int i = Math.floorMod(index, Math.max(n, 1));
      Vec3 fwd = forward(yawDegrees);
      Vec3 right = right(fwd);
      return switch (order) {
         case FOLLOW -> {
            double[] o = WEDGE[Math.min(i, WEDGE.length - 1)];
            yield anchor.add(right.scale(o[0])).add(0.0, o[1], 0.0).subtract(fwd.scale(o[2]));
         }
         case HOLD -> {
            double angle = i * Math.PI * 2.0 / n + Math.PI / 6.0;
            yield anchor.add(Math.cos(angle) * HOLD_RADIUS, 2.5 + (i % 2) * 0.8, Math.sin(angle) * HOLD_RADIUS);
         }
         case RETURN -> anchor.add(0.0, 40.0, 0.0);
         default -> {
            double angle = time * RING_SPIN + i * Math.PI * 2.0 / n;
            yield anchor.add(Math.cos(angle) * RING_RADIUS, 1.6 + (i % 2) * 0.7, Math.sin(angle) * RING_RADIUS);
         }
      };
   }

   /**
    * Where a suit fights from. {@code side} is +1 or -1 (which flank / which
    * way to strafe); {@code time} drives the slow strafe so the suit never
    * hangs still in front of its target.
    */
   public static Vec3 combatSpot(LegionRole role, Vec3 target, Vec3 owner, Vec3 self, double range, double altitude, int side, double time) {
      Vec3 away = flat(owner.subtract(target));
      if (away.lengthSqr() < 1.0E-4) {
         away = flat(self.subtract(target));
      }

      if (away.lengthSqr() < 1.0E-4) {
         away = new Vec3(1.0, 0.0, 0.0);
      }

      away = away.normalize();
      Vec3 across = right(away).scale(side >= 0 ? 1.0 : -1.0);
      double sway = Math.sin(time * 0.07 + side) * 0.35;
      return switch (role) {
         case VANGUARD -> target.add(away.scale(range)).add(across.scale(range * sway)).add(0.0, altitude + 1.0, 0.0);
         case FLANKER -> {
            double angle = Math.PI / 2.0 + 0.45 * Math.sin(time * 0.05);
            Vec3 dir = rotateY(away, side * angle);
            yield target.add(dir.scale(range)).add(0.0, altitude + 1.0, 0.0);
         }
         case OVERWATCH -> target.add(away.scale(range * 0.6)).add(across.scale(range * 0.4 * sway)).add(0.0, altitude + 6.0, 0.0);
         case GUARDIAN -> {
            Vec3 toTarget = flat(target.subtract(owner));
            Vec3 dir = toTarget.lengthSqr() < 1.0E-4 ? across : toTarget.normalize();
            yield owner.add(dir.scale(Math.min(2.5, Math.max(1.5, toTarget.length() * 0.5)))).add(0.0, 0.8, 0.0);
         }
      };
   }

   /** Retreat spot: farther away and higher than the normal combat spot. */
   public static Vec3 retreatSpot(Vec3 target, Vec3 owner, double range, double altitude) {
      Vec3 away = flat(owner.subtract(target));
      Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
      return target.add(dir.scale(range * 1.8)).add(0.0, altitude + 5.0, 0.0);
   }

   /** Control points of an arrival curve: every slot comes from its own side and with its own shape. */
   public record Arrival(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
      public Vec3 at(double t) {
         return bezier(this.p0, this.p1, this.p2, this.p3, t);
      }
   }

   /**
    * Slot 0 dives steeply from the front left, slot 1 makes a wide arc from the
    * right, slot 2 comes in low from behind and pulls up.
    */
   public static Arrival arrival(int index, Vec3 slot, float ownerYaw) {
      Vec3 fwd = forward(ownerYaw);
      Vec3 right = right(fwd);
      double h = LegionRules.ARRIVAL_HEIGHT;
      double s = LegionRules.ARRIVAL_SIDE;
      return switch (Math.floorMod(index, 3)) {
         case 0 -> new Arrival(
            slot.add(fwd.scale(s * 0.8)).subtract(right.scale(s * 0.6)).add(0.0, h, 0.0),
            slot.add(fwd.scale(s * 0.5)).subtract(right.scale(s * 0.3)).add(0.0, h * 0.45, 0.0),
            slot.add(fwd.scale(6.0)).add(0.0, 8.0, 0.0),
            slot);
         case 1 -> new Arrival(
            slot.add(right.scale(s)).add(fwd.scale(s * 0.2)).add(0.0, h * 0.85, 0.0),
            slot.add(right.scale(s * 1.1)).subtract(fwd.scale(s * 0.6)).add(0.0, h * 0.35, 0.0),
            slot.add(right.scale(4.0)).subtract(fwd.scale(8.0)).add(0.0, 5.0, 0.0),
            slot);
         default -> new Arrival(
            slot.subtract(fwd.scale(s)).add(right.scale(s * 0.25)).add(0.0, h * 0.7, 0.0),
            slot.subtract(fwd.scale(s * 0.7)).add(0.0, 2.0, 0.0),
            slot.subtract(fwd.scale(7.0)).add(0.0, -1.0, 0.0),
            slot);
      };
   }

   public static Vec3 bezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
      double u = Math.max(0.0, Math.min(1.0, t));
      double v = 1.0 - u;
      return p0.scale(v * v * v).add(p1.scale(3.0 * v * v * u)).add(p2.scale(3.0 * v * u * u)).add(p3.scale(u * u * u));
   }

   /** Arrival progress: fast through the sky, a long soft brake at the end (no stop-and-go). */
   public static double arrivalEase(double t) {
      double u = Math.max(0.0, Math.min(1.0, t));
      return 1.0 - Math.pow(1.0 - u, 2.2);
   }

   /** Arrive steering: full speed far away, a smooth brake inside {@code slowRadius}, acceleration limited. */
   public static Vec3 arrive(Vec3 pos, Vec3 vel, Vec3 goal, double maxSpeed, double accel, double slowRadius) {
      Vec3 to = goal.subtract(pos);
      double dist = to.length();
      Vec3 desired = dist < 1.0E-4 ? Vec3.ZERO : to.scale(Math.min(maxSpeed, maxSpeed * dist / Math.max(0.5, slowRadius)) / dist);
      return vel.add(limit(desired.subtract(vel), accel));
   }

   public static Vec3 limit(Vec3 v, double max) {
      double length = v.length();
      return length > max && length > 1.0E-9 ? v.scale(max / length) : v;
   }

   /** Push away from neighbours closer than {@code radius} (no suits stacked in one spot). */
   public static Vec3 separation(Vec3 pos, List<Vec3> others, double radius) {
      Vec3 push = Vec3.ZERO;
      for (Vec3 other : others) {
         Vec3 away = pos.subtract(other);
         double d = away.length();
         if (d < radius && d > 1.0E-4) {
            push = push.add(away.scale((radius - d) / (radius * d)));
         } else if (d <= 1.0E-4) {
            push = push.add(0.3, 0.1, 0.0);
         }
      }

      return push;
   }

   /**
    * Obstacle answer from feeler clearances (blocks to the first solid block,
    * {@code lookahead} = free). A blocked front lifts the suit and turns it
    * to the freer side; close side walls push it off. Result is a steering
    * direction, roughly unit length at most.
    */
   public static Vec3 avoid(Vec3 forward, double front, double left, double right, double up, double lookahead) {
      Vec3 fwd = flat(forward);
      if (fwd.lengthSqr() < 1.0E-6 || lookahead <= 0.0) {
         return Vec3.ZERO;
      }

      fwd = fwd.normalize();
      Vec3 side = right(fwd);
      Vec3 out = Vec3.ZERO;
      double block = 1.0 - Math.max(0.0, Math.min(1.0, front / lookahead));
      if (block > 0.0) {
         // Over a roof when there is room above, else around the wall.
         double climb = up >= 2.5 ? 1.0 : 0.25;
         double turn = right > left ? 1.0 : -1.0;
         out = out.add(0.0, block * climb, 0.0).add(side.scale(turn * block * (1.2 - climb * 0.5))).subtract(fwd.scale(block * 0.6));
      }

      double l = 1.0 - Math.max(0.0, Math.min(1.0, left / (lookahead * 0.5)));
      double r = 1.0 - Math.max(0.0, Math.min(1.0, right / (lookahead * 0.5)));
      out = out.add(side.scale((l - r) * 0.6));
      return limit(out, 1.2);
   }

   /** Closest distance of a projectile to {@code pos} within {@code horizon} ticks (straight line). */
   public static double closestApproach(Vec3 pos, Vec3 projectile, Vec3 velocity, int horizon) {
      Vec3 rel = pos.subtract(projectile);
      double vv = velocity.lengthSqr();
      if (vv < 1.0E-6) {
         return rel.length();
      }

      double t = Math.max(0.0, Math.min(horizon, rel.dot(velocity) / vv));
      return rel.subtract(velocity.scale(t)).length();
   }

   /** Side-step direction across the projectile's path. */
   public static Vec3 dodge(Vec3 velocity, boolean left) {
      Vec3 across = velocity.cross(UP);
      if (across.lengthSqr() < 1.0E-6) {
         across = new Vec3(1.0, 0.0, 0.0);
      }

      return across.normalize().scale(left ? -1.0 : 1.0).add(0.0, 0.25, 0.0).normalize();
   }

   /** Yaw that turns toward {@code desired} by at most {@code maxStep} degrees. */
   public static float turn(float current, float desired, float maxStep) {
      float delta = wrap(desired - current);
      return current + Math.max(-maxStep, Math.min(maxStep, delta));
   }

   /** Yaw (degrees) that faces along {@code dir}. */
   public static float yawOf(Vec3 dir, float fallback) {
      return dir.x * dir.x + dir.z * dir.z < 1.0E-6 ? fallback : (float)Math.toDegrees(Math.atan2(-dir.x, dir.z));
   }

   /** Pitch (degrees, down positive as in Minecraft) that looks along {@code dir}. */
   public static float pitchOf(Vec3 dir) {
      double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
      return (float)Math.toDegrees(-Math.atan2(dir.y, Math.max(1.0E-6, flat)));
   }

   public static float wrap(float degrees) {
      float d = degrees % 360.0F;
      if (d >= 180.0F) {
         d -= 360.0F;
      }

      if (d < -180.0F) {
         d += 360.0F;
      }

      return d;
   }

   /** Body lean 0..1 from the horizontal speed: 0 = upright hover, 1 = flat cruise. */
   public static float lean(Vec3 velocity, double cruise) {
      double h = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
      return (float)Math.max(0.0, Math.min(1.0, (h - 0.12) / Math.max(0.1, cruise * 0.8)));
   }

   private static Vec3 flat(Vec3 v) {
      return new Vec3(v.x, 0.0, v.z);
   }

   private static Vec3 rotateY(Vec3 v, double angle) {
      double c = Math.cos(angle);
      double s = Math.sin(angle);
      return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
   }
}

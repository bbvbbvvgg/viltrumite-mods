package dev.baranhan.viltrumitecore.hero.thor.scene;

import net.minecraft.world.phys.Vec3;

/**
 * A scene's local frame: forward along a yaw, up, side to the actor's right
 * (the same right as the shared grab anchors). Pure.
 */
public final class SceneFrame {
   private SceneFrame() {
   }

   /** An offset in a frame: blocks forward, up and to the right. */
   public record Offset(double forward, double up, double side) {
      public static final Offset ZERO = new Offset(0, 0, 0);

      public Offset lerp(Offset to, double t) {
         return new Offset(this.forward + (to.forward - this.forward) * t, this.up + (to.up - this.up) * t, this.side + (to.side - this.side) * t);
      }

      public Offset plus(double forward, double up, double side) {
         return new Offset(this.forward + forward, this.up + up, this.side + side);
      }
   }

   public static Vec3 forward(float yaw) {
      double rad = Math.toRadians(yaw);
      return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
   }

   public static Vec3 right(float yaw) {
      Vec3 f = forward(yaw);
      return new Vec3(-f.z, 0, f.x);
   }

   public static Vec3 world(Vec3 origin, float yaw, Offset offset) {
      Vec3 f = forward(yaw);
      Vec3 r = right(yaw);
      return origin.add(f.scale(offset.forward())).add(r.scale(offset.side())).add(0, offset.up(), 0);
   }

   /** Minecraft yaw from {@code from} looking at {@code to} (horizontal). */
   public static float yawTo(Vec3 from, Vec3 to) {
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      if (dx * dx + dz * dz < 1.0E-8) {
         return Float.NaN;
      }

      return (float)Math.toDegrees(Math.atan2(-dx, dz));
   }

   /** Shortest signed angle from a to b in degrees, (-180, 180]. */
   public static float wrap(float degrees) {
      float d = degrees % 360.0F;
      if (d > 180.0F) {
         d -= 360.0F;
      } else if (d <= -180.0F) {
         d += 360.0F;
      }
      return d;
   }

   /** Smoothstep on [0, 1]; its steepest slope is 1.5. */
   public static double smooth(double t) {
      double x = Math.max(0.0, Math.min(1.0, t));
      return x * x * (3.0 - 2.0 * x);
   }

   public static double easeOut(double t) {
      double x = Math.max(0.0, Math.min(1.0, t));
      return 1.0 - (1.0 - x) * (1.0 - x);
   }

   public static double easeIn(double t) {
      double x = Math.max(0.0, Math.min(1.0, t));
      return x * x;
   }

   /** Share of the way through [from, to] at t, clamped. */
   public static double span(double t, double from, double to) {
      return to <= from ? (t >= to ? 1.0 : 0.0) : Math.max(0.0, Math.min(1.0, (t - from) / (to - from)));
   }
}

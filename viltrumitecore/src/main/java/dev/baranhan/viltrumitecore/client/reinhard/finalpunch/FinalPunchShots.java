package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import net.minecraft.world.phys.Vec3;

/**
 * Pure camera direction of THE FINAL PUNCH for the caster's view: nine shots in Reinhard's
 * frame (feet, scene yaw), the burning fist and the target's jaw. Shots blend into each other
 * except the two hard cuts ({@link FinalPunchCues#CUTS}: the silence and the contact). The
 * scene camera clips the result against walls and blends from / back to the player's view.
 */
public final class FinalPunchShots {
   /** Where things are this frame (world). {@code target} may be null (lost or far). */
   public record Frame(Vec3 feet, float yaw, Vec3 fist, Vec3 jaw, Vec3 target) {
   }

   /** Camera position and the point it looks at. */
   public record Shot(Vec3 pos, Vec3 focus) {
      public Shot lerp(Shot other, double w) {
         return new Shot(this.pos.lerp(other.pos, w), this.focus.lerp(other.focus, w));
      }
   }

   private interface Segment {
      Shot at(double t, Frame f);
   }

   private static final int[] START = {0, 58, 150, 245, 330, FinalPunchRules.SILENCE, FinalPunchRules.STRIKE, FinalPunchRules.CONTACT, FinalPunchRules.LAUNCH};
   /** Blend into each segment (0 = hard cut). */
   private static final int[] BLEND = {0, 22, 24, 20, 18, 0, 6, 0, 4};
   private static final Segment[] SEGMENTS = {
      // 0 – the sentence: both in profile from his left, slow dolly in.
      (t, f) -> {
         double p = FinalPunchCues.ramp(t, 0, 58);
         return new Shot(local(f, 0.55 + 0.2 * p, 1.55, -2.9 + 0.35 * p), local(f, 0.6, 1.45, 0.05));
      },
      // 1 – the windup begins: three-quarter on Reinhard over the target's shoulder.
      (t, f) -> {
         double p = FinalPunchCues.ramp(t, 58, 150);
         return new Shot(local(f, 2.7 - 0.2 * p, 1.75 - 0.1 * p, -1.25 - 0.55 * p), local(f, 0.0, 1.35, 0.15));
      },
      // 2 – the leg lifts, the fist catches fire: low angle from his right, behind.
      (t, f) -> {
         double p = FinalPunchCues.ramp(t, 150, 245);
         return new Shot(local(f, -1.6 + 0.3 * p, 0.55 + 0.15 * p, 2.7 - 0.4 * p), local(f, 0.35, 1.25, 0.2));
      },
      // 3 – rings and arcs: a slow orbit around the burning fist.
      (t, f) -> {
         double p = FinalPunchCues.smooth(FinalPunchCues.ramp(t, 245, 330));
         double a = Math.toRadians(70 + 90 * p);
         Vec3 off = forward(f).scale(Math.cos(a) * 1.7).add(right(f).scale(Math.sin(a) * 1.7)).add(0, 0.35, 0);
         return new Shot(f.fist().add(off), f.fist().lerp(local(f, 0.5, 1.4, 0.0), 0.25));
      },
      // 4 – reality loses its mind: high and wide, orbiting behind his right shoulder.
      (t, f) -> {
         double p = FinalPunchCues.smooth(FinalPunchCues.ramp(t, 330, FinalPunchRules.SILENCE));
         double a = Math.toRadians(120 + 45 * p);
         double r = 6.5 - 1.0 * p;
         return new Shot(f.feet().add(forward(f).scale(Math.cos(a) * r)).add(right(f).scale(Math.sin(a) * r)).add(0, 3.6 - 0.6 * p, 0),
            local(f, 0.4, 1.2, 0.0));
      },
      // 5 – absolute silence: extremely close to the fist, a slow push in.
      (t, f) -> {
         double p = FinalPunchCues.ramp(t, FinalPunchRules.SILENCE, FinalPunchRules.STRIKE);
         double d = 0.78 - 0.16 * p;
         Vec3 off = right(f).scale(0.75 * d).add(forward(f).scale(0.6 * d)).add(0, 0.14, 0);
         return new Shot(f.fist().add(off), f.fist());
      },
      // 6 – the strike: a side tracking shot riding with the fist towards the jaw.
      (t, f) -> {
         Vec3 mid = f.fist().lerp(f.jaw(), 0.4);
         return new Shot(mid.add(right(f).scale(1.6)).add(0, 0.15, 0).add(forward(f).scale(-0.25)), f.fist().lerp(f.jaw(), 0.55));
      },
      // 7 – contact (hit-stop): a close profile on the fist in the jaw.
      (t, f) -> new Shot(f.jaw().add(right(f).scale(1.25)).add(forward(f).scale(0.35)).add(0, 0.05, 0), f.jaw()),
      // 8 – the launch: pull back wide and follow the body.
      (t, f) -> {
         double p = FinalPunchCues.smooth(FinalPunchCues.ramp(t, FinalPunchRules.LAUNCH, FinalPunchRules.CAMERA_OUT));
         Vec3 follow = f.target() == null ? f.jaw() : f.jaw().lerp(f.target(), Math.min(1.0, 0.75 * p));
         return new Shot(local(f, -1.2 - 0.8 * p, 2.0 + 0.4 * p, 3.6 + 0.6 * p), f.jaw().lerp(follow, p));
      }
   };

   private FinalPunchShots() {
   }

   public static int segment(double t) {
      int i = 0;
      while (i + 1 < START.length && t >= START[i + 1]) i++;
      return i;
   }

   /** The scene shot at tick {@code t} (blends and cuts included; the vanilla blends are the camera's). */
   public static Shot at(double t, Frame f) {
      int i = segment(t);
      Shot shot = SEGMENTS[i].at(t, f);
      if (i > 0 && BLEND[i] > 0 && t < START[i] + BLEND[i]) {
         Shot before = SEGMENTS[i - 1].at(t, f);
         shot = orbit(before, shot, FinalPunchCues.smooth((t - START[i]) / BLEND[i]), f.feet());
      }
      // A blend between two sides of him travels round the bodies, never through them.
      Vec3 pos = clear(shot.pos(), f.feet());
      if (f.target() != null) pos = clear(pos, f.target());
      return pos == shot.pos() ? shot : new Shot(pos, shot.focus());
   }

   /** Blend round {@code centre}: angle on the shortest arc, radius and height linear (never through him). */
   static Shot orbit(Shot a, Shot b, double w, Vec3 centre) {
      double ax = a.pos().x - centre.x, az = a.pos().z - centre.z;
      double bx = b.pos().x - centre.x, bz = b.pos().z - centre.z;
      double ra = Math.sqrt(ax * ax + az * az), rb = Math.sqrt(bx * bx + bz * bz);
      if (ra < 1.0E-3 || rb < 1.0E-3) return a.lerp(b, w);
      double aa = Math.atan2(az, ax), ab = Math.atan2(bz, bx);
      double d = ab - aa;
      while (d > Math.PI) d -= 2 * Math.PI;
      while (d < -Math.PI) d += 2 * Math.PI;
      double angle = aa + d * w;
      double r = ra + (rb - ra) * w;
      double y = a.pos().y + (b.pos().y - a.pos().y) * w;
      return new Shot(new Vec3(centre.x + Math.cos(angle) * r, y, centre.z + Math.sin(angle) * r), a.focus().lerp(b.focus(), w));
   }

   /** Clearance of a standing body (feet, 2.1 tall): the lens is pushed out to {@link #CLEARANCE}. */
   static final double CLEARANCE = 0.85;

   static Vec3 clear(Vec3 pos, Vec3 feet) {
      if (pos.y < feet.y - 0.2 || pos.y > feet.y + 2.3) return pos;
      double dx = pos.x - feet.x;
      double dz = pos.z - feet.z;
      double d = Math.sqrt(dx * dx + dz * dz);
      if (d >= CLEARANCE) return pos;
      if (d < 1.0E-4) {
         dx = 1;
         dz = 0;
         d = 1;
      }
      double k = CLEARANCE / d;
      return new Vec3(feet.x + dx * k, pos.y, feet.z + dz * k);
   }

   public static Vec3 forward(Frame f) {
      return Vec3.directionFromRotation(0, f.yaw());
   }

   /** Reinhard's right (the anchor's convention). */
   public static Vec3 right(Frame f) {
      Vec3 fw = forward(f);
      return new Vec3(-fw.z, 0, fw.x);
   }

   public static Vec3 local(Frame f, double fwd, double up, double rightOffset) {
      return f.feet().add(forward(f).scale(fwd)).add(0, up, 0).add(right(f).scale(rightOffset));
   }

   /** Yaw (degrees, Minecraft convention) looking from {@code from} to {@code to}. */
   public static float yaw(Vec3 from, Vec3 to) {
      Vec3 d = to.subtract(from);
      return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
   }

   public static float pitch(Vec3 from, Vec3 to) {
      Vec3 d = to.subtract(from);
      return (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
   }
}

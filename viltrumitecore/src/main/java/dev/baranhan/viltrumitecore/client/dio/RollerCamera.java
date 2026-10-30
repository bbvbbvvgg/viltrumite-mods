package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import net.minecraft.world.phys.Vec3;

/**
 * Pure Road Roller cinematic camera for the caster (unit tested). Six shots on
 * the ult clock, each aimed at its subject, cross-faded in position and in
 * shortest-arc yaw/pitch (no degenerate look vectors, no flips), entered from
 * and returned to the player's own camera: no cut, no teleport.
 *
 * <ol>
 *   <li>CAST (0-26): low three-quarter front of DIO; the crouch reads.</li>
 *   <li>LAUNCH (22-60): low beside DIO, chasing him up a little below him (looking up past him at the sky).</li>
 *   <li>RIDE (54-98): above and behind DIO, looking down past him at the target
 *       while the roller appears, hangs and falls.</li>
 *   <li>ORBIT (92-150): slow orbit around the roller through the slam and MUDA.</li>
 *   <li>WRYYY (144-156): close low front of DIO's face.</li>
 *   <li>BACK (155-162): over his shoulder for the resume blast, then into his own eyes.</li>
 * </ol>
 */
public final class RollerCamera {
   /** Fade-in from the player's view and fade-out back to it (ticks). */
   public static final double ENTER = 10.0;
   public static final double EXIT_START = DioRules.ROLLER_RESUME + 2.0;
   public static final double EXIT_END = DioRules.ROLLER_LAND + 2.0;

   public record View(Vec3 pos, float yaw, float pitch, float bars) {
   }

   private RollerCamera() {
   }

   static double smooth(double u) {
      u = Math.max(0.0, Math.min(1.0, u));
      return u * u * (3.0 - 2.0 * u);
   }

   /** Horizontal unit facing (start to landing point, or the fallback yaw). */
   public static Vec3 facing(Vec3 start, Vec3 land, float fallbackYawDeg) {
      Vec3 d = new Vec3(land.x - start.x, 0.0, land.z - start.z);
      if (d.lengthSqr() < 0.25) {
         double r = Math.toRadians(fallbackYawDeg);
         return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
      }

      return d.normalize();
   }

   /** Scene weight 0..1 (0 = the player's own camera). */
   public static double weight(double t) {
      if (t < 0.0 || t >= DioRules.ROLLER_LENGTH) {
         return 0.0;
      }

      return smooth(t / ENTER) * (1.0 - smooth((t - EXIT_START) / (EXIT_END - EXIT_START)));
   }

   /** Letterbox amount 0..1, a little behind the scene weight. */
   public static float bars(double t) {
      return (float)(smooth((t - 2.0) / 10.0) * (1.0 - smooth((t - EXIT_START) / (EXIT_END - 4.0 - EXIT_START))));
   }

   /** The scene camera alone (before the blend with the player's own view). */
   public static View scene(double t, Vec3 dio, Vec3 start, Vec3 land, Vec3 f) {
      Vec3 up = new Vec3(0, 1, 0);
      Vec3 s = new Vec3(-f.z, 0.0, f.x);
      View cast = aim(start.add(f.scale(4.2)).add(s.scale(1.9)).add(0, 0.9, 0), dio.add(0, 1.25, 0));
      // Chases him up: stays 2.6 blocks under and 3.8 beside him, so DIO fills the frame against the sky
      // instead of shrinking to a dot over a fixed ground camera (seen in the first in-game pass).
      Vec3 beside = new Vec3(dio.x, start.y, dio.z).add(f.scale(-1.6)).add(s.scale(3.4));
      View launch = aim(new Vec3(beside.x, Math.max(start.y + 0.45, dio.y - 2.6), beside.z), dio.add(0, 1.0, 0));
      Vec3 rideLook = dio.add(land.subtract(dio).scale(0.65)).add(0, 0.6, 0);
      View ride = aim(dio.add(f.scale(-5.2)).add(s.scale(1.4)).add(up.scale(3.2)), rideLook);
      double a = Math.toRadians(-35.0 + 80.0 * smooth((t - DioRules.ROLLER_SLAM) / (double)(DioRules.ROLLER_BARRAGE_END - DioRules.ROLLER_SLAM)));
      Vec3 around = s.scale(Math.cos(a)).add(f.scale(-Math.sin(a)));
      double zoom = 7.6 - 1.2 * Math.exp(-Math.max(0.0, t - DioRules.ROLLER_SLAM) / 6.0);
      View orbit = aim(land.add(around.scale(zoom)).add(0, 3.3, 0), land.add(0, 1.9, 0));
      View wry = aim(dio.add(f.scale(2.9)).add(s.scale(0.9)).add(0, 1.05, 0), dio.add(0, 1.85, 0));
      // Over DIO's shoulder for the resume blast, already facing where his own eyes will look.
      View back = aim(dio.add(f.scale(-3.4)).add(s.scale(1.1)).add(0, 2.3, 0), dio.add(f.scale(6.0)).add(0, 1.0, 0));
      View out = cast;
      out = mix(out, launch, smooth((t - 20.0) / 8.0));
      out = mix(out, ride, smooth((t - 52.0) / 10.0));
      out = mix(out, orbit, smooth((t - (DioRules.ROLLER_SLAM - 4.0)) / 6.0));
      out = mix(out, wry, smooth((t - (DioRules.ROLLER_BARRAGE_END - 4.0)) / 8.0));
      out = mix(out, back, smooth((t - (DioRules.ROLLER_RESUME - 5.0)) / 7.0));
      return out;
   }

   /** Final camera: the scene blended with the player's own camera position/angles. */
   public static View view(double t, Vec3 dio, Vec3 start, Vec3 land, Vec3 f, Vec3 ownPos, float ownYaw, float ownPitch) {
      double w = weight(t);
      View own = new View(ownPos, ownYaw, ownPitch, 0.0F);
      if (w <= 0.0) {
         return new View(ownPos, ownYaw, ownPitch, bars(t));
      }

      View v = mix(own, scene(t, dio, start, land, f), w);
      return new View(v.pos(), v.yaw(), v.pitch(), bars(t));
   }

   /** A camera at {@code pos} looking at {@code look}. */
   static View aim(Vec3 pos, Vec3 look) {
      Vec3 d = look.subtract(pos);
      float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
      float pitch = (float)Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
      return new View(pos, yaw, Math.max(-88.0F, Math.min(88.0F, pitch)), 0.0F);
   }

   /** Position lerp + shortest-arc yaw and pitch lerp: never degenerate, never flips. */
   static View mix(View a, View b, double u) {
      if (u <= 0.0) {
         return a;
      }

      if (u >= 1.0) {
         return b;
      }

      float dy = ((b.yaw() - a.yaw()) % 360.0F + 540.0F) % 360.0F - 180.0F;
      return new View(a.pos().add(b.pos().subtract(a.pos()).scale(u)), (float)(a.yaw() + dy * u), (float)(a.pitch() + (b.pitch() - a.pitch()) * u), 0.0F);
   }

   /** Minecraft look vector for yaw/pitch in degrees. */
   public static Vec3 direction(float yawDeg, float pitchDeg) {
      double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
      return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
   }
}

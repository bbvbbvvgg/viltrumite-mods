package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.hero.captain.CaptainMove;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import net.minecraft.world.phys.Vec3;

/**
 * Pure camera shots of «Avengers, assemble!» (no Minecraft client state): the call on Steve's face, a wide
 * high crane over the landing site while the team arrives, and the low 360° orbit around the circle from the
 * 2012 film, easing back into Steve's eyes before the fight. Each shot is {position, look-at}.
 */
public final class CaptainSceneShots {
   /** Orbit radius around the circle centre (outside the ring of heroes). */
   public static final double ORBIT_RADIUS = CaptainRules.CIRCLE_RADIUS + 3.4;
   /** Wide crane radius while the team arrives. */
   public static final double WIDE_RADIUS = 11.0;
   /** The full turn ends here; the rest of the circle eases back into the eyes. */
   public static final int ORBIT_TICKS = CaptainRules.CIRCLE_TICKS - 14;
   public static final int RETURN = CaptainRules.CIRCLE_TICKS - ORBIT_TICKS;
   /** Blend from the crane into the orbit. */
   public static final int BLEND = 16;
   /** Wide shot start angle (degrees, 0 = where Steve faces, out of the circle) and drift per tick. */
   static final double WIDE_START = 25.0;
   static final double WIDE_DRIFT = -0.3;

   private CaptainSceneShots() {
   }

   /** Unit vector at {@code deg} around the centre in Steve's frame ({@code f} ahead, {@code r} right). */
   static Vec3 around(Vec3 f, double deg) {
      double a = Math.toRadians(deg);
      Vec3 r = new Vec3(-f.z, 0.0, f.x);
      return f.scale(Math.cos(a)).add(r.scale(Math.sin(a)));
   }

   /** Crane angle reached after {@code age} ticks of the arrival shot. */
   public static double wideAngle(double age) {
      return WIDE_START + WIDE_DRIFT * Math.max(0.0, age);
   }

   /** Orbit angle {@code age} ticks into the circle, starting where the crane stopped. */
   public static double orbitAngle(double startDeg, double age) {
      return startDeg + 360.0 * smooth(age / ORBIT_TICKS);
   }

   /**
    * Raw shot of {@code move} at {@code age} ticks. {@code steve} is his feet, {@code centre} the circle centre,
    * {@code f} his flat facing (out of the circle), {@code wideEnd} the crane angle when the circle began.
    */
   public static Vec3[] shot(CaptainMove move, double age, Vec3 steve, Vec3 centre, Vec3 f, double wideEnd) {
      Vec3 r = new Vec3(-f.z, 0.0, f.x);
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      if (move == CaptainMove.ASSEMBLE_CALL) {
         // low frontal on his face, a slow push-in while he calls them
         double u = Math.min(1.0, age / CaptainRules.CALL_TICKS);
         return new Vec3[]{steve.add(f.scale(4.2 - 0.8 * u)).add(r.scale(1.1)).add(up.scale(1.35 + 0.1 * u)), steve.add(up.scale(1.45))};
      }
      if (move == CaptainMove.ASSEMBLE_WAIT) {
         // wide and high in front of him, over the landing site behind him: the sky, the jet, the drops
         double lift = 4.6 - Math.min(1.2, age * 0.012);
         return new Vec3[]{centre.add(around(f, wideAngle(age)).scale(WIDE_RADIUS)).add(up.scale(lift)), centre.add(up.scale(2.2))};
      }
      // the circle: low, outside the ring, a full turn round the team
      double u = Math.min(1.0, age / ORBIT_TICKS);
      double h = 0.9 + 0.5 * Math.sin(Math.PI * u);
      return new Vec3[]{centre.add(around(f, orbitAngle(wideEnd, age)).scale(ORBIT_RADIUS)).add(up.scale(h)), centre.add(up.scale(1.35))};
   }

   /** Weight of Steve's own eyes at the end of the circle (0 = film, 1 = his view). */
   public static double returnWeight(double age) {
      return smooth((age - ORBIT_TICKS) / RETURN);
   }

   static double smooth(double x) {
      double k = Math.max(0.0, Math.min(1.0, x));
      return k * k * (3.0 - 2.0 * k);
   }
}

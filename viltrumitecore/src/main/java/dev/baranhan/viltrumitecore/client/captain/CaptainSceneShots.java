package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.hero.captain.CaptainMove;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import net.minecraft.world.phys.Vec3;

/**
 * Pure camera shots of «Avengers, assemble!» (no Minecraft client state): the call on Steve's face, a wide
 * high crane over the landing site while the team arrives (cut in for close-ups of Natasha and Clint dropping
 * from the Quinjet's ramp), and the low 360° orbit around the circle from the
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

   /** Close-ups of the ramp drop (scene ticks, the call included): Natasha from her jump, then Clint. */
   public static final int CLOSE_TICKS = 24;
   public static final int WIDOW_CLOSE = CaptainRules.QUINJET_JUMP;
   public static final int CLINT_CLOSE = WIDOW_CLOSE + CLOSE_TICKS;
   /** Cross-fade between the crane and a close-up. */
   public static final int CLOSE_BLEND = 8;
   /** Close-up lens distance from the face and its swing off the line to the circle (degrees). */
   static final double CLOSE_DIST = 3.0;
   static final double CLOSE_SWING = 38.0;
   static final double CLINT_SWING = 50.0;
   /** Lens height over the feet once down, and how far under the face it rides the fall. */
   static final double CLOSE_FACE = 1.15;
   static final double CLOSE_BELOW = 1.4;

   private CaptainSceneShots() {
   }

   /** Who the arrival camera is on at scene tick {@code sceneT}: 1 = Natasha, 2 = Clint, 0 = the wide crane. */
   public static int closeUp(int sceneT) {
      if (sceneT >= WIDOW_CLOSE && sceneT < CLINT_CLOSE) {
         return 1;
      }
      return sceneT >= CLINT_CLOSE && sceneT < CLINT_CLOSE + CLOSE_TICKS ? 2 : 0;
   }

   /**
    * Close-up on a dropping Avenger: a short lens in front of the subject ({@code facing}, locked when the shot starts),
    * swung to one side (Natasha 38 degrees to her left, Clint 50 degrees to his right, away from the bow arm he
    * extends along his gaze, so it never covers his face), riding the fall a little below the face
    * so the drop fills the frame, and stopping at face height over the ground ({@code ground}) so the landing reads
    * as an impact. {@code u} 0..1 through the shot adds a slow push-in.
    */
   public static Vec3[] closeShot(Vec3 subject, Vec3 facing, double ground, int who, double u) {
      Vec3 front = new Vec3(facing.x, 0.0, facing.z);
      front = front.lengthSqr() > 1.0E-4 ? front.normalize() : new Vec3(0.0, 0.0, 1.0);
      Vec3 side = around(front, who == 1 ? -CLOSE_SWING : CLINT_SWING);
      double dist = CLOSE_DIST - 0.5 * Math.min(1.0, Math.max(0.0, u));
      double y = Math.max(ground + CLOSE_FACE, subject.y + CLOSE_FACE - CLOSE_BELOW);
      Vec3 lens = new Vec3(subject.x, y, subject.z).add(side.scale(dist));
      return new Vec3[]{lens, subject.add(0.0, 1.35, 0.0)};
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

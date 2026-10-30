package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.dio.DioRules;

/**
 * Pure Road Roller staging over the 180-tick ult (unit tested). The roller
 * materialises under DIO near the top of his leap just after the appear sound,
 * hangs with him, rides the fall down nose first, squashes on the slam tick,
 * takes a dent on every server pulse, shakes through WRYYY and is gone on the
 * resume tick, where the server explosion fires.
 */
public final class RollerChoreo {
   /** First visible tick (the server appear sound plays at ROLLER_APPEAR). */
   public static final int APPEAR = DioRules.ROLLER_APPEAR + 2;
   public static final int MATERIALIZE = 6;
   /** Model scale (body top 39 units ≈ ROLLER_SEAT blocks). */
   public static final float SCALE = 0.95F;
   /** DIO stands this far in front of the roller origin along its nose (model -Z), blocks. */
   public static final double DIO_FORWARD = 16.5 * SCALE / 16.0;
   public static final int TREMBLE_START = DioRules.ROLLER_BARRAGE_END;

   public record Pose(boolean visible, float alpha, float grow, boolean landed, float pitchDeg, float squash, float sink, float dent, float tremble,
      float drumSpinDeg) {
      public static final Pose HIDDEN = new Pose(false, 0, 0, false, 0, 1, 0, 0, 0, 0);
   }

   private RollerChoreo() {
   }

   /** DIO's feet at ult tick t on the arc from the start point to the landing point (DioRules curves). */
   public static net.minecraft.world.phys.Vec3 dioFeet(double t, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 land) {
      double u = DioRules.rollerTravel(t);
      return new net.minecraft.world.phys.Vec3(from.x + (land.x - from.x) * u, DioRules.rollerHeight(t, from.y, land.y), from.z + (land.z - from.z) * u);
   }

   public static boolean visible(double t) {
      return t >= APPEAR && t < DioRules.ROLLER_RESUME;
   }

   /** Server pulses landed up to t (inclusive), 0..rollerPulseCount. */
   public static int pulsesUpTo(double t) {
      int n = 0;
      for (int i = DioRules.ROLLER_SLAM + 1; i <= Math.min(t, DioRules.ROLLER_BARRAGE_END); i++) {
         if (DioRules.rollerPulseTick(i)) {
            n++;
         }
      }

      return n;
   }

   public static Pose at(double t) {
      if (!visible(t)) {
         return Pose.HIDDEN;
      }

      double m = Math.min(1.0, (t - APPEAR) / MATERIALIZE);
      float grow = (float)(0.55 + 0.45 * (1.0 - (1.0 - m) * (1.0 - m)));
      boolean landed = t >= DioRules.ROLLER_SLAM;
      float pitch;
      if (t < DioRules.ROLLER_HANG) {
         pitch = (float)(4.0 * Math.min(1.0, (t - APPEAR) / (double)(DioRules.ROLLER_APEX - APPEAR)));
      } else if (t < DioRules.ROLLER_SLAM - 3) {
         double u = (t - DioRules.ROLLER_HANG) / (double)(DioRules.ROLLER_SLAM - 3 - DioRules.ROLLER_HANG);
         pitch = (float)(4.0 - 30.0 * u * u);
      } else if (t < DioRules.ROLLER_SLAM) {
         double u = (t - (DioRules.ROLLER_SLAM - 3)) / 3.0;
         pitch = (float)(-26.0 * (1.0 - u));
      } else {
         double s = t - DioRules.ROLLER_SLAM;
         pitch = (float)(3.5 * Math.exp(-s / 3.0) * Math.sin(s * 1.6));
      }

      float squash = 1.0F, sink = 0.0F;
      if (landed) {
         double s = t - DioRules.ROLLER_SLAM;
         squash = (float)(1.0 - 0.2 * Math.exp(-s / 2.2) * Math.cos(s * 0.9));
         sink = (float)Math.min(0.18, 0.06 + 0.006 * Math.min(s, 20.0));
      }

      float dent = pulsesUpTo(t) / (float)DioRules.rollerPulseCount();
      float tremble = t >= TREMBLE_START ? (float)Math.min(1.0, (t - TREMBLE_START) / (DioRules.ROLLER_RESUME - TREMBLE_START)) : 0.0F;
      float spin = landed ? (float)((DioRules.ROLLER_SLAM - APPEAR) * 38.0) : (float)((t - APPEAR) * 38.0);
      return new Pose(true, (float)m, grow, landed, pitch, squash, sink, dent, tremble, spin % 360.0F);
   }
}

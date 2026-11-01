package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where the Sanctuary II is (pure, shared by the server and the client so both agree without a
 * position stream): a glide from the horizon down to the hover point along a slight curve, the hover
 * bob, and the climb away after a defeat. States are synced as ints.
 */
public final class ShipPath {
   public static final int NONE = 0, ARRIVING = 1, HOVER = 2, LEAVING = 3, GONE = 4;
   /** Lateral sweep of the glide at its middle (blocks). */
   static final double SWEEP = 46.0;

   private ShipPath() {
   }

   /** Unit horizontal vector from the start towards the hover point. */
   public static Vec3 heading(Vec3 start, Vec3 hover) {
      Vec3 d = new Vec3(hover.x - start.x, 0.0, hover.z - start.z);
      return d.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : d.normalize();
   }

   /** The ship's centre at {@code t} ticks into {@code state}. */
   public static Vec3 at(int state, double t, Vec3 start, Vec3 hover) {
      Vec3 dir = heading(start, hover);
      Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
      switch (state) {
         case ARRIVING -> {
            double f = t / ThanosWarRules.SHIP_ARRIVAL;
            double g = ThanosWarRules.glide(f);
            Vec3 p = start.add(hover.subtract(start).scale(g));
            double sweep = Math.sin(Math.PI * Mth.clamp(f, 0.0, 1.0)) * SWEEP;
            return p.add(side.scale(sweep));
         }
         case HOVER -> {
            return hover.add(0.0, ThanosWarRules.SHIP_BOB * Math.sin(t * Math.PI * 2.0 / ThanosWarRules.SHIP_BOB_PERIOD), 0.0);
         }
         case LEAVING -> {
            double f = Mth.clamp(t / ThanosWarRules.SHIP_LEAVE, 0.0, 1.0);
            // turns away and climbs, faster and faster
            return hover.add(dir.scale(f * f * 760.0)).add(side.scale(f * 80.0)).add(0.0, f * f * 260.0 + f * 20.0, 0.0);
         }
         default -> {
            return hover;
         }
      }
   }

   /** Heading yaw (degrees, Minecraft convention) of the ship at {@code t} in {@code state}. */
   public static float yaw(int state, double t, Vec3 start, Vec3 hover) {
      Vec3 dir = heading(start, hover);
      float base = (float)(Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
      return switch (state) {
         case ARRIVING -> base - 10.0F * (float)Math.sin(Math.PI * Mth.clamp(t / ThanosWarRules.SHIP_ARRIVAL, 0.0, 1.0));
         case HOVER -> base + 3.0F * (float)Math.sin(t * Math.PI * 2.0 / (ThanosWarRules.SHIP_BOB_PERIOD * 3.0));
         case LEAVING -> base + 25.0F * (float)Mth.clamp(t / ThanosWarRules.SHIP_LEAVE, 0.0, 1.0);
         default -> base;
      };
   }

   /** Bank (roll, degrees) during the glide's curve and the departure's turn. */
   public static float roll(int state, double t) {
      return switch (state) {
         case ARRIVING -> 7.0F * (float)Math.cos(Math.PI * Mth.clamp(t / ThanosWarRules.SHIP_ARRIVAL, 0.0, 1.0));
         case LEAVING -> -9.0F * (float)Math.sin(Math.PI * Mth.clamp(t / ThanosWarRules.SHIP_LEAVE, 0.0, 1.0));
         default -> 0.0F;
      };
   }

   /** Nose pitch (degrees, + nose down) while descending, up while climbing away. */
   public static float pitch(int state, double t) {
      return switch (state) {
         case ARRIVING -> 6.0F * (float)(1.0 - ThanosWarRules.glide(t / ThanosWarRules.SHIP_ARRIVAL));
         case LEAVING -> -12.0F * (float)Mth.clamp(t / 60.0, 0.0, 1.0);
         default -> 0.0F;
      };
   }

   /**
    * Emitters on the underside in the ship's frame (x right, y up, z forward, blocks): the hub and
    * the four arms. The model is built around the same points (client {@code SanctuaryMesh}).
    */
   public static final double[][] EMITTERS = {{0.0, -16.0, 0.0}, {48.0, -12.0, 30.0}, {-48.0, -12.0, 30.0}, {48.0, -12.0, -30.0}, {-48.0, -12.0, -30.0}};

   /** World point of an emitter for a ship at {@code centre} with {@code yaw}. */
   public static Vec3 emitter(int index, Vec3 centre, float yaw) {
      double[] e = EMITTERS[Math.floorMod(index, EMITTERS.length)];
      double r = yaw * Mth.DEG_TO_RAD;
      double cos = Math.cos(r);
      double sin = Math.sin(r);
      // Minecraft yaw: forward = (-sin, 0, cos); right = (-cos, 0, -sin)
      double x = -cos * e[0] - sin * e[2];
      double z = -sin * e[0] + cos * e[2];
      return centre.add(x, e[1], z);
   }

   /** The emitter closest to {@code target}. */
   public static Vec3 gun(Vec3 centre, float yaw, Vec3 target) {
      Vec3 best = centre;
      double bestD = Double.MAX_VALUE;
      for (int i = 0; i < EMITTERS.length; i++) {
         Vec3 p = emitter(i, centre, yaw);
         double d = p.distanceToSqr(target);
         if (d < bestD) {
            bestD = d;
            best = p;
         }
      }

      return best;
   }
}

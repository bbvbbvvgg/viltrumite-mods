package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import net.minecraft.world.phys.Vec3;

/**
 * Hulkbuster movement state and its pure rules (rework spec §5): inertia on the legs, jet impulses,
 * no flight. The same functions decide on the client (prediction of its own impulse) and on the
 * server (validation, refusal snaps back like Thor's dash). One rule for dash availability.
 */
public final class HulkbusterMotion {
   public enum State {
      NONE,
      DASH_WINDUP,
      DASH,
      JET_CHARGE,
      JET_RISE,
      HOVER,
      DIVE,
      LANDING,
      RAM,
      STAGGER,
      FALL;

      private static final State[] VALUES = values();

      public static State byOrdinal(int ordinal) {
         return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
      }

      public boolean airborne() {
         return this == JET_RISE || this == HOVER || this == DIVE || this == FALL;
      }
   }

   public State state = State.NONE;
   public int ticks;
   public int direction;
   public int dashCharges = HulkbusterRules.DASH_CHARGES;
   public int dashRecharge;
   public boolean airDashUsed;
   public int jetCharge;
   public int hoverUsed;
   public double peakY = Double.NaN;
   public double diveStartY;
   public int sprintTicks;
   public int staggerTicks;
   /** S held (client report): RMB in the hold slams down instead of throwing. */
   public boolean backHeld;

   public void set(State next) {
      if (next != this.state) {
         this.state = next;
         this.ticks = 0;
      }
   }

   /** Dash charges refill one at a time. */
   public void tickRecharge() {
      if (this.dashCharges >= HulkbusterRules.DASH_CHARGES) {
         this.dashRecharge = 0;
         return;
      }

      if (++this.dashRecharge >= HulkbusterRules.DASH_RECHARGE) {
         this.dashRecharge = 0;
         this.dashCharges++;
      }
   }

   public void reset() {
      this.state = State.NONE;
      this.ticks = 0;
      this.dashCharges = HulkbusterRules.DASH_CHARGES;
      this.dashRecharge = 0;
      this.airDashUsed = false;
      this.jetCharge = 0;
      this.hoverUsed = 0;
      this.peakY = Double.NaN;
      this.sprintTicks = 0;
      this.staggerTicks = 0;
      this.backHeld = false;
   }

   // ---- pure rules (both sides) ----

   /**
    * Double-WASD dash (spec §5, Thor rule §1.3.7): on the ground or in the hover (one air dash), a charge
    * and energy left, not rooted, not inside a committed move or a scene. One function for both sides.
    */
   public static boolean dashAllowed(boolean hulkActive, boolean busy, boolean rooted, State state, boolean onGround, int charges, boolean airDashUsed,
      boolean energy) {
      if (!hulkActive || busy || rooted || charges <= 0 || !energy) {
         return false;
      }

      if (state == State.STAGGER || state == State.DASH || state == State.DASH_WINDUP || state == State.JET_CHARGE || state == State.DIVE) {
         return false;
      }

      return onGround || state == State.HOVER && !airDashUsed;
   }

   /** Horizontal dash velocity per tick of the burst: DASH_DISTANCE over DASH_BURST ticks along yaw + direction. */
   public static Vec3 dashVelocity(float yaw, int direction) {
      float offset = switch (direction) {
         case 1 -> -90.0F;
         case 2 -> 180.0F;
         case 3 -> 90.0F;
         default -> 0.0F;
      };
      Vec3 forward = Vec3.directionFromRotation(0.0F, yaw + offset);
      return new Vec3(forward.x, 0.0, forward.z).normalize().scale(HulkbusterRules.DASH_DISTANCE / HulkbusterRules.DASH_BURST);
   }

   /** Speed factor of the dash over its burst: hard push, then a skid (sparks and a furrow). */
   public static double dashSpeed(int burstTick) {
      double t = Math.max(0, Math.min(HulkbusterRules.DASH_BURST, burstTick)) / (double)HulkbusterRules.DASH_BURST;
      return 1.6 * (1.0 - t) + 0.25;
   }

   /**
    * Jet jump launch velocity (spec §5): apex height 8..20 blocks from the charge, along the look's yaw;
    * the pitch only shifts height against distance. Vanilla gravity 0.08 and drag 0.98 per tick.
    */
   public static Vec3 jetVelocity(int charge, float yaw, float pitch) {
      double height = HulkbusterRules.jetHeight(charge);
      double vy = Math.sqrt(2.0 * 0.08 * height) * 1.08;
      double up = Math.max(0.0, Math.min(1.0, (-pitch + 10.0) / 70.0));
      double forward = 0.35 + 0.75 * (1.0 - up) * Math.min(1.0, charge / (double)HulkbusterRules.JET_CHARGE_MAX + 0.25);
      Vec3 flat = Vec3.directionFromRotation(0.0F, yaw);
      return new Vec3(flat.x * forward, vy * (0.85 + 0.15 * up), flat.z * forward);
   }

   /** Hover: thrusters hold the fall (vertical speed per tick while the hover lasts). */
   public static double hoverVertical(double current) {
      return Math.max(current * 0.5, -0.04);
   }

   /** Low heavy jump (tap): vanilla 0.42 is too light for the machine. */
   /** Owner's playtest 2026-10-28: 0.36 (~0.6 block) could not clear one block; 0.46 rises ~1.4 blocks. */
   public static final double HEAVY_JUMP = 0.46;

   /** Jump immediately when a jet charge cannot start on this press. */
   public static boolean jumpAtPress(boolean jetPossible) {
      return !jetPossible;
   }

   /** Worst-case preparation for a heavy tap jump. */
   public static int worstJumpPrepTicks() {
      return HulkbusterRules.JET_TAP - 1;
   }

   /** Can a dive start here: airborne and high enough above the ground below. */
   public static boolean diveAllowed(boolean onGround, double heightAboveGround, State state) {
      return !onGround && heightAboveGround >= HulkbusterRules.DIVE_MIN_HEIGHT && state != State.DIVE && state != State.STAGGER;
   }
}

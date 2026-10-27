package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import javax.annotation.Nullable;

/**
 * One authored melee strike of session B: the hit shape and numbers on the
 * server, the clip id on the client ({@link #pose()}, synced as the snapshot
 * resource). Every number is PLACEHOLDER (spec §6: list, hitboxes, damage
 * and windows are "[ОТКРЫТО]"). Hits follow the player's view: no target
 * choice, no auto turn (#112, #143).
 * Pose ids are the ordinal + 1: append only.
 */
public enum ThorStrike {
   // --- Mjölnir in the hand (HAMMER_COMBO) ---
   SWING_RIGHT(ThorMove.HAMMER_COMBO, Shape.ARC, 9.0F, 3.6, 70, 0.6, 0.0, 0, 0, Hand.RIGHT),
   SWING_BACKHAND(ThorMove.HAMMER_COMBO, Shape.ARC, 9.0F, 3.6, 70, 0.7, 0.0, 0, 0, Hand.RIGHT),
   OVERHEAD(ThorMove.HAMMER_COMBO, Shape.ARC, 12.0F, 3.8, 40, 0.4, 0.0, 0, 0, Hand.RIGHT),
   SPIN(ThorMove.HAMMER_COMBO, Shape.RING, 13.0F, 3.8, 180, 1.4, 0.2, 0, 0, Hand.RIGHT),
   UPPERCUT(ThorMove.HAMMER_COMBO, Shape.ARC, 11.0F, 3.4, 45, 0.2, 1.0, 0, 0, Hand.RIGHT),
   RISING(ThorMove.HAMMER_COMBO, Shape.ARC, 10.0F, 3.6, 50, 0.3, 0.5, 0, 0, Hand.RIGHT),
   GROUND_SLAM(ThorMove.HAMMER_COMBO, Shape.SLAM, 16.0F, 3.0, 180, 1.1, 0.5, 4, 2, Hand.RIGHT),
   HEAVY_DOWN(ThorMove.HAMMER_COMBO, Shape.ARC, 13.0F, 3.8, 55, 0.9, 0.0, 0, 0, Hand.RIGHT),
   HEAVY_REVERSE(ThorMove.HAMMER_COMBO, Shape.ARC, 13.0F, 3.8, 55, 1.0, 0.0, 0, 0, Hand.RIGHT),
   HEAVY_SPIN(ThorMove.HAMMER_COMBO, Shape.RING, 17.0F, 4.2, 180, 1.8, 0.3, 0, 0, Hand.RIGHT),
   POMMEL(ThorMove.HAMMER_COMBO, Shape.ARC, 7.0F, 2.8, 45, 1.0, 0.0, 0, 0, Hand.RIGHT),
   DOUBLE_SWING(ThorMove.HAMMER_COMBO, Shape.ARC, 14.0F, 3.8, 75, 1.2, 0.0, 0, 0, Hand.RIGHT),
   THRUST(ThorMove.HAMMER_COMBO, Shape.THRUST, 14.0F, 5.0, 20, 2.0, 0.1, 0, 0, Hand.RIGHT),
   LUNGE(ThorMove.HAMMER_COMBO, Shape.ARC, 11.0F, 4.2, 45, 0.8, 0.0, 0, 0, Hand.RIGHT),
   LUNGE_FINISH(ThorMove.HAMMER_COMBO, Shape.ARC, 14.0F, 3.8, 60, 1.6, 0.4, 0, 0, Hand.RIGHT),
   FALLING_SLAM(ThorMove.HAMMER_COMBO, Shape.SLAM, 15.0F, 2.0, 180, 1.0, 0.4, 3, 1, Hand.RIGHT),
   LOW_SWEEP(ThorMove.HAMMER_COMBO, Shape.ARC, 9.0F, 3.8, 80, 0.5, 0.3, 0, 0, Hand.RIGHT),
   SWEEP_LAUNCH(ThorMove.HAMMER_COMBO, Shape.ARC, 12.0F, 3.4, 50, 0.2, 1.2, 0, 0, Hand.RIGHT),
   // --- bare fists (FIST_COMBO) ---
   JAB(ThorMove.FIST_COMBO, Shape.ARC, 5.0F, 3.0, 35, 0.3, 0.0, 0, 0, Hand.LEFT),
   CROSS(ThorMove.FIST_COMBO, Shape.ARC, 6.0F, 3.0, 35, 0.4, 0.0, 0, 0, Hand.RIGHT),
   HOOK(ThorMove.FIST_COMBO, Shape.ARC, 7.0F, 3.0, 60, 0.6, 0.0, 0, 0, Hand.LEFT),
   FIST_UPPERCUT(ThorMove.FIST_COMBO, Shape.ARC, 9.0F, 3.0, 40, 0.2, 0.9, 0, 0, Hand.RIGHT),
   HAYMAKER(ThorMove.FIST_COMBO, Shape.ARC, 10.0F, 3.2, 60, 1.8, 0.2, 0, 0, Hand.RIGHT),
   HAMMER_FIST(ThorMove.FIST_COMBO, Shape.ARC, 7.0F, 3.0, 40, 0.3, 0.0, 0, 0, Hand.RIGHT),
   HAMMER_FIST_LEFT(ThorMove.FIST_COMBO, Shape.ARC, 7.0F, 3.0, 40, 0.3, 0.0, 0, 0, Hand.LEFT),
   GROUND_POUND(ThorMove.FIST_COMBO, Shape.SLAM, 11.0F, 2.0, 180, 0.9, 0.5, 3, 0, Hand.BOTH),
   BODY_BLOW(ThorMove.FIST_COMBO, Shape.ARC, 6.0F, 2.8, 40, 0.3, 0.0, 0, 0, Hand.RIGHT),
   ELBOW(ThorMove.FIST_COMBO, Shape.ARC, 7.0F, 2.6, 50, 0.5, 0.0, 0, 0, Hand.LEFT),
   PUSH_KICK(ThorMove.FIST_COMBO, Shape.THRUST, 8.0F, 3.4, 30, 2.2, 0.2, 0, 0, Hand.BOTH),
   TACKLE(ThorMove.FIST_COMBO, Shape.ARC, 7.0F, 3.4, 50, 1.2, 0.1, 0, 0, Hand.BOTH),
   TACKLE_HOOK(ThorMove.FIST_COMBO, Shape.ARC, 9.0F, 3.0, 60, 1.4, 0.3, 0, 0, Hand.RIGHT),
   // --- MMB melee branch (HAMMER_STRIKE, chosen by A's resolver) ---
   MMB_BLOW(ThorMove.HAMMER_STRIKE, Shape.ARC, 14.0F, 4.0, 60, 1.2, 0.2, 0, 0, Hand.RIGHT),
   MMB_FINISHER(ThorMove.HAMMER_STRIKE, Shape.ARC, 17.0F, 4.0, 70, 1.8, 0.4, 3, 0, Hand.RIGHT);

   /** Where a strike lands relative to the player's view. */
   public enum Shape {
      /** A cone in front of the view, {@code arc} degrees each side. */
      ARC,
      /** All around (spins). */
      RING,
      /** A narrow, longer line along the view. */
      THRUST,
      /** A ground circle of {@code shockRadius} in front of the feet. */
      SLAM
   }

   /** Which hand swings (first-person swing and clips). */
   public enum Hand {
      RIGHT,
      LEFT,
      BOTH
   }

   private static final ThorStrike[] VALUES = values();

   private final ThorMove move;
   private final Shape shape;
   private final float damage;
   private final double reach;
   private final int arc;
   private final double knockback;
   private final double lift;
   private final int shockRadius;
   private final int crack;
   private final Hand hand;

   ThorStrike(ThorMove move, Shape shape, float damage, double reach, int arc, double knockback, double lift, int shockRadius, int crack, Hand hand) {
      this.move = move;
      this.shape = shape;
      this.damage = damage;
      this.reach = reach;
      this.arc = arc;
      this.knockback = knockback;
      this.lift = lift;
      this.shockRadius = shockRadius;
      this.crack = crack;
      this.hand = hand;
   }

   public ThorMove move() {
      return this.move;
   }

   public Shape shape() {
      return this.shape;
   }

   public float damage() {
      return this.damage;
   }

   public double reach() {
      return this.reach;
   }

   /** Half-angle of the cone, degrees. */
   public int arc() {
      return this.arc;
   }

   public double knockback() {
      return this.knockback;
   }

   public double lift() {
      return this.lift;
   }

   /** Ground shockwave radius (0 = none). */
   public int shockRadius() {
      return this.shockRadius;
   }

   /** Radius of the shallow crater of ordinary blocks (0 = none). */
   public int crack() {
      return this.crack;
   }

   public Hand hand() {
      return this.hand;
   }

   /** Heavy enough for the heavy impact sound and the hit stop. */
   public boolean heavy() {
      return this.damage >= 13.0F || this.shockRadius > 0;
   }

   /** Snapshot pose id (&gt; 0). */
   public int pose() {
      return this.ordinal() + 1;
   }

   @Nullable
   public static ThorStrike byPose(int pose) {
      return pose >= 1 && pose <= VALUES.length ? VALUES[pose - 1] : null;
   }
}

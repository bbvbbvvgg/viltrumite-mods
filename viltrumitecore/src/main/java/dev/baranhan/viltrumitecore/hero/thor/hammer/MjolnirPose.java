package dev.baranhan.viltrumitecore.hero.thor.hammer;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The world Mjölnir at rest (rework spec 2026-10-28 §6), one source for
 * the server box and support, the renderer and the lift anchor: it stands
 * upright, head down and buried {@link #BURY} into the ground, handle
 * straight up. The rest point (entity position) is the ground surface on
 * the hammer's axis; everything above it is the visible part, the buried
 * head is visual only (no block changes). Numbers come from the world model
 * {@code geo/thor/mjolnir_world.geo.json} (tools/assets/make_mjolnir_world.py:
 * head along model +Y, handle along -Y, axis through the model origin,
 * 1 px = 1/16 block). Pure, both sides.
 *
 * <p>Lift anchor API (for the lift animation of stages R1 / R2): the
 * handle top of a planted hammer is {@code handleTop(rest)}; the two palms
 * go to {@link #upperPalm(Vec3)} / {@link #lowerPalm(Vec3)} on the same
 * vertical axis. The handle axis is world +Y for every yaw (the handle sits
 * on the model axis), so the anchors do not depend on the hammer's yaw.
 * Server: {@code MjolnirEntity.handleTop(1.0F)}; client:
 * {@code client.thor.MjolnirAnchors}.
 */
public final class MjolnirPose {
   /** Model extent of the head end (the boss on top of the head), px from the model origin along +Y. */
   public static final double HEAD_END_PX = 8.65;
   /** Model extent of the handle end (the pommel), px from the model origin along -Y. */
   public static final double HANDLE_END_PX = 7.8463;
   /**
    * How deep the head sits in the ground, blocks. Spec: 0.2-0.3. The head
    * of this model is only 0.37 block tall (boss 0.11 + block 0.23 +
    * plates), so the low end keeps a readable band of the head above the
    * ground; deeper hides almost the whole head.
    */
   public static final double BURY = 0.22;
   /** Model origin above the rest point when upright (head down, model rotated 180° about X). */
   public static final double MODEL_OFFSET = HEAD_END_PX / 16.0 - BURY;
   /** Top of the handle (pommel end) above the rest point. */
   public static final double HANDLE_TOP = MODEL_OFFSET + HANDLE_END_PX / 16.0;
   /** Upper palm centre below the handle top (on the flared grip end under the pommel). */
   public static final double UPPER_PALM_DROP = 0.12;
   /** Lower palm centre below the upper one (one palm width). */
   public static final double PALM_SPACING = 0.11;
   /** Width of the head (model X extent 2 x 5.46 px): the upright box is this wide. */
   public static final float UPRIGHT_WIDTH = 0.7F;
   /** Visible height: from the ground to just above the handle top. */
   public static final float UPRIGHT_HEIGHT = (float)(Math.ceil(HANDLE_TOP * 100.0) / 100.0);
   public static final EntityDimensions UPRIGHT = EntityDimensions.fixed(UPRIGHT_WIDTH, UPRIGHT_HEIGHT);
   /** Half side of the support probe under the buried head. */
   static final double SUPPORT_HALF = 0.2;
   /** Depth of the support probe below the rest point. */
   static final double SUPPORT_DEPTH = 0.1;

   private MjolnirPose() {
   }

   /**
    * Does a resting hammer stand planted upright? Every rest is upright
    * except a throw that stuck in a wall or ceiling (embedded and not
    * planted: it keeps the throw direction, head first in the block).
    */
   public static boolean planted(MjolnirPhase phase, boolean lying, boolean embedded) {
      return phase == MjolnirPhase.RESTING && (lying || !embedded);
   }

   /** World position of the handle top of a hammer planted at {@code rest}. */
   public static Vec3 handleTop(Vec3 rest) {
      return rest.add(0.0, HANDLE_TOP, 0.0);
   }

   public static Vec3 upperPalm(Vec3 rest) {
      return rest.add(0.0, HANDLE_TOP - UPPER_PALM_DROP, 0.0);
   }

   public static Vec3 lowerPalm(Vec3 rest) {
      return rest.add(0.0, HANDLE_TOP - UPPER_PALM_DROP - PALM_SPACING, 0.0);
   }

   /** Lowest point of the buried head (visual). */
   public static Vec3 headBottom(Vec3 rest) {
      return rest.subtract(0.0, BURY, 0.0);
   }

   /** Pure: a point just outside a side face hit at {@code at} (face step x / z), clear of a box {@code width} wide. */
   public static Vec3 besideFace(Vec3 at, int stepX, int stepZ, double width) {
      double out = width * 0.5 + 0.05;
      return at.add(stepX * out, 0.0, stepZ * out);
   }

   /** The collision / pick box of a hammer planted at {@code rest} (the visible part). */
   public static AABB box(Vec3 rest) {
      return UPRIGHT.makeBoundingBox(rest);
   }

   /** The probe under the buried head: the hammer stays planted while a block collides with it. */
   public static AABB supportProbe(Vec3 rest) {
      return new AABB(rest.x - SUPPORT_HALF, rest.y - SUPPORT_DEPTH, rest.z - SUPPORT_HALF, rest.x + SUPPORT_HALF, rest.y + 0.02, rest.z + SUPPORT_HALF);
   }

   /** May a lifter whose eye is at {@code eye} reach the planted hammer's box (same distance rule as vanilla interaction). */
   public static boolean withinReach(Vec3 eye, Vec3 rest, double reach) {
      return box(rest).distanceToSqr(eye) <= reach * reach;
   }
}

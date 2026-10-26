package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * REINHARD OVERHAUL 3b item 5: both World Cuts are heavy two-handed strokes whose contact IS the
 * BLAST tick (carve, explosion, camera jolt and Erasure start there): a coil before the swing, an
 * accelerating swing that is fastest on the contact, inertia past the cut line and a settle, in
 * third person (the sampled grip bone) and in first person (the rigid two-handed rig). Geometry
 * of the production chains only: the look of it is an in-game check.
 */
class WorldCutStrokeTest {
   static final int BLAST = ReinhardRules.WORLD_CUT_BLAST;
   static final String[] CLIPS = {ReinhardClips.WORLD_CUT, ReinhardClips.WORLD_CUT_FROM_LEFT, "diagonal_world_cut"};
   static BakedGeoModel body, arms;
   static Map<String, Animation> clips;

   @BeforeAll
   static void load() throws Exception {
      body = ReinhardSecondPassTest.body();
      clips = ReinhardSecondPassTest.clips();
      arms = FirstPersonBlade.arms();
   }

   /** Blade direction of the third-person grip (model space, chest motion included). */
   static Vector3f blade(String clip, double ticks) {
      return ReinhardTitanBlade.gripFrame(body, clips.get(clip), ticks).transformDirection(new Vector3f(0, 1, 0)).normalize();
   }

   static double turn(String clip, double from, double to) {
      return Math.toDegrees(blade(clip, from).angle(blade(clip, to)));
   }

   @Test
   void thirdPersonSwingAcceleratesIntoTheBlastContact() {
      for (String clip : CLIPS) {
         double first = turn(clip, 74, 75), last = turn(clip, BLAST - 1, BLAST), after = turn(clip, BLAST, BLAST + 1);
         double peak = 0, at = 0;
         for (double t = 74; t < BLAST; t += .25) {
            double speed = turn(clip, t, t + .25);
            if (speed > peak) { peak = speed; at = t; }
         }
         assertTrue(at >= BLAST - 1, clip + ": the blade is fastest in the last tick before the contact, peak at " + at);
         assertTrue(last > 4 * first && last > 25, clip + ": an accelerating swing, " + first + " -> " + last + " deg/tick");
         assertTrue(after < last * .6, clip + ": the contact stops the blade hard, " + last + " -> " + after);
         // Anticipation: through the hold and the coil the blade draws back, away from the contact.
         assertTrue(Math.toDegrees(blade(clip, 74).angle(blade(clip, BLAST))) > Math.toDegrees(blade(clip, 64).angle(blade(clip, BLAST))) + 4,
            clip + ": the coil pulls the blade back before the swing");
         // Inertia past the cut line, then the settle and the guard it started from.
         assertTrue(blade(clip, 84).y < blade(clip, BLAST).y - .1F, clip + ": the follow-through carries the blade past the contact");
         assertTrue(blade(clip, 90).y > blade(clip, 84).y, clip + ": and it settles back");
         assertTrue(ReinhardTitanBlade.gripFrame(body, clips.get(clip), 0).equals(ReinhardTitanBlade.gripFrame(body, clips.get(clip), 100), 1E-4F),
            clip + ": starts and ends in the same drawn guard");
      }
   }

   @Test
   void theNormalCutLandsSquareAndTheDiagonalCrossesItsPlane() {
      float side = Math.signum(blade(ReinhardClips.WORLD_CUT, 64).x);
      assertTrue(Math.abs(blade(ReinhardClips.WORLD_CUT, 64).x) > .15F, "raised over a shoulder");
      assertEquals(-side, Math.signum(blade(ReinhardClips.WORLD_CUT_FROM_LEFT, 64).x), "the other shoulder");
      for (String clip : new String[]{ReinhardClips.WORLD_CUT, ReinhardClips.WORLD_CUT_FROM_LEFT}) {
         assertEquals(0, blade(clip, BLAST).x, .02, clip + ": square on the trench line at the contact");
         for (double t : new double[]{84, 90})
            assertEquals(0, blade(clip, t).x, .1, clip + " t=" + t + ": the follow-through stays along the trench");
      }
      // The diagonal: raised over the same shoulder as 'world_cut', down through the front, on to the
      // lower other side - one tilted plane, not a vertical chop with a twist.
      Vector3f raised = blade("diagonal_world_cut", 64), contact = blade("diagonal_world_cut", BLAST), carry = blade("diagonal_world_cut", 84);
      assertTrue(raised.y > .5F && raised.x * side > .35F, "loaded high over the shoulder: " + raised);
      assertTrue(carry.y < -.3F && carry.x * side < -.25F, "follows through to the lower other side: " + carry);
      Vector3f plane = new Vector3f(raised).cross(carry).normalize();
      assertTrue(Math.abs(plane.dot(contact)) < .2F, "the contact lies in the swing plane: " + plane.dot(contact));
      assertTrue(Math.abs(plane.y) > .35F && Math.abs(plane.y) < .9F, "a tilted plane (neither vertical nor flat): " + plane);
   }

   @Test
   void theProjectionStaysInTheTrenchThroughTheFollowThrough() throws Exception {
      assertEquals(70, ReinhardTitanBlade.poseTick(70), 1E-9);
      assertEquals(BLAST, ReinhardTitanBlade.poseTick(BLAST + 6), 1E-9, "the hands' inertia does not drive the projection into the ground");
      Vec3 origin = new Vec3(0, 64, 0), dir = new Vec3(0, 0, 1);
      for (int sweep : new int[]{WorldCutSweep.FROM_RIGHT, WorldCutSweep.FROM_LEFT}) {
         var clip = clips.get(ReinhardTitanBlade.clip(sweep));
         Matrix4f impact = ReinhardTitanBlade.gripFrame(body, clip, BLAST);
         for (double t = BLAST; t <= ReinhardTitanBlade.VISIBLE_TO; t += 2) {
            var placement = ReinhardTitanBlade.placement(origin, dir, 64, t, ReinhardTitanBlade.gripFrame(body, clip, ReinhardTitanBlade.poseTick(t)), impact,
               WorldCutSweep.side(sweep));
            assertTrue(placement.point(ReinhardTitanBlade.TIP).y <= 64.5, "sweep " + sweep + " t=" + t + ": the edge stays in the trench");
         }
      }
   }

   // ---- first person: one rigid motion of the production two-handed rig ----

   static Matrix4f right() {
      FirstPersonBlade.poseElbows(arms, clips, "idle_holy_drawn", 0);
      return ReinhardFirstPersonRig.arm(ReinhardFirstPersonPose.DRAWN_GUARD, false, 0, 0, null);
   }

   static float onScreen(Vector3f p) {
      float[] n = FirstPersonBlade.ndc(p);
      return n[2] < .05F ? 0 : Math.max(0, Math.min(1 - Math.abs(n[0]), 1 - Math.abs(n[1])));
   }

   static float turn(Matrix4f a, Matrix4f b) {
      Quaternionf qa = a.getUnnormalizedRotation(new Quaternionf()).normalize(), qb = b.getUnnormalizedRotation(new Quaternionf()).normalize();
      return (float)Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(qa.dot(qb)))));
   }

   @Test
   void firstPersonPivotIsTheGuardOfTheProductionRig() {
      Matrix4f blade = ReinhardFirstPersonRig.blade(right(), arms, 1);
      assertEquals(0, blade.transformPosition(new Vector3f()).distance(ReinhardFirstPersonPose.CUT_PIVOT), 2E-3, "the rig turns about the right palm");
      assertEquals(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.of(ReinhardAbility.DIAGONAL_WORLD_CUT, 0, 60));
      assertNull(ReinhardFirstPersonPose.cutView(ReinhardAbility.SAW_SPHERE, 0, 60), "only the two World Cuts");
      assertNull(ReinhardFirstPersonPose.cutView(ReinhardAbility.WORLD_CUT, 0, Double.NaN));
      assertNotNull(ReinhardFirstPersonPose.cutView(ReinhardAbility.DIAGONAL_WORLD_CUT, 0, 60));
   }

   @Test
   void firstPersonCutsAreContinuousTwoHandedAndKeepTheArmEndsOutOfView() {
      Matrix4f right = right();
      Matrix4f blade = ReinhardFirstPersonRig.blade(right, arms, 1);
      Matrix4f leftFree = ReinhardFirstPersonRig.arm(ReinhardFirstPersonPose.Offset.NONE, true, 0, 0, null);
      Vector3f bladeDir = blade.transformDirection(new Vector3f(0, 1, 0)).normalize();
      for (Object[] cut : new Object[][]{{ReinhardAbility.WORLD_CUT, WorldCutSweep.FROM_RIGHT}, {ReinhardAbility.WORLD_CUT, WorldCutSweep.FROM_LEFT},
         {ReinhardAbility.DIAGONAL_WORLD_CUT, 0}}) {
         var ability = (ReinhardAbility)cut[0];
         int sweep = (Integer)cut[1];
         String name = ability + "/" + sweep;
         assertTrue(turn(ReinhardFirstPersonPose.cutView(ability, sweep, .01), new Matrix4f()) < .1F, name + ": starts in the guard");
         assertTrue(turn(ReinhardFirstPersonPose.cutView(ability, sweep, 99.99), new Matrix4f()) < .1F, name + ": ends in the guard");
         Matrix4f previous = new Matrix4f();
         for (int i = 1; i < 1200; i++) {
            double t = i / 12.0;
            Matrix4f view = ReinhardFirstPersonPose.cutView(ability, sweep, t);
            assertTrue(turn(view, previous) < 8, name + " t=" + t + ": no jump between 240 Hz frames");
            assertTrue(view.transformPosition(new Vector3f(ReinhardFirstPersonPose.CUT_PIVOT))
               .distance(previous.transformPosition(new Vector3f(ReinhardFirstPersonPose.CUT_PIVOT))) < .05F, name + " t=" + t + ": no teleport");
            previous = view;
            if (i % 3 != 0) continue;
            Matrix4f b = new Matrix4f(view).mul(blade), free = new Matrix4f(view).mul(leftFree);
            Matrix4f left = new Matrix4f().translation(ReinhardFirstPersonRig.supportShift(b, view, free)).mul(free);
            FirstPersonBlade.poseElbows(arms, clips, "idle_holy_drawn", 0);
            assertTrue(ReinhardFirstPersonRig.support(arms, b, left, 1) < .05F, name + " t=" + t + ": both palms stay on the hilt");
            assertEquals(0, onScreen(new Matrix4f(view).mul(right).transformPosition(new Vector3f(5 / 16F, 22 / 16F, 0))), 1E-6, name + " t=" + t + ": right arm end out of view");
            assertEquals(0, onScreen(left.transformPosition(new Vector3f(ReinhardFirstPersonRig.SUPPORT_ROOT))), 1E-6, name + " t=" + t + ": left arm end out of view");
            assertTrue(-b.transformPosition(new Vector3f()).z > .1F && -b.transformPosition(new Vector3f(0, 49.7F / 16, 0)).z > .1F, name + " t=" + t + ": the blade stays in front of the camera");
         }
         // The same beats as the third person: still at the top of the coil, fastest on the BLAST contact.
         float early = turn(ReinhardFirstPersonPose.cutView(ability, sweep, 74), ReinhardFirstPersonPose.cutView(ability, sweep, 74.25));
         float into = turn(ReinhardFirstPersonPose.cutView(ability, sweep, BLAST - .25), ReinhardFirstPersonPose.cutView(ability, sweep, BLAST));
         float out = turn(ReinhardFirstPersonPose.cutView(ability, sweep, BLAST), ReinhardFirstPersonPose.cutView(ability, sweep, BLAST + .25));
         assertTrue(into > 8 && early < into * .1F && out < into * .6F, name + ": swing " + early + " -> " + into + " -> " + out);
      }
      // Both shoulders bring the blade to the same cut line; the diagonal ends across the view.
      Vector3f fromRight = ReinhardFirstPersonPose.cutView(ReinhardAbility.WORLD_CUT, WorldCutSweep.FROM_RIGHT, BLAST).transformDirection(new Vector3f(bladeDir));
      Vector3f fromLeft = ReinhardFirstPersonPose.cutView(ReinhardAbility.WORLD_CUT, WorldCutSweep.FROM_LEFT, BLAST).transformDirection(new Vector3f(bladeDir));
      assertTrue(fromRight.angle(fromLeft) < Math.toRadians(3), "one contact line: " + fromRight + " " + fromLeft);
      assertTrue(fromRight.z < -.8F, "the blade points down the cut, away from the camera: " + fromRight);
      Vector3f diagonal = ReinhardFirstPersonPose.cutView(ReinhardAbility.DIAGONAL_WORLD_CUT, 0, BLAST).transformDirection(new Vector3f(bladeDir));
      assertTrue(diagonal.x < -.5F, "the diagonal sweeps across the view to the left: " + diagonal);
   }
}

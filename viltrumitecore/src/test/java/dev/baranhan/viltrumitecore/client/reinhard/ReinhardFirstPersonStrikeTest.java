package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeArc;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Item 4: drawn Reid in first person sits on a diagonal and every drawn stroke crosses the
 * view along the server's MeleeArc path. Replays the real first-person chain (FirstPersonBlade). */
class ReinhardFirstPersonStrikeTest {
   private static BakedGeoModel arms;

   @BeforeAll
   static void load() throws Exception {
      arms = FirstPersonBlade.arms();
      FirstPersonBlade.poseElbows(arms, ReinhardSecondPassTest.clips(), "idle_holy_drawn", 0);
   }

   private static float[] ndc(ReinhardFirstPersonPose.Offset o, float y) {
      return FirstPersonBlade.ndc(FirstPersonBlade.point(FirstPersonBlade.frame(arms, o), y));
   }

   /** The production drawn guard (ReinhardFirstPersonRig): both palms on the hilt, the sword palm
    * slid to the guard end (REINHARD OVERHAUL 3b item 1). View-space blade frame. */
   private static Matrix4f guardBlade() throws Exception {
      FirstPersonBlade.poseElbows(arms, ReinhardSecondPassTest.clips(), "idle_holy_drawn", 0);
      return ReinhardFirstPersonRig.blade(ReinhardFirstPersonRig.arm(ReinhardFirstPersonPose.DRAWN_GUARD, false, 0, 0, null), arms, 1);
   }

   @Test
   void drawnGuardIsADiagonalBesideTheCrosshair() throws Exception {
      Matrix4f blade = guardBlade();
      float[] guard = FirstPersonBlade.ndc(FirstPersonBlade.point(blade, FirstPersonBlade.GUARD));
      float[] tip = FirstPersonBlade.ndc(FirstPersonBlade.point(blade, FirstPersonBlade.TIP));
      assertTrue(guard[0] > .4F && guard[1] < -.35F, "the hands stay low right: " + guard[0] + "," + guard[1]);
      assertTrue(tip[1] > .5F && Math.abs(tip[0]) < .35F, "the tip rises toward the top of the view: " + tip[0] + "," + tip[1]);
      // A projected straight blade stays straight: where it passes the crosshair height it is right of the centre.
      float atCrosshair = guard[0] + (tip[0] - guard[0]) * -guard[1] / (tip[1] - guard[1]);
      assertTrue(atCrosshair > .12F, "the blade does not run over the crosshair: " + atCrosshair);
      assertTrue(tip[2] > guard[2] + .15F, "the tip leans away from the camera, never toward it: " + guard[2] + " -> " + tip[2]);
      float slope = Math.abs((tip[1] - guard[1]) / (tip[0] - guard[0]));
      assertTrue(slope > .8F && slope < 5, "a diagonal, neither flat nor a vertical bar: " + slope);
   }

   @Test
   void drawnGuardFollowsTheHandWeight() {
      assertSame(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.drawnGuard(ReinhardPoses.ReidPhase.SHEATHED, 0));
      assertEquals(ReinhardFirstPersonPose.DRAWN_GUARD, ReinhardFirstPersonPose.drawnGuard(ReinhardPoses.ReidPhase.DRAWN, 3));
      float half = ReinhardFirstPersonPose.drawnGuard(ReinhardPoses.ReidPhase.DRAWING, 108).roll();
      assertTrue(half < 0 && half > ReinhardFirstPersonPose.DRAWN_GUARD.roll(), "the guard grows in with the grip");
   }

   @Test
   void strokesStartAndEndAtTheGuard() {
      for (int move = 0; move < MeleeMoves.REID.length; move++) {
         assertEquals(ReinhardFirstPersonPose.DRAWN_GUARD, ReinhardFirstPersonPose.reidStrike(move, 0), "move " + move);
         assertEquals(ReinhardFirstPersonPose.DRAWN_GUARD, ReinhardFirstPersonPose.reidStrike(move, MeleeMoves.REID[move].ticks()), "move " + move);
         var rest = ReinhardFirstPersonPose.reidSupport(move, 0);
         assertEquals(0, rest.x() + rest.y() + rest.z() + rest.pitch() + rest.yaw() + rest.roll(), 1e-5, "support rests at zero");
      }
   }

   @Test
   void strokesCrossTheViewAlongTheServerArc() {
      for (int move = 0; move < MeleeMoves.REID.length; move++) {
         MeleeMoves.Move m = MeleeMoves.REID[move];
         MeleeArc.Swing s = m.swing();
         double from = s.contactStart() - 1, to = s.contactEnd();
         float[] a = ndc(ReinhardFirstPersonPose.reidStrike(move, from), FirstPersonBlade.TIP * .6F);
         float[] b = ndc(ReinhardFirstPersonPose.reidStrike(move, to), FirstPersonBlade.TIP * .6F);
         double sx = b[0] - a[0], sy = b[1] - a[1], ax = s.yawTo() - s.yawFrom(), ay = s.pitchTo() - s.pitchFrom();
         double dot = (sx * ax + sy * ay) / Math.sqrt((sx * sx + sy * sy) * (ax * ax + ay * ay));
         assertTrue(dot > .6, m.clip() + ": the blade moves along the hit arc on screen, cos=" + dot);
         assertTrue(Math.hypot(sx, sy) > .6, m.clip() + ": a full stroke across the view");
         for (double t = 0; t <= m.ticks(); t += .5) {
            var o = ReinhardFirstPersonPose.reidStrike(move, t);
            float[] h = ndc(o, FirstPersonBlade.GUARD), p = ndc(o, FirstPersonBlade.TIP);
            assertTrue(h[2] > .2F && p[2] > .2F, m.clip() + " t=" + t + ": the blade stays in front of the camera");
         }
      }
   }

   @Test
   void airStepDipIsAShortPulse() {
      assertSame(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.airStep(-1));
      assertSame(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.airStep(ReinhardFirstPersonPose.AIR_STEP_SECONDS));
      assertTrue(ReinhardFirstPersonPose.airStep(ReinhardFirstPersonPose.AIR_STEP_SECONDS / 2).y() < -.05F);
   }

   @Test
   void otherActionsHandTheGuardOverAndBack() {
      assertEquals(1, ReinhardFirstPersonPose.guardUnder(0, 20), 1e-6);
      assertEquals(0, ReinhardFirstPersonPose.guardUnder(10, 20), 1e-6);
      assertEquals(1, ReinhardFirstPersonPose.guardUnder(20, 20), 1e-6);
      assertEquals(0, ReinhardFirstPersonPose.guardUnder(30, -1), 1e-6, "an open-ended action keeps it away");
   }

   @Test
   void leftJabIsTheLeftArmsStroke() {
      assertTrue(ReinhardFirstPersonPose.leftStrike(ReinhardAbility.LIGHT_PUNCH, 1));
      assertTrue(!ReinhardFirstPersonPose.leftStrike(ReinhardAbility.LIGHT_PUNCH, 0) && !ReinhardFirstPersonPose.leftStrike(ReinhardAbility.FIST_COMBO, 1));
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.LIGHT_PUNCH, 2, 4).yaw() > 0, "move 2 is the hook");
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.LIGHT_PUNCH, 1, 4).z() < -.2F, "the left jab reaches forward");
   }

   @Test
   void printStrokePaths() {
      if (System.getenv("REINHARD_FP_PATHS") == null) return;
      for (int move = 0; move < MeleeMoves.REID.length; move++) {
         MeleeMoves.Move m = MeleeMoves.REID[move];
         StringBuilder out = new StringBuilder("PATH " + m.clip());
         for (double t = 0; t <= m.ticks(); t += .5) {
            var o = ReinhardFirstPersonPose.reidStrike(move, t);
            float[] h = ndc(o, FirstPersonBlade.GUARD), p = ndc(o, FirstPersonBlade.TIP);
            out.append(String.format("%n  t=%4.1f guard(%.2f,%.2f,d%.2f) tip(%.2f,%.2f,d%.2f)", t, h[0], h[1], h[2], p[0], p[1], p[2]));
         }
         System.out.println(out);
      }
   }
}

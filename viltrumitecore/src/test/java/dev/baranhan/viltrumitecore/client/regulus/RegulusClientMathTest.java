package dev.baranhan.viltrumitecore.client.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.regulus.RegulusMove;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pure client maths: pose blending, gait, the ult cameras and the HUD. */
class RegulusClientMathTest {
   private static final Vec3 CASTER = new Vec3(10.0, 64.0, -3.0);
   private static final Vec3 TARGET = new Vec3(16.0, 64.0, 4.0);

   @Test
   void moveWeightEasesInAndOut() {
      for (RegulusMove move : RegulusMove.values()) {
         assertTrue(RegulusPoser.moveWeight(move, 0.0) > 0.0F, move.name());
         assertEquals(0.0F, RegulusPoser.moveWeight(move, move.length()), 1.0E-6F, move.name());
         float mid = RegulusPoser.moveWeight(move, move.length() / 2.0);
         assertEquals(1.0F, mid, 1.0E-6F, move.name());
         float prev = 0.0F;
         for (double t = 0.0; t <= move.length(); t += 0.25) {
            float w = RegulusPoser.moveWeight(move, t);
            assertTrue(w >= 0.0F && w <= 1.0F);
            assertTrue(Math.abs(w - prev) <= 0.25 / 1.5 + 1.0E-4 || t == 0.0, move + " jump at " + t);
            prev = w;
         }
      }
   }

   @Test
   void onlyFullBodyMovesOwnTheLegs() {
      assertFalse(RegulusPoser.legsWalk(RegulusMove.TRAMPLE));
      assertFalse(RegulusPoser.legsWalk(RegulusMove.ULT));
      assertTrue(RegulusPoser.legsWalk(RegulusMove.SAND));
      assertTrue(RegulusPoser.legsWalk(null));
   }

   @Test
   void strideBlendsWalkRunAndSneak() {
      assertEquals(RegulusPoser.WALK_STRIDE, RegulusPoser.stride(0.0F, 0.0F), 1.0E-6F);
      assertEquals(RegulusPoser.RUN_STRIDE, RegulusPoser.stride(1.0F, 0.0F), 1.0E-6F);
      assertEquals(RegulusPoser.SNEAK_STRIDE, RegulusPoser.stride(1.0F, 1.0F), 1.0E-6F);
   }

   @Test
   void walkYawFacesTheStepAndWalksBackwards() {
      float[] still = RegulusPoser.walkYaw(0.0, 0.0);
      assertEquals(0.0F, still[0]);
      assertEquals(1.0F, still[1]);
      float[] fwd = RegulusPoser.walkYaw(1.0, 0.0);
      assertEquals(0.0F, fwd[0], 1.0E-6F);
      assertEquals(1.0F, fwd[1]);
      float[] back = RegulusPoser.walkYaw(-1.0, 0.0);
      assertEquals(-1.0F, back[1]);
      assertEquals(0.0F, back[0], 1.0E-4F);
      float[] diag = RegulusPoser.walkYaw(1.0, 1.0);
      assertTrue(diag[0] > 0.0F && diag[0] <= RegulusPoser.WALK_YAW_LIMIT);
      float[] side = RegulusPoser.walkYaw(0.0, 1.0);
      assertEquals(RegulusPoser.WALK_YAW_LIMIT, side[0], 1.0E-4F);
      float[] backDiag = RegulusPoser.walkYaw(-1.0, 1.0);
      assertEquals(-1.0F, backDiag[1]);
      assertTrue(backDiag[0] < 0.0F, "backwards right step turns the pelvis left");
   }

   private static double jump(Vec3[] a, Vec3[] b) {
      return Math.max(a[0].distanceTo(b[0]), a[1].distanceTo(b[1]));
   }

   @Test
   void ultCamerasAreContinuousExceptTheContactCut() {
      Vec3 eye = CASTER.add(0.0, 1.62, 0.0);
      Vec3 look = eye.add(0.0, 0.0, 6.0);
      for (boolean caster : new boolean[]{true, false}) {
         Vec3[] prev = caster ? RegulusUltScene.casterShot(CASTER, TARGET, 0.0, eye, look) : RegulusUltScene.targetShot(CASTER, TARGET, 0.0);
         for (double t = 0.25; t <= RegulusRules.ULT_LENGTH; t += 0.25) {
            Vec3[] cur = caster ? RegulusUltScene.casterShot(CASTER, TARGET, t, eye, look) : RegulusUltScene.targetShot(CASTER, TARGET, t);
            for (Vec3 v : cur) {
               assertTrue(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z));
            }

            boolean contactCut = caster && t > RegulusRules.ULT_FLICK_CONTACT - 0.25 && t <= RegulusRules.ULT_FLICK_CONTACT;
            if (!contactCut) {
               assertTrue(jump(prev, cur) < 2.5, (caster ? "caster" : "target") + " camera jumps " + jump(prev, cur) + " at " + t);
            }

            prev = cur;
         }
      }
   }

   @Test
   void theCasterEndsInHisOwnEyes() {
      Vec3 eye = CASTER.add(0.0, 1.62, 0.0);
      Vec3 look = eye.add(0.0, 0.0, 6.0);
      Vec3[] last = RegulusUltScene.casterShot(CASTER, TARGET, RegulusRules.ULT_LENGTH, eye, look);
      assertEquals(0.0, last[0].distanceTo(eye), 1.0E-6);
      assertEquals(0.0, last[1].distanceTo(look), 1.0E-6);
   }

   @Test
   void camerasSurviveACollapsedScene() {
      for (double t = 0.0; t <= RegulusRules.ULT_LENGTH; t += 5.0) {
         for (boolean caster : new boolean[]{true, false}) {
            for (Vec3 v : RegulusUltScene.raw(CASTER, CASTER, t, caster)) {
               assertTrue(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z));
            }
         }
      }
   }

   @Test
   void observerEnvelopeIsBounded() {
      assertEquals(0.0F, RegulusUltScene.envelope(-1.0));
      assertEquals(0.0F, RegulusUltScene.envelope(RegulusRules.ULT_LENGTH + 1.0));
      assertEquals(1.0F, RegulusUltScene.envelope(RegulusRules.ULT_LENGTH / 2.0), 1.0E-6F);
      assertEquals(0.0F, RegulusUltScene.envelope(RegulusRules.ULT_LENGTH), 1.0E-6F);
      assertTrue(RegulusUltScene.TARGET_RELEASE > RegulusRules.ULT_FLICK_CONTACT);
   }

   @Test
   void smoothstepIsClamped() {
      assertEquals(0.0, RegulusUltScene.smooth(-2.0));
      assertEquals(1.0, RegulusUltScene.smooth(3.0));
      assertEquals(0.5, RegulusUltScene.smooth(0.5), 1.0E-9);
   }

   @Test
   void hudSecondsAndStrainColour() {
      assertEquals("0.0", RegulusHud.seconds(0));
      assertEquals("0.0", RegulusHud.seconds(-5));
      assertEquals("3.5", RegulusHud.seconds(70));
      assertEquals("0.5", RegulusHud.seconds(9));
      assertEquals(RegulusPalette.INK, RegulusHud.strainColour(500, true, true, 0L));
      assertEquals(RegulusPalette.IVORY, RegulusHud.strainColour(0, false, false, 0L));
      assertEquals(RegulusPalette.GOLD, RegulusHud.strainColour(RegulusRules.STRAIN_MAX, false, false, 0L));
      assertTrue(RegulusHud.strainColour(800, true, false, 0L) != RegulusHud.strainColour(800, false, false, 0L));
   }

   @Test
   void shockwaveRingGrowsThenStops() {
      assertEquals(0.5, RegulusVfx.Shockwave.front(0, 0.9, 7.0), 1.0E-9);
      assertEquals(0.5, RegulusVfx.Shockwave.front(-3, 0.9, 7.0), 1.0E-9);
      assertEquals(1.4, RegulusVfx.Shockwave.front(1, 0.9, 7.0), 1.0E-9);
      assertEquals(7.0, RegulusVfx.Shockwave.front(40, 0.9, 7.0), 1.0E-9);
   }
}

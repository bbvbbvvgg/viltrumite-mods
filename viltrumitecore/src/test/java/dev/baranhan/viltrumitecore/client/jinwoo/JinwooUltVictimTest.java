package dev.baranhan.viltrumitecore.client.jinwoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Playtest: the opponent caught in «Армия теней, восстань!» gets its own cinematic shots. */
class JinwooUltVictimTest {
   private static final double R2 = JinwooRules.ULT_RADIUS * JinwooRules.ULT_RADIUS;

   @Test
   void onlyNonAlliedPlayersInsideTheRadiusAreCaughtBeforeTheCharge() {
      assertTrue(JinwooUltScene.catches(false, false, 100, 0));
      assertTrue(JinwooUltScene.catches(false, false, R2, JinwooRules.ULT_WAVE - 1));
      assertFalse(JinwooUltScene.catches(true, false, 0, 0), "the caster has the owner camera");
      assertFalse(JinwooUltScene.catches(false, true, 100, 0), "allies only watch");
      assertFalse(JinwooUltScene.catches(false, false, R2 + 1, 0), "outside the radius");
      assertFalse(JinwooUltScene.catches(false, false, 100, JinwooRules.ULT_WAVE), "no late camera grab");
   }

   @Test
   void controlReturnsBeforeTheUltEnds() {
      assertTrue(JinwooUltScene.VICTIM_END > JinwooRules.ULT_WAVE_END);
      assertTrue(JinwooUltScene.VICTIM_END < JinwooRules.ULT_LENGTH);
   }

   @Test
   void firstShotLooksAtTheMonarchFromBehindTheTarget() {
      JinwooUltScene.Scene scene = new JinwooUltScene.Scene(1, 0L, new Vec3(0, 64, 0), 0.0F);
      Vec3 victim = new Vec3(10, 64, 0);
      Vec3[] shot = JinwooUltScene.victimShot(scene, 0, victim);
      assertEquals(0.0, shot[1].x, 1.0E-6);
      assertTrue(shot[0].x > victim.x, "camera behind the target, the Monarch in front");
      assertTrue(shot[0].distanceTo(victim) < 4.0);
   }

   @Test
   void cutsAreBlendedLikeTheOwnerShots() {
      JinwooUltScene.Scene scene = new JinwooUltScene.Scene(1, 0L, new Vec3(0, 64, 0), 90.0F);
      Vec3 victim = new Vec3(6, 64, 6);
      for (int cut : new int[]{JinwooRules.ULT_RISE_START, JinwooRules.ULT_WAVE}) {
         Vec3[] before = JinwooUltScene.victimShot(scene, cut - 1, victim);
         Vec3[] at = JinwooUltScene.victimShot(scene, cut, victim);
         assertTrue(before[0].distanceTo(at[0]) < 1.0E-6, "no hard jump at tick " + cut);
      }
   }

   @Test
   void standingOnTheMonarchStillGivesAFiniteShot() {
      JinwooUltScene.Scene scene = new JinwooUltScene.Scene(1, 0L, new Vec3(0, 64, 0), 0.0F);
      for (int t = 0; t < JinwooUltScene.VICTIM_END; t += 7) {
         Vec3[] shot = JinwooUltScene.victimShot(scene, t, scene.origin);
         assertTrue(Double.isFinite(shot[0].x + shot[0].y + shot[0].z));
      }
   }
}

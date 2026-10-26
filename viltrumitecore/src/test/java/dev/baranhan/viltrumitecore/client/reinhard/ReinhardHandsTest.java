package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * REINHARD OVERHAUL 3b item 2: one owner per support hand. With Reid drawn the empty sheath never
 * claims it; a stroke, the sprint or Lethal Chop frees it for its own clip and gives it back; the
 * two-handed actions keep both palms on the hilt. Item 1: the first-person sheath is not drawn
 * while Reid is drawn (third person keeps it).
 */
class ReinhardHandsTest {
   static final ReinhardPoses.ReidPhase SHEATHED = ReinhardPoses.ReidPhase.SHEATHED, DRAWN = ReinhardPoses.ReidPhase.DRAWN,
      DRAWING = ReinhardPoses.ReidPhase.DRAWING;

   @Test
   void theSheathNeverOwnsTheHandOfADrawnReid() {
      for (float release : new float[]{0, .3F, 1}) {
         var drawn = ReinhardHands.support(DRAWN, 0, 1, release);
         assertEquals(0, drawn.sheath(), "drawn, release " + release);
         assertEquals(1 - release, drawn.hilt(), 1E-6, "the pommel lets go by the release, release " + release);
      }
      assertEquals(1, ReinhardHands.support(SHEATHED, 0, 1, 0).sheath(), 1E-6, "sheathed: the hand rests on the hilt in the sheath");
      assertEquals(0, ReinhardHands.support(SHEATHED, 0, 1, 1).sheath(), 1E-6, "a released hand leaves the sheath for its clip");
      assertEquals(0, ReinhardHands.support(SHEATHED, 0, 1, 0).hilt(), 1E-6, "nothing to hold two-handed");
      assertEquals(1, ReinhardHands.support(DRAWING, 40, 1, 1).sheath(), 1E-6, "the draw owns its own grip");
      assertEquals(0, ReinhardHands.support(DRAWN, 0, 0, 0).hilt(), 1E-6, "outside the Holy Sword stance nothing claims it");
   }

   @Test
   void actionsReleaseTheSupportHandOnlyWhenTheyUseIt() {
      for (var two : new ReinhardAbility[]{ReinhardAbility.WORLD_CUT, ReinhardAbility.DIAGONAL_WORLD_CUT, ReinhardAbility.WORLD_ERASURE}) {
         assertTrue(ReinhardHands.twoHanded(two));
         assertEquals(0, ReinhardHands.actionRelease(two, true, 5, 20), "two-handed " + two + " keeps both palms on the hilt");
      }
      assertFalse(ReinhardHands.twoHanded(ReinhardAbility.REID_STRIKE));
      assertFalse(ReinhardHands.twoHanded(ReinhardAbility.SAW_SPHERE), "the Saw Sphere flings the saws one-handed");
      assertEquals(1, ReinhardHands.actionRelease(ReinhardAbility.SAW_SPHERE, true, 120, 260), 1E-6, "its clip owns the balancing left arm");
      assertEquals(1, ReinhardHands.actionRelease(ReinhardAbility.REID_STRIKE, false, 6, 13), 1E-6, "drawn strokes are one-handed");
      assertEquals(0, ReinhardHands.actionRelease(ReinhardAbility.REID_STRIKE, false, 0, 13), 1E-6, "and take the hand back");
      assertEquals(0, ReinhardHands.actionRelease(ReinhardAbility.REID_STRIKE, false, 13, 13), 1E-6);
      assertEquals(1, ReinhardHands.actionRelease(ReinhardAbility.LIGHT_PUNCH, true, 5, 12), 1E-6, "a left strike frees its hand");
      assertEquals(0, ReinhardHands.actionRelease(ReinhardAbility.LIGHT_PUNCH, false, 5, 12), 1E-6, "a right strike does not");
      assertEquals(0, ReinhardHands.actionRelease(ReinhardAbility.SHEATH_STRIKE, true, 5, 12), "Sheath Strike owns its grips");
      assertEquals(0, ReinhardHands.actionRelease(null, true, 5, 12));
      assertEquals(.7F, ReinhardHands.release(.2F, .7F, 0), 1E-6, "the strongest claim");
      assertEquals(1, ReinhardHands.release(3), 1E-6);
   }

   @Test
   void chopReleaseOpensAndClosesInsideTheChop() {
      assertEquals(0, ReinhardSword.chopRelease(0));
      assertEquals(0, ReinhardSword.chopRelease(1));
      assertEquals(0, ReinhardSword.chopRelease(Double.NaN));
      assertEquals(1, ReinhardSword.chopRelease(.5), 1E-6, "free through the chop");
      float previous = 0;
      for (int i = 1; i <= 50; i++) {
         float r = ReinhardSword.chopRelease(i / 100.0);
         assertTrue(r >= previous - 1E-6 && r - previous < .25F, "lets go smoothly at " + i);
         previous = r;
      }
   }

   @Test
   void theLeftHandChopDoesNotSwingTheSwordItIsNotHolding() throws Exception {
      var all = JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
      assertFalse(all.getAsJsonObject("chop_left").getAsJsonObject("bones").has("reid_grip"), "no grip rows on the left-hand chop");
      assertTrue(all.getAsJsonObject("chop_right").getAsJsonObject("bones").has("reid_grip"), "the right-hand chop carries Reid");
      var clips = ReinhardSecondPassTest.clips();
      for (double t = 0; t <= 20; t += 1) {
         var still = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("idle_holy_drawn"), still, 0);
         var guard = ReinhardSword.equipment(still, DRAWN, 0, false, 0, 1, 0);
         var chop = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("idle_holy_drawn"), chop, 0);
         ReinhardLayers.overlay(clips.get("chop_left"), chop, t / 20, 1);
         float release = ReinhardSword.chopRelease(t / 20);
         var frames = ReinhardSword.equipment(chop, DRAWN, 0, false, 0, 1, release);
         // The chest turns with the chop and carries the right arm and Reid; relative to it the blade does not move.
         var held = new org.joml.Matrix4f(ReinhardGeometry.frame(still.getBone("body"))).invert().mul(guard[1]);
         var during = new org.joml.Matrix4f(ReinhardGeometry.frame(chop.getBone("body"))).invert().mul(frames[1]);
         assertTrue(held.equals(during, 1E-4F), "t=" + t + ": Reid stays in the right hand's guard");
      }
   }

   @Test
   void aReleasedSupportHandFollowsItsClipNotTheEmptySheath() throws Exception {
      var clips = ReinhardSecondPassTest.clips();
      for (String clip : new String[]{"drawn_run", "chop_left", "reid_slash_down"}) {
         var length = clips.get(clip).lengthSeconds() * 20;
         double t = clip.equals("drawn_run") ? 5 : clip.equals("chop_left") ? 7 : 6;
         var free = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("idle_holy_drawn"), free, 0);
         ReinhardLayers.overlay(clips.get(clip), free, t / 20, 1);
         Vector3f own = ReinhardGrip.point(free, true);
         var solved = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("idle_holy_drawn"), solved, 0);
         ReinhardLayers.overlay(clips.get(clip), solved, t / 20, 1);
         float release = clip.equals("chop_left") ? ReinhardSword.chopRelease(t / length) : ReinhardEquipmentTest.oneHand(clip, t, length);
         assertEquals(1, release, 1E-3, clip + ": fully released at t=" + t);
         ReinhardSword.equipment(solved, DRAWN, 0, false, 0, 1, release);
         assertTrue(ReinhardGrip.point(solved, true).distance(own) < 1E-4F, clip + ": the released palm is exactly its clip's, no solve sends it to the empty sheath");
      }
   }

   @Test
   void theFirstPersonSheathIsOnlyThereWhileItIsUsed() {
      assertFalse(ReinhardSword.firstPersonSheathVisible(DRAWN, false), "drawn Reid: no sheath in the first-person view");
      assertTrue(ReinhardSword.firstPersonSheathVisible(DRAWN, true), "Sheath Strike swings it");
      assertTrue(ReinhardSword.firstPersonSheathVisible(SHEATHED, false));
      assertTrue(ReinhardSword.firstPersonSheathVisible(DRAWING, false));
      assertEquals(0, ReinhardSword.firstPersonSheathDrop(SHEATHED, 0, false));
      assertEquals(0, ReinhardSword.firstPersonSheathDrop(DRAWING, 0, false), 1E-6, "the draw starts at the belt");
      assertEquals(ReinhardSword.FIRST_PERSON_SHEATH_DROP * ReinhardSword.handWeight(DRAWING, 110), ReinhardSword.firstPersonSheathDrop(DRAWING, 110, false), 1E-6,
         "it sinks out of the view with the hand");
      assertEquals(0, ReinhardSword.firstPersonSheathDrop(DRAWING, 110, true));
   }
}

package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** REINHARD_FP_PREVIEW=1: exports first-person frames of the production two-handed rig for
 * tools/preview_reinhard_fp.py. A CPU preview of the render chain; it does not stand in for an
 * in-game check (no Minecraft lighting, bobbing, FOV effects or real frame timing). */
class ReinhardFirstPersonPreviewTest {
   static final String ARMS_TEXTURE = "reinhard.png", SWORD = "dragon_sword.png";

   /** The drawn guard moved by {@code cut} (identity = the plain guard), built like the renderer:
    * the sword arm and its blade first, then the support arm carried to the pommel and solved. */
   static void scene(String name, BakedGeoModel arms, BakedGeoModel blade, Matrix4f cut) throws Exception {
      FirstPersonBlade.poseElbows(arms, ReinhardSecondPassTest.clips(), "idle_holy_drawn", 0);
      Matrix4f right = new Matrix4f(cut).mul(ReinhardFirstPersonRig.arm(ReinhardFirstPersonPose.DRAWN_GUARD, false, 0, 0, null));
      Matrix4f hand = ReinhardFirstPersonRig.blade(right, arms, 1);
      var scene = new FirstPersonScene();
      scene.bone(arms, "armorRightArm", right, ARMS_TEXTURE);
      float roll = ReinhardSword.drawnRoll(ReinhardPoses.ReidPhase.DRAWN, 0) * ReinhardSword.FIRST_PERSON_ROLL;
      scene.model(blade, new Matrix4f(hand).rotateY(roll), SWORD);
      Matrix4f free = new Matrix4f(cut).mul(ReinhardFirstPersonRig.arm(ReinhardFirstPersonPose.Offset.NONE, true, 0, 0, null));
      Matrix4f left = new Matrix4f().translation(ReinhardFirstPersonRig.supportShift(hand, cut, free)).mul(free);
      ReinhardFirstPersonRig.support(arms, hand, left, 1);
      scene.bone(arms, "armorLeftArm", left, ARMS_TEXTURE);
      scene.marker(hand.transformPosition(new Vector3f()), "red");
      scene.write(name);
   }

   @Test
   void export() throws Exception {
      if (System.getenv("REINHARD_FP_PREVIEW") == null) return;
      var arms = FirstPersonBlade.arms();
      var blade = FirstPersonScene.geo("blade");
      scene("guard", arms, blade, new Matrix4f());
      Object[][] cuts = {{"cut_right", ReinhardAbility.WORLD_CUT, WorldCutSweep.FROM_RIGHT},
         {"cut_left", ReinhardAbility.WORLD_CUT, WorldCutSweep.FROM_LEFT}, {"diagonal", ReinhardAbility.DIAGONAL_WORLD_CUT, 0}};
      for (Object[] cut : cuts)
         for (double t : new double[]{5, 10, 18, 40, 64, 70, 74, 77, 79, 80, 82, 84, 87, 90, 95}) {
            Matrix4f view = ReinhardFirstPersonPose.cutView((ReinhardAbility)cut[1], (Integer)cut[2], t);
            scene(String.format("%s__%05.1f", cut[0], t), arms, blade, view);
         }
   }
}

package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

class ReinhardPosesTest {
   private static ReinhardPoses.Input input(ReinhardMode mode, int actionId, int elapsed, int length, float swing) {
      return new ReinhardPoses.Input(mode, ReinhardFlags.withMode(0, mode), actionId, elapsed, length, 0.5F, swing, 3.0F, 12.5,
         net.minecraft.world.entity.Pose.STANDING, false);
   }

   @Test
   void idleWhenStill() {
      ReinhardPoses.Pose pose = ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, -1, 0, 0, 0.0F));
      assertEquals(0.0F, pose.walk(), 1.0E-6F);
      assertEquals(12.5, pose.stanceSeconds(), 1.0E-9);
      assertNull(pose.overlay());
      assertEquals(ReinhardPoses.BodyState.STAND, pose.body());
   }

   @Test
   void walkWeightFromSwing() {
      assertEquals(1.0F, ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, -1, 0, 0, 0.55F)).walk(), 1.0E-6F);
      assertEquals(0.5F, ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, -1, 0, 0, 0.275F)).walk(), 1.0E-6F);
      assertEquals(1.0F, ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, -1, 0, 0, 0.9F)).walk(), 1.0E-6F, "clamped");
      // Clip time follows the walked distance: one vanilla limb-swing period (2 pi / 0.6662) = one clip length.
      ReinhardPoses.Pose pose = ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, -1, 0, 0, 0.55F));
      assertEquals(3.0 * ReinhardPoses.WALK_SECONDS_PER_BLOCK, pose.walkSeconds(), 1.0E-9);
      assertEquals(1.0, 2.0 * Math.PI / 0.6662 * ReinhardPoses.WALK_SECONDS_PER_BLOCK, 1.0E-6);
   }

   @Test
   void sprintSelectsTheRunClip() {
      ReinhardPoses.Input sprint = new ReinhardPoses.Input(ReinhardMode.RESTRAINED, ReinhardFlags.withMode(0, ReinhardMode.RESTRAINED), -1, 0, 0, 0.5F,
         0.55F, 3.0F, 12.5, net.minecraft.world.entity.Pose.STANDING, false, 0, 0.0, true);
      assertTrue(ReinhardPoses.pose(sprint).run());
      assertFalse(ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, -1, 0, 0, 0.55F)).run(), "walking is not running");
      // Sprint held while standing still: no locomotion weight, so no run clip either.
      ReinhardPoses.Input idleSprint = new ReinhardPoses.Input(ReinhardMode.RESTRAINED, ReinhardFlags.withMode(0, ReinhardMode.RESTRAINED), -1, 0, 0,
         0.5F, 0.0F, 3.0F, 12.5, net.minecraft.world.entity.Pose.STANDING, false, 0, 0.0, true);
      assertFalse(ReinhardPoses.pose(idleSprint).run());
   }

   @Test
   void stanceFollowsMode() {
      assertEquals("idle_restrained", ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, -1, 0, 0, 0.0F)).stance());
      assertEquals("idle_knight", ReinhardPoses.pose(input(ReinhardMode.KNIGHT, -1, 0, 0, 0.0F)).stance());
      assertEquals("idle_holy", ReinhardPoses.pose(input(ReinhardMode.HOLY_SWORD, -1, 0, 0, 0.0F)).stance());
   }

   @Test
   void overlayFromAction() {
      int id = dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.LIGHT_PUNCH.ordinal();
      ReinhardPoses.Clip clip = ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, id, 4, 10, 0.0F)).overlay();
      assertNotNull(clip);
      assertEquals("light_punch", clip.animation());
      assertEquals((4 + 0.5) / 20.0, clip.seconds(), 1.0E-9);
      ReinhardPoses.Clip late = ReinhardPoses.pose(input(ReinhardMode.RESTRAINED, id, 12, 10, 0.0F)).overlay();
      assertEquals(10 / 20.0, late.seconds(), 1.0E-9, "clamped to the action length");
   }

   @Test
   void bodyStateFromVanillaPose() {
      ReinhardPoses.Input crouch = new ReinhardPoses.Input(ReinhardMode.RESTRAINED, 0, -1, 0, 0, 0.0F, 0.0F, 0.0F, 0.0,
         net.minecraft.world.entity.Pose.CROUCHING, false);
      assertEquals(ReinhardPoses.BodyState.CROUCH, ReinhardPoses.pose(crouch).body());
      ReinhardPoses.Input ride = new ReinhardPoses.Input(ReinhardMode.RESTRAINED, 0, -1, 0, 0, 0.0F, 0.0F, 0.0F, 0.0,
         net.minecraft.world.entity.Pose.STANDING, true);
      assertEquals(ReinhardPoses.BodyState.SIT, ReinhardPoses.pose(ride).body());
   }

   @Test
   void passesInvisibleGlowingOnlyOutline() {
      ReinhardPoses.Passes passes = ReinhardPoses.passes(true, false, true);
      assertFalse(passes.body());
      assertTrue(passes.outline());
      assertFalse(passes.extras());
      ReinhardPoses.Passes none = ReinhardPoses.passes(true, false, false);
      assertFalse(none.body() || none.outline() || none.extras(), "invisible, not glowing: nothing, and still no vanilla body");
   }

   @Test
   void passesInvisibleSpectatorTranslucent() {
      ReinhardPoses.Passes passes = ReinhardPoses.passes(true, true, false);
      assertTrue(passes.body());
      assertTrue(passes.translucent());
      assertFalse(passes.extras());
      assertFalse(passes.outline());
   }

   @Test
   void passesVisibleAll() {
      ReinhardPoses.Passes passes = ReinhardPoses.passes(false, true, true);
      assertTrue(passes.body());
      assertFalse(passes.translucent());
      assertTrue(passes.extras());
      assertFalse(passes.outline(), "a visible glowing body outlines through the outline buffer source itself");
   }

   @Test
   void heldItemHiddenOnReidHand() {
      assertTrue(ReinhardPoses.drawsHeldItem(HumanoidArm.RIGHT, ReinhardPoses.ReidPhase.SHEATHED));
      for (ReinhardPoses.ReidPhase phase : new ReinhardPoses.ReidPhase[]{ReinhardPoses.ReidPhase.DRAWING, ReinhardPoses.ReidPhase.DRAWN, ReinhardPoses.ReidPhase.SHEATHING}) {
         assertFalse(ReinhardPoses.drawsHeldItem(HumanoidArm.RIGHT, phase), phase.name());
         assertTrue(ReinhardPoses.drawsHeldItem(HumanoidArm.LEFT, phase), phase.name());
      }
      assertEquals(ReinhardPoses.ReidPhase.DRAWN, ReinhardPoses.reidPhase(1 << ReinhardFlags.REID_DRAWN));
      assertEquals(ReinhardPoses.ReidPhase.SHEATHED, ReinhardPoses.reidPhase(0));
   }

   @Test
   void knightHidesOffArmInFirstPerson() {
      assertFalse(ReinhardPoses.firstPersonArmVisible(ReinhardMode.KNIGHT, HumanoidArm.LEFT, 0), "the left arm is behind the back");
      assertTrue(ReinhardPoses.firstPersonArmVisible(ReinhardMode.KNIGHT, HumanoidArm.RIGHT, 0));
   }

   @Test
   void restrainedShowsBoth() {
      assertTrue(ReinhardPoses.firstPersonArmVisible(ReinhardMode.RESTRAINED, HumanoidArm.LEFT, 0));
      assertTrue(ReinhardPoses.firstPersonArmVisible(ReinhardMode.RESTRAINED, HumanoidArm.RIGHT, 0));
   }

   @Test
   void holyShowsBoth() {
      assertTrue(ReinhardPoses.firstPersonArmVisible(ReinhardMode.HOLY_SWORD, HumanoidArm.LEFT, 0));
      assertTrue(ReinhardPoses.firstPersonArmVisible(ReinhardMode.HOLY_SWORD, HumanoidArm.RIGHT, 0));
   }

   @Test
   void wantsOffhandOnlyWhenVisible() {
      assertTrue(ReinhardPoses.wantsOffhand(ReinhardMode.RESTRAINED, HumanoidArm.LEFT, 0));
      assertTrue(ReinhardPoses.wantsOffhand(ReinhardMode.HOLY_SWORD, HumanoidArm.LEFT, 0));
      assertFalse(ReinhardPoses.wantsOffhand(ReinhardMode.KNIGHT, HumanoidArm.LEFT, 0));
      assertFalse(ReinhardPoses.wantsOffhand(ReinhardMode.KNIGHT, HumanoidArm.RIGHT, 0), "Knight never forces the empty off hand");
      assertFalse(ReinhardPoses.wantsOffhand(null, HumanoidArm.LEFT, 0), "other hero");
   }

   @Test
   void bodyStateFromPose() {
      net.minecraft.world.entity.Pose[] poses = {net.minecraft.world.entity.Pose.CROUCHING, net.minecraft.world.entity.Pose.SLEEPING,
         net.minecraft.world.entity.Pose.SWIMMING, net.minecraft.world.entity.Pose.FALL_FLYING, net.minecraft.world.entity.Pose.STANDING,
         net.minecraft.world.entity.Pose.SPIN_ATTACK};
      ReinhardPoses.BodyState[] states = {ReinhardPoses.BodyState.CROUCH, ReinhardPoses.BodyState.SLEEP, ReinhardPoses.BodyState.SWIM,
         ReinhardPoses.BodyState.FALL_FLY, ReinhardPoses.BodyState.STAND, ReinhardPoses.BodyState.STAND};
      for (int i = 0; i < poses.length; i++) {
         assertEquals(states[i], ReinhardPoses.body(poses[i], false), poses[i].name());
      }
      assertEquals(ReinhardPoses.BodyState.SIT, ReinhardPoses.body(net.minecraft.world.entity.Pose.STANDING, true));
   }

   @Test
   void swimPitchLikeVanilla() {
      // PlayerRenderer.setupRotations: lerp(swimAmount, 0, inWater ? -90 - xRot : -90).
      assertEquals(0.0F, ReinhardPoses.swimPitch(0.0F, true, 30.0F), 1.0E-6F);
      assertEquals(-120.0F, ReinhardPoses.swimPitch(1.0F, true, 30.0F), 1.0E-6F);
      assertEquals(-45.0F, ReinhardPoses.swimPitch(0.5F, false, 30.0F), 1.0E-6F, "crawling on land: -90 whatever the look");
   }

   @Test
   void elytraPitchLikeVanilla() {
      // PlayerRenderer.setupRotations: clamp(t^2 / 100, 0, 1) * (-90 - xRot), none during a riptide spin.
      assertEquals(0.0F, ReinhardPoses.elytraPitch(0.0F, 10.0F, false), 1.0E-6F);
      assertEquals(-25.0F, ReinhardPoses.elytraPitch(5.0F, 10.0F, false), 1.0E-6F);
      assertEquals(-100.0F, ReinhardPoses.elytraPitch(20.0F, 10.0F, false), 1.0E-6F);
      assertEquals(0.0F, ReinhardPoses.elytraPitch(20.0F, 10.0F, true), 1.0E-6F);
   }

   @Test
   void sleepYawFromBed() {
      // LivingEntityRenderer.sleepDirectionToRotation.
      assertEquals(90.0F, ReinhardPoses.sleepYaw(net.minecraft.core.Direction.SOUTH), 1.0E-6F);
      assertEquals(0.0F, ReinhardPoses.sleepYaw(net.minecraft.core.Direction.WEST), 1.0E-6F);
      assertEquals(270.0F, ReinhardPoses.sleepYaw(net.minecraft.core.Direction.NORTH), 1.0E-6F);
      assertEquals(180.0F, ReinhardPoses.sleepYaw(net.minecraft.core.Direction.EAST), 1.0E-6F);
   }
}

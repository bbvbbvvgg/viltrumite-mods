package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.AirJumpRules;
import dev.baranhan.viltrumitecore.hero.HeroAirJump;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.CloudStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Divine Protection of the Cloudy Sky (spec §4.4). */
class CloudStepTest {
   /** A walking-off-a-ledge body: airborne, Reinhard's numbers. */
   private static final class Body implements HeroAirJump.Body {
      int used;
      boolean onGround;
      boolean onLadder;
      Vec3 delta = Vec3.ZERO;

      @Override
      public int used() {
         return this.used;
      }

      @Override
      public void setUsed(int used) {
         this.used = used;
      }

      @Override
      public int max() {
         return ReinhardHero.INSTANCE.airJumps(null);
      }

      @Override
      public double velocity() {
         return ReinhardHero.INSTANCE.airJumpVelocity(null);
      }

      @Override
      public boolean resetState() {
         return AirJumpRules.resets(this.onGround, false, false, this.onLadder, false, false);
      }

      @Override
      public boolean anchored() {
         return false;
      }

      @Override
      public boolean canAct() {
         return true;
      }

      @Override
      public Vec3 deltaMovement() {
         return this.delta;
      }

      @Override
      public void setDeltaMovement(Vec3 velocity) {
         this.delta = velocity;
      }

      @Override
      public void resetFall() {
      }

      /** The player tick reset of HeroAirJump. */
      void tick() {
         if (this.resetState()) {
            this.used = 0;
         }
      }
   }

   private static final HeroAirJump.Sink QUIET = new HeroAirJump.Sink() {
      @Override
      public void stepped(HeroAirJump.Body body) {
      }

      @Override
      public void motion(HeroAirJump.Body body, Vec3 velocity) {
      }

      @Override
      public void sync(HeroAirJump.Body body, int used) {
      }
   };

   @Test
   void unlimitedSteps() {
      assertEquals(Integer.MAX_VALUE, CloudStep.steps());
      assertEquals(Integer.MAX_VALUE, ReinhardHero.INSTANCE.airJumps(null));
      Body body = new Body();
      for (int i = 0; i < 500; i++) {
         assertTrue(HeroAirJump.press(body, QUIET), "step " + i);
         assertEquals(0.62, body.delta.y, 1.0E-9);
      }
   }

   @Test
   void groundOrClimbResetsAndRefusesWhileThere() {
      Body body = new Body();
      for (int i = 0; i < 12; i++) {
         HeroAirJump.press(body, QUIET);
      }

      body.onLadder = true;
      body.tick();
      assertEquals(0, body.used, "the ladder refills the count");
      assertFalse(HeroAirJump.press(body, QUIET), "no step while on the ladder");
      body.onLadder = false;
      assertTrue(HeroAirJump.press(body, QUIET), "a step after leaving the ladder");
      body.onGround = true;
      body.tick();
      assertEquals(0, body.used);
      assertFalse(HeroAirJump.press(body, QUIET), "the ground jump is vanilla's, not a step");
   }

   @Test
   void stepFollowsTheInputAndBuildsSpeed() {
      // Yaw 0 faces +Z; forward input from a standstill.
      Vec3 v = CloudStep.step(Vec3.ZERO, 0.0F, 0.62, 0.0F, 1.0F);
      assertEquals(0.0, v.x, 1.0E-9);
      assertEquals(ReinhardRules.AIR_STEP_ACCEL, v.z, 1.0E-9);
      assertEquals(0.62, v.y, 1.0E-9);
      double previous = v.z;
      for (int i = 0; i < 10; i++) {
         v = CloudStep.step(v, 0.0F, 0.62, 0.0F, 1.0F);
         assertTrue(v.z >= previous - 1.0E-9, "speed builds or holds at the cap");
         previous = v.z;
      }
      assertEquals(ReinhardRules.AIR_STEP_MAX_SPEED, v.z, 1.0E-9, "capped");
      // Strafe right (left impulse negative) with yaw 0 goes toward -X.
      Vec3 side = CloudStep.step(Vec3.ZERO, 0.0F, 0.62, -1.0F, 0.0F);
      assertTrue(side.x < 0.0 && Math.abs(side.z) < 1.0E-9, "strafe " + side);
   }

   @Test
   void turnsBendInsteadOfSnapping() {
      Vec3 fast = new Vec3(0.0, -0.3, ReinhardRules.AIR_STEP_MAX_SPEED);
      // Hard left (+X at yaw 0 is the left impulse): the old forward momentum is kept in part.
      Vec3 turned = CloudStep.step(fast, 0.0F, 0.62, 1.0F, 0.0F);
      assertTrue(turned.z > 0.3, "keeps part of the old heading " + turned);
      assertTrue(turned.x > 0.3, "already bends into the new heading " + turned);
      // Reversal: the first step brakes, the next ones accelerate the other way.
      Vec3 back = CloudStep.step(fast, 0.0F, 0.62, 0.0F, -1.0F);
      assertTrue(back.z < 0.2 && back.z > -0.2, "braked, not snapped " + back);
      Vec3 again = CloudStep.step(back, 0.0F, 0.62, 0.0F, -1.0F);
      assertTrue(again.z < back.z - 0.2, "then turns " + again);
      // No input: coasts on.
      Vec3 coast = CloudStep.step(fast, 0.0F, 0.62, 0.0F, 0.0F);
      assertEquals(ReinhardRules.AIR_STEP_MAX_SPEED * ReinhardRules.AIR_STEP_COAST_KEEP, coast.z, 1.0E-9);
   }

   @Test
   void airStepsAreNotFlight() {
      assertTrue(ReinhardHero.INSTANCE.sustainsAirborne(null), "the floating kick never counts air steps");
      assertFalse(ReinhardHero.INSTANCE.allowsFlight(null), "no vanilla or mod flight");
      assertEquals(ReinhardRules.AIR_STEP_INTERVAL, ReinhardHero.INSTANCE.airJumpInterval(null));
      assertTrue(ReinhardHero.INSTANCE.airControl(null) > 0.02F, "more air control than vanilla");
   }

   @Test
   void superJumpApexAbout20() {
      assertEquals(2.2F, ReinhardHero.INSTANCE.superJumpVelocity(null));
      double apex = CloudStep.superJumpHeight();
      assertTrue(apex >= 18.0 && apex <= 24.0, "apex " + apex);
   }

   @Test
   void stepHeightAbout2() {
      double height = CloudStep.stepHeight();
      assertTrue(height >= 1.8 && height <= 2.6, "step " + height);
   }
}

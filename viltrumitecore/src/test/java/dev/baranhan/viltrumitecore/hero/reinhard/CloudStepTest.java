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
   void fiveSteps() {
      assertEquals(5, CloudStep.steps());
      assertEquals(5, ReinhardHero.INSTANCE.airJumps(null));
      Body body = new Body();
      for (int i = 0; i < 5; i++) {
         assertTrue(HeroAirJump.press(body, QUIET));
         assertEquals(0.62, body.delta.y, 1.0E-9);
      }

      assertFalse(HeroAirJump.press(body, QUIET));
   }

   @Test
   void groundOrClimbResets() {
      Body body = new Body();
      for (int i = 0; i < 5; i++) {
         HeroAirJump.press(body, QUIET);
      }

      body.onLadder = true;
      body.tick();
      assertFalse(HeroAirJump.press(body, QUIET), "no step while on the ladder");
      body.onLadder = false;
      assertTrue(HeroAirJump.press(body, QUIET), "6th step after touching a ladder");
      for (int i = 0; i < 4; i++) {
         HeroAirJump.press(body, QUIET);
      }

      assertFalse(HeroAirJump.press(body, QUIET));
      body.onGround = true;
      body.tick();
      body.onGround = false;
      assertTrue(HeroAirJump.press(body, QUIET));
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

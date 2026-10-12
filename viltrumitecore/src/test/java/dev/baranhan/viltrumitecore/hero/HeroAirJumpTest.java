package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Air jump seam, server side (stage 2 Task 4): validate → confirm / correct. */
class HeroAirJumpTest {
   static final class FakeBody implements HeroAirJump.Body {
      int used;
      int max = 5;
      double velocity = 0.62;
      boolean reset;
      boolean anchored;
      boolean canAct = true;
      Vec3 delta = new Vec3(0.2, -0.5, 0.1);
      boolean fallReset;

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
         return this.max;
      }

      @Override
      public double velocity() {
         return this.velocity;
      }

      @Override
      public boolean resetState() {
         return this.reset;
      }

      @Override
      public boolean anchored() {
         return this.anchored;
      }

      @Override
      public boolean canAct() {
         return this.canAct;
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
         this.fallReset = true;
      }
   }

   static final class FakeSink implements HeroAirJump.Sink {
      final List<String> sent = new ArrayList<>();
      Vec3 motion;
      int synced = -1;

      @Override
      public void stepped(HeroAirJump.Body body) {
         this.sent.add("fx");
      }

      @Override
      public void motion(HeroAirJump.Body body, Vec3 velocity) {
         this.sent.add("motion");
         this.motion = velocity;
      }

      @Override
      public void sync(HeroAirJump.Body body, int used) {
         this.sent.add("sync");
         this.synced = used;
      }
   }

   @Test
   void refusesAboveMax() {
      FakeBody body = new FakeBody();
      body.used = 5;
      FakeSink sink = new FakeSink();
      assertFalse(HeroAirJump.press(body, sink));
      assertEquals(5, body.used);
   }

   @Test
   void refusesAnchored() {
      FakeBody body = new FakeBody();
      body.anchored = true;
      FakeSink sink = new FakeSink();
      assertFalse(HeroAirJump.press(body, sink));
      assertEquals(0, body.used);
      body.anchored = false;
      body.canAct = false;
      assertFalse(HeroAirJump.press(body, sink));
   }

   @Test
   void refusalSendsCorrection() {
      FakeBody body = new FakeBody();
      body.used = 5;
      body.delta = new Vec3(0.3, 0.62, -0.1);
      FakeSink sink = new FakeSink();
      HeroAirJump.press(body, sink);
      assertEquals(List.of("motion", "sync"), sink.sent);
      // The predicted boost is cancelled: no upward velocity, horizontal kept.
      assertEquals(new Vec3(0.3, 0.0, -0.1), sink.motion);
      assertEquals(5, sink.synced);
      assertFalse(body.fallReset);
   }

   @Test
   void acceptedSetsServerVelocity() {
      FakeBody body = new FakeBody();
      FakeSink sink = new FakeSink();
      assertTrue(HeroAirJump.press(body, sink));
      assertEquals(new Vec3(0.2, 0.62, 0.1), body.delta);
      assertEquals(1, body.used);
      assertTrue(body.fallReset);
      // Confirmed without a motion packet (the client already moved; a late one would boost twice).
      assertEquals(List.of("fx"), sink.sent);
   }

   @Test
   void sixthPacketRefused() {
      FakeBody body = new FakeBody();
      FakeSink sink = new FakeSink();
      for (int i = 0; i < 5; i++) {
         assertTrue(HeroAirJump.press(body, sink), "step " + (i + 1));
      }

      assertFalse(HeroAirJump.press(body, sink));
      assertEquals(5, body.used);
      assertEquals(List.of("fx", "fx", "fx", "fx", "fx", "motion", "sync"), sink.sent);
   }

   @Test
   void groundPressRefused() {
      FakeBody body = new FakeBody();
      body.reset = true;
      assertFalse(HeroAirJump.press(body, new FakeSink()));
   }

   @Test
   void otherHeroesHaveNoAirJumps() {
      HeroDefinition plain = new HumanHero();
      assertEquals(0, plain.airJumps(null));
      assertEquals(0.0, plain.airJumpVelocity(null));
   }
}

package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.LiftLeases;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.GravityLift;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Stage 4 Task 3: the draw event's slow lift (spec §7.3.3, review focus 1–2). */
class GravityLiftTest {
   static final class Body implements GravityLift.Liftee {
      final UUID id = UUID.randomUUID();
      boolean noGravity;
      double y;
      double vy;
      boolean nudged;
      final CompoundTag tag = new CompoundTag();

      Body(boolean noGravity, double y) {
         this.noGravity = noGravity;
         this.y = y;
      }

      @Override
      public UUID id() {
         return this.id;
      }

      @Override
      public boolean noGravity() {
         return this.noGravity;
      }

      @Override
      public void setNoGravity(boolean value) {
         this.noGravity = value;
      }

      @Override
      public double y() {
         return this.y;
      }

      @Override
      public CompoundTag persistentData() {
         return this.tag;
      }

      @Override
      public void liftVelocity(double v) {
         this.vy = v;
      }

      @Override
      public void nudgeDown() {
         this.nudged = true;
      }

      /** One server tick of movement: the set velocity moves it (no gravity while lifted). */
      void move() {
         this.y += this.vy;
      }
   }

   @Test
   void riseNeverAboveMax() {
      assertEquals(2.5, ReinhardRules.REID_LIFT_MAX, 1.0E-9);
      assertEquals(96, ReinhardRules.REID_LIFT_RADIUS);
      for (int t = -5; t <= 300; t++) {
         assertTrue(GravityLift.rise(t) <= ReinhardRules.REID_LIFT_MAX + 1.0E-9, "t " + t);
         assertTrue(GravityLift.rise(t) >= 0.0, "t " + t);
      }
   }

   @Test
   void riseReachesAboutMax() {
      double offset = 0.0;
      for (int t = 0; t < ReinhardRules.REID_DRAW_TICKS; t++) {
         offset += GravityLift.velocity(t, offset, offset);
      }

      assertTrue(offset > 2.4 && offset <= 2.5 + 1.0E-9, "offset " + offset);
   }

   @Test
   void velocityNonNegative() {
      for (int t = 0; t < 130; t++) {
         for (double offset = 0.0; offset < 4.0; offset += 0.25) {
            double v = GravityLift.velocity(t, offset, offset);
            assertTrue(v >= 0.0 && v <= 0.1 + 1.0E-9, "t " + t + " offset " + offset + " v " + v);
         }
      }
   }

   @Test
   void restoreOnEveryEnd() {
      LiftLeases leases = new LiftLeases();
      Body cow = new Body(false, 64.0);
      Body vex = new Body(true, 70.0);
      GravityLift lift = new GravityLift(UUID.randomUUID(), leases);
      Map<UUID, Body> world = new HashMap<>();
      world.put(cow.id, cow);
      world.put(vex.id, vex);
      for (int t = 0; t < 30; t++) {
         for (Body body : List.of(cow, vex)) {
            lift.step(t, body);
            body.move();
         }
      }

      assertTrue(cow.noGravity);
      assertTrue(cow.y > 64.0);
      lift.end(world::get);
      assertFalse(cow.noGravity, "abort at t 30 restores the original flag");
      assertTrue(vex.noGravity, "a vex keeps floating");
      assertTrue(cow.nudged);
      assertTrue(vex.nudged);
      assertFalse(leases.holds(cow.id));
      assertEquals(0, lift.tracked());
   }

   @Test
   void overlapCapsAtMax() {
      LiftLeases leases = new LiftLeases();
      Body cow = new Body(false, 64.0);
      GravityLift first = new GravityLift(UUID.randomUUID(), leases);
      GravityLift second = new GravityLift(UUID.randomUUID(), leases);
      Map<UUID, Body> world = Map.of(cow.id, cow);
      double highest = 0.0;
      for (int tick = 0; tick < 240; tick++) {
         double v = 0.0;
         if (tick < 120) {
            first.step(tick, cow);
            v = Math.max(v, cow.vy);
         } else if (tick == 120) {
            first.end(world::get);
         }

         if (tick >= 60 && tick < 180) {
            second.step(tick - 60, cow);
            v = Math.max(v, cow.vy);
         } else if (tick == 180) {
            second.end(world::get);
         }

         if (tick == 120) {
            assertTrue(cow.noGravity, "the first event ended: still floating for the second");
            assertFalse(cow.nudged, "no nudge while a lease remains");
         }

         if (leases.holds(cow.id)) {
            cow.y += v;
         }

         highest = Math.max(highest, cow.y - 64.0);
      }

      assertTrue(highest <= ReinhardRules.REID_LIFT_CAP + 1.0E-9, "offset " + highest);
      assertEquals(3.0, ReinhardRules.REID_LIFT_CAP, 1.0E-9);
      assertFalse(cow.noGravity, "original false after both ended");
      assertTrue(cow.nudged);
   }

   @Test
   void lateEntrantStartsFromItsY() {
      LiftLeases leases = new LiftLeases();
      GravityLift lift = new GravityLift(UUID.randomUUID(), leases);
      Body late = new Body(false, 80.0);
      lift.step(100, late);
      assertTrue(late.vy <= 0.1 + 1.0E-9);
      assertEquals(80.0, lift.startY(late.id), 1.0E-9);
   }
}

package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LegionSteeringTest {
   @Test
   void accelerationIsLimited() {
      Vec3 v = LegionSteering.arrive(Vec3.ZERO, Vec3.ZERO, new Vec3(100, 0, 0), 1.0, 0.1, 6.0);
      assertEquals(0.1, v.length(), 1.0E-6);
      // Braking near the goal: the speed falls smoothly instead of overshooting.
      Vec3 fast = new Vec3(1.0, 0, 0);
      Vec3 braked = LegionSteering.arrive(new Vec3(99, 0, 0), fast, new Vec3(100, 0, 0), 1.0, 0.1, 6.0);
      assertTrue(braked.x < fast.x && braked.x > 0.8);
   }

   @Test
   void reachesTheGoalWithoutOscillating() {
      Vec3 pos = Vec3.ZERO;
      Vec3 vel = Vec3.ZERO;
      Vec3 goal = new Vec3(30, 5, -10);
      double maxOvershoot = 0.0;
      for (int i = 0; i < 400; i++) {
         vel = LegionSteering.arrive(pos, vel, goal, 0.9, 0.08, 6.0);
         pos = pos.add(vel);
         if (i > 200) {
            maxOvershoot = Math.max(maxOvershoot, pos.distanceTo(goal));
         }
      }

      assertTrue(pos.distanceTo(goal) < 0.5);
      assertTrue(maxOvershoot < 1.5);
   }

   @Test
   void avoidsWallAhead() {
      Vec3 push = LegionSteering.avoid(new Vec3(0, 0, 1), 1.0, 6.0, 1.0, 8.0, 6.0);
      assertTrue(push.y > 0.3, "climbs over a wall with room above");
      Vec3 noRoof = LegionSteering.avoid(new Vec3(0, 0, 1), 1.0, 6.0, 1.0, 0.5, 6.0);
      Vec3 left = LegionSteering.right(new Vec3(0, 0, 1)).scale(-1.0);
      assertTrue(noRoof.dot(left) > 0.3, "turns to the open side under a roof");
      assertEquals(0.0, LegionSteering.avoid(new Vec3(0, 0, 1), 6.0, 6.0, 6.0, 6.0, 6.0).length(), 1.0E-9);
   }

   @Test
   void formationSlotsAreDistinct() {
      for (LegionOrder order : new LegionOrder[]{LegionOrder.FOLLOW, LegionOrder.GUARD, LegionOrder.HOLD}) {
         Set<String> seen = new HashSet<>();
         for (int i = 0; i < 3; i++) {
            Vec3 slot = LegionSteering.slot(order, i, 3, Vec3.ZERO, 30.0F, 100.0);
            for (int j = 0; j < i; j++) {
               assertTrue(slot.distanceTo(LegionSteering.slot(order, j, 3, Vec3.ZERO, 30.0F, 100.0)) > 2.0, order + " " + i + "/" + j);
            }

            seen.add(String.format("%.1f %.1f %.1f", slot.x, slot.y, slot.z));
         }

         assertEquals(3, seen.size());
      }

      // FOLLOW stays behind Tony.
      Vec3 fwd = LegionSteering.forward(0.0F);
      assertTrue(LegionSteering.slot(LegionOrder.FOLLOW, 0, 3, Vec3.ZERO, 0.0F, 0.0).dot(fwd) < 0.0);
   }

   @Test
   void arrivalCurvesComeFromDifferentSidesAndEndOnTheSlot() {
      Vec3 slot = new Vec3(10, 64, 10);
      Vec3[] starts = new Vec3[3];
      for (int i = 0; i < 3; i++) {
         LegionSteering.Arrival a = LegionSteering.arrival(i, slot, 45.0F);
         starts[i] = a.at(0.0);
         assertTrue(a.at(1.0).distanceTo(slot) < 1.0E-9);
         assertTrue(starts[i].y > slot.y + 20.0);
      }

      assertTrue(starts[0].distanceTo(starts[1]) > 20.0 && starts[1].distanceTo(starts[2]) > 20.0 && starts[0].distanceTo(starts[2]) > 20.0);
      assertEquals(0.0, LegionSteering.arrivalEase(0.0), 1.0E-9);
      assertEquals(1.0, LegionSteering.arrivalEase(1.0), 1.0E-9);
      assertTrue(LegionSteering.arrivalEase(0.5) > 0.5);
   }

   @Test
   void projectileDodge() {
      Vec3 suit = new Vec3(0, 0, 10);
      assertTrue(LegionSteering.closestApproach(suit, Vec3.ZERO, new Vec3(0, 0, 2), LegionRules.DODGE_HORIZON) < 0.01);
      assertTrue(LegionSteering.closestApproach(suit, Vec3.ZERO, new Vec3(2, 0, 0), LegionRules.DODGE_HORIZON) > 9.0);
      Vec3 step = LegionSteering.dodge(new Vec3(0, 0, 2), true);
      assertTrue(Math.abs(step.x) > 0.8 && Math.abs(step.z) < 1.0E-9);
   }

   @Test
   void separationPushesApart() {
      Vec3 push = LegionSteering.separation(Vec3.ZERO, List.of(new Vec3(1, 0, 0)), 3.0);
      assertTrue(push.x < 0.0);
      assertEquals(0.0, LegionSteering.separation(Vec3.ZERO, List.of(new Vec3(10, 0, 0)), 3.0).length(), 1.0E-9);
   }

   @Test
   void combatSpotsDependOnRole() {
      Vec3 target = new Vec3(0, 64, 0);
      Vec3 owner = new Vec3(-10, 64, 0);
      Vec3 front = LegionSteering.combatSpot(LegionRole.VANGUARD, target, owner, owner, 8.0, 2.0, 1, 0.0);
      Vec3 top = LegionSteering.combatSpot(LegionRole.OVERWATCH, target, owner, owner, 8.0, 2.0, 1, 0.0);
      Vec3 flank = LegionSteering.combatSpot(LegionRole.FLANKER, target, owner, owner, 8.0, 2.0, 1, 0.0);
      Vec3 guard = LegionSteering.combatSpot(LegionRole.GUARDIAN, target, owner, owner, 8.0, 2.0, 1, 0.0);
      assertTrue(front.x < 0.0, "vanguard between Tony and the target");
      assertTrue(top.y > front.y + 4.0, "overwatch above");
      assertTrue(Math.abs(flank.z) > 5.0, "flanker on the side");
      assertTrue(guard.distanceTo(owner) < 3.5, "guardian at Tony's side");
   }

   @Test
   void turnIsBounded() {
      assertEquals(10.0F, LegionSteering.turn(0.0F, 90.0F, 10.0F), 1.0E-5);
      assertEquals(-10.0F, LegionSteering.turn(0.0F, 270.0F, 10.0F), 1.0E-5);
      assertEquals(170.0F, LegionSteering.wrap(-190.0F), 1.0E-5);
   }

   @Test
   void scheduleSpreadsTheWork() {
      int due0 = 0;
      int due1 = 0;
      int together = 0;
      for (int t = 0; t < 100; t++) {
         boolean a = LegionRules.brainDue(t, 0);
         boolean b = LegionRules.brainDue(t, 1);
         due0 += a ? 1 : 0;
         due1 += b ? 1 : 0;
         together += a && b ? 1 : 0;
      }

      assertEquals(50, due0);
      assertEquals(50, due1);
      assertEquals(0, together);
      int squad = 0;
      for (long t = 0; t < 100; t++) {
         squad += LegionRules.squadDue(t, 12345) ? 1 : 0;
      }

      assertEquals(100 / LegionRules.SQUAD_PERIOD, squad);
   }
}

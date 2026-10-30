package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Owner playtest 2026-10-28: Mjölnir must not jump into the hand; PICKUP bends, grips, then heaves. */
class MjolnirPickupTest {
   @Test
   void theStepEndsAtTheGripStand() {
      Vec3 feet = new Vec3(0.0, 64.0, 0.0);
      Vec3 hammer = new Vec3(0.0, 64.0, 2.4);
      Vec3 v = MjolnirWorld.pickupStep(feet, hammer);
      // A grounded body travels about v / (1 - friction): it stops PICKUP_STAND short of the handle.
      double travel = v.length() / (1.0 - MjolnirTuning.PICKUP_STEP_FRICTION);
      assertEquals(2.4 - MjolnirTuning.PICKUP_STAND, travel, 1.0E-6);
      assertTrue(v.z > 0.0 && v.x == 0.0 && v.y == 0.0);
   }

   @Test
   void alreadyAtTheHandleNoStep() {
      Vec3 feet = new Vec3(1.0, 64.0, 1.0);
      assertEquals(Vec3.ZERO, MjolnirWorld.pickupStep(feet, feet.add(0.0, 0.0, MjolnirTuning.PICKUP_STAND)));
      assertEquals(Vec3.ZERO, MjolnirWorld.pickupStep(feet, feet));
   }

   @Test
   void theHammerChangesHandsOnTheContactTickNotTheClick() {
      MjolnirModule.registerMoves();
      assertEquals(MjolnirTuning.PICKUP_REACH, ThorMove.PICKUP.ticks(MovePhase.INTRO));
      assertEquals(MjolnirTuning.PICKUP_HEAVE, ThorMove.PICKUP.ticks(MovePhase.MAIN));
      assertEquals(MjolnirTuning.PICKUP_SETTLE, ThorMove.PICKUP.ticks(MovePhase.FINISH));
      assertTrue(MjolnirTuning.PICKUP_REACH >= 6, "the bend needs the lift blend's 0.3 s before the hands close");
   }
}

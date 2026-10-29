package dev.baranhan.viltrumitecore.hero.thor.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** C-NIT8: a rehearsal body lowered into the floor stands back up on it, never higher, never into blocks. */
class SceneSpaceStandUpTest {
   @Test
   void kneelingBodyRisesOntoItsFloor() {
      Vec3 sunk = new Vec3(0.5, 63.15, 0.5);
      Vec3 top = new Vec3(0.5, 64.0, 0.5);
      assertEquals(top, SceneSpace.standUp(sunk, top, true));
   }

   @Test
   void blockedOrTooHighStaysPut() {
      Vec3 sunk = new Vec3(0.5, 63.15, 0.5);
      assertEquals(sunk, SceneSpace.standUp(sunk, new Vec3(0.5, 64.0, 0.5), false), "the box above is not free");
      assertEquals(sunk, SceneSpace.standUp(sunk, new Vec3(0.5, 65.5, 0.5), true), "never a lift over a ledge");
      assertEquals(sunk, SceneSpace.standUp(sunk, new Vec3(0.5, 62.0, 0.5), true), "never down");
   }
}

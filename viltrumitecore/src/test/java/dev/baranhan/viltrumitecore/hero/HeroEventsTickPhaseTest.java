package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraftforge.event.TickEvent;
import org.junit.jupiter.api.Test;

/** Reinhard stage 4 Task 8: ControlManager keeps START, block work runs on END. */
class HeroEventsTickPhaseTest {
   @Test
   void controlOnStartCarveOnEnd() {
      assertEquals(List.of(LevelTickPhases.System.CONTROL), LevelTickPhases.systemsFor(TickEvent.Phase.START));
      List<LevelTickPhases.System> end = LevelTickPhases.systemsFor(TickEvent.Phase.END);
      assertTrue(end.contains(LevelTickPhases.System.CARVE));
      assertTrue(end.contains(LevelTickPhases.System.PROJECTILES));
      assertTrue(end.contains(LevelTickPhases.System.RESOURCE_REGEN));
      // The Saw Sphere cuts after the World Cut and before the shared carve queue spends its budget.
      assertTrue(end.indexOf(LevelTickPhases.System.SAW_SPHERE) > end.indexOf(LevelTickPhases.System.WORLD_CUT));
      assertTrue(end.indexOf(LevelTickPhases.System.SAW_SPHERE) < end.indexOf(LevelTickPhases.System.CARVE));
      assertFalse(end.contains(LevelTickPhases.System.CONTROL));
   }
}

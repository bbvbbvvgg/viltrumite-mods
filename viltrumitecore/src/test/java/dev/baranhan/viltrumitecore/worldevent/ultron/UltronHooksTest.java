package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.entity.Homing;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMissile;
import dev.baranhan.viltrumitecore.entity.ultron.UltronNodeEntity;
import dev.baranhan.viltrumitecore.hero.CleanDamageSink;
import org.junit.jupiter.api.Test;

/** The shared hero systems see Ultron's entities: countermeasures his missiles, clean hero damage his nodes. */
class UltronHooksTest {
   @Test
   void flaresAndReflectSeeHisMissiles() {
      assertTrue(Homing.class.isAssignableFrom(UltronMissile.class), "Iron Man's flares and Reflect retarget Homing projectiles");
   }

   @Test
   void cleanHeroDamageReachesTheNodeHull() {
      assertTrue(CleanDamageSink.class.isAssignableFrom(UltronNodeEntity.class), "a node's body health is reset every tick: clean damage must reach the hull");
   }
}

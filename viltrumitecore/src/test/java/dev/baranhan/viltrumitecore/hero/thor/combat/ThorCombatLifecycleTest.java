package dev.baranhan.viltrumitecore.hero.thor.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** B-M2 / B-m3: B's transient state follows the foundation lifecycle; deferred effects never outlive their body or dimension. */
class ThorCombatLifecycleTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
      ThorDevCommands.resetForTests();
      ThorCombatModule.resetForTests();
   }

   @Test
   void aDueEffectRunsOnlyForALivingOnlineThorInItsDimensionOutsideAScene() {
      assertTrue(ThorCombatRuntime.runs(true, true, true, false));
      assertFalse(ThorCombatRuntime.runs(false, true, true, false), "dead");
      assertFalse(ThorCombatRuntime.runs(true, false, true, false), "logged out");
      assertFalse(ThorCombatRuntime.runs(true, true, false, false), "another dimension: never at stale coordinates");
      assertFalse(ThorCombatRuntime.runs(true, true, true, true), "held by a scene");
   }

   @Test
   void aRefusedDueEffectIsDroppedNotKept() {
      ThorCombatState combat = new ThorCombatState();
      List<String> ran = new ArrayList<>();
      combat.schedule(0, () -> ran.add("ring"));
      combat.schedule(5, () -> ran.add("later"));
      ThorCombatRuntime.runScheduled(combat, task -> false);
      assertTrue(ran.isEmpty());
      assertEquals(1, combat.scheduled.size(), "the due one is gone, the later one waits");
      combat.ticks = 5;
      ThorCombatRuntime.runScheduled(combat);
      assertEquals(List.of("later"), ran);
   }

   @Test
   void theLifecycleStopClearsEveryTransientAndForgetsTheBody() {
      ThorCombatModule.install();
      assertTrue(ThorLifecycle.owners().contains(ThorCombatModule.OWNER), "B listens through the foundation hook, not a direct call");
      ThorState state = new ThorState();
      ThorCombatState combat = ThorCombatState.of(state);
      combat.schedule(3, () -> { });
      combat.skyFallback.put(1, Vec3.ZERO);
      combat.stormTicks = 20;
      combat.rmbHeld = true;
      combat.chainMove = ThorMove.HAMMER_COMBO;

      ThorActions.stop(null, state, ThorLifecycle.Cause.DIMENSION_CHANGE);
      assertSame(combat, ThorCombatState.peek(state), "same body after a dimension change");
      assertTrue(combat.scheduled.isEmpty());
      assertTrue(combat.skyFallback.isEmpty());
      assertEquals(-1, combat.stormTicks);
      assertFalse(combat.rmbHeld);
      assertNull(combat.chainMove);

      for (ThorLifecycle.Cause cause : new ThorLifecycle.Cause[]{ThorLifecycle.Cause.DEATH, ThorLifecycle.Cause.DISCONNECT, ThorLifecycle.Cause.HERO_CHANGE,
         ThorLifecycle.Cause.ERASED, ThorLifecycle.Cause.CLONE}) {
         ThorCombatState before = ThorCombatState.of(state);
         before.schedule(1, () -> { });
         ThorActions.stop(null, state, cause);
         assertTrue(before.scheduled.isEmpty(), cause.name());
         assertNull(ThorCombatState.peek(state), cause + ": the body is forgotten (no WeakHashMap cycle through a captured player)");
         assertNotSame(before, ThorCombatState.of(state));
      }
   }
}

package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.CatchWindow;
import org.junit.jupiter.api.Test;

/** Spec §6.1 Catch Projectile (stage 3 Task 4). */
class CatchWindowTest {
   @Test
   void windowTenTicks() {
      assertEquals(10, ReinhardRules.CATCH_WINDOW);
      ReinhardState state = new ReinhardState();
      CatchWindow.open(state);
      int open = 0;
      while (CatchWindow.isOpen(state)) {
         open++;
         CatchWindow.tickWindow(state);
         state.action.tick();
         if (open > 100) {
            break;
         }
      }

      assertEquals(10, open);
      assertTrue(ReinhardFlags.has(ReinhardHero.stateFlags(withWindow(), 0), ReinhardFlags.CATCH_WINDOW), "the window is synced as a flag");
      assertFalse(ReinhardFlags.has(ReinhardHero.stateFlags(state, 0), ReinhardFlags.CATCH_WINDOW));
   }

   private static ReinhardState withWindow() {
      ReinhardState state = new ReinhardState();
      CatchWindow.open(state);
      return state;
   }

   @Test
   void cooldownFortyFromPress() {
      assertEquals(8, ReinhardRules.CATCH_COOLDOWN);
      ReinhardState state = new ReinhardState();
      CatchWindow.open(state);
      assertEquals(8, state.cooldowns[ReinhardAbility.CATCH.ordinal()], "the cooldown starts at the press");
      // A catch inside the window does not restart it.
      CatchWindow.tickWindow(state);
      state.tick();
      CatchWindow.snap(state);
      assertEquals(7, state.cooldowns[ReinhardAbility.CATCH.ordinal()]);
   }

   @Test
   void catchBeatsDeflectInWindow() {
      ReinhardState state = new ReinhardState();
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.projectileResponse(state));
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.projectileResponse((ReinhardState) null), "no state yet = no window");
      CatchWindow.open(state);
      assertEquals(ProjectileResponse.CATCH, ReinhardHero.projectileResponse(state));
      // Several catches: the window stays open after a snap, the snap pose plays.
      CatchWindow.snap(state);
      assertEquals(ProjectileResponse.CATCH, ReinhardHero.projectileResponse(state));
      assertEquals(ActionTimeline.PHASE_SNAP, state.action.phase());
      assertTrue(state.action.length() >= ReinhardRules.CATCH_SNAP_TICKS);
   }

   @Test
   void deflectAfterWindow() {
      ReinhardState state = new ReinhardState();
      CatchWindow.open(state);
      for (int i = 0; i < ReinhardRules.CATCH_WINDOW; i++) {
         CatchWindow.tickWindow(state);
      }

      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.projectileResponse(state));
      // A mode switch / cleanup closes it at once.
      CatchWindow.open(state);
      CatchWindow.close(state);
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.projectileResponse(state));
   }
}

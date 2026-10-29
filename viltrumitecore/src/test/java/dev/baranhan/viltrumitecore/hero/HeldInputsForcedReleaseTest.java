package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** F-M4 / B-M1: a forced release (death, logout, hero change, control) is distinguishable from the player's release. */
class HeldInputsForcedReleaseTest {
   private static final class Sink implements HeldInputs.Sink {
      final List<String> calls = new ArrayList<>();
      final boolean overridesForced;

      Sink(boolean overridesForced) {
         this.overridesForced = overridesForced;
      }

      @Override
      public HeroAction claim(MouseButton button) {
         return button == MouseButton.MIDDLE ? HeroAction.CONTEXT_POWER : null;
      }

      @Override
      public boolean canAct(HeroAction action) {
         return true;
      }

      @Override
      public void handle(HeroAction action, boolean pressed) {
         this.calls.add(action + (pressed ? ":press" : ":release"));
      }

      @Override
      public void forcedRelease(HeroAction action) {
         if (this.overridesForced) {
            this.calls.add(action + ":forced");
         } else {
            HeldInputs.Sink.super.forcedRelease(action);
         }
      }
   }

   @Test
   void releaseAllIsForcedAndThePlayersReleaseIsNot() {
      Sink sink = new Sink(true);
      HeldInputs held = new HeldInputs();
      held.press(MouseButton.MIDDLE, sink);
      held.release(MouseButton.MIDDLE, sink);
      held.press(MouseButton.MIDDLE, sink);
      held.releaseAll(sink);
      held.releaseAll(sink);
      assertEquals(List.of("CONTEXT_POWER:press", "CONTEXT_POWER:release", "CONTEXT_POWER:press", "CONTEXT_POWER:forced"), sink.calls);
      assertTrue(held.isEmpty());
   }

   @Test
   void heroesThatDoNotCareStillSeeAPlainRelease() {
      Sink sink = new Sink(false);
      HeldInputs held = new HeldInputs();
      held.press(MouseButton.MIDDLE, sink);
      held.releaseAll(sink);
      assertEquals(List.of("CONTEXT_POWER:press", "CONTEXT_POWER:release"), sink.calls, "default forcedRelease = handle(action, false)");
   }
}

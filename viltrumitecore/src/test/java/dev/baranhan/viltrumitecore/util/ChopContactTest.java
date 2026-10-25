package dev.baranhan.viltrumitecore.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lethal Chop / Viltrumite chop: a refused hit leaves no bleed, push or hit FX. */
class ChopContactTest {
   private static final class Recorder implements ChopImpactManager.Contact {
      final List<String> calls = new ArrayList<>();
      private final boolean accepted;

      Recorder(boolean accepted) {
         this.accepted = accepted;
      }

      @Override
      public boolean hurt() {
         calls.add("hurt");
         return accepted;
      }

      @Override
      public void push() {
         calls.add("push");
      }

      @Override
      public void bleed() {
         calls.add("bleed");
      }

      @Override
      public void fx() {
         calls.add("fx");
      }
   }

   @Test
   void refusedHitHasNoSideEffects() {
      Recorder contact = new Recorder(false);
      assertFalse(ChopImpactManager.land(contact));
      assertEquals(List.of("hurt"), contact.calls);
   }

   @Test
   void acceptedHitPushesBleedsAndShowsTheHit() {
      Recorder contact = new Recorder(true);
      assertTrue(ChopImpactManager.land(contact));
      assertEquals(List.of("hurt", "push", "bleed", "fx"), contact.calls);
   }
}

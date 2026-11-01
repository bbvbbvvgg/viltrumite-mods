package dev.baranhan.viltrumitecore.client.ultron;

import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;

/** Client reactions to the machines' synced actions (clip starts, loops). */
public final class UltronClientHooks {
   private UltronClientHooks() {
   }

   /** A unit started a timed move: the renderer picks the clip up from {@link UltronUnit#clientMoveStart}. */
   public static void moveStarted(UltronUnit unit) {
      UltronLoops.moveStarted(unit);
   }
}

package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import javax.annotation.Nullable;

/**
 * Read-only snapshot of Mjölnir and Thor's move state for other sessions.
 * A copy: changing it changes nothing. There is one hammer per Thor; this
 * view never creates one.
 */
public record HammerView(
   HammerState hammer,
   ThorMode mode,
   @Nullable ThorMove move,
   @Nullable MovePhase phase,
   int moveElapsed,
   int grabWindowTicks,
   @Nullable String sceneLockOwner
) {
   public boolean grabWindowOpen() {
      return this.grabWindowTicks > 0;
   }

   public boolean sceneLocked() {
      return this.sceneLockOwner != null;
   }
}

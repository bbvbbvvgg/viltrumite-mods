package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import javax.annotation.Nullable;

/** Read-only copy of Thor's move state and bridge flags. */
public record ThorStateView(
   ThorMode mode,
   @Nullable ThorMove move,
   @Nullable MovePhase phase,
   int moveElapsed,
   int grabWindowTicks,
   @Nullable String sceneLockOwner,
   @Nullable String cameraLeaseOwner
) {
   public boolean grabWindowActive() {
      return this.grabWindowTicks > 0;
   }

   public boolean sceneLocked() {
      return this.sceneLockOwner != null;
   }

   public boolean cameraLeased() {
      return this.cameraLeaseOwner != null;
   }
}

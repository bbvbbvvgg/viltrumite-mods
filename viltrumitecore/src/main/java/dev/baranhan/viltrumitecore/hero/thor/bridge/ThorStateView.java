package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import javax.annotation.Nullable;

/**
 * Read-only copy of Thor's move state and bridge flags: the running move with
 * its phase, step, partner and the queued follow-up, the windows, lock and
 * lease owners, and whether Mjölnir is detached from Thor (review m6).
 */
public record ThorStateView(
   ThorMode mode,
   @Nullable ThorMove move,
   @Nullable MovePhase phase,
   int moveElapsed,
   int grabWindowTicks,
   @Nullable String sceneLockOwner,
   @Nullable String cameraLeaseOwner,
   int step,
   int partnerId,
   @Nullable ThorMove queued,
   boolean hammerDetached
) {
   /** Idle, nothing held (tests and defaults). */
   public static ThorStateView idle(ThorMode mode) {
      return new ThorStateView(mode, null, null, 0, 0, null, null, 0, -1, null, false);
   }

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

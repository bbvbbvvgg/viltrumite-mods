package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/**
 * The shared surface for the Thor sessions: read-only hammer view, the grab
 * window flag (read by the MMB resolver) and the scene lock (while held,
 * every Thor press is refused as busy). Pure on {@link ThorState}; server
 * main thread.
 */
public final class ThorBridge {
   private ThorBridge() {
   }

   public static HammerView view(ThorState state) {
      var timeline = state.timeline;
      return new HammerView(state.hammer, state.mode, timeline.move(), timeline.running() ? timeline.phase() : null, timeline.elapsed(),
         state.grabWindow, state.sceneLock);
   }

   /** Null when the player is not Thor. */
   @Nullable
   public static HammerView view(Player player) {
      ThorState state = ThorState.of(player);
      return state == null ? null : view(state);
   }

   /** Open (or extend to) a grab window of {@code ticks}; MMB then resolves to GRAB first. */
   public static void openGrabWindow(ThorState state, int ticks) {
      if (ticks <= 0) {
         throw new IllegalArgumentException("ticks <= 0");
      }

      state.grabWindow = Math.max(state.grabWindow, ticks);
   }

   public static void closeGrabWindow(ThorState state) {
      state.grabWindow = 0;
   }

   public static boolean grabWindowOpen(ThorState state) {
      return state.grabWindow > 0;
   }

   /** Take the scene lock; false when another owner holds it. Re-entrant for the same owner. */
   public static boolean lockScene(ThorState state, String owner) {
      if (owner == null || owner.isBlank()) {
         throw new IllegalArgumentException("owner is required");
      }

      if (state.sceneLock != null && !state.sceneLock.equals(owner)) {
         return false;
      }

      state.sceneLock = owner;
      return true;
   }

   /** Release the lock; false when this owner does not hold it. */
   public static boolean unlockScene(ThorState state, String owner) {
      if (state.sceneLock == null || !state.sceneLock.equals(owner)) {
         return false;
      }

      state.sceneLock = null;
      return true;
   }

   public static boolean sceneLocked(ThorState state) {
      return state.sceneLock != null;
   }
}

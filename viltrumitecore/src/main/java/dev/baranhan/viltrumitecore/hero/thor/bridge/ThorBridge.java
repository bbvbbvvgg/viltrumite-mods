package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/**
 * The shared server surface for the Thor sessions (FQCN of the contract's
 * "integration bridge"): hammer access, read-only state, the grab window
 * (read by the MMB resolver), the scene lock (every Thor press refused while
 * held) and the camera lease (one owner; dropped on logout, death, dimension
 * or hero change). Events: {@link ThorEvents}; move authoring:
 * {@link ThorMoves}. Server main thread.
 */
public final class ThorBridge {
   private static HammerAccess hammerAccess = HammerAccess.FOUNDATION;
   @Nullable
   private static String hammerAccessOwner;

   private ThorBridge() {
   }

   // --- hammer access (A writes, everyone reads) ---

   /** Session A installs the world-hammer access once. */
   public static synchronized void installHammerAccess(String owner, HammerAccess access) {
      if (owner == null || owner.isBlank() || access == null) {
         throw new IllegalArgumentException("owner and access are required");
      }

      if (hammerAccessOwner != null) {
         throw new IllegalStateException("hammer access already installed by " + hammerAccessOwner);
      }

      hammerAccess = access;
      hammerAccessOwner = owner;
   }

   @Nullable
   public static synchronized String hammerAccessOwner() {
      return hammerAccessOwner;
   }

   public static synchronized HammerView hammer(UUID thor, @Nullable ThorState state) {
      return hammerAccess.view(thor, state);
   }

   /** Null when the player is not Thor. */
   @Nullable
   public static HammerView hammer(Player player) {
      ThorState state = ThorState.of(player);
      return state == null ? null : hammer(player.getUUID(), state);
   }

   // --- read-only state ---

   public static ThorStateView state(ThorState state) {
      var timeline = state.timeline;
      return new ThorStateView(state.mode, timeline.move(), timeline.running() ? timeline.phase() : null, timeline.elapsed(), state.grabWindow,
         state.sceneLock, state.cameraLease);
   }

   @Nullable
   public static ThorStateView state(Player player) {
      ThorState state = ThorState.of(player);
      return state == null ? null : state(state);
   }

   // --- grab window ---

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

   public static boolean grabWindowActive(ThorState state) {
      return state.grabWindow > 0;
   }

   // --- scene lock ---

   /** Take the scene lock; false when another owner holds it. Re-entrant for the same owner. */
   public static boolean lockScene(ThorState state, String owner) {
      requireOwner(owner);
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

   // --- camera lease ---

   /** Take the camera lease (synced as ThorFlags.CAMERA_LEASE); false when another owner holds it. */
   public static boolean acquireCamera(ThorState state, String owner) {
      requireOwner(owner);
      if (state.cameraLease != null && !state.cameraLease.equals(owner)) {
         return false;
      }

      state.cameraLease = owner;
      return true;
   }

   public static boolean releaseCamera(ThorState state, String owner) {
      if (state.cameraLease == null || !state.cameraLease.equals(owner)) {
         return false;
      }

      state.cameraLease = null;
      return true;
   }

   public static boolean cameraLeased(ThorState state) {
      return state.cameraLease != null;
   }

   /** Tests only. */
   public static synchronized void resetForTests() {
      hammerAccess = HammerAccess.FOUNDATION;
      hammerAccessOwner = null;
   }

   private static void requireOwner(String owner) {
      if (owner == null || owner.isBlank()) {
         throw new IllegalArgumentException("owner is required");
      }
   }
}

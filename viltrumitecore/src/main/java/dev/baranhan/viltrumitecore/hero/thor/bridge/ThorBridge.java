package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
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

   /**
    * Mjölnir really in Thor's right hand: attached (HammerView.inHand) and the
    * hand free. With an item there it rides the belt (MjolnirPlace.BELT,
    * ThorCombat.presence): no hammer blow, fatality or teleport (integration
    * rule shared by sessions B and C).
    */
   public static boolean hammerHeld(Player player) {
      HammerView view = hammer(player);
      return view != null && view.inHand() && ThorHands.free(player);
   }

   // --- move lifecycle (every session start / stop goes through here) ---

   /**
    * Start {@code move} for this Thor with its handler lifecycle (gate →
    * admits → timeline → onStop of the replaced move → onStart → entry
    * phase); {@code partnerId} is the partner of a paired move (-1 none).
    * NOT_READY when the player is not Thor.
    */
   public static ThorActions.Refusal start(ServerPlayer player, ThorMove move, int partnerId) {
      ThorState state = ThorState.of(player);
      return state == null ? ThorActions.Refusal.NOT_READY : start(player, state, move, partnerId);
   }

   public static ThorActions.Refusal start(ServerPlayer player, ThorState state, ThorMove move, int partnerId) {
      return ThorActions.begin(player, state, move, partnerId, null, null, null);
   }

   /**
    * Start a scene-like move at once: a different running move is first
    * stopped with {@code cut} (its owner hears onStop), whatever its priority.
    */
   public static ThorActions.Refusal startCutting(ServerPlayer player, ThorState state, ThorMove move, int partnerId, InterruptReason cut) {
      return ThorActions.begin(player, state, move, partnerId, null, null, cut);
   }

   /**
    * Stop the running move with {@code reason} unless it resists; its owner
    * hears onStop. Never touches the scene lock, the camera lease or the
    * windows (they belong to their owners). True when a move stopped.
    */
   public static boolean interrupt(@Nullable ServerPlayer player, ThorState state, InterruptReason reason) {
      return ThorActions.interrupt(player, state, reason);
   }

   /** An owner ends its own {@code move} (no-op when another move runs); the handler hears onStop. */
   public static boolean end(@Nullable ServerPlayer player, ThorState state, ThorMove move) {
      return ThorActions.end(player, state, move);
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

   // --- combo window (session B publishes, others read) ---

   /** Open (or extend to) the combo window of a chain of {@code move} for {@code ticks}. */
   public static void openComboWindow(ThorState state, ThorMove move, int ticks) {
      if (move == null || ticks <= 0) {
         throw new IllegalArgumentException("move and ticks > 0 are required");
      }

      state.comboWindow = state.comboMove == move ? Math.max(state.comboWindow, ticks) : ticks;
      state.comboMove = move;
   }

   public static void closeComboWindow(ThorState state) {
      state.comboMove = null;
      state.comboWindow = 0;
   }

   /** The move of the open combo chain, or null. */
   @Nullable
   public static ThorMove comboWindow(ThorState state) {
      return state.comboWindow > 0 ? state.comboMove : null;
   }

   // --- grab window ---

   /** Open (or extend to) a grab window of {@code ticks}; MMB then resolves to GRAB first. */
   public static void openGrabWindow(ThorState state, int ticks) {
      if (ticks <= 0) {
         throw new IllegalArgumentException("ticks <= 0");
      }

      state.grabWindow = Math.max(state.grabWindow, ticks);
   }

   /**
    * Open the window on one target (the enemy whose hit opened it): the
    * paired GRAB move then starts with it as the partner.
    */
   public static void openGrabWindow(ThorState state, int ticks, int targetId) {
      openGrabWindow(state, ticks);
      state.grabTarget = targetId;
   }

   /** Entity id the open grab window is for, -1 when none. */
   public static int grabTarget(ThorState state) {
      return state.grabWindow > 0 ? state.grabTarget : -1;
   }

   public static void closeGrabWindow(ThorState state) {
      state.grabWindow = 0;
      state.grabTarget = -1;
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

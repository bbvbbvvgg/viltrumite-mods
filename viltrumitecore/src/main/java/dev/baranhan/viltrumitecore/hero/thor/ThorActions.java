package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server press / release / tick flow: resolve the context move, gate it
 * (authored, hands, scene lock, busy), play it on the timeline and hand its
 * events to the move's registered handler. The flow is pure ({@link #start},
 * {@link #release(ThorState, ThorInput)}, {@link #advance}); the ServerPlayer
 * wrappers read the world and give feedback. Without registrations nothing
 * reachable from a key starts: every press is refused as not ready.
 */
public final class ThorActions {
   /** Why a resolved move does not start. */
   public enum Refusal {
      NONE,
      /** No session has registered the move. */
      NOT_READY,
      /** An item occupies the right hand (drawing Mjölnir needs it free). */
      HANDS_BUSY,
      /** Another move runs and does not give way, or a scene holds the lock. */
      BUSY
   }

   /** What a release reached: the move chosen on the press and how long the key was held. */
   public record Release(ThorMove move, int heldTicks) {
   }

   private ThorActions() {
   }

   /** Pure gate before the timeline (the timeline adds BUSY). */
   public static Refusal gate(ThorState state, ThorMove move, boolean handFree) {
      if (!move.authored()) {
         return Refusal.NOT_READY;
      } else if (ThorBridge.sceneLocked(state)) {
         return Refusal.BUSY;
      }

      return needsHand(move) && !handFree ? Refusal.HANDS_BUSY : Refusal.NONE;
   }

   /** Drawing or throwing Mjölnir takes the right hand: an item there refuses it. */
   static boolean needsHand(ThorMove move) {
      return ThorControls.needsHand(move);
   }

   /**
    * Overlay moves never take the timeline: the recall runs beside the
    * current strike (the strike finishes, the arm reaches for the hammer,
    * the fight goes on), so it neither cuts nor waits for a move.
    */
   public static boolean overlay(ThorMove move) {
      return ThorControls.overlay(move);
   }

   /** Server context of a player: live state, held items, movement, the nearest enemy. */
   public static ThorContext context(ServerPlayer player, ThorState state, @Nullable LivingEntity enemy) {
      return new ThorContext(state.hammer, state.mode, ThorHands.free(player), player.isShiftKeyDown(), !player.onGround(), false, enemy != null,
         ThorBridge.grabWindowActive(state), state.hammerDetached, fistChainOpen(state));
   }

   /** An unarmed chain (session B's combo window) is still open: LMB / RMB finish it first (#166). */
   public static boolean fistChainOpen(ThorState state) {
      return ThorBridge.comboWindow(state) == ThorMove.FIST_COMBO;
   }

   /** A press of one of Thor's inputs (already past canAct). */
   public static void press(ServerPlayer player, ThorState state, ThorInput input) {
      LivingEntity enemy = ThorTargets.nearestEnemy(player, ThorRules.MELEE_ENEMY_RADIUS);
      ThorContext context = context(player, state, enemy);
      ThorMove move = ThorControls.resolve(input, context);
      if (move == null) {
         return;
      }

      LivingEntity target = move == ThorMove.HAMMER_STRIKE ? enemy : null;
      if (move.authored() && !ThorMoves.handler(move).admits(new ThorMoveContext(player, state, move, target, input))) {
         return;
      }

      HammerState before = state.hammer;
      ThorMove previous = state.timeline.move();
      switch (start(state, input, move, context.handFree())) {
         case NOT_READY -> ThorFeedback.refuse(player, ThorFeedback.notReady(move));
         case HANDS_BUSY -> ThorFeedback.refuse(player, Component.translatable(ThorFeedback.HANDS_BUSY));
         case BUSY -> ThorFeedback.refuse(player, Component.translatable(ThorFeedback.BUSY));
         case NONE -> {
            if (previous != null && previous != state.timeline.move()) {
               ThorMoves.handler(previous).onStop(new ThorMoveContext(player, state, previous, null));
            }

            ThorMoves.handler(move).onStart(new ThorMoveContext(player, state, move, target, input));
            ThorFeedback.hammerMoved(player, before, state.hammer);
         }
      }
   }

   /** Pure press flow without an input (technical starts by a session). */
   public static Refusal start(ThorState state, ThorMove move, boolean handFree) {
      return start(state, null, move, handFree);
   }

   /**
    * Pure press flow: gate the move, then admit it on the timeline (it starts,
    * cancels the running one, chains or is buffered). An admitted press
    * remembers its move for the release of {@code input}.
    */
   public static Refusal start(ThorState state, @Nullable ThorInput input, ThorMove move, boolean handFree) {
      Refusal gate = gate(state, move, handFree);
      if (gate != Refusal.NONE || overlay(move)) {
         return gate;
      }

      StagedTimeline.Admission admission = state.timeline.request(move, -1);
      if (admission == StagedTimeline.Admission.REFUSE) {
         return Refusal.BUSY;
      }

      if (input != null) {
         state.pressed.put(input, move);
         state.heldTicks.put(input, 0);
      }

      if (admission.started()) {
         apply(state, state.timeline.entryEvent());
      }

      return Refusal.NONE;
   }

   /** Release of a held input (HeldInputs always delivers it to the started action). */
   public static void release(ServerPlayer player, ThorState state, ThorInput input) {
      Release release = release(state, input);
      if (release != null) {
         ThorMoves.handler(release.move()).onRelease(new ThorMoveContext(player, state, release.move(), null), release.heldTicks());
      }
   }

   /** Pure release: the move chosen on the press, or null when the press started nothing. */
   @Nullable
   public static Release release(ThorState state, ThorInput input) {
      ThorMove move = state.pressed.remove(input);
      Integer held = state.heldTicks.remove(input);
      return move == null ? null : new Release(move, held == null ? 0 : held);
   }

   /** One server tick of the move flow. */
   public static void tick(ServerPlayer player, ThorState state) {
      HammerState before = state.hammer;
      ThorMove running = state.timeline.move();
      StagedTimeline.Event event = advance(state, ThorHands.free(player), !player.isAlive() || HeroDamage.isAnchored(player));
      if (running != null && event == StagedTimeline.Event.NONE && !state.timeline.running()) {
         ThorMoves.handler(running).onStop(new ThorMoveContext(player, state, running, null));
      } else if (event != StagedTimeline.Event.NONE) {
         ThorMove owner = event == StagedTimeline.Event.END ? running : state.timeline.move();
         if (owner != null) {
            ThorMoves.handler(owner).onPhase(new ThorMoveContext(player, state, owner, null), event);
         }
      }

      ThorFeedback.hammerMoved(player, before, state.hammer);
   }

   /**
    * Pure tick flow; returns the timeline event of this tick. {@code
    * controlled}: dead or held by external control, which stops the move. An
    * item taken into the right hand before the grip cancels a draw. The
    * hammer state is not touched here: the world store (session A) is its
    * one writer, and an item in the hand only changes where clients draw an
    * attached hammer (MjolnirPlace). Held inputs count up, the grab window
    * counts down.
    */
   public static StagedTimeline.Event advance(ThorState state, boolean handFree, boolean controlled) {
      if (controlled) {
         state.timeline.interrupt(InterruptReason.CONTROL);
      }

      if (!handFree && state.timeline.is(ThorMove.DRAW) && state.timeline.phase() == MovePhase.INTRO) {
         state.timeline.interrupt(InterruptReason.INPUT);
      }

      if (state.grabWindow > 0) {
         state.grabWindow--;
      }

      if (state.comboWindow > 0 && --state.comboWindow == 0) {
         state.comboMove = null;
      }

      state.heldTicks.replaceAll((input, ticks) -> ticks + 1);
      StagedTimeline.Event event = state.timeline.tick();
      apply(state, event);
      return event;
   }

   /** Pure: where Mjölnir is after a technical DRAW / HOLSTER MAIN phase began. */
   public static HammerState afterMain(ThorMove move, HammerState hammer) {
      return switch (move) {
         case DRAW -> hammer == HammerState.HOLSTERED ? HammerState.IN_HAND : hammer;
         case HOLSTER -> hammer == HammerState.IN_HAND ? HammerState.HOLSTERED : hammer;
         default -> hammer;
      };
   }

   /** Foundation phase effects of one timeline event (a chained step may start in MAIN). */
   private static void apply(ThorState state, StagedTimeline.Event event) {
      if (event == StagedTimeline.Event.CHAIN) {
         event = state.timeline.entryEvent();
      }

      ThorMove move = state.timeline.move();
      if (event == StagedTimeline.Event.ENTER_MAIN && move != null) {
         state.hammer = afterMain(move, state.hammer);
      }
   }

   /** Lifecycle stop with the player at hand: the stopped move's handler hears onStop. */
   public static void stop(ServerPlayer player, ThorState state) {
      ThorMove running = state.timeline.move();
      stop(state);
      if (running != null) {
         ThorMoves.handler(running).onStop(new ThorMoveContext(player, state, running, null));
      }
   }

   /** Death, logout, dimension or hero change: stop the move, drop the bridge flags (the hammer stays where it is). */
   public static void stop(ThorState state) {
      state.timeline.interrupt(InterruptReason.LIFECYCLE);
      state.clearTransient();
   }
}

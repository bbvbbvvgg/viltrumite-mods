package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ReleaseCause;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorScenes;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server press / release / tick flow: resolve the context move, gate it
 * (authored, hands, scene lock, busy), play it on the timeline and hand its
 * events to the move's registered handler. One start path for every
 * session ({@link #begin}, exposed as ThorBridge.start / startCutting), one
 * stop ({@link #interrupt} / {@link #end}, ThorBridge.interrupt / end), one
 * lifecycle stop ({@link #stop(ServerPlayer, ThorState, ThorLifecycle.Cause)}).
 * Handler order: admits → onStop(replaced) → onStart → onPhase(entry);
 * a buffered press → onQueued; a buffered step → onPhase(CHAIN) →
 * onPhase(entry); a stop that bypassed the API → onStop on the next tick.
 * The bookkeeping is pure ({@link #start}, {@link #release(ThorState,
 * ThorInput)}, {@link #advance}). Without registrations nothing reachable
 * from a key starts: every press is refused as not ready.
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
      BUSY,
      /** The owning session's {@code admits} said no (it gave its own feedback). */
      DECLINED
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
      return new ThorContext(state.hammer(), state.mode(), ThorHands.free(player), player.isShiftKeyDown(), !player.onGround(),
         ThorScenes.fullFlight(player), enemy != null, ThorBridge.grabWindowActive(state),
         state.hammerDetached(), fistChainOpen(state));
   }

   /**
    * B-m2: clicking a chain faster than it buffers (the one buffer slot of
    * the running chained move is already taken) is ordinary combat input,
    * not a refusal worth a "Not now": it is dropped silently. Any other BUSY
    * (a different move blocked by the running one) still tells the player.
    */
   static boolean quietBusy(ThorState state, ThorMove move) {
      return move.maxSteps() > 1 && state.timeline.is(move) && state.timeline.queued() == move;
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
         if (ThorControls.onBelt(input, context)) {
            // Mjölnir is not with him (another Thor's, not created yet): MMB answers; B says to take the hammer first.
            ThorFeedback.refuse(player, Component.translatable(ThorControls.crushWaitsForTheHand(input, context) ? ThorFeedback.CRUSH_TAKE_FIRST : ThorFeedback.NO_HAMMER));
         }

         return;
      }

      if (move == ThorMove.DRAW && !ThorHands.free(player) && !ThorHands.clearForMjolnir(player)) {
         // Nowhere to put the item: say how to take the hammer instead of a silent no.
         ThorFeedback.refuse(player, Component.translatable(ThorFeedback.DRAW_NO_SLOT));
         return;
      }

      LivingEntity target = move == ThorMove.HAMMER_STRIKE ? enemy : null;
      switch (begin(player, state, move, move.paired() ? ThorBridge.grabTarget(state) : -1, input, target, null)) {
         case NOT_READY -> ThorFeedback.refuse(player, ThorFeedback.notReady(move));
         case HANDS_BUSY -> ThorFeedback.refuse(player, Component.translatable(ThorFeedback.HANDS_BUSY));
         case BUSY -> {
            if (!quietBusy(state, move)) {
               ThorFeedback.refuse(player, Component.translatable(ThorFeedback.BUSY));
            }
         }
         default -> {
            if (move == ThorMove.DRAW && state.timeline.is(ThorMove.DRAW) && state.timeline.phase() == MovePhase.INTRO) {
               // Audit v3 (item 7): the freed hand reaches for the belt; Mjölnir stays drawn there until the grip (ENTER_MAIN).
               state.projectTechnical(HammerState.HOLSTERED);
            }
         }
      }
   }

   /**
    * The one start of a move with its handler lifecycle (every session start
    * goes here, through ThorBridge.start): gate (authored, scene lock, hand)
    * → the owner's {@code admits} → (optional) cut the running move with
    * {@code cut}, its owner hears onStop → admit on the timeline → the
    * replaced move hears onStop (also a restart of the same move) → onStart
    * of the started step → onPhase(entry event) when it starts past its
    * intro. A buffered press reaches only {@code onQueued}; an overlay
    * (RECALL) only {@code onStart}, beside the running move. No feedback:
    * the caller reports the refusal.
    *
    * @param player    null only in pure tests (then the hand counts as free)
    * @param partnerId the partner entity of a paired move, -1 for none
    * @param input     the pressed key (its release then reaches this move), null for a technical start
    * @param target    the resolver's reason for the MMB melee branch, else null
    * @param cut       when non-null, a different running move is stopped with this reason first, so the
    *                  start does not depend on priorities (scenes); null = timeline rules only
    */
   public static Refusal begin(@Nullable ServerPlayer player, ThorState state, ThorMove move, int partnerId, @Nullable ThorInput input,
      @Nullable LivingEntity target, @Nullable InterruptReason cut) {
      Refusal gate = gate(state, move, player == null || ThorHands.free(player));
      if (gate != Refusal.NONE) {
         return gate;
      }

      ThorMoveHandler handler = ThorMoves.handler(move);
      ThorMoveContext context = new ThorMoveContext(player, state, move, target, input);
      if (!handler.admits(context)) {
         return Refusal.DECLINED;
      }

      HammerState before = state.hammer();
      if (overlay(move)) {
         handler.onStart(context);
         hammerMoved(player, before, state);
         return Refusal.NONE;
      }

      if (cut != null && state.timeline.running() && !state.timeline.is(move)) {
         interrupt(player, state, cut);
      }

      ThorMove previous = state.timeline.move();
      StagedTimeline.Admission admission = state.timeline.request(move, partnerId);
      if (admission == StagedTimeline.Admission.REFUSE) {
         return Refusal.BUSY;
      }

      if (input != null) {
         state.pressed.put(input, move);
         state.heldTicks.put(input, 0);
      }

      if (admission == StagedTimeline.Admission.QUEUE) {
         handler.onQueued(context);
         return Refusal.NONE;
      }

      StagedTimeline.Event entry = state.timeline.entryEvent();
      apply(state, entry);
      // The flow now owns the new move: a stop of it from here on (also from a handler below) still owes it onStop.
      state.lastMove = state.timeline.move();
      if (previous != null && (previous != move || admission == StagedTimeline.Admission.INTERRUPT)) {
         ThorMoves.handler(previous).onStop(new ThorMoveContext(player, state, previous, null));
      }

      if (state.timeline.is(move)) {
         handler.onStart(context);
      }

      if (entry != StagedTimeline.Event.NONE && state.timeline.is(move) && state.timeline.entryEvent() == entry) {
         handler.onPhase(new ThorMoveContext(player, state, move, null), entry);
      }

      hammerMoved(player, before, state);
      return Refusal.NONE;
   }

   /**
    * Stop whatever runs with {@code reason} (unless it resists it); its owner
    * hears onStop. Never touches the scene lock, the camera lease or the
    * windows of other owners. True when a move stopped.
    */
   public static boolean interrupt(@Nullable ServerPlayer player, ThorState state, InterruptReason reason) {
      ThorMove stopped = state.timeline.interrupt(reason);
      if (stopped == null) {
         return false;
      }

      state.lastMove = state.timeline.move();
      ThorMoves.handler(stopped).onStop(new ThorMoveContext(player, state, stopped, null));
      return true;
   }

   /**
    * Audit v3: a strike's recovery is the player's to cut. Movement, a dash or
    * the next strike (the buffer, already from the first recovery tick) end it
    * at once; the step ends as if it ran out (END, so a combo chain stays open
    * for its grace), not as an interruption. Only MELEE moves, only in their
    * FINISH, never with a press buffered (that one chains on its own). True
    * when the recovery was cut.
    */
   public static boolean finishRecovery(@Nullable ServerPlayer player, ThorState state) {
      ThorMove running = state.timeline.move();
      if (!recoveryCuttable(running, state.timeline.phase(), state.timeline.queued() != null)) {
         return false;
      }

      state.timeline.clear();
      state.lastMove = null;
      ThorMoves.handler(running).onPhase(new ThorMoveContext(player, state, running, null), StagedTimeline.Event.END);
      return true;
   }

   /** Pure: may this running move's recovery be cut now. */
   public static boolean recoveryCuttable(@Nullable ThorMove running, @Nullable MovePhase phase, boolean queued) {
      return running != null && !queued && phase == MovePhase.FINISH
         && (running == ThorMove.HAMMER_COMBO || running == ThorMove.FIST_COMBO || running == ThorMove.HAMMER_STRIKE);
   }

   /** An owner ends its own move (only when it is the one running); the handler hears onStop (keep it idempotent). */
   public static boolean end(@Nullable ServerPlayer player, ThorState state, ThorMove move) {
      return state.timeline.is(move) && interrupt(player, state, InterruptReason.LIFECYCLE);
   }

   private static void hammerMoved(@Nullable ServerPlayer player, HammerState before, ThorState state) {
      if (player != null) {
         ThorFeedback.hammerMoved(player, before, state.hammer());
      }
   }

   /**
    * Pure timeline bookkeeping without handlers (tests, the foundation's own
    * technical moves). Sessions never start a move with it: they call
    * ThorBridge.start, which runs the handler lifecycle ({@link #begin}).
    */
   public static Refusal start(ThorState state, ThorMove move, boolean handFree) {
      return start(state, null, move, handFree);
   }

   /**
    * Pure press flow: gate the move, then admit it on the timeline (it starts,
    * cancels the running one, chains or is buffered). An admitted press
    * remembers its move for the release of {@code input}.
    */
   public static Refusal start(ThorState state, @Nullable ThorInput input, ThorMove move, boolean handFree) {
      return start(state, input, move, handFree, move.paired() ? ThorBridge.grabTarget(state) : -1);
   }

   /** Press flow with an explicit partner entity id (paired moves: the grab target, the fatality victim; -1 = none). */
   public static Refusal start(ThorState state, @Nullable ThorInput input, ThorMove move, boolean handFree, int partnerId) {
      Refusal gate = gate(state, move, handFree);
      if (gate != Refusal.NONE || overlay(move)) {
         return gate;
      }

      StagedTimeline.Admission admission = state.timeline.request(move, partnerId);
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

   /** The player's release of a held input (HeldInputs always delivers it to the started action). */
   public static void release(ServerPlayer player, ThorState state, ThorInput input) {
      release(player, state, input, ReleaseCause.PLAYER);
   }

   /** Release with its cause: a forced one (death, logout, hero change, control) never counts as the player's intent. */
   public static void release(@Nullable ServerPlayer player, ThorState state, ThorInput input, ReleaseCause cause) {
      Release release = release(state, input);
      if (release != null) {
         ThorMoves.handler(release.move()).onRelease(new ThorMoveContext(player, state, release.move(), null), release.heldTicks(), cause);
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
      tick(player, state, player == null || ThorHands.free(player), player != null && (!player.isAlive() || HeroDamage.isAnchored(player)));
   }

   /**
    * The tick with its world reads given (pure tests pass a null player).
    * A move remembered from the last tick but no longer on the timeline was
    * stopped outside the API (a direct timeline interrupt): it hears onStop
    * now, once. A buffered step that began (CHAIN) is followed by its entry
    * event when it starts past its intro.
    */
   static void tick(@Nullable ServerPlayer player, ThorState state, boolean handFree, boolean controlled) {
      HammerState before = state.hammer();
      ThorMove remembered = state.lastMove;
      ThorMove running = state.timeline.move();
      if (remembered != null && remembered != running) {
         state.lastMove = running;
         ThorMoves.handler(remembered).onStop(new ThorMoveContext(player, state, remembered, null));
         running = state.timeline.move();
      }

      StagedTimeline.Event event = advance(state, handFree, controlled);
      state.lastMove = state.timeline.move();
      if (running != null && event == StagedTimeline.Event.NONE && !state.timeline.running()) {
         ThorMoves.handler(running).onStop(new ThorMoveContext(player, state, running, null));
      } else if (event != StagedTimeline.Event.NONE) {
         ThorMove owner = event == StagedTimeline.Event.END ? running : state.timeline.move();
         if (owner != null) {
            ThorMoveHandler handler = ThorMoves.handler(owner);
            handler.onPhase(new ThorMoveContext(player, state, owner, null), event);
            if (event == StagedTimeline.Event.CHAIN && state.timeline.is(owner)) {
               StagedTimeline.Event entry = state.timeline.entryEvent();
               if (entry != StagedTimeline.Event.NONE) {
                  handler.onPhase(new ThorMoveContext(player, state, owner, null), entry);
               }
            }
         }
      }

      hammerMoved(player, before, state);
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

      if (state.hitStop > 0) {
         // Audit v3 hit-stop: the move, its windows and held presses wait on the contact frame.
         state.hitStop--;
         if (state.timeline.running()) {
            return StagedTimeline.Event.NONE;
         }
      }

      state.tickWindows();

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
         state.projectTechnical(afterMain(move, state.hammer()));
      }
   }

   /**
    * Lifecycle stop with the player at hand: the running move is stopped and
    * hears onStop first (it still sees the lock, lease and windows), then the
    * lifecycle listeners (ThorLifecycle: sessions clear their own transient
    * state, lock / lease owners let go), then the bridge flags are dropped.
    */
   public static void stop(@Nullable ServerPlayer player, ThorState state, ThorLifecycle.Cause cause) {
      ThorMove running = state.timeline.interrupt(InterruptReason.LIFECYCLE);
      state.lastMove = null;
      if (running != null) {
         ThorMoves.handler(running).onStop(new ThorMoveContext(player, state, running, null));
      }

      ThorLifecycle.post(player, state, cause);
      state.timeline.clear();
      state.clearTransient();
   }

   /** Pure stop without handlers: stop the move, drop the bridge flags (the hammer stays where it is). */
   public static void stop(ThorState state) {
      state.timeline.interrupt(InterruptReason.LIFECYCLE);
      state.lastMove = null;
      state.clearTransient();
   }
}

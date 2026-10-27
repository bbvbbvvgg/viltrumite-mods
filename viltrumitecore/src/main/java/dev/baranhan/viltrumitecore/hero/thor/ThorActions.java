package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server press and tick flow: resolve the context move, gate it (shipped,
 * hands, busy), play it on the timeline and apply its phase effects. The
 * flow itself is pure ({@link #start}, {@link #advance}); the ServerPlayer
 * wrappers only read the world and give feedback. Stage 1 effect: Mjölnir
 * changes place at the start of the MAIN phase of DRAW / HOLSTER.
 */
public final class ThorActions {
   /** Why a resolved move does not start. */
   public enum Refusal {
      NONE,
      /** The move belongs to a later stage. */
      NOT_READY,
      /** An item occupies the right hand (drawing Mjölnir needs it free). */
      HANDS_BUSY,
      /** Another move runs and does not give way (priority, cancel window, full buffer). */
      BUSY
   }

   private ThorActions() {
   }

   /** Pure gate before the timeline (the timeline adds BUSY). */
   public static Refusal gate(ThorMove move, boolean handFree) {
      if (!move.shipped()) {
         return Refusal.NOT_READY;
      }

      return move == ThorMove.DRAW && !handFree ? Refusal.HANDS_BUSY : Refusal.NONE;
   }

   /** Server context of a player: live state, held items, movement. */
   public static ThorContext context(ServerPlayer player, ThorState state) {
      // hasTarget / flying / guarding / grabbing arrive with the stages that need them (2, 4, 5).
      return new ThorContext(state.hammer, state.mode, ThorHands.free(player), player.isShiftKeyDown(), !player.onGround(),
         false, false, false, false);
   }

   /** A press of one of Thor's inputs (already past canAct). */
   public static void press(ServerPlayer player, ThorState state, ThorInput input) {
      ThorContext context = context(player, state);
      ThorMove move = ThorControls.resolve(input, context);
      if (move == null) {
         return;
      }

      HammerState before = state.hammer;
      switch (start(state, move, context.handFree())) {
         case NOT_READY -> ThorFeedback.refuse(player, ThorFeedback.notReady(move));
         case HANDS_BUSY -> ThorFeedback.refuse(player, Component.translatable(ThorFeedback.HANDS_BUSY));
         case BUSY -> ThorFeedback.refuse(player, Component.translatable(ThorFeedback.BUSY));
         case NONE -> ThorFeedback.hammerMoved(player, before, state.hammer);
      }
   }

   /**
    * Pure press flow: gate the move, then admit it on the timeline (it starts,
    * cancels the running one, chains or is buffered). A move that starts in
    * its MAIN phase applies the effect at once.
    */
   public static Refusal start(ThorState state, ThorMove move, boolean handFree) {
      Refusal gate = gate(move, handFree);
      if (gate != Refusal.NONE) {
         return gate;
      }

      StagedTimeline.Admission admission = state.timeline.request(move, -1);
      if (admission == StagedTimeline.Admission.REFUSE) {
         return Refusal.BUSY;
      }

      if (admission.started()) {
         apply(state, state.timeline.entryEvent());
      }

      return Refusal.NONE;
   }

   /** One server tick of the move flow. */
   public static void tick(ServerPlayer player, ThorState state) {
      HammerState before = state.hammer;
      advance(state, ThorHands.free(player), !player.isAlive() || HeroDamage.isAnchored(player));
      ThorFeedback.hammerMoved(player, before, state.hammer);
   }

   /**
    * Pure tick flow. {@code controlled}: dead or held by external control,
    * which stops the move. An item taken into the right hand before the grip
    * cancels a draw; Mjölnir never shares the hand with an item.
    */
   public static void advance(ThorState state, boolean handFree, boolean controlled) {
      if (controlled) {
         state.timeline.interrupt(InterruptReason.CONTROL);
      }

      if (!handFree && state.timeline.is(ThorMove.DRAW) && state.timeline.phase() == MovePhase.INTRO) {
         state.timeline.interrupt(InterruptReason.INPUT);
      }

      apply(state, state.timeline.tick());
      state.hammer = settle(state.hammer, handFree);
   }

   /** Mjölnir cannot share the right hand with an item: it goes back to the belt at once. */
   public static HammerState settle(HammerState hammer, boolean handFree) {
      return hammer == HammerState.IN_HAND && !handFree ? HammerState.HOLSTERED : hammer;
   }

   /** Pure: where Mjölnir is after a move's MAIN phase began. */
   public static HammerState afterMain(ThorMove move, HammerState hammer) {
      return switch (move) {
         case DRAW -> hammer == HammerState.HOLSTERED ? HammerState.IN_HAND : hammer;
         case HOLSTER -> hammer == HammerState.IN_HAND ? HammerState.HOLSTERED : hammer;
         default -> hammer;
      };
   }

   /** Phase effects of one timeline event (a chained step may start in MAIN). */
   private static void apply(ThorState state, StagedTimeline.Event event) {
      if (event == StagedTimeline.Event.CHAIN) {
         event = state.timeline.entryEvent();
      }

      ThorMove move = state.timeline.move();
      if (event == StagedTimeline.Event.ENTER_MAIN && move != null) {
         state.hammer = afterMain(move, state.hammer);
      }
   }

   /** Death, logout, dimension or hero change: stop the move (the hammer stays where it is). */
   public static void stop(ThorState state) {
      state.timeline.interrupt(InterruptReason.LIFECYCLE);
   }
}

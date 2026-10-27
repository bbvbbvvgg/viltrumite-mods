package dev.baranhan.viltrumitecore.hero.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

/** Shared staged move player: phases, skips, priorities, windows, combos, partners, interrupts. */
class StagedTimelineTest {
   /** A test move; {@code armour} lists the reasons resisted during MAIN. */
   private record Move(int wireId, int priority, int intro, int main, int finish, @Nullable MovePhase cancellableFrom, int maxSteps, boolean paired,
                       Set<InterruptReason> armour) implements StagedMove {
      @Override
      public int ticks(MovePhase phase) {
         return switch (phase) {
            case INTRO -> this.intro;
            case MAIN -> this.main;
            case FINISH -> this.finish;
         };
      }

      @Override
      public boolean resists(InterruptReason reason, MovePhase phase) {
         return phase == MovePhase.MAIN && this.armour.contains(reason);
      }
   }

   private static Move move(int wireId, int priority, int intro, int main, int finish, @Nullable MovePhase window) {
      return new Move(wireId, priority, intro, main, finish, window, 1, false, Set.of());
   }

   private static final Move WALK = move(1, 1, 2, 3, 1, MovePhase.FINISH);
   private static final Move OTHER = move(2, 1, 2, 2, 2, MovePhase.FINISH);
   private static final Move HEAVY = move(3, 2, 1, 1, 1, null);
   private static final Move COMBO = new Move(4, 1, 1, 1, 2, MovePhase.FINISH, 3, false, Set.of());
   private static final Move ARMOURED = new Move(5, 1, 1, 2, 1, null, 1, false, Set.of(InterruptReason.INPUT, InterruptReason.HIT));
   private static final Move SCENE = new Move(6, 4, 1, 4, 1, MovePhase.FINISH, 2, true, Set.of());

   private static void ticks(StagedTimeline<Move> timeline, int count) {
      for (int i = 0; i < count; i++) {
         timeline.tick();
      }
   }

   @Test
   void idleTimelinePublishesNothing() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      assertFalse(timeline.running());
      assertEquals(-1, timeline.wireId());
      assertEquals(0, timeline.variant());
      assertEquals(0, timeline.length());
      assertEquals(-1, timeline.partnerId());
      assertEquals(StagedTimeline.Event.NONE, timeline.tick());
      assertEquals(StagedTimeline.Event.NONE, timeline.entryEvent());
      assertNull(timeline.interrupt(InterruptReason.LIFECYCLE));
   }

   @Test
   void playsIntroMainFinishThenEnds() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      assertEquals(StagedTimeline.Admission.START, timeline.request(WALK, -1));
      assertEquals(MovePhase.INTRO, timeline.phase());
      assertEquals(StagedTimeline.Event.NONE, timeline.entryEvent());
      assertEquals(1, timeline.wireId());
      assertEquals(6, timeline.length());

      List<StagedTimeline.Event> events = new ArrayList<>();
      while (timeline.running()) {
         events.add(timeline.tick());
      }

      assertEquals(List.of(StagedTimeline.Event.NONE, StagedTimeline.Event.ENTER_MAIN, StagedTimeline.Event.NONE, StagedTimeline.Event.NONE,
         StagedTimeline.Event.ENTER_FINISH, StagedTimeline.Event.END), events);
      assertEquals(WALK.length(), events.size(), "one tick per authored tick");
      assertEquals(-1, timeline.wireId());
   }

   @Test
   void unauthoredPhasesAreSkipped() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      Move mainOnly = move(7, 1, 0, 2, 0, null);
      timeline.request(mainOnly, -1);
      assertEquals(MovePhase.MAIN, timeline.phase());
      assertEquals(StagedTimeline.Event.ENTER_MAIN, timeline.entryEvent(), "a move without intro starts in its effective part");
      assertEquals(StagedTimeline.Event.NONE, timeline.tick());
      assertEquals(StagedTimeline.Event.END, timeline.tick());

      Move noMain = move(8, 1, 1, 0, 2, null);
      timeline.request(noMain, -1);
      assertEquals(StagedTimeline.Event.ENTER_FINISH, timeline.tick(), "an empty main phase is skipped");
      assertEquals(0, timeline.phaseElapsed());
   }

   @Test
   void unauthoredOrMissingMoveNeverStarts() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(move(9, 9, 0, 0, 0, null), -1));
      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(null, -1));
      assertFalse(timeline.running());
   }

   @Test
   void admitDoesNotChangeState() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      assertEquals(StagedTimeline.Admission.START, timeline.admit(WALK, -1));
      assertFalse(timeline.running());
   }

   @Test
   void equalPriorityWaitsForTheWindowThenAnyMoveCancels() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      timeline.request(WALK, -1);
      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(OTHER, -1));
      assertSame(WALK, timeline.move());

      ticks(timeline, 5);
      assertEquals(MovePhase.FINISH, timeline.phase());
      Move weak = move(10, 0, 1, 1, 1, null);
      assertEquals(StagedTimeline.Admission.INTERRUPT, timeline.request(weak, -1), "inside the window even a lower tier cancels");
      assertSame(weak, timeline.move());
      assertEquals(0, timeline.elapsed());
   }

   @Test
   void higherPriorityInterruptsOutsideTheWindow() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      timeline.request(WALK, -1);
      assertEquals(StagedTimeline.Admission.INTERRUPT, timeline.request(HEAVY, -1));
      assertSame(HEAVY, timeline.move());
      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(WALK, -1), "no window: the heavy move plays to its end");
   }

   @Test
   void hyperArmourResistsInputAndHitsOnlyInItsPhase() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      timeline.request(ARMOURED, -1);
      assertEquals(MovePhase.INTRO, timeline.phase());
      assertEquals(StagedTimeline.Admission.INTERRUPT, timeline.admit(HEAVY, -1), "no armour during the intro");

      timeline.tick();
      assertEquals(MovePhase.MAIN, timeline.phase());
      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(HEAVY, -1));
      assertNull(timeline.interrupt(InterruptReason.HIT));
      assertTrue(timeline.running());
      assertSame(ARMOURED, timeline.interrupt(InterruptReason.CONTROL));
      assertFalse(timeline.running());
   }

   @Test
   void lifecycleAlwaysStops() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      timeline.request(ARMOURED, -1);
      timeline.tick();
      assertSame(ARMOURED, timeline.interrupt(InterruptReason.LIFECYCLE));
      assertFalse(timeline.running());
      assertEquals(-1, timeline.partnerId());
      assertNull(timeline.queued());
   }

   @Test
   void comboBuffersOnePressBeforeTheWindowAndChainsOnEntry() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      timeline.request(COMBO, -1);
      assertEquals(StagedTimeline.Admission.QUEUE, timeline.request(COMBO, -1));
      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(COMBO, -1), "one buffered press only");
      assertSame(COMBO, timeline.queued());

      assertEquals(StagedTimeline.Event.ENTER_MAIN, timeline.tick());
      assertEquals(StagedTimeline.Event.ENTER_FINISH, timeline.tick());
      assertEquals(StagedTimeline.Event.CHAIN, timeline.tick(), "the buffered step starts once the window opens");
      assertEquals(1, timeline.step());
      assertEquals(0, timeline.elapsed());
      assertNull(timeline.queued());
      assertEquals(MovePhase.INTRO, timeline.phase());
   }

   @Test
   void comboChainsInsideTheWindowAndRestartsAfterTheLastStep() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      timeline.request(COMBO, -1);
      ticks(timeline, 2);
      assertEquals(StagedTimeline.Admission.CHAIN, timeline.request(COMBO, -1));
      assertEquals(1, timeline.step());
      ticks(timeline, 2);
      assertEquals(StagedTimeline.Admission.CHAIN, timeline.request(COMBO, -1));
      assertEquals(2, timeline.step());

      ticks(timeline, 2);
      assertEquals(StagedTimeline.Admission.INTERRUPT, timeline.request(COMBO, -1), "after the last step a press starts over");
      assertEquals(0, timeline.step());
   }

   @Test
   void bufferedStepWithoutWindowStartsAtTheEnd() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      Move pair = new Move(11, 1, 1, 1, 1, null, 2, false, Set.of());
      timeline.request(pair, -1);
      assertEquals(StagedTimeline.Admission.QUEUE, timeline.request(pair, -1));
      ticks(timeline, 2);
      assertEquals(StagedTimeline.Event.CHAIN, timeline.tick());
      assertEquals(1, timeline.step());
      ticks(timeline, 2);
      assertEquals(StagedTimeline.Event.END, timeline.tick());
   }

   @Test
   void pairedMoveNeedsAndKeepsItsPartner() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(SCENE, -1), "a two-character scene needs a partner");
      assertEquals(StagedTimeline.Admission.START, timeline.request(SCENE, 42));
      assertEquals(42, timeline.partnerId());

      ticks(timeline, 5);
      assertEquals(MovePhase.FINISH, timeline.phase());
      assertEquals(StagedTimeline.Admission.CHAIN, timeline.request(SCENE, -1));
      assertEquals(42, timeline.partnerId(), "the next step keeps the partner");

      assertEquals(StagedTimeline.Admission.REFUSE, timeline.request(WALK, 7), "a lower tier cannot break the scene");
      assertEquals(42, timeline.partnerId());
      timeline.clear();
      timeline.request(WALK, 7);
      assertEquals(-1, timeline.partnerId(), "unpaired moves never carry a partner");
   }

   @Test
   void variantPacksPhaseAndStep() {
      for (MovePhase phase : MovePhase.values()) {
         for (int step = 0; step < 5; step++) {
            int variant = StagedTimeline.pack(phase, step);
            assertSame(phase, StagedTimeline.phaseOf(variant));
            assertEquals(step, StagedTimeline.stepOf(variant));
         }
      }

      StagedTimeline<Move> timeline = new StagedTimeline<>();
      timeline.request(COMBO, -1);
      ticks(timeline, 2);
      timeline.request(COMBO, -1);
      timeline.tick();
      assertEquals(StagedTimeline.pack(MovePhase.MAIN, 1), timeline.variant());
   }

   @Test
   void phaseMathMatchesTheTicks() {
      assertSame(MovePhase.INTRO, StagedTimeline.phaseAt(WALK, 0));
      assertSame(MovePhase.INTRO, StagedTimeline.phaseAt(WALK, 1));
      assertSame(MovePhase.MAIN, StagedTimeline.phaseAt(WALK, 2));
      assertSame(MovePhase.FINISH, StagedTimeline.phaseAt(WALK, 5));
      assertSame(MovePhase.FINISH, StagedTimeline.phaseAt(WALK, 99));
      assertEquals(0, StagedTimeline.phaseStart(WALK, MovePhase.INTRO));
      assertEquals(2, StagedTimeline.phaseStart(WALK, MovePhase.MAIN));
      assertEquals(5, StagedTimeline.phaseStart(WALK, MovePhase.FINISH));
   }

   @Test
   void phaseOrdinalsAreStable() {
      assertEquals(0, MovePhase.INTRO.ordinal());
      assertEquals(1, MovePhase.MAIN.ordinal());
      assertEquals(2, MovePhase.FINISH.ordinal());
      assertSame(MovePhase.INTRO, MovePhase.byOrdinal(7));
      assertTrue(MovePhase.FINISH.atLeast(MovePhase.MAIN));
      assertFalse(MovePhase.INTRO.atLeast(MovePhase.MAIN));
      assertFalse(MovePhase.FINISH.atLeast(null));
   }

   @Test
   void skipToJumpsOnlyForwardIntoAPhaseWithTicks() {
      StagedTimeline<Move> timeline = new StagedTimeline<>();
      assertEquals(StagedTimeline.Event.NONE, timeline.skipTo(MovePhase.MAIN), "idle: nothing to skip");
      timeline.request(WALK, -1);
      assertEquals(StagedTimeline.Event.ENTER_MAIN, timeline.skipTo(MovePhase.MAIN));
      assertEquals(MovePhase.MAIN, timeline.phase());
      assertEquals(WALK.intro(), timeline.elapsed(), "first tick of MAIN");
      assertEquals(StagedTimeline.Event.NONE, timeline.skipTo(MovePhase.MAIN), "never the current phase");
      assertEquals(StagedTimeline.Event.NONE, timeline.skipTo(MovePhase.INTRO), "never backwards");
      assertEquals(StagedTimeline.Event.ENTER_FINISH, timeline.skipTo(MovePhase.FINISH));
      assertEquals(WALK.intro() + WALK.main(), timeline.elapsed());

      StagedTimeline<Move> noFinish = new StagedTimeline<>();
      noFinish.request(move(7, 1, 1, 1, 0, null), -1);
      assertEquals(StagedTimeline.Event.NONE, noFinish.skipTo(MovePhase.FINISH), "a phase without ticks is not entered");
      assertEquals(MovePhase.INTRO, noFinish.phase());
   }
}

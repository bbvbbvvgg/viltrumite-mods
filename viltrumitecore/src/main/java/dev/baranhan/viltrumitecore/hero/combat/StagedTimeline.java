package dev.baranhan.viltrumitecore.hero.combat;

import javax.annotation.Nullable;

/**
 * Server-side player of one {@link StagedMove} at a time: intro → main →
 * finish, combo steps, a one-press buffer, priorities, cancel windows and an
 * optional partner for two-character scenes. Pure (no world access): the hero
 * reacts to the returned {@link Admission} and {@link Event} values and owns
 * every effect. Clients see {@link #wireId()}, {@link #elapsed()},
 * {@link #length()}, {@link #variant()} and {@link #partnerId()} through the
 * public snapshot; the partner never runs its own copy (the owner's snapshot
 * is the single clock of a paired scene).
 */
public final class StagedTimeline<M extends StagedMove> {
   /** Outcome of a request. */
   public enum Admission {
      /** Nothing was running: the move starts. */
      START,
      /** A running move stopped for this one (higher priority, or its cancel window). */
      INTERRUPT,
      /** Same move inside its cancel window: the next combo step starts now. */
      CHAIN,
      /** Same move before its cancel window: the next step is buffered (one press). */
      QUEUE,
      /** Not now (busy, unauthored move, missing partner, full buffer). */
      REFUSE;

      /** The requested move (or its next step) runs from now on. */
      public boolean started() {
         return this == START || this == INTERRUPT || this == CHAIN;
      }
   }

   /** What one tick changed. */
   public enum Event {
      NONE,
      ENTER_MAIN,
      ENTER_FINISH,
      /** A buffered step started. */
      CHAIN,
      /** The last phase of the step ended; the timeline is idle. */
      END
   }

   @Nullable
   private M move;
   private MovePhase phase = MovePhase.INTRO;
   private int elapsed;
   private int step;
   private int partnerId = -1;
   @Nullable
   private M queued;
   private int queuedPartner = -1;
   /** Own phase ticks of the current step (intro, main, finish), null = the move's. Set by {@link #retime}, dropped by every new step. */
   @Nullable
   private int[] stepTicks;

   public boolean running() {
      return this.move != null;
   }

   @Nullable
   public M move() {
      return this.move;
   }

   public boolean is(M candidate) {
      return this.move != null && this.move == candidate;
   }

   public MovePhase phase() {
      return this.phase;
   }

   /** Ticks since the current step began. */
   public int elapsed() {
      return this.elapsed;
   }

   /** Ticks of the current step, 0 when idle. */
   public int length() {
      if (this.move == null) {
         return 0;
      }

      int total = 0;
      for (MovePhase phase : MovePhase.values()) {
         total += this.stepTicks(phase);
      }

      return total;
   }

   /** Ticks of one phase of the current step (its own ticks after {@link #retime}), 0 when idle. */
   public int stepTicks(MovePhase phase) {
      if (this.move == null) {
         return 0;
      }

      return Math.max(0, this.stepTicks != null ? this.stepTicks[phase.ordinal()] : this.move.ticks(phase, this.step));
   }

   /** Ticks since the current phase began. */
   public int phaseElapsed() {
      return this.move == null ? 0 : this.elapsed - this.stepPhaseStart(this.phase);
   }

   /**
    * Gives the current step its own phase ticks: a combo step whose content
    * (chosen when the step starts) has its own length, e.g. a heavy finisher
    * after a light opener. Only on the first tick of a step (a fresh start or
    * a chained step); the move's priority, cancel window and step count stay.
    * The next step starts with the move's ticks again. Returns false (and
    * changes nothing) when idle, past the first tick, or for invalid ticks.
    */
   public boolean retime(int intro, int main, int finish) {
      if (this.move == null || this.elapsed != 0 || intro < 0 || main < 0 || finish < 0 || intro + main + finish <= 0) {
         return false;
      }

      this.stepTicks = new int[]{intro, main, finish};
      this.phase = this.stepPhaseAt(0);
      return true;
   }

   public int step() {
      return this.step;
   }

   /** Partner entity id of a paired move, -1 otherwise. */
   public int partnerId() {
      return this.partnerId;
   }

   @Nullable
   public M queued() {
      return this.queued;
   }

   /** Snapshot actionId: the move's wire id, -1 when idle. */
   public int wireId() {
      return this.move == null ? -1 : this.move.wireId();
   }

   /** Snapshot variant: phase in bits 0-1, combo step above. 0 when idle. */
   public int variant() {
      return this.move == null ? 0 : pack(this.phase, this.step);
   }

   public static int pack(MovePhase phase, int step) {
      return phase.ordinal() | Math.max(0, step) << 2;
   }

   public static MovePhase phaseOf(int variant) {
      return MovePhase.byOrdinal(variant & 3);
   }

   public static int stepOf(int variant) {
      return Math.max(0, variant >> 2);
   }

   /**
    * Pure admission decision, no state change. {@code partnerId} -1 keeps the
    * current partner when the same move chains.
    */
   public Admission admit(@Nullable M next, int partnerId) {
      if (next == null || next.length() <= 0) {
         return Admission.REFUSE;
      }

      M current = this.move;
      int partner = current != null && next == current && partnerId < 0 ? this.partnerId : partnerId;
      if (next.paired() && partner < 0) {
         return Admission.REFUSE;
      }

      if (current == null) {
         return Admission.START;
      }

      boolean window = this.phase.atLeast(current.cancellableFrom());
      // A chain of steps never overflows: past the last step the count wraps to 0 (review B-m2),
      // so the n-th press of a long chain buffers like any other instead of being refused.
      if (next == current && current.maxSteps() > 1) {
         if (window) {
            return Admission.CHAIN;
         }

         return this.queued == null ? Admission.QUEUE : Admission.REFUSE;
      }

      if (window) {
         return Admission.INTERRUPT;
      }

      if (next.priority() > current.priority() && !current.resists(InterruptReason.INPUT, this.phase)) {
         return Admission.INTERRUPT;
      }

      return Admission.REFUSE;
   }

   /**
    * Admit and apply. Read {@link #move()} before calling when the replaced
    * move needs cleanup (INTERRUPT). A started move is in {@link #phase()} now:
    * see {@link #entryEvent()}.
    */
   public Admission request(@Nullable M next, int partnerId) {
      Admission admission = this.admit(next, partnerId);
      switch (admission) {
         case START, INTERRUPT -> this.begin(next, partnerId, 0);
         case CHAIN -> this.begin(next, partnerId < 0 ? this.partnerId : partnerId, this.nextStep(next));
         case QUEUE -> {
            this.queued = next;
            this.queuedPartner = partnerId < 0 ? this.partnerId : partnerId;
         }
         case REFUSE -> {
         }
      }

      return admission;
   }

   /**
    * Like {@link #request} but a START / INTERRUPT begins at combo step {@code step}
    * (a sequence whose step the hero counts itself, e.g. escalating grab punches).
    * CHAIN and QUEUE keep the timeline's own step order.
    */
   public Admission requestAt(@Nullable M next, int partnerId, int step) {
      Admission admission = this.admit(next, partnerId);
      if (admission == Admission.START || admission == Admission.INTERRUPT) {
         this.begin(next, partnerId, Math.max(0, step));
         return admission;
      }

      if (admission == Admission.CHAIN) {
         this.begin(next, partnerId < 0 ? this.partnerId : partnerId, this.nextStep(next));
      } else if (admission == Admission.QUEUE) {
         this.queued = next;
         this.queuedPartner = partnerId < 0 ? this.partnerId : partnerId;
      }

      return admission;
   }

   /** The step after the current one, wrapping inside {@code move}'s step count. */
   private int nextStep(M move) {
      return (this.step + 1) % Math.max(1, move.maxSteps());
   }

   /** The phase event a freshly started step stands in (ENTER_MAIN for a move without intro), NONE for INTRO or idle. */
   public Event entryEvent() {
      if (this.move == null || this.elapsed != 0) {
         return Event.NONE;
      }

      return switch (this.phase) {
         case INTRO -> Event.NONE;
         case MAIN -> Event.ENTER_MAIN;
         case FINISH -> Event.ENTER_FINISH;
      };
   }

   /** One server tick. */
   public Event tick() {
      M current = this.move;
      if (current == null) {
         return Event.NONE;
      }

      if (this.queued != null && this.phase.atLeast(current.cancellableFrom())) {
         this.begin(this.queued, this.queuedPartner, this.nextStep(this.queued));
         return Event.CHAIN;
      }

      this.elapsed++;
      if (this.elapsed >= this.length()) {
         if (this.queued != null) {
            this.begin(this.queued, this.queuedPartner, this.nextStep(this.queued));
            return Event.CHAIN;
         }

         this.clear();
         return Event.END;
      }

      MovePhase now = this.stepPhaseAt(this.elapsed);
      if (now != this.phase) {
         this.phase = now;
         return now == MovePhase.MAIN ? Event.ENTER_MAIN : Event.ENTER_FINISH;
      }

      return Event.NONE;
   }

   /**
    * Stops the running move unless it resists the reason in its current
    * phase (never for LIFECYCLE). Returns the stopped move, or null when
    * nothing stopped.
    */
   @Nullable
   public M interrupt(InterruptReason reason) {
      M current = this.move;
      if (current == null) {
         return null;
      }

      if (reason != InterruptReason.LIFECYCLE && current.resists(reason, this.phase)) {
         return null;
      }

      this.clear();
      return current;
   }

   /**
    * Jump the running step forward to the first tick of a later phase (a
    * held step released early, e.g. a throw whose hold ends on the release).
    * Returns the event of the phase now entered, NONE when idle, when the
    * phase is not later than the current one or has no ticks.
    */
   public Event skipTo(MovePhase target) {
      M current = this.move;
      if (current == null || target.ordinal() <= this.phase.ordinal() || this.stepTicks(target) <= 0) {
         return Event.NONE;
      }

      this.elapsed = this.stepPhaseStart(target);
      this.phase = target;
      return target == MovePhase.MAIN ? Event.ENTER_MAIN : Event.ENTER_FINISH;
   }

   /** Idle, no buffer, no partner. */
   public void clear() {
      this.move = null;
      this.phase = MovePhase.INTRO;
      this.elapsed = 0;
      this.step = 0;
      this.partnerId = -1;
      this.queued = null;
      this.queuedPartner = -1;
      this.stepTicks = null;
   }

   private void begin(M next, int partner, int step) {
      this.move = next;
      this.step = Math.min(step, Math.max(0, next.maxSteps() - 1));
      this.partnerId = next.paired() ? partner : -1;
      this.elapsed = 0;
      this.queued = null;
      this.queuedPartner = -1;
      this.stepTicks = null;
      this.phase = phaseAt(next, 0, this.step);
   }

   private MovePhase stepPhaseAt(int elapsed) {
      int end = 0;
      for (MovePhase phase : MovePhase.values()) {
         end += this.stepTicks(phase);
         if (elapsed < end) {
            return phase;
         }
      }

      return MovePhase.FINISH;
   }

   private int stepPhaseStart(MovePhase phase) {
      int start = 0;
      for (MovePhase earlier : MovePhase.values()) {
         if (earlier == phase) {
            break;
         }

         start += this.stepTicks(earlier);
      }

      return start;
   }

   /** Phase at a tick of one step: unauthored (0-tick) phases are skipped. */
   public static MovePhase phaseAt(StagedMove move, int elapsed) {
      return phaseAt(move, elapsed, 0);
   }

   /** Phase at a tick of combo step {@code step} ({@link StagedMove#ticks(MovePhase, int)}). */
   public static MovePhase phaseAt(StagedMove move, int elapsed, int step) {
      int end = 0;
      for (MovePhase phase : MovePhase.values()) {
         end += Math.max(0, move.ticks(phase, step));
         if (elapsed < end) {
            return phase;
         }
      }

      return MovePhase.FINISH;
   }

   /** First tick of a phase within one step. */
   public static int phaseStart(StagedMove move, MovePhase phase) {
      return phaseStart(move, phase, 0);
   }

   /** First tick of a phase within combo step {@code step}. */
   public static int phaseStart(StagedMove move, MovePhase phase, int step) {
      int start = 0;
      for (MovePhase earlier : MovePhase.values()) {
         if (earlier == phase) {
            break;
         }

         start += Math.max(0, move.ticks(earlier, step));
      }

      return start;
   }
}

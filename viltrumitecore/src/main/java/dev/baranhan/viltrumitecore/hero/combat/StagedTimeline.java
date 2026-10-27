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
      return this.move == null ? 0 : this.move.length();
   }

   /** Ticks since the current phase began. */
   public int phaseElapsed() {
      return this.move == null ? 0 : this.elapsed - phaseStart(this.move, this.phase);
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
      if (next == current && this.step + 1 < current.maxSteps()) {
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
         case CHAIN -> this.begin(next, partnerId < 0 ? this.partnerId : partnerId, this.step + 1);
         case QUEUE -> {
            this.queued = next;
            this.queuedPartner = partnerId < 0 ? this.partnerId : partnerId;
         }
         case REFUSE -> {
         }
      }

      return admission;
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
         this.begin(this.queued, this.queuedPartner, this.step + 1);
         return Event.CHAIN;
      }

      this.elapsed++;
      if (this.elapsed >= current.length()) {
         if (this.queued != null) {
            this.begin(this.queued, this.queuedPartner, this.step + 1);
            return Event.CHAIN;
         }

         this.clear();
         return Event.END;
      }

      MovePhase now = phaseAt(current, this.elapsed);
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

   /** Idle, no buffer, no partner. */
   public void clear() {
      this.move = null;
      this.phase = MovePhase.INTRO;
      this.elapsed = 0;
      this.step = 0;
      this.partnerId = -1;
      this.queued = null;
      this.queuedPartner = -1;
   }

   private void begin(M next, int partner, int step) {
      this.move = next;
      this.step = Math.min(step, Math.max(0, next.maxSteps() - 1));
      this.partnerId = next.paired() ? partner : -1;
      this.elapsed = 0;
      this.queued = null;
      this.queuedPartner = -1;
      this.phase = phaseAt(next, 0);
   }

   /** Phase at a tick of one step: unauthored (0-tick) phases are skipped. */
   public static MovePhase phaseAt(StagedMove move, int elapsed) {
      int end = 0;
      for (MovePhase phase : MovePhase.values()) {
         end += Math.max(0, move.ticks(phase));
         if (elapsed < end) {
            return phase;
         }
      }

      return MovePhase.FINISH;
   }

   /** First tick of a phase within one step. */
   public static int phaseStart(StagedMove move, MovePhase phase) {
      int start = 0;
      for (MovePhase earlier : MovePhase.values()) {
         if (earlier == phase) {
            break;
         }

         start += Math.max(0, move.ticks(earlier));
      }

      return start;
   }
}

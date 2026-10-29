package dev.baranhan.viltrumitecore.client.hero.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/**
 * Pure director of the subtitle panel: exactly one line on screen, the rest
 * wait (at most {@link #MAX_WAITING}, each only for a short while). A line of
 * the same speaker or of a strictly higher priority cuts the shown one short
 * (fast fade, then it plays); equal priorities take turns, the local player's
 * own lines first. The same line is never queued twice.
 */
public final class SubtitleQueue<C extends SubtitleQueue.Cue> {
   public static final int MAX_WAITING = 3;

   public interface Cue {
      int serial();

      UUID speaker();

      String lineId();

      int priority();

      boolean own();
   }

   public static final class Slot<C> {
      private final C cue;
      private final double arrived;
      private double started = Double.NaN;
      private double forcedAt = Double.NaN;
      private int length;

      private Slot(C cue, double arrived) {
         this.cue = cue;
         this.arrived = arrived;
      }

      public C cue() {
         return this.cue;
      }

      /** Clock time it went on screen (NaN while waiting). */
      public double started() {
         return this.started;
      }

      /** Forced-exit time relative to {@link #started()}, or NaN. */
      public double forcedAt() {
         return Double.isNaN(this.forcedAt) ? Double.NaN : this.forcedAt - this.started;
      }

      public boolean exiting() {
         return !Double.isNaN(this.forcedAt);
      }

      /** Re-measured length (e.g. after the window was resized). */
      public void length(int ticks) {
         this.length = ticks;
      }

      boolean finished(double now) {
         return now - this.started >= this.length || this.exiting() && now >= this.forcedAt + SubtitleTiming.FORCED_EXIT;
      }

      void forceExit(double now) {
         if (!this.exiting()) {
            this.forcedAt = now;
         }
      }
   }

   private final List<Slot<C>> waiting = new ArrayList<>();
   @Nullable
   private Slot<C> active;

   /** Longest wait of a queued line by priority (ticks): a stale line is dropped, not shown late. */
   public static int maxWait(int priority) {
      return switch (priority) {
         case 0 -> 60;
         case 1 -> 120;
         default -> 160;
      };
   }

   /** Accepts a line; false when it is a duplicate. */
   public boolean offer(C cue, double now) {
      if (this.has(slot -> slot.cue.serial() == cue.serial())
         || this.has(slot -> !slot.exiting() && slot.cue.speaker().equals(cue.speaker()) && slot.cue.lineId().equals(cue.lineId()))) {
         return false;
      }

      Slot<C> slot = new Slot<>(cue, now);
      Slot<C> shown = this.active;
      if (shown != null && !shown.exiting() && (shown.cue.speaker().equals(cue.speaker()) || cue.priority() > shown.cue.priority())) {
         this.waiting.removeIf(other -> other.cue.speaker().equals(cue.speaker()));
         this.waiting.add(0, slot);
         shown.forceExit(now);
         this.trim();
         return true;
      }

      this.waiting.removeIf(other -> other.cue.speaker().equals(cue.speaker()));
      int at = 0;
      while (at < this.waiting.size() && !before(cue, this.waiting.get(at).cue)) {
         at++;
      }

      this.waiting.add(at, slot);
      this.trim();
      return true;
   }

   /** Stops one line: fades it out when shown, forgets it when waiting. */
   public void stop(int serial, double now) {
      this.stopIf(cue -> cue.serial() == serial, now);
   }

   public void stopIf(Predicate<C> which, double now) {
      this.waiting.removeIf(slot -> which.test(slot.cue));
      if (this.active != null && which.test(this.active.cue)) {
         this.active.forceExit(now);
      }
   }

   public void clear() {
      this.waiting.clear();
      this.active = null;
   }

   public boolean isEmpty() {
      return this.active == null && this.waiting.isEmpty();
   }

   public int waitingCount() {
      return this.waiting.size();
   }

   @Nullable
   public Slot<C> active() {
      return this.active;
   }

   /**
    * Advances to {@code now}: ends the finished line, drops stale waiting ones
    * and starts the next, measured by {@code length} (ticks incl. exit; 0 = skip).
    */
   @Nullable
   public Slot<C> update(double now, ToIntFunction<C> length) {
      if (this.active != null && this.active.finished(now)) {
         this.active = null;
      }

      this.waiting.removeIf(slot -> now - slot.arrived > maxWait(slot.cue.priority()));
      while (this.active == null && !this.waiting.isEmpty()) {
         Slot<C> next = this.waiting.remove(0);
         int ticks = length.applyAsInt(next.cue);
         if (ticks > 0) {
            next.started = now;
            next.length = ticks;
            this.active = next;
         }
      }

      return this.active;
   }

   private boolean has(Predicate<Slot<C>> test) {
      if (this.active != null && test.test(this.active)) {
         return true;
      }

      for (Slot<C> slot : this.waiting) {
         if (test.test(slot)) {
            return true;
         }
      }

      return false;
   }

   private void trim() {
      while (this.waiting.size() > MAX_WAITING) {
         this.waiting.remove(this.waiting.size() - 1);
      }
   }

   /** Queue order: higher priority, then own lines, then arrival. */
   private static boolean before(Cue a, Cue b) {
      if (a.priority() != b.priority()) {
         return a.priority() > b.priority();
      }

      return a.own() && !b.own();
   }
}

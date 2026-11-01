package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Diminishing returns for every hold the war puts on a hero (pure): Thanos's time stop, compulsion,
 * anomaly snap and reality lift, Ebony Maw's telekinetic grab, Cull Obsidian's grab. Within one
 * {@link #WINDOW} the first control gets its full length, the second half, the third a quarter;
 * after the third the hero is immune for {@link #IMMUNITY} ticks. A hero already held is never
 * held again on top (no chained stuns): the new control is refused.
 */
public final class ThanosControlLedger {
   public static final int WINDOW = 200;
   public static final int IMMUNITY = 120;
   /** Shortest control worth granting (shorter ones are refused). */
   public static final int MIN_TICKS = 4;

   private static final class Entry {
      long windowStart;
      int count;
      long until = Long.MIN_VALUE;
      long immuneUntil = Long.MIN_VALUE;
   }

   private final Map<UUID, Entry> entries = new HashMap<>();

   /** Share of the wanted length for the n-th control in a window (1-based). */
   public static float factor(int n) {
      return n <= 1 ? 1.0F : n == 2 ? 0.5F : 0.25F;
   }

   /**
    * Grants a control of up to {@code ticks} to {@code id} now: the ticks it may last (0 = refused:
    * already held, immune, or too short after the diminishing returns).
    */
   public int grant(UUID id, int ticks, long now) {
      if (ticks <= 0) {
         return 0;
      }

      Entry e = this.entries.computeIfAbsent(id, k -> new Entry());
      if (now < e.until || now < e.immuneUntil) {
         return 0;
      }

      if (e.count > 0 && now - e.windowStart >= WINDOW) {
         e.count = 0;
      }

      if (e.count == 0) {
         e.windowStart = now;
      }

      int granted = Math.round(ticks * factor(e.count + 1));
      if (granted < MIN_TICKS) {
         return 0;
      }

      e.count++;
      e.until = now + granted;
      if (e.count >= 3) {
         e.immuneUntil = e.until + IMMUNITY;
         e.count = 0;
      }

      return granted;
   }

   /** The hero broke free (or the holder fell): the hold ends now, the count stays. */
   public void release(UUID id, long now) {
      Entry e = this.entries.get(id);
      if (e != null && e.until > now) {
         e.until = now;
      }
   }

   public boolean controlled(UUID id, long now) {
      Entry e = this.entries.get(id);
      return e != null && now < e.until;
   }

   public boolean immune(UUID id, long now) {
      Entry e = this.entries.get(id);
      return e != null && now < e.immuneUntil;
   }

   /** May a new control land on this hero now (neither held nor immune)? */
   public boolean controllable(UUID id, long now) {
      return !this.controlled(id, now) && !this.immune(id, now);
   }

   /** Drops entries whose window, hold and immunity are all over. */
   public void prune(long now) {
      for (Iterator<Entry> it = this.entries.values().iterator(); it.hasNext(); ) {
         Entry e = it.next();
         if (now >= e.until && now >= e.immuneUntil && (e.count == 0 || now - e.windowStart >= WINDOW)) {
            it.remove();
         }
      }
   }

   public int size() {
      return this.entries.size();
   }

   public void clear() {
      this.entries.clear();
   }
}

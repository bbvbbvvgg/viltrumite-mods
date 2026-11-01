package dev.baranhan.viltrumitecore.worldevent.chitauri.war;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Pure record of every person the hive took (no Minecraft types): who, in which state, held by whom, where last seen and
 * a snapshot of the person (SNBT) so nobody is ever lost when their chunk unloads while held. The server wraps it in
 * saved data; the rules here decide what may happen next.
 */
public final class CaptiveLedger {
   public enum State {
      /** On a captor's shoulder. */
      CARRIED,
      /** In a containment field. */
      HELD,
      /** The body vanished with its chunk while held: restore from the snapshot when the place ticks again. */
      LOST
   }

   public static final class Entry {
      public final UUID id;
      public final String type;
      public State state;
      /** Captor or containment pylon. */
      @Nullable
      public UUID holder;
      public double x;
      public double y;
      public double z;
      /** SNBT of the person (non-saved citizens only), or "". */
      public String snapshot = "";
      public int since;

      public Entry(UUID id, String type, State state, @Nullable UUID holder, double x, double y, double z, int since) {
         this.id = id;
         this.type = type;
         this.state = state;
         this.holder = holder;
         this.x = x;
         this.y = y;
         this.z = z;
         this.since = since;
      }

      public boolean active() {
         return this.state == State.CARRIED || this.state == State.HELD;
      }
   }

   private final Map<UUID, Entry> entries = new LinkedHashMap<>();
   public int taken;
   public int delivered;
   public int freed;
   public int dropped;

   public Collection<Entry> entries() {
      return this.entries.values();
   }

   @Nullable
   public Entry get(UUID id) {
      return this.entries.get(id);
   }

   public boolean isEmpty() {
      return this.entries.isEmpty();
   }

   /** People carried or held right now. */
   public int active() {
      int n = 0;
      for (Entry e : this.entries.values()) {
         if (e.active()) {
            n++;
         }
      }
      return n;
   }

   public boolean canTake() {
      return this.active() < WarRules.MAX_CAPTIVES;
   }

   public int heldBy(UUID holder) {
      int n = 0;
      for (Entry e : this.entries.values()) {
         if (e.state == State.HELD && holder.equals(e.holder)) {
            n++;
         }
      }
      return n;
   }

   public List<Entry> listHeldBy(UUID holder) {
      List<Entry> out = new ArrayList<>();
      for (Entry e : this.entries.values()) {
         if (e.state == State.HELD && holder.equals(e.holder)) {
            out.add(e);
         }
      }
      return out;
   }

   public boolean hasRoom(UUID beacon) {
      return this.heldBy(beacon) < WarRules.CONTAINMENT_CAPACITY;
   }

   /** A captor grabbed someone; false when the hive holds as many as it may (or the person is already taken). */
   public boolean carry(UUID id, String type, UUID captor, double x, double y, double z, int now) {
      Entry old = this.entries.get(id);
      if (old != null && old.active() || !this.canTake()) {
         return false;
      }
      this.entries.put(id, new Entry(id, type, State.CARRIED, captor, x, y, z, now));
      this.taken++;
      return true;
   }

   /** The captor reached a containment field with room; false = no room (the caller frees the person). */
   public boolean deliver(UUID id, UUID beacon, int now) {
      Entry e = this.entries.get(id);
      if (e == null || e.state != State.CARRIED || !this.hasRoom(beacon)) {
         return false;
      }
      e.state = State.HELD;
      e.holder = beacon;
      e.since = now;
      this.delivered++;
      return true;
   }

   /** The person is free again. {@code byHero}: a hero freed them (counts for the director and the report). */
   @Nullable
   public Entry release(UUID id, boolean byHero) {
      Entry e = this.entries.remove(id);
      if (e != null) {
         if (byHero) {
            this.freed++;
         } else {
            this.dropped++;
         }
      }
      return e;
   }

   /** The body vanished with its chunk: keep the record for the restore. */
   public void lose(UUID id) {
      Entry e = this.entries.get(id);
      if (e != null) {
         e.state = State.LOST;
         e.holder = null;
      }
   }

   /** Forget a record without counting (the person died, or was restored and freed). */
   public void forget(UUID id) {
      this.entries.remove(id);
   }

   /** Everyone still carried or held goes free (invasion over); records of lost bodies stay for their restore. */
   public List<Entry> releaseAll() {
      List<Entry> out = new ArrayList<>();
      for (Entry e : List.copyOf(this.entries.values())) {
         if (e.active()) {
            out.add(e);
            this.entries.remove(e.id);
            this.dropped++;
         }
      }
      return out;
   }

   /** Saved-data load: puts a record back as it was saved. */
   public void entriesForLoad(Entry e) {
      this.entries.put(e.id, e);
   }

   public void resetCounters() {
      this.taken = 0;
      this.delivered = 0;
      this.freed = 0;
      this.dropped = 0;
   }
}

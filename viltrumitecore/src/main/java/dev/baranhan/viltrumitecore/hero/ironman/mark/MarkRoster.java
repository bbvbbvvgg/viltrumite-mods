package dev.baranhan.viltrumitecore.hero.ironman.mark;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Durability, cooldown and location of every mark (spec §12.3, §12.7, §16).
 * One mark = one instance = one durability: the location changes only through
 * {@link #move}, which refuses an unexpected source (two choose packets in a
 * row cannot create a second instance). Persisted under {@code MarkRoster}.
 */
public final class MarkRoster {
   public static final String KEY = "MarkRoster";
   /** Broken mark unavailable ~5 min (spec §12.7); durability is full again after it. */
   public static final int BREAK_COOLDOWN = 6000;
   private final Map<MarkId, Entry> entries = new EnumMap<>(MarkId.class);

   public MarkRoster() {
      this.reset();
   }

   private static final class Entry {
      float durability;
      int cooldown;
      MarkLocation location = MarkLocation.STORED;
   }

   private Entry entry(MarkId id) {
      return this.entries.get(id);
   }

   public float durability(MarkId id) {
      return this.entry(id).durability;
   }

   public float maxDurability(MarkId id) {
      return MarkSpec.of(id).durability();
   }

   public void setDurability(MarkId id, float durability) {
      this.entry(id).durability = Math.max(0.0F, Math.min(this.maxDurability(id), durability));
   }

   /** Debug reset: every mark's break cooldown ends now (durability returns as on a natural end). */
   public void clearCooldowns() {
      for (Entry entry : this.entries.values()) {
         if (entry.cooldown > 0) {
            entry.cooldown = 1;
         }
      }
   }

   public int cooldown(MarkId id) {
      return this.entry(id).cooldown;
   }

   public MarkLocation location(MarkId id) {
      return this.entry(id).location;
   }

   /** Choosable in the Veronica menu: no cooldown, in storage or standing as the empty suit (spec §12.3). */
   public boolean choosable(MarkId id) {
      Entry entry = this.entry(id);
      return entry.cooldown <= 0 && entry.durability > 0.0F && (entry.location == MarkLocation.STORED || entry.location == MarkLocation.EMPTY);
   }

   /** Guarded transition: false (and nothing changes) when the mark is not at {@code from}. */
   public boolean move(MarkId id, MarkLocation from, MarkLocation to) {
      Entry entry = this.entry(id);
      if (entry.location != from) {
         return false;
      }

      entry.location = to;
      return true;
   }

   /** The worn mark, or null. */
   @Nullable
   public MarkId worn() {
      return this.at(MarkLocation.WORN);
   }

   @Nullable
   public MarkId at(MarkLocation location) {
      for (Map.Entry<MarkId, Entry> e : this.entries.entrySet()) {
         if (e.getValue().location == location) {
            return e.getKey();
         }
      }

      return null;
   }

   /** Durability 0: back to storage, ~5 min cooldown, durability restored when it ends. */
   public void breakMark(MarkId id) {
      Entry entry = this.entry(id);
      entry.location = MarkLocation.STORED;
      entry.durability = 0.0F;
      entry.cooldown = BREAK_COOLDOWN;
   }

   public void tick() {
      for (Map.Entry<MarkId, Entry> e : this.entries.entrySet()) {
         Entry entry = e.getValue();
         if (entry.cooldown > 0 && --entry.cooldown == 0) {
            entry.durability = this.maxDurability(e.getKey());
         }
      }
   }

   /** World instances are gone (logout, death, dimension): empty suit and delivery go back to storage. */
   public void recallWorld() {
      for (Entry entry : this.entries.values()) {
         if (entry.location == MarkLocation.EMPTY || entry.location == MarkLocation.IN_DELIVERY) {
            entry.location = MarkLocation.STORED;
         }
      }
   }

   /** Hero change: everything fresh (spec §2). */
   public void reset() {
      for (MarkId id : MarkId.values()) {
         Entry entry = new Entry();
         entry.durability = MarkSpec.of(id).durability();
         this.entries.put(id, entry);
      }
   }

   public void save(CompoundTag tag) {
      CompoundTag roster = new CompoundTag();
      for (Map.Entry<MarkId, Entry> e : this.entries.entrySet()) {
         CompoundTag mark = new CompoundTag();
         mark.putFloat("Durability", e.getValue().durability);
         mark.putInt("Cooldown", e.getValue().cooldown);
         mark.putString("Location", e.getValue().location.name());
         roster.put(e.getKey().key(), mark);
      }

      tag.put(KEY, roster);
   }

   public void load(CompoundTag tag) {
      this.reset();
      if (!tag.contains(KEY, 10)) {
         return;
      }

      CompoundTag roster = tag.getCompound(KEY);
      for (MarkId id : MarkId.values()) {
         if (!roster.contains(id.key(), 10)) {
            continue;
         }

         CompoundTag mark = roster.getCompound(id.key());
         Entry entry = this.entry(id);
         float durability = mark.getFloat("Durability");
         entry.durability = Float.isFinite(durability) ? Math.max(0.0F, Math.min(this.maxDurability(id), durability)) : this.maxDurability(id);
         entry.cooldown = Math.max(0, Math.min(BREAK_COOLDOWN, mark.getInt("Cooldown")));
         try {
            entry.location = MarkLocation.valueOf(mark.getString("Location"));
         } catch (IllegalArgumentException ignored) {
            entry.location = MarkLocation.STORED;
         }
      }

      // World instances never survive a relog (spec §16): the pod and the empty suit fly away.
      this.recallWorld();
   }

   /** Copy for a respawn clone: cooldowns and durability carry over (spec §16). */
   public void copyFrom(MarkRoster other) {
      for (MarkId id : MarkId.values()) {
         Entry from = other.entry(id);
         Entry to = this.entry(id);
         to.durability = from.durability;
         to.cooldown = from.cooldown;
         to.location = from.location;
      }
   }
}

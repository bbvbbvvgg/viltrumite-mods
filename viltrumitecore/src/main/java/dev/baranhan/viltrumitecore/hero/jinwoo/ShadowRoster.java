package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The shadows Jinwoo owns (spec §5.7, §6, §8): at most {@link JinwooRules#ARMY_ROSTER},
 * saved in the hero NBT, kept across death and hero changes. Active entity UUIDs are
 * transient (shadows are never saved to the world).
 */
public final class ShadowRoster {
   public static final String NBT = "ShadowRoster";

   public static final class Entry {
      public final int serial;
      public final ShadowKind kind;
      public final boolean elite;
      public int revive;
      @Nullable
      public UUID active;

      public Entry(int serial, ShadowKind kind, boolean elite, int revive) {
         this.serial = serial;
         this.kind = kind;
         this.elite = elite;
         this.revive = revive;
      }

      public boolean ready() {
         return this.revive <= 0 && this.active == null;
      }
   }

   private final List<Entry> entries = new ArrayList<>();
   private int nextSerial;

   public List<Entry> entries() {
      return Collections.unmodifiableList(this.entries);
   }

   public int size() {
      return this.entries.size();
   }

   public boolean full() {
      return this.entries.size() >= JinwooRules.ARMY_ROSTER;
   }

   public boolean hasElite(ShadowKind kind) {
      for (Entry entry : this.entries) {
         if (entry.elite && entry.kind == kind) {
            return true;
         }
      }
      return false;
   }

   /** Start roster (spec §2): Igris and three infantry, only when empty. */
   public void seedIfEmpty() {
      if (this.entries.isEmpty()) {
         this.add(ShadowKind.KNIGHT, true);
         this.add(ShadowKind.INFANTRY, false);
         this.add(ShadowKind.INFANTRY, false);
         this.add(ShadowKind.INFANTRY, false);
      }
   }

   /**
    * Adds an extracted shadow. The first insect becomes Beru, the first beast Tank
    * (spec §5.7). Returns null when the roster is full.
    */
   @Nullable
   public Entry addExtracted(ShadowKind kind) {
      if (this.full()) {
         return null;
      }
      boolean elite = kind.eliteName() != null && kind != ShadowKind.KNIGHT && !this.hasElite(kind);
      return this.add(kind, elite);
   }

   @Nullable
   public Entry add(ShadowKind kind, boolean elite) {
      if (this.full()) {
         return null;
      }
      Entry entry = new Entry(this.nextSerial++, kind, elite, 0);
      this.entries.add(entry);
      return entry;
   }

   @Nullable
   public Entry bySerial(int serial) {
      for (Entry entry : this.entries) {
         if (entry.serial == serial) {
            return entry;
         }
      }
      return null;
   }

   @Nullable
   public Entry byActive(UUID uuid) {
      for (Entry entry : this.entries) {
         if (uuid.equals(entry.active)) {
            return entry;
         }
      }
      return null;
   }

   /** Ready entries in summon order: elites first, then by serial (spec §5.10). */
   public List<Entry> summonOrder(int limit) {
      List<Entry> out = new ArrayList<>();
      for (int pass = 0; pass < 2 && out.size() < limit; pass++) {
         for (Entry entry : this.entries) {
            if (out.size() >= limit) {
               break;
            }
            if (entry.ready() && entry.elite == (pass == 0)) {
               out.add(entry);
            }
         }
      }
      return out;
   }

   public int activeCount() {
      int n = 0;
      for (Entry entry : this.entries) {
         if (entry.active != null) {
            n++;
         }
      }
      return n;
   }

   /** A shadow died: it goes on the revive cooldown (spec §6.2). */
   public void fell(Entry entry) {
      entry.active = null;
      entry.revive = JinwooRules.SHADOW_REVIVE;
   }

   /** A shadow dissolved without dying (recall, leash, cleanup): ready again at once. */
   public void released(Entry entry) {
      entry.active = null;
   }

   public void clearActive() {
      for (Entry entry : this.entries) {
         entry.active = null;
      }
   }

   public void tick() {
      for (Entry entry : this.entries) {
         if (entry.revive > 0) {
            entry.revive--;
         }
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      ListTag list = new ListTag();
      for (Entry entry : this.entries) {
         CompoundTag e = new CompoundTag();
         e.putInt("Serial", entry.serial);
         e.putString("Kind", entry.kind.key());
         e.putBoolean("Elite", entry.elite);
         e.putInt("Revive", entry.revive);
         list.add(e);
      }
      tag.put("Entries", list);
      tag.putInt("Next", this.nextSerial);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.entries.clear();
      ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
      int max = -1;
      for (int i = 0; i < list.size() && this.entries.size() < JinwooRules.ARMY_ROSTER; i++) {
         CompoundTag e = list.getCompound(i);
         int serial = e.getInt("Serial");
         max = Math.max(max, serial);
         this.entries.add(new Entry(serial, ShadowKind.byKey(e.getString("Kind")), e.getBoolean("Elite"), Math.max(0, e.getInt("Revive"))));
      }
      this.nextSerial = Math.max(tag.getInt("Next"), max + 1);
   }

   public ShadowRoster copy() {
      ShadowRoster copy = new ShadowRoster();
      copy.load(this.save());
      return copy;
   }
}

package dev.baranhan.viltrumitecore.scp.scp106;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World state of SCP-106 (overworld data storage): sessions of the players in the pocket dimension, the next free
 * instance slot (never reused, so a new capture is always a new place), recapture immunity, the moved maze walls
 * of every instance and the natural-spawn clock.
 */
public final class Scp106SavedData extends SavedData {
   private static final String NAME = "viltrumitecore_scp106";
   private final Map<UUID, PocketSession> sessions = new LinkedHashMap<>();
   private final Map<UUID, Long> immuneUntil = new HashMap<>();
   private final Map<Integer, int[]> variants = new HashMap<>();
   private int nextSlot;
   private boolean naturalEnabled = true;
   private long lastNatural = Long.MIN_VALUE;
   private long nextCheck;

   public static Scp106SavedData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(Scp106SavedData::load, Scp106SavedData::new, NAME);
   }

   public Map<UUID, PocketSession> sessions() {
      return this.sessions;
   }

   public PocketSession session(UUID id) {
      return this.sessions.get(id);
   }

   public PocketSession open(UUID player) {
      PocketSession s = new PocketSession(player, this.nextSlot++);
      this.sessions.put(player, s);
      this.setDirty();
      return s;
   }

   public void close(UUID player) {
      if (this.sessions.remove(player) != null) {
         this.setDirty();
      }
   }

   public boolean immune(UUID player, long now) {
      Long until = this.immuneUntil.get(player);
      return until != null && now < until;
   }

   public void grantImmunity(UUID player, long until) {
      this.immuneUntil.put(player, until);
      this.setDirty();
   }

   public int[] variants(int slot) {
      return this.variants.get(slot);
   }

   public Map<Integer, int[]> allVariants() {
      return this.variants;
   }

   public void setVariants(int slot, int[] v) {
      this.variants.put(slot, v.clone());
      this.setDirty();
   }

   public int nextSlot() {
      return this.nextSlot;
   }

   public boolean naturalEnabled() {
      return this.naturalEnabled;
   }

   public void setNaturalEnabled(boolean on) {
      this.naturalEnabled = on;
      this.setDirty();
   }

   public long lastNatural() {
      return this.lastNatural;
   }

   public void markNatural(long now) {
      this.lastNatural = now;
      this.setDirty();
   }

   public long nextCheck() {
      return this.nextCheck;
   }

   public void setNextCheck(long tick) {
      this.nextCheck = tick;
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ListTag list = new ListTag();
      for (PocketSession s : this.sessions.values()) {
         list.add(s.save());
      }
      tag.put("sessions", list);
      CompoundTag imm = new CompoundTag();
      for (Map.Entry<UUID, Long> e : this.immuneUntil.entrySet()) {
         imm.putLong(e.getKey().toString(), e.getValue());
      }
      tag.put("immune", imm);
      CompoundTag var = new CompoundTag();
      for (Map.Entry<Integer, int[]> e : this.variants.entrySet()) {
         var.putIntArray(Integer.toString(e.getKey()), e.getValue());
      }
      tag.put("variants", var);
      tag.putInt("nextSlot", this.nextSlot);
      tag.putBoolean("natural", this.naturalEnabled);
      tag.putLong("lastNatural", this.lastNatural);
      tag.putLong("nextCheck", this.nextCheck);
      return tag;
   }

   private static Scp106SavedData load(CompoundTag tag) {
      Scp106SavedData d = new Scp106SavedData();
      for (Tag t : tag.getList("sessions", Tag.TAG_COMPOUND)) {
         PocketSession s = PocketSession.load((CompoundTag)t);
         d.sessions.put(s.player, s);
      }
      CompoundTag imm = tag.getCompound("immune");
      for (String k : imm.getAllKeys()) {
         try {
            d.immuneUntil.put(UUID.fromString(k), imm.getLong(k));
         } catch (IllegalArgumentException ignored) {
         }
      }
      CompoundTag var = tag.getCompound("variants");
      for (String k : var.getAllKeys()) {
         try {
            d.variants.put(Integer.parseInt(k), var.getIntArray(k));
         } catch (NumberFormatException ignored) {
         }
      }
      d.nextSlot = tag.getInt("nextSlot");
      d.naturalEnabled = !tag.contains("natural") || tag.getBoolean("natural");
      d.lastNatural = tag.contains("lastNatural") ? tag.getLong("lastNatural") : Long.MIN_VALUE;
      d.nextCheck = tag.getLong("nextCheck");
      return d;
   }
}

package dev.baranhan.viltrumitecore.scp.scp096;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World memory of SCP-096 (overworld data storage, {@code data/viltrumitecore_scp096.dat}): the natural-spawn
 * switch and clock, every instance (loaded or sleeping in an unloaded chunk) and the ids to drop when they load
 * (a despawn while unloaded, a stale natural copy). Survives restarts; one natural instance at most.
 */
public final class Scp096SavedData extends SavedData {
   public static final String NAME = "viltrumitecore_scp096";
   private boolean naturalEnabled = true;
   private long nextCheck;
   private long lastNaturalSpawn = Long.MIN_VALUE;
   private final Map<UUID, Instance> instances = new LinkedHashMap<>();
   private final Set<UUID> pendingRemoval = new HashSet<>();

   /** One known SCP-096 (last seen position, its dimension, natural or placed by an operator). */
   public static final class Instance {
      public final UUID id;
      public String dimension;
      public double x;
      public double y;
      public double z;
      public boolean natural;
      public String state;

      Instance(UUID id, String dimension, double x, double y, double z, boolean natural, String state) {
         this.id = id;
         this.dimension = dimension;
         this.x = x;
         this.y = y;
         this.z = z;
         this.natural = natural;
         this.state = state;
      }
   }

   public static Scp096SavedData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(Scp096SavedData::load, Scp096SavedData::new, NAME);
   }

   public boolean naturalEnabled() {
      return this.naturalEnabled;
   }

   public void setNaturalEnabled(boolean on) {
      if (this.naturalEnabled != on) {
         this.naturalEnabled = on;
         this.setDirty();
      }
   }

   public long nextCheck() {
      return this.nextCheck;
   }

   public void setNextCheck(long tick) {
      this.nextCheck = tick;
      this.setDirty();
   }

   public long lastNaturalSpawn() {
      return this.lastNaturalSpawn;
   }

   public void markNaturalSpawn(long tick) {
      this.lastNaturalSpawn = tick;
      this.setDirty();
   }

   public Collection<Instance> instances() {
      return Collections.unmodifiableCollection(this.instances.values());
   }

   @Nullable
   public Instance natural() {
      for (Instance i : this.instances.values()) {
         if (i.natural) {
            return i;
         }
      }
      return null;
   }

   /** Records or refreshes a live instance (position, state). */
   public void touch(Scp096Entity e) {
      Instance i = this.instances.get(e.getUUID());
      String dim = e.level().dimension().location().toString();
      String st = e.state().name();
      if (i == null) {
         this.instances.put(e.getUUID(), new Instance(e.getUUID(), dim, e.getX(), e.getY(), e.getZ(), e.natural(), st));
         this.setDirty();
         return;
      }
      boolean moved = i.x != e.getX() || i.y != e.getY() || i.z != e.getZ() || !i.dimension.equals(dim) || !i.state.equals(st) || i.natural != e.natural();
      i.dimension = dim;
      i.x = e.getX();
      i.y = e.getY();
      i.z = e.getZ();
      i.state = st;
      i.natural = e.natural();
      if (moved) {
         this.setDirty();
      }
   }

   public void forget(UUID id) {
      if (this.instances.remove(id) != null) {
         this.setDirty();
      }
   }

   /** Marks an unloaded instance: it is removed the moment its chunk loads. */
   public void removeOnLoad(UUID id) {
      this.instances.remove(id);
      this.pendingRemoval.add(id);
      this.setDirty();
   }

   /** True (and the mark is consumed) when this id was despawned while unloaded. */
   public boolean consumeRemoval(UUID id) {
      if (this.pendingRemoval.remove(id)) {
         this.setDirty();
         return true;
      }
      return false;
   }

   public int pendingRemovals() {
      return this.pendingRemoval.size();
   }

   // ------------------------------------------------------------------ NBT

   public static Scp096SavedData load(CompoundTag tag) {
      Scp096SavedData d = new Scp096SavedData();
      d.naturalEnabled = !tag.contains("NaturalEnabled") || tag.getBoolean("NaturalEnabled");
      d.nextCheck = tag.getLong("NextCheck");
      d.lastNaturalSpawn = tag.contains("LastNaturalSpawn") ? tag.getLong("LastNaturalSpawn") : Long.MIN_VALUE;
      ListTag list = tag.getList("Instances", Tag.TAG_COMPOUND);
      for (int k = 0; k < list.size(); k++) {
         CompoundTag e = list.getCompound(k);
         if (!e.hasUUID("Id")) {
            continue;
         }
         UUID id = e.getUUID("Id");
         d.instances.put(id, new Instance(id, e.getString("Dim"), e.getDouble("X"), e.getDouble("Y"), e.getDouble("Z"), e.getBoolean("Natural"),
            e.getString("State")));
      }
      ListTag gone = tag.getList("PendingRemoval", Tag.TAG_INT_ARRAY);
      for (int k = 0; k < gone.size(); k++) {
         d.pendingRemoval.add(NbtUtils.loadUUID(gone.get(k)));
      }
      return d;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.putInt("Version", 1);
      tag.putBoolean("NaturalEnabled", this.naturalEnabled);
      tag.putLong("NextCheck", this.nextCheck);
      if (this.lastNaturalSpawn != Long.MIN_VALUE) {
         tag.putLong("LastNaturalSpawn", this.lastNaturalSpawn);
      }
      ListTag list = new ListTag();
      for (Instance i : this.instances.values()) {
         CompoundTag e = new CompoundTag();
         e.putUUID("Id", i.id);
         e.putString("Dim", i.dimension);
         e.putDouble("X", i.x);
         e.putDouble("Y", i.y);
         e.putDouble("Z", i.z);
         e.putBoolean("Natural", i.natural);
         e.putString("State", i.state == null ? "" : i.state);
         list.add(e);
      }
      tag.put("Instances", list);
      ListTag gone = new ListTag();
      for (UUID id : this.pendingRemoval) {
         gone.add(NbtUtils.createUUID(id));
      }
      tag.put("PendingRemoval", gone);
      return tag;
   }
}

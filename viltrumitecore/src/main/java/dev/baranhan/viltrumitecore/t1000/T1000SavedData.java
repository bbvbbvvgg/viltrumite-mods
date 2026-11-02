package dev.baranhan.viltrumitecore.t1000;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World memory of the T-1000 (overworld data storage, {@code data/viltrumitecore_t1000.dat}). The entity itself
 * is never saved into a chunk; this file remembers every instance instead: its disguise, its integrity and core,
 * where it was last, and whether it is <b>dormant</b> (its chunk unloaded, the server stopped, or it slipped away
 * from every player). Dormant instances come back near a player after a delay, one at a time
 * ({@link T1000Spawner}). Also the natural-spawn switch, the clock of the checks and of the last destruction.
 */
public final class T1000SavedData extends SavedData {
   public static final String NAME = "viltrumitecore_t1000";
   private boolean naturalEnabled = true;
   private long nextCheck;
   private long nextWakeCheck;
   private long lastNaturalSpawn = Long.MIN_VALUE;
   private long lastDestroyed = Long.MIN_VALUE;
   private int destroyedTotal;
   private final Map<UUID, Instance> instances = new LinkedHashMap<>();

   /** One known T-1000. */
   public static final class Instance {
      public final UUID id;
      public String dimension;
      public double x;
      public double y;
      public double z;
      public String disguiseKind;
      public int disguise;
      public float integrity;
      public float core;
      public boolean natural;
      public boolean dormant;
      public long dormantSince;
      /** Last AI state name (status display only). */
      public String state;

      Instance(UUID id) {
         this.id = id;
         this.dimension = "minecraft:overworld";
         this.disguiseKind = T1000Disguises.CITIZEN;
         this.integrity = T1000Damage.MAX_INTEGRITY;
         this.core = T1000Damage.MAX_CORE;
         this.state = "DISGUISE";
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putUUID("Id", this.id);
         t.putString("Dim", this.dimension);
         t.putDouble("X", this.x);
         t.putDouble("Y", this.y);
         t.putDouble("Z", this.z);
         t.putString("Kind", this.disguiseKind);
         t.putInt("Disguise", this.disguise);
         t.putFloat("Integrity", this.integrity);
         t.putFloat("Core", this.core);
         t.putBoolean("Natural", this.natural);
         t.putBoolean("Dormant", this.dormant);
         t.putLong("DormantSince", this.dormantSince);
         t.putString("State", this.state);
         return t;
      }

      static Instance load(CompoundTag t) {
         Instance i = new Instance(t.getUUID("Id"));
         i.dimension = t.getString("Dim");
         i.x = t.getDouble("X");
         i.y = t.getDouble("Y");
         i.z = t.getDouble("Z");
         i.disguiseKind = t.contains("Kind") ? t.getString("Kind") : T1000Disguises.CITIZEN;
         i.disguise = t.getInt("Disguise");
         i.integrity = t.contains("Integrity") ? t.getFloat("Integrity") : T1000Damage.MAX_INTEGRITY;
         i.core = t.contains("Core") ? t.getFloat("Core") : T1000Damage.MAX_CORE;
         i.natural = t.getBoolean("Natural");
         i.dormant = t.getBoolean("Dormant");
         i.dormantSince = t.getLong("DormantSince");
         i.state = t.getString("State");
         return i;
      }
   }

   public static T1000SavedData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(T1000SavedData::load, T1000SavedData::new, NAME);
   }

   public static T1000SavedData load(CompoundTag tag) {
      T1000SavedData d = new T1000SavedData();
      d.naturalEnabled = !tag.contains("Natural") || tag.getBoolean("Natural");
      d.nextCheck = tag.getLong("NextCheck");
      d.nextWakeCheck = tag.getLong("NextWake");
      d.lastNaturalSpawn = tag.contains("LastSpawn") ? tag.getLong("LastSpawn") : Long.MIN_VALUE;
      d.lastDestroyed = tag.contains("LastDestroyed") ? tag.getLong("LastDestroyed") : Long.MIN_VALUE;
      d.destroyedTotal = tag.getInt("DestroyedTotal");
      ListTag list = tag.getList("Instances", Tag.TAG_COMPOUND);
      for (int n = 0; n < list.size(); n++) {
         Instance i = Instance.load(list.getCompound(n));
         if (!i.dormant) {
            // the entity is never saved into a chunk: after a restart nothing of it is in the world any more
            i.dormant = true;
            i.dormantSince = Math.max(i.dormantSince, d.nextWakeCheck - T1000Rules.SCHEDULER_PERIOD);
         }
         d.instances.put(i.id, i);
      }
      return d;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.putBoolean("Natural", this.naturalEnabled);
      tag.putLong("NextCheck", this.nextCheck);
      tag.putLong("NextWake", this.nextWakeCheck);
      if (this.lastNaturalSpawn != Long.MIN_VALUE) {
         tag.putLong("LastSpawn", this.lastNaturalSpawn);
      }
      if (this.lastDestroyed != Long.MIN_VALUE) {
         tag.putLong("LastDestroyed", this.lastDestroyed);
      }
      tag.putInt("DestroyedTotal", this.destroyedTotal);
      ListTag list = new ListTag();
      for (Instance i : this.instances.values()) {
         list.add(i.save());
      }
      tag.put("Instances", list);
      return tag;
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

   public long nextWakeCheck() {
      return this.nextWakeCheck;
   }

   public void setNextWakeCheck(long tick) {
      this.nextWakeCheck = tick;
      this.setDirty();
   }

   public long lastNaturalSpawn() {
      return this.lastNaturalSpawn;
   }

   public void markNaturalSpawn(long tick) {
      this.lastNaturalSpawn = tick;
      this.setDirty();
   }

   public long lastDestroyed() {
      return this.lastDestroyed;
   }

   public int destroyedTotal() {
      return this.destroyedTotal;
   }

   public Collection<Instance> instances() {
      return Collections.unmodifiableCollection(this.instances.values());
   }

   @Nullable
   public Instance instance(UUID id) {
      return this.instances.get(id);
   }

   public int activeCount() {
      int n = 0;
      for (Instance i : this.instances.values()) {
         if (!i.dormant) {
            n++;
         }
      }
      return n;
   }

   public int dormantCount() {
      return this.instances.size() - this.activeCount();
   }

   public List<Instance> dormant() {
      List<Instance> out = new ArrayList<>();
      for (Instance i : this.instances.values()) {
         if (i.dormant) {
            out.add(i);
         }
      }
      return out;
   }

   /** A body in the world: remember (or refresh) it as active. */
   public Instance touch(T1000Entity e, boolean natural) {
      Instance i = this.instances.computeIfAbsent(e.getUUID(), Instance::new);
      if (natural) {
         i.natural = true;
      }
      this.copy(i, e);
      i.dormant = false;
      this.setDirty();
      return i;
   }

   /** Refresh position and condition (every few seconds while loaded). */
   public void refresh(T1000Entity e) {
      Instance i = this.instances.get(e.getUUID());
      if (i != null) {
         this.copy(i, e);
         this.setDirty();
      }
   }

   private void copy(Instance i, T1000Entity e) {
      i.dimension = e.level().dimension().location().toString();
      i.x = e.getX();
      i.y = e.getY();
      i.z = e.getZ();
      i.disguiseKind = e.disguiseKind();
      i.disguise = e.skin();
      i.integrity = e.integrity();
      i.core = e.getHealth();
      i.state = e.brain().state().name();
   }

   /** The body left the world without being destroyed (chunk unloaded, server stop, slipped away). */
   public void sleep(T1000Entity e, long now) {
      Instance i = this.instances.get(e.getUUID());
      if (i == null) {
         return;
      }
      this.copy(i, e);
      i.dormant = true;
      i.dormantSince = now;
      this.setDirty();
   }

   /** Final destruction: forget it, start the respawn delay. */
   public void destroyed(UUID id, long now) {
      if (this.instances.remove(id) != null) {
         this.lastDestroyed = now;
         this.destroyedTotal++;
         this.setDirty();
      }
   }

   /** Operator removal: forget without the respawn delay. */
   public boolean forget(UUID id) {
      boolean had = this.instances.remove(id) != null;
      if (had) {
         this.setDirty();
      }
      return had;
   }

   public int forgetAll() {
      int n = this.instances.size();
      this.instances.clear();
      this.setDirty();
      return n;
   }
}

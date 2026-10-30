package dev.baranhan.viltrumitecore.worldevent;

import dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteCosmeticsPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public abstract class WorldEvent {
   private static final int[] ANNOUNCE_AT_SECONDS = new int[]{600, 300, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1};
   private static final int MAX_ACTIVE_TICKS = 24000;
   private WorldEventType type;
   private int warmupTicks;
   private int activeTicks;
   private boolean begun;
   private boolean over;
   /** Started by an operator command; automatic starts stay off by default (playtest fix). */
   private boolean manual;
   private final List<Integer> announced = new ArrayList<>();
   private Vec3 origin = Vec3.ZERO;
   protected final List<UUID> spawned = new ArrayList<>();

   protected abstract void onBegin(ServerLevel var1);

   protected void onTick(ServerLevel level) {
   }

   protected boolean isOver(ServerLevel level) {
      return this.allSpawnedGone(level);
   }

   protected void onEnd(ServerLevel level) {
      this.despawnAll(level);
   }

   protected void saveExtra(CompoundTag tag) {
   }

   protected void loadExtra(CompoundTag tag) {
   }

   public void begin(ServerLevel level, Vec3 origin, int warmupTicks) {
      this.origin = origin;
      this.warmupTicks = Math.max(0, warmupTicks);
      this.announced.clear();
   }

   public void tick(ServerLevel level) {
      if (!this.over) {
         if (!this.begun) {
            this.announceCountdown(level);
            if (this.warmupTicks <= 0) {
               this.begun = true;
               this.broadcast(level, Component.translatable(this.type.startKey()));
               this.onBegin(level);
            } else {
               this.warmupTicks--;
            }
         } else {
            this.activeTicks++;
            this.onTick(level);
            if (this.activeTicks > 20 && (this.isOver(level) || this.activeTicks > 24000)) {
               this.finish(level);
            }
         }
      }
   }

   public void finish(ServerLevel level) {
      if (!this.over) {
         this.over = true;
         this.onEnd(level);
         this.broadcast(level, Component.translatable(this.type.endKey()));
      }
   }

   private void announceCountdown(ServerLevel level) {
      int secondsLeft = Math.round((float)this.warmupTicks / 20.0F);

      for (int threshold : ANNOUNCE_AT_SECONDS) {
         if (secondsLeft == threshold && !this.announced.contains(threshold)) {
            this.announced.add(threshold);
            Component time = threshold >= 60
               ? Component.translatable("event.viltrumitecore.time.minutes", new Object[]{threshold / 60})
               : Component.translatable("event.viltrumitecore.time.seconds", new Object[]{threshold});
            this.broadcast(level, Component.translatable(this.type.warnKey(), new Object[]{time}));
            return;
         }
      }
   }

   protected void broadcast(ServerLevel level, Component message) {
      Component styled = Component.empty().append(message).withStyle(this.type.color());
      level.getServer().getPlayerList().broadcastSystemMessage(styled, false);
   }

   protected ViltrumiteFakePlayer spawnViltrumite(
      ServerLevel level,
      Vec3 offset,
      String name,
      String skin,
      String cape,
      String model,
      float scale,
      int intelligence,
      ViltrumiteFakePlayer.TargetMode mode,
      float baseDamage,
      float damageIgnoreThreshold,
      float damageReduction,
      float healFactor,
      float maxFlightSpeed,
      float throttleSpeed
   ) {
      ViltrumiteFakePlayer clone = new ViltrumiteFakePlayer(
         level.getServer(), level, name, scale, intelligence, mode, baseDamage, damageIgnoreThreshold, damageReduction, healFactor, maxFlightSpeed, throttleSpeed
      );
      Vec3 pos = this.origin.add(offset);
      clone.setPos(pos.x, pos.y, pos.z);
      ViltrumiteCosmeticsPlayer cosmetics = (ViltrumiteCosmeticsPlayer)clone;
      cosmetics.setViltrumiteSkin(skin);
      cosmetics.setViltrumiteCape(cape);
      cosmetics.setViltrumiteModel(model);
      level.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(Action.ADD_PLAYER, clone));
      level.addFreshEntity(clone);
      this.spawned.add(clone.getUUID());
      return clone;
   }

   protected boolean allSpawnedGone(ServerLevel level) {
      for (UUID id : this.spawned) {
         Entity entity = level.getEntity(id);
         if (entity != null && entity.isAlive()) {
            return false;
         }
      }

      return true;
   }

   protected void despawnAll(ServerLevel level) {
      for (UUID id : this.spawned) {
         Entity entity = level.getEntity(id);
         if (entity != null) {
            entity.discard();
         }

         level.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(id)));
      }

      this.spawned.clear();
   }

   protected int aliveCount(ServerLevel level) {
      int count = 0;

      for (UUID id : this.spawned) {
         Entity entity = level.getEntity(id);
         if (entity != null && entity.isAlive()) {
            count++;
         }
      }

      return count;
   }

   public boolean isManual() {
      return this.manual;
   }

   public void markManual() {
      this.manual = true;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Id", this.type.id());
      tag.putBoolean("Manual", this.manual);
      tag.putInt("Warmup", this.warmupTicks);
      tag.putInt("Active", this.activeTicks);
      tag.putBoolean("Begun", this.begun);
      tag.putBoolean("Over", this.over);
      tag.putDouble("OriginX", this.origin.x);
      tag.putDouble("OriginY", this.origin.y);
      tag.putDouble("OriginZ", this.origin.z);
      ListTag announcedTag = new ListTag();

      for (int value : this.announced) {
         announcedTag.add(IntTag.valueOf(value));
      }

      tag.put("Announced", announcedTag);
      ListTag spawnedTag = new ListTag();

      for (UUID id : this.spawned) {
         spawnedTag.add(NbtUtils.createUUID(id));
      }

      tag.put("Spawned", spawnedTag);
      CompoundTag extra = new CompoundTag();
      this.saveExtra(extra);
      tag.put("Extra", extra);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.manual = tag.getBoolean("Manual");
      this.warmupTicks = tag.getInt("Warmup");
      this.activeTicks = tag.getInt("Active");
      this.begun = tag.getBoolean("Begun");
      this.over = tag.getBoolean("Over");
      this.origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
      this.announced.clear();

      for (Tag element : tag.getList("Announced", 3)) {
         this.announced.add(((IntTag)element).getAsInt());
      }

      this.spawned.clear();

      for (Tag element : tag.getList("Spawned", 11)) {
         this.spawned.add(NbtUtils.loadUUID(element));
      }

      this.loadExtra(tag.getCompound("Extra"));
   }

   public WorldEventType type() {
      return this.type;
   }

   void setType(WorldEventType type) {
      this.type = type;
   }

   public boolean isOver() {
      return this.over;
   }

   public boolean hasBegun() {
      return this.begun;
   }

   public int warmupTicks() {
      return this.warmupTicks;
   }

   public Vec3 origin() {
      return this.origin;
   }

   protected ServerPlayer nearestPlayer(ServerLevel level) {
      return (ServerPlayer)level.getNearestPlayer(this.origin.x, this.origin.y, this.origin.z, -1.0, false);
   }
}

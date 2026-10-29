package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Base of Sinclair's non-living stage pieces (hat, hex mark, act props): no physics, never saved, owner-checked. */
public abstract class SinclairMarker extends Entity implements SinclairOwned.Owned {
   private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SinclairMarker.class, EntityDataSerializers.INT);
   @Nullable
   protected UUID owner;

   protected SinclairMarker(EntityType<?> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.setNoGravity(true);
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(OWNER_ID, -1);
   }

   protected void bindOwner(Entity owner) {
      this.owner = owner.getUUID();
      this.entityData.set(OWNER_ID, owner.getId());
   }

   public int ownerEntityId() {
      return this.entityData.get(OWNER_ID);
   }

   @Override
   @Nullable
   public UUID ownerId() {
      return this.owner;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean isAttackable() {
      return false;
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return new ClientboundAddEntityPacket(this);
   }
}

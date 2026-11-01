package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriWar;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Chitauri pylon planted in a seized sector. {@link #KIND_STRONGHOLD} anchors the hive's claim on the sector (the
 * heroes liberate it by destroying the pylon); {@link #KIND_CONTAINMENT} also holds captured citizens in its field
 * (destroying it frees all of them). It has no AI and never moves; the war layer owns its life. Never saved.
 */
public class ChitauriBeacon extends Mob implements ChitauriMob, net.minecraft.world.entity.monster.Enemy {
   private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(ChitauriBeacon.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> HELD = SynchedEntityData.defineId(ChitauriBeacon.class, EntityDataSerializers.BYTE);
   public static final byte KIND_STRONGHOLD = 0, KIND_CONTAINMENT = 1;
   public static final int DEPLOY_TICKS = 40;
   public static final double FIELD_RADIUS = 2.4;

   public int sector = -1;
   public int clientHurtStart = -1000;

   public ChitauriBeacon(EntityType<? extends ChitauriBeacon> type, Level level) {
      super(type, level);
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, BalanceTable.CHITAURI_BEACON_HEALTH)
         .add(Attributes.ARMOR, 6.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.MOVEMENT_SPEED, 0.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, KIND_STRONGHOLD);
      this.entityData.define(HELD, (byte)0);
   }

   public byte kind() {
      return this.entityData.get(KIND);
   }

   public void setKind(byte kind) {
      this.entityData.set(KIND, kind);
   }

   public boolean containment() {
      return this.kind() == KIND_CONTAINMENT;
   }

   /** Captives in the field (synced for the field's glow). */
   public int held() {
      return this.entityData.get(HELD);
   }

   public void setHeld(int n) {
      this.entityData.set(HELD, (byte)Math.max(0, Math.min(127, n)));
   }

   /** Spot {@code i} of {@code n} in the field around the pylon. */
   public Vec3 slot(int i, int n) {
      double a = Math.PI * 2.0 * i / Math.max(1, n) + this.getId() * 0.37;
      return this.position().add(Math.cos(a) * FIELD_RADIUS, 0.35, Math.sin(a) * FIELD_RADIUS);
   }

   @Override
   public void tick() {
      super.tick();
      this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
      if (!this.level().isClientSide) {
         if (this.tickCount == 1) {
            ServerLevel level = (ServerLevel)this.level();
            ChitauriSounds.playWide(level, this.position(), ChitauriSounds.BEACON_DEPLOY, 3.0F, 1.0F, 96.0);
            ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.BEACON_DEPLOY, this.position(), new Vec3(0, 1, 0), this.containment() ? 1.4F : 1.0F,
               this.getId()), this.position(), 160);
         }
         if (this.tickCount > DEPLOY_TICKS && this.tickCount % 50 == 0) {
            ChitauriSounds.play((ServerLevel)this.level(), this.position().add(0, 1.5, 0), ChitauriSounds.BEACON_HUM, 0.9F, this.containment() ? 0.8F : 1.0F);
         }
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (ChitauriMob.is(source.getEntity()) || ChitauriMob.is(source.getDirectEntity())) {
         return false;
      }
      return super.hurt(source, amount);
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      if (this.level() instanceof ServerLevel level) {
         Vec3 at = this.position().add(0, 1.2, 0);
         ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.BEACON_DOWN, at, new Vec3(0, 1, 0), this.containment() ? 1.4F : 1.0F,
            this.getId()), at, 160);
         ChitauriSounds.playWide(level, at, ChitauriSounds.BEACON_DOWN, 3.0F, 1.0F, 128.0);
         ChitauriWar.beaconDown(level, this, source);
      }
   }

   @Override
   public void remove(Entity.RemovalReason reason) {
      if (this.level() instanceof ServerLevel level && this.isAlive()) {
         ChitauriWar.beaconGone(level, this); // unloaded or purged: captives go free
      }
      super.remove(reason);
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return ChitauriMob.is(other) || super.isAlliedTo(other);
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void pushEntities() {
   }

   @Override
   public void knockback(double strength, double x, double z) {
   }

   @Override
   protected boolean shouldDropLoot() {
      return false;
   }

   @Override
   public boolean shouldDropExperience() {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 256.0 * 256.0;
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(3.0, 2.0, 3.0);
   }
}

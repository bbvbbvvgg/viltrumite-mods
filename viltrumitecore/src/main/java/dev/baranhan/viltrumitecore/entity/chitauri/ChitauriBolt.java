package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriDestruction;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Chitauri energy bolt: straight, no gravity, blue-white core with a halo and a trail (client
 * renderer). Rifle bolts (power 1) hurt one target; cannon bolts (power 2) also blast a small
 * radius and may crack weak blocks (glass, leaves) within the invasion's destruction cap.
 */
public class ChitauriBolt extends Projectile {
   private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(ChitauriBolt.class, EntityDataSerializers.FLOAT);
   public static final int MAX_LIFE = 60;
   /** Global balance table: 5 Normal-difficulty hits on an unarmored human. */
   public static final float RIFLE_DAMAGE = dev.baranhan.viltrumitecore.balance.BalanceTable.CHITAURI_RIFLE;
   public static final float CANNON_DAMAGE = dev.baranhan.viltrumitecore.balance.BalanceTable.CHITAURI_CANNON;

   // client trail: last positions, newest first
   public final double[] trail = new double[24];
   public int trailCount;

   public ChitauriBolt(EntityType<? extends ChitauriBolt> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
   }

   public static ChitauriBolt shoot(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, double speed, float power, float inaccuracy) {
      ChitauriBolt bolt = new ChitauriBolt(ChitauriEntities.BOLT.get(), level);
      bolt.setOwner(owner);
      bolt.setPos(from.x, from.y, from.z);
      bolt.entityData.set(POWER, power);
      bolt.shoot(dir.x, dir.y, dir.z, (float)speed, inaccuracy);
      level.addFreshEntity(bolt);
      return bolt;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(POWER, 1.0F);
   }

   public float power() {
      return this.entityData.get(POWER);
   }

   @Override
   public void tick() {
      super.tick();
      Vec3 v = this.getDeltaMovement();
      Vec3 next = this.position().add(v);
      if (!this.level().isClientSide) {
         // never trace into an unloaded chunk (Level.clip would load it)
         if (this.tickCount > MAX_LIFE || !this.level().hasChunkAt(BlockPos.containing(next)) || this.getY() < this.level().getMinBuildHeight()) {
            this.discard();
            return;
         }
         HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
         if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.onHit(hit);
            if (this.isRemoved()) {
               return;
            }
         }
      } else {
         System.arraycopy(this.trail, 0, this.trail, 3, this.trail.length - 3);
         this.trail[0] = this.getX();
         this.trail[1] = this.getY();
         this.trail[2] = this.getZ();
         this.trailCount = Math.min(this.trail.length / 3, this.trailCount + 1);
      }
      this.setPos(next.x, next.y, next.z);
      ProjectileUtil.rotateTowardsMovement(this, 1.0F);
   }

   @Override
   protected boolean canHitEntity(Entity entity) {
      return super.canHitEntity(entity) && !ChitauriMob.is(entity) && !(entity instanceof ChitauriBolt);
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      if (!(this.level() instanceof ServerLevel level)) {
         return;
      }
      Entity target = result.getEntity();
      Entity owner = this.getOwner();
      float power = this.power();
      target.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null), power >= 2.0F ? CANNON_DAMAGE : RIFLE_DAMAGE);
      this.impact(level, result.getLocation(), power);
   }

   @Override
   protected void onHitBlock(BlockHitResult result) {
      super.onHitBlock(result);
      if (this.level() instanceof ServerLevel level) {
         if (this.power() >= 2.0F) {
            ChitauriDestruction.crack(level, result.getBlockPos(), 1.6, this.getDeltaMovement());
         } else {
            ChitauriDestruction.shatterGlass(level, result.getBlockPos());
         }
         this.impact(level, result.getLocation(), this.power());
      }
   }

   private void impact(ServerLevel level, Vec3 at, float power) {
      Vec3 d = this.getDeltaMovement().normalize();
      if (power >= 2.0F) {
         ChitauriDestruction.blastEntities(level, at, 3.0, 4.0F, this.getOwner(), this);
         ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.CANNON_IMPACT, at, d, power, this.random.nextInt()), at, 96);
         ChitauriSounds.play(level, at, ChitauriSounds.EXPLOSION, 3.0F, 1.25F + this.random.nextFloat() * 0.2F);
      } else {
         ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.BOLT_IMPACT, at, d, power, this.random.nextInt()), at, 64);
         ChitauriSounds.play(level, at, ChitauriSounds.BOLT_IMPACT, 1.2F, 0.9F + this.random.nextFloat() * 0.25F);
      }
      this.discard();
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 128.0 * 128.0;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }
}

package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronNet;
import javax.annotation.Nullable;
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
 * Ultron energy bolt: straight, no gravity, red-orange core in a halo with a short trail (client renderer).
 * Repulsor (combat robot), laser (interceptor) and palm (Ultron Prime: bigger, a small splash). Never
 * traces into an unloaded chunk, never hurts Ultron's own, never breaks blocks.
 */
public class UltronBolt extends Projectile {
   private static final EntityDataAccessor<Byte> STYLE = SynchedEntityData.defineId(UltronBolt.class, EntityDataSerializers.BYTE);
   public static final int STYLE_REPULSOR = 0;
   public static final int STYLE_LASER = 1;
   public static final int STYLE_PALM = 2;
   public static final int MAX_LIFE = 60;

   private float damage = 4.0F;
   // client trail: last positions, newest first
   public final double[] trail = new double[24];
   public int trailCount;

   public UltronBolt(EntityType<? extends UltronBolt> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
   }

   public static UltronBolt shoot(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, double speed, float damage, int style) {
      UltronBolt bolt = new UltronBolt(UltronEntities.BOLT.get(), level);
      bolt.setOwner(owner);
      bolt.setPos(from.x, from.y, from.z);
      bolt.damage = damage;
      bolt.entityData.set(STYLE, (byte)style);
      bolt.shoot(dir.x, dir.y, dir.z, (float)speed, style == STYLE_LASER ? 1.2F : 0.8F);
      level.addFreshEntity(bolt);
      return bolt;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(STYLE, (byte)STYLE_REPULSOR);
   }

   public int style() {
      return this.entityData.get(STYLE);
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

   /**
    * A shot a hero turned back (Reflect makes him its owner): it now hits Ultron's machines and spares the
    * hero's side. An Ultron shot whose shooter is gone stays Ultron's.
    */
   public static boolean turned(@Nullable Entity owner) {
      return owner != null && !UltronMob.side(owner);
   }

   /** Who a shot of this owner may hit: Ultron's enemies, or — turned — Ultron's own (never the captured golems / suits). */
   static boolean hits(@Nullable Entity owner, Entity entity) {
      if (entity instanceof UltronBolt || entity instanceof UltronMissile) {
         return false;
      }
      return turned(owner) ? entity != owner && UltronMob.is(entity) : !UltronMob.side(entity);
   }

   @Override
   protected boolean canHitEntity(Entity entity) {
      return super.canHitEntity(entity) && hits(this.getOwner(), entity);
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      if (!(this.level() instanceof ServerLevel level)) {
         return;
      }
      Entity target = result.getEntity();
      Entity owner = this.getOwner();
      target.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null), this.damage);
      this.impact(level, result.getLocation());
   }

   @Override
   protected void onHitBlock(BlockHitResult result) {
      super.onHitBlock(result);
      if (this.level() instanceof ServerLevel level) {
         this.impact(level, result.getLocation());
      }
   }

   private void impact(ServerLevel level, Vec3 at) {
      Vec3 d = this.getDeltaMovement().normalize();
      int style = this.style();
      Entity owner = this.getOwner();
      if (style == STYLE_PALM && owner instanceof LivingEntity shooter) {
         // the palm blast bursts: a small splash around the hit (turned back by a hero, it splashes Ultron's own)
         boolean turned = turned(owner);
         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.5),
            e -> turned ? e != owner && e.isAlive() && UltronMob.is(e) : UltronMob.validTarget(e))) {
            if (e.position().distanceToSqr(at) < 2.5 * 2.5) {
               e.hurt(this.damageSources().mobProjectile(this, shooter), this.damage * 0.35F);
            }
         }
      }
      UltronNet.fx(level, new UltronFxS2CPacket(UltronFxS2CPacket.BOLT_IMPACT, at, d, style == STYLE_PALM ? 2.0F : style == STYLE_LASER ? 0.7F : 1.0F, style << 1), at, 64.0);
      UltronSounds.play(level, at, UltronSounds.BOLT_IMPACT, style == STYLE_PALM ? 1.8F : 1.0F, (style == STYLE_LASER ? 1.25F : 0.95F) + this.random.nextFloat() * 0.15F);
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

package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * Repulsor bolt (spec §8.1): a fast straight projectile without gravity.
 * Damage and knockback come from the shooter's release; every entity hit
 * goes through vanilla hurt (and so HeroDamage.route). Visual: pixel bolt
 * (RepulsorRenderer) and an impact flash / scorch on the client.
 */
public class RepulsorBlastEntity extends Projectile {
   private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(RepulsorBlastEntity.class, EntityDataSerializers.FLOAT);
   private float damage = IronManRules.REPULSOR_SHOT;
   private double knockback = IronManRules.REPULSOR_KNOCKBACK_TAP;
   private int life;

   public RepulsorBlastEntity(EntityType<? extends RepulsorBlastEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   public static RepulsorBlastEntity shoot(ServerPlayer owner, Vec3 from, Vec3 direction, float damage, float power, double knockback) {
      RepulsorBlastEntity blast = new RepulsorBlastEntity(ViltrumiteEntities.REPULSOR_BLAST.get(), owner.level());
      blast.setOwner(owner);
      blast.damage = damage;
      blast.knockback = knockback;
      blast.entityData.set(POWER, power);
      blast.setPos(from);
      blast.setDeltaMovement(direction.normalize().scale(IronManRules.REPULSOR_SPEED));
      owner.level().addFreshEntity(blast);
      return blast;
   }

   public float power() {
      return this.entityData.get(POWER);
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(POWER, 0.35F);
   }

   @Override
   public void tick() {
      super.tick();
      Vec3 motion = this.getDeltaMovement();
      HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
      if (!this.level().isClientSide() && dev.baranhan.viltrumitecore.hero.HeroProjectiles.intercept(this, hit)) return;
      // Only a hit the server may turn away waits for it; ordinary hits (PvP too) play their impact.
      if (this.level().isClientSide() && dev.baranhan.viltrumitecore.hero.HeroProjectiles.awaitsServer(this, hit)) return;
      if (hit.getType() != HitResult.Type.MISS) {
         if (this.level().isClientSide()) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
               () -> () -> dev.baranhan.viltrumitecore.client.ironman.IronManCombatVfx.repulsorImpact(hit, this.power()));
            this.discard();
         } else {
            this.onHit(hit);
         }

         return;
      }

      this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
      if (++this.life > (int)Math.ceil(IronManRules.REPULSOR_RANGE / IronManRules.REPULSOR_SPEED)) {
         this.discard();
      }
   }

   @Override
   protected boolean canHitEntity(Entity entity) {
      return super.canHitEntity(entity) && !(entity instanceof RepulsorBlastEntity) && !(entity instanceof MicroMissileEntity);
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      Entity entity = result.getEntity();
      if (entity instanceof net.minecraftforge.entity.PartEntity<?> part) {
         entity = part.getParent();
      }

      Entity owner = this.getOwner();
      if (entity instanceof LivingEntity living) {
         // Volleys hit twice in one tick: the second bolt must not vanish in the iframes.
         int previous = living.invulnerableTime;
         living.invulnerableTime = 0;
         boolean hurt = living.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity shooter ? shooter : null), this.damage);
         if (hurt && dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(living)) {
            Vec3 push = this.getDeltaMovement().normalize().scale(this.knockback);
            living.push(push.x, Math.max(0.1, push.y * 0.5 + 0.15 * this.knockback), push.z);
            living.hurtMarked = true;
         } else if (!hurt) {
            living.invulnerableTime = previous;
         }
      }

      this.impact(result.getLocation());
   }

   @Override
   protected void onHitBlock(BlockHitResult result) {
      this.impact(result.getLocation());
   }

   private void impact(Vec3 at) {
      if (this.getOwner() instanceof ServerPlayer owner) {
         dev.baranhan.viltrumitecore.hero.fx.HeroFx.flash(owner, at);
      }

      this.level().playSound(null, at.x, at.y, at.z, dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds.REPULSOR_FIZZLE.get(),
         net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.6F);
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
      super.readAdditionalSaveData(tag);
      this.discard();
   }
}

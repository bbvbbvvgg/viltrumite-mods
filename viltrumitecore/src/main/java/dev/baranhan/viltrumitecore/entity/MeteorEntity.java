package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.client.render.vfx.MeteorImpactVFXManager;
import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.ForgeEventFactory;

public class MeteorEntity extends Projectile {
   /** False for meteor-rain meteors: an endless rain must not pile up invulnerable gun parts. */
   private boolean dropsLoot = true;

   public MeteorEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   public void setDropsLoot(boolean dropsLoot) {
      this.dropsLoot = dropsLoot;
   }

   public boolean dropsLoot() {
      return this.dropsLoot;
   }

   @Override
   protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (!this.dropsLoot) {
         tag.putBoolean("NoLoot", true);
      }
   }

   @Override
   protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.dropsLoot = !tag.getBoolean("NoLoot");
   }

   public void tick() {
      super.tick();
      HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, x$0 -> this.canHitEntity(x$0));
      if (hitresult.getType() != Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hitresult)) {
         this.onHit(hitresult);
      }

      if (!this.isRemoved()) {
         Vec3 movement = this.getDeltaMovement();
         this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
         if (this.level().isClientSide()) {
            this.level()
               .addParticle(
                  ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), -movement.x * 0.5, 0.1, -movement.z * 0.5
               );
            this.level()
               .addParticle(
                  ParticleTypes.FLAME, this.getX(), this.getY() + 0.5, this.getZ(), -movement.x * 0.5, 0.1, -movement.z * 0.5
               );
         }
      }
   }

   protected void onHitBlock(BlockHitResult pResult) {
      super.onHitBlock(pResult);
      BlockPos hitPos = pResult.getBlockPos();
      if (!this.level().isClientSide()) {
         this.level().explode(this, this.getX(), this.getY(), this.getZ(), 12.0F, ExplosionInteraction.TNT);
         int radius = 8;

         for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
               if (x * x + z * z <= radius * radius) {
                  for (int y = radius + 4; y >= -radius - 4; y--) {
                     BlockPos targetPos = hitPos.offset(x, y, z);
                     BlockState currentState = this.level().getBlockState(targetPos);
                     if (!currentState.isAir() && currentState.canOcclude()) {
                        if (!currentState.is(Blocks.BEDROCK)) {
                           this.level().setBlock(targetPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                        }
                        break;
                     }
                  }
               }
            }
         }

         if (this.dropsLoot) {
            int randomPiece = this.random.nextInt(3);

            Item droppedItem = switch (randomPiece) {
               case 0 -> (Item)ViltrumiteItems.INFINITY_GUN_BASE.get();
               case 1 -> (Item)ViltrumiteItems.INFINITY_GUN_HANDLE.get();
               default -> (Item)ViltrumiteItems.INFINITY_GUN_BARREL.get();
            };
            ItemStack dropStack = new ItemStack(droppedItem, 1);
            ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY() + 2.0, this.getZ(), dropStack);
            itemEntity.setDeltaMovement(0.0, 0.4, 0.0);
            itemEntity.setUnlimitedLifetime();
            itemEntity.setInvulnerable(true);
            this.level().addFreshEntity(itemEntity);
         }

         this.level().broadcastEntityEvent(this, (byte)77);
         this.discard();
      }
   }

   public void handleEntityEvent(byte id) {
      if (id == 77) {
         Vec3 vfxPos = this.position().add(0.0, 2.0, 0.0);
         float yaw = (float)Math.toDegrees(Math.atan2(this.getDeltaMovement().z, this.getDeltaMovement().x)) - 90.0F;
         float pitch = (float)(-Math.toDegrees(Math.asin(this.getDeltaMovement().y)));
         MeteorImpactVFXManager.playImpactVFX(vfxPos, yaw, pitch);
         this.level().addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
         this.level().addAlwaysVisibleParticle(ParticleTypes.FLASH, true, this.getX(), this.getY() + 1.0, this.getZ(), 0.0, 0.0, 0.0);

         for (int i = 0; i < 40; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * 8.0;
            double offsetZ = (this.random.nextDouble() - 0.5) * 8.0;
            double speedY = 1.1 + this.random.nextDouble() * 2.0;
            this.level()
               .addParticle(
                  (ParticleOptions)ViltrumiteParticles.GIANT_METEOR_SMOKE.get(),
                  this.getX() + offsetX,
                  this.getY() + 1.0,
                  this.getZ() + offsetZ,
                  offsetX * 0.1,
                  speedY,
                  offsetZ * 0.1
               );
         }
      } else {
         super.handleEntityEvent(id);
      }
   }

   protected void defineSynchedData() {
   }
}

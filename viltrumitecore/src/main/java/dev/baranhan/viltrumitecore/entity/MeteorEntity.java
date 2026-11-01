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
   /** True for meteor-rain meteors: the impact starts the spreading city fire (Meteor Rain 2.0). */
   private boolean ignitesCity;

   public MeteorEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   public void setDropsLoot(boolean dropsLoot) {
      this.dropsLoot = dropsLoot;
   }

   public boolean dropsLoot() {
      return this.dropsLoot;
   }

   public void setIgnitesCity(boolean ignitesCity) {
      this.ignitesCity = ignitesCity;
   }

   public boolean ignitesCity() {
      return this.ignitesCity;
   }

   /** Rendering only: the glowing head and its plasma tail read from far away (tracking range stays 10 chunks). */
   @Override
   public boolean shouldRenderAtSqrDistance(double distanceSq) {
      return distanceSq < 256.0 * 256.0;
   }

   @Override
   protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (!this.dropsLoot) {
         tag.putBoolean("NoLoot", true);
      }

      if (this.ignitesCity) {
         tag.putBoolean("CityFire", true);
      }
   }

   @Override
   protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.dropsLoot = !tag.getBoolean("NoLoot");
      this.ignitesCity = tag.getBoolean("CityFire");
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
            // flame, sparks and turbulent smoke of the entry trail (client only, distance LOD)
            dev.baranhan.viltrumitecore.client.render.vfx.MeteorTrailFx.tick(this);
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

         if (this.ignitesCity && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            dev.baranhan.viltrumitecore.worldevent.meteor.fire.CityFire.meteorImpact(serverLevel, this.position());
         }

         if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // city disaster aftermath: the crater keeps glowing, smoking and crumbling long after the impact
            dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.registerDamage(serverLevel, dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathEvent.of(serverLevel, "meteor:" + this.getUUID(), hitPos, 11.0F,
               this.ignitesCity ? 0.75F : 0.6F, dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause.METEOR, java.util.List.of()));
         }

         this.level().broadcastEntityEvent(this, (byte)77);
         farImpact();
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
         // Meteor Rain 2.0: layered dust, billow, hot column, debris sparks, ash, a smouldering crater and the
         // distance-aware impact sound replace the old flat smoke puffs
         dev.baranhan.viltrumitecore.client.render.vfx.MeteorImpactFx.play(this.level(), this.position(), this.getDeltaMovement());
      } else {
         super.handleEntityEvent(id);
      }
   }

   protected void defineSynchedData() {
   }

   /** Players beyond the entity tracking range still hear a distant impact (sound only, no VFX). */
   private void farImpact() {
      if (!(this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
         return;
      }

      dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket packet = new dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket(
         dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket.FAR_IMPACT, this.position(), 1.0F, null);
      for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
         double d2 = p.distanceToSqr(this);
         if (d2 > 150.0 * 150.0 && d2 < 480.0 * 480.0) {
            dev.baranhan.viltrumitecore.network.CoreMessages.sendToPlayer(packet, p);
         }
      }
   }
}

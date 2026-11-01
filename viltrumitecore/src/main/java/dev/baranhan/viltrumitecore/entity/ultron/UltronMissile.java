package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.entity.Homing;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronNet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * Micro-missile (heavy robot pods, interceptor belly): launches on its own heading, then homes on its
 * target with a limited turn rate (a hero can out-turn it), bursts on contact, near the target or at the
 * end of its fuel. Splash hurts only Ultron's enemies and never breaks blocks; chunk-safe like the bolts.
 * Flares and Reflect retarget it ({@link Homing}); turned back by a hero it hunts Ultron's own.
 */
public class UltronMissile extends Projectile implements Homing {
   public static final int FUEL = 90;
   private static final double SPEED = 0.95;
   private static final double TURN = 0.11;
   private int targetId = -1;
   private int arming = 6;
   // client trail: last positions, newest first
   public final double[] trail = new double[30];
   public int trailCount;

   public UltronMissile(EntityType<? extends UltronMissile> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
   }

   public static UltronMissile launch(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, @Nullable LivingEntity target) {
      UltronMissile m = new UltronMissile(UltronEntities.MISSILE.get(), level);
      m.setOwner(owner);
      m.setPos(from.x, from.y, from.z);
      m.targetId = target == null ? -1 : target.getId();
      Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : dir.normalize();
      m.setDeltaMovement(d.scale(0.55));
      ProjectileUtil.rotateTowardsMovement(m, 1.0F);
      level.addFreshEntity(m);
      UltronSounds.play(level, from, UltronSounds.MISSILE_FLIGHT, 1.0F, 0.95F + level.random.nextFloat() * 0.1F);
      return m;
   }

   @Override
   protected void defineSynchedData() {
   }

   /** Iron Man's flares and a hero's Reflect pick a new target (server; null: it flies straight on). */
   @Override
   public void retarget(@Nullable Entity target) {
      this.targetId = target == null ? -1 : target.getId();
   }

   @Override
   @Nullable
   public Entity homingTarget() {
      return this.targetId >= 0 ? this.level().getEntity(this.targetId) : null;
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         if (this.tickCount > FUEL || this.getY() < this.level().getMinBuildHeight()) {
            this.burst((ServerLevel)this.level(), this.position());
            return;
         }
         Vec3 v = this.getDeltaMovement();
         if (this.arming > 0) {
            this.arming--;
         } else {
            Entity t = this.homingTarget();
            double speed = Math.min(SPEED, v.length() + 0.05);
            if (t != null && t.isAlive()) {
               // a living target or a decoy (a flare): it bursts next to whatever it chases
               Vec3 aim = t.position().add(0.0, t.getBbHeight() * 0.5, 0.0);
               if (aim.distanceToSqr(this.position()) < 1.4 * 1.4) {
                  this.burst((ServerLevel)this.level(), this.position());
                  return;
               }
               Vec3 want = aim.subtract(this.position()).normalize();
               Vec3 cur = v.normalize();
               v = cur.add(want.subtract(cur).scale(TURN)).normalize().scale(speed);
            } else {
               v = v.normalize().scale(speed);
            }
            this.setDeltaMovement(v);
         }
         Vec3 next = this.position().add(v);
         if (!this.level().hasChunkAt(BlockPos.containing(next)) || !ChitauriTerrain.loaded(this.level(), next.x, next.z)) {
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
      Vec3 v = this.getDeltaMovement();
      this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
      ProjectileUtil.rotateTowardsMovement(this, 0.6F);
   }

   @Override
   protected boolean canHitEntity(Entity entity) {
      return super.canHitEntity(entity) && UltronBolt.hits(this.getOwner(), entity);
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      if (this.level() instanceof ServerLevel level) {
         this.burst(level, result.getLocation());
      }
   }

   @Override
   protected void onHitBlock(BlockHitResult result) {
      super.onHitBlock(result);
      if (this.level() instanceof ServerLevel level) {
         this.burst(level, result.getLocation());
      }
   }

   private void burst(ServerLevel level, Vec3 at) {
      if (this.isRemoved()) {
         return;
      }
      Entity owner = this.getOwner();
      LivingEntity source = owner instanceof LivingEntity living ? living : null;
      boolean turned = UltronBolt.turned(owner);
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.0),
         e -> turned ? e != owner && e.isAlive() && UltronMob.is(e) : UltronMob.validTarget(e))) {
         double d = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0).distanceTo(at);
         if (d < 3.0) {
            float f = (float)(1.0 - 0.6 * (d / 3.0));
            e.hurt(this.damageSources().explosion(this, source), BalanceTable.ULTRON_MISSILE * f);
         }
      }
      UltronNet.fx(level, new UltronFxS2CPacket(UltronFxS2CPacket.MISSILE_BLAST, at, this.getDeltaMovement().normalize(), 1.0F, this.random.nextInt()), at, 96.0);
      UltronSounds.play(level, at, UltronSounds.MISSILE_EXPLODE, 2.2F, 0.9F + this.random.nextFloat() * 0.2F);
      this.discard();
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 160.0 * 160.0;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }
}

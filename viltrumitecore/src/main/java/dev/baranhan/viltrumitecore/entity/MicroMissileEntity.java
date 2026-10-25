package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Iron Man micro-missile (spec §8.3): homing on its mark, or straight. On
 * impact: direct hit damage plus a small splash, no block damage, own FX and
 * sound (no vanilla explosion). The target id is synced so the client trail
 * and the countermeasures (Stage 3, {@link Homing}) see the same target.
 */
public class MicroMissileEntity extends Projectile implements Homing {
   private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(MicroMissileEntity.class, EntityDataSerializers.INT);
   private int life;
   /** Client only: recent positions for the smoke trail (IronManCombatVfx). */
   public final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();

   public MicroMissileEntity(EntityType<? extends MicroMissileEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   public static MicroMissileEntity launch(ServerPlayer owner, Vec3 from, Vec3 direction, @Nullable Entity target) {
      return launch(owner, from, direction, target, 1.0F);
   }

   /** {@code damageMul}: suit missile factor (spec §13.7: War Machine ×1.4). */
   public static MicroMissileEntity launch(ServerPlayer owner, Vec3 from, Vec3 direction, @Nullable Entity target, float damageMul) {
      MicroMissileEntity missile = new MicroMissileEntity(ViltrumiteEntities.MICRO_MISSILE.get(), owner.level());
      missile.damageMul = damageMul;
      missile.setOwner(owner);
      missile.setPos(from);
      missile.setDeltaMovement(direction.normalize().scale(IronManRules.MISSILE_SPEED * 0.6));
      missile.retarget(target);
      owner.level().addFreshEntity(missile);
      return missile;
   }

   /** Server: suit missile factor. */
   private float damageMul = 1.0F;

   @Override
   protected void defineSynchedData() {
      this.entityData.define(TARGET, -1);
   }

   @Override
   public void retarget(@Nullable Entity target) {
      this.entityData.set(TARGET, target == null ? -1 : target.getId());
   }

   @Override
   @Nullable
   public Entity homingTarget() {
      int id = this.entityData.get(TARGET);
      return id < 0 ? null : this.level().getEntity(id);
   }

   @Override
   public void tick() {
      super.tick();
      Vec3 motion = this.getDeltaMovement();
      Entity target = this.homingTarget();
      double speed = Math.min(IronManRules.MISSILE_SPEED, motion.length() + 0.08);
      Vec3 dir = motion.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : motion.normalize();
      if (target != null && target.isAlive() && this.life > 2) {
         Vec3 to = target.getBoundingBox().getCenter().subtract(this.position());
         if (to.lengthSqr() > 1.0E-4) {
            dir = dir.add(to.normalize().subtract(dir).scale(IronManRules.MISSILE_TURN)).normalize();
         }
      }

      this.setDeltaMovement(dir.scale(speed));
      HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
      if (!this.level().isClientSide() && dev.baranhan.viltrumitecore.hero.HeroProjectiles.intercept(this, hit)) return;
      if (this.level().isClientSide() && hit instanceof EntityHitResult entityHit
         && entityHit.getEntity() instanceof net.minecraft.world.entity.player.Player) return;
      if (hit.getType() != HitResult.Type.MISS) {
         if (!this.level().isClientSide()) {
            this.onHit(hit);
         } else {
            this.discard();
         }

         return;
      }

      Vec3 next = this.position().add(this.getDeltaMovement());
      this.setPos(next.x, next.y, next.z);
      this.setYRot((float)(Math.atan2(dir.x, dir.z) * 180.0 / Math.PI));
      this.setXRot((float)(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180.0 / Math.PI));
      if (this.level().isClientSide()) {
         this.trail.addFirst(this.position());
         while (this.trail.size() > 14) {
            this.trail.removeLast();
         }
      }

      if (++this.life > IronManRules.MISSILE_LIFETIME) {
         if (!this.level().isClientSide()) {
            this.explode(this.position(), null);
         } else {
            this.discard();
         }
      }
   }

   @Override
   protected boolean canHitEntity(Entity entity) {
      return super.canHitEntity(entity) && entity != this.getOwner() && !(entity instanceof MicroMissileEntity) && !(entity instanceof RepulsorBlastEntity);
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      Entity entity = result.getEntity();
      if (entity instanceof net.minecraftforge.entity.PartEntity<?> part) {
         entity = part.getParent();
      }

      this.explode(result.getLocation(), entity);
   }

   @Override
   protected void onHitBlock(BlockHitResult result) {
      this.explode(result.getLocation(), null);
   }

   /** Splash stops at walls: the blast point must see the target's centre. */
   private boolean sees(Vec3 at, LivingEntity target) {
      Vec3 centre = target.getBoundingBox().getCenter();
      Vec3 toward = centre.subtract(at);
      if (toward.lengthSqr() < 0.0625) {
         return true;
      }

      // Start a bit off the impact face, else the struck block itself blocks the ray.
      Vec3 start = at.add(toward.normalize().scale(0.25));
      return this.level().clip(new net.minecraft.world.level.ClipContext(start, centre, net.minecraft.world.level.ClipContext.Block.COLLIDER,
         net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
   }

   /** Own explosion: direct hit + splash through vanilla hurt, no blocks, no vanilla explosion. */
   private void explode(Vec3 at, @Nullable Entity direct) {
      Entity owner = this.getOwner();
      LivingEntity shooter = owner instanceof LivingEntity living ? living : null;
      if (direct instanceof LivingEntity living) {
         int iframes = living.invulnerableTime;
         living.invulnerableTime = 0;
         if (!living.hurt(this.damageSources().explosion(this, shooter), IronManRules.MISSILE_HIT * this.damageMul)) {
            // Refused hit (PvP off, ally, layer): keep the target's own iframes.
            living.invulnerableTime = iframes;
         }
      }

      AABB box = new AABB(at, at).inflate(IronManRules.MISSILE_SPLASH_RADIUS);
      for (LivingEntity near : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != direct && e != owner && e.isAlive())) {
         if (near.position().distanceTo(at) <= IronManRules.MISSILE_SPLASH_RADIUS + near.getBbWidth() && sees(at, near)) {
            if (near.hurt(this.damageSources().explosion(this, shooter), IronManRules.MISSILE_SPLASH * this.damageMul)
               && dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(near)) {
               Vec3 push = near.position().subtract(at).normalize().scale(0.5);
               near.push(push.x, 0.25, push.z);
               near.hurtMarked = true;
            }
         }
      }

      if (owner instanceof ServerPlayer player) {
         dev.baranhan.viltrumitecore.hero.fx.HeroFx.flash(player, at);
         dev.baranhan.viltrumitecore.hero.fx.HeroFx.shockwave(player, at, 0.9F, null, (float)IronManRules.MISSILE_SPLASH_RADIUS);
      }

      this.level().playSound(null, at.x, at.y, at.z, IronManCombatSounds.MISSILE_EXPLODE.get(), SoundSource.PLAYERS, 1.2F,
         0.9F + this.random.nextFloat() * 0.2F);
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
      super.readAdditionalSaveData(tag);
      this.discard();
   }
}

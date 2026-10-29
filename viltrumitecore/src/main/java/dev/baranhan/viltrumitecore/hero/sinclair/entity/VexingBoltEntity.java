package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.Ovation;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vexing Bolt (spec §5.1): slow spectral orb, 44 ticks, damage 3.0 → 7.0 over 40 ticks, burst radius 2,
 * "Vexed" −25 % speed 1 s. One redirect (snap) toward a point; the assistant's copy deals half.
 */
public class VexingBoltEntity extends SinclairProjectile {
   /** Tick count of the redirect, -1 = none (client: snap flash and the turn trail). */
   private static final EntityDataAccessor<Integer> REDIRECT_AGE = SynchedEntityData.defineId(VexingBoltEntity.class, EntityDataSerializers.INT);
   @Nullable
   private Vec3 pendingTurn;
   private boolean exploded;

   public VexingBoltEntity(EntityType<? extends VexingBoltEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(REDIRECT_AGE, -1);
   }

   public int redirectAge() {
      return this.entityData.get(REDIRECT_AGE);
   }

   public boolean redirected() {
      return this.redirectAge() >= 0;
   }

   @Override
   protected int maxAge() {
      return SinclairRules.BOLT_LIFE;
   }

   /** §5.1: turn toward the aim point; once, and not before 4 ticks of flight. */
   public boolean redirect(Vec3 point) {
      if (this.redirected() || this.age < SinclairRules.BOLT_REDIRECT_MIN_AGE) {
         return false;
      }

      this.pendingTurn = point;
      this.entityData.set(REDIRECT_AGE, this.age);
      return true;
   }

   @Override
   protected void steer(ServerPlayer owner) {
      if (this.pendingTurn != null) {
         Vec3 dir = this.pendingTurn.subtract(this.position());
         if (dir.lengthSqr() > 1.0E-4) {
            this.setDeltaMovement(dir.normalize().scale(SinclairRules.BOLT_SPEED));
         }

         this.pendingTurn = null;
      }
   }

   public float damage() {
      float base = SinclairRules.boltDamage(this.age);
      return this.fromAssistant() ? base * SinclairRules.BOLT_ASSIST_FACTOR : base;
   }

   @Override
   protected void onHit(HitResult hit) {
      this.explode(hit.getLocation());
   }

   @Override
   protected void onHitEntity(EntityHitResult hit) {
      this.explode(hit.getLocation());
   }

   @Override
   protected void onHitBlock(BlockHitResult hit) {
      this.explode(hit.getLocation());
   }

   private void explode(Vec3 at) {
      if (this.exploded || this.level().isClientSide) {
         return;
      }

      this.exploded = true;
      ServerPlayer owner = SinclairOwned.liveOwner(this, this.sinclairOwner);
      boolean hitAny = false;
      if (owner != null) {
         float damage = this.damage();
         AABB box = new AABB(at, at).inflate(SinclairRules.BOLT_RADIUS);
         for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> this.canHitEntity(e) && e.isAlive())) {
            if (target.distanceToSqr(at) > (SinclairRules.BOLT_RADIUS + target.getBbWidth()) * (SinclairRules.BOLT_RADIUS + target.getBbWidth())) {
               continue;
            }

            if (SinclairCombat.projectileHit(owner, this, target, damage)) {
               SinclairCombat.vex(target);
               hitAny = true;
            }
         }

         if (hitAny) {
            Ovation.grant(owner);
         }

         SinclairCombat.flash(owner, at, 0.8F);
      }

      SinclairSounds.play(this.level(), at, SinclairSounds.BOLT_HIT, 1.0F, this.fromAssistant() ? 1.2F : 1.0F);
      this.discard();
   }

   @Override
   protected void onExpire() {
      this.explode(this.position());
   }
}

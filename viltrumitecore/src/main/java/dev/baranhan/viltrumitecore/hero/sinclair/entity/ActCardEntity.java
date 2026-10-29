package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Card Fan card (spec §6.1 BARRAGE): homing playing card, 1.8 damage times the act power. */
public class ActCardEntity extends SinclairProjectile {
   private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ActCardEntity.class, EntityDataSerializers.INT);
   private static final double SPEED = 1.15;
   private static final double TURN = 0.22;
   private int targetId = -1;
   private float power = 1.0F;

   public ActCardEntity(EntityType<? extends ActCardEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(COLOR, 0xC9B6FF);
   }

   public int color() {
      return this.entityData.get(COLOR);
   }

   public void aim(@Nullable LivingEntity target, float power, int color) {
      this.targetId = target == null ? -1 : target.getId();
      this.power = power;
      this.entityData.set(COLOR, color & 0xFFFFFF);
   }

   @Override
   protected int maxAge() {
      return 40;
   }

   @Override
   protected void steer(ServerPlayer owner) {
      if (this.age < 3 || this.targetId < 0) {
         return;
      }

      Entity target = this.level().getEntity(this.targetId);
      if (!(target instanceof LivingEntity living) || !living.isAlive()) {
         this.targetId = -1;
         return;
      }

      Vec3 want = living.getBoundingBox().getCenter().subtract(this.position()).normalize().scale(SPEED);
      Vec3 v = this.getDeltaMovement();
      this.setDeltaMovement(v.add(want.subtract(v).scale(TURN)).normalize().scale(SPEED));
   }

   @Override
   protected void onHitEntity(EntityHitResult hit) {
      LivingEntity target = SinclairCombat.living(hit.getEntity());
      ServerPlayer owner = SinclairOwned.liveOwner(this, this.sinclairOwner);
      if (target != null && owner != null) {
         SinclairCombat.projectileHit(owner, this, target, ActScript.BARRAGE_DAMAGE * this.power);
      }

      SinclairSounds.play(this.level(), this.position(), SinclairSounds.CARD_HIT, 0.8F, 0.9F + this.random.nextFloat() * 0.3F);
      this.discard();
   }

   @Override
   protected void onHitBlock(BlockHitResult hit) {
      SinclairSounds.play(this.level(), hit.getLocation(), SinclairSounds.CARD_HIT, 0.5F, 1.3F);
      this.discard();
   }
}

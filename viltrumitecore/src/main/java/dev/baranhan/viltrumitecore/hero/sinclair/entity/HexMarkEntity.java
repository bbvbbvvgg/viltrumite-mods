package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.sinclair.HexMarks;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One rabbit (spec §5.4): rides its target, lasts 50 t (player) / 70 t (mob). While it lives the target is
 * small (0.45×0.55, EntityEvent.Size), 36 % faster (transient), takes ×1.15 damage and cannot act. It ends
 * with its time, the target's death or its owner leaving; the size comes back with the vanilla refresh.
 */
public class HexMarkEntity extends SinclairMarker {
   public static final UUID SPEED_ID = UUID.fromString("5c1a7f30-2b7e-4c55-9a31-7f0de1b0a103");
   private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(HexMarkEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(HexMarkEntity.class, EntityDataSerializers.INT);
   private int age;
   private int registeredId = -1;

   public HexMarkEntity(EntityType<? extends HexMarkEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(TARGET_ID, -1);
      this.entityData.define(DURATION, 0);
   }

   public void setup(ServerPlayer owner, LivingEntity target, int ticks) {
      this.bindOwner(owner);
      this.entityData.set(TARGET_ID, target.getId());
      this.entityData.set(DURATION, ticks);
      this.setPos(target.position());
   }

   public int targetId() {
      return this.entityData.get(TARGET_ID);
   }

   public int duration() {
      return this.entityData.get(DURATION);
   }

   /** Ticks lived (client: from tickCount). */
   public int age() {
      return this.level().isClientSide ? this.tickCount : this.age;
   }

   @Nullable
   public LivingEntity target() {
      Entity entity = this.level().getEntity(this.targetId());
      return entity instanceof LivingEntity living ? living : null;
   }

   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      this.register();
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (TARGET_ID.equals(key) && this.level() != null && this.isAddedToWorld()) {
         this.register();
      }
   }

   private void register() {
      int id = this.targetId();
      if (id < 0 || id == this.registeredId) {
         return;
      }

      boolean client = this.level().isClientSide;
      if (this.registeredId >= 0) {
         HexMarks.remove(client, this.registeredId, this);
      }

      this.registeredId = id;
      HexMarks.put(client, id, this);
      LivingEntity target = this.target();
      if (target != null) {
         target.refreshDimensions();
         if (!client) {
            AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(SPEED_ID) == null) {
               speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Sinclair rabbit", SinclairRules.HEX_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }

            if (target instanceof Mob mob) {
               mob.setTarget(null);
               mob.getNavigation().stop();
            }
         }
      }
   }

   @Override
   public void onRemovedFromWorld() {
      boolean client = this.level().isClientSide;
      if (this.registeredId >= 0) {
         HexMarks.remove(client, this.registeredId, this);
      }

      LivingEntity target = this.target();
      if (target != null && !HexMarks.hexed(target)) {
         target.refreshDimensions();
         if (!client) {
            AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
               speed.removeModifier(SPEED_ID);
            }

            if (target.isAlive()) {
               SinclairSounds.play(this.level(), target.position().add(0.0, 0.5, 0.0), SinclairSounds.HEX_END, 0.8F, 1.0F);
            }
         }
      }

      super.onRemovedFromWorld();
   }

   @Override
   public void tick() {
      super.tick();
      LivingEntity target = this.target();
      if (target != null) {
         this.setPos(target.getX(), target.getY(), target.getZ());
      }

      if (this.level().isClientSide) {
         return;
      }

      this.age++;
      if (target == null || !target.isAlive() || target.isRemoved() || this.age >= this.duration()
         || SinclairOwned.liveOwner(this, this.owner) == null) {
         this.discard();
         return;
      }

      if (target instanceof Mob mob) {
         scamper(mob, this.age);
      }
   }

   /** §5.4: a hexed mob forgets its target and darts around in hops; a creeper's fuse winds back. */
   private static void scamper(Mob mob, int age) {
      mob.setTarget(null);
      if (mob instanceof Creeper creeper) {
         creeper.setSwellDir(-1);
      }

      if (age % 9 == 1 && mob.onGround()) {
         double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
         double speed = 0.32;
         mob.getNavigation().stop();
         mob.setDeltaMovement(new Vec3(Math.cos(angle) * speed, 0.38, Math.sin(angle) * speed));
         mob.setYRot((float) Math.toDegrees(Math.atan2(-Math.cos(angle), Math.sin(angle))));
         mob.yBodyRot = mob.getYRot();
         mob.hasImpulse = true;
      }
   }
}

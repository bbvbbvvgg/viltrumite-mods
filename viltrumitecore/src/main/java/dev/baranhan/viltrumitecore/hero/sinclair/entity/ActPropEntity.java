package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A stage prop of one act (spec §6.1): the thrown top hat (NOVA), the saw line (CUT), the velvet curtain
 * (AEGIS) and the falling chest (FINALE). Pure presentation and position: the act scene moves it and
 * owns every effect; the client draws it from kind, color, reach and its age.
 */
public class ActPropEntity extends SinclairMarker {
   public static final int NOVA_HAT = 0;
   public static final int CUT_LINE = 1;
   public static final int AEGIS_CURTAIN = 2;
   public static final int FINALE_CHEST = 3;
   private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ActPropEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ActPropEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> REACH = SynchedEntityData.defineId(ActPropEntity.class, EntityDataSerializers.FLOAT);
   /** Scene tick at which the prop spawned (the act clock of the owner snapshot). */
   private static final EntityDataAccessor<Integer> SCENE_START = SynchedEntityData.defineId(ActPropEntity.class, EntityDataSerializers.INT);
   private int life;
   private int maxLife = 100;

   public ActPropEntity(EntityType<? extends ActPropEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, 0);
      this.entityData.define(COLOR, 0xF2C14E);
      this.entityData.define(REACH, 1.0F);
      this.entityData.define(SCENE_START, 0);
   }

   public void setup(ServerPlayer owner, int kind, Vec3 at, float yaw, int color, float reach, int sceneTick, int maxLife) {
      this.bindOwner(owner);
      this.entityData.set(KIND, kind);
      this.entityData.set(COLOR, color & 0xFFFFFF);
      this.entityData.set(REACH, reach);
      this.entityData.set(SCENE_START, sceneTick);
      this.maxLife = maxLife;
      this.moveTo(at.x, at.y, at.z, yaw, 0.0F);
   }

   public int kind() {
      return this.entityData.get(KIND);
   }

   public int color() {
      return this.entityData.get(COLOR);
   }

   public float reach() {
      return this.entityData.get(REACH);
   }

   public int sceneStart() {
      return this.entityData.get(SCENE_START);
   }

   /** Server: the scene places the prop each tick. */
   public void place(Vec3 at) {
      this.setPos(at.x, at.y, at.z);
   }

   @Override
   public void tick() {
      this.xo = this.getX();
      this.yo = this.getY();
      this.zo = this.getZ();
      super.tick();
      if (this.level().isClientSide) {
         return;
      }

      if (++this.life > this.maxLife || SinclairOwned.liveOwner(this, this.owner) == null) {
         this.discard();
      }
   }
}

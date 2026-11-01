package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNetwork;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUprising;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The body of one network node: a modular pylon (per kind) standing on its site. It has no AI and no
 * health of its own: the node's shield and hull live in the network ({@link UltronNetwork}), hits go
 * there through {@link UltronUprising#damageNode}, sneak + use at its core runs the player's hack. The
 * director rebuilds bodies in ticking chunks and syncs their state every second; a body whose node is
 * gone removes itself. Never saved, never pushed, never moves.
 */
public class UltronNodeEntity extends Mob implements UltronMob {
   private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(UltronNodeEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(UltronNodeEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(UltronNodeEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Float> HULL = SynchedEntityData.defineId(UltronNodeEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> SHIELD = SynchedEntityData.defineId(UltronNodeEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> BUILD = SynchedEntityData.defineId(UltronNodeEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> NODE = SynchedEntityData.defineId(UltronNodeEntity.class, EntityDataSerializers.INT);

   public static final int F_DISCOVERED = 1;
   public static final int F_POWERED = 2;
   public static final int F_BULWARK = 4;
   public static final int F_RELOCATION = 8;
   public static final int F_FORTIFIED = 16;
   /** Collapse clip length (ticks) before the body is gone. */
   public static final int COLLAPSE_TICKS = 40;

   private int collapseTicks = -1;
   private int strayCheck;

   // ---- client presentation ----
   public int clientStateStart = -1000;
   public UltronNodeState clientState = UltronNodeState.PLANNED;
   public UltronNodeState clientPrevState = UltronNodeState.PLANNED;
   public int clientHitTick = -1000;
   private float clientLastHull = 1.0F;

   public UltronNodeEntity(EntityType<? extends UltronNodeEntity> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.setNoAi(true);
      this.setPersistenceRequired();
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 100.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.MOVEMENT_SPEED, 0.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, (byte)UltronNodeKind.UPLINK.ordinal());
      this.entityData.define(STATE, (byte)UltronNodeState.BUILDING.ordinal());
      this.entityData.define(FLAGS, (byte)0);
      this.entityData.define(HULL, 1.0F);
      this.entityData.define(SHIELD, 0.0F);
      this.entityData.define(BUILD, 0.0F);
      this.entityData.define(NODE, -1);
   }

   // ------------------------------------------------------------------ director side

   public void bind(int id, UltronNodeKind kind) {
      this.entityData.set(NODE, id);
      this.entityData.set(KIND, (byte)kind.ordinal());
   }

   public int nodeId() {
      return this.entityData.get(NODE);
   }

   public UltronNodeKind nodeKind() {
      return UltronNodeKind.byId(this.entityData.get(KIND));
   }

   public UltronNodeState nodeState() {
      return UltronNodeState.byId(this.entityData.get(STATE));
   }

   public float hullShare() {
      return this.entityData.get(HULL);
   }

   public float shieldShare() {
      return this.entityData.get(SHIELD);
   }

   public float buildShare() {
      return this.entityData.get(BUILD);
   }

   public boolean nodeFlag(int f) {
      return (this.entityData.get(FLAGS) & f) != 0;
   }

   /** The node's state, shares and links for the renderer. */
   public void sync(UltronNetwork.Node n, boolean bulwarkLinked, boolean relocation) {
      if (this.collapseTicks >= 0) {
         return;
      }
      this.entityData.set(KIND, (byte)n.kind.ordinal());
      this.entityData.set(STATE, (byte)n.state.ordinal());
      this.entityData.set(HULL, round(n.hullFraction()));
      float max = n.maxShield();
      this.entityData.set(SHIELD, max <= 0.0F ? 0.0F : round(n.shield / max));
      this.entityData.set(BUILD, round(n.build));
      int flags = (n.discovered ? F_DISCOVERED : 0) | (n.powered ? F_POWERED : 0) | (bulwarkLinked ? F_BULWARK : 0) | (relocation ? F_RELOCATION : 0)
         | (n.fortified() ? F_FORTIFIED : 0);
      this.entityData.set(FLAGS, (byte)flags);
   }

   /** Shares in steps of 1/64: a regenerating shield does not resend every tick. */
   private static float round(float v) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, v)) * 64.0F) / 64.0F;
   }

   /** The node was destroyed: the collapse plays, then the body is gone. */
   public void collapse() {
      if (this.collapseTicks < 0) {
         this.collapseTicks = 0;
         this.entityData.set(STATE, (byte)UltronNodeState.DESTROYED.ordinal());
      }
   }

   public boolean collapsing() {
      return this.collapseTicks >= 0 || this.nodeState() == UltronNodeState.DESTROYED;
   }

   @Override
   public void tick() {
      super.tick();
      this.setDeltaMovement(Vec3.ZERO);
      if (this.level().isClientSide) {
         float hull = this.hullShare();
         if (hull < this.clientLastHull - 1.0E-3F) {
            this.clientHitTick = this.tickCount;
         }
         this.clientLastHull = hull;
         return;
      }
      if (this.getHealth() < this.getMaxHealth()) {
         this.setHealth(this.getMaxHealth()); // the real hull lives in the network
      }
      if (this.collapseTicks >= 0) {
         if (++this.collapseTicks >= COLLAPSE_TICKS) {
            this.discard();
         }
         return;
      }
      if (++this.strayCheck >= 40) {
         this.strayCheck = 0;
         UltronNetwork.Node n = UltronUprising.node(this.level(), this.nodeId());
         if (n == null || !n.standing() || n.entity != null && !n.entity.equals(this.getUUID())) {
            this.discard(); // its node is gone (event over, another body): nothing left to stand for
         }
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (STATE.equals(key) && this.level().isClientSide) {
         UltronNodeState now = this.nodeState();
         if (now != this.clientState) {
            this.clientPrevState = this.clientState;
            this.clientState = now;
            this.clientStateStart = this.tickCount;
         }
      }
   }

   // ------------------------------------------------------------------ player interaction

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.collapsing()) {
         return false;
      }
      if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL) || source.is(DamageTypes.CRAMMING)
         || source.is(DamageTypes.STARVE) || source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)) {
         return false;
      }
      return UltronUprising.damageNode(this, source, amount);
   }

   @Override
   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || this.collapsing()) {
         return InteractionResult.PASS;
      }
      if (!this.level().isClientSide) {
         UltronUprising.hackNode(player, this);
      }
      return InteractionResult.sidedSuccess(this.level().isClientSide);
   }

   @Override
   protected Component getTypeName() {
      return Component.translatable("viltrumitecore.ultron.node." + this.nodeKind().key());
   }

   // ------------------------------------------------------------------ a fixed structure

   @Override
   public void travel(Vec3 input) {
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public void push(Entity entity) {
   }

   @Override
   protected void doPush(Entity entity) {
   }

   @Override
   public boolean isPushedByFluid() {
      return false;
   }

   @Override
   public void knockback(double strength, double x, double z) {
   }

   @Override
   public boolean isPickable() {
      return !this.isRemoved() && !this.collapsing();
   }

   @Override
   public boolean canBeLeashed(Player player) {
      return false;
   }

   @Override
   public boolean canChangeDimensions() {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   public void checkDespawn() {
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   protected boolean shouldDropLoot() {
      return false;
   }

   @Override
   public boolean shouldDropExperience() {
      return false;
   }

   @Override
   public boolean canBreatheUnderwater() {
      return true;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return UltronMob.side(other) || super.isAlliedTo(other);
   }

   @Nullable
   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   @Nullable
   @Override
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Nullable
   @Override
   protected SoundEvent getAmbientSound() {
      return null;
   }
}

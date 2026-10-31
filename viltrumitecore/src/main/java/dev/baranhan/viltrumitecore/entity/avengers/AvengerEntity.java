package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import dev.baranhan.viltrumitecore.hero.captain.assemble.AvengersTeam;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One summoned Avenger (spec §5.7): a real server mob, never saved, no loot, no vanilla sounds. Kind, owner,
 * the body action (with a restart serial), flight and the scene phase are synced; the scene scripts the
 * body during the ceremony (kinematic, untouchable), the kind's {@link AvengerBrain} fights afterwards.
 */
public class AvengerEntity extends PathfinderMob {
   private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(AvengerEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(AvengerEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(AvengerEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(AvengerEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(AvengerEntity.class, EntityDataSerializers.BYTE);

   public static final int PHASE_ARRIVAL = 0;
   public static final int PHASE_CIRCLE = 1;
   public static final int PHASE_FIGHT = 2;
   public static final int PHASE_DEPART = 3;

   @Nullable public UUID ownerUuid;
   @Nullable public AvengerBrain brain;
   /** Ceremony / departure: the scene moves the body, no AI, no damage taken. */
   public boolean scripted = true;
   /** Scripted kinematic target (scene) and its velocity for the client interpolation. */
   @Nullable public Vec3 scriptTo;
   private int serial;
   private int actionStart;
   private boolean gone;

   // --- client presentation clocks (client tickCount based) ---
   public int clientActionStart = -1000;
   public int clientAction;
   public float clientFly;

   public AvengerEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setPersistenceRequired();
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.ATTACK_DAMAGE, 6.0)
         .add(Attributes.ARMOR, 8.0).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.FLYING_SPEED, 0.6);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, (byte)0);
      this.entityData.define(OWNER, -1);
      this.entityData.define(ACTION, 0);
      this.entityData.define(FLYING, false);
      this.entityData.define(PHASE, (byte)PHASE_ARRIVAL);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
   }

   /** Server: configure before it is added to the level. */
   public void setup(ServerPlayer owner, AvengerKind kind) {
      this.ownerUuid = owner.getUUID();
      this.entityData.set(KIND, (byte)kind.ordinal());
      this.entityData.set(OWNER, owner.getId());
      this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.health);
      this.getAttribute(Attributes.ARMOR).setBaseValue(kind.armor);
      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed);
      this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(kind.knockbackResist);
      this.setHealth(kind.health);
      this.setCustomName(Component.translatable("entity.viltrumitecore.avenger." + kind.key()));
      this.setCustomNameVisible(false);
      this.brain = AvengerBrain.create(this, kind);
      this.refreshDimensions();
      this.setMaxUpStep(kind == AvengerKind.HULK ? 1.6F : 1.1F);
   }

   /** Client-only preview entity (encyclopedia portrait): which Avenger it draws as. */
   public void previewKind(AvengerKind kind) {
      this.entityData.set(KIND, (byte)kind.ordinal());
   }

   public AvengerKind kind() {
      return AvengerKind.byOrdinal(this.entityData.get(KIND));
   }

   public int ownerId() {
      return this.entityData.get(OWNER);
   }

   @Nullable
   public ServerPlayer owner() {
      if (this.ownerUuid == null || !(this.level() instanceof ServerLevel level)) {
         return null;
      }
      return level.getServer().getPlayerList().getPlayer(this.ownerUuid);
   }

   // --- action / phase ---

   public AvengerAction action() {
      return AvengerAction.byOrdinal(this.entityData.get(ACTION) & 0xFF);
   }

   /** Server: (re)starts a body action; every call restarts the client clip. */
   public void play(AvengerAction action) {
      this.serial = (this.serial + 1) & 0xFFFF;
      this.actionStart = this.tickCount;
      this.entityData.set(ACTION, action.ordinal() | this.serial << 8);
   }

   /** Server ticks since the current action started. */
   public int actionAge() {
      return this.tickCount - this.actionStart;
   }

   public boolean flying() {
      return this.entityData.get(FLYING);
   }

   public void setFlying(boolean on) {
      if (this.flying() != on) {
         this.entityData.set(FLYING, on);
      }
      this.setNoGravity(on || this.scripted);
   }

   public int phase() {
      return this.entityData.get(PHASE);
   }

   public void setPhase(int phase) {
      this.entityData.set(PHASE, (byte)phase);
      this.scripted = phase != PHASE_FIGHT;
      this.noPhysics = this.scripted;
      this.setNoGravity(this.scripted || this.flying());
      if (!this.scripted) {
         this.scriptTo = null;
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (KIND.equals(key)) {
         this.refreshDimensions();
      }
      if (ACTION.equals(key) && this.level().isClientSide) {
         this.clientAction = this.entityData.get(ACTION) & 0xFF;
         this.clientActionStart = this.tickCount;
      }
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      AvengerKind kind = this.kind();
      return EntityDimensions.scalable(kind.width, kind.height);
   }

   // --- tick ---

   @Override
   public void tick() {
      if (this.scripted && !this.level().isClientSide && this.scriptTo != null) {
         Vec3 to = this.scriptTo;
         this.setDeltaMovement(to.subtract(this.position()));
      }
      super.tick();
      if (this.level().isClientSide) {
         return;
      }
      if (this.scripted) {
         if (this.scriptTo != null) {
            this.setPos(this.scriptTo);
         }
         this.getNavigation().stop();
         return;
      }
      if (this.tickCount % 20 == 0 && !AvengersTeam.member(this)) {
         this.vanish(); // a lost scene: never linger
         return;
      }
      if (this.isAlive() && this.brain != null) {
         this.brain.tick();
      }
   }

   /** Scripted move to an exact point this tick (scene; kinematic, through blocks). */
   public void scriptTo(Vec3 at, float yaw) {
      this.scriptTo = at;
      this.setYRot(yaw);
      this.setYBodyRot(yaw);
      this.setYHeadRot(yaw);
      this.yRotO = yaw;
   }

   @Override
   public void travel(Vec3 input) {
      if (this.scripted) {
         return; // the scene positions the body
      }
      super.travel(input);
   }

   // --- allies, damage, death ---

   @Override
   public boolean isAlliedTo(Entity other) {
      if (other == null) {
         return false;
      }
      if (other instanceof AvengerEntity a) {
         return this.ownerUuid != null ? this.ownerUuid.equals(a.ownerUuid) : a.ownerId() == this.ownerId();
      }
      if (this.ownerUuid != null && this.ownerUuid.equals(other.getUUID())) {
         return true;
      }
      if (this.level().isClientSide && other.getId() == this.ownerId()) {
         return true;
      }
      ServerPlayer owner = this.level().isClientSide ? null : this.owner();
      return owner != null && owner != other && (owner.isAlliedTo(other) || AvengersTeam.ally(owner, other)) || super.isAlliedTo(other);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.scripted && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
         return false;
      }
      Entity attacker = source.getEntity();
      if (attacker != null && this.isAlliedTo(attacker)) {
         return false;
      }
      if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL) || source.is(DamageTypes.LIGHTNING_BOLT)) {
         return false;
      }
      boolean hurt = super.hurt(source, amount);
      if (hurt && attacker instanceof LivingEntity living && living != this && this.brain != null) {
         this.brain.onHurtBy(living);
      }
      return hurt;
   }

   /** Legal target of this Avenger's hits (no friendly fire; players only with PvP). */
   public boolean mayHit(LivingEntity target) {
      if (target == this || !target.isAlive() || this.isAlliedTo(target) || HeroDamage.isProtected(target)) {
         return false;
      }
      if (target instanceof net.minecraft.world.entity.npc.AbstractVillager) {
         return false;
      }
      if ((target instanceof net.minecraft.world.entity.animal.AbstractGolem) && !this.turnedOnTeam(target)) {
         return false; // village guards are spared until they turn on Steve or the team
      }
      return CaptainTargets.mayHit(this.owner(), target);
   }

   /** The mob hunts Steve or the team, is angry at Steve, hit Steve lately, or Steve struck it himself. */
   public boolean turnedOnTeam(LivingEntity e) {
      ServerPlayer owner = this.owner();
      if (owner == null) {
         return false;
      }
      LivingEntity hunted = e instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
      boolean huntsTeam = hunted != null && hunted.isAlive() && this.isAlliedTo(hunted);
      boolean angry = e instanceof net.minecraft.world.entity.NeutralMob n && n.isAngryAt(owner);
      dev.baranhan.viltrumitecore.hero.captain.CaptainState state = dev.baranhan.viltrumitecore.hero.captain.CaptainState.of(owner);
      boolean hitOwner = state != null && state.lastAttackerTicks > 0 && state.lastAttackerId == e.getId();
      boolean marked = state != null && state.markTicks > 0 && e.getUUID().equals(state.markTarget);
      return AvengerBrain.hostileToTeam(huntsTeam, angry, hitOwner, marked);
   }

   @Override
   public void die(DamageSource source) {
      if (!this.level().isClientSide && !this.gone && this.phase() == PHASE_FIGHT) {
         // spec §6: a beaten Avenger retreats (departure) instead of dying
         this.setHealth(1.0F);
         AvengersTeam.retreat(this);
         return;
      }
      super.die(source);
      this.setFlying(false);
   }

   @Override
   protected void tickDeath() {
      ++this.deathTime;
      if (this.deathTime >= 20 && !this.level().isClientSide && !this.isRemoved()) {
         this.remove(RemovalReason.KILLED);
      }
   }

   /** Server: leave the field now (scene end, cleanup). */
   public void vanish() {
      if (!this.gone) {
         this.gone = true;
         this.discard();
      }
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
   public int getExperienceReward() {
      return 0;
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return !this.scripted && super.isPushable();
   }

   @Override
   public boolean fireImmune() {
      return this.kind() != AvengerKind.BLACK_WIDOW && this.kind() != AvengerKind.HAWKEYE;
   }

   @Override
   public boolean canBreatheUnderwater() {
      return true;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 128 * 128;
   }

   // --- no vanilla sounds ---

   @Nullable
   @Override
   protected SoundEvent getAmbientSound() {
      return null;
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

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
   }

   @Override
   public void playSound(SoundEvent sound, float volume, float pitch) {
   }

   @Override
   protected float getSoundVolume() {
      return 0.0F;
   }

   @Override
   public void onRemovedFromWorld() {
      super.onRemovedFromWorld();
      if (!this.level().isClientSide) {
         AvengersTeam.onMemberGone(this);
      }
   }
}

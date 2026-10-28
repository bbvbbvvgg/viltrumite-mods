package dev.baranhan.viltrumitecore.entity.jinwoo;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import dev.baranhan.viltrumitecore.hero.jinwoo.ShadowArmy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A real server-side shadow of Jinwoo's army (spec §6). Never saved to the world,
 * no loot or experience, no vanilla sounds or particles. Kind, elite, mode, owner and
 * the hidden/dissolving states are synched; attacks and re-rises are entity events.
 * The army logic (targets, formation, orders) lives in {@link ShadowBrain}.
 */
public class ShadowSoldierEntity extends PathfinderMob {
   private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Boolean> ELITE = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> DISSOLVE = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> EMPOWERED = SynchedEntityData.defineId(ShadowSoldierEntity.class, EntityDataSerializers.BOOLEAN);

   public static final byte EVENT_ATTACK = 64;
   public static final byte EVENT_SPECIAL = 65;
   public static final byte EVENT_RISE = 66;
   public static final byte EVENT_EYES = 67;

   /** Live server shadows per dimension (spec §6.4 level cap). */
   private static final Map<ResourceKey<Level>, Integer> LIVE = new HashMap<>();

   @Nullable
   public UUID ownerUuid;
   public int serial = -1;
   public int slot;
   @Nullable
   public UUID ward;
   /** Bell sentinels guard a point instead of a creature. */
   @Nullable
   public Vec3 wardPoint;
   public final ShadowBrain brain = new ShadowBrain(this);
   private boolean counted;
   private int dissolveTicks = -1;
   /** Dissolve that frees the roster slot at once (true) or a death (false). */
   private boolean released;

   // --- client presentation clocks (client tickCount based) ---
   public int clientRiseStart;
   public int clientAttackStart = -1000;
   public boolean clientSpecial;
   public int clientEyesStart = -1000;
   public float clientWalk;
   public int clientDissolveStart = -1;

   public ShadowSoldierEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setPersistenceRequired();
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.36).add(Attributes.ATTACK_DAMAGE, 5.0)
         .add(Attributes.ARMOR, 4.0).add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, (byte)0);
      this.entityData.define(ELITE, false);
      this.entityData.define(MODE, (byte)0);
      this.entityData.define(OWNER, -1);
      this.entityData.define(HIDDEN, false);
      this.entityData.define(DISSOLVE, -1);
      this.entityData.define(EMPOWERED, false);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
   }

   /** Server: configure a fresh shadow before it is added to the level. */
   public void setup(ServerPlayer owner, int serial, ShadowKind kind, boolean elite, int slot) {
      this.ownerUuid = owner.getUUID();
      this.serial = serial;
      this.slot = slot;
      this.entityData.set(KIND, (byte)kind.ordinal());
      this.entityData.set(ELITE, elite);
      this.entityData.set(OWNER, owner.getId());
      float health = kind.maxHealth(elite);
      this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
      this.getAttribute(Attributes.ARMOR).setBaseValue(kind.armor);
      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed);
      this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(kind.damage);
      this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(kind.knockbackImmune() ? 1.0 : 0.3);
      this.setHealth(health);
      if (elite && kind.eliteName() != null) {
         this.setCustomName(Component.translatable("entity.viltrumitecore.shadow." + kind.eliteName()));
      }
      this.refreshDimensions();
   }

   public ShadowKind kind() {
      return ShadowKind.byOrdinal(this.entityData.get(KIND));
   }

   public boolean elite() {
      return this.entityData.get(ELITE);
   }

   public ShadowMode mode() {
      return ShadowMode.byOrdinal(this.entityData.get(MODE));
   }

   public void setMode(ShadowMode mode) {
      this.entityData.set(MODE, (byte)mode.ordinal());
      this.setHidden(mode == ShadowMode.SENTINEL && this.brain.sentinelHidden());
   }

   public int ownerId() {
      return this.entityData.get(OWNER);
   }

   public boolean hidden() {
      return this.entityData.get(HIDDEN);
   }

   public void setHidden(boolean hidden) {
      this.entityData.set(HIDDEN, hidden);
   }

   public boolean empowered() {
      return this.entityData.get(EMPOWERED);
   }

   public void setEmpowered(boolean on) {
      if (this.empowered() != on) {
         this.entityData.set(EMPOWERED, on);
      }
   }

   /** Server tick at which the dissolve started (client: ticks since), -1 none. */
   public int dissolveStart() {
      return this.entityData.get(DISSOLVE);
   }

   public boolean dissolving() {
      return this.dissolveTicks >= 0 || this.dissolveStart() >= 0;
   }

   /** Server: sink into the puddle (14 t) and vanish; the roster entry is free again. */
   public void dissolve() {
      if (this.level().isClientSide || this.dissolveTicks >= 0 || !this.isAlive()) {
         return;
      }
      this.released = true;
      this.dissolveTicks = 0;
      this.entityData.set(DISSOLVE, this.tickCount);
      this.getNavigation().stop();
      ShadowArmy.onShadowGone(this, false);
   }

   @Nullable
   public ServerPlayer owner() {
      if (this.ownerUuid == null || !(this.level() instanceof ServerLevel level)) {
         return null;
      }
      return level.getServer().getPlayerList().getPlayer(this.ownerUuid);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }
      if (this.dissolveTicks >= 0) {
         this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
         if (++this.dissolveTicks >= JinwooRules.SHADOW_DISSOLVE) {
            this.discard();
         }
         return;
      }
      if (this.isAlive()) {
         this.brain.tick();
      }
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
   }

   @Override
   public void handleEntityEvent(byte id) {
      switch (id) {
         case EVENT_ATTACK -> {
            this.clientAttackStart = this.tickCount;
            this.clientSpecial = false;
         }
         case EVENT_SPECIAL -> {
            this.clientAttackStart = this.tickCount;
            this.clientSpecial = true;
         }
         case EVENT_RISE -> this.clientRiseStart = this.tickCount;
         case EVENT_EYES -> this.clientEyesStart = this.tickCount;
         default -> super.handleEntityEvent(id);
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (KIND.equals(key) || ELITE.equals(key)) {
         this.refreshDimensions();
      }
      if (DISSOLVE.equals(key) && this.level().isClientSide && this.entityData.get(DISSOLVE) >= 0) {
         this.clientDissolveStart = this.tickCount;
      }
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      ShadowKind kind = this.kind();
      float s = kind.scale(this.elite());
      return switch (kind) {
         case INFANTRY -> EntityDimensions.scalable(0.62F * s, 1.9F * s);
         case KNIGHT -> EntityDimensions.scalable(0.7F * s, 2.15F * s);
         case INSECT -> EntityDimensions.scalable(0.7F * s, 1.95F * s);
         case BEAST -> EntityDimensions.scalable(1.35F * s, 1.55F * s);
      };
   }

   // --- allies, damage, death (spec §6.2) ---

   @Override
   public boolean isAlliedTo(Entity other) {
      if (other == null) {
         return false;
      }
      if (other instanceof ShadowSoldierEntity shadow) {
         return this.ownerUuid != null ? this.ownerUuid.equals(shadow.ownerUuid) : shadow.ownerId() == this.ownerId();
      }
      if (this.ownerUuid != null && this.ownerUuid.equals(other.getUUID())) {
         return true;
      }
      if (this.level().isClientSide && other.getId() == this.ownerId()) {
         return true;
      }
      ServerPlayer owner = this.level().isClientSide ? null : this.owner();
      return owner != null && owner != other && owner.isAlliedTo(other) || super.isAlliedTo(other);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.hidden() || this.dissolving()) {
         return false;
      }
      Entity attacker = source.getEntity();
      if (attacker != null && this.isAlliedTo(attacker)) {
         return false;
      }
      boolean hurt = super.hurt(source, amount);
      if (hurt && attacker instanceof LivingEntity living && living != this) {
         this.brain.onHurtBy(living);
      }
      return hurt;
   }

   @Override
   public void die(DamageSource source) {
      if (!this.level().isClientSide && this.dissolveTicks < 0 && !this.released) {
         ShadowArmy.onShadowGone(this, true);
      }
      super.die(source);
      if (!this.level().isClientSide) {
         this.entityData.set(DISSOLVE, this.tickCount);
      }
   }

   /** Death = the dissolve (14 t), no poof particles, no loot. */
   @Override
   protected void tickDeath() {
      ++this.deathTime;
      if (this.deathTime >= JinwooRules.SHADOW_DISSOLVE && !this.level().isClientSide && !this.isRemoved()) {
         this.remove(RemovalReason.KILLED);
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
   public boolean isPickable() {
      return !this.hidden() && !this.dissolving() && super.isPickable();
   }

   @Override
   public boolean isPushable() {
      return !this.hidden() && super.isPushable();
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public boolean isInvisible() {
      return this.hidden() || super.isInvisible();
   }

   @Override
   public boolean fireImmune() {
      return true;
   }

   @Override
   public boolean canBreatheUnderwater() {
      return true;
   }

   @Override
   public boolean isSensitiveToWater() {
      return false;
   }

   // --- no vanilla sounds (spec §1.2) ---

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

   // --- level cap bookkeeping ---

   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide && !this.counted) {
         this.counted = true;
         LIVE.merge(this.level().dimension(), 1, Integer::sum);
      } else if (this.level().isClientSide) {
         this.clientRiseStart = this.tickCount;
      }
   }

   @Override
   public void onRemovedFromWorld() {
      super.onRemovedFromWorld();
      if (!this.level().isClientSide && this.counted) {
         this.counted = false;
         LIVE.merge(this.level().dimension(), -1, (a, b) -> Math.max(0, a + b) == 0 ? null : a + b);
         if (this.dissolveTicks < 0 && !this.released && this.isAlive()) {
            // Unloaded chunk / level removal without a dissolve: free the roster slot.
            this.released = true;
            ShadowArmy.onShadowGone(this, false);
         }
      }
   }

   public static int live(ServerLevel level) {
      return LIVE.getOrDefault(level.dimension(), 0);
   }

   public static void clearCounts() {
      LIVE.clear();
   }

   /** True when the target is a legal hit for this shadow (spec §6.2). */
   public boolean mayHit(LivingEntity target) {
      if (target == this || !target.isAlive() || this.isAlliedTo(target) || HeroDamage.isProtected(target)) {
         return false;
      }
      if (target instanceof net.minecraft.world.entity.npc.AbstractVillager || target instanceof net.minecraft.world.entity.animal.IronGolem
         || target instanceof net.minecraft.world.entity.animal.SnowGolem) {
         return false;
      }
      if (target instanceof Player player) {
         ServerPlayer owner = this.owner();
         return !player.isCreative() && !player.isSpectator() && owner != null && dev.baranhan.viltrumitecore.hero.ServerRules.pvp(owner)
            && this.brain.playerAllowed(player);
      }
      return true;
   }

   public boolean impulseAllowed(LivingEntity target) {
      return HeroRegistry.allowsImpulse(target);
   }

   public Vec3 feet() {
      return this.position();
   }
}

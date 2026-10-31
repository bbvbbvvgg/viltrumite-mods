package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.legion.IronManLegion;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionAllies;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionMove;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionSteering;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionSuitBrain;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkDamage;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureActor;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An autonomous suit of the Iron Legion (stage 6, design §3–§6). The body
 * only mirrors the owner's {@link dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster}:
 * durability lives there, damage goes there, the entity never heals. It is
 * never saved; it checks every tick that its owner, his roster and his squad
 * still claim it and leaves the world otherwise. Movement is the brain's own
 * velocity applied with {@code move(SELF)} (no gravity, no vanilla travel).
 */
public class LegionSuitEntity extends PathfinderMob implements SignatureActor.Provider {
   public static final int FLAG_CAMO = 1;
   public static final int FLAG_CRITICAL = 2;
   public static final int FLAG_LASER = 4;
   public static final int FLAG_PULSE = 8;
   public static final int FLAG_GUN = 16;
   public static final int FLAG_BOOST = 32;
   public static final int FLAG_FIST_AWAY = 64;

   private static final EntityDataAccessor<Byte> MARK = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.BYTE);
   /** (sequence &lt;&lt; 8) | move id: a new sequence restarts the same move. */
   private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.INT);
   /** Entity id the arms aim at, -1 = none. */
   private static final EntityDataAccessor<Integer> AIM = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> FRACTION = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.BYTE);
   /** Thruster output 0..1 (flames, engine loop pitch). */
   private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Byte> SLOT = SynchedEntityData.defineId(LegionSuitEntity.class, EntityDataSerializers.BYTE);

   @Nullable
   private UUID ownerUuid;
   /** Server only. */
   @Nullable
   public LegionSuitBrain brain;
   /** Server: the ATTACK order target id (villagers may be hit only as this). */
   private int orderedTarget = -1;
   private int moveSeq;
   private int lastHitMove = -100;
   /** Set once the roster is updated for this suit leaving the world (no double recall). */
   private boolean released;
   private final Actor actor = new Actor();

   // --- client presentation clocks (client tickCount based) ---
   public int clientPhaseStart;
   public int clientMoveStart = -1000;
   public LegionMove clientMove = LegionMove.NONE;
   public float clientThrottle;
   public float clientThrottleO;

   public LegionSuitEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.setPersistenceRequired();
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ARMOR, LegionRules.SHELL_ARMOR)
         .add(Attributes.FOLLOW_RANGE, LegionRules.SCAN_RADIUS).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FLYING_SPEED, 0.6);
   }

   /** Server: a fresh suit, configured before it is added to the level. */
   public void setup(ServerPlayer owner, MarkId mark, int slot, float fraction) {
      this.ownerUuid = owner.getUUID();
      this.entityData.set(MARK, (byte)mark.ordinal());
      this.entityData.set(OWNER, owner.getId());
      this.entityData.set(SLOT, (byte)slot);
      this.entityData.set(FRACTION, fraction);
      this.brain = new LegionSuitBrain(this, mark, slot);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(MARK, (byte)0);
      this.entityData.define(OWNER, -1);
      this.entityData.define(PHASE, (byte)LegionPhase.APPROACH.ordinal());
      this.entityData.define(MOVE, 0);
      this.entityData.define(AIM, -1);
      this.entityData.define(FRACTION, 1.0F);
      this.entityData.define(FLAGS, (byte)0);
      this.entityData.define(THROTTLE, 1.0F);
      this.entityData.define(SLOT, (byte)0);
   }

   @Override
   protected void registerGoals() {
   }

   // --- synced state ---

   public MarkId mark() {
      MarkId id = MarkId.byId(this.entityData.get(MARK));
      return id == null ? MarkId.MARK_7 : id;
   }

   public int ownerId() {
      return this.entityData.get(OWNER);
   }

   @Nullable
   public UUID ownerUuid() {
      return this.ownerUuid;
   }

   public int slot() {
      return this.entityData.get(SLOT);
   }

   public LegionPhase phase() {
      return LegionPhase.byId(this.entityData.get(PHASE));
   }

   public void setPhase(LegionPhase phase) {
      this.entityData.set(PHASE, (byte)phase.ordinal());
   }

   public float fraction() {
      return this.entityData.get(FRACTION);
   }

   public boolean flag(int flag) {
      return (this.entityData.get(FLAGS) & flag) != 0;
   }

   public void setFlag(int flag, boolean on) {
      byte flags = this.entityData.get(FLAGS);
      byte next = (byte)(on ? flags | flag : flags & ~flag);
      if (next != flags) {
         this.entityData.set(FLAGS, next);
      }
   }

   public int aimId() {
      return this.entityData.get(AIM);
   }

   public void setAim(int id) {
      if (this.entityData.get(AIM) != id) {
         this.entityData.set(AIM, id);
      }
   }

   public float throttle() {
      return this.entityData.get(THROTTLE);
   }

   public void setThrottle(float throttle) {
      float t = Math.max(0.0F, Math.min(1.0F, throttle));
      if (Math.abs(this.entityData.get(THROTTLE) - t) > 0.04F || t == 0.0F && this.entityData.get(THROTTLE) != 0.0F) {
         this.entityData.set(THROTTLE, t);
      }
   }

   /** Server: start a one-shot move on every client (restarts when played again). */
   public void playMove(LegionMove move) {
      this.moveSeq = (this.moveSeq + 1) & 0xFFFFFF;
      this.entityData.set(MOVE, this.moveSeq << 8 | move.ordinal());
   }

   public int orderedTarget() {
      return this.orderedTarget;
   }

   public void setOrderedTarget(int id) {
      this.orderedTarget = id;
   }

   @Nullable
   public ServerPlayer owner() {
      if (this.ownerUuid == null || !(this.level() instanceof ServerLevel level)) {
         return null;
      }

      return level.getServer().getPlayerList().getPlayer(this.ownerUuid);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (!this.level().isClientSide) {
         return;
      }

      // tickCount 0 = the spawn data of a suit that was already flying (late join, tracking range): no replay.
      boolean initial = this.tickCount == 0;
      if (PHASE.equals(key)) {
         this.clientPhaseStart = initial ? -200 : this.tickCount;
      } else if (MOVE.equals(key)) {
         this.clientMove = LegionMove.byId(this.entityData.get(MOVE) & 0xFF);
         this.clientMoveStart = initial ? -1000 : this.tickCount;
      }
   }

   /** Client: ticks since the phase began. */
   public int clientPhaseAge() {
      return this.tickCount - this.clientPhaseStart;
   }

   // --- tick ---

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.clientThrottleO = this.clientThrottle;
         this.clientThrottle += (this.throttle() - this.clientThrottle) * 0.25F;
         return;
      }

      ServerPlayer owner = this.owner();
      if (!this.claimed(owner)) {
         this.release();
         this.discard();
         return;
      }

      if (this.brain != null && this.isAlive()) {
         this.brain.tick(owner);
      }
   }

   /** Owner online in this level, still Iron Man, the roster has the mark in the Legion and his squad owns this body. */
   private boolean claimed(@Nullable ServerPlayer owner) {
      if (owner == null || this.brain == null || owner.level() != this.level()) {
         return false;
      }

      LegionPhase phase = this.phase();
      if (phase == LegionPhase.LEAVE || phase == LegionPhase.CRASH) {
         // Already handed back (death, hero change, a broken mark): only the exit plays out.
         return true;
      }

      if (!owner.isAlive()) {
         return false;
      }

      IronManState state = IronManState.of(owner);
      return state != null && state.roster.location(this.mark()) == MarkLocation.LEGION && IronManLegion.owns(owner, this);
   }

   /** Vanilla travel is off: the brain moves the body itself. */
   @Override
   public void travel(Vec3 input) {
   }

   /** Apply the brain's velocity this tick (collision unless noPhysics). */
   public void fly(Vec3 velocity) {
      this.setDeltaMovement(velocity);
      this.move(net.minecraft.world.entity.MoverType.SELF, velocity);
   }

   @Override
   protected void pushEntities() {
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public void push(Entity entity) {
   }

   @Override
   public void knockback(double strength, double x, double z) {
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   /** Tony's own crosshair passes through his suits (his picks and orders aim past them). */
   @Override
   public boolean isPickable() {
      if (this.level().isClientSide && dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.isLocalOwner(this.ownerId())) {
         return false;
      }

      return this.phase() != LegionPhase.LEAVE && super.isPickable();
   }

   // --- damage: into the roster ---

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.isRemoved()) {
         return false;
      }

      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         // /kill, the void: the mark is lost from the world and goes back to Veronica as it is.
         this.release();
         this.discard();
         return true;
      }

      LegionPhase phase = this.phase();
      if (phase == LegionPhase.LEAVE || phase == LegionPhase.CRASH || this.isInvulnerableTo(source)
         || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypeTags.IS_FALL)
         || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.CRAMMING)) {
         return false;
      }

      Entity attacker = source.getEntity();
      ServerPlayer owner = this.owner();
      if (owner == null || attacker == owner || LegionAllies.spares(attacker, this) || LegionAllies.spares(source.getDirectEntity(), this)) {
         return false;
      }

      // A player or another player's Legion (direct hit, missile, splash): the server PvP and team rules of the two owners decide.
      if (!LegionAllies.playerDamageAllowed(owner, attacker, source.getDirectEntity())) {
         return false;
      }

      IronManState state = IronManState.of(owner);
      if (state == null) {
         return false;
      }

      float armored = CombatRules.getDamageAfterAbsorb(Math.max(0.0F, amount), (float)LegionRules.SHELL_ARMOR, 4.0F);
      MarkDamage.Result result = state.roster.damageLegion(this.mark(), armored);
      float max = state.roster.maxDurability(this.mark());
      this.entityData.set(FRACTION, LegionRules.fraction(result.durability(), max));
      this.setFlag(FLAG_CRITICAL, result.durability() / Math.max(1.0E-3F, max) < LegionRules.RETURN_FRACTION);
      if (this.tickCount - this.lastHitMove > 10 && phase == LegionPhase.ACTIVE) {
         this.lastHitMove = this.tickCount;
         this.playMove(LegionMove.HIT);
      }

      this.hurtTime = this.hurtDuration = 6;
      if (this.brain != null) {
         this.brain.onHurt(owner, attacker instanceof LivingEntity living ? living : null, armored, result.broke());
      }

      if (attacker instanceof LivingEntity living && living != this) {
         this.setLastHurtByMob(living);
      }

      return true;
   }

   /** The durability fraction was changed outside of a hit (call refresh). */
   public void syncFraction(float fraction) {
      this.entityData.set(FRACTION, fraction);
      this.setFlag(FLAG_CRITICAL, fraction < LegionRules.RETURN_FRACTION);
   }

   @Override
   public void die(DamageSource source) {
      // Never dies the vanilla way: the brain crashes the suit when the roster says it broke.
   }

   @Override
   public void kill() {
      this.release();
      this.discard();
   }

   /**
    * The body leaves the world while the roster still has the mark in the
    * Legion: it goes back to Veronica with the durability it has (no heal).
    */
   public void release() {
      if (this.released || this.level().isClientSide) {
         return;
      }

      this.released = true;
      ServerPlayer owner = this.owner();
      IronManState state = owner == null ? null : IronManState.of(owner);
      if (state != null && state.roster.location(this.mark()) == MarkLocation.LEGION) {
         state.roster.move(this.mark(), MarkLocation.LEGION, MarkLocation.STORED);
      }

      if (owner != null) {
         IronManLegion.forget(owner, this);
      }
   }

   /** The roster was already updated by the caller (returned, crashed, dismissed). */
   public void markReleased() {
      this.released = true;
   }

   @Override
   public void onRemovedFromWorld() {
      super.onRemovedFromWorld();
      if (!this.level().isClientSide) {
         this.release();
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
   protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
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

   @Override
   public boolean canBeLeashed(Player player) {
      return false;
   }

   @Override
   public boolean canChangeDimensions() {
      return false;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      if (other == null) {
         return false;
      }

      if (other instanceof LegionSuitEntity suit) {
         return this.ownerUuid != null ? this.ownerUuid.equals(suit.ownerUuid) : suit.ownerId() == this.ownerId();
      }

      if (this.ownerUuid != null && this.ownerUuid.equals(other.getUUID()) || this.level().isClientSide && other.getId() == this.ownerId()) {
         return true;
      }

      return super.isAlliedTo(other);
   }

   // --- no vanilla sounds: the Legion has its own (IronManLegionSounds) ---

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
   protected float getSoundVolume() {
      return 0.0F;
   }

   /** Own one-shot at the suit, heard by everyone around. */
   public void sound(SoundEvent sound, float volume, float pitch) {
      this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
   }

   // --- body points for the weapons ---

   public Vec3 bodyForward() {
      return LegionSteering.forward(this.yBodyRot);
   }

   /** Forearm muzzle in front of the shoulder. */
   public Vec3 hand(boolean right) {
      Vec3 fwd = this.bodyForward();
      Vec3 side = LegionSteering.right(fwd);
      return this.position().add(0.0, 1.32, 0.0).add(side.scale(right ? 0.38 : -0.38)).add(fwd.scale(0.42));
   }

   /** Arc reactor. */
   public Vec3 chest() {
      return this.position().add(0.0, 1.28, 0.0).add(this.bodyForward().scale(0.3));
   }

   /** Shoulder launchers (missiles, War Machine gun). */
   public Vec3 shoulder(boolean right) {
      Vec3 fwd = this.bodyForward();
      Vec3 side = LegionSteering.right(fwd);
      return this.position().add(0.0, 1.62, 0.0).add(side.scale(right ? 0.3 : -0.3)).subtract(fwd.scale(0.05));
   }

   /** Look direction of the weapons: the aim target if there is one, else the head. */
   public Vec3 aimDirection() {
      Entity target = this.aimId() >= 0 ? this.level().getEntity(this.aimId()) : null;
      if (target != null) {
         Vec3 to = target.getBoundingBox().getCenter().subtract(this.getEyePosition());
         if (to.lengthSqr() > 1.0E-4) {
            return to.normalize();
         }
      }

      return this.getLookAngle();
   }

   @Override
   public SignatureActor signatureActor() {
      return this.actor;
   }

   /** The suit as a signature shooter: the stage 4 kernels with the Legion's ally rule. */
   private final class Actor implements SignatureActor {
      @Override
      public LivingEntity body() {
         return LegionSuitEntity.this;
      }

      @Override
      public ServerLevel level() {
         return (ServerLevel)LegionSuitEntity.this.level();
      }

      @Override
      public Vec3 eye() {
         return LegionSuitEntity.this.getEyePosition();
      }

      @Override
      public Vec3 look() {
         return LegionSuitEntity.this.aimDirection();
      }

      @Override
      public Vec3 hand(boolean right) {
         return LegionSuitEntity.this.hand(right);
      }

      @Override
      public Vec3 chest() {
         return LegionSuitEntity.this.chest();
      }

      @Override
      public DamageSource attack() {
         return LegionSuitEntity.this.damageSources().mobAttack(LegionSuitEntity.this);
      }

      @Override
      public boolean canHit(Entity target) {
         return target != LegionSuitEntity.this && target.isAlive() && LegionAllies.mayHit(LegionSuitEntity.this, target);
      }

      @Override
      public boolean mayStrike(LivingEntity target) {
         return LegionAllies.mayHit(LegionSuitEntity.this, target);
      }

      @Override
      public void sound(SoundEvent sound, float volume, float pitch) {
         LegionSuitEntity.this.sound(sound, volume, pitch);
      }

      @Override
      public void fistReturned() {
         LegionSuitEntity.this.setFlag(FLAG_FIST_AWAY, false);
      }
   }
}

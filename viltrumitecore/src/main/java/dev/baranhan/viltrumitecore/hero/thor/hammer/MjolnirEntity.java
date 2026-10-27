package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.thor.ThorTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * The physical body of the one Mjölnir while its chunk is loaded (§3.4):
 * own model, real position and collision, never pushed, never hurt, not
 * moved by explosions, not destroyed by lava; not an item. Never saved
 * (noSave type): MjolnirWorld respawns it from the record with the same
 * UUID, and any body that is not the saved hammer removes itself. Server
 * physics per phase; clients only draw it (MjolnirRenderer) and hear it.
 */
public class MjolnirEntity extends Entity {
   private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> LYING = SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> EMBEDDED = SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> CHARGED = SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.OPTIONAL_UUID);
   private static final EntityDataAccessor<CompoundTag> LIFTERS = SynchedEntityData.defineId(MjolnirEntity.class, EntityDataSerializers.COMPOUND_TAG);
   private static final MjolnirPhase[] PHASES = MjolnirPhase.values();

   /** Entities hit by the running throw / return (never twice, №33). */
   private final Set<Integer> hit = new HashSet<>();
   @Nullable
   private LivingEntity target;
   private int flightTicks;
   private int broken;
   private long lastBreakSound;
   private boolean ripPending;
   private boolean clientSound;

   public MjolnirEntity(EntityType<? extends MjolnirEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.blocksBuilding = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(PHASE, MjolnirPhase.RESTING.ordinal());
      this.entityData.define(LYING, false);
      this.entityData.define(EMBEDDED, false);
      this.entityData.define(CHARGED, false);
      this.entityData.define(OWNER, Optional.empty());
      this.entityData.define(LIFTERS, new CompoundTag());
   }

   // --- client reads ---

   public MjolnirPhase phase() {
      int ordinal = this.entityData.get(PHASE);
      return ordinal >= 0 && ordinal < PHASES.length ? PHASES[ordinal] : MjolnirPhase.RESTING;
   }

   public boolean lying() {
      return this.entityData.get(LYING);
   }

   public boolean embedded() {
      return this.entityData.get(EMBEDDED);
   }

   public boolean charged() {
      return this.entityData.get(CHARGED);
   }

   @Nullable
   public UUID owner() {
      return this.entityData.get(OWNER).orElse(null);
   }

   /** Entity ids of players trying to lift it right now (their pose, MjolnirLift). */
   public int[] lifters() {
      return this.entityData.get(LIFTERS).getIntArray("L");
   }

   void syncFrom(MjolnirRecord record) {
      if (this.entityData.get(PHASE) != record.phase.ordinal()) {
         this.entityData.set(PHASE, record.phase.ordinal());
      }

      if (this.entityData.get(LYING) != record.lying) {
         this.entityData.set(LYING, record.lying);
      }

      if (this.entityData.get(EMBEDDED) != record.embedded) {
         this.entityData.set(EMBEDDED, record.embedded);
      }

      if (this.entityData.get(CHARGED) != record.charged) {
         this.entityData.set(CHARGED, record.charged);
      }

      Optional<UUID> owner = Optional.ofNullable(record.activeOwner);
      if (!this.entityData.get(OWNER).equals(owner)) {
         this.entityData.set(OWNER, owner);
      }
   }

   private void syncLifters(int[] ids) {
      int[] now = this.lifters();
      if (!java.util.Arrays.equals(now, ids)) {
         CompoundTag tag = new CompoundTag();
         tag.putIntArray("L", ids);
         this.entityData.set(LIFTERS, tag);
      }
   }

   // --- immunity (§3.4) ---

   @Override
   public boolean hurt(DamageSource source, float amount) {
      return false;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return true;
   }

   @Override
   public boolean ignoreExplosion() {
      return true;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public void push(Entity entity) {
   }

   @Override
   public void push(double x, double y, double z) {
   }

   @Override
   public boolean isPushedByFluid() {
      return false;
   }

   @Override
   public PushReaction getPistonPushReaction() {
      return PushReaction.IGNORE;
   }

   @Override
   public boolean fireImmune() {
      return true;
   }

   @Override
   public boolean displayFireAnimation() {
      return false;
   }

   /** Solid only while it rests: bodies stand on it and cannot walk through it. */
   @Override
   public boolean canBeCollidedWith() {
      return this.phase() == MjolnirPhase.RESTING;
   }

   @Override
   public boolean isPickable() {
      return !this.isRemoved();
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 256.0 * 256.0;
   }

   @Override
   protected void onBelowWorld() {
      if (!this.level().isClientSide) {
         MinecraftServer server = this.getServer();
         if (server != null) {
            MjolnirWorld.voidFall(server, MjolnirWorld.record(server));
         }
      }
   }

   // --- RMB on the model: the active Thor picks it up, anyone else tries to lift it (§3.4) ---

   @Override
   public InteractionResult interact(Player player, InteractionHand hand) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      }

      if (this.level().isClientSide) {
         return this.phase() == MjolnirPhase.RESTING ? InteractionResult.SUCCESS : InteractionResult.PASS;
      }

      if (!(player instanceof ServerPlayer serverPlayer) || this.phase() != MjolnirPhase.RESTING) {
         return InteractionResult.PASS;
      }

      if (MjolnirWorld.isOwner(serverPlayer)) {
         return MjolnirWorld.pickUp(serverPlayer, this) ? InteractionResult.CONSUME : InteractionResult.PASS;
      }

      return MjolnirLift.begin(serverPlayer, this);
   }

   // --- flight starts (MjolnirWorld) ---

   void beginThrow(ServerPlayer thor, Vec3 origin, Vec3 look, boolean charged) {
      this.hit.clear();
      this.flightTicks = 0;
      this.broken = 0;
      this.target = this.aimTarget(thor, origin, look);
      this.entityData.set(CHARGED, charged);
   }

   void beginReturn(boolean ripped) {
      this.hit.clear();
      this.target = null;
      this.flightTicks = 0;
      this.ripPending = ripped;
   }

   // --- tick ---

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.clientTick();
         return;
      }

      MinecraftServer server = this.getServer();
      if (server == null || !MjolnirWorld.owns(this)) {
         this.discard();
         return;
      }

      MjolnirRecord record = MjolnirWorld.record(server);
      this.syncFrom(record);
      this.syncLifters(MjolnirLift.lifterIds(this));
      ServerLevel level = (ServerLevel)this.level();
      switch (record.phase) {
         case WORLD_FALLING -> this.firstFall(server, record, level);
         case THROWN -> this.thrown(server, record, level);
         case RETURNING -> this.returning(server, record, level);
         case FALLING -> this.falling(server, record, level);
         case RESTING -> this.resting(server, record, level);
         case DEATH_IMPACT -> this.deathImpact(server, record, level);
         default -> {
         }
      }

      if (!this.isRemoved() && this.getY() < level.getMinBuildHeight() - MjolnirTuning.VOID_MARGIN) {
         MjolnirWorld.voidFall(server, record);
      }
   }

   private void move(Vec3 to) {
      Vec3 from = this.position();
      Vec3 delta = to.subtract(from);
      this.setPos(to);
      this.setDeltaMovement(delta);
      MinecraftServer server = this.getServer();
      if (server != null) {
         MjolnirWorld.track(this);
      }
   }

   /** The first appearance: straight down from the sky with thunder and an electric trail (§3.3). */
   private void firstFall(MinecraftServer server, MjolnirRecord record, ServerLevel level) {
      Vec3 from = this.position();
      Vec3 to = from.add(0.0, -MjolnirTuning.FALL_SPEED, 0.0);
      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 12, 0.3, 1.2, 0.3, 0.2);
      level.sendParticles(ParticleTypes.END_ROD, from.x, from.y + 1.0, from.z, 3, 0.1, 1.5, 0.1, 0.01);
      if (this.tickCount % MjolnirTuning.FALL_THUNDER_GAP == 1) {
         level.playSound(null, from.x, from.y, from.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0F, 0.6F);
      }

      LivingEntity struck = this.firstVictim(from, to);
      BlockHitResult ground = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      if (ground.getType() == HitResult.Type.MISS && struck == null) {
         this.setXRot(90.0F);
         this.move(to);
         return;
      }

      Vec3 at = ground.getType() == HitResult.Type.MISS ? to : ground.getLocation();
      this.setPos(at);
      Vec3 rest = MjolnirImpact.firstFall(level, this, at);
      this.setYRot(this.random.nextFloat() * 360.0F);
      this.setXRot(0.0F);
      this.setPos(rest);
      this.setDeltaMovement(Vec3.ZERO);
      MjolnirWorld.settle(server, record, level, rest);
      this.syncFrom(record);
   }

   @Nullable
   private LivingEntity firstVictim(Vec3 from, Vec3 to) {
      AABB sweep = this.getBoundingBox().expandTowards(to.subtract(from)).inflate(0.5);
      for (LivingEntity living : this.level().getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && !e.isSpectator())) {
         return living;
      }

      return null;
   }

   /** Autonomous flight after a throw (§4). */
   private void thrown(MinecraftServer server, MjolnirRecord record, ServerLevel level) {
      this.flightTicks++;
      ServerPlayer thrower = MjolnirWorld.owner(server, record);
      Vec3 origin = new Vec3(record.originX, record.originY, record.originZ);
      Vec3 velocity = this.getDeltaMovement();
      double speed = record.charged ? MjolnirTuning.THROW_SPEED_CHARGED : MjolnirTuning.THROW_SPEED;
      if (this.target != null && (!this.target.isAlive() || !MjolnirTargeting.inRange(origin, this.target.getBoundingBox().getCenter()))) {
         this.target = null;
      }

      if (this.target != null) {
         // Homing only while a target lives inside the 128-block range; never steered by the player.
         velocity = MjolnirFlight.homing(velocity, this.target.getBoundingBox().getCenter().subtract(this.position()), speed);
      } else {
         velocity = MjolnirFlight.ballistic(velocity);
      }

      Vec3 from = this.position();
      Vec3 to = from.add(velocity);
      this.spinTrail(level, from, record.charged);
      boolean pierced = false;
      for (LivingEntity victim : this.sweep(from, to, thrower)) {
         float damage = record.charged ? MjolnirTuning.THROW_DAMAGE_CHARGED : MjolnirTuning.THROW_DAMAGE;
         this.strike(victim, thrower, damage, velocity);
         pierced = true;
      }

      if (pierced && thrower != null) {
         this.target = this.nextTarget(thrower, origin);
      }

      BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      int brokenNow = 0;
      while (block.getType() != HitResult.Type.MISS && record.charged && this.broken < MjolnirTuning.CHARGED_BREAK_TOTAL
         && brokenNow < MjolnirTuning.CHARGED_BREAK_PER_TICK) {
         // №11: a charged throw breaks the blocks it is allowed to; a protected one stops it.
         if (!this.smash(level, block.getBlockPos())) {
            break;
         }

         brokenNow++;
         velocity = velocity.scale(MjolnirTuning.CHARGED_BREAK_DRAG);
         block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      }

      if (block.getType() != HitResult.Type.MISS) {
         // A normal throw sticks in the block (head first).
         Vec3 dir = velocity.lengthSqr() < 1.0E-6 ? new Vec3(0.0, -1.0, 0.0) : velocity.normalize();
         Vec3 at = block.getLocation().subtract(dir.scale(0.25));
         this.face(dir);
         this.setPos(at);
         this.setDeltaMovement(Vec3.ZERO);
         BlockState state = level.getBlockState(block.getBlockPos());
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 24, 0.2, 0.2, 0.2, 0.15);
         level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.6F, 0.6F);
         level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.6F);
         MjolnirWorld.embed(server, record, this);
         this.syncFrom(record);
         return;
      }

      this.face(velocity);
      this.setPos(to);
      this.setDeltaMovement(velocity);
      MjolnirWorld.track(this);
      if (this.flightTicks > MjolnirTuning.THROW_MAX_TICKS) {
         MjolnirWorld.go(server, record, MjolnirPhase.FALLING);
      }
   }

   /** Straight back to the palm: breaks normal blocks, passes protected ones, hurts everyone but the owner (§5.1). */
   private void returning(MinecraftServer server, MjolnirRecord record, ServerLevel level) {
      ServerPlayer owner = MjolnirWorld.owner(server, record);
      if (owner == null || owner.level() != level) {
         return;
      }

      Vec3 from = this.position();
      if (this.ripPending) {
         // №158: out of the block with a jerk, particles and its own sound.
         this.ripPending = false;
         BlockPos in = BlockPos.containing(from);
         BlockState state = level.getBlockState(in);
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state.isAir() ? level.getBlockState(in.below()) : state), from.x, from.y,
            from.z, 30, 0.3, 0.3, 0.3, 0.25);
         level.playSound(null, from.x, from.y, from.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0F, 0.6F);
         this.move(from.add(0.0, MjolnirTuning.RIP_JERK, 0.0));
         return;
      }

      Vec3 palm = MjolnirFlight.palm(owner.getEyePosition(), owner.getYRot());
      double distance = from.distanceTo(palm);
      double speed = MjolnirFlight.returnSpeed(distance);
      if (MjolnirFlight.reaches(distance, speed)) {
         MjolnirWorld.caught(server, record, owner, this);
         return;
      }

      Vec3 to = MjolnirFlight.step(from, palm, speed);
      this.breakAlong(level, from, to, MjolnirTuning.RETURN_BREAK_PER_TICK);
      for (LivingEntity victim : this.sweep(from, to, owner)) {
         this.strike(victim, owner, MjolnirTuning.RETURN_DAMAGE, to.subtract(from));
      }

      this.spinTrail(level, from, false);
      this.face(to.subtract(from));
      this.move(to);
   }

   /** Lost support / released from the hand: heavy fall, heavy landing. */
   private void falling(MinecraftServer server, MjolnirRecord record, ServerLevel level) {
      Vec3 velocity = MjolnirFlight.falling(this.getDeltaMovement());
      Vec3 from = this.position();
      Vec3 to = from.add(velocity);
      BlockHitResult ground = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      if (ground.getType() == HitResult.Type.MISS) {
         this.setXRot(90.0F);
         this.move(to);
         return;
      }

      Vec3 at = ground.getLocation();
      double impact = -velocity.y;
      BlockState state = level.getBlockState(ground.getBlockPos());
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 20 + (int)(impact * 20.0), 0.4, 0.1, 0.4, 0.15);
      level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8F, 0.5F);
      if (impact > 1.0) {
         MjolnirFx.shockwave(level, at, (float)Math.min(1.0, impact / 2.5), state, 3.0F);
      }

      this.setYRot(this.random.nextFloat() * 360.0F);
      this.setXRot(0.0F);
      Vec3 rest = ground.getDirection() == net.minecraft.core.Direction.UP ? at : at.add(0.0, -0.3, 0.0);
      this.setPos(rest);
      this.setDeltaMovement(Vec3.ZERO);
      MjolnirWorld.settle(server, record, level, rest);
      this.syncFrom(record);
   }

   /** Lies / sticks; falls when the support is gone (an explosion never moves it, a missing floor does). */
   private void resting(MinecraftServer server, MjolnirRecord record, ServerLevel level) {
      if (this.tickCount % 5 != 0) {
         return;
      }

      AABB touch = this.getBoundingBox().inflate(0.08);
      if (level.noCollision(this, touch)) {
         record.embedded = false;
         record.lying = false;
         record.velocity(0.0, 0.0, 0.0);
         this.setDeltaMovement(Vec3.ZERO);
         MjolnirWorld.go(server, record, MjolnirPhase.FALLING);
      }
   }

   /** №169: full force into the death point, destruction stronger than the first fall. */
   private void deathImpact(MinecraftServer server, MjolnirRecord record, ServerLevel level) {
      if (record.death == null || record.deathDimension == null || !record.deathDimension.equals(MjolnirWorld.dim(level))) {
         return;
      }

      Vec3 from = this.position();
      Vec3 point = new Vec3(record.death[0], record.death[1], record.death[2]);
      double distance = from.distanceTo(point);
      double speed = MjolnirTuning.RETURN_MAX_SPEED;
      if (distance <= speed) {
         this.setPos(point);
         Vec3 rest = MjolnirImpact.deathStrike(level, point, this, record.activeOwner);
         this.setYRot(this.random.nextFloat() * 360.0F);
         this.setXRot(0.0F);
         this.setPos(rest);
         this.setDeltaMovement(Vec3.ZERO);
         MjolnirWorld.settle(server, record, level, rest);
         this.syncFrom(record);
         return;
      }

      Vec3 to = MjolnirFlight.step(from, point, speed);
      this.breakAlong(level, from, to, MjolnirTuning.RETURN_BREAK_PER_TICK);
      for (LivingEntity victim : this.sweep(from, to, null)) {
         if (!victim.getUUID().equals(record.activeOwner)) {
            this.strike(victim, null, MjolnirTuning.RETURN_DAMAGE, to.subtract(from));
         }
      }

      this.spinTrail(level, from, true);
      this.face(to.subtract(from));
      this.move(to);
   }

   // --- helpers ---

   private void face(Vec3 direction) {
      if (direction.lengthSqr() < 1.0E-8) {
         return;
      }

      Vec3 dir = direction.normalize();
      this.setYRot((float)(Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
      this.setXRot((float)(-Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG));
   }

   private void spinTrail(ServerLevel level, Vec3 at, boolean strong) {
      level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, strong ? 6 : 3, 0.15, 0.15, 0.15, 0.05);
   }

   /** Living bodies the segment passes through this tick, not hit yet by this flight; never the owner. */
   private List<LivingEntity> sweep(Vec3 from, Vec3 to, @Nullable ServerPlayer owner) {
      List<LivingEntity> out = new ArrayList<>();
      AABB box = this.getBoundingBox().expandTowards(to.subtract(from)).inflate(0.6);
      for (LivingEntity living : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
         if (living == owner || this.hit.contains(living.getId())) {
            continue;
         }

         AABB body = living.getBoundingBox().inflate(0.4);
         if (body.contains(from) || body.clip(from, to).isPresent()) {
            out.add(living);
         }
      }

      return out;
   }

   private void strike(LivingEntity victim, @Nullable Entity attacker, float damage, Vec3 direction) {
      this.hit.add(victim.getId());
      if (victim.hurt(this.damageSources().thrown(this, attacker), damage) && HeroRegistry.allowsImpulse(victim) && direction.lengthSqr() > 1.0E-6) {
         Vec3 push = direction.normalize().scale(0.8);
         victim.push(push.x, 0.25, push.z);
         victim.hurtMarked = true;
      }

      Vec3 at = victim.getBoundingBox().getCenter();
      ((ServerLevel)this.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.3);
      this.level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
   }

   /** One break: allowed blocks only (HeroDestruction), one universal heavy sound at most every few ticks (№161). */
   private boolean smash(ServerLevel level, BlockPos pos) {
      if (!HeroDestruction.canDestroy(level, pos) || !HeroDestruction.destroyBlock(level, pos)) {
         return false;
      }

      this.broken++;
      long now = level.getGameTime();
      if (now - this.lastBreakSound >= MjolnirTuning.BREAK_SOUND_GAP) {
         this.lastBreakSound = now;
         level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.7F, 1.5F);
      }

      return true;
   }

   /** Return path: every collidable block on the straight segment that may break, breaks; protected ones are passed through. */
   private void breakAlong(ServerLevel level, Vec3 from, Vec3 to, int budget) {
      Vec3 delta = to.subtract(from);
      int steps = Math.max(1, (int)Math.ceil(delta.length() / 0.4));
      BlockPos last = null;
      int broken = 0;
      for (int i = 1; i <= steps && broken < budget; i++) {
         BlockPos pos = BlockPos.containing(from.add(delta.scale((double)i / steps)));
         if (pos.equals(last)) {
            continue;
         }

         last = pos;
         if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && this.smash(level, pos)) {
            broken++;
         }
      }
   }

   @Nullable
   private LivingEntity aimTarget(ServerPlayer thor, Vec3 origin, Vec3 look) {
      List<LivingEntity> enemies = this.enemies(thor, origin, origin, MjolnirTuning.THROW_RANGE);
      MjolnirTargeting.Candidate best = MjolnirTargeting.aim(origin, look, candidates(enemies));
      return best == null ? null : byId(enemies, best.id());
   }

   @Nullable
   private LivingEntity nextTarget(ServerPlayer thor, Vec3 origin) {
      List<LivingEntity> enemies = this.enemies(thor, origin, this.position(), MjolnirTuning.NEXT_TARGET_RADIUS);
      MjolnirTargeting.Candidate next = MjolnirTargeting.next(this.position(), origin, this.hit, candidates(enemies));
      return next == null ? null : byId(enemies, next.id());
   }

   /** Visible enemies of the thrower around a point (line of sight from that point). */
   private List<LivingEntity> enemies(ServerPlayer thor, Vec3 origin, Vec3 around, double radius) {
      AABB box = new AABB(around, around).inflate(radius);
      return this.level().getEntitiesOfClass(LivingEntity.class, box, e -> ThorTargets.isEnemy(thor, e)
         && MjolnirTargeting.inRange(origin, e.getBoundingBox().getCenter())
         && this.level().clip(new ClipContext(around, e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
            .getType() == HitResult.Type.MISS);
   }

   private static List<MjolnirTargeting.Candidate> candidates(List<LivingEntity> enemies) {
      List<MjolnirTargeting.Candidate> out = new ArrayList<>(enemies.size());
      for (LivingEntity enemy : enemies) {
         out.add(new MjolnirTargeting.Candidate(enemy.getId(), enemy.getBoundingBox().getCenter()));
      }

      return out;
   }

   @Nullable
   private static LivingEntity byId(List<LivingEntity> enemies, int id) {
      for (LivingEntity enemy : enemies) {
         if (enemy.getId() == id) {
            return enemy;
         }
      }

      return null;
   }

   // --- client ---

   private void clientTick() {
      MjolnirPhase phase = this.phase();
      if (phase != MjolnirPhase.RESTING && phase != MjolnirPhase.IN_HAND) {
         Vec3 at = this.position();
         this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + (this.random.nextDouble() - 0.5) * 0.4, at.y + (this.random.nextDouble() - 0.5) * 0.4,
            at.z + (this.random.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
      }

      if (!this.clientSound) {
         this.clientSound = true;
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.thor.MjolnirClient.flightSound(this));
      }
   }

   // --- never saved: the record is the truth ---

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return new ClientboundAddEntityPacket(this);
   }
}

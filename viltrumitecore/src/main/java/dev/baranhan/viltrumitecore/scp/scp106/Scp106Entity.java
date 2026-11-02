package dev.baranhan.viltrumitecore.scp.scp106;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.Scp106S2CPacket;
import dev.baranhan.viltrumitecore.scp.scp106.block.PocketBlocks;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Door;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Room;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketPainter;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SCP-106, the Old Man (design §2). No goals: one state machine. It walks slowly towards its prey, stops to stare,
 * sinks into the floor and rises again behind it, passes through walls and doors up to {@link Scp106Rules#PHASE_THICKNESS}
 * blocks thick (thicker ones make it sink and re-emerge), leaves a temporary corrosion film where it touches, and
 * takes the one player it caught into the pocket dimension. It never dies: enough damage only makes it retreat.
 * Inside the pocket dimension it is a hunter bound to one player: a catch there drags and hurts, never kills.
 */
public class Scp106Entity extends PathfinderMob {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/scp106");
   public static final int FLAG_POCKET = 1;
   public static final int FLAG_NATURAL = 2;
   private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Scp106Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Scp106Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> DATA_SWIPE = SynchedEntityData.defineId(Scp106Entity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DATA_HELD = SynchedEntityData.defineId(Scp106Entity.class, EntityDataSerializers.INT);
   private static final Set<Scp106Entity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
   /** Stride of the walk cycle in blocks (one cycle = two steps). */
   public static final double STRIDE = 1.7;

   // client animation state
   public Scp106State clientState = Scp106State.EMERGE;
   public Scp106State clientPrevState = Scp106State.EMERGE;
   public int clientStateStart = -1000;
   public int clientSwipeStart = -1000;
   public float clientMove;
   public float clientMoveO;
   public double clientGait;
   public double clientGaitO;

   // server state
   @Nullable
   private UUID prey;
   private int stateTicks;
   private int stateLength;
   private int huntTicks;
   private int lostTicks;
   private int stuckTicks;
   private int submergeTries;
   private boolean leaving;
   private long lastSeen = Long.MIN_VALUE;
   private boolean checkedLoad;
   private Vec3 lastPos = Vec3.ZERO;
   @Nullable
   private Vec3 phaseDir;
   @Nullable
   private Vec3 phaseTarget;
   @Nullable
   private Vec3 captureAt;
   private float pace = 1.0F;
   private int nextBreath = 60;
   private int swipeCooldown;
   private final long[] hitTicks = new long[16];
   private final float[] hitDamage = new float[16];
   private int hitIndex;

   public Scp106Entity(EntityType<? extends Scp106Entity> type, Level level) {
      super(type, level);
      this.setMaxUpStep(1.1F);
      this.xpReward = 0;
      this.setPersistenceRequired();
      this.getNavigation().setCanFloat(true);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, Scp106Rules.MAX_HEALTH)
         .add(Attributes.MOVEMENT_SPEED, Scp106Rules.SPEED)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.ARMOR, 6.0)
         .add(Attributes.ATTACK_DAMAGE, 5.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(DATA_STATE, (byte)Scp106State.EMERGE.ordinal());
      this.entityData.define(DATA_FLAGS, (byte)0);
      this.entityData.define(DATA_SWIPE, 0);
      this.entityData.define(DATA_HELD, -1);
   }

   @Override
   protected void registerGoals() {
      // the state machine below drives everything
   }

   // ------------------------------------------------------------------ state

   public Scp106State state() {
      return Scp106State.byId(this.entityData.get(DATA_STATE));
   }

   public boolean flag(int bit) {
      return (this.entityData.get(DATA_FLAGS) & bit) != 0;
   }

   private void setFlag(int bit, boolean on) {
      int f = this.entityData.get(DATA_FLAGS);
      int next = on ? f | bit : f & ~bit;
      if (next != f) {
         this.entityData.set(DATA_FLAGS, (byte)next);
      }
   }

   public boolean pocketHunter() {
      return this.flag(FLAG_POCKET);
   }

   public boolean natural() {
      return this.flag(FLAG_NATURAL);
   }

   public void setNatural(boolean on) {
      this.setFlag(FLAG_NATURAL, on);
   }

   /** Network id of the player held in the capture animation (-1: none), for the client-side pull. */
   public int heldId() {
      return this.entityData.get(DATA_HELD);
   }

   @Nullable
   public UUID preyId() {
      return this.prey;
   }

   public void setPrey(@Nullable UUID id) {
      this.prey = id;
   }

   public int stateTicks() {
      return this.stateTicks;
   }

   public int huntTicks() {
      return this.huntTicks;
   }

   public boolean leaving() {
      return this.leaving;
   }

   public void setState(Scp106State next) {
      Scp106State prev = this.state();
      if (prev != next && !this.level().isClientSide) {
         LOG.debug("SCP-106 #{} {} -> {} at {} {} {}", this.getId(), prev, next, this.getBlockX(), this.getBlockY(), this.getBlockZ());
      }
      this.stateTicks = 0;
      this.entityData.set(DATA_STATE, (byte)next.ordinal());
      boolean ghost = next == Scp106State.SUBMERGED || next == Scp106State.PHASE;
      this.noPhysics = ghost;
      this.setNoGravity(ghost);
      this.setInvisible(next == Scp106State.SUBMERGED);
      if (next != Scp106State.CAPTURE) {
         this.entityData.set(DATA_HELD, -1);
         this.captureAt = null;
      }
      this.getNavigation().stop();
      this.setDeltaMovement(Vec3.ZERO);
      switch (next) {
         case EMERGE -> this.stateLength = Scp106Rules.EMERGE_TICKS;
         case SINK, RETREAT -> this.stateLength = Scp106Rules.SINK_TICKS;
         case STARE -> this.stateLength = Scp106Rules.stareTicks(this.random.nextDouble());
         case SUBMERGED -> this.stateLength = 30 + this.random.nextInt(70);
         case CAPTURE -> this.stateLength = Scp106Rules.CAPTURE_TICKS;
         default -> this.stateLength = 0;
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (!this.level().isClientSide) {
         return;
      }
      if (DATA_STATE.equals(key)) {
         Scp106State s = this.state();
         if (s != this.clientState) {
            boolean fresh = this.tickCount == 0;
            this.clientPrevState = fresh ? s : this.clientState;
            this.clientState = s;
            // a creature that comes into view mid-emerge or mid-walk does not restart its animation
            this.clientStateStart = fresh && s != Scp106State.EMERGE ? -1000 : this.tickCount;
         }
      } else if (DATA_SWIPE.equals(key)) {
         this.clientSwipeStart = this.tickCount;
      }
   }

   // ------------------------------------------------------------------ lifecycle

   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide) {
         LOADED.add(this);
      }
   }

   @Override
   public void onRemovedFromWorld() {
      super.onRemovedFromWorld();
      if (!this.level().isClientSide) {
         LOADED.remove(this);
      }
   }

   /** Server instances currently loaded in any level. */
   public static List<Scp106Entity> loaded() {
      List<Scp106Entity> out = new ArrayList<>();
      for (Scp106Entity e : LOADED) {
         if (e != null && !e.isRemoved()) {
            out.add(e);
         }
      }
      return out;
   }

   @Override
   public boolean removeWhenFarAway(double distanceSq) {
      return false;
   }

   @Override
   public boolean requiresCustomPersistence() {
      return true;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void doPush(Entity other) {
   }

   @Override
   public boolean isPickable() {
      return this.state() != Scp106State.SUBMERGED && super.isPickable();
   }

   @Override
   public boolean canBeLeashed(Player player) {
      return false;
   }

   @Override
   public void knockback(double strength, double x, double z) {
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return false;
      }
      Scp106State s = this.state();
      return s == Scp106State.SUBMERGED || s == Scp106State.PHASE || s == Scp106State.RETREAT || super.isInvulnerableTo(source);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return super.hurt(source, amount);
      }
      if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING)
         || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.CRAMMING)
         || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.WITHER) || source.is(DamageTypeTags.IS_FIRE)) {
         return false;
      }
      boolean hit = super.hurt(source, amount);
      if (hit && !this.level().isClientSide) {
         this.hitTicks[this.hitIndex] = this.level().getGameTime();
         this.hitDamage[this.hitIndex] = amount;
         this.hitIndex = (this.hitIndex + 1) % this.hitTicks.length;
         if (Scp106Rules.shouldRetreat(this.level().getGameTime(), this.hitTicks, this.hitDamage, this.hitTicks.length)) {
            this.retreat();
         } else if (this.state() == Scp106State.STARE) {
            this.setState(Scp106State.STALK);
         }
      }
      return hit;
   }

   /** Ordinary damage never takes it below one heart point: it retreats instead (only /kill and the void kill it). */
   @Override
   protected void actuallyHurt(DamageSource source, float amount) {
      if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         amount = Math.max(0.0F, Math.min(amount, this.getHealth() - 1.0F));
      }
      super.actuallyHurt(source, amount);
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distanceSq) {
      double r = 128.0 * getViewScale();
      return distanceSq < r * r;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return null;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      if (this.state() == Scp106State.STALK) {
         this.playSound(Scp106Sounds.STEP.get(), 0.55F, 0.85F + this.random.nextFloat() * 0.2F);
      }
   }

   // ------------------------------------------------------------------ ticking

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         double dx = this.getX() - this.xo;
         double dz = this.getZ() - this.zo;
         float speed = (float)Math.sqrt(dx * dx + dz * dz);
         this.clientMoveO = this.clientMove;
         this.clientMove += (speed - this.clientMove) * 0.4F;
         this.clientGaitO = this.clientGait;
         this.clientGait += speed / STRIDE;
      }
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (!this.level().isClientSide && this.state() == Scp106State.PHASE && this.phaseDir != null) {
         this.setDeltaMovement(this.phaseDir.scale(Scp106Rules.PHASE_SPEED));
      }
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel)this.level();
      long now = level.getGameTime();
      if (!this.checkedLoad) {
         this.checkedLoad = true;
         // a saved hunter that comes back long after (chunk reloaded, server restarted) has no business left here
         if (this.pocketHunter() || this.lastSeen != Long.MIN_VALUE && now - this.lastSeen > 1_200) {
            LOG.debug("SCP-106 #{} reloaded stale ({} ticks), discarded", this.getId(), now - this.lastSeen);
            this.discard();
            return;
         }
         Scp106State s = this.state();
         if (s == Scp106State.PHASE || s == Scp106State.SINK || s == Scp106State.SUBMERGED || s == Scp106State.CAPTURE) {
            this.setState(this.leaving ? Scp106State.SINK : Scp106State.SUBMERGED);
         } else {
            this.setState(s);
         }
      }
      this.lastSeen = now;
      this.stateTicks++;
      this.huntTicks++;
      if (this.swipeCooldown > 0) {
         this.swipeCooldown--;
      }
      if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
         this.heal(1.0F);
      }
      ServerPlayer target = this.target(level);
      switch (this.state()) {
         case EMERGE -> this.tickEmerge(level, target);
         case STALK -> this.tickStalk(level, target);
         case STARE -> this.tickStare(level, target);
         case PHASE -> this.tickPhase(level, target);
         case SINK, RETREAT -> this.tickSink(level);
         case SUBMERGED -> this.tickSubmerged(level, target);
         case CAPTURE -> this.tickCapture(level, target);
      }
   }

   /** Its prey if still valid, or (when it has none and is not a pocket hunter) the nearest player it can take. */
   @Nullable
   private ServerPlayer target(ServerLevel level) {
      if (this.prey != null) {
         ServerPlayer p = level.getServer().getPlayerList().getPlayer(this.prey);
         if (p != null && p.level() == level && this.valid(p)) {
            return p;
         }
         if (this.pocketHunter() || this.state() == Scp106State.CAPTURE) {
            return null;
         }
         if (p == null || p.level() != level || !this.valid(p)) {
            this.prey = null;
         }
      }
      if (this.pocketHunter() || this.leaving) {
         return null;
      }
      ServerPlayer best = null;
      double bd = 40.0 * 40.0;
      for (ServerPlayer p : level.players()) {
         double d = p.distanceToSqr(this);
         if (d < bd && this.valid(p)) {
            bd = d;
            best = p;
         }
      }
      if (best != null) {
         this.prey = best.getUUID();
      }
      return best;
   }

   private boolean valid(ServerPlayer p) {
      if (!p.isAlive() || p.isCreative() || p.isSpectator() || p.distanceToSqr(this) > 96.0 * 96.0) {
         return false;
      }
      if (this.pocketHunter()) {
         return PocketWorld.is(p.level()) && Scp106SavedData.get(p.server).session(p.getUUID()) != null;
      }
      return Scp106Pocket.canTake(p) || this.state() == Scp106State.CAPTURE;
   }

   private void tickEmerge(ServerLevel level, @Nullable ServerPlayer target) {
      if (this.stateTicks == 1) {
         this.sound(level, Scp106Sounds.EMERGE.get(), 1.4F, 0.9F + this.random.nextFloat() * 0.15F);
         this.corrodeAround(level, this.blockPosition(), 1);
      }
      if (target != null) {
         this.getLookControl().setLookAt(target, 6.0F, 6.0F);
      }
      if (this.stateTicks >= this.stateLength) {
         this.lastPos = this.position();
         this.stuckTicks = 0;
         this.setState(Scp106State.STALK);
      }
   }

   private void tickStalk(ServerLevel level, @Nullable ServerPlayer target) {
      int timeout = this.pocketHunter() ? Scp106Rules.POCKET_HUNT_TICKS : Scp106Rules.HUNT_TIMEOUT;
      if (this.huntTicks > timeout) {
         this.leave();
         return;
      }
      if (target == null) {
         this.getNavigation().stop();
         if (++this.lostTicks > (this.pocketHunter() ? 20 : 120)) {
            this.leave();
         }
         return;
      }
      this.lostTicks = 0;
      this.getLookControl().setLookAt(target, 10.0F, 10.0F);
      double dist = this.distanceTo(target);
      if (dist < Scp106Rules.CAPTURE_REACH + target.getBbWidth() * 0.5 && Math.abs(target.getY() - this.getY()) < 1.6 && this.hasLineOfSight(target)) {
         this.startCapture(level, target);
         return;
      }
      this.swipeAttacker(level);
      if (this.stateTicks % 100 == 1) {
         this.pace = 0.8F + this.random.nextFloat() * 0.4F;
      }
      if (this.stateTicks % 10 == 1 || this.getNavigation().isDone()) {
         this.getNavigation().moveTo(target, this.pace);
      }
      if (this.stateTicks % 20 == 0) {
         // stuck: no path, or no progress for two seconds while far
         double moved = this.position().distanceToSqr(this.lastPos);
         this.lastPos = this.position();
         boolean noPath = this.getNavigation().isDone() || this.getNavigation().getPath() == null || !this.getNavigation().getPath().canReach();
         this.stuckTicks = dist > 2.6 && (moved < 0.09 || noPath) ? this.stuckTicks + 20 : 0;
         if (this.stuckTicks >= 40) {
            this.stuckTicks = 0;
            if (!this.tryPhase(level, target)) {
               this.submerge();
            }
            return;
         }
         int d = Scp106Rules.decide(this.random.nextDouble(), dist, this.hasLineOfSight(target));
         if (d == 1) {
            this.setState(Scp106State.STARE);
            return;
         }
         if (d == 2) {
            this.submerge();
            return;
         }
         if (this.onGround() && this.random.nextInt(3) == 0) {
            this.corrode(level, this.blockPosition(), Direction.DOWN);
         }
      }
      if (this.horizontalCollision && this.tickCount % 15 == 0) {
         Direction facing = this.getDirection();
         this.corrode(level, this.blockPosition().above(), facing);
      }
      if (--this.nextBreath <= 0) {
         this.nextBreath = 80 + this.random.nextInt(120);
         this.sound(level, Scp106Sounds.BREATH.get(), 0.9F, 0.9F + this.random.nextFloat() * 0.2F);
      }
   }

   private void tickStare(ServerLevel level, @Nullable ServerPlayer target) {
      if (target == null) {
         this.setState(Scp106State.STALK);
         return;
      }
      this.getLookControl().setLookAt(target, 30.0F, 30.0F);
      if (this.stateTicks == 2) {
         this.sound(level, this.random.nextInt(3) == 0 ? Scp106Sounds.LAUGH.get() : Scp106Sounds.BREATH.get(), 1.1F, 0.8F + this.random.nextFloat() * 0.1F);
      }
      double dist = this.distanceTo(target);
      if (dist < Scp106Rules.CAPTURE_REACH + 0.3 && this.hasLineOfSight(target)) {
         this.startCapture(level, target);
      } else if (this.stateTicks >= this.stateLength) {
         this.setState(Scp106State.STALK);
      }
   }

   // ------------------------------------------------------------------ passing through walls

   /** Starts passing through the wall between it and its prey when it is thin enough and there is room behind. */
   private boolean tryPhase(ServerLevel level, ServerPlayer target) {
      Vec3 to = target.position().subtract(this.position());
      Vec3 dir = new Vec3(to.x, 0, to.z);
      if (dir.lengthSqr() < 1.0E-4) {
         return false;
      }
      dir = dir.normalize();
      // snap to the dominant axis: walls of houses and corridors are axis aligned, the film lands on real faces
      Direction face = Direction.getNearest(dir.x, 0, dir.z);
      Vec3 axis = new Vec3(face.getStepX(), 0, face.getStepZ());
      Vec3 use = Math.abs(dir.x * axis.x + dir.z * axis.z) > 0.85 ? dir : axis;
      Vec3 exit = phaseExit(level, this.position(), use);
      if (exit == null && use != axis) {
         use = axis;
         exit = phaseExit(level, this.position(), use);
      }
      if (exit == null) {
         return false;
      }
      this.phaseDir = use;
      this.phaseTarget = exit;
      this.setState(Scp106State.PHASE);
      this.sound(level, Scp106Sounds.PHASE.get(), 1.2F, 0.9F);
      BlockPos front = BlockPos.containing(this.position().add(use.scale(0.9)));
      this.corrode(level, front.relative(face.getOpposite()), face);
      this.corrode(level, front.relative(face.getOpposite()).above(), face);
      return true;
   }

   /**
    * Where it comes out when walking straight along {@code dir}: the first free standing spot after at least one
    * solid block and at most {@link Scp106Rules#PHASE_THICKNESS} of them. Null for thicker walls, unloaded chunks,
    * the edge of a pocket instance, fluids, or nothing in the way.
    */
   @Nullable
   static Vec3 phaseExit(ServerLevel level, Vec3 from, Vec3 dir) {
      int solid = 0;
      boolean seen = false;
      int slot = PocketWorld.is(level) ? PocketLayout.slotAt(Mth.floor(from.x), Mth.floor(from.z)) : -2;
      for (double s = 0.5; s <= Scp106Rules.PHASE_THICKNESS + 2.5; s += 0.5) {
         Vec3 p = from.add(dir.scale(s));
         BlockPos feet = BlockPos.containing(p.x, from.y + 0.1, p.z);
         if (!level.hasChunkAt(feet) || slot != -2 && PocketLayout.slotAt(feet.getX(), feet.getZ()) != slot) {
            return null;
         }
         boolean blocked = !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
         if (blocked) {
            seen = true;
            if (s == Math.floor(s)) {
               solid++;
            }
            if (solid > Scp106Rules.PHASE_THICKNESS) {
               return null;
            }
         } else if (seen) {
            Vec3 at = new Vec3(p.x, Math.floor(from.y + 0.1), p.z).add(dir.scale(0.6));
            BlockPos f = BlockPos.containing(at);
            for (int dy = 0; dy >= -1; dy--) {
               if (standable(level, f.above(dy))) {
                  return new Vec3(at.x, f.getY() + dy, at.z);
               }
            }
            return null;
         }
      }
      return null;
   }

   private void tickPhase(ServerLevel level, @Nullable ServerPlayer target) {
      if (this.phaseDir == null || this.phaseTarget == null) {
         this.submerge();
         return;
      }
      Vec3 d = this.phaseTarget.subtract(this.position());
      double flat = d.x * this.phaseDir.x + d.z * this.phaseDir.z;
      if (Math.abs(d.y) > 0.05) {
         this.setPos(this.getX(), this.getY() + Mth.clamp(d.y, -0.08, 0.08), this.getZ());
      }
      if (flat <= 0.05 && level.noCollision(this, this.getBoundingBox().deflate(0.05))) {
         this.setPos(this.phaseTarget.x, this.phaseTarget.y, this.phaseTarget.z);
         Direction face = Direction.getNearest(this.phaseDir.x, 0, this.phaseDir.z);
         BlockPos here = this.blockPosition();
         this.corrode(level, here.relative(face.getOpposite()), face.getOpposite());
         this.corrode(level, here, face.getOpposite());
         this.corrode(level, here.above(), face.getOpposite());
         this.phaseDir = null;
         this.phaseTarget = null;
         this.lastPos = this.position();
         this.setState(Scp106State.STALK);
         return;
      }
      if (this.stateTicks % 12 == 0) {
         this.sound(level, Scp106Sounds.CORRODE.get(), 0.7F, 0.8F + this.random.nextFloat() * 0.3F);
      }
      if (this.stateTicks > Scp106Rules.PHASE_MAX_TICKS) {
         this.submerge();
      }
   }

   // ------------------------------------------------------------------ sinking and rising elsewhere

   /** Sinks into the floor to rise again near (behind) its prey. */
   private void submerge() {
      this.leaving = false;
      this.submergeTries = 0;
      this.setState(Scp106State.SINK);
   }

   /** Sinks into the floor for good (lost its prey, hunt over, prey escaped). */
   public void leave() {
      if (this.state() == Scp106State.SUBMERGED) {
         this.discard();
         return;
      }
      if (this.leaving && (this.state() == Scp106State.SINK || this.state() == Scp106State.RETREAT)) {
         return;
      }
      this.leaving = true;
      this.setState(Scp106State.SINK);
   }

   private void retreat() {
      this.leaving = true;
      this.setState(Scp106State.RETREAT);
   }

   private void tickSink(ServerLevel level) {
      if (this.stateTicks == 1) {
         this.sound(level, Scp106Sounds.SINK.get(), 1.2F, 0.9F + this.random.nextFloat() * 0.1F);
         this.corrodeAround(level, this.blockPosition(), 1);
      }
      if (this.stateTicks >= this.stateLength) {
         if (this.leaving) {
            this.discard();
         } else {
            this.setState(Scp106State.SUBMERGED);
         }
      }
   }

   private void tickSubmerged(ServerLevel level, @Nullable ServerPlayer target) {
      if (this.stateTicks < this.stateLength) {
         return;
      }
      if (target == null || this.leaving) {
         this.discard();
         return;
      }
      Vec3 at = emergeSpot(level, target, this.random, 4.0, 8.0, true);
      if (at == null) {
         if (++this.submergeTries > 6) {
            this.discard();
         } else {
            this.stateTicks = this.stateLength - 20;
         }
         return;
      }
      float yaw = (float)(Mth.atan2(target.getZ() - at.z, target.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
      this.moveTo(at.x, at.y, at.z, yaw, 0.0F);
      this.setYHeadRot(yaw);
      this.yBodyRot = yaw;
      this.setState(Scp106State.EMERGE);
   }

   /** A free standing spot 4..8 blocks from the player (behind them first), loaded, in the same pocket instance. */
   @Nullable
   static Vec3 emergeSpot(ServerLevel level, ServerPlayer target, RandomSource r, double min, double max, boolean behind) {
      Vec3 look = target.getViewVector(1.0F);
      double back = Math.atan2(-look.z, -look.x);
      boolean pocket = PocketWorld.is(level);
      int slot = pocket ? PocketLayout.slotAt(target.getBlockX(), target.getBlockZ()) : -1;
      for (int i = 0; i < 48; i++) {
         double ang = behind && i < 28 ? back + (r.nextDouble() - 0.5) * 1.8 : r.nextDouble() * Math.PI * 2.0;
         double d = min + r.nextDouble() * (max - min);
         int x = Mth.floor(target.getX() + Math.cos(ang) * d);
         int z = Mth.floor(target.getZ() + Math.sin(ang) * d);
         if (pocket && PocketLayout.slotAt(x, z) != slot) {
            continue;
         }
         for (int dy = 1; dy >= -2; dy--) {
            BlockPos feet = new BlockPos(x, target.getBlockY() + dy, z);
            if (standable(level, feet)) {
               return Vec3.atBottomCenterOf(feet);
            }
         }
      }
      return null;
   }

   static boolean standable(ServerLevel level, BlockPos feet) {
      if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) {
         return false;
      }
      BlockPos below = feet.below();
      if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(feet).isEmpty()) {
         return false;
      }
      AABB body = new AABB(feet.getX() + 0.5 - Scp106Rules.WIDTH / 2, feet.getY() + 0.01, feet.getZ() + 0.5 - Scp106Rules.WIDTH / 2,
         feet.getX() + 0.5 + Scp106Rules.WIDTH / 2, feet.getY() + Scp106Rules.HEIGHT, feet.getZ() + 0.5 + Scp106Rules.WIDTH / 2);
      return level.noCollision(body);
   }

   // ------------------------------------------------------------------ capture

   private void startCapture(ServerLevel level, ServerPlayer target) {
      this.setState(Scp106State.CAPTURE);
      this.captureAt = target.position();
      this.entityData.set(DATA_HELD, target.getId());
      target.stopRiding();
      target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Scp106Rules.CAPTURE_TICKS + 10, 6, false, false));
      target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, Scp106Rules.CAPTURE_TICKS + 40, 0, false, false));
      float yaw = (float)(Mth.atan2(target.getZ() - this.getZ(), target.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
      this.setYRot(yaw);
      this.setYHeadRot(yaw);
      this.yBodyRot = yaw;
      this.sound(level, Scp106Sounds.CAPTURE.get(), 1.6F, 1.0F);
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.CAPTURE, this.getId(), this.pocketHunter() ? 1 : 0, 0L), target);
      LOG.debug("SCP-106 #{} caught {}{}", this.getId(), target.getGameProfile().getName(), this.pocketHunter() ? " (pocket drag)" : "");
   }

   private void tickCapture(ServerLevel level, @Nullable ServerPlayer target) {
      if (target == null || this.captureAt == null || target.hasDisconnected()) {
         // the prey died, logged out or was moved away: nothing taken, it leaves
         this.leave();
         return;
      }
      this.getLookControl().setLookAt(target, 30.0F, 30.0F);
      if (this.stateTicks < Scp106Rules.CAPTURE_TRANSFER) {
         if (target.position().distanceToSqr(this.captureAt) > 0.0025) {
            target.connection.teleport(this.captureAt.x, this.captureAt.y, this.captureAt.z, target.getYRot(), target.getXRot());
         }
         target.setDeltaMovement(Vec3.ZERO);
         if (this.stateTicks % 8 == 0) {
            this.corrodeAround(level, target.blockPosition(), Math.min(2, this.stateTicks / 16));
         }
         if (this.stateTicks == 26) {
            this.sound(level, Scp106Sounds.LAUGH.get(), 1.0F, 0.85F);
         }
      } else if (this.stateTicks == Scp106Rules.CAPTURE_TRANSFER) {
         this.entityData.set(DATA_HELD, -1);
         if (this.pocketHunter()) {
            Scp106Pocket.drag(target, this);
         } else {
            Scp106Pocket.queueCapture(target);
         }
      }
      if (this.stateTicks >= this.stateLength) {
         this.leave();
      }
   }

   /** Somebody (a mob, a pet, a player it is not after) keeps hitting it from close by: one slow backhand swipe. */
   private void swipeAttacker(ServerLevel level) {
      LivingEntity hitter = this.getLastHurtByMob();
      if (hitter == null || this.swipeCooldown > 0 || !hitter.isAlive() || this.tickCount - this.getLastHurtByMobTimestamp() > 100
         || this.distanceToSqr(hitter) > 2.6 * 2.6 || hitter instanceof Player p && (p.isCreative() || p.isSpectator())) {
         return;
      }
      this.swipeCooldown = 40;
      this.entityData.set(DATA_SWIPE, this.entityData.get(DATA_SWIPE) + 1);
      this.sound(level, Scp106Sounds.CORRODE.get(), 1.0F, 0.7F);
      hitter.hurt(this.damageSources().mobAttack(this), 5.0F);
      hitter.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true));
   }

   // ------------------------------------------------------------------ corrosion

   /** A corrosion film in the free block {@code pos}, on its {@code face} side (that neighbour must be a solid face). */
   void corrode(ServerLevel level, BlockPos pos, Direction face) {
      if (!level.hasChunkAt(pos)) {
         return;
      }
      BlockState here = level.getBlockState(pos);
      Block corr = PocketBlocks.CORROSION.get();
      BlockPos support = pos.relative(face);
      if (!level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite())) {
         return;
      }
      BlockState next;
      if (here.is(corr)) {
         next = here.setValue(MultifaceBlock.getFaceProperty(face), true);
      } else if (here.isAir()) {
         next = corr.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true);
      } else {
         return;
      }
      if (next != here) {
         level.setBlock(pos, next, Block.UPDATE_ALL);
      }
   }

   void corrodeAround(ServerLevel level, BlockPos feet, int radius) {
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz <= radius * radius + 1 && (radius == 0 || this.random.nextInt(4) != 0 || dx == 0 && dz == 0)) {
               this.corrode(level, feet.offset(dx, 0, dz), Direction.DOWN);
            }
         }
      }
   }

   private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
      level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
   }

   // ------------------------------------------------------------------ module hooks

   /** Its prey got out (or an admin released them): every hunter after them sinks away. */
   public static void dismissHunters(ServerPlayer p) {
      for (Scp106Entity e : loaded()) {
         if (p.getUUID().equals(e.prey)) {
            e.prey = null;
            if (e.pocketHunter() || e.level() != p.level()) {
               e.discard();
            } else {
               e.leave();
            }
         }
      }
   }

   /** One hunt inside the pocket: it rises at a doorway of the player's room (or behind them) and comes for them. */
   @Nullable
   public static Scp106Entity spawnPocketHunter(ServerLevel level, ServerPlayer p, PocketLayout l, Room room) {
      for (Scp106Entity e : loaded()) {
         if (e.pocketHunter() && p.getUUID().equals(e.prey)) {
            return null;
         }
      }
      int ox = PocketLayout.slotOriginX(l.slot);
      int oz = PocketLayout.slotOriginZ(l.slot);
      Vec3 at = null;
      double best = -1;
      for (Door d : room.doors) {
         int[] in = PocketPainter.doorInside(room, d);
         BlockPos feet = new BlockPos(ox + in[0], in[1], oz + in[2]);
         double dist = p.distanceToSqr(Vec3.atBottomCenterOf(feet));
         if (dist > 64 && dist > best && standable(level, feet)) {
            best = dist;
            at = Vec3.atBottomCenterOf(feet);
         }
      }
      if (at == null) {
         at = emergeSpot(level, p, level.random, 6.0, 12.0, true);
      }
      if (at == null) {
         return null;
      }
      Scp106Entity e = Scp106Entities.SCP106.get().create(level);
      if (e == null) {
         return null;
      }
      float yaw = (float)(Mth.atan2(p.getZ() - at.z, p.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
      e.moveTo(at.x, at.y, at.z, yaw, 0.0F);
      e.setYHeadRot(yaw);
      e.yBodyRot = yaw;
      e.setFlag(FLAG_POCKET, true);
      e.prey = p.getUUID();
      e.setState(Scp106State.EMERGE);
      if (!level.addFreshEntity(e)) {
         return null;
      }
      LOG.debug("SCP-106 pocket hunter for {} at {}", p.getGameProfile().getName(), e.blockPosition());
      return e;
   }

   /** Rises near (behind) a player and hunts them: natural spawns and /scp106 spawn. */
   @Nullable
   public static Scp106Entity emergeNear(ServerLevel level, ServerPlayer p, double min, double max, boolean natural) {
      Vec3 at = emergeSpot(level, p, level.random, min, max, true);
      return at == null ? null : emergeAt(level, at, p, natural);
   }

   @Nullable
   public static Scp106Entity emergeAt(ServerLevel level, Vec3 at, @Nullable ServerPlayer prey, boolean natural) {
      Scp106Entity e = Scp106Entities.SCP106.get().create(level);
      if (e == null) {
         return null;
      }
      float yaw = prey == null ? level.random.nextFloat() * 360.0F : (float)(Mth.atan2(prey.getZ() - at.z, prey.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
      e.moveTo(at.x, at.y, at.z, yaw, 0.0F);
      e.setYHeadRot(yaw);
      e.yBodyRot = yaw;
      e.setNatural(natural);
      e.prey = prey == null ? null : prey.getUUID();
      e.setState(Scp106State.EMERGE);
      return level.addFreshEntity(e) ? e : null;
   }

   // ------------------------------------------------------------------ saving

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putByte("State", (byte)this.state().ordinal());
      tag.putByte("Flags", this.entityData.get(DATA_FLAGS));
      if (this.prey != null) {
         tag.putUUID("Prey", this.prey);
      }
      tag.putInt("Hunt", this.huntTicks);
      tag.putBoolean("Leaving", this.leaving);
      tag.putLong("LastSeen", this.lastSeen);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.entityData.set(DATA_STATE, tag.getByte("State"));
      this.entityData.set(DATA_FLAGS, tag.getByte("Flags"));
      this.prey = tag.hasUUID("Prey") ? tag.getUUID("Prey") : null;
      this.huntTicks = tag.getInt("Hunt");
      this.leaving = tag.getBoolean("Leaving");
      this.lastSeen = tag.contains("LastSeen") ? tag.getLong("LastSeen") : Long.MIN_VALUE;
   }

   public String describe() {
      return String.format(java.util.Locale.ROOT, "#%d %s %s at %.0f %.0f %.0f in %s, hunt %ds%s%s", this.getId(), this.state(),
         this.pocketHunter() ? "pocket" : this.natural() ? "natural" : "manual", this.getX(), this.getY(), this.getZ(),
         this.level().dimension().location(), this.huntTicks / 20, this.prey == null ? "" : ", prey " + this.prey, this.leaving ? ", leaving" : "");
   }
}

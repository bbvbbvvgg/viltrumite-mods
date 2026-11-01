package dev.baranhan.viltrumitecore.scp.scp096;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.Scp096S2CPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * SCP-096 "Shy Guy" (docs/design/2026-11-05-scp096-design.md). Server-authoritative: the state machine, the
 * face check, the target set and the chase run here; clients get the state, a few flags and event counters and
 * draw, animate and play the loops from them. Persistent (saved with its chunk, never despawns), not an
 * {@code Enemy} (citizens flee only from the danger zone it raises while enraged), effectively unkillable without
 * a boss bar.
 */
public class Scp096Entity extends PathfinderMob {
   public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "scp096"));
   public static final int FLAG_FACE_OPEN = 1;
   public static final int FLAG_CLIMB = 2;
   public static final int FLAG_LEAP = 4;
   public static final int FLAG_COVER = 8;
   private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Scp096Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Scp096Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(Scp096Entity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DATA_SMASH = SynchedEntityData.defineId(Scp096Entity.class, EntityDataSerializers.INT);
   /** Loaded server instances (deaths of targets, logins, status). */
   private static final Set<Scp096Entity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

   private int stateTicks;
   private int stateLength = 2400;
   private boolean natural;
   private int peekTicks;
   private int nextPeek = 240;
   @Nullable
   private Vec3 home;
   @Nullable
   private BlockPos wanderGoal;
   @Nullable
   private UUID primary;
   private int noTargetTicks;
   private int hysteriaTicks = Scp096Rules.DEFAULT_HYSTERIA_TICKS;
   private final Map<UUID, Target> targets = new LinkedHashMap<>();
   private final Map<UUID, Integer> seen = new HashMap<>();
   private final Set<UUID> notified = new HashSet<>();
   final Scp096Chase chase = new Scp096Chase(this);

   // ---- client-side animation inputs (written by the client tick and the renderer, unused on the server) ----
   public Scp096State clientState = Scp096State.SIT;
   public Scp096State clientPrevState = Scp096State.SIT;
   public int clientStateStart = -1000;
   public int clientAttackStart = -1000;
   public int clientSmashStart = -1000;
   public float clientMove;
   public float clientMoveO;
   public double clientGait;
   public double clientGaitO;
   public int clientAirTicks;
   public int clientClimbTicks;
   /** Smoothed clip weights kept by the renderer (real-time exponential smoothing). */
   public final float[] clientWeights = new float[24];
   public float clientWeightAge = -1.0F;

   /** One entry of the target set. */
   static final class Target {
      final UUID id;
      final boolean player;
      int missing;

      Target(UUID id, boolean player) {
         this.id = id;
         this.player = player;
      }
   }

   public Scp096Entity(EntityType<? extends Scp096Entity> type, Level level) {
      super(type, level);
      this.setMaxUpStep(1.1F);
      this.xpReward = 0;
      this.setPersistenceRequired();
      this.getNavigation().setCanFloat(true);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, Scp096Rules.MAX_HEALTH)
         .add(Attributes.MOVEMENT_SPEED, 0.25)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.ARMOR, 4.0)
         .add(Attributes.ATTACK_DAMAGE, Scp096Rules.ATTACK_DAMAGE);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(DATA_STATE, (byte)Scp096State.SIT.id());
      this.entityData.define(DATA_FLAGS, (byte)0);
      this.entityData.define(DATA_ATTACK, 0);
      this.entityData.define(DATA_SMASH, 0);
   }

   @Override
   protected void registerGoals() {
      // no goals: the state machine below drives everything
   }

   // ------------------------------------------------------------------ state

   public Scp096State state() {
      return Scp096State.byId(this.entityData.get(DATA_STATE));
   }

   public boolean flag(int bit) {
      return (this.entityData.get(DATA_FLAGS) & bit) != 0;
   }

   void setFlag(int bit, boolean on) {
      int f = this.entityData.get(DATA_FLAGS);
      int next = on ? f | bit : f & ~bit;
      if (next != f) {
         this.entityData.set(DATA_FLAGS, (byte)next);
      }
   }

   public boolean natural() {
      return this.natural;
   }

   public void setNatural(boolean natural) {
      this.natural = natural;
   }

   public int stateTicks() {
      return this.stateTicks;
   }

   public int targetCount() {
      return this.targets.size();
   }

   public List<UUID> targetIds() {
      return new ArrayList<>(this.targets.keySet());
   }

   public void setState(Scp096State next) {
      Scp096State prev = this.state();
      this.stateTicks = 0;
      this.entityData.set(DATA_STATE, (byte)next.id());
      switch (next) {
         case SIT -> this.stateLength = 2400 + this.random.nextInt(3600);
         case STAND -> this.stateLength = 200 + this.random.nextInt(300);
         case WANDER -> {
            this.stateLength = 600;
            this.wanderGoal = null;
         }
         case HYSTERIA -> this.hysteriaTicks = Scp096Rules.hysteriaTicks(dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig.INSTANCE.scp096HysteriaSeconds);
         default -> {
         }
      }
      if (next != Scp096State.WANDER) {
         this.getNavigation().stop();
      }
      if (!next.enraged()) {
         this.chase.reset();
      }
      if (prev != next) {
         this.refreshDimensions();
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DATA_STATE.equals(key)) {
         this.refreshDimensions();
         if (this.level().isClientSide) {
            Scp096State s = this.state();
            if (s != this.clientState) {
               this.clientPrevState = this.clientState;
               this.clientState = s;
               this.clientStateStart = this.tickCount;
            }
         }
      } else if (DATA_ATTACK.equals(key) && this.level().isClientSide) {
         this.clientAttackStart = this.tickCount;
      } else if (DATA_SMASH.equals(key) && this.level().isClientSide) {
         this.clientSmashStart = this.tickCount;
      }
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      Scp096State s = this.state();
      if (s == Scp096State.SIT) {
         return EntityDimensions.scalable(Scp096Rules.SIT_WIDTH, Scp096Rules.SIT_HEIGHT);
      }
      if (s == Scp096State.CHASE || s == Scp096State.HYSTERIA) {
         return EntityDimensions.scalable(Scp096Rules.WIDTH, Scp096Rules.RUN_HEIGHT);
      }
      return EntityDimensions.scalable(Scp096Rules.WIDTH, Scp096Rules.HEIGHT);
   }

   @Override
   protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
      return dims.height * 0.9F;
   }

   // ------------------------------------------------------------------ lifecycle

   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide) {
         LOADED.add(this);
         if (this.home == null) {
            this.home = this.position();
         }
      }
   }

   @Override
   public void onRemovedFromWorld() {
      super.onRemovedFromWorld();
      LOADED.remove(this);
   }

   @Override
   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
         if (reason.shouldDestroy()) {
            this.releaseAll(sl);
            Scp096SavedData.get(sl.getServer()).forget(this.getUUID());
         } else {
            Scp096SavedData.get(sl.getServer()).touch(this);
         }
      }
      super.remove(reason);
   }

   /** Server instances currently loaded in any level. */
   public static List<Scp096Entity> loaded() {
      List<Scp096Entity> out = new ArrayList<>();
      for (Scp096Entity e : LOADED) {
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
   public boolean hurt(DamageSource source, float amount) {
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return super.hurt(source, amount);
      }
      if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING)
         || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
         || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypeTags.IS_FIRE)) {
         return false;
      }
      return super.hurt(source, Scp096Rules.cappedHit(amount));
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distanceSq) {
      double r = 192.0 * getViewScale();
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
   protected float getSoundVolume() {
      return this.state() == Scp096State.CHASE ? 1.6F : 0.7F;
   }

   // ------------------------------------------------------------------ ticking

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.clientTick();
      }
   }

   private void clientTick() {
      double dx = this.getX() - this.xo;
      double dz = this.getZ() - this.zo;
      float speed = (float)Math.sqrt(dx * dx + dz * dz);
      this.clientMoveO = this.clientMove;
      this.clientMove += (speed - this.clientMove) * 0.5F;
      this.clientGaitO = this.clientGait;
      Scp096State s = this.state();
      double stride = s == Scp096State.CHASE ? 3.6 : 1.5;
      this.clientGait += speed / stride;
      this.clientAirTicks = this.onGround() ? 0 : this.clientAirTicks + 1;
      this.clientClimbTicks = this.flag(FLAG_CLIMB) ? this.clientClimbTicks + 1 : 0;
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      ServerLevel level = (ServerLevel)this.level();
      this.stateTicks++;
      if (this.getHealth() < this.getMaxHealth() && this.isAlive()) {
         this.heal(Scp096Rules.REGEN_PER_TICK);
      }
      boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
      if (this.tickCount % 5 == 0) {
         this.updateTargets(level, peaceful);
      }
      if (!peaceful && this.faceOpen()) {
         if (this.tickCount % 2 == 0) {
            this.scanPlayers(level);
         }
         if (this.tickCount % Scp096Rules.CITIZEN_CHECK_INTERVAL == 0) {
            this.scanCitizens(level);
         }
      }
      switch (this.state()) {
         case SIT -> this.tickSit(level);
         case STAND -> this.tickStand(level);
         case WANDER -> this.tickWander(level);
         case TRIGGERED -> this.tickTriggered(level);
         case HYSTERIA -> this.tickHysteria(level);
         case CHASE -> this.tickChase(level);
         case CALMING -> this.tickCalming();
      }
      if (this.state().enraged() && this.tickCount % 10 == 0) {
         CityManager city = CityManager.of(level);
         if (city != null) {
            city.dangerMap().track(this.getId(), this.getX(), this.getY() + 1.0, this.getZ(), 10.0F, 28.0F, city.now(), 30);
         }
      }
      if (this.tickCount % 40 == 0) {
         this.resyncTargets(level);
      }
      if (this.tickCount % 20 == 0) {
         Scp096SavedData.get(level.getServer()).touch(this);
      }
   }

   /** The face can be seen right now (synced flag, set by the state logic). */
   public boolean faceOpen() {
      return this.flag(FLAG_FACE_OPEN);
   }

   private void tickSit(ServerLevel level) {
      this.getNavigation().stop();
      if (this.peekTicks > 0) {
         this.peekTicks--;
      } else if (--this.nextPeek <= 0) {
         this.peekTicks = 24 + this.random.nextInt(30);
         this.nextPeek = 220 + this.random.nextInt(480);
      }
      this.setFlag(FLAG_FACE_OPEN, this.peekTicks > 0);
      this.setFlag(FLAG_COVER, false);
      if (this.stateTicks >= this.stateLength) {
         this.setState(Scp096State.STAND);
      }
   }

   private void tickStand(ServerLevel level) {
      this.getNavigation().stop();
      boolean near = this.observerWithin(level, 10.0);
      this.setFlag(FLAG_COVER, near);
      this.setFlag(FLAG_FACE_OPEN, !near && this.stateTicks > 30);
      if (this.stateTicks >= this.stateLength) {
         if (!near && this.random.nextFloat() < 0.6F) {
            this.setState(Scp096State.WANDER);
         } else {
            this.setState(Scp096State.SIT);
         }
      }
   }

   private void tickWander(ServerLevel level) {
      this.setFlag(FLAG_COVER, false);
      this.setFlag(FLAG_FACE_OPEN, true);
      if (this.observerWithin(level, 8.0)) {
         this.setState(Scp096State.STAND);
         return;
      }
      if (this.wanderGoal == null) {
         this.wanderGoal = this.pickWanderGoal(level);
         if (this.wanderGoal == null) {
            this.setState(Scp096State.STAND);
            return;
         }
         this.getNavigation().moveTo(this.wanderGoal.getX() + 0.5, this.wanderGoal.getY(), this.wanderGoal.getZ() + 0.5, Scp096Rules.WANDER_SPEED_MOD);
      }
      boolean arrived = this.wanderGoal.distToCenterSqr(this.position()) < 2.5 * 2.5;
      if (arrived || this.getNavigation().isDone() && this.stateTicks > 20 || this.stateTicks >= this.stateLength) {
         this.setState(this.random.nextFloat() < 0.5F ? Scp096State.SIT : Scp096State.STAND);
      }
   }

   private void tickTriggered(ServerLevel level) {
      this.getNavigation().stop();
      this.setFlag(FLAG_COVER, false);
      this.setFlag(FLAG_FACE_OPEN, true);
      Entity t = this.primaryEntity(level);
      if (t != null) {
         this.lookAtPoint(t.getEyePosition(), 60.0F);
      }
      if (this.stateTicks >= Scp096Rules.TRIGGERED_TICKS) {
         this.setState(Scp096State.HYSTERIA);
      }
   }

   private void tickHysteria(ServerLevel level) {
      this.getNavigation().stop();
      this.setFlag(FLAG_COVER, false);
      this.setFlag(FLAG_FACE_OPEN, true);
      if (this.targets.isEmpty()) {
         this.setState(Scp096State.CALMING);
         return;
      }
      Entity t = this.primaryEntity(level);
      if (t != null && this.stateTicks % 20 < 10) {
         this.lookAtPoint(t.getEyePosition(), 12.0F);
      }
      if (this.stateTicks >= this.hysteriaTicks) {
         this.setState(Scp096State.CHASE);
      }
   }

   private void tickChase(ServerLevel level) {
      this.setFlag(FLAG_COVER, false);
      this.setFlag(FLAG_FACE_OPEN, true);
      if (this.targets.isEmpty()) {
         this.setState(Scp096State.CALMING);
         return;
      }
      Entity t = this.primaryEntity(level);
      if (t == null) {
         // every target is away (other dimension, offline, unloaded): wait where it stands
         this.noTargetTicks++;
         this.chase.idle();
         return;
      }
      this.noTargetTicks = 0;
      this.chase.tick(level, t);
   }

   private void tickCalming() {
      this.getNavigation().stop();
      this.setFlag(FLAG_COVER, true);
      this.setFlag(FLAG_FACE_OPEN, false);
      this.setFlag(FLAG_CLIMB, false);
      this.setFlag(FLAG_LEAP, false);
      if (this.stateTicks >= Scp096Rules.CALMING_TICKS) {
         this.home = this.position();
         this.setState(Scp096State.SIT);
      }
   }

   void lookAtPoint(Vec3 p, float maxTurn) {
      Vec3 eye = this.getEyePosition();
      double dx = p.x - eye.x;
      double dy = p.y - eye.y;
      double dz = p.z - eye.z;
      double h = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float)(-(Mth.atan2(dy, h) * (180.0 / Math.PI)));
      float head = Mth.approachDegrees(this.getYHeadRot(), yaw, maxTurn);
      this.setYHeadRot(head);
      this.setXRot(Mth.approachDegrees(this.getXRot(), Mth.clamp(pitch, -60.0F, 60.0F), maxTurn));
      if (Math.abs(Mth.wrapDegrees(head - this.yBodyRot)) > 50.0F) {
         this.yBodyRot = Mth.approachDegrees(this.yBodyRot, head, maxTurn * 0.5F);
         this.setYRot(this.yBodyRot);
      }
   }

   @Nullable
   private BlockPos pickWanderGoal(ServerLevel level) {
      Vec3 h = this.home == null ? this.position() : this.home;
      CityManager city = CityManager.of(level);
      if (city != null && city.profile().inArena(this.getBlockX(), this.getBlockY(), this.getBlockZ())) {
         WalkGrid grid = city.grid();
         long cell = grid.randomCell(this.random::nextDouble, h.x, h.z, 4.0, 18.0, false, 16,
            (x, feetY, z) -> level.isPositionEntityTicking(new BlockPos(x, feetY, z)) && Math.abs(feetY - this.getBlockY()) <= 3);
         if (cell != Long.MIN_VALUE) {
            int x = WalkGrid.unpackX(cell);
            int z = WalkGrid.unpackZ(cell);
            return new BlockPos(x, grid.feetY(x, z), z);
         }
         return null;
      }
      for (int i = 0; i < 10; i++) {
         double a = this.random.nextDouble() * Math.PI * 2.0;
         double r = 4.0 + this.random.nextDouble() * 12.0;
         BlockPos p = BlockPos.containing(h.x + Math.cos(a) * r, this.getY(), h.z + Math.sin(a) * r);
         if (!level.isPositionEntityTicking(p)) {
            continue;
         }
         for (int dy = 2; dy >= -3; dy--) {
            BlockPos q = p.above(dy);
            if (level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)
               && level.noCollision(this, this.getBoundingBox().move(Vec3.atBottomCenterOf(q).subtract(this.position())))) {
               return q;
            }
         }
      }
      return null;
   }

   // ------------------------------------------------------------------ the face

   /** Where the face is now (world space), by state: bowed when sitting, hunched while raging. */
   public Vec3 facePoint() {
      Scp096State s = this.state();
      double up;
      double fwd;
      if (s == Scp096State.SIT) {
         up = 1.12;
         fwd = 0.38;
      } else if (s == Scp096State.CHASE || s == Scp096State.HYSTERIA) {
         up = 1.95;
         fwd = 0.36;
      } else {
         up = 2.18;
         fwd = 0.16;
      }
      double yaw = Math.toRadians(this.getYHeadRot());
      return new Vec3(this.getX() - Math.sin(yaw) * fwd, this.getY() + up, this.getZ() + Math.cos(yaw) * fwd);
   }

   /** Unit normal of the face: the head's look, tipped down while sitting with the head lifted. */
   public double[] faceNormal() {
      float pitch = this.state() == Scp096State.SIT ? 10.0F : this.getXRot();
      return Scp096Sight.normal(this.getYHeadRot(), pitch);
   }

   /** Pure + raycast: can this observer see the face now. */
   boolean canSeeFace(LivingEntity observer, boolean player) {
      Vec3 eye = observer.getEyePosition();
      Vec3 look = observer.getViewVector(1.0F);
      Vec3 face = this.facePoint();
      double[] n = this.faceNormal();
      double range;
      double halfH;
      double halfV;
      if (player) {
         Player p = (Player)observer;
         if (p.isScoping()) {
            range = Scp096Rules.SPYGLASS_RANGE;
            halfH = halfV = Scp096Rules.SPYGLASS_HALF;
         } else {
            range = Scp096Rules.FACE_RANGE;
            halfH = Scp096Rules.PLAYER_HALF_H;
            halfV = Scp096Rules.PLAYER_HALF_V;
         }
      } else {
         range = Scp096Rules.CITIZEN_RANGE;
         halfH = Scp096Rules.CITIZEN_HALF_H;
         halfV = Scp096Rules.CITIZEN_HALF_V;
      }
      if (observer.hasEffect(MobEffects.BLINDNESS)) {
         range = Math.min(range, Scp096Rules.BLIND_RANGE);
      } else if (observer.hasEffect(MobEffects.DARKNESS)) {
         range = Math.min(range, Scp096Rules.DARKNESS_RANGE);
      }
      if (!Scp096Sight.faceInView(eye.x, eye.y, eye.z, look.x, look.y, look.z, face.x, face.y, face.z, n[0], n[1], n[2], halfH, halfV, range,
         Scp096Rules.FACE_FRONT)) {
         return false;
      }
      return this.clearSight(eye, face, observer);
   }

   boolean clearSight(Vec3 from, Vec3 to, Entity viewer) {
      if (!this.level().isLoaded(BlockPos.containing(to)) || !this.level().isLoaded(BlockPos.containing(from))) {
         return false;
      }
      HitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
      return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) >= from.distanceToSqr(to) - 0.09;
   }

   private void scanPlayers(ServerLevel level) {
      double reach = Scp096Rules.SPYGLASS_RANGE;
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
            this.seen.remove(p.getUUID());
            continue;
         }
         if (p.distanceToSqr(this) > reach * reach) {
            this.seen.remove(p.getUUID());
            continue;
         }
         if (this.canSeeFace(p, true)) {
            int n = this.seen.merge(p.getUUID(), 1, Integer::sum);
            if (n >= Scp096Rules.SEEN_CHECKS) {
               this.seen.remove(p.getUUID());
               this.onFaceSeen(level, p, true);
            }
         } else {
            this.seen.remove(p.getUUID());
         }
      }
   }

   private void scanCitizens(ServerLevel level) {
      CityManager city = CityManager.of(level);
      if (city == null) {
         return;
      }
      double r2 = Scp096Rules.CITIZEN_RANGE * Scp096Rules.CITIZEN_RANGE;
      List<CitizenEntity> near = new ArrayList<>();
      for (CitizenEntity c : city.citizens()) {
         if (c.isAlive() && !this.targets.containsKey(c.getUUID()) && c.distanceToSqr(this) <= r2) {
            near.add(c);
         }
      }
      near.sort((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)));
      int rays = 0;
      for (int i = 0; i < near.size() && i < Scp096Rules.CITIZEN_MAX_CHECKED; i++) {
         CitizenEntity c = near.get(i);
         Vec3 eye = c.getEyePosition();
         Vec3 look = c.getViewVector(1.0F);
         Vec3 face = this.facePoint();
         double[] n = this.faceNormal();
         if (!Scp096Sight.faceInView(eye.x, eye.y, eye.z, look.x, look.y, look.z, face.x, face.y, face.z, n[0], n[1], n[2],
            Scp096Rules.CITIZEN_HALF_H, Scp096Rules.CITIZEN_HALF_V, Scp096Rules.CITIZEN_RANGE, Scp096Rules.FACE_FRONT)) {
            continue;
         }
         if (rays++ >= Scp096Rules.CITIZEN_MAX_RAYS) {
            break;
         }
         if (this.clearSight(eye, face, c)) {
            this.onFaceSeen(level, c, false);
         }
      }
   }

   private boolean observerWithin(ServerLevel level, double r) {
      double r2 = r * r;
      for (ServerPlayer p : level.players()) {
         if (!p.isSpectator() && p.distanceToSqr(this) < r2) {
            return true;
         }
      }
      CityManager city = CityManager.of(level);
      if (city != null && this.tickCount % 10 == 0) {
         for (CitizenEntity c : city.citizens()) {
            if (c.distanceToSqr(this) < r2) {
               return true;
            }
         }
      }
      return false;
   }

   /** Someone saw the face: a new target; a calm 096 locks up and starts raging. */
   void onFaceSeen(ServerLevel level, LivingEntity observer, boolean player) {
      Scp096State s = this.state();
      if (s == Scp096State.CALMING) {
         return;
      }
      boolean added = this.addTarget(observer, player);
      if (s.calm()) {
         this.primary = observer.getUUID();
         this.setState(Scp096State.TRIGGERED);
         this.lookAtPoint(observer.getEyePosition(), 180.0F);
      }
      if (added && observer instanceof ServerPlayer sp) {
         CoreMessages.sendToPlayer(new Scp096S2CPacket(Scp096S2CPacket.TRIGGERED, this.getId()), sp);
         this.notified.add(sp.getUUID());
      }
   }

   // ------------------------------------------------------------------ targets

   /** Adds a target (true when it is new). */
   public boolean addTarget(LivingEntity e, boolean player) {
      if (this.targets.containsKey(e.getUUID())) {
         return false;
      }
      this.targets.put(e.getUUID(), new Target(e.getUUID(), player));
      if (this.primary == null) {
         this.primary = e.getUUID();
      }
      return true;
   }

   /** Operator trigger (QA): enrage at these entities as if they had seen the face. */
   public void forceTrigger(ServerLevel level, List<? extends LivingEntity> who) {
      for (LivingEntity e : who) {
         if (e != this) {
            this.onFaceSeen(level, e, e instanceof Player);
         }
      }
   }

   /** Operator calm (QA) and the end of a chase: every target is let go. */
   public void calmDown(ServerLevel level) {
      this.releaseAll(level);
      if (this.state().enraged()) {
         this.setState(Scp096State.CALMING);
      }
   }

   void removeTarget(ServerLevel level, UUID id) {
      Target t = this.targets.remove(id);
      if (t == null) {
         return;
      }
      if (id.equals(this.primary)) {
         this.primary = null;
      }
      if (t.player && this.notified.remove(id)) {
         ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
         if (p != null) {
            CoreMessages.sendToPlayer(new Scp096S2CPacket(Scp096S2CPacket.RELEASED, this.getId()), p);
         }
      }
   }

   private void releaseAll(ServerLevel level) {
      for (UUID id : new ArrayList<>(this.targets.keySet())) {
         this.removeTarget(level, id);
      }
      this.targets.clear();
      this.notified.clear();
      this.primary = null;
   }

   @Nullable
   Entity resolve(ServerLevel level, Target t) {
      if (t.player) {
         ServerPlayer p = level.getServer().getPlayerList().getPlayer(t.id);
         return p != null && p.level() == level ? p : null;
      }
      return level.getEntity(t.id);
   }

   private void updateTargets(ServerLevel level, boolean peaceful) {
      if (this.targets.isEmpty()) {
         return;
      }
      if (peaceful) {
         this.calmDown(level);
         return;
      }
      Iterator<Map.Entry<UUID, Target>> it = this.targets.entrySet().iterator();
      List<UUID> drop = new ArrayList<>();
      while (it.hasNext()) {
         Target t = it.next().getValue();
         Entity e = this.resolve(level, t);
         if (e == null) {
            t.missing += 5;
            int limit = t.player ? Scp096Rules.ABSENT_PLAYER_TICKS : Scp096Rules.ABSENT_CITIZEN_TICKS;
            if (t.missing > limit) {
               drop.add(t.id);
            }
            continue;
         }
         t.missing = 0;
         if (!e.isAlive() || e.isRemoved() || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            drop.add(t.id);
         }
      }
      for (UUID id : drop) {
         this.removeTarget(level, id);
      }
      // nearest present target leads (the current one keeps it unless another is clearly closer)
      Entity cur = this.primaryEntity(level);
      double curD = cur == null ? Double.MAX_VALUE : cur.distanceToSqr(this);
      for (Target t : this.targets.values()) {
         Entity e = this.resolve(level, t);
         if (e != null && e != cur && e.distanceToSqr(this) < curD * 0.5) {
            this.primary = t.id;
            curD = e.distanceToSqr(this);
            cur = e;
         }
      }
      if (cur == null) {
         for (Target t : this.targets.values()) {
            if (this.resolve(level, t) != null) {
               this.primary = t.id;
               break;
            }
         }
      }
      if (this.targets.isEmpty() && this.state().enraged()) {
         this.setState(Scp096State.CALMING);
      }
   }

   @Nullable
   Entity primaryEntity(ServerLevel level) {
      if (this.primary == null) {
         return null;
      }
      Target t = this.targets.get(this.primary);
      return t == null ? null : this.resolve(level, t);
   }

   private void resyncTargets(ServerLevel level) {
      if (!this.state().enraged()) {
         return;
      }
      for (Target t : this.targets.values()) {
         if (t.player && !this.notified.contains(t.id)) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(t.id);
            if (p != null && p.level() == level) {
               CoreMessages.sendToPlayer(new Scp096S2CPacket(Scp096S2CPacket.TARGET, this.getId()), p);
               this.notified.add(t.id);
            }
         }
      }
   }

   /** A player logged in again: they get their "you are hunted" state back on the next resync. */
   public static void onLogin(ServerPlayer player) {
      for (Scp096Entity e : loaded()) {
         e.notified.remove(player.getUUID());
      }
   }

   /** Somebody died: whoever they were, they are no longer hunted (a respawned player starts clean). */
   public static void onDeath(LivingEntity dead) {
      if (!(dead.level() instanceof ServerLevel)) {
         return;
      }
      for (Scp096Entity e : loaded()) {
         if (e.targets.containsKey(dead.getUUID()) && e.level() instanceof ServerLevel sl) {
            e.removeTarget(sl, dead.getUUID());
         }
      }
   }

   // ------------------------------------------------------------------ attack and events

   DamageSource attackSource() {
      return new DamageSource(this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE), this);
   }

   void markAttack() {
      this.entityData.set(DATA_ATTACK, this.entityData.get(DATA_ATTACK) + 1);
   }

   void markSmash() {
      this.entityData.set(DATA_SMASH, this.entityData.get(DATA_SMASH) + 1);
   }

   // ------------------------------------------------------------------ persistence

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putString("Scp096State", this.state().name());
      tag.putInt("StateTicks", this.stateTicks);
      tag.putInt("StateLength", this.stateLength);
      tag.putInt("HysteriaTicks", this.hysteriaTicks);
      tag.putBoolean("Natural", this.natural);
      tag.putInt("NextPeek", this.nextPeek);
      if (this.home != null) {
         tag.putDouble("HomeX", this.home.x);
         tag.putDouble("HomeY", this.home.y);
         tag.putDouble("HomeZ", this.home.z);
      }
      if (this.primary != null) {
         tag.putUUID("Primary", this.primary);
      }
      ListTag list = new ListTag();
      for (Target t : this.targets.values()) {
         CompoundTag e = new CompoundTag();
         e.putUUID("Id", t.id);
         e.putBoolean("Player", t.player);
         e.putInt("Missing", t.missing);
         list.add(e);
      }
      tag.put("Targets", list);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      Scp096State s = Scp096State.byName(tag.getString("Scp096State"));
      this.entityData.set(DATA_STATE, (byte)s.id());
      this.stateTicks = tag.getInt("StateTicks");
      this.stateLength = tag.contains("StateLength") ? tag.getInt("StateLength") : 2400;
      this.hysteriaTicks = tag.contains("HysteriaTicks") ? tag.getInt("HysteriaTicks") : Scp096Rules.DEFAULT_HYSTERIA_TICKS;
      this.natural = tag.getBoolean("Natural");
      this.nextPeek = tag.contains("NextPeek") ? tag.getInt("NextPeek") : 240;
      if (tag.contains("HomeX")) {
         this.home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
      }
      this.primary = tag.hasUUID("Primary") ? tag.getUUID("Primary") : null;
      this.targets.clear();
      ListTag list = tag.getList("Targets", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size() && i < 64; i++) {
         CompoundTag e = list.getCompound(i);
         if (e.hasUUID("Id")) {
            Target t = new Target(e.getUUID("Id"), e.getBoolean("Player"));
            t.missing = e.getInt("Missing");
            this.targets.put(t.id, t);
         }
      }
      if (s.enraged() && this.targets.isEmpty()) {
         this.entityData.set(DATA_STATE, (byte)Scp096State.CALMING.id());
         this.stateTicks = 0;
      }
      this.refreshDimensions();
   }

   /** Status line for /scp096 status. */
   public String describe() {
      return String.format(java.util.Locale.ROOT, "%s t=%d targets=%d face=%s hp=%.0f", this.state().name().toLowerCase(java.util.Locale.ROOT),
         this.stateTicks, this.targets.size(), this.faceOpen() ? "open" : "hidden", this.getHealth());
   }

   AABB box() {
      return this.getBoundingBox();
   }
}

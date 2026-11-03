package dev.baranhan.viltrumitecore.t1000;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.CleanDamageSink;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The T-1000 (docs/design/2026-11-06-t1000-design.md). A {@link CitizenEntity} in every respect while disguised —
 * same look seed, same goals, same renderer — with its own mind ({@link T1000Brain}), a liquid-metal body
 * (synced form, arms, action and wounds for the client mesh) and a two-pool damage model ({@link T1000Damage}).
 * Never written into a chunk ({@code noSave}); {@link T1000SavedData} keeps it across unloads and restarts.
 */
public class T1000Entity extends CitizenEntity implements CleanDamageSink {
   private static final EntityDataAccessor<Byte> FORM = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> FORM_START = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> ARMS = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> ARM_R_START = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> ARM_L_START = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.INT);
   /** Bit 0: right morph slow, bit 1: left morph slow, bit 2: heated, bit 3: frozen, bit 4: flowing through. */
   private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> ACTION_START = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<CompoundTag> WOUNDS = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.COMPOUND_TAG);
   private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.STRING);
   /** Hit flash direction for the client ripple: yaw of the last hit (degrees) and its strength 0..255. */
   private static final EntityDataAccessor<Integer> LAST_HIT = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> HIT_TIME = SynchedEntityData.defineId(T1000Entity.class, EntityDataSerializers.INT);

   public static final int FLAG_SLOW_R = 1;
   public static final int FLAG_SLOW_L = 2;
   public static final int FLAG_HEATED = 4;
   public static final int FLAG_FROZEN = 8;
   public static final int FLAG_FLOWING = 16;
   /** Entity events: droplets tear off at the newest wound / everywhere (heavy) / the frozen body shatters. */
   public static final byte EVENT_SPLASH = 101;
   public static final byte EVENT_BURST = 102;
   public static final byte EVENT_SHATTER = 103;
   public static final byte EVENT_RETURN = 104;

   /** Wound parts (the vanilla humanoid bones). */
   public static final int PART_HEAD = 0;
   public static final int PART_BODY = 1;
   public static final int PART_RIGHT_ARM = 2;
   public static final int PART_LEFT_ARM = 3;
   public static final int PART_RIGHT_LEG = 4;
   public static final int PART_LEFT_LEG = 5;

   private float integrity = T1000Damage.MAX_INTEGRITY;
   private float cold;
   private int heatTicks;
   private int sinceHit = 1000;
   private int lastHitTick = -100;
   private float lastHitAmount;
   private boolean vanishing;
   private boolean destroyedRecorded;
   private boolean natural;
   /** Added to the world while a city was active: it belongs to the city and waits there (even an operator spawn). */
   private boolean cityBound;
   @Nullable
   private T1000Brain brain;
   @Nullable
   private GroundPathNavigation chaseNav;
   @Nullable
   private PathNavigation cityNav;
   private boolean chaseMode;
   private int refreshIn = 40;
   private int flowSoundIn;
   /** Flow through a thin obstacle: from, to, ticks left. */
   @Nullable
   private Vec3 flowFrom;
   @Nullable
   private Vec3 flowTo;
   private int flowTicks;
   private int flowLength;
   /** Client-only render memory (droplets, springs); opaque to the common side. */
   @Nullable
   public Object t1000Client;

   public T1000Entity(EntityType<? extends T1000Entity> type, Level level) {
      super(type, level);
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createT1000Attributes() {
      return CitizenEntity.createAttributes()
         .add(Attributes.MAX_HEALTH, T1000Damage.MAX_CORE)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
         .add(Attributes.ATTACK_DAMAGE, T1000Rules.STAB_DAMAGE)
         .add(Attributes.FOLLOW_RANGE, 64.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(FORM, (byte)T1000Form.HUMAN.ordinal());
      this.entityData.define(FORM_START, 0);
      this.entityData.define(ARMS, (byte)0);
      this.entityData.define(ARM_R_START, 0);
      this.entityData.define(ARM_L_START, 0);
      this.entityData.define(FLAGS, (byte)0);
      this.entityData.define(ACTION, (byte)T1000Action.NONE.ordinal());
      this.entityData.define(ACTION_START, 0);
      this.entityData.define(WOUNDS, new CompoundTag());
      this.entityData.define(KIND, T1000Disguises.CITIZEN);
      this.entityData.define(LAST_HIT, 0);
      this.entityData.define(HIT_TIME, -1000);
   }

   @Override
   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(0, new T1000Hunt(this));
      this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
   }

   public T1000Brain brain() {
      T1000Brain b = this.brain;
      if (b == null) {
         b = new T1000Brain(this);
         this.brain = b;
      }
      return b;
   }

   // --- synced state -----------------------------------------------------------------------------------------

   private int now() {
      return (int)this.level().getGameTime();
   }

   public T1000Form form() {
      return T1000Form.of(this.entityData.get(FORM));
   }

   /** Ticks since the form started (client: with the partial tick). */
   public float formAge(float partialTick) {
      return (float)(this.level().getGameTime() - this.entityData.get(FORM_START)) + partialTick;
   }

   public void setForm(T1000Form f) {
      if (this.form() != f) {
         this.entityData.set(FORM, (byte)f.ordinal());
         this.entityData.set(FORM_START, this.now());
      }
   }

   public int arms() {
      return this.entityData.get(ARMS) & 0xFF;
   }

   public T1000Arm rightArm() {
      return T1000Arm.right(this.arms());
   }

   public T1000Arm leftArm() {
      return T1000Arm.left(this.arms());
   }

   public float armAge(boolean right, float partialTick) {
      return (float)(this.level().getGameTime() - this.entityData.get(right ? ARM_R_START : ARM_L_START)) + partialTick;
   }

   /** Morph progress 0..1 of one arm towards its current shape. */
   public float armMorph(boolean right, float partialTick) {
      int a = this.arms();
      T1000Arm before = right ? T1000Arm.rightBefore(a) : T1000Arm.leftBefore(a);
      T1000Arm now = right ? T1000Arm.right(a) : T1000Arm.left(a);
      boolean slow = (this.flags() & (right ? FLAG_SLOW_R : FLAG_SLOW_L)) != 0;
      return T1000Rules.morph(this.armAge(right, partialTick), T1000Rules.morphTicks(before, now, slow));
   }

   /** True once the arm finished flowing into its current weapon. */
   public boolean armReady(boolean right) {
      return (right ? this.rightArm() : this.leftArm()).weapon() && this.armMorph(right, 0.0F) >= 0.999F;
   }

   public void setArm(boolean right, T1000Arm shape, boolean slow) {
      int a = this.arms();
      T1000Arm r = T1000Arm.right(a);
      T1000Arm l = T1000Arm.left(a);
      if ((right ? r : l) == shape) {
         return;
      }
      int packed = right ? T1000Arm.pack(shape, l, r, T1000Arm.leftBefore(a)) : T1000Arm.pack(r, shape, T1000Arm.rightBefore(a), l);
      this.entityData.set(ARMS, (byte)packed);
      this.entityData.set(right ? ARM_R_START : ARM_L_START, this.now());
      this.setFlag(right ? FLAG_SLOW_R : FLAG_SLOW_L, slow);
      if (shape.weapon() && !this.level().isClientSide) {
         this.playSound(T1000Sounds.MORPH.get(), slow ? 0.7F : 0.9F, slow ? 0.85F : 1.05F + this.random.nextFloat() * 0.1F);
      }
   }

   public int flags() {
      return this.entityData.get(FLAGS) & 0xFF;
   }

   private void setFlag(int bit, boolean on) {
      int f = this.flags();
      int n = on ? f | bit : f & ~bit;
      if (n != f) {
         this.entityData.set(FLAGS, (byte)n);
      }
   }

   public T1000Action action() {
      return T1000Action.of(this.entityData.get(ACTION));
   }

   public float actionAge(float partialTick) {
      return (float)(this.level().getGameTime() - this.entityData.get(ACTION_START)) + partialTick;
   }

   /** The action still runs (server: its tick window; client: same numbers). */
   public boolean acting() {
      T1000Action a = this.action();
      return a != T1000Action.NONE && this.actionAge(0.0F) < a.ticks;
   }

   public void setAction(T1000Action a) {
      this.entityData.set(ACTION, (byte)a.ordinal());
      this.entityData.set(ACTION_START, this.now());
   }

   /** Yaw (degrees, world) the last hit came from and its strength 0..1. */
   public float lastHitYaw() {
      return (this.entityData.get(LAST_HIT) >> 8) * 360.0F / 1024.0F;
   }

   public float lastHitStrength() {
      return (this.entityData.get(LAST_HIT) & 0xFF) / 255.0F;
   }

   /** Ticks since the last hit (client ripple). */
   public float hitAge(float partialTick) {
      return (float)(this.level().getGameTime() - this.entityData.get(HIT_TIME)) + partialTick;
   }

   public String disguiseKind() {
      return this.entityData.get(KIND);
   }

   public void setDisguise(String kind, int payload) {
      this.entityData.set(KIND, kind);
      this.setSkin(payload);
   }

   public float integrity() {
      return this.integrity;
   }

   public float cold() {
      return this.cold;
   }

   public boolean natural() {
      return this.natural;
   }

   public void setNatural(boolean n) {
      this.natural = n;
   }

   /** Restores the condition remembered by the world data (a dormant instance coming back). */
   public void restore(float integrity, float core) {
      this.integrity = Mth.clamp(integrity, 10.0F, T1000Damage.MAX_INTEGRITY);
      this.setHealth(Mth.clamp(core, 6.0F, this.getMaxHealth()));
   }

   // --- wounds -----------------------------------------------------------------------------------------------

   /** A wound: part, rest-model position (pixels), size (pixels), start tick, kind (0 hole, 1 cut, 2 burn). */
   public record Wound(int part, float x, float y, float z, float size, int start, int kind) {
   }

   public List<Wound> wounds() {
      CompoundTag tag = this.entityData.get(WOUNDS);
      ListTag list = tag.getList("W", Tag.TAG_COMPOUND);
      List<Wound> out = new ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
         CompoundTag w = list.getCompound(i);
         out.add(new Wound(w.getByte("p"), w.getFloat("x"), w.getFloat("y"), w.getFloat("z"), w.getFloat("s"), w.getInt("t"), w.getByte("k")));
      }
      return out;
   }

   void addWound(Wound wound) {
      List<Wound> all = this.wounds();
      int now = this.now();
      all.removeIf(w -> now - w.start() > T1000Rules.WOUND_CLOSE_TICKS + 20);
      while (all.size() >= T1000Rules.MAX_WOUNDS) {
         all.remove(0);
      }
      all.add(wound);
      ListTag list = new ListTag();
      for (Wound w : all) {
         CompoundTag t = new CompoundTag();
         t.putByte("p", (byte)w.part());
         t.putFloat("x", w.x());
         t.putFloat("y", w.y());
         t.putFloat("z", w.z());
         t.putFloat("s", w.size());
         t.putInt("t", w.start());
         t.putByte("k", (byte)w.kind());
         list.add(t);
      }
      CompoundTag tag = new CompoundTag();
      tag.put("W", list);
      this.entityData.set(WOUNDS, tag);
   }

   /** Where a hit from world point {@code from} lands on the body, as a wound. */
   Wound woundFrom(@Nullable Vec3 from, float size, int kind) {
      Vec3 at = from == null ? this.position().add(this.getLookAngle()).add(0.0, 1.2, 0.0) : from;
      double yaw = Math.toRadians(this.yBodyRot);
      double fx = -Math.sin(yaw);
      double fz = Math.cos(yaw);
      double rx = -Math.cos(yaw);
      double rz = -Math.sin(yaw);
      double dx = at.x - this.getX();
      double dz = at.z - this.getZ();
      double lat = dx * rx + dz * rz;
      double fwd = dx * fx + dz * fz;
      double dist = Math.sqrt(lat * lat + fwd * fwd);
      if (dist > 0.3) {
         // a hit from afar: the point where that direction meets the body
         lat *= 0.3 / dist;
         fwd *= 0.3 / dist;
      }
      float h = Mth.clamp((float)(at.y - this.getY()), 0.05F, T1000Rules.HEIGHT - 0.02F);
      float my = 24.0F - h / T1000Rules.HEIGHT * 32.0F;
      return T1000WoundMath.onSurface(-lat * 16.0, my, -fwd * 16.0, size, this.now(), kind, this.random.nextFloat());
   }

   // --- navigation ---------------------------------------------------------------------------------------------

   /** Chase mode: door-capable ground navigation instead of the pedestrian grid. */
   public void setChaseMode(boolean on) {
      if (on == this.chaseMode) {
         return;
      }
      if (this.cityNav == null) {
         this.cityNav = this.navigation;
      }
      if (this.chaseNav == null) {
         GroundPathNavigation n = new GroundPathNavigation(this, this.level());
         n.setCanOpenDoors(true);
         n.setCanPassDoors(true);
         n.setCanFloat(true);
         this.chaseNav = n;
      }
      this.navigation.stop();
      this.navigation = on ? this.chaseNav : this.cityNav;
      this.chaseMode = on;
   }

   public boolean chaseMode() {
      return this.chaseMode;
   }

   /** Squeeze through a thin obstacle to {@code to} over {@code ticks} ticks (no collision meanwhile). */
   public void flowThrough(Vec3 to, int ticks) {
      this.flowFrom = this.position();
      this.flowTo = to;
      this.flowLength = Math.max(4, ticks);
      this.flowTicks = this.flowLength;
      this.noPhysics = true;
      this.getNavigation().stop();
      this.setFlag(FLAG_FLOWING, true);
      this.setAction(T1000Action.FLOW);
      this.playSound(T1000Sounds.STRETCH.get(), 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
   }

   public boolean flowing() {
      return this.flowTicks > 0;
   }

   private void tickFlow() {
      if (this.flowTicks <= 0 || this.flowFrom == null || this.flowTo == null) {
         return;
      }
      this.flowTicks--;
      float t = 1.0F - (float)this.flowTicks / this.flowLength;
      float s = T1000Rules.smooth(t);
      Vec3 p = this.flowFrom.lerp(this.flowTo, s);
      this.setDeltaMovement(Vec3.ZERO);
      this.setPos(p.x, p.y, p.z);
      if (this.flowTicks == 0) {
         this.noPhysics = false;
         this.setFlag(FLAG_FLOWING, false);
         this.flowFrom = null;
         this.flowTo = null;
      }
   }

   // --- citizen seams ------------------------------------------------------------------------------------------

   @Override
   protected boolean citizenMind() {
      return this.form() == T1000Form.HUMAN && (this.level().isClientSide || this.brain().state() == T1000State.DISGUISE);
   }

   @Override
   protected boolean discardWhenStuck() {
      return false;
   }

   @Override
   protected boolean cityTracked() {
      return false;
   }

   /**
    * Lost outside the walk grid (after a chase, say): never cleaned up like a passer-by (that was counted as a
    * destruction). Unseen and calm, it slips away (dormant) and later returns at a city site near a player.
    */
   @Override
   protected void lostOffGrid() {
      if (this.level() instanceof ServerLevel sl && this.form() == T1000Form.HUMAN && this.brain().state() == T1000State.DISGUISE
         && this.brain().unobserved(sl, 32.0)) {
         this.vanish();
      }
   }

   @Override
   public boolean capturable() {
      return false;
   }

   @Override
   public boolean isBystander() {
      return this.form() == T1000Form.HUMAN && this.isAlive();
   }

   /** Citizen state for the shared walking/idle animation while it hunts in disguise. */
   void citizenPose(CitizenState s) {
      this.setCitizenState(s);
   }

   @Override
   protected Component getTypeName() {
      // in disguise it is "a citizen" everywhere a name is shown (death messages, subtitles of hurt sounds)
      return this.form() == T1000Form.HUMAN ? ViltrumiteEntities.CITIZEN.get().getDescription() : super.getTypeName();
   }

   // --- tick ---------------------------------------------------------------------------------------------------

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }
      if (!this.isAlive()) {
         return;
      }
      this.sinceHit++;
      if (this.heatTicks > 0) {
         this.heatTicks--;
      }
      if (this.isOnFire() || this.isInLava()) {
         this.heatTicks = Math.max(this.heatTicks, 40);
      }
      this.setFlag(FLAG_HEATED, this.heatTicks > 0);
      if (this.isFullyFrozen()) {
         this.cold = Math.min(1.6F, this.cold + 0.02F);
      }
      this.cold = T1000Damage.thaw(this.cold);
      this.setFlag(FLAG_FROZEN, this.cold >= T1000Damage.FROZEN);
      T1000Form f = this.form();
      boolean puddle = f == T1000Form.PUDDLE || f == T1000Form.MELTING;
      float before = this.integrity;
      this.integrity = T1000Damage.regen(this.integrity, this.sinceHit, puddle, this.heatTicks > 0, this.cold);
      if (this.integrity > before + 0.05F && this.tickCount % 30 == 0 && f.revealed() && !puddle) {
         this.playSound(T1000Sounds.REGEN.get(), 0.25F, 0.9F + this.random.nextFloat() * 0.2F);
      }
      float coreRegen = T1000Damage.coreRegen(f == T1000Form.HUMAN, this.sinceHit);
      if (coreRegen > 0.0F && this.getHealth() < this.getMaxHealth()) {
         this.heal(coreRegen);
      }
      this.tickForm(f);
      this.tickFlow();
      if (this.isNoAi()) {
         // a control stun (cage, freeze) or an explicit NoAI holds the whole body, brain included
         this.brain().onDisabled();
      } else {
         this.brain().tick();
      }
      this.tickSounds(f);
      if (--this.refreshIn <= 0) {
         this.refreshIn = 100;
         if (this.level() instanceof ServerLevel sl) {
            T1000SavedData.get(sl.getServer()).refresh(this);
         }
      }
   }

   private void tickForm(T1000Form f) {
      float age = this.formAge(0.0F);
      switch (f) {
         case REVEALING -> {
            if (age >= T1000Rules.REVEAL_TICKS) {
               this.setForm(T1000Form.LIQUID);
            }
         }
         case CONCEALING -> {
            if (age >= T1000Rules.CONCEAL_TICKS) {
               this.setForm(T1000Form.HUMAN);
               this.entityData.set(WOUNDS, new CompoundTag());
            }
         }
         case MELTING -> {
            if (age >= T1000Rules.MELT_TICKS) {
               this.setForm(T1000Form.PUDDLE);
               this.refreshDimensions();
            }
         }
         case PUDDLE -> {
            if (age >= T1000Rules.PUDDLE_TICKS && this.integrity >= 45.0F || age >= T1000Rules.PUDDLE_TICKS * 3) {
               this.setForm(T1000Form.REFORMING);
               this.playSound(T1000Sounds.REASSEMBLE.get(), 1.0F, 1.0F);
            }
         }
         case REFORMING -> {
            if (age >= T1000Rules.REFORM_TICKS) {
               this.setForm(T1000Form.LIQUID);
               this.refreshDimensions();
               this.setAction(T1000Action.SHAKE);
               this.level().broadcastEntityEvent(this, EVENT_RETURN);
            }
         }
         default -> {
         }
      }
   }

   private void tickSounds(T1000Form f) {
      if (!f.revealed() || f == T1000Form.DYING) {
         return;
      }
      double v = this.getDeltaMovement().horizontalDistanceSqr();
      if (--this.flowSoundIn <= 0 && (v > 0.004 || f == T1000Form.PUDDLE && v > 0.0005)) {
         this.flowSoundIn = 14 + this.random.nextInt(10);
         this.playSound(T1000Sounds.FLOW.get(), f == T1000Form.PUDDLE ? 0.5F : 0.3F, 0.85F + this.random.nextFloat() * 0.3F);
      }
   }

   /** Puddle hitbox while melted. */
   @Override
   public EntityDimensions getDimensions(Pose pose) {
      T1000Form f = this.form();
      if (f == T1000Form.PUDDLE || f == T1000Form.MELTING && this.formAge(0.0F) > T1000Rules.MELT_TICKS * 0.6F) {
         return EntityDimensions.scalable(T1000Rules.PUDDLE_WIDTH, T1000Rules.PUDDLE_HEIGHT);
      }
      return super.getDimensions(pose);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (FORM.equals(key)) {
         this.refreshDimensions();
      }
   }

   // --- damage -------------------------------------------------------------------------------------------------

   static T1000Damage.Kind kindOf(DamageSource source, float amount) {
      if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) {
         return T1000Damage.Kind.ABSOLUTE;
      }
      if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.STARVE)
         || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRY_OUT)
         || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.FLY_INTO_WALL)) {
         return T1000Damage.Kind.IMMUNE;
      }
      if (source.is(DamageTypeTags.IS_FREEZING)) {
         return T1000Damage.Kind.COLD;
      }
      if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_LIGHTNING)) {
         return T1000Damage.Kind.HEAT;
      }
      if (source.is(DamageTypeTags.IS_EXPLOSION) || amount >= T1000Damage.HEAVY_HIT) {
         return T1000Damage.Kind.HEAVY;
      }
      return T1000Damage.Kind.NORMAL;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return super.isInvulnerableTo(source) || kindOf(source, 0.0F) == T1000Damage.Kind.IMMUNE;
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      if (distance > 4.0F && this.form().revealed() && !this.level().isClientSide) {
         this.playSound(T1000Sounds.SPLASH.get(), 0.6F, 1.3F);
      }
      return false;
   }

   @Override
   public boolean canBeAffected(MobEffectInstance effect) {
      // no biology: only what moves or marks a body
      return effect.getEffect() == MobEffects.MOVEMENT_SLOWDOWN || effect.getEffect() == MobEffects.LEVITATION || effect.getEffect() == MobEffects.GLOWING;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.isInvulnerableTo(source) || !this.isAlive() || this.isDeadOrDying()) {
         return false;
      }
      if (this.form() == T1000Form.DYING) {
         return false;
      }
      T1000Damage.Kind kind = kindOf(source, amount);
      // own invulnerability window: a stronger hit inside it only counts the difference (vanilla rule)
      float effective = amount;
      boolean inWindow = this.tickCount - this.lastHitTick < 10;
      if (inWindow && kind != T1000Damage.Kind.ABSOLUTE) {
         if (amount <= this.lastHitAmount) {
            return false;
         }
         effective = amount - this.lastHitAmount;
      }
      Entity attacker = source.getEntity();
      if (attacker instanceof T1000Entity) {
         return false;
      }
      if (kind != T1000Damage.Kind.ABSOLUTE && this.brain().tryParry(source, attacker, effective, kind)) {
         return false;
      }
      boolean puddle = this.form() == T1000Form.PUDDLE || this.form() == T1000Form.MELTING;
      T1000Damage.Split split = T1000Damage.split(effective, kind, this.integrity, this.cold, puddle, this.getMaxHealth());
      if (!inWindow) {
         this.lastHitTick = this.tickCount;
         this.lastHitAmount = amount;
      } else {
         this.lastHitAmount = Math.max(amount, this.lastHitAmount);
      }
      this.sinceHit = 0;
      this.integrity = Math.max(0.0F, this.integrity - split.integrity());
      this.cold = Math.max(0.0F, this.cold + split.cold());
      if (kind == T1000Damage.Kind.HEAT) {
         this.heatTicks = Math.max(this.heatTicks, 60);
      }
      this.reactToHit(source, attacker, effective, kind, split);
      // the core part goes through the vanilla chain (events, knockback, combat tracker, death)
      this.invulnerableTime = 0;
      boolean hurt = super.hurt(source, Math.max(0.01F, split.core()));
      if (this.isAlive() && this.integrity <= 0.0F && !puddle && this.form() != T1000Form.DYING) {
         this.melt();
      }
      return hurt || split.integrity() > 0.0F;
   }

   /**
    * Clean hero damage (Mutilation, drains, deferred control damage) skips {@code hurt()}; it still
    * goes through the liquid-metal model: integrity first, the capped core part, wounds and the brain.
    * No parry and no invulnerability window: clean damage is not a blow that can be met.
    */
   @Override
   public void cleanDamage(DamageSource source, float amount) {
      if (this.level().isClientSide || amount <= 0.0F || this.isInvulnerableTo(source) || !this.isAlive() || this.isDeadOrDying()
         || this.form() == T1000Form.DYING) {
         return;
      }
      T1000Damage.Kind kind = kindOf(source, amount);
      Entity attacker = source.getEntity();
      if (attacker instanceof T1000Entity) {
         return;
      }
      boolean puddle = this.form() == T1000Form.PUDDLE || this.form() == T1000Form.MELTING;
      T1000Damage.Split split = T1000Damage.split(amount, kind, this.integrity, this.cold, puddle, this.getMaxHealth());
      this.sinceHit = 0;
      this.integrity = Math.max(0.0F, this.integrity - split.integrity());
      this.cold = Math.max(0.0F, this.cold + split.cold());
      if (kind == T1000Damage.Kind.HEAT) {
         this.heatTicks = Math.max(this.heatTicks, 60);
      }
      this.reactToHit(source, attacker, amount, kind, split);
      float core = Math.max(0.0F, split.core());
      if (core > 0.0F) {
         float health = this.getHealth() - core;
         if (health <= 0.0F) {
            this.setHealth(0.0F);
            this.die(source);
            return;
         }
         this.setHealth(health);
      }
      if (this.isAlive() && this.integrity <= 0.0F && !puddle && this.form() != T1000Form.DYING) {
         this.melt();
      }
   }

   private void reactToHit(DamageSource source, @Nullable Entity attacker, float amount, T1000Damage.Kind kind, T1000Damage.Split split) {
      Vec3 from = source.getSourcePosition();
      if (from == null && attacker != null) {
         from = attacker.getEyePosition();
      }
      double dx = from == null ? 0.0 : from.x - this.getX();
      double dz = from == null ? 1.0 : from.z - this.getZ();
      int yaw = Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(-dx, dz)) * 1024.0 / 360.0), 1024);
      int strength = Mth.clamp((int)(amount / 20.0F * 255.0F) + 40, 0, 255);
      this.entityData.set(LAST_HIT, yaw << 8 | strength);
      this.entityData.set(HIT_TIME, this.now());
      boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() != null && source.getDirectEntity() != attacker;
      int woundKind = kind == T1000Damage.Kind.HEAT ? 2 : projectile ? 0 : 1;
      if (!projectile && from != null) {
         // a blow lands on the torso or the arms, not where the attacker's eyes are
         from = new Vec3(from.x, this.getY() + 0.75 + this.random.nextDouble() * 0.6, from.z);
      }
      Wound w = this.woundFrom(projectile && source.getDirectEntity() != null ? source.getDirectEntity().position() : from,
         projectile ? 1.4F : Mth.clamp(amount * 0.25F, 1.2F, 3.2F), woundKind);
      if (kind != T1000Damage.Kind.COLD) {
         this.addWound(w);
      }
      T1000Form f = this.form();
      if (split.shatter()) {
         this.playSound(T1000Sounds.SHATTER.get(), 1.4F, 1.0F);
         this.level().broadcastEntityEvent(this, EVENT_SHATTER);
         this.integrity = 0.0F;
         return;
      }
      if (f.revealed() && f != T1000Form.CONCEALING) {
         if (kind == T1000Damage.Kind.COLD) {
            // frost biting into frozen metal creaks; it does not splash like a shot
            if (this.cold >= T1000Damage.FROZEN) {
               this.playSound(T1000Sounds.CRACK.get(), 0.45F, 1.15F + this.random.nextFloat() * 0.2F);
            }
         } else if (kind == T1000Damage.Kind.HEAT) {
            // heated metal runs and sloshes
            this.playSound(T1000Sounds.FLOW.get(), 0.6F, 0.7F + this.random.nextFloat() * 0.1F);
         } else {
            this.playSound(T1000Sounds.IMPACT.get(), 0.9F, 0.9F + this.random.nextFloat() * 0.25F);
         }
      }
      if (f.grounded()) {
         this.level().broadcastEntityEvent(this, EVENT_SPLASH);
         return;
      }
      T1000Action running = this.action();
      boolean busy = this.acting() && (running.attack() || running == T1000Action.LEAP || running == T1000Action.FLOW || running.interrupts());
      if (projectile && w.part() == PART_HEAD && f.revealed()) {
         this.setAction(T1000Action.FACE_SPLIT);
         this.level().broadcastEntityEvent(this, EVENT_SPLASH);
      } else if (split.heavy()) {
         this.setAction(T1000Action.HEAVY);
         this.playSound(T1000Sounds.DEFORM.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.15F);
         this.level().broadcastEntityEvent(this, EVENT_BURST);
      } else if (kind == T1000Damage.Kind.HEAT) {
         if (!this.acting() || this.action() != T1000Action.BURN) {
            this.setAction(T1000Action.BURN);
         }
      } else if (kind == T1000Damage.Kind.COLD) {
         if (this.cold >= T1000Damage.FROZEN && this.action() != T1000Action.FREEZE) {
            this.setAction(T1000Action.FREEZE);
            this.playSound(T1000Sounds.CRACK.get(), 0.9F, 1.0F);
         }
      } else if (!busy) {
         this.setAction(T1000Action.HIT);
         if (f.revealed()) {
            this.level().broadcastEntityEvent(this, EVENT_SPLASH);
         }
      }
      this.brain().onHurt(attacker, amount, kind);
   }

   /** Integrity gone: collapse into a puddle (not a death). */
   public void melt() {
      this.integrity = 0.0F;
      this.setForm(T1000Form.MELTING);
      this.setAction(T1000Action.NONE);
      this.setArm(true, T1000Arm.HAND, false);
      this.setArm(false, T1000Arm.HAND, false);
      this.getNavigation().stop();
      this.playSound(T1000Sounds.SPLASH.get(), 1.2F, 0.9F);
      this.level().broadcastEntityEvent(this, EVENT_BURST);
      this.brain().onMelt();
   }

   @Override
   public boolean isPushable() {
      return !this.form().grounded() && super.isPushable();
   }

   @Override
   public void die(DamageSource source) {
      if (this.isRemoved() || this.dead) {
         return;
      }
      super.die(source);
      if (!this.level().isClientSide) {
         this.setForm(T1000Form.DYING);
         this.setAction(T1000Action.NONE);
         this.getNavigation().stop();
         this.playSound(T1000Sounds.DESTROY.get(), 1.6F, 1.0F);
         this.level().broadcastEntityEvent(this, EVENT_BURST);
         this.recordDestroyed();
         this.brain().onDestroyed();
      }
   }

   private void recordDestroyed() {
      if (!this.destroyedRecorded && this.level() instanceof ServerLevel sl) {
         this.destroyedRecorded = true;
         T1000SavedData.get(sl.getServer()).destroyed(this.getUUID(), sl.getServer().overworld().getGameTime());
      }
   }

   /** Long disintegration instead of the vanilla tip-over and poof. */
   @Override
   protected void tickDeath() {
      this.deathTime++;
      if (!this.level().isClientSide) {
         if (this.deathTime == T1000Rules.DEATH_MELT_START) {
            this.playSound(T1000Sounds.SPLASH.get(), 1.0F, 0.7F);
         }
         if (this.deathTime >= T1000Rules.DEATH_TICKS && !this.isRemoved()) {
            this.remove(RemovalReason.KILLED);
         }
      }
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == EVENT_SPLASH || id == EVENT_BURST || id == EVENT_SHATTER || id == EVENT_RETURN) {
         T1000ClientHooks.event(this, id);
         return;
      }
      super.handleEntityEvent(id);
   }

   // --- sounds of the two faces --------------------------------------------------------------------------------

   @Nullable
   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return this.form() == T1000Form.HUMAN ? SoundEvents.PLAYER_HURT : null;
   }

   @Nullable
   @Override
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      if (this.form().revealed()) {
         this.playSound(T1000Sounds.STEP.get(), 0.35F, 0.9F + this.random.nextFloat() * 0.2F);
      } else {
         super.playStepSound(pos, state);
      }
   }

   @Override
   public SoundSource getSoundSource() {
      return this.form().revealed() ? SoundSource.HOSTILE : super.getSoundSource();
   }

   // --- lifecycle ----------------------------------------------------------------------------------------------

   @Override
   public boolean canChangeDimensions() {
      return false;
   }

   @Override
   public boolean canBeLeashed(Player player) {
      return false;
   }

   /** Slips away (far from every player): the world data keeps it, it comes back near a player later. */
   public void vanish() {
      if (this.level() instanceof ServerLevel sl) {
         this.vanishing = true;
         T1000SavedData.get(sl.getServer()).sleep(this, sl.getServer().overworld().getGameTime());
         this.discard();
      }
   }

   /** Operator removal: forget it entirely (no respawn delay). */
   public void removeByOperator() {
      if (this.level() instanceof ServerLevel sl) {
         this.vanishing = true;
         T1000SavedData.get(sl.getServer()).forget(this.getUUID());
         this.discard();
      }
   }

   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (this.level() instanceof ServerLevel sl) {
         this.cityBound |= CityManager.of(sl) != null;
         T1000SavedData.get(sl.getServer()).touch(this, this.natural);
      }
   }

   @Override
   public void onRemovedFromWorld() {
      super.onRemovedFromWorld();
      if (!(this.level() instanceof ServerLevel sl) || this.vanishing) {
         return;
      }
      T1000SavedData data = T1000SavedData.get(sl.getServer());
      RemovalReason r = this.getRemovalReason();
      long now = sl.getServer().overworld().getGameTime();
      if (r == RemovalReason.KILLED || this.dead) {
         this.recordDestroyed();
      } else if (r == RemovalReason.DISCARDED) {
         // erased by something else (a snap, an erasure, /kill of another mod): that was its end
         this.recordDestroyed();
      } else if (data.instance(this.getUUID()) != null) {
         // chunk unloaded or the server stops: it waits, never keeping a chunk loaded. The city manager is already
         // gone when the server stops (ServerStoppingEvent comes first), so remember whether it lived in a city.
         if (this.natural || this.cityBound || CityManager.of(sl) != null) {
            data.sleep(this, now);
         } else {
            data.forget(this.getUUID());
         }
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putFloat("Integrity", this.integrity);
      tag.putBoolean("Natural", this.natural);
      tag.putString("Kind", this.disguiseKind());
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("Integrity")) {
         this.integrity = tag.getFloat("Integrity");
      }
      this.natural = tag.getBoolean("Natural");
      if (tag.contains("Kind")) {
         this.entityData.set(KIND, tag.getString("Kind"));
      }
   }

   /** A body can see this point (attack line of sight helper). */
   boolean near(LivingEntity e, double reach) {
      AABB box = this.getBoundingBox().inflate(reach);
      return box.intersects(e.getBoundingBox());
   }

   /** The players of this level that count for the AI (not spectators, not creative). */
   List<ServerPlayer> huntablePlayers() {
      List<ServerPlayer> out = new ArrayList<>();
      if (this.level() instanceof ServerLevel sl) {
         for (ServerPlayer p : sl.players()) {
            if (T1000Brain.huntable(p)) {
               out.add(p);
            }
         }
      }
      return out;
   }
}

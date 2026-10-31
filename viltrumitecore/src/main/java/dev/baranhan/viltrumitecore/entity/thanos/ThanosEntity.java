package dev.baranhan.viltrumitecore.entity.thanos;

import dev.baranhan.viltrumitecore.hero.AdminKill;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosDamageCap;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTrail;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Thanos of the six-stones event (never saved, one per server). The body only walks, turns and
 * takes damage; every decision and move is the event's ({@code worldevent.thanos.ThanosEvent}),
 * so a stopped entity (DIO's time stop, a world pause, a hero's freeze) simply does nothing.
 * Health loss of any path goes through {@link #setHealth}: the per-hit and per-second caps hold
 * against one-shot abilities; operators can still end him ({@code /kill}, AdminKill).
 */
public class ThanosEntity extends PathfinderMob implements Enemy {
   public static final byte STATE_INTRO = 0;
   public static final byte STATE_FIGHT = 1;
   public static final byte STATE_VICTORY = 2;
   public static final byte STATE_SNAP = 3;
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(ThanosEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> MOVE = SynchedEntityData.defineId(ThanosEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> MOVE_SERIAL = SynchedEntityData.defineId(ThanosEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(ThanosEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(ThanosEntity.class, EntityDataSerializers.INT);
   /** Short reactions shown by the client: hurt flinch, stun stagger. */
   private static final EntityDataAccessor<Byte> REACTION = SynchedEntityData.defineId(ThanosEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> REACTION_SERIAL = SynchedEntityData.defineId(ThanosEntity.class, EntityDataSerializers.INT);
   public static final byte REACT_HURT = 1;
   /** Blocks walked per gait cycle (two steps of a 3.75-block titan). */
   public static final double STRIDE = 3.4;
   public static final byte REACT_STUN = 2;

   private ThanosDamageCap cap;
   private final ThanosTrail trail = new ThanosTrail(ThanosRules.REWIND_BACK + 2);
   /** Heroes' scripted-motion guard: only the event moves him. */
   private boolean scripted;
   private boolean defeated;
   private int threatTicks;
   private int lastHurtReact;

   // --- client presentation clocks (client tickCount based) ---
   public int clientMoveStart = -1000;
   public int clientStateStart;
   public int clientReactStart = -1000;
   public float clientWalk;
   public float clientWalkO;
   /** Gait cycles walked (one cycle = two steps), for the walk clips and the footfalls. */
   public double clientGait;
   public double clientGaitO;
   /** Smoothed body turn, degrees per tick (+ = turning right). */
   public float clientTurn;

   public ThanosEntity(EntityType<? extends ThanosEntity> type, Level level) {
      super(type, level);
      this.cap = new ThanosDamageCap();
      this.setMaxUpStep(1.6F);
      this.setPersistenceRequired();
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, ThanosRules.BASE_HEALTH)
         .add(Attributes.ARMOR, 10.0)
         .add(Attributes.ARMOR_TOUGHNESS, 8.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.MOVEMENT_SPEED, ThanosRules.WALK_SPEED)
         .add(Attributes.FOLLOW_RANGE, 96.0)
         .add(Attributes.ATTACK_DAMAGE, 16.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STATE, STATE_INTRO);
      this.entityData.define(MOVE, (byte)-1);
      this.entityData.define(MOVE_SERIAL, 0);
      this.entityData.define(PHASE, (byte)0);
      this.entityData.define(TARGET, -1);
      this.entityData.define(REACTION, (byte)0);
      this.entityData.define(REACTION_SERIAL, 0);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (this.level().isClientSide()) {
         if (MOVE_SERIAL.equals(key)) {
            this.clientMoveStart = this.tickCount;
         } else if (STATE.equals(key)) {
            this.clientStateStart = this.tickCount;
         } else if (REACTION_SERIAL.equals(key)) {
            this.clientReactStart = this.tickCount;
         }
      }
   }

   // ---- synced state ----------------------------------------------------------------------------
   public byte state() {
      return this.entityData.get(STATE);
   }

   public void setState(byte state) {
      this.entityData.set(STATE, state);
   }

   public boolean fighting() {
      return this.state() == STATE_FIGHT;
   }

   @Nullable
   public ThanosMove move() {
      return ThanosMove.byId(this.entityData.get(MOVE));
   }

   public void startMove(@Nullable ThanosMove move) {
      this.entityData.set(MOVE, (byte)(move == null ? -1 : move.ordinal()));
      this.entityData.set(MOVE_SERIAL, this.entityData.get(MOVE_SERIAL) + 1);
   }

   public ThanosPhase phase() {
      return ThanosPhase.byIndex(this.entityData.get(PHASE));
   }

   public void setPhase(ThanosPhase phase) {
      this.entityData.set(PHASE, (byte)phase.ordinal());
   }

   public int targetId() {
      return this.entityData.get(TARGET);
   }

   public void setTargetId(int id) {
      this.entityData.set(TARGET, id);
   }

   public byte reaction() {
      return this.entityData.get(REACTION);
   }

   public void react(byte reaction) {
      this.entityData.set(REACTION, reaction);
      this.entityData.set(REACTION_SERIAL, this.entityData.get(REACTION_SERIAL) + 1);
   }

   // ---- damage -----------------------------------------------------------------------------------
   public ThanosDamageCap damageCap() {
      return this.cap;
   }

   public ThanosTrail trail() {
      return this.trail;
   }

   /** The health reached 0 through the caps (or an operator ended him): the heroes won. */
   public boolean defeated() {
      return this.defeated;
   }

   /** A strong ability hit him within the last second. */
   public boolean threatened() {
      return this.threatTicks > 0;
   }

   private static boolean adminDamage(@Nullable DamageSource source) {
      return AdminKill.active() || source != null && (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.FELL_OUT_OF_WORLD));
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide()) {
         return false;
      }

      boolean admin = adminDamage(source);
      if (!admin && (!this.fighting() || this.isInvulnerableTo(source))) {
         return false;
      }

      float before = this.getHealth();
      boolean hurt;
      if (admin) {
         this.scripted = true;
         try {
            hurt = super.hurt(source, amount);
         } finally {
            this.scripted = false;
         }
      } else {
         hurt = super.hurt(source, amount);
      }

      float lost = before - this.getHealth();
      if (lost >= ThanosRules.THREAT_HIT * this.getMaxHealth()) {
         this.threatTicks = ThanosRules.TPS;
      }

      if (lost > 0.0F && this.tickCount - this.lastHurtReact > 30) {
         this.lastHurtReact = this.tickCount;
         this.react(REACT_HURT);
      }

      return hurt;
   }

   @Override
   public void setHealth(float health) {
      if (this.cap == null || this.level().isClientSide() || this.scripted || AdminKill.active()) {
         super.setHealth(health);
         return;
      }

      float current = this.getHealth();
      if (!(health < current)) {
         super.setHealth(health);
         return;
      }

      if (!this.fighting()) {
         return;
      }

      float loss = this.cap.allow(current - health, this.getMaxHealth());
      this.cap.record(loss);
      super.setHealth(current - loss);
   }

   /** Healing from the stones (Soul drain, Time Rewind): never above the max. */
   public void stoneHeal(float amount) {
      if (amount > 0.0F) {
         super.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + amount));
      }
   }

   /** Scales the max health keeping the fraction (more heroes joined early). */
   public void scaleMaxHealth(float max) {
      float fraction = this.getMaxHealth() <= 0.0F ? 1.0F : this.getHealth() / this.getMaxHealth();
      var attribute = this.getAttribute(Attributes.MAX_HEALTH);
      if (attribute != null) {
         attribute.setBaseValue(max);
      }

      super.setHealth(Math.max(1.0F, max * fraction));
   }

   @Override
   public void die(DamageSource source) {
      if (this.level().isClientSide()) {
         return;
      }

      if (this.getHealth() > 0.0F && !adminDamage(source)) {
         return;
      }

      // No vanilla death: the event plays the defeat and removes him.
      this.defeated = true;
      super.setHealth(1.0F);
      this.dead = false;
      this.deathTime = 0;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
         || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.FLY_INTO_WALL)
         || source.is(DamageTypes.STALAGMITE) || super.isInvulnerableTo(source);
   }

   // ---- body ----------------------------------------------------------------------------------------
   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide()) {
         this.cap.tick();
         this.trail.push(this.getX(), this.getY(), this.getZ());
         if (this.threatTicks > 0) {
            this.threatTicks--;
         }
      } else {
         this.clientWalkO = this.clientWalk;
         double dx = this.getX() - this.xo;
         double dz = this.getZ() - this.zo;
         double moved = Math.sqrt(dx * dx + dz * dz);
         float speed = (float)Math.min(1.0, moved * 6.0);
         this.clientWalk += (speed - this.clientWalk) * 0.25F;
         this.clientGaitO = this.clientGait;
         this.clientGait += Math.min(0.5, moved) / STRIDE;
         float turn = net.minecraft.util.Mth.wrapDegrees(this.yBodyRot - this.yBodyRotO);
         this.clientTurn += (turn - this.clientTurn) * 0.3F;
      }
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (!this.level().isClientSide() && !this.scripted) {
         // Heroes' pushes barely move him: a titan, not a ragdoll (his own motion is scripted).
         Vec3 v = this.getDeltaMovement();
         double h = v.x * v.x + v.z * v.z;
         if (h > 0.64) {
            double k = 0.8 / Math.sqrt(h);
            this.setDeltaMovement(v.x * k, Math.min(v.y, 0.6), v.z * k);
         }
      }
   }

   /** Event-driven motion (lunges, steps): bypasses the push guard for this call. */
   public void scriptedMotion(Vec3 velocity) {
      this.scripted = true;
      try {
         this.setDeltaMovement(velocity);
         this.hasImpulse = true;
      } finally {
         this.scripted = false;
      }
   }

   @Override
   public void knockback(double strength, double x, double z) {
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
   public boolean canBeLeashed(Player player) {
      return false;
   }

   @Override
   protected boolean canRide(Entity vehicle) {
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
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
   }

   @Override
   public boolean shouldDropExperience() {
      return false;
   }

   @Override
   protected boolean shouldDropLoot() {
      return false;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return other == this || super.isAlliedTo(other);
   }

   /** Server: the event drives the look; he faces his target. */
   public void faceTowards(Vec3 at, float maxTurn) {
      double dx = at.x - this.getX();
      double dz = at.z - this.getZ();
      if (dx * dx + dz * dz < 1.0E-4) {
         return;
      }

      float yaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
      float turned = net.minecraft.util.Mth.approachDegrees(this.getYRot(), yaw, maxTurn);
      this.setYRot(turned);
      this.setYBodyRot(turned);
      this.setYHeadRot(turned);
   }
}

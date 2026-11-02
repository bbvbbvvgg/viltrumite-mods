package dev.baranhan.viltrumitecore.entity.thanos;

import dev.baranhan.viltrumitecore.entity.Bystander;
import dev.baranhan.viltrumitecore.hero.AdminKill;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWar;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.OrderBrain;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.OrderMove;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A member of the Black Order (THANOS 2.0): Ebony Maw or Cull Obsidian. Brought in by the war
 * ({@code ThanosArmy}) on the Sanctuary II's beam and only alive while that war runs (an orphan
 * leaves at once; never saved). Each has its own brain ({@link OrderBrain}): moves with a readable
 * telegraph, an active part and a recovery, cooldowns and no repeats. Their max health scales with
 * the heroes past vanilla's 1024 clamp ({@code ThanosMaxHealthMixin}); one hit takes at most
 * {@link ThanosWarRules#ORDER_HIT_CAP} of it. Thanos, the Chitauri and each other never hurt them.
 */
public abstract class BlackOrderEntity extends PathfinderMob implements Enemy, ThanosAlly {
   public static final byte STATE_BEAM_IN = 0, STATE_FIGHT = 1, STATE_BEAM_OUT = 2;
   public static final byte REACT_HURT = 1, REACT_STAGGER = 2;
   public static final int FLAG_ENRAGED = 1, FLAG_GUARD = 2, FLAG_STAND_DOWN = 4;
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> MOVE = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> MOVE_SERIAL = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> REACTION = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> REACTION_SERIAL = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> MAX_HEALTH = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(BlackOrderEntity.class, EntityDataSerializers.BYTE);

   @Nullable
   protected ThanosWar war;
   @Nullable
   protected LivingEntity target;
   protected int moveTick;
   protected int stateTicks;
   protected int retarget;
   protected int stagger;
   private boolean scripted;
   private int lastHurtReact = -100;
   /** Client: tick the current move / state / reaction started (animation clocks). */
   public int clientMoveStart;
   public int clientStateStart;
   public int clientReactStart;
   public float clientWalk;
   public float clientWalkO;
   public float clientGait;
   public float clientGaitO;

   protected BlackOrderEntity(EntityType<? extends BlackOrderEntity> type, Level level) {
      super(type, level);
      this.setPersistenceRequired();
      this.xpReward = 0;
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STATE, STATE_BEAM_IN);
      this.entityData.define(MOVE, (byte)-1);
      this.entityData.define(MOVE_SERIAL, 0);
      this.entityData.define(TARGET, -1);
      this.entityData.define(REACTION, (byte)0);
      this.entityData.define(REACTION_SERIAL, 0);
      this.entityData.define(MAX_HEALTH, 100.0F);
      this.entityData.define(FLAGS, (byte)0);
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

   // ---- synced state ------------------------------------------------------------------------------
   /** The synced max ({@code ThanosMaxHealthMixin} answers {@code getMaxHealth()} from it). */
   public float orderMaxHealth() {
      return this.entityData.get(MAX_HEALTH);
   }

   public void scaleMaxHealth(float max) {
      float fraction = this.getMaxHealth() <= 0.0F ? 1.0F : this.getHealth() / this.getMaxHealth();
      this.entityData.set(MAX_HEALTH, Math.max(1.0F, max));
      this.scripted = true;
      try {
         super.setHealth(Math.max(1.0F, this.getMaxHealth() * fraction));
      } finally {
         this.scripted = false;
      }
   }

   public byte state() {
      return this.entityData.get(STATE);
   }

   public void setState(byte state) {
      if (this.state() != state) {
         this.entityData.set(STATE, state);
         this.stateTicks = 0;
      }
   }

   public int moveId() {
      return this.entityData.get(MOVE);
   }

   protected void showMove(int id) {
      this.entityData.set(MOVE, (byte)id);
      this.entityData.set(MOVE_SERIAL, this.entityData.get(MOVE_SERIAL) + 1);
   }

   public int targetId() {
      return this.entityData.get(TARGET);
   }

   public byte reaction() {
      return this.entityData.get(REACTION);
   }

   public void react(byte reaction) {
      this.entityData.set(REACTION, reaction);
      this.entityData.set(REACTION_SERIAL, this.entityData.get(REACTION_SERIAL) + 1);
   }

   public boolean flag(int flag) {
      return (this.entityData.get(FLAGS) & flag) != 0;
   }

   public void setFlag(int flag, boolean on) {
      int f = this.entityData.get(FLAGS);
      this.entityData.set(FLAGS, (byte)(on ? f | flag : f & ~flag));
   }

   // ---- the war ----------------------------------------------------------------------------------
   public void bind(ThanosWar war) {
      this.war = war;
   }

   @Nullable
   public ThanosWar war() {
      return this.war;
   }

   /** Teleported out on the ship's beam (victory, the end): gone after a short shimmer. */
   public void beamOut() {
      if (this.state() != STATE_BEAM_OUT && this.isAlive()) {
         this.setState(STATE_BEAM_OUT);
         this.endMove();
         this.getNavigation().stop();
      }
   }

   public void enrage() {
      this.setFlag(FLAG_ENRAGED, true);
   }

   public void guard(boolean on) {
      this.setFlag(FLAG_GUARD, on);
   }

   public void standDown() {
      this.setFlag(FLAG_STAND_DOWN, true);
      this.endMove();
   }

   public boolean fighting() {
      return this.state() == STATE_FIGHT;
   }

   /** Thanos's side never hurts its own. */
   public static boolean friendly(@Nullable Entity e) {
      return ThanosEntity.friendly(e) || e instanceof ThanosEntity;
   }

   /** May a Black Order attack hurt this entity (the same gates as Thanos's own moves)? */
   public boolean victim(LivingEntity e) {
      if (e == this || !e.isAlive() || friendly(e) || e instanceof ArmorStand || Bystander.is(e)) {
         return false;
      }

      if (e instanceof Player p && !ThanosTargets.hero(p)) {
         return false;
      }

      return !WorldPause.isProtected(e);
   }

   public List<LivingEntity> victims(Vec3 c, double radius) {
      return this.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(radius, radius + 1.5, radius),
         e -> this.victim(e) && e.position().distanceToSqr(c) <= (radius + e.getBbWidth()) * (radius + e.getBbWidth()));
   }

   /** One hit of this member (a mob attack: hero defences, parries and frames apply); scaled by the war. */
   public boolean strike(LivingEntity v, float amount) {
      if (amount <= 0.0F || !this.victim(v)) {
         return false;
      }

      float scale = this.war == null ? 1.0F : this.war.combat.scale();
      if (this.flag(FLAG_ENRAGED)) {
         scale *= 1.15F;
      }

      return v.hurt(this.damageSources().mobAttack(this), amount * scale);
   }

   protected void fx(int kind, int b, Vec3 at, Vec3 dir, float power, int extra) {
      if (this.level() instanceof ServerLevel sl) {
         ThanosTargets.fx(sl, kind, this.getId(), b, at, dir, power, extra);
      }
   }

   // ---- damage -----------------------------------------------------------------------------------
   private static boolean admin(@Nullable DamageSource source) {
      return AdminKill.active() || source != null && (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.FELL_OUT_OF_WORLD));
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
         || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.FLY_INTO_WALL)
         || source.is(DamageTypes.STALAGMITE) || super.isInvulnerableTo(source);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide()) {
         return false;
      }

      boolean admin = admin(source);
      if (!admin && (this.state() != STATE_FIGHT || this.isInvulnerableTo(source) || friendly(source.getEntity()) || friendly(source.getDirectEntity()))) {
         return false;
      }

      if (!admin && this.deflects(source)) {
         return false;
      }

      float before = this.getHealth();
      boolean hurt = super.hurt(source, amount);
      float lost = before - this.getHealth();
      if (lost > 0.0F) {
         if (this.tickCount - this.lastHurtReact > 24) {
            this.lastHurtReact = this.tickCount;
            this.react(REACT_HURT);
            this.hurtSound();
         }

         if (source.getEntity() instanceof LivingEntity attacker && this.victim(attacker) && this.target != attacker) {
            // the one hurting him becomes his target unless he is busy with a move
            if (this.currentMove() == null && this.random.nextInt(3) == 0) {
               this.setTarget(attacker);
            }
         }
      }

      return hurt;
   }

   /** A chance to refuse a hit (Maw's catches); default none. */
   protected boolean deflects(DamageSource source) {
      return false;
   }

   @Override
   public void setHealth(float health) {
      if (this.scripted || this.level().isClientSide() || health >= this.getHealth() || AdminKill.active()) {
         super.setHealth(health);
         return;
      }

      float cap = Math.max(8.0F, this.getMaxHealth() * ThanosWarRules.ORDER_HIT_CAP);
      float loss = Math.min(this.getHealth() - health, cap);
      super.setHealth(this.getHealth() - loss);
   }

   @Override
   public void knockback(double strength, double x, double z) {
      super.knockback(strength * this.knockbackFactor(), x, z);
   }

   protected double knockbackFactor() {
      return 0.4;
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   // ---- ticking ----------------------------------------------------------------------------------
   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         this.clientWalkO = this.clientWalk;
         double dx = this.getX() - this.xo;
         double dz = this.getZ() - this.zo;
         double moved = Math.sqrt(dx * dx + dz * dz);
         this.clientWalk += ((float)Math.min(1.0, moved * 5.0) - this.clientWalk) * 0.25F;
         this.clientGaitO = this.clientGait;
         this.clientGait += (float)Math.min(0.5, moved) / this.stride();
         return;
      }

      ThanosWar w = this.war;
      if (this.tickCount > 2 && (w == null || w.ended() || ThanosWar.of(this.level()) != w)) {
         this.discard();
         return;
      }

      this.stateTicks++;
      switch (this.state()) {
         case STATE_BEAM_IN -> {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
            if (this.stateTicks >= 20) {
               this.setState(STATE_FIGHT);
               this.onArrived();
            }
         }
         case STATE_BEAM_OUT -> {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.stateTicks >= 24) {
               this.discard();
            }
         }
         default -> {
            if (!WorldPause.isPaused(this) && !this.isNoAi()) {
               this.fightTick();
            }
         }
      }
   }

   private void fightTick() {
      if (this.stagger > 0) {
         this.stagger--;
         this.getNavigation().stop();
         return;
      }

      if (this.flag(FLAG_STAND_DOWN)) {
         this.getNavigation().stop();
         return;
      }

      this.brain().tick();
      if (--this.retarget <= 0 || this.target == null || !this.validTarget(this.target)) {
         this.retarget = 20;
         this.pickTarget();
      }

      this.entityData.set(TARGET, this.target == null ? -1 : this.target.getId());
      this.leash();
      Object m = this.currentMove();
      if (m != null) {
         this.tickCurrent();
         return;
      }

      if (this.target != null) {
         this.position(this.target);
         OrderBrain.Situation s = this.situation(this.target);
         if (this.choose(s)) {
            return;
         }
      } else {
         this.getNavigation().stop();
      }

      this.passive();
   }

   /** Never too far from Thanos: past the leash he is beamed back to his side. */
   private void leash() {
      ThanosWar w = this.war;
      if (w == null || this.tickCount % 20 != 0 || this.currentMove() != null) {
         return;
      }

      if (this.distanceToSqr(w.thanos) > ThanosWarRules.ORDER_LEASH * ThanosWarRules.ORDER_LEASH) {
         Vec3 at = w.thanos.position().add(this.random.nextGaussian() * 4.0, 0.0, this.random.nextGaussian() * 4.0);
         at = dev.baranhan.viltrumitecore.worldevent.thanos.war.WarGround.below((ServerLevel)this.level(), at.add(0, 6, 0), 16);
         this.fx(ThanosFxS2CPacket.BEAM, 0, this.position(), new Vec3(0, 60, 0), 1.0F, 10);
         this.teleportTo(at.x, at.y, at.z);
         this.fx(ThanosFxS2CPacket.BEAM, 0, at, new Vec3(0, 60, 0), 1.0F, 16);
      }
   }

   protected boolean validTarget(LivingEntity e) {
      ThanosWar w = this.war;
      return e.isAlive() && e.level() == this.level() && this.victim(e) && e.distanceToSqr(this) < 64.0 * 64.0
         && (!(e instanceof ServerPlayer p) || w == null || w.event.isParticipant(p));
   }

   /**
    * His target: under Thanos's defensive call (guard) the hero hurting Thanos most; else the war's
    * threat table weighted by distance (each member keeps to its own role), held for a while.
    */
   protected void pickTarget() {
      ThanosWar w = this.war;
      if (w == null) {
         this.target = null;
         return;
      }

      LivingEntity best = null;
      double bestScore = -1.0;
      for (ServerPlayer p : w.heroes()) {
         if (!this.validTarget(p)) {
            continue;
         }

         double d = Math.sqrt(p.distanceToSqr(this));
         double score = this.targetScore(p, d, w.threat.score(p.getUUID()), w.threat.rangedShare(p.getUUID()));
         if (this.flag(FLAG_GUARD)) {
            score += w.threat.score(p.getUUID()) * 2.0;
         }

         if (p == this.target) {
            score *= 1.25;
         }

         if (score > bestScore) {
            bestScore = score;
            best = p;
         }
      }

      if (best == null) {
         // no hero in reach: citizens and golems in his way
         best = this.level().getNearestEntity(LivingEntity.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(24.0)
            .selector(e -> this.victim(e) && !(e instanceof Player)), this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(24.0));
      }

      this.target = best;
      this.setTarget(best);
   }

   /** Higher = better target: closeness, the threat table, his role (snipers for Maw, brawlers for Cull). */
   protected abstract double targetScore(LivingEntity e, double distance, double threat, double rangedShare);

   public boolean sees(LivingEntity t) {
      Vec3 from = this.getEyePosition();
      Vec3 to = t.getEyePosition();
      return this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS
         || this.level().clip(new ClipContext(from, t.position().add(0, t.getBbHeight() * 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
            .getType() == HitResult.Type.MISS;
   }

   public static boolean airborne(LivingEntity e) {
      if (e.onGround() || e.isPassenger() || e.isInWater()) {
         return false;
      }

      if (e instanceof Player p && p.getAbilities().flying) {
         return true;
      }

      net.minecraft.core.BlockPos pos = e.blockPosition();
      for (int i = 1; i <= 3; i++) {
         if (!e.level().getBlockState(pos.below(i)).getCollisionShape(e.level(), pos.below(i)).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   /** A big hero (the Hulkbuster's frame or anything that size). */
   public static boolean heavy(LivingEntity e) {
      return e.getBbHeight() >= 2.6F || e.getBbWidth() >= 1.1F;
   }

   protected int incomingProjectiles(double radius) {
      int n = 0;
      for (Projectile p : this.level().getEntitiesOfClass(Projectile.class, this.getBoundingBox().inflate(radius))) {
         Entity owner = p.getOwner();
         if (owner == null || friendly(owner)) {
            continue;
         }

         Vec3 v = p.getDeltaMovement();
         Vec3 to = this.position().add(0, this.getBbHeight() * 0.5, 0).subtract(p.position());
         if (v.lengthSqr() > 0.04 && v.normalize().dot(to.normalize()) > 0.6) {
            n++;
         }
      }

      return n;
   }

   protected boolean structuresNear() {
      ThanosWar w = this.war;
      if (w == null || w.destruction.left() <= 0) {
         return false;
      }

      return !w.destruction.liftable(this.position(), 10.0, 1, ThanosWarRules.STRIKE_HARDNESS).isEmpty();
   }

   // ---- moves (subclass) -------------------------------------------------------------------------
   protected abstract OrderBrain<?> brain();

   @Nullable
   protected abstract Object currentMove();

   protected abstract OrderBrain.Situation situation(LivingEntity target);

   /** Picks and starts a move for this situation; false when none fits (he moves into position). */
   protected abstract boolean choose(OrderBrain.Situation s);

   protected abstract void tickCurrent();

   /** Drops the current move (interrupted, beamed out). */
   protected abstract void endMove();

   /** Walking/floating into position against {@code t} between moves. */
   protected abstract void position(LivingEntity t);

   /** Between moves (catching shots, guarding). */
   protected void passive() {
   }

   protected void onArrived() {
   }

   protected abstract void hurtSound();

   protected abstract float stride();

   /** A hero's control or a heavy blow: the current move is cut (recovery skipped). */
   public void staggerFor(int ticks) {
      this.endMove();
      this.stagger = Math.max(this.stagger, ticks);
      this.react(REACT_STAGGER);
   }

   // ---- body ------------------------------------------------------------------------------------
   public void faceTowards(Vec3 at, float maxTurn) {
      double dx = at.x - this.getX();
      double dz = at.z - this.getZ();
      if (dx * dx + dz * dz < 1.0E-4) {
         return;
      }

      float yaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
      float turned = Mth.approachDegrees(this.getYRot(), yaw, maxTurn);
      this.setYRot(turned);
      this.setYBodyRot(turned);
      this.setYHeadRot(turned);
   }

   public Vec3 facing() {
      float r = this.getYRot() * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
   }

   @Override
   public boolean isPushable() {
      return false;
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
      return other == this || friendly(other) || super.isAlliedTo(other);
   }

   @Override
   protected boolean canRide(Entity vehicle) {
      return false;
   }

   @Override
   public boolean isSilent() {
      return false;
   }
}

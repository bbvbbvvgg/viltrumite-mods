package dev.baranhan.viltrumitecore.hero.venom.boss;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.venom.VenomCombat;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomFx;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.VenomPossession;
import dev.baranhan.viltrumitecore.hero.venom.VenomPossessor;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import dev.baranhan.viltrumitecore.hero.venom.VenomSounds;
import dev.baranhan.viltrumitecore.hero.venom.VenomTargets;
import dev.baranhan.viltrumitecore.hero.venom.VenomWeakness;
import dev.baranhan.viltrumitecore.network.packet.VenomDriveC2SPacket;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Venom boss (spec §9): a summoned symbiote in a permanent frenzy with its own AI - claw combos, tendril yanks and
 * throws, mega leaps, the lash sweep, wall climbing, a shriek when fire or a loud sound hits it - and, when the config
 * allows ({@code venomBossPossession}), the host takeover of a weakened player through the same server manager the
 * player Venom uses (shorter, same end rules, same immunity). Its moves reuse {@link VenomMove} so the client poses it
 * with the player's clips.
 */
public class VenomBossEntity extends Monster implements VenomPossessor {
   static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> SERIAL = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   public static final float MAX_HEALTH = 300.0F;

   private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.viltrumitecore.venom_boss"), BossEvent.BossBarColor.PURPLE,
      BossEvent.BossBarOverlay.NOTCHED_10);
   @Nullable private VenomMove move;
   private int elapsed;
   private int combo;
   private int clawCd, tendrilCd, leapCd, lashCd, takeoverCd, recoilImmune;
   private int sinceHurt = 100, fireBlock;
   private int heldId = -1;
   @Nullable private UUID heldEffect;
   private int holdTicks;
   private boolean possessing;
   /** Client: the move's start, on the client clock. */
   public int clientSerialSeen = -1;
   public float clientStart;

   public VenomBossEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
      this.xpReward = 120;
      this.setMaxUpStep(1.25F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, MAX_HEALTH)
         .add(Attributes.ARMOR, 10.0)
         .add(Attributes.ARMOR_TOUGHNESS, 4.0)
         .add(Attributes.ATTACK_DAMAGE, 10.0)
         .add(Attributes.MOVEMENT_SPEED, 0.33)
         .add(Attributes.FOLLOW_RANGE, 48.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(MOVE, -1);
      this.entityData.define(SERIAL, 0);
      this.entityData.define(FLAGS, VenomFlags.set(VenomFlags.set(0, VenomFlags.FORM, true), VenomFlags.RAGE, true));
      this.entityData.define(TARGET, -1);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
      this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
         p -> p instanceof Player pl && !pl.isCreative() && !pl.isSpectator()));
   }

   // --- client view --------------------------------------------------------------------------------------------

   @Nullable
   public VenomMove move() {
      return VenomMove.byId(this.entityData.get(MOVE));
   }

   public int serial() {
      return this.entityData.get(SERIAL);
   }

   public int flags() {
      return this.entityData.get(FLAGS);
   }

   public int targetId() {
      return this.entityData.get(TARGET);
   }

   // --- server AI ----------------------------------------------------------------------------------------------

   private void start(VenomMove m) {
      this.move = m;
      this.elapsed = 0;
      this.entityData.set(MOVE, m.ordinal());
      this.entityData.set(SERIAL, (this.serial() + 1) & 0xFFFF);
   }

   private void endMove() {
      this.move = null;
      this.elapsed = 0;
      this.entityData.set(MOVE, -1);
   }

   private void flag(int bit, boolean on) {
      this.entityData.set(FLAGS, VenomFlags.set(this.flags(), bit, on));
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      this.bossBar.setProgress(this.getHealth() / this.getMaxHealth());
      tickTimers();
      if (this.possessing) {
         driveHost();
         return;
      }
      LivingEntity target = this.getTarget();
      holdTick(target);
      if (this.move != null) {
         this.elapsed++;
         if (this.elapsed == this.move.contact) {
            contact(this.move, target);
         }
         if (this.move != null && !this.move.loops() && this.elapsed >= this.move.length) {
            endMove();
         }
         if (this.move != null && this.move.roots()) {
            this.getNavigation().stop();
         }
         return;
      }
      if (target == null || !target.isAlive()) {
         return;
      }
      double d = this.distanceTo(target);
      this.getLookControl().setLookAt(target, 30.0F, 30.0F);
      this.getNavigation().moveTo(target, 1.15);
      // climbing: a wall in the way is just another floor
      if (this.horizontalCollision && target.getY() > this.getY() - 1) {
         Vec3 v = this.getDeltaMovement();
         this.setDeltaMovement(v.x, Math.max(v.y, VenomRules.WALL_CLIMB_SPEED + 0.06), v.z);
         this.flag(VenomFlags.WALL, true);
         this.fallDistance = 0;
      } else {
         this.flag(VenomFlags.WALL, false);
      }
      int nearby = VenomTargets.around(this, this.position(), 4.5, e -> e != this && e instanceof Player p && !p.isCreative() && !p.isSpectator()).size();
      if (this.takeoverCd <= 0 && d < VenomRules.TAKEOVER_RANGE && target instanceof ServerPlayer host && VenomPossession.bossMayPossess()
         && host.getHealth() / host.getMaxHealth() <= VenomRules.TAKEOVER_HEALTH_FRACTION && VenomPossession.eligible(this, host)) {
         if (VenomPossession.engulfByBoss(this, host)) {
            this.start(VenomMove.ENGULF);
            this.takeoverCd = 20 * 45;
            return;
         }
         this.takeoverCd = 100;
      }
      if (nearby >= 2 && this.lashCd <= 0) {
         this.start(VenomMove.LASH_SWEEP);
         this.lashCd = 80;
         VenomSounds.play(this, VenomSounds.LASH, 1.0F, 0.9F);
      } else if (d < 3.6 && this.clawCd <= 0) {
         this.combo = (this.combo + 1) % 3;
         this.start(VenomMove.claw(this.combo));
         this.clawCd = this.combo == 2 ? 22 : 11;
         VenomSounds.play(this, VenomSounds.CLAW, 0.9F, VenomSounds.jitter(this, 0.9F));
      } else if (d > 6 && d < VenomRules.TENDRIL_RANGE * 0.7 && this.tendrilCd <= 0 && this.hasLineOfSight(target)) {
         this.start(VenomMove.TENDRIL_SHOT);
         this.tendrilCd = 90;
         VenomSounds.play(this, VenomSounds.TENDRIL_SHOT, 1.0F, 1.0F);
      } else if (d > 9 && this.leapCd <= 0 && this.onGround()) {
         Vec3 to = target.position().subtract(this.position());
         Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
         this.setDeltaMovement(flat.scale(Math.min(1.6, d * 0.09)).add(0, 0.95, 0));
         this.hasImpulse = true;
         this.leapCd = 70;
         VenomFx.send(this, VenomFx.LEAP, this.position(), null, 1.2F, 0);
         VenomSounds.play(this, VenomSounds.ROAR, 1.2F, VenomSounds.jitter(this, 0.85F));
      }
   }

   private void tickTimers() {
      if (this.clawCd > 0) this.clawCd--;
      if (this.tendrilCd > 0) this.tendrilCd--;
      if (this.leapCd > 0) this.leapCd--;
      if (this.lashCd > 0) this.lashCd--;
      if (this.takeoverCd > 0) this.takeoverCd--;
      if (this.recoilImmune > 0) this.recoilImmune--;
      if (this.fireBlock > 0) this.fireBlock--;
      if (this.sinceHurt < 1000) this.sinceHurt++;
      if (this.isOnFire()) {
         this.fireBlock = VenomRules.FIRE_REGEN_BLOCK;
         recoil();
      }
      if (this.sinceHurt > VenomRules.REGEN_DELAY && this.fireBlock <= 0 && this.tickCount % 10 == 0) {
         this.heal(0.5F);
      }
   }

   private void contact(VenomMove m, @Nullable LivingEntity target) {
      switch (m) {
         case CLAW_1, CLAW_2, CLAW_3 -> {
            Vec3 eye = this.getEyePosition();
            Vec3 look = target != null ? VenomTargets.center(target).subtract(eye).normalize() : this.getLookAngle();
            boolean heavy = m == VenomMove.CLAW_3;
            for (LivingEntity e : VenomTargets.around(this, eye.add(look.scale(1.8)), 2.2, e -> e != this && legal(e))) {
               if (VenomTargets.strike(this, e, heavy ? 14.0F : 9.0F)) {
                  VenomTargets.push(e, VenomTargets.outward(this.position(), e).scale(heavy ? 1.0 : 0.4).add(0, heavy ? 0.4 : 0.15, 0));
                  VenomFx.send(this, VenomFx.CLAW, VenomTargets.center(e), VenomTargets.hand(this), heavy ? 1.4F : 1.0F, 0);
                  VenomSounds.playAt(this, VenomTargets.center(e), VenomSounds.CLAW_HIT, 1.0F, VenomSounds.jitter(this, 0.9F));
               }
            }
         }
         case TENDRIL_SHOT -> {
            if (target != null && this.hasLineOfSight(target) && this.distanceTo(target) < VenomRules.TENDRIL_RANGE && grab(target)) {
               this.start(VenomMove.PULL);
               VenomFx.send(this, VenomFx.STICK, VenomTargets.center(target), VenomTargets.shoulder(this, 1), 1.0F, 0);
               VenomSounds.playAt(this, VenomTargets.center(target), VenomSounds.TENDRIL_STICK, 1.0F, 1.0F);
            }
         }
         case THROW -> {
            LivingEntity held = held();
            releaseHeld(held == null ? null : this.getLookAngle().scale(VenomRules.THROW_SPEED).add(0, 0.4, 0));
            if (held != null) {
               dev.baranhan.viltrumitecore.hero.venom.VenomThrows.track(this, held, VenomRules.THROW_IMPACT_DAMAGE);
               VenomSounds.play(this, VenomSounds.THROW, 1.0F, 0.9F);
            }
         }
         case LASH_SWEEP -> {
            for (LivingEntity e : VenomTargets.around(this, VenomTargets.center(this), VenomRules.LASH_SWEEP_RADIUS, e -> e != this && legal(e))) {
               VenomTargets.strike(this, e, VenomRules.LASH_SWEEP_DAMAGE + 2.0F);
               VenomTargets.push(e, VenomTargets.outward(this.position(), e).scale(0.9).add(0, 0.3, 0));
            }
            VenomFx.send(this, VenomFx.SWEEP, VenomTargets.center(this), VenomFx.radius(VenomTargets.center(this), VenomRules.LASH_SWEEP_RADIUS), 1.2F, 0);
         }
         default -> {
         }
      }
   }

   private boolean legal(LivingEntity e) {
      return e.isAlive() && !(e instanceof VenomBossEntity) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
         && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
   }

   private boolean grab(LivingEntity t) {
      if (!(this.level() instanceof ServerLevel level) || HeroDamage.isAnchored(t) || VenomPossession.involved(t)
         || !dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(t, ControlKind.PULL)) {
         return false;
      }
      UUID effect = UUID.randomUUID();
      if (!ControlManager.get(level).tryAcquire(t, this.getUUID(), effect, ControlKind.PULL, level.getGameTime() + 80)) {
         return false;
      }
      this.heldId = t.getId();
      this.heldEffect = effect;
      this.holdTicks = 0;
      this.entityData.set(TARGET, t.getId());
      return true;
   }

   @Nullable
   private LivingEntity held() {
      if (this.heldId < 0 || !(this.level() instanceof ServerLevel level)) {
         return null;
      }
      if (!(level.getEntity(this.heldId) instanceof LivingEntity t) || !t.isAlive() || this.heldEffect == null
         || !this.heldEffect.equals(ControlManager.get(level).effectIdOf(t))) {
         return null;
      }
      return t;
   }

   private void holdTick(@Nullable LivingEntity target) {
      if (this.heldId < 0) {
         return;
      }
      LivingEntity t = held();
      this.holdTicks++;
      if (t == null || t.distanceToSqr(this) > 30 * 30) {
         releaseHeld(null);
         return;
      }
      Vec3 hold = VenomCombat.holdPoint(this.position(), this.getYRot(), this.getBbHeight(), t.getBbHeight());
      Vec3 d = hold.subtract(t.position());
      VenomTargets.place(t, d.length() > 1.4 ? t.position().add(d.normalize().scale(Math.min(d.length(), VenomRules.PULL_SPEED))) : hold);
      if (this.move == VenomMove.PULL && d.length() < 1.6) {
         this.start(VenomMove.HOLD);
      }
      if (this.move == VenomMove.HOLD && this.holdTicks > 30) {
         this.start(VenomMove.THROW);
      }
      if (this.holdTicks > 90) {
         releaseHeld(null);
      }
   }

   private void releaseHeld(@Nullable Vec3 velocity) {
      if (this.heldId >= 0 && this.level() instanceof ServerLevel level) {
         if (level.getEntity(this.heldId) instanceof LivingEntity t) {
            if (this.heldEffect != null) {
               ControlManager.get(level).release(t.getUUID(), this.heldEffect, ReleaseReason.NORMAL_END);
            }
            if (velocity != null) {
               VenomTargets.push(t, velocity);
            }
            if (t instanceof ServerPlayer p) {
               p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
               p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
            }
         }
      }
      this.heldId = -1;
      this.heldEffect = null;
      this.entityData.set(TARGET, -1);
      if (this.move == VenomMove.HOLD || this.move == VenomMove.PULL) {
         endMove();
      }
   }

   /** While it rides a host its body is a husk; the AI drives the host at the nearest other player. */
   private void driveHost() {
      ServerPlayer host = VenomPossession.hostOf(this);
      if (host == null) {
         return;
      }
      int control = VenomPossession.controlTicksOf(this);
      if (control < 0) {
         return; // still engulfing
      }
      if (this.move != VenomMove.POSSESS) {
         this.start(VenomMove.POSSESS);
      }
      this.getNavigation().stop();
      Player prey = null;
      double best = 24 * 24;
      for (Player p : this.level().players()) {
         double dd = p.distanceToSqr(host);
         if (p != host && !p.isCreative() && !p.isSpectator() && dd < best) {
            best = dd;
            prey = p;
         }
      }
      float yaw;
      float pitch;
      float forward;
      int bits = 0;
      if (prey != null) {
         Vec3 to = prey.getEyePosition().subtract(host.getEyePosition());
         yaw = (float)(Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90.0F;
         pitch = (float)(-(Mth.atan2(to.y, to.horizontalDistance()) * (180.0 / Math.PI)));
         forward = best > 2.5 * 2.5 ? 1.0F : 0.0F;
         if (best < 3.2 * 3.2 && control % 10 == 0) {
            bits |= VenomDriveC2SPacket.ATTACK;
         }
         if (host.horizontalCollision) {
            bits |= VenomDriveC2SPacket.JUMP;
         }
      } else {
         // nobody to hunt: the host staggers in circles under the symbiote
         yaw = host.getYRot() + 6.0F;
         pitch = 10.0F;
         forward = 0.6F;
      }
      VenomPossession.bossInput(this, new VenomDriveC2SPacket(false, forward, 0.0F, yaw, pitch, bits, 0));
   }

   @Override
   public void onPossessionEnd(VenomPossession.End reason) {
      this.possessing = false;
      this.flag(VenomFlags.POSSESSING, false);
      this.entityData.set(TARGET, -1);
      this.start(reason.violent() ? VenomMove.RECOIL : VenomMove.REFORM);
      this.recoilImmune = 60;
   }

   @Override
   public void beganPossessing(ServerPlayer host) {
      this.possessing = true;
      this.flag(VenomFlags.POSSESSING, true);
      this.entityData.set(TARGET, host.getId());
   }

   private void recoil() {
      if (this.recoilImmune > 0 || this.possessing) {
         return;
      }
      releaseHeld(null);
      this.start(VenomMove.RECOIL);
      this.recoilImmune = VenomRules.RECOIL_IMMUNITY;
      VenomFx.send(this, VenomFx.RECOIL, VenomTargets.center(this), null, 1.4F, 0);
      VenomSounds.play(this, VenomSounds.SHRIEK, 1.4F, VenomSounds.jitter(this, 0.85F));
   }

   /** A loud sound in the level: every boss in earshot recoils. */
   public static void loudSound(ServerLevel level, Vec3 at, @Nullable ResourceLocation id, float volume) {
      List<VenomBossEntity> bosses = level.getEntitiesOfClass(VenomBossEntity.class, new AABB(at, at).inflate(VenomRules.SOUND_RADIUS * 3));
      for (VenomBossEntity boss : bosses) {
         if (VenomWeakness.loud(id, volume, boss.position().distanceTo(at))) {
            boss.recoil();
         }
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (VenomTargets.fire(source)) {
         amount *= VenomRules.FIRE_TAKEN;
         this.fireBlock = VenomRules.FIRE_REGEN_BLOCK;
         recoil();
      } else if (VenomTargets.sonic(source)) {
         amount *= VenomRules.SONIC_TAKEN;
         recoil();
      }
      if (this.possessing) {
         amount *= VenomRules.HUSK_DAMAGE_TAKEN;
      }
      boolean hurt = super.hurt(source, amount);
      if (hurt) {
         this.sinceHurt = 0;
         if (this.possessing && amount >= VenomRules.HUSK_BREAK_DAMAGE) {
            VenomPossession.endAllFor(this, VenomPossession.End.HUSK_BROKEN);
         }
      }
      return hurt;
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      if (distance > 6.0F && this.level() instanceof ServerLevel) {
         VenomFx.send(this, VenomFx.LEAP, this.position(), null, Math.min(2.0F, distance / 10.0F), 0);
         VenomSounds.play(this, VenomSounds.SLAM, 1.0F, 0.9F);
         for (LivingEntity e : VenomTargets.around(this, this.position(), 3.5, e -> e != this && legal(e))) {
            VenomTargets.strike(this, e, 8.0F);
            VenomTargets.push(e, VenomTargets.outward(this.position(), e).scale(0.8).add(0, 0.4, 0));
         }
      }
      return false;
   }

   @Override
   public void die(DamageSource source) {
      VenomPossession.endAllFor(this, VenomPossession.End.DEATH);
      releaseHeld(null);
      super.die(source);
   }

   @Override
   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide) {
         VenomPossession.endAllFor(this, VenomPossession.End.INTERRUPTED);
         releaseHeld(null);
      }
      super.remove(reason);
   }

   @Override
   public void startSeenByPlayer(ServerPlayer player) {
      super.startSeenByPlayer(player);
      this.bossBar.addPlayer(player);
   }

   @Override
   public void stopSeenByPlayer(ServerPlayer player) {
      super.stopSeenByPlayer(player);
      this.bossBar.removePlayer(player);
   }

   @Override
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   public boolean onClimbable() {
      return this.horizontalCollision || super.onClimbable();
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      // nothing of a hold or a takeover is ever saved: a reloaded boss starts free
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (this.hasCustomName()) {
         this.bossBar.setName(this.getDisplayName());
      }
   }
}

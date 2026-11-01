package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronBossRules;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronScenario;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUnitKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUprising;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronNet;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ultron Prime, the finale. Assembled from the network's matter at the finale site, he fights in three
 * stages: TACTICAL (a ground brawler: jab-cross, uppercut, backhand, palm blast, dash, eye beam), AERIAL
 * from {@link UltronBossRules#AERIAL_AT} (flight bouts with beams, palm volleys and diving strikes,
 * short ground bouts between them) and OVERDRIVE from {@link UltronBossRules#OVERDRIVE_AT} (faster
 * chains, ground slams, hop-dives). He reads the fight: kept at range he dashes in and beams, brawled he
 * slams and backhands people off, against flyers he takes to the air. Every powered node feeds him
 * through its tether (armour, regeneration, faster moves, more damage, reinforcements, sharper aim), so
 * the fight is as hard as the network the players left standing. Hits are capped per hit and per
 * second; his max health (group-scaled, past vanilla's 1024 clamp) is synced like Thanos's.
 */
public class UltronPrime extends UltronUnit {
   private static final EntityDataAccessor<Float> MAX_HP = SynchedEntityData.defineId(UltronPrime.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Byte> STAGE = SynchedEntityData.defineId(UltronPrime.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(UltronPrime.class, EntityDataSerializers.BOOLEAN);

   /** Aerial stage: a flight bout, then a ground bout (melee heroes get their turn). */
   public static final int AIR_BOUT = 25 * 20;
   public static final int GROUND_BOUT = 10 * 20;
   /** Overdrive flights are short hops into a dive. */
   public static final int OVERDRIVE_AIR = 5 * 20;
   /** Damage within the stagger window (share of max health) that staggers him. */
   public static final float STAGGER_SHARE = 0.06F;
   public static final double BEAM_RANGE = 42.0;

   private int beamCooldown = 120;
   private int palmCooldown = 40;
   private int dashCooldown = 60;
   private int slamCooldown = 100;
   private int diveCooldown = 140;
   private int summonCooldown = 300;
   private int absorbCooldown = 500;
   private int comboStep;
   private int comboWindow;
   private int secondClock;
   private float secondTaken;
   private int staggerClock;
   private float staggerTaken;
   private float meleeTaken;
   private float rangedTaken;
   private UltronBossRules.Tethers tethers = UltronBossRules.Tethers.NONE;
   private int boutClock;
   private boolean landing;
   private boolean ascending;
   private double orbit;
   private Vec3 dashDir = Vec3.ZERO;
   @Nullable
   private Vec3 diveTo;
   private Vec3 beamDir = new Vec3(0.0, 0.0, 1.0);

   // ---- client presentation ----
   public UltronBossRules.Stage clientStage = UltronBossRules.Stage.ASSEMBLY;
   public int clientStageStart = -1000;

   public UltronPrime(EntityType<? extends UltronPrime> type, Level level) {
      super(type, level);
      this.setMaxUpStep(1.2F);
      this.orbit = this.random.nextDouble() * Math.PI * 2.0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         // vanilla clamps this attribute at 1024: the real max is the synced MAX_HP (see primeMaxHealth)
         .add(Attributes.MAX_HEALTH, 1024.0)
         .add(Attributes.ARMOR, 12.0)
         .add(Attributes.ARMOR_TOUGHNESS, 8.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.MOVEMENT_SPEED, 0.3)
         .add(Attributes.FLYING_SPEED, 0.6)
         .add(Attributes.ATTACK_DAMAGE, BalanceTable.ULTRON_PRIME_CROSS)
         .add(Attributes.FOLLOW_RANGE, 96.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(MAX_HP, UltronBossRules.BASE_HEALTH);
      this.entityData.define(STAGE, (byte)UltronBossRules.Stage.ASSEMBLY.ordinal());
      this.entityData.define(FLYING, false);
   }

   // ------------------------------------------------------------------ the director's handles

   /** Before he is added: health for the group (share kept over a restart), stage, the assembly cinematic. */
   public void setup(UltronBossRules.Stage stage, float share, int heroes) {
      float max = UltronBossRules.maxHealth(heroes);
      this.entityData.set(MAX_HP, max);
      this.setHealth(Math.max(1.0F, Mth.clamp(share, 0.0F, 1.0F) * max));
      this.entityData.set(STAGE, (byte)(stage == UltronBossRules.Stage.DEFEATED ? UltronBossRules.Stage.TACTICAL : stage).ordinal());
      this.setFlying(false);
      this.forceMove(UltronMove.PRIME_ASSEMBLE, null);
   }

   /** His synced max health ({@code ThanosMaxHealthMixin} answers {@code getMaxHealth()} from it). */
   public float primeMaxHealth() {
      return this.entityData.get(MAX_HP);
   }

   public UltronBossRules.Stage stage() {
      return UltronBossRules.Stage.byId(this.entityData.get(STAGE));
   }

   private void setStage(UltronBossRules.Stage stage) {
      this.entityData.set(STAGE, (byte)stage.ordinal());
   }

   /** Defeat: the upload is complete, he rises into the sky and is gone. */
   public void ascend() {
      if (this.ascending || !this.isAlive()) {
         return;
      }
      this.ascending = true;
      this.setInvulnerable(true);
      this.setFlag(FLAG_BEAM, false);
      this.setFlying(true);
      this.forceMove(UltronMove.PRIME_ASCEND, null);
      if (this.level() instanceof ServerLevel level) {
         UltronSounds.playWide(level, this.position(), UltronSounds.PRIME_ASCEND, 3.0F, 1.0F, 160.0);
         UltronNet.fxAlways(level, new UltronFxS2CPacket(UltronFxS2CPacket.PRIME_TRANSITION, this.position().add(0.0, 1.5, 0.0), Vec3.ZERO, 2.0F, -1), this.position(), 160.0);
      }
   }

   public float share() {
      return Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F);
   }

   // ------------------------------------------------------------------ kind hooks

   @Override
   public UltronUnitKind kind() {
      return UltronUnitKind.SENTRY; // not a foundry kind: never counted, the renderer goes by the entity type
   }

   @Override
   public int deathTicks() {
      return UltronMove.PRIME_DEATH.length;
   }

   @Override
   protected double senseRange() {
      return 48.0;
   }

   @Override
   public boolean flier() {
      return this.entityData.get(FLYING);
   }

   private void setFlying(boolean on) {
      this.entityData.set(FLYING, on);
      this.setNoGravity(on);
      if (on) {
         this.landing = false;
         this.steer = new Vec3(0.0, 0.25, 0.0);
         this.getNavigation().stop();
      }
   }

   @Override
   protected double hoverHeight() {
      return 6.0;
   }

   @Override
   protected double cruiseSpeed() {
      return 1.3;
   }

   @Override
   public float stride(float speed) {
      return 1.9F + 2.4F * Mth.clamp(speed / 0.3F, 0.0F, 1.0F);
   }

   @Override
   protected boolean interruptible(UltronMove m) {
      return switch (m) {
         case PRIME_JAB_CROSS, PRIME_UPPERCUT, PRIME_BACKHAND, PRIME_PALM_BLAST, PRIME_BEAM, PRIME_SUMMON, PRIME_ABSORB -> true;
         default -> false;
      };
   }

   @Override
   protected boolean rooted(UltronMove m) {
      return switch (m) {
         case PRIME_ASSEMBLE, PRIME_TRANSITION_AIR, PRIME_TRANSITION_OVERDRIVE, PRIME_ASCEND, PRIME_SUMMON, PRIME_ABSORB, PRIME_BEAM, PRIME_PALM_BLAST,
            HEAVY_HIT, LAND -> true;
         default -> false;
      };
   }

   @Override
   protected void holdStill() {
      if (this.move == UltronMove.PRIME_TRANSITION_AIR || this.move == UltronMove.PRIME_ASCEND) {
         return; // the cinematic steers him (onMoveTick)
      }
      this.stopMoving();
   }

   /** His moves scale with the group and the core's tether. */
   @Override
   protected float damageScale() {
      return UltronBossRules.damageScale(UltronUprising.heroes(this.level())) * this.tethers.damageFactor();
   }

   private int cooldown(int base) {
      float f = this.tethers.cooldownFactor() * (this.stage() == UltronBossRules.Stage.OVERDRIVE ? 0.7F : 1.0F);
      return Math.max(10, Math.round(base * f));
   }

   // ------------------------------------------------------------------ tick

   @Override
   protected void customServerAiStep() {
      if (++this.secondClock >= 20) {
         this.secondClock = 0;
         this.secondTaken = 0.0F;
         this.tethers = UltronUprising.tethers(this.level());
         if (this.tethers.regenPerSecond() > 0.0F && this.isAlive() && !this.ascending && this.stage() != UltronBossRules.Stage.ASSEMBLY) {
            this.heal(this.getMaxHealth() * this.tethers.regenPerSecond());
         }
         UltronUprising.bossHealth(this, this.share());
         this.meleeTaken *= 0.9F;
         this.rangedTaken *= 0.9F;
      }
      if (++this.staggerClock >= 60) {
         this.staggerClock = 0;
         this.staggerTaken = 0.0F;
      }
      if (this.beamCooldown > 0) {
         this.beamCooldown--;
      }
      if (this.palmCooldown > 0) {
         this.palmCooldown--;
      }
      if (this.dashCooldown > 0) {
         this.dashCooldown--;
      }
      if (this.slamCooldown > 0) {
         this.slamCooldown--;
      }
      if (this.diveCooldown > 0) {
         this.diveCooldown--;
      }
      if (this.summonCooldown > 0) {
         this.summonCooldown--;
      }
      if (this.absorbCooldown > 0) {
         this.absorbCooldown--;
      }
      if (this.comboWindow > 0 && --this.comboWindow == 0) {
         this.comboStep = 0;
      }
      this.boutClock++;
      if (this.landing && this.onGround()) {
         this.landing = false;
         this.forceMove(UltronMove.LAND, null);
         this.sound(UltronSounds.PRIME_IMPACT, 1.6F, 0.8F);
         this.fx(UltronFxS2CPacket.PRIME_SHOCKWAVE, this.position(), Vec3.ZERO, 3.0F, this.getId());
      }
      super.customServerAiStep();
   }

   /** Out of sight of everyone: he goes for the nearest hero in the area (he knows where the fight is). */
   @Override
   protected void idle() {
      if (this.stage() == UltronBossRules.Stage.ASSEMBLY || this.ascending) {
         this.stopMoving();
         return;
      }
      Player best = null;
      double bestD = 96.0 * 96.0;
      for (Player p : this.level().players()) {
         double d = p.distanceToSqr(this);
         if (d < bestD && UltronMob.validTarget(p)) {
            bestD = d;
            best = p;
         }
      }
      if (best == null) {
         this.stopMoving();
         return;
      }
      this.travelTo(best.position(), this.flier() ? 1.4 : 1.2);
   }

   @Override
   protected void fight(LivingEntity target, double distance, boolean sees) {
      UltronBossRules.Stage st = this.stage();
      if (st == UltronBossRules.Stage.ASSEMBLY || st == UltronBossRules.Stage.DEFEATED || this.ascending) {
         this.stopMoving();
         return;
      }
      if (this.flier()) {
         this.fightAir(target, distance, sees, st);
      } else {
         this.fightGround(target, distance, sees, st);
      }
   }

   private void fightGround(LivingEntity target, double distance, boolean sees, UltronBossRules.Stage st) {
      if (this.move == UltronMove.PRIME_DASH || this.move == UltronMove.PRIME_SLAM || this.move == UltronMove.PRIME_DIVE) {
         return; // these steer themselves
      }
      this.faceTowards(target.position(), this.move == UltronMove.NONE ? 16.0F : 8.0F);
      if (this.move != UltronMove.NONE) {
         this.stopMoving();
         return;
      }
      UltronScenario.BossStyle style = UltronUprising.bossStyle(this.level());
      boolean kiting = this.rangedTaken > this.meleeTaken * 1.5F + 4.0F;
      boolean brawling = this.meleeTaken > this.rangedTaken * 1.5F + 4.0F;
      boolean air = this.airborne(target);
      double reach = 2.2 + target.getBbWidth() * 0.5;
      // the network's help first
      if (this.summonCooldown <= 0 && this.tethers.summons() && this.random.nextInt(style == UltronScenario.BossStyle.TACTICIAN ? 2 : 4) == 0) {
         this.startMove(UltronMove.PRIME_SUMMON, target);
         this.summonCooldown = this.cooldown(UltronBossRules.FOUNDRY_SUMMON_TICKS);
         return;
      }
      if (this.absorbCooldown <= 0 && this.share() < 0.8F && !UltronUprising.tetherNodes(this.level()).isEmpty()
         && (style == UltronScenario.BossStyle.JUGGERNAUT || this.random.nextInt(3) == 0)) {
         this.startMove(UltronMove.PRIME_ABSORB, target);
         this.absorbCooldown = this.cooldown(30 * 20);
         return;
      }
      // the aerial stage: back up after the ground bout; flyers out of reach bring him up sooner
      if (st == UltronBossRules.Stage.AERIAL && (this.boutClock > GROUND_BOUT || air && this.boutClock > 60)) {
         this.takeOff();
         return;
      }
      if (st == UltronBossRules.Stage.OVERDRIVE && this.diveCooldown <= 0 && sees && distance > 8.0 && distance < 24.0 && !air) {
         this.startMove(UltronMove.PRIME_DIVE, target);
         this.diveCooldown = this.cooldown(170);
         return;
      }
      if (distance <= reach) {
         this.stopMoving();
         if (this.attackCooldown <= 0) {
            UltronMove m = this.meleeChoice(target, brawling, style);
            this.startMove(m, target);
            this.attackCooldown = m == UltronMove.PRIME_JAB_CROSS ? 2 : this.cooldown(style == UltronScenario.BossStyle.DUELIST ? 6 : 12);
         }
         return;
      }
      if (air && sees) {
         if (this.beamCooldown <= 0) {
            this.startMove(UltronMove.PRIME_BEAM, target);
            this.beamCooldown = this.cooldown(style == UltronScenario.BossStyle.TACTICIAN ? 110 : 150);
            return;
         }
         if (this.palmCooldown <= 0) {
            this.startMove(UltronMove.PRIME_PALM_BLAST, target);
            this.palmCooldown = this.cooldown(50);
            return;
         }
         if (st == UltronBossRules.Stage.OVERDRIVE && this.boutClock > 80) {
            this.takeOff();
            return;
         }
      }
      if (sees && distance < 9.0 && this.palmCooldown <= 0 && (style == UltronScenario.BossStyle.JUGGERNAUT || this.random.nextInt(3) == 0)) {
         this.startMove(UltronMove.PRIME_PALM_BLAST, target);
         this.palmCooldown = this.cooldown(60);
         return;
      }
      if (sees && distance >= 6.0 && distance < 22.0 && this.dashCooldown <= 0 && this.onGround()
         && (kiting || style == UltronScenario.BossStyle.DUELIST || this.random.nextInt(2) == 0)) {
         this.startMove(UltronMove.PRIME_DASH, target);
         this.dashCooldown = this.cooldown(kiting ? 50 : 90);
         return;
      }
      if (sees && distance >= 10.0 && this.beamCooldown <= 0) {
         this.startMove(UltronMove.PRIME_BEAM, target);
         this.beamCooldown = this.cooldown(kiting ? 100 : 160);
         return;
      }
      this.travelTo(target.position(), st == UltronBossRules.Stage.OVERDRIVE ? 1.5 : 1.25);
   }

   /** Close range: side / back gets the backhand, a crowd or a brawler the slam, else the chain. */
   private UltronMove meleeChoice(LivingEntity target, boolean brawling, UltronScenario.BossStyle style) {
      Vec3 to = target.position().subtract(this.position());
      float yawTo = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
      if (Math.abs(Mth.wrapDegrees(yawTo - this.yBodyRot)) > 60.0F) {
         return UltronMove.PRIME_BACKHAND;
      }
      if (this.slamCooldown <= 0 && this.onGround() && (brawling || this.around(this.position(), 4.0).size() >= 2 || style == UltronScenario.BossStyle.JUGGERNAUT && this.random.nextInt(3) == 0)) {
         this.slamCooldown = this.cooldown(140);
         return UltronMove.PRIME_SLAM;
      }
      UltronMove m = switch (this.comboStep) {
         case 0 -> UltronMove.PRIME_JAB_CROSS;
         case 1 -> style == UltronScenario.BossStyle.JUGGERNAUT ? UltronMove.PRIME_PALM_BLAST : UltronMove.PRIME_UPPERCUT;
         default -> UltronMove.PRIME_BACKHAND;
      };
      this.comboStep = (this.comboStep + 1) % 3;
      this.comboWindow = 50;
      return m;
   }

   private void fightAir(LivingEntity target, double distance, boolean sees, UltronBossRules.Stage st) {
      if (this.move == UltronMove.PRIME_DIVE || this.move == UltronMove.PRIME_DASH) {
         return;
      }
      boolean air = this.airborne(target);
      this.orbit += 0.04;
      double ring = air ? 4.0 : 9.0;
      Vec3 spot = target.position().add(Math.cos(this.orbit) * ring, air ? 0.5 : 7.0, Math.sin(this.orbit) * ring);
      this.steerTo(spot, st == UltronBossRules.Stage.OVERDRIVE ? 0.6 : 0.48, air ? 1.5 : 5.0);
      this.faceTowards(target.position(), 14.0F);
      if (this.move != UltronMove.NONE) {
         return;
      }
      if (!air && (st == UltronBossRules.Stage.AERIAL && this.boutClock > AIR_BOUT || st != UltronBossRules.Stage.AERIAL && this.boutClock > OVERDRIVE_AIR)) {
         this.land();
         return;
      }
      double reach = 2.4 + target.getBbWidth() * 0.5;
      if (air && distance <= reach + 0.5) {
         if (this.attackCooldown <= 0) {
            UltronMove m = this.meleeChoice(target, false, UltronScenario.BossStyle.DUELIST);
            if (m == UltronMove.PRIME_SLAM) {
               m = UltronMove.PRIME_UPPERCUT;
            }
            this.startMove(m, target);
            this.attackCooldown = this.cooldown(10);
         }
         return;
      }
      if (!air && this.diveCooldown <= 0 && distance < 30.0 && sees) {
         this.startMove(UltronMove.PRIME_DIVE, target);
         this.diveCooldown = this.cooldown(st == UltronBossRules.Stage.AERIAL ? 120 : 160);
         return;
      }
      if (sees && this.beamCooldown <= 0 && distance < BEAM_RANGE - 2.0) {
         this.startMove(UltronMove.PRIME_BEAM, target);
         this.beamCooldown = this.cooldown(130);
         return;
      }
      if (sees && this.palmCooldown <= 0 && distance < 26.0) {
         this.startMove(UltronMove.PRIME_PALM_BLAST, target);
         this.palmCooldown = this.cooldown(45);
         return;
      }
      if (air && sees && this.dashCooldown <= 0 && distance > 6.0) {
         this.startMove(UltronMove.PRIME_DASH, target);
         this.dashCooldown = this.cooldown(70);
      }
   }

   private void takeOff() {
      this.setFlying(true);
      this.boutClock = 0;
      this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.6, 0.0));
      this.sound(UltronSounds.PRIME_JET, 1.6F, 1.0F);
      this.fx(UltronFxS2CPacket.JET, this.position(), new Vec3(0.0, -1.0, 0.0), 2.0F, this.getId());
   }

   private void land() {
      this.setFlying(false);
      this.boutClock = 0;
      this.landing = true;
   }

   // ------------------------------------------------------------------ moves

   @Override
   protected void onMoveStart(UltronMove m, @Nullable LivingEntity target) {
      switch (m) {
         case PRIME_PALM_BLAST -> {
            this.sound(UltronSounds.PRIME_BEAM_CHARGE, 1.0F, 1.35F);
            this.fx(UltronFxS2CPacket.CHARGE, this.local(-0.45, 1.55, 0.7), Vec3.ZERO, 1.0F, this.getId());
         }
         case PRIME_BEAM -> {
            this.sound(UltronSounds.PRIME_BEAM_CHARGE, 1.6F, 1.0F);
            this.beamDir = target != null ? target.getEyePosition().subtract(this.getEyePosition()).normalize() : this.facing();
         }
         case PRIME_DASH -> {
            Vec3 to = target != null ? target.position().subtract(this.position()) : this.facing();
            this.dashDir = this.flier() ? to.normalize() : new Vec3(to.x, 0.0, to.z).normalize();
            this.sound(UltronSounds.PRIME_DASH, 1.6F, 1.0F);
            this.fx(UltronFxS2CPacket.DASH_TRAIL, this.position().add(0.0, 1.4, 0.0), this.dashDir, 1.0F, this.getId());
         }
         case PRIME_SLAM -> this.sound(UltronSounds.PRIME_WHOOSH, 1.4F, 0.7F);
         case PRIME_DIVE -> {
            if (!this.flier()) {
               this.setFlying(true);
               this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.8, 0.0));
            }
            this.diveTo = null;
            this.sound(UltronSounds.PRIME_JET, 1.6F, 1.1F);
         }
         case PRIME_SUMMON -> {
            if (this.level() instanceof ServerLevel level) {
               UltronSounds.playWide(level, this.position(), UltronSounds.PRIME_SUMMON, 2.6F, 1.0F, 128.0);
            }
         }
         case PRIME_ABSORB -> this.fx(UltronFxS2CPacket.CHARGE, this.position().add(0.0, 1.6, 0.0), Vec3.ZERO, 2.0F, this.getId());
         case PRIME_TRANSITION_AIR, PRIME_TRANSITION_OVERDRIVE -> {
            if (this.level() instanceof ServerLevel level) {
               UltronSounds.playWide(level, this.position(), UltronSounds.PRIME_TRANSITION, 3.0F, 1.0F, 160.0);
               UltronNet.fxAlways(level, new UltronFxS2CPacket(UltronFxS2CPacket.PRIME_TRANSITION, this.position().add(0.0, 1.5, 0.0), Vec3.ZERO, 1.0F,
                  this.stage().ordinal()), this.position(), 160.0);
            }
         }
         default -> {
         }
      }
   }

   @Override
   protected void onMoveTick(UltronMove m, int tick, @Nullable LivingEntity target) {
      switch (m) {
         case PRIME_JAB_CROSS, PRIME_UPPERCUT, PRIME_BACKHAND -> {
            if (target != null) {
               this.faceTowards(target.position(), 20.0F);
            }
            for (int i = 0; i < m.contacts(); i++) {
               int c = m == UltronMove.PRIME_JAB_CROSS ? (i == 0 ? 6 : 13) : m.firstContact();
               if (tick == c - 3) {
                  this.sound(UltronSounds.PRIME_WHOOSH, 1.1F, m == UltronMove.PRIME_UPPERCUT ? 0.85F : 1.05F);
               }
               if (tick == c - 2 && !this.flier() && target != null && this.distanceTo(target) > 2.0F) {
                  Vec3 f = this.facing();
                  this.setDeltaMovement(this.getDeltaMovement().add(f.x * 0.28, 0.0, f.z * 0.28)); // steps into the punch
               }
            }
         }
         case PRIME_PALM_BLAST -> {
            if (target != null) {
               this.faceTowards(target.position(), 15.0F);
            }
         }
         case PRIME_BEAM -> {
            if (target != null && target.isAlive()) {
               Vec3 want = this.lead(target, this.getEyePosition(), 6.0, this.tethers.sensors() ? 0.5 : 0.0).subtract(this.getEyePosition()).normalize();
               double track = this.tethers.sensors() ? 0.2 : 0.13;
               this.beamDir = this.beamDir.add(want.subtract(this.beamDir).scale(track)).normalize();
            }
            Vec3 at = this.getEyePosition().add(this.beamDir.scale(10.0));
            this.faceTowards(at, 25.0F);
            this.setXRot((float)(-Math.asin(Mth.clamp(this.beamDir.y, -1.0, 1.0)) * Mth.RAD_TO_DEG));
            if (tick == 14) {
               this.setFlag(FLAG_BEAM, true);
               this.sound(UltronSounds.PRIME_BEAM, 2.0F, 1.0F);
            } else if (tick == 44) {
               this.setFlag(FLAG_BEAM, false);
            }
         }
         case PRIME_DASH -> {
            if (tick <= 7) {
               Vec3 v = this.dashDir.scale(1.35);
               this.setDeltaMovement(this.flier() ? v : new Vec3(v.x, this.getDeltaMovement().y, v.z));
               this.steer = v;
               float yaw = (float)(Mth.atan2(this.dashDir.z, this.dashDir.x) * Mth.RAD_TO_DEG) - 90.0F;
               this.setYRot(yaw);
               this.yBodyRot = yaw;
            } else {
               this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 1.0, 0.5));
               this.steer = this.steer.scale(0.5);
            }
         }
         case PRIME_SLAM -> {
            if (tick == 8 && this.onGround()) {
               Vec3 f = this.facing();
               this.setDeltaMovement(f.x * 0.25, 0.55, f.z * 0.25); // the leap before the slam
            }
         }
         case PRIME_DIVE -> {
            if (tick < 16) {
               this.steer = new Vec3(0.0, 0.42, 0.0); // rises, coils
               if (target != null) {
                  this.faceTowards(target.position(), 18.0F);
               }
            } else if (tick == 16) {
               this.diveTo = target != null ? target.position().add(target.getDeltaMovement().scale(this.tethers.sensors() ? 8.0 : 4.0))
                  : this.position().add(this.facing().scale(8.0)).add(0.0, -10.0, 0.0);
               this.sound(UltronSounds.PRIME_WHOOSH, 2.0F, 0.6F);
            }
            if (tick >= 16 && tick < 24 && this.diveTo != null) {
               Vec3 d = this.diveTo.subtract(this.position());
               double len = d.length();
               Vec3 v = len < 0.4 ? Vec3.ZERO : d.scale(Math.min(1.8, len / Math.max(1, 24 - tick)) / len);
               this.setDeltaMovement(v);
               this.steer = v;
               this.faceTowards(this.diveTo, 40.0F);
            } else if (tick > 24) {
               this.steer = this.steer.scale(0.6);
            }
         }
         case PRIME_TRANSITION_AIR -> {
            if (tick >= 40) {
               this.steer = new Vec3(0.0, tick < 60 ? 0.14 : 0.02, 0.0);
            }
         }
         case PRIME_ASCEND -> {
            this.setFlying(true);
            double up = tick < 30 ? 0.0 : 0.04 + (tick - 30) * 0.006;
            this.steer = new Vec3(0.0, up, 0.0);
            this.setDeltaMovement(this.steer);
            if (tick % 20 == 10) {
               this.fx(UltronFxS2CPacket.GLITCH, this.position().add(0.0, 1.5, 0.0), Vec3.ZERO, 1.0F, this.getId());
            }
         }
         default -> {
         }
      }
   }

   @Override
   protected void onContact(UltronMove m, int index, @Nullable LivingEntity target) {
      switch (m) {
         case PRIME_JAB_CROSS -> this.punch(target, index == 0 ? BalanceTable.ULTRON_PRIME_JAB : BalanceTable.ULTRON_PRIME_CROSS, 2.7, index == 0 ? 0.35 : 1.1,
            index == 0 ? 0.05 : 0.25);
         case PRIME_UPPERCUT -> this.punch(target, BalanceTable.ULTRON_PRIME_UPPERCUT, 2.7, 0.4, 1.15);
         case PRIME_BACKHAND -> {
            // a sweep: everything in the arc goes sideways
            Vec3 side = new Vec3(this.facing().z, 0.0, -this.facing().x);
            int hits = 0;
            for (LivingEntity e : this.around(this.local(0.0, 1.4, 0.6), 3.6)) {
               Vec3 to = e.position().subtract(this.position());
               if (to.dot(this.facing()) < -1.0) {
                  continue;
               }
               if (e.hurt(this.damageSources().mobAttack(this), BalanceTable.ULTRON_PRIME_BACKHAND * this.damageScale())) {
                  hits++;
                  this.impulse(e, side.scale(1.6).add(this.facing().scale(0.4)), 0.3);
                  this.fx(UltronFxS2CPacket.PRIME_IMPACT, e.position().add(0.0, e.getBbHeight() * 0.6, 0.0), side, 1.0F, this.getId());
               }
            }
            if (hits > 0) {
               this.sound(UltronSounds.PRIME_IMPACT, 1.6F, 0.9F);
            }
         }
         case PRIME_PALM_BLAST -> {
            if (!(this.level() instanceof ServerLevel level)) {
               return;
            }
            Vec3 palm = this.local(-0.45, 1.55, 0.9);
            this.sound(UltronSounds.PRIME_PALM, 2.0F, 1.0F);
            if (target != null && target.isAlive() && this.distanceTo(target) < 4.0F) {
               // point-blank: the repulsor goes straight through
               if (target.hurt(this.damageSources().indirectMagic(this, this), BalanceTable.ULTRON_PRIME_PALM * this.damageScale())) {
                  this.impulse(target, target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize().scale(2.2), 0.5);
               }
               this.fx(UltronFxS2CPacket.PRIME_BEAM, palm, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 0.5F, this.getId());
               this.fx(UltronFxS2CPacket.PRIME_IMPACT, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), this.facing(), 1.5F, this.getId());
               return;
            }
            Vec3 aim = target != null ? this.lead(target, palm, 1.7, this.tethers.sensors() ? 1.0 : 0.6) : palm.add(this.facing().scale(10.0));
            Vec3 dir = aim.subtract(palm);
            UltronBolt.shoot(level, this, palm, dir, 1.7, BalanceTable.ULTRON_PRIME_PALM * this.damageScale(), UltronBolt.STYLE_PALM);
            this.fx(UltronFxS2CPacket.MUZZLE, palm, dir.normalize(), 1.8F, this.getId());
         }
         case PRIME_BEAM -> {
            Vec3 eye = this.getEyePosition();
            Vec3 end = this.beam(eye, this.beamDir, BEAM_RANGE, BalanceTable.ULTRON_PRIME_BEAM, 0.45);
            this.fx(UltronFxS2CPacket.PRIME_BEAM, eye, end, 1.0F, this.getId());
         }
         case PRIME_SLAM -> {
            double r = this.stage() == UltronBossRules.Stage.OVERDRIVE ? 7.0 : 6.0;
            Vec3 at = this.local(0.0, 0.0, 1.0);
            this.blast(at, r, BalanceTable.ULTRON_PRIME_SLAM, 1.4, 0.6, true);
            this.fx(UltronFxS2CPacket.PRIME_SHOCKWAVE, at, Vec3.ZERO, (float)r, this.getId());
            if (this.level() instanceof ServerLevel level) {
               UltronSounds.playWide(level, at, UltronSounds.PRIME_SLAM, 2.6F, 1.0F, 96.0);
            }
         }
         case PRIME_DIVE -> {
            Vec3 at = this.position();
            this.blast(at, 4.5, BalanceTable.ULTRON_PRIME_DIVE, 1.6, 0.7, false);
            this.fx(UltronFxS2CPacket.PRIME_SHOCKWAVE, at, Vec3.ZERO, 4.5F, this.getId());
            this.fx(UltronFxS2CPacket.PRIME_IMPACT, at.add(0.0, 0.5, 0.0), new Vec3(0.0, -1.0, 0.0), 2.0F, this.getId());
            if (this.level() instanceof ServerLevel level) {
               UltronSounds.playWide(level, at, UltronSounds.PRIME_SLAM, 2.6F, 1.15F, 96.0);
            }
         }
         case PRIME_DASH -> {
            boolean hit = false;
            for (LivingEntity e : this.around(this.local(0.0, 1.2, 1.2), 2.6)) {
               if (e.hurt(this.damageSources().mobAttack(this), BalanceTable.ULTRON_PRIME_DASH * this.damageScale())) {
                  hit = true;
                  this.impulse(e, this.dashDir.multiply(1.0, 0.0, 1.0).normalize().scale(1.3), 0.35);
               }
            }
            if (hit) {
               this.sound(UltronSounds.PRIME_IMPACT, 1.4F, 1.1F);
               this.fx(UltronFxS2CPacket.PRIME_IMPACT, this.local(0.0, 1.3, 1.4), this.dashDir, 1.2F, this.getId());
            }
         }
         case PRIME_SUMMON -> {
            UltronUprising.bossSummon(this);
            this.fx(UltronFxS2CPacket.GLITCH, this.position().add(0.0, 1.5, 0.0), Vec3.ZERO, 1.5F, this.getId());
         }
         case PRIME_ABSORB -> {
            float heal = UltronUprising.bossAbsorb(this);
            if (heal > 0.0F) {
               this.heal(heal);
            }
         }
         case PRIME_TRANSITION_AIR -> {
            this.blast(this.position(), 8.0, 4.0F, 1.8, 0.8, false);
            this.fx(UltronFxS2CPacket.PRIME_SHOCKWAVE, this.position(), Vec3.ZERO, 8.0F, this.getId());
            this.setFlying(true);
         }
         case PRIME_TRANSITION_OVERDRIVE -> {
            this.blast(this.position(), 10.0, BalanceTable.ULTRON_PRIME_SLAM * 0.5F, 2.0, 0.9, false);
            this.fx(UltronFxS2CPacket.PRIME_SHOCKWAVE, this.position(), Vec3.ZERO, 10.0F, this.getId());
            if (this.tethers.summons()) {
               UltronUprising.bossSummon(this);
            }
         }
         case PRIME_ASSEMBLE -> {
            this.fx(UltronFxS2CPacket.PRIME_SHOCKWAVE, this.position(), Vec3.ZERO, 4.0F, this.getId());
            this.sound(UltronSounds.PRIME_IMPACT, 2.0F, 0.7F);
         }
         default -> {
         }
      }
   }

   @Override
   protected void onMoveEnd(UltronMove m) {
      switch (m) {
         case PRIME_ASSEMBLE -> {
            if (this.stage() == UltronBossRules.Stage.ASSEMBLY) {
               this.setStage(UltronBossRules.Stage.TACTICAL);
               UltronUprising.bossStage(this, UltronBossRules.Stage.TACTICAL);
            } else if (this.stage() == UltronBossRules.Stage.AERIAL) {
               this.setFlying(true);
            }
            this.boutClock = 0;
         }
         case PRIME_BEAM -> {
            this.setFlag(FLAG_BEAM, false);
            this.setXRot(0.0F);
         }
         case PRIME_DIVE -> {
            if (this.stage() != UltronBossRules.Stage.AERIAL) {
               this.land();
            }
         }
         case PRIME_TRANSITION_AIR -> {
            this.setFlying(true);
            this.boutClock = 0;
         }
         case PRIME_TRANSITION_OVERDRIVE -> {
            this.setFlying(false);
            this.boutClock = 0;
         }
         case PRIME_ASCEND -> {
            this.fx(UltronFxS2CPacket.GLITCH, this.position().add(0.0, 1.5, 0.0), Vec3.ZERO, 3.0F, this.getId());
            this.discard();
         }
         default -> {
         }
      }
   }

   private void punch(@Nullable LivingEntity target, float damage, double reach, double push, double lift) {
      if (this.strike(target, damage, reach, push, lift)) {
         this.sound(UltronSounds.PRIME_IMPACT, 1.5F, 1.0F);
         this.fx(UltronFxS2CPacket.PRIME_IMPACT, target.position().add(0.0, target.getBbHeight() * 0.6, 0.0), this.facing(), 1.0F, this.getId());
      }
   }

   private void impulse(LivingEntity e, Vec3 push, double lift) {
      if (!dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(e)) {
         return;
      }
      double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
      e.push(push.x * resist, lift * resist, push.z * resist);
      e.hurtMarked = true;
   }

   // ------------------------------------------------------------------ damage

   private boolean untouchable() {
      UltronBossRules.Stage st = this.stage();
      return st == UltronBossRules.Stage.ASSEMBLY || st == UltronBossRules.Stage.DEFEATED || this.ascending || this.move == UltronMove.PRIME_ASSEMBLE;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide) {
         return false;
      }
      Entity attacker = source.getEntity();
      if (UltronMob.side(attacker) || UltronMob.side(source.getDirectEntity())) {
         return false;
      }
      if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL) || source.is(DamageTypes.CRAMMING)) {
         return false;
      }
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return this.baseHurt(source, amount); // /kill (operator)
      }
      if (this.untouchable()) {
         this.fx(UltronFxS2CPacket.DEFLECT, this.getEyePosition(), Vec3.ZERO, 1.5F, this.getId());
         return false;
      }
      boolean transition = this.move == UltronMove.PRIME_TRANSITION_AIR || this.move == UltronMove.PRIME_TRANSITION_OVERDRIVE;
      float max = this.getMaxHealth();
      float a = Math.min(UltronBossRules.incoming(amount, this.tethers, transition), UltronBossRules.hitCap(max));
      float room = UltronBossRules.secondCap(max) - this.secondTaken;
      if (room <= 0.01F) {
         this.fx(UltronFxS2CPacket.DEFLECT, this.getEyePosition(), Vec3.ZERO, 0.6F, this.getId());
         return false;
      }
      a = Math.min(a, room);
      boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || attacker != null && attacker.distanceTo(this) > 5.0F;
      if (!this.baseHurt(source, a)) {
         return false;
      }
      this.secondTaken += a;
      this.staggerTaken += a;
      if (ranged) {
         this.rangedTaken += a;
      } else {
         this.meleeTaken += a;
      }
      UltronUprising.unitHurt(this, source, a, ranged);
      if (this.isAlive()) {
         this.checkStage();
         if (this.staggerTaken >= max * STAGGER_SHARE && this.heavyCooldown <= 0 && (this.move == UltronMove.NONE || this.interruptible(this.move))) {
            this.staggerTaken = 0.0F;
            this.heavyCooldown = 200;
            this.forceMove(UltronMove.HEAVY_HIT, null);
         }
      }
      return true;
   }

   private void checkStage() {
      UltronBossRules.Stage cur = this.stage();
      if (cur != UltronBossRules.Stage.TACTICAL && cur != UltronBossRules.Stage.AERIAL) {
         return;
      }
      UltronBossRules.Stage next = UltronBossRules.stageFor(this.share(), cur);
      if (next != cur) {
         this.setStage(next);
         this.boutClock = 0;
         this.forceMove(next == UltronBossRules.Stage.AERIAL ? UltronMove.PRIME_TRANSITION_AIR : UltronMove.PRIME_TRANSITION_OVERDRIVE, null);
         UltronUprising.bossStage(this, next);
      }
   }

   @Override
   public void die(DamageSource source) {
      this.setFlag(FLAG_BEAM, false);
      super.die(source);
      if (!this.level().isClientSide) {
         this.entityData.set(FLYING, false);
         this.setStage(UltronBossRules.Stage.DEFEATED);
         this.forceMove(UltronMove.PRIME_DEATH, null);
         if (this.level() instanceof ServerLevel level) {
            UltronSounds.playWide(level, this.position(), UltronSounds.PRIME_DEATH, 3.0F, 1.0F, 160.0);
         }
      }
   }

   @Override
   protected void tickDeath() {
      this.deathTime++;
      if (!this.onGround()) {
         this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
      }
      if (this.level().isClientSide || this.isRemoved()) {
         return;
      }
      if (this.deathTime % 15 == 5 && this.deathTime < this.deathTicks() - 10) {
         Vec3 at = this.position().add((this.random.nextDouble() - 0.5) * 0.8, 0.6 + this.random.nextDouble() * 2.0, (this.random.nextDouble() - 0.5) * 0.8);
         this.fx(UltronFxS2CPacket.SHUTDOWN, at, Vec3.ZERO, 1.0F, this.getId());
         this.sound(UltronSounds.DEBRIS, 0.8F, 1.3F);
      }
      if (this.deathTime >= this.deathTicks()) {
         Vec3 at = this.position().add(0.0, 1.2, 0.0);
         if (this.level() instanceof ServerLevel level) {
            UltronNet.fxAlways(level, new UltronFxS2CPacket(UltronFxS2CPacket.PRIME_DEATH, at, Vec3.ZERO, 1.0F, this.getId()), at, 160.0);
            UltronSounds.playWide(level, at, UltronSounds.DEBRIS, 2.4F, 0.7F, 96.0);
         }
         this.remove(Entity.RemovalReason.KILLED);
      }
   }

   @Override
   protected SoundEvent getDeathSound() {
      return null; // played wide in die()
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (STAGE.equals(key) && this.level().isClientSide) {
         UltronBossRules.Stage now = this.stage();
         if (now != this.clientStage) {
            this.clientStage = now;
            this.clientStageStart = this.tickCount;
         }
      }
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected SoundEvent hurtSound() {
      return UltronSounds.PRIME_HURT.get();
   }

   @Override
   protected SoundEvent deathSound() {
      return UltronSounds.PRIME_DEATH.get();
   }
}

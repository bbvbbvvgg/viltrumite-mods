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
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossBrain.Attack;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossBrain.Phase;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossBrain.Threat;
import java.util.ArrayList;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Venom boss (spec §9): a summoned symbiote in a permanent frenzy with its own AI - claw combos, tendril yanks and
 * throws, mega leaps, the lash sweep, wall climbing, a shriek when fire or a loud sound hits it - and, when the config
 * allows ({@code venomBossPossession}), the host takeover of a weakened player through the same server manager the
 * player Venom uses (shorter, same end rules, same immunity). Its moves reuse {@link VenomMove} so the client poses it
 * with the player's clips.
 * <p>
 * Three phases and an adaptive mind ({@link VenomBossBrain}, spec §9.2-§9.4): at two thirds of its health it roars
 * into the FRENZY (faster, the tendril impale and the crashing dive), at one third into STARVING (the boss bar turns
 * white and darkens the sky; tendril storms burst from the ground under the target after a visible mark, and a held
 * victim is bitten, which heals it unless fire stopped its regeneration). It reads how it is fought: arrows and beams
 * raise a shell, kiting gets yanked and leapt at, a melee flurry is swept off, fire makes it jump back, a player on a
 * roof is pounced on from the wall. Fire and loud sounds still make it recoil every time.
 */
public class VenomBossEntity extends Monster implements VenomPossessor {
   static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> SERIAL = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VenomBossEntity.class, EntityDataSerializers.INT);
   /** Shell against shooters (ticks) and what it lets through. */
   static final int SHELL_TICKS = 50;
   static final float SHELL_PROJECTILE_TAKEN = 0.25F;
   /** The phase roar: damage taken while it roars, the push ring. */
   static final float ROAR_TAKEN = 0.3F;
   static final double ROAR_RADIUS = 6.0;
   /** Tendril storm: spikes per wave, the ring radius, the mark-to-burst delay, the second wave. */
   static final int STORM_SPIKES = 5;
   static final double STORM_RING = 2.6;
   static final int STORM_DELAY = 14;
   static final int STORM_SECOND_WAVE = 22;
   static final double SPIKE_RADIUS = 1.4;
   static final float SPIKE_DAMAGE = 10.0F;
   /** The starving bite: damage and the health it eats back. */
   static final float BITE_DAMAGE = 5.0F;
   static final float BITE_HEAL = 8.0F;
   public static final float MAX_HEALTH = 300.0F;

   private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.viltrumitecore.venom_boss"), BossEvent.BossBarColor.PURPLE,
      BossEvent.BossBarOverlay.NOTCHED_10);
   @Nullable private VenomMove move;
   private int elapsed;
   private int combo;
   private final VenomBossBrain brain = new VenomBossBrain();
   private int recoilImmune;
   private int shellTicks;
   private boolean diving;
   private int airTicks;
   private int tauntCd;
   private final List<Spike> spikes = new ArrayList<>();
   private int stormWaveAt = -1;
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
      this.entityData.define(PHASE, 0);
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

   /** 0 hunt, 1 frenzy, 2 starving (synced; the renderer grows the body with it). */
   public int phaseId() {
      return this.entityData.get(PHASE);
   }

   /** Server: the mind (tests and the /venom debug output read it). */
   public VenomBossBrain brain() {
      return this.brain;
   }

   // --- server AI ----------------------------------------------------------------------------------------------

   private void start(VenomMove m) {
      if (this.move == VenomMove.SHELL && m != VenomMove.SHELL) {
         this.flag(VenomFlags.SHELL, false);
      }
      this.move = m;
      this.elapsed = 0;
      this.entityData.set(MOVE, m.ordinal());
      this.entityData.set(SERIAL, (this.serial() + 1) & 0xFFFF);
   }

   private void endMove() {
      if (this.move == VenomMove.SHELL) {
         this.flag(VenomFlags.SHELL, false);
      }
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
      float health = this.getHealth() / this.getMaxHealth();
      this.bossBar.setProgress(health);
      Phase next = this.brain.health(health);
      if (next != null) {
         enterPhase(next, true);
      }
      this.brain.tick();
      tickTimers();
      tickSpikes();
      if (this.possessing) {
         driveHost();
         return;
      }
      LivingEntity target = this.getTarget();
      holdTick(target);
      tickDive();
      if (this.move != null) {
         this.elapsed++;
         if (this.elapsed == this.move.contact) {
            contact(this.move, target);
         }
         if (this.move == VenomMove.SHELL) {
            // the shell walks on, slower; it hardens against what it learned to fear
            if (target != null && target.isAlive()) {
               this.getLookControl().setLookAt(target, 30.0F, 30.0F);
               this.getNavigation().moveTo(target, 0.8 * this.brain.phase().speedScale);
            }
            if (--this.shellTicks <= 0) {
               endMove();
            }
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
      double above = target.getY() - this.getY();
      this.brain.watch(d, above);
      Threat learned = this.brain.newlyAdapted();
      if (learned != null) {
         taunt("boss_adapt_" + learned.name().toLowerCase(java.util.Locale.ROOT));
      }
      this.getLookControl().setLookAt(target, 30.0F, 30.0F);
      this.getNavigation().moveTo(target, 1.15 * this.brain.phase().speedScale);
      // climbing: a wall in the way is just another floor
      boolean wall = this.horizontalCollision && target.getY() > this.getY() - 1;
      if (wall) {
         Vec3 v = this.getDeltaMovement();
         double climb = VenomRules.WALL_CLIMB_SPEED + (this.brain.adapted(Threat.HEIGHT) ? 0.16 : 0.06);
         this.setDeltaMovement(v.x, Math.max(v.y, climb), v.z);
         this.fallDistance = 0;
      }
      this.flag(VenomFlags.WALL, wall);
      int nearby = VenomTargets.around(this, this.position(), 4.5, e -> e != this && e instanceof Player p && !p.isCreative() && !p.isSpectator()).size();
      boolean mayTakeover = target instanceof ServerPlayer host && VenomPossession.bossMayPossess()
         && host.getHealth() / host.getMaxHealth() <= this.brain.takeoverFraction() && VenomPossession.eligible(this, host);
      Attack attack = this.brain.choose(new VenomBossBrain.Situation(d, above, this.hasLineOfSight(target), this.onGround(), wall, nearby, mayTakeover));
      perform(attack, target, d);
   }

   /** Starts what the brain chose; {@link VenomBossBrain#used} only once it really began. */
   private void perform(Attack attack, LivingEntity target, double d) {
      switch (attack) {
         case ROAR -> {
            releaseHeld(null);
            this.start(VenomMove.RAGE);
            this.brain.used(Attack.ROAR);
            VenomSounds.play(this, VenomSounds.ROAR, 2.0F, this.brain.phase() == Phase.STARVING ? 0.7F : 0.8F);
            VenomFx.send(this, VenomFx.TRANSFORM, VenomTargets.center(this), null, 1.6F, 0);
         }
         case TAKEOVER -> {
            if (target instanceof ServerPlayer host && VenomPossession.engulfByBoss(this, host)) {
               this.start(VenomMove.ENGULF);
               this.brain.used(Attack.TAKEOVER);
            } else {
               this.brain.delay(Attack.TAKEOVER, 100);
            }
         }
         case RETREAT -> {
            Vec3 away = VenomTargets.flat(this.position().subtract(target.position()));
            away = away.lengthSqr() < 1.0E-4 ? VenomTargets.flat(this.getLookAngle()).scale(-1) : away.normalize();
            this.setDeltaMovement(away.scale(1.1).add(0, 0.55, 0));
            this.hasImpulse = true;
            this.brain.used(Attack.RETREAT);
            VenomSounds.play(this, VenomSounds.SQUIRM, 1.0F, VenomSounds.jitter(this, 1.1F));
            VenomFx.send(this, VenomFx.LEAP, this.position(), null, 0.7F, 0);
         }
         case LASH_SWEEP -> {
            this.start(VenomMove.LASH_SWEEP);
            this.brain.used(Attack.LASH_SWEEP);
            VenomSounds.play(this, VenomSounds.LASH, 1.0F, 0.9F);
         }
         case STORM -> {
            this.start(VenomMove.SLAM);
            this.brain.used(Attack.STORM);
            VenomSounds.play(this, VenomSounds.LASH, 1.3F, 0.7F);
         }
         case SHELL -> {
            this.start(VenomMove.SHELL);
            this.shellTicks = SHELL_TICKS;
            this.flag(VenomFlags.SHELL, true);
            this.brain.used(Attack.SHELL);
            VenomSounds.play(this, VenomSounds.SHELL, 1.1F, VenomSounds.jitter(this, 0.9F));
         }
         case POUNCE, DIVE -> {
            Vec3 to = target.position().subtract(this.position());
            double[] v = VenomBossBrain.pounce(to.x, attack == Attack.DIVE ? Math.max(to.y, 8.0) : to.y, to.z);
            this.setDeltaMovement(v[0], v[1], v[2]);
            this.hasImpulse = true;
            this.diving = true;
            this.airTicks = 0;
            this.flag(VenomFlags.WALL, false);
            this.brain.used(attack);
            VenomFx.send(this, VenomFx.LEAP, this.position(), null, 1.3F, 0);
            VenomSounds.play(this, VenomSounds.ROAR, 1.3F, VenomSounds.jitter(this, 0.8F));
         }
         case CLAW -> {
            this.combo = (this.combo + 1) % 3;
            this.start(VenomMove.claw(this.combo));
            this.brain.used(Attack.CLAW);
            if (this.combo == 2) {
               this.brain.delay(Attack.CLAW, Math.round(22 * this.brain.phase().cooldownScale));
            }
            VenomSounds.play(this, VenomSounds.CLAW, 0.9F, VenomSounds.jitter(this, 0.9F));
         }
         case IMPALE -> {
            this.start(VenomMove.LASH_IMPALE);
            this.entityData.set(TARGET, target.getId());
            this.brain.used(Attack.IMPALE);
            VenomSounds.play(this, VenomSounds.LASH, 1.0F, 1.1F);
         }
         case TENDRIL -> {
            this.start(VenomMove.TENDRIL_SHOT);
            this.brain.used(Attack.TENDRIL);
            VenomSounds.play(this, VenomSounds.TENDRIL_SHOT, 1.0F, 1.0F);
         }
         case LEAP -> {
            Vec3 to = target.position().subtract(this.position());
            Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
            this.setDeltaMovement(flat.scale(Math.min(1.6, d * 0.09)).add(0, 0.95, 0));
            this.hasImpulse = true;
            this.brain.used(Attack.LEAP);
            VenomFx.send(this, VenomFx.LEAP, this.position(), null, 1.2F, 0);
            VenomSounds.play(this, VenomSounds.ROAR, 1.2F, VenomSounds.jitter(this, 0.85F));
         }
         case NONE -> {
         }
      }
   }

   /** A phase border: the bar, the synced phase, the line (the roar itself comes from the brain). */
   private void enterPhase(Phase phase, boolean announce) {
      this.entityData.set(PHASE, phase.ordinal());
      this.bossBar.setColor(switch (phase) {
         case HUNT -> BossEvent.BossBarColor.PURPLE;
         case FRENZY -> BossEvent.BossBarColor.RED;
         case STARVING -> BossEvent.BossBarColor.WHITE;
      });
      this.bossBar.setDarkenScreen(phase == Phase.STARVING);
      if (announce && phase != Phase.HUNT) {
         this.tauntCd = 0;
         taunt(phase == Phase.FRENZY ? "boss_frenzy" : "boss_starving");
      }
   }

   /** A subtitle line to the players around (text only; the boss has no voice audio, like the player Venom). */
   private void taunt(String key) {
      if (this.tauntCd > 0 || !(this.level() instanceof ServerLevel level)) {
         return;
      }
      this.tauntCd = 160;
      List<ServerPlayer> audience = new ArrayList<>();
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(this) < 48 * 48) {
            audience.add(p);
         }
      }
      HeroSpeech.announce(audience, this, "venom", "venom:" + key, "subtitle.viltrumitecore.speaker.venom",
         List.of("subtitle.viltrumitecore.venom." + key), HeroLine.Priority.ULTIMATE);
   }

   /** The end of a pounce or a dive: the tendrils slam out where it lands. */
   private void tickDive() {
      if (!this.diving) {
         return;
      }
      this.airTicks++;
      if (this.airTicks > 80 || this.isInWater() || this.isInLava()) {
         this.diving = false;
         return;
      }
      if (this.airTicks > 3 && this.onGround()) {
         this.diving = false;
         if (this.move == null || this.move.cancellable(this.elapsed)) {
            this.start(VenomMove.LASH_SLAM);
            VenomSounds.play(this, VenomSounds.SLAM, 1.2F, 0.85F);
         }
      }
   }

   /** One spike of the tendril storm: marked on the ground, bursting {@link #STORM_DELAY} ticks later. */
   private record Spike(Vec3 at, int burstAt) {
   }

   private void stormWave(LivingEntity target) {
      Vec3 feet = target.position();
      spike(feet);
      double turn = this.random.nextDouble() * Math.PI * 2;
      for (int i = 0; i < STORM_SPIKES; i++) {
         double a = turn + i * Math.PI * 2 / STORM_SPIKES;
         spike(feet.add(Math.cos(a) * STORM_RING, 0, Math.sin(a) * STORM_RING));
      }
      VenomSounds.playAt(this, feet, VenomSounds.TENDRIL_STICK, 1.0F, 0.7F);
   }

   private void spike(Vec3 at) {
      this.spikes.add(new Spike(at, this.tickCount + STORM_DELAY));
      VenomFx.send(this, VenomFx.STICK, at.add(0, 0.05, 0), null, 0.8F, 0);
   }

   private void tickSpikes() {
      if (this.stormWaveAt >= 0 && this.tickCount >= this.stormWaveAt) {
         this.stormWaveAt = -1;
         LivingEntity target = this.getTarget();
         if (target != null && target.isAlive() && target.distanceToSqr(this) < 24 * 24 && !this.possessing) {
            stormWave(target);
         }
      }
      if (this.spikes.isEmpty()) {
         return;
      }
      List<Spike> due = new ArrayList<>();
      this.spikes.removeIf(sp -> {
         if (this.tickCount >= sp.burstAt()) {
            due.add(sp);
            return true;
         }
         return false;
      });
      for (Spike sp : due) {
         Vec3 at = sp.at();
         for (LivingEntity e : VenomTargets.around(this, at.add(0, 0.8, 0), SPIKE_RADIUS, e -> e != this && legal(e) && !VenomPossession.involved(e))) {
            if (VenomTargets.strike(this, e, SPIKE_DAMAGE)) {
               VenomTargets.push(e, new Vec3(0, 0.85, 0));
               VenomFx.send(this, VenomFx.CLAW, VenomTargets.center(e), at, 1.0F, 0);
            }
         }
         VenomFx.send(this, VenomFx.SLAM, at, null, 0.55F, 0);
         VenomSounds.playAt(this, at, VenomSounds.LASH, 0.9F, VenomSounds.jitter(this, 1.15F));
      }
   }

   private void tickTimers() {
      if (this.tauntCd > 0) this.tauntCd--;
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
         case LASH_IMPALE -> {
            Vec3 eye = this.getEyePosition();
            Vec3 look = target != null ? VenomTargets.center(target).subtract(eye).normalize() : this.getLookAngle();
            Vec3 end = VenomTargets.clip(this, eye, eye.add(look.scale(VenomBossBrain.IMPALE_MAX)));
            for (LivingEntity e : VenomTargets.along(this, eye, end, 0.8, e -> e != this && legal(e))) {
               if (VenomTargets.strike(this, e, VenomRules.LASH_IMPALE_DAMAGE + 1.0F)) {
                  VenomTargets.push(e, look.scale(0.6).add(0, 0.25, 0));
                  VenomFx.send(this, VenomFx.CLAW, VenomTargets.center(e), eye, 1.2F, 0);
                  VenomSounds.playAt(this, VenomTargets.center(e), VenomSounds.CLAW_HIT, 1.0F, VenomSounds.jitter(this, 0.8F));
               }
            }
            VenomFx.send(this, VenomFx.STICK, end, eye, 1.0F, 0);
            this.entityData.set(TARGET, -1);
         }
         case SLAM -> {
            // the tendril storm: the hands go into the ground, the spikes come out under the target
            VenomFx.send(this, VenomFx.SLAM, this.position(), null, 1.0F, 0);
            if (target != null && target.isAlive()) {
               stormWave(target);
               this.stormWaveAt = this.tickCount + STORM_SECOND_WAVE;
            }
         }
         case LASH_SLAM -> {
            double r = VenomRules.LASH_SLAM_RADIUS + 0.5;
            for (LivingEntity e : VenomTargets.around(this, this.position(), r, e -> e != this && legal(e))) {
               if (VenomTargets.strike(this, e, VenomRules.LASH_SLAM_DAMAGE)) {
                  VenomTargets.push(e, VenomTargets.outward(this.position(), e).scale(1.0).add(0, 0.5, 0));
               }
            }
            VenomFx.send(this, VenomFx.SLAM, this.position(), null, 1.5F, 0);
            VenomSounds.play(this, VenomSounds.SLAM, 1.4F, 0.8F);
         }
         case RAGE -> {
            for (LivingEntity e : VenomTargets.around(this, VenomTargets.center(this), ROAR_RADIUS, e -> e != this && legal(e))) {
               VenomTargets.strike(this, e, 4.0F);
               VenomTargets.push(e, VenomTargets.outward(this.position(), e).scale(1.2).add(0, 0.45, 0));
            }
            VenomFx.send(this, VenomFx.SLAM, this.position(), null, 1.6F, 0);
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
      boolean starving = this.brain.phase() == Phase.STARVING;
      if (starving && this.move == VenomMove.HOLD && VenomBossBrain.biteAt(this.holdTicks) && VenomTargets.strike(this, t, BITE_DAMAGE)) {
         // the starving symbiote eats: it heals, unless fire stopped its regeneration
         if (this.fireBlock <= 0) {
            this.heal(BITE_HEAL);
         }
         VenomFx.send(this, VenomFx.CLAW, VenomTargets.center(t), VenomTargets.center(this), 1.1F, 0);
         VenomSounds.playAt(this, VenomTargets.center(t), VenomSounds.CLAW_HIT, 1.0F, VenomSounds.jitter(this, 0.75F));
         VenomSounds.play(this, VenomSounds.GRAB, 0.8F, VenomSounds.jitter(this, 0.8F));
      }
      if (this.move == VenomMove.HOLD && this.holdTicks > (starving ? 40 : 30)) {
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

   /** hellfire_scalds_symbiote: no regeneration for a while, then the usual shriek and recoil (if it can). */
   public void scald() {
      this.fireBlock = Math.max(this.fireBlock, VenomRules.SCALD_REGEN_BLOCK);
      this.sinceHurt = 0;
      recoil();
   }

   /** A loud sound in the level: every boss in earshot recoils. */
   public static void loudSound(ServerLevel level, Vec3 at, @Nullable ResourceLocation id, float volume) {
      List<VenomBossEntity> bosses = level.getEntitiesOfClass(VenomBossEntity.class, new AABB(at, at).inflate(VenomRules.SOUND_RADIUS * 3));
      for (VenomBossEntity boss : bosses) {
         if (VenomWeakness.loud(id, volume, boss.position().distanceTo(at))) {
            boss.brain.hurt(Threat.SOUND, 6.0F);
            boss.recoil();
         }
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide || this.isInvulnerableTo(source)) {
         return super.hurt(source, amount);
      }
      Entity attacker = source.getEntity();
      boolean ranged = source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE)
         || attacker != null && attacker != this && attacker.distanceTo(this) > 6.0F;
      if (VenomTargets.fire(source)) {
         amount *= VenomRules.FIRE_TAKEN;
         this.fireBlock = VenomRules.FIRE_REGEN_BLOCK;
         this.brain.hurt(Threat.FIRE, amount);
         recoil();
      } else if (VenomTargets.sonic(source)) {
         amount *= VenomRules.SONIC_TAKEN;
         this.brain.hurt(Threat.SOUND, amount);
         recoil();
      } else if (ranged) {
         this.brain.hurt(Threat.RANGED, amount);
         if (this.move == VenomMove.SHELL) {
            amount *= SHELL_PROJECTILE_TAKEN;
            VenomSounds.play(this, VenomSounds.SHELL, 0.6F, VenomSounds.jitter(this, 1.4F));
         }
      } else if (attacker != null && attacker != this) {
         this.brain.hurt(Threat.MELEE, amount);
         if (this.move == VenomMove.SHELL) {
            amount *= 1.0F - VenomRules.SHELL_ABSORB;
         }
      }
      if (this.move == VenomMove.RAGE) {
         amount *= ROAR_TAKEN;
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
      this.spikes.clear();
      this.stormWaveAt = -1;
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
      // the phase follows the saved health, silently (no roar on a reload); adaptation starts fresh
      this.brain.restore(this.getHealth() / this.getMaxHealth());
      enterPhase(this.brain.phase(), false);
   }
}

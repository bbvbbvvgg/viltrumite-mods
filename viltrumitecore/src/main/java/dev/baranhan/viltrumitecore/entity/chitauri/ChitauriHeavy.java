package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriDestruction;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chitauri heavy assault trooper: a bulked frame in thick plate with a reactor on its back and a two-handed plasma
 * cannon. It walks into the fight slowly, braces and fires one charged cannon bolt (splash), vents the cannon after
 * every shot, and answers anyone who closes in with an overhead ground slam (wind-up, contact, shockwave). Its chest
 * plate breaks off at half health (armor drops). No single hit takes more than a third of its health.
 */
public class ChitauriHeavy extends ChitauriSoldier {
   public static final byte EVENT_SLAM = 76, EVENT_VENT = 77, EVENT_ARMOR_BREAK = 78, EVENT_DISEMBARK = 79, EVENT_STAGGER = 74;
   private static final int FLAG_BROKEN = 16;
   /** Slam timing: wind-up until contact, then the recovery. */
   public static final int SLAM_CONTACT = 14, SLAM_TICKS = 34;

   private int slamTimer = -1;
   private int slamCooldown;

   public int clientSlamStart = -1000;
   public int clientVentStart = -1000;
   public int clientBreakStart = -1000;
   public int clientDisembarkStart = -1000;
   public int clientStaggerStart = -1000;

   public ChitauriHeavy(EntityType<? extends ChitauriHeavy> type, Level level) {
      super(type, level);
      this.setMaxUpStep(1.1F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, BalanceTable.CHITAURI_HEAVY_HEALTH)
         .add(Attributes.MOVEMENT_SPEED, 0.21)
         .add(Attributes.FOLLOW_RANGE, 44.0)
         .add(Attributes.ATTACK_DAMAGE, BalanceTable.CHITAURI_SLAM)
         .add(Attributes.ATTACK_KNOCKBACK, 1.4)
         .add(Attributes.ARMOR, BalanceTable.CHITAURI_HEAVY_ARMOR)
         .add(Attributes.ARMOR_TOUGHNESS, 6.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.85);
   }

   @Override
   protected void registerRoleGoals() {
      this.goalSelector.addGoal(1, new SlamGoal(this));
   }

   public boolean armorBroken() {
      return this.flag(FLAG_BROKEN);
   }

   // ---- weapon: one charged cannon bolt, long cooldown, vent after each shot ----

   @Override
   protected int burstSize() {
      return 1;
   }

   @Override
   protected int chargeTicks() {
      return 26;
   }

   @Override
   protected int burstCooldown() {
      return 56 + this.random.nextInt(30);
   }

   @Override
   protected double meleeRange() {
      return 0.0; // the slam goal handles close range
   }

   @Override
   protected double fireRange() {
      return 30.0;
   }

   @Override
   protected boolean mayFight() {
      return this.slamTimer < 0;
   }

   @Override
   public Vec3 muzzle() {
      Vec3 look = this.getViewVector(1.0F);
      float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
      Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
      return this.getEyePosition().add(look.scale(1.5)).add(right.scale(0.3)).add(0, -0.75, 0);
   }

   @Override
   protected void fireAt(LivingEntity target) {
      ServerLevel level = (ServerLevel)this.level();
      Vec3 from = this.muzzle();
      Vec3 aim = target.position().add(0, target.getBbHeight() * 0.4, 0);
      double dist = from.distanceTo(aim);
      aim = aim.add(target.getDeltaMovement().multiply(1.0, 0.2, 1.0).scale(dist / 1.8 * 0.7));
      ChitauriBolt.shoot(level, this, from, aim.subtract(from), 1.8, 2.0F, (float)(1.0 + dist * 0.04) * (this.commanded() ? 0.7F : 1.0F));
      this.level().broadcastEntityEvent(this, EVENT_SHOOT);
      ChitauriSounds.play(level, from, ChitauriSounds.HEAVY_CANNON, 3.0F, 0.9F + this.random.nextFloat() * 0.12F);
      ChitauriDestruction.danger(level, this.position(), 18.0F, 100);
   }

   @Override
   protected void onBurstEnd() {
      this.level().broadcastEntityEvent(this, EVENT_VENT);
      ChitauriSounds.play((ServerLevel)this.level(), this.position(), ChitauriSounds.HEAVY_VENT, 1.2F, 0.95F + this.random.nextFloat() * 0.1F);
   }

   @Override
   public void onDisembark() {
      this.level().broadcastEntityEvent(this, EVENT_DISEMBARK);
   }

   @Override
   protected float capHit(DamageSource source, float amount) {
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return amount;
      }
      return Math.min(amount, this.getMaxHealth() * BalanceTable.CHITAURI_HEAVY_HIT_CAP);
   }

   @Override
   protected void afterHurt(DamageSource source, float amount) {
      ServerLevel level = (ServerLevel)this.level();
      if (!this.armorBroken() && this.isAlive() && this.getHealth() <= this.getMaxHealth() * 0.5F) {
         // the chest plate tears off: softer from now on
         this.setFlag(FLAG_BROKEN, true);
         AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
         if (armor != null) {
            armor.setBaseValue(4.0);
         }
         this.level().broadcastEntityEvent(this, EVENT_ARMOR_BREAK);
         ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.ARMOR_BREAK, this.position().add(0, 1.6, 0), this.getLookAngle(), 1.0F,
            this.random.nextInt()), this.position(), 64);
         ChitauriSounds.play(level, this.position(), ChitauriSounds.ARMOR_BREAK, 1.6F, 0.9F);
      } else if (amount >= this.getMaxHealth() * 0.2F && this.slamTimer < 0) {
         this.level().broadcastEntityEvent(this, EVENT_STAGGER);
      }
   }

   void slamContact() {
      ServerLevel level = (ServerLevel)this.level();
      float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
      Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
      Vec3 at = this.position().add(fwd.scale(1.6));
      double radius = 3.5;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 2.0, radius),
         e -> e.isAlive() && !ChitauriMob.is(e) && ChitauriMob.validTarget(e))) {
         double d = e.position().distanceTo(at);
         if (d > radius) {
            continue;
         }
         float f = (float)(1.0 - d / radius);
         e.hurt(this.damageSources().mobAttack(this), BalanceTable.CHITAURI_SLAM * (0.5F + 0.5F * f));
         if (HeroRegistry.allowsImpulse(e)) {
            Vec3 away = e.position().subtract(this.position()).normalize();
            e.knockback(0.9 * f + 0.3, -away.x, -away.z);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.35 * f, 0));
            e.hurtMarked = true;
         }
      }
      ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.SLAM, at, fwd, 1.0F, this.random.nextInt()), at, 64);
      ChitauriSounds.play(level, at, ChitauriSounds.HEAVY_SLAM, 2.5F, 0.85F + this.random.nextFloat() * 0.1F);
      ChitauriDestruction.danger(level, at, 10.0F, 60);
   }

   @Override
   public void handleEntityEvent(byte id) {
      switch (id) {
         case EVENT_SLAM -> this.clientSlamStart = this.tickCount;
         case EVENT_VENT -> this.clientVentStart = this.tickCount;
         case EVENT_ARMOR_BREAK -> this.clientBreakStart = this.tickCount;
         case EVENT_DISEMBARK -> this.clientDisembarkStart = this.tickCount;
         case EVENT_STAGGER -> this.clientStaggerStart = this.tickCount;
         default -> super.handleEntityEvent(id);
      }
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return ChitauriSounds.HEAVY_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return ChitauriSounds.HEAVY_DEATH.get();
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return ChitauriSounds.HEAVY_GROWL.get();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound(ChitauriSounds.HEAVY_STEP.get(), 0.7F, 0.85F + this.random.nextFloat() * 0.15F);
   }

   /** Close range: wind up over the head, slam the cannon down (contact frame), shockwave, recover. */
   static final class SlamGoal extends Goal {
      private final ChitauriHeavy h;

      SlamGoal(ChitauriHeavy h) {
         this.h = h;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         LivingEntity t = this.h.getTarget();
         if (this.h.slamCooldown > 0) {
            this.h.slamCooldown--;
         }
         return t != null && ChitauriMob.validTarget(t) && !this.h.isPassenger() && !this.h.severed() && this.h.slamCooldown <= 0
            && this.h.distanceToSqr(t) < 3.4 * 3.4;
      }

      @Override
      public boolean canContinueToUse() {
         return this.h.slamTimer >= 0 && this.h.isAlive();
      }

      @Override
      public void start() {
         this.h.slamTimer = 0;
         this.h.getNavigation().stop();
         this.h.level().broadcastEntityEvent(this.h, EVENT_SLAM);
         ChitauriSounds.play((ServerLevel)this.h.level(), this.h.position(), ChitauriSounds.HEAVY_WINDUP, 1.4F, 1.0F);
      }

      @Override
      public void stop() {
         this.h.slamTimer = -1;
         this.h.slamCooldown = 50 + this.h.random.nextInt(30);
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void tick() {
         LivingEntity t = this.h.getTarget();
         if (t != null && this.h.slamTimer < SLAM_CONTACT) {
            this.h.getLookControl().setLookAt(t, 20.0F, 20.0F);
            this.h.yBodyRot = Mth.approachDegrees(this.h.yBodyRot, this.h.yHeadRot, 9.0F);
         }
         if (++this.h.slamTimer == SLAM_CONTACT) {
            this.h.slamContact();
         }
         if (this.h.slamTimer >= SLAM_TICKS) {
            this.h.slamTimer = -1;
         }
      }
   }
}

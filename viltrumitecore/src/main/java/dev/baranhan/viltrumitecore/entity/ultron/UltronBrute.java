package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUnitKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy assault robot: a hunched armoured walker with piston arms, a hull-mounted deflector plate and two
 * shoulder missile pods. Slams the ground (shockwave around it), swings, charges across open ground and
 * fires missile salvos. Under the DEFLECTOR protocol it raises its plate against ranged fire. Its pods
 * blow off when it is badly damaged.
 */
public class UltronBrute extends UltronUnit {
   private static final int SHIELD_TICKS = 100;
   private int missileCooldown = 60;
   private int chargeCooldown = 40;
   private int slamCooldown;
   private int shieldTicks;
   private int pod;
   private Vec3 chargeDir = Vec3.ZERO;
   private final Set<Integer> chargeHits = new HashSet<>();

   public UltronBrute(EntityType<? extends UltronBrute> type, Level level) {
      super(type, level);
      this.setMaxUpStep(1.1F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 110.0)
         .add(Attributes.ARMOR, 14.0)
         .add(Attributes.ARMOR_TOUGHNESS, 6.0)
         .add(Attributes.MOVEMENT_SPEED, 0.22)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
         .add(Attributes.ATTACK_DAMAGE, BalanceTable.ULTRON_BRUTE_SWING)
         .add(Attributes.FOLLOW_RANGE, 64.0);
   }

   @Override
   public UltronUnitKind kind() {
      return UltronUnitKind.BRUTE;
   }

   /** Shield held up (the renderer blends in the hold stance). */
   @Override
   protected float clientHoldTarget() {
      return this.isAlive() && this.flag(FLAG_SHIELD) ? 1.0F : 0.0F;
   }

   @Override
   public int deathTicks() {
      return 40;
   }

   @Override
   protected double senseRange() {
      return 34.0;
   }

   @Override
   public float stride(float speed) {
      return 2.2F + 1.6F * Math.min(1.0F, speed / 0.25F);
   }

   @Override
   protected boolean interruptible(UltronMove m) {
      return m != UltronMove.BRUTE_SLAM && m != UltronMove.BRUTE_MISSILES && m != UltronMove.BRUTE_CHARGE;
   }

   @Override
   protected void customServerAiStep() {
      if (this.missileCooldown > 0) {
         this.missileCooldown--;
      }
      if (this.chargeCooldown > 0) {
         this.chargeCooldown--;
      }
      if (this.slamCooldown > 0) {
         this.slamCooldown--;
      }
      if (this.shieldTicks > 0 && --this.shieldTicks == 0) {
         this.setFlag(FLAG_SHIELD, false);
      }
      super.customServerAiStep();
   }

   @Override
   protected void fight(LivingEntity target, double distance, boolean sees) {
      if (this.move == UltronMove.BRUTE_CHARGE) {
         return;
      }
      this.faceTowards(target.position(), this.move == UltronMove.NONE ? 9.0F : 4.0F);
      if (this.move != UltronMove.NONE) {
         this.stopMoving();
         return;
      }
      double reach = 2.4 + target.getBbWidth() * 0.5;
      if (distance <= reach + 0.6) {
         this.stopMoving();
         if (this.attackCooldown <= 0) {
            if (this.slamCooldown <= 0 && (this.around(this.position(), 4.5).size() >= 2 || this.random.nextInt(3) == 0)) {
               this.startMove(UltronMove.BRUTE_SLAM, target);
               this.slamCooldown = 110;
               this.attackCooldown = 26;
            } else {
               this.startMove(UltronMove.BRUTE_SWING, target);
               this.attackCooldown = 22;
            }
         }
         return;
      }
      if (sees && this.missileCooldown <= 0 && distance > 8.0 && distance < 30.0) {
         this.stopMoving();
         this.startMove(UltronMove.BRUTE_MISSILES, target);
         this.missileCooldown = 150 + this.random.nextInt(60);
         return;
      }
      if (sees && this.chargeCooldown <= 0 && distance > 6.0 && distance < 18.0 && this.onGround() && !this.airborne(target)) {
         this.startMove(UltronMove.BRUTE_CHARGE, target);
         this.chargeCooldown = 120 + this.random.nextInt(40);
         return;
      }
      this.travelTo(target.position(), 1.0);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      boolean hit = super.hurt(source, amount);
      if (!this.level().isClientSide && this.isAlive() && this.flag(FLAG_DEFLECTOR) && this.shieldTicks <= 0 && this.move == UltronMove.NONE) {
         Entity attacker = source.getEntity();
         boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || attacker != null && attacker.distanceTo(this) > 5.0F;
         if (ranged) {
            // the plate comes up against the fire (a learned counter: DEFLECTOR protocol)
            if (attacker != null) {
               this.faceTowards(attacker.position(), 60.0F);
            }
            this.forceMove(UltronMove.BRUTE_SHIELD, null);
         }
      }
      return hit;
   }

   @Override
   protected void onMoveStart(UltronMove m, @Nullable LivingEntity target) {
      switch (m) {
         case BRUTE_SLAM, BRUTE_SWING -> this.sound(UltronSounds.BRUTE_SERVO, 1.0F, m == UltronMove.BRUTE_SLAM ? 0.8F : 1.0F);
         case BRUTE_MISSILES -> this.sound(UltronSounds.BRUTE_SERVO, 1.0F, 1.2F);
         case BRUTE_CHARGE -> {
            this.chargeDir = target == null ? this.facing() : new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ()).normalize();
            this.chargeHits.clear();
            this.sound(UltronSounds.BRUTE_CHARGE, 1.6F, 1.0F);
            this.fx(UltronFxS2CPacket.CHARGE, this.local(0.0, 1.4, 1.0), this.chargeDir, 1.5F, this.getId());
         }
         case BRUTE_SHIELD -> {
            this.shieldTicks = SHIELD_TICKS;
            this.setFlag(FLAG_SHIELD, true);
            this.sound(UltronSounds.BRUTE_SHIELD, 1.2F, 1.0F);
         }
         default -> {
         }
      }
   }

   @Override
   protected void onMoveTick(UltronMove m, int tick, @Nullable LivingEntity target) {
      if (m == UltronMove.BRUTE_SWING && tick == m.firstContact() - 3) {
         this.sound(UltronSounds.BRUTE_SWING, 1.2F, 1.0F);
      }
      if (m == UltronMove.BRUTE_SLAM && tick == 9 && this.onGround()) {
         Vec3 v = this.getDeltaMovement();
         this.setDeltaMovement(v.x * 0.2, 0.42, v.z * 0.2); // the hop before the slam
      }
      if (m == UltronMove.BRUTE_CHARGE) {
         if (tick < 3) {
            return; // lowers its shoulder
         }
         float yaw = (float)(Math.atan2(this.chargeDir.z, this.chargeDir.x) * (180.0 / Math.PI)) - 90.0F;
         this.setYRot(yaw);
         this.yBodyRot = yaw;
         Vec3 v = this.getDeltaMovement();
         this.setDeltaMovement(this.chargeDir.x * 0.62, v.y, this.chargeDir.z * 0.62);
         Vec3 front = this.position().add(this.chargeDir.scale(1.3)).add(0.0, 1.0, 0.0);
         for (LivingEntity e : this.around(front, 1.7)) {
            if (this.chargeHits.add(e.getId()) && e.hurt(this.damageSources().mobAttack(this), BalanceTable.ULTRON_BRUTE_CHARGE * this.damageScale())) {
               if (dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(e)) {
                  double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                  e.push(this.chargeDir.x * 1.6 * resist, 0.45 * resist, this.chargeDir.z * 1.6 * resist);
                  e.hurtMarked = true;
               }
               this.fx(UltronFxS2CPacket.BOLT_IMPACT, e.position().add(0.0, e.getBbHeight() * 0.5, 0.0), this.chargeDir, 1.2F, 1);
               this.sound(UltronSounds.BRUTE_SLAM, 0.9F, 1.3F);
            }
         }
         if (this.horizontalCollision && tick > 5) {
            // ran into a wall: the charge ends, it rocks back
            this.setDeltaMovement(this.chargeDir.x * -0.2, 0.0, this.chargeDir.z * -0.2);
            this.sound(UltronSounds.DEBRIS, 1.0F, 0.8F);
            this.forceMove(UltronMove.HEAVY_HIT, null);
         }
      }
   }

   @Override
   protected void onContact(UltronMove m, int index, @Nullable LivingEntity target) {
      switch (m) {
         case BRUTE_SLAM -> {
            Vec3 at = this.local(0.0, 0.0, 1.2);
            this.blast(at, 4.5, BalanceTable.ULTRON_BRUTE_SLAM, 1.1, 0.55, true);
            this.fx(UltronFxS2CPacket.SLAM_WAVE, at, Vec3.ZERO, 4.5F, this.getId());
            this.sound(UltronSounds.BRUTE_SLAM, 2.0F, 1.0F);
         }
         case BRUTE_SWING -> {
            if (this.strike(target, BalanceTable.ULTRON_BRUTE_SWING, 2.8, 1.3, 0.45)) {
               this.fx(UltronFxS2CPacket.BOLT_IMPACT, target.position().add(0.0, target.getBbHeight() * 0.55, 0.0), this.facing(), 1.0F, 1);
               this.sound(UltronSounds.SENTRY_STRIKE, 1.3F, 0.7F);
            }
         }
         case BRUTE_MISSILES -> {
            if (!(this.level() instanceof ServerLevel level)) {
               return;
            }
            this.pod ^= 1;
            Vec3 from = this.local(this.pod == 0 ? 0.75 : -0.75, 2.75, -0.1);
            Vec3 up = this.facing().scale(0.4).add(0.0, 1.0, 0.0);
            UltronMissile.launch(level, this, from, up, target);
            this.fx(UltronFxS2CPacket.MUZZLE, from, up.normalize(), 1.2F, this.getId());
            if (index == 0) {
               this.sound(UltronSounds.BRUTE_MISSILES, 1.6F, 1.0F);
            }
         }
         default -> {
         }
      }
   }

   @Override
   protected void onMoveEnd(UltronMove m) {
      if (m == UltronMove.BRUTE_CHARGE) {
         this.chargeHits.clear();
      }
   }

   @Override
   protected SoundEvent hurtSound() {
      return UltronSounds.BRUTE_HURT.get();
   }

   @Override
   protected SoundEvent deathSound() {
      return UltronSounds.BRUTE_DEATH.get();
   }
}

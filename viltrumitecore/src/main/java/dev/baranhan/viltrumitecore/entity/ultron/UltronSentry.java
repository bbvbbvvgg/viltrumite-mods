package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUnitKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Combat robot: the line machine. A lean bipedal frame with a blade forearm on the left and a repulsor
 * cannon on the right. Closes in and runs a three-strike chain (jab, hook, overhead), lunges over short
 * gaps and blasts from range. Its left forearm tears off when it is critically damaged.
 */
public class UltronSentry extends UltronUnit {
   private int combo;
   private int comboWindow;
   private int blastCooldown = 30;
   private int lungeCooldown = 20;
   private Vec3 lungeDir = Vec3.ZERO;

   public UltronSentry(EntityType<? extends UltronSentry> type, Level level) {
      super(type, level);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 36.0)
         .add(Attributes.ARMOR, 8.0)
         .add(Attributes.ARMOR_TOUGHNESS, 2.0)
         .add(Attributes.MOVEMENT_SPEED, 0.26)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
         .add(Attributes.ATTACK_DAMAGE, BalanceTable.ULTRON_SENTRY_STRIKE)
         .add(Attributes.FOLLOW_RANGE, 64.0);
   }

   @Override
   public UltronUnitKind kind() {
      return UltronUnitKind.SENTRY;
   }

   /** Deflector guard in a fight (the renderer blends in the guard stance). */
   @Override
   protected float clientHoldTarget() {
      return this.isAlive() && this.flag(FLAG_DEFLECTOR) && this.flag(FLAG_COMBAT) ? 1.0F : 0.0F;
   }

   @Override
   public int deathTicks() {
      return 30;
   }

   @Override
   protected double senseRange() {
      return 32.0;
   }

   @Override
   protected void customServerAiStep() {
      if (this.blastCooldown > 0) {
         this.blastCooldown--;
      }
      if (this.lungeCooldown > 0) {
         this.lungeCooldown--;
      }
      if (this.comboWindow > 0 && --this.comboWindow == 0) {
         this.combo = 0;
      }
      super.customServerAiStep();
   }

   @Override
   protected void fight(LivingEntity target, double distance, boolean sees) {
      double reach = 1.9 + target.getBbWidth() * 0.5;
      if (this.move == UltronMove.SENTRY_LUNGE) {
         return; // the lunge steers itself
      }
      if (this.move != UltronMove.NONE) {
         this.stopMoving();
         this.faceTowards(target.position(), 22.0F);
         return;
      }
      if (distance > reach) {
         if (sees && distance > 9.0 && distance < 28.0 && this.blastCooldown <= 0) {
            this.stopMoving();
            this.startMove(UltronMove.SENTRY_BLAST, target);
            this.blastCooldown = 50 + this.random.nextInt(40);
            return;
         }
         if (sees && distance > 3.2 && distance < 6.5 && this.lungeCooldown <= 0 && this.onGround()) {
            this.startMove(UltronMove.SENTRY_LUNGE, target);
            this.lungeCooldown = 70 + this.random.nextInt(30);
            return;
         }
         this.travelTo(target.position(), distance > 12.0 ? 1.3 : 1.1);
         return;
      }
      this.stopMoving();
      this.faceTowards(target.position(), 20.0F);
      if (this.attackCooldown <= 0) {
         UltronMove m = this.combo == 0 ? UltronMove.SENTRY_STRIKE_A : this.combo == 1 ? UltronMove.SENTRY_STRIKE_B : UltronMove.SENTRY_STRIKE_C;
         this.startMove(m, target);
         this.combo = (this.combo + 1) % 3;
         this.comboWindow = m.length + 14;
         this.attackCooldown = m == UltronMove.SENTRY_STRIKE_C ? 24 : 1;
      }
   }

   @Override
   protected void onMoveStart(UltronMove m, @Nullable LivingEntity target) {
      switch (m) {
         case SENTRY_STRIKE_A, SENTRY_STRIKE_B, SENTRY_STRIKE_C -> this.sound(UltronSounds.SENTRY_SERVO, 0.7F, 1.15F);
         case SENTRY_BLAST -> {
            this.sound(UltronSounds.SENTRY_SERVO, 0.8F, 0.9F);
            this.fx(UltronFxS2CPacket.CHARGE, this.local(0.25, 1.68, 0.8), Vec3.ZERO, 0.8F, this.getId());
         }
         case SENTRY_LUNGE -> {
            this.sound(UltronSounds.SENTRY_SERVO, 0.9F, 0.8F);
            this.lungeDir = target == null ? this.facing() : new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ()).normalize();
         }
         default -> {
         }
      }
   }

   @Override
   protected void onMoveTick(UltronMove m, int tick, @Nullable LivingEntity target) {
      int contact = m.firstContact();
      if (contact > 0 && tick == contact - 3 && m != UltronMove.SENTRY_BLAST) {
         this.sound(UltronSounds.SENTRY_SWING, 0.9F, m == UltronMove.SENTRY_STRIKE_C ? 0.85F : 1.05F);
      }
      if (m == UltronMove.SENTRY_LUNGE && tick >= 3 && tick <= 8) {
         // anticipation crouch (0-3), then the burst forward into contact
         Vec3 v = this.getDeltaMovement();
         this.setDeltaMovement(this.lungeDir.x * 0.62, v.y, this.lungeDir.z * 0.62);
         if (target != null) {
            this.faceTowards(target.position(), 30.0F);
         }
      }
      if (m == UltronMove.SENTRY_BLAST && target != null) {
         this.faceTowards(target.position(), 18.0F);
      }
   }

   @Override
   protected void onContact(UltronMove m, int index, @Nullable LivingEntity target) {
      switch (m) {
         case SENTRY_STRIKE_A, SENTRY_STRIKE_B -> this.hit(target, BalanceTable.ULTRON_SENTRY_STRIKE, 1.9, 0.35, 0.05);
         case SENTRY_STRIKE_C -> this.hit(target, BalanceTable.ULTRON_SENTRY_STRIKE * 1.6F, 2.2, 0.95, 0.35);
         case SENTRY_LUNGE -> this.hit(target, BalanceTable.ULTRON_SENTRY_STRIKE * 1.3F, 2.4, 0.6, 0.2);
         case SENTRY_BLAST -> {
            if (target == null || !(this.level() instanceof ServerLevel level)) {
               return;
            }
            Vec3 from = this.local(0.25, 1.68, 0.9); // the raised right palm at the blast clip's contact frame
            Vec3 aim = this.lead(target, from, 1.6, 0.6);
            UltronBolt.shoot(level, this, from, aim.subtract(from), 1.6, BalanceTable.ULTRON_SENTRY_BOLT * this.damageScale(), UltronBolt.STYLE_REPULSOR);
            this.fx(UltronFxS2CPacket.MUZZLE, from, aim.subtract(from).normalize(), 0.8F, this.getId());
            this.sound(UltronSounds.SENTRY_BLAST, 1.2F, 1.0F);
         }
         default -> {
         }
      }
   }

   private void hit(@Nullable LivingEntity target, float damage, double reach, double push, double lift) {
      if (this.strike(target, damage, reach, push, lift)) {
         Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
         this.fx(UltronFxS2CPacket.BOLT_IMPACT, at, this.facing(), 0.5F, 1);
         this.sound(UltronSounds.SENTRY_STRIKE, 1.0F, 1.0F);
      }
   }

   @Override
   protected SoundEvent hurtSound() {
      return UltronSounds.SENTRY_HURT.get();
   }

   @Override
   protected SoundEvent deathSound() {
      return UltronSounds.SENTRY_DEATH.get();
   }
}

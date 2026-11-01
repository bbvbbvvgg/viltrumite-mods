package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronRole;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUnitKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Flying interceptor: a swept delta airframe with a central jet, two wingtip guns and a belly missile.
 * Hunts what flies (the ANTI_AIR protocol sends more of them): strafing bursts on an orbit, a missile
 * against flyers, a diving strike against what stands, an evasive boost when hit. Banks into its turns.
 */
public class UltronInterceptor extends UltronUnit {
   private double orbit;
   private int orbitDir;
   private int burstCooldown = 20;
   private int missileCooldown = 70;
   private int diveCooldown = 100;
   private int boostCooldown;
   private int gun;
   @Nullable
   private Vec3 diveTo;

   public UltronInterceptor(EntityType<? extends UltronInterceptor> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.orbit = this.random.nextDouble() * Math.PI * 2.0;
      this.orbitDir = this.random.nextBoolean() ? 1 : -1;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 30.0)
         .add(Attributes.ARMOR, 5.0)
         .add(Attributes.MOVEMENT_SPEED, 0.3)
         .add(Attributes.FLYING_SPEED, 0.6)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
         .add(Attributes.FOLLOW_RANGE, 64.0);
   }

   @Override
   public UltronUnitKind kind() {
      return UltronUnitKind.INTERCEPTOR;
   }

   @Override
   public int deathTicks() {
      return 30;
   }

   @Override
   protected double senseRange() {
      return this.role() == UltronRole.INTERCEPT ? 48.0 : 40.0;
   }

   @Override
   protected double hoverHeight() {
      return 12.0;
   }

   @Override
   protected double cruiseSpeed() {
      return 1.6;
   }

   @Override
   protected boolean interruptible(UltronMove m) {
      return m != UltronMove.INTERCEPTOR_DIVE;
   }

   @Override
   protected void customServerAiStep() {
      if (this.burstCooldown > 0) {
         this.burstCooldown--;
      }
      if (this.missileCooldown > 0) {
         this.missileCooldown--;
      }
      if (this.diveCooldown > 0) {
         this.diveCooldown--;
      }
      if (this.boostCooldown > 0) {
         this.boostCooldown--;
      }
      super.customServerAiStep();
   }

   @Override
   protected void fight(LivingEntity target, double distance, boolean sees) {
      if (this.move == UltronMove.INTERCEPTOR_DIVE) {
         return; // the dive steers itself
      }
      boolean air = this.airborne(target);
      double ring = air ? 9.0 : 15.0;
      this.orbit += (air ? 0.06 : 0.045) * this.orbitDir;
      Vec3 spot = target.position().add(Math.cos(this.orbit) * ring, air ? 2.5 : 9.0, Math.sin(this.orbit) * ring);
      this.steerTo(spot, this.move == UltronMove.INTERCEPTOR_BOOST ? 0.95 : 0.55, air ? 2.0 : 6.0);
      this.faceTowards(this.move == UltronMove.INTERCEPTOR_BOOST ? this.position().add(this.steer.scale(10.0)) : target.position(), 14.0F);
      if (!sees || this.move != UltronMove.NONE) {
         return;
      }
      if (!air && this.diveCooldown <= 0 && distance > 8.0 && distance < 26.0) {
         this.startMove(UltronMove.INTERCEPTOR_DIVE, target);
         this.diveCooldown = 150 + this.random.nextInt(60);
      } else if (this.missileCooldown <= 0 && distance < 36.0 && (air || this.random.nextInt(4) == 0)) {
         this.startMove(UltronMove.INTERCEPTOR_MISSILE, target);
         this.missileCooldown = air ? 90 : 140;
      } else if (this.burstCooldown <= 0 && distance < 30.0) {
         this.startMove(UltronMove.INTERCEPTOR_BURST, target);
         this.burstCooldown = 30 + this.random.nextInt(16);
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      boolean hit = super.hurt(source, amount);
      if (hit && this.isAlive() && this.boostCooldown <= 0 && this.move == UltronMove.NONE) {
         this.boostCooldown = 70;
         this.orbitDir = -this.orbitDir;
         this.forceMove(UltronMove.INTERCEPTOR_BOOST, null);
      }
      return hit;
   }

   @Override
   protected void onMoveStart(UltronMove m, @Nullable LivingEntity target) {
      switch (m) {
         case INTERCEPTOR_BOOST -> {
            Vec3 side = new Vec3(-this.facing().z, 0.0, this.facing().x).scale(this.orbitDir * 0.9);
            this.setDeltaMovement(this.getDeltaMovement().add(side).add(0.0, 0.25, 0.0));
            this.steer = side.add(0.0, 0.2, 0.0);
            this.sound(UltronSounds.INTERCEPTOR_BOOST, 1.3F, 1.0F);
            this.fx(UltronFxS2CPacket.JET, this.local(0.0, 0.5, -0.8), this.facing().scale(-1.0), 1.5F, this.getId());
         }
         case INTERCEPTOR_DIVE -> {
            this.diveTo = null;
            this.sound(UltronSounds.INTERCEPTOR_BOOST, 1.3F, 0.8F); // the engines kick in (the jet loop itself only runs on the client)
         }
         case INTERCEPTOR_BURST -> this.fx(UltronFxS2CPacket.CHARGE, this.local(0.0, 0.5, 0.7), Vec3.ZERO, 0.5F, this.getId());
         default -> {
         }
      }
   }

   @Override
   protected void onMoveTick(UltronMove m, int tick, @Nullable LivingEntity target) {
      if (m != UltronMove.INTERCEPTOR_DIVE) {
         return;
      }
      if (tick < 14) {
         // anticipation: pull up and roll toward the target
         this.steer = new Vec3(this.steer.x * 0.8, 0.35, this.steer.z * 0.8);
         if (target != null) {
            this.faceTowards(target.position(), 20.0F);
         }
         return;
      }
      if (tick == 14) {
         this.diveTo = target != null ? target.position().add(target.getDeltaMovement().scale(6.0)) : this.position().add(this.facing().scale(10.0)).add(0.0, -8.0, 0.0);
         this.sound(UltronSounds.INTERCEPTOR_DIVE, 1.6F, 1.0F);
      }
      if (this.diveTo != null && tick < 22) {
         Vec3 d = this.diveTo.subtract(this.position());
         double len = d.length();
         Vec3 v = len < 0.5 ? Vec3.ZERO : d.scale(Math.min(1.35, len / Math.max(1, 22 - tick)) / len);
         this.setDeltaMovement(v);
         this.steer = v;
         this.faceTowards(this.diveTo, 40.0F);
      } else if (tick > 22) {
         this.steer = new Vec3(this.steer.x * 0.85, 0.45, this.steer.z * 0.85); // follow-through: pull out of the dive
      }
   }

   @Override
   protected void onContact(UltronMove m, int index, @Nullable LivingEntity target) {
      if (!(this.level() instanceof ServerLevel level)) {
         return;
      }
      switch (m) {
         case INTERCEPTOR_BURST -> {
            if (target == null) {
               return;
            }
            this.gun ^= 1;
            Vec3 from = this.local(this.gun == 0 ? 0.55 : -0.55, 0.45, 0.6);
            Vec3 aim = this.lead(target, from, 2.0, 0.8);
            Vec3 dir = aim.subtract(from);
            UltronBolt.shoot(level, this, from, dir, 2.0, BalanceTable.ULTRON_INTERCEPTOR_BOLT * this.damageScale(), UltronBolt.STYLE_LASER);
            this.fx(UltronFxS2CPacket.MUZZLE, from, dir.normalize(), 0.5F, this.getId());
            if (index == 0) {
               this.sound(UltronSounds.INTERCEPTOR_BURST, 1.2F, 1.0F);
            }
         }
         case INTERCEPTOR_MISSILE -> {
            Vec3 from = this.local(0.0, 0.1, 0.3);
            Vec3 dir = target == null ? this.facing() : target.position().subtract(from).normalize().add(0.0, -0.2, 0.0);
            UltronMissile.launch(level, this, from, dir, target);
            this.fx(UltronFxS2CPacket.MUZZLE, from, dir.normalize(), 1.0F, this.getId());
         }
         case INTERCEPTOR_DIVE -> {
            Vec3 at = this.position();
            int hits = this.blast(at, 2.8, BalanceTable.ULTRON_INTERCEPTOR_DIVE, 0.9, 0.4, false);
            this.fx(UltronFxS2CPacket.SLAM_WAVE, at, Vec3.ZERO, 2.8F, this.getId());
            this.sound(hits > 0 ? UltronSounds.SENTRY_STRIKE : UltronSounds.BOLT_IMPACT, 1.4F, 0.8F);
         }
         default -> {
         }
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide && this.isAlive()) {
         // keep the airframe level when it idles (the renderer banks from clientTurn)
         this.setXRot(Mth.approach(this.getXRot(), 0.0F, 2.0F));
      }
   }

   @Override
   protected SoundEvent hurtSound() {
      return UltronSounds.INTERCEPTOR_HURT.get();
   }

   @Override
   protected SoundEvent deathSound() {
      return UltronSounds.INTERCEPTOR_DEATH.get();
   }
}

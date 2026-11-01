package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronRole;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUnitKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUprising;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Scout drone: a hovering sensor disc with a lens eye and two stub emitters. Recon is its job: scan
 * sweeps make every player in range known to the network, the designator marks a target (it glows,
 * nearby assault machines converge on it), and a short zap beam keeps it from being ignored. Fragile;
 * a downed drone can be salvaged for the position of a hidden node.
 */
public class UltronDrone extends UltronUnit {
   public static final double SCAN_RANGE = 26.0;
   private static final double ZAP_RANGE = 16.0;
   private int scanCooldown = 40;
   private int markCooldown;
   private double orbit;
   private int orbitDir;

   public UltronDrone(EntityType<? extends UltronDrone> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.orbit = this.random.nextDouble() * Math.PI * 2.0;
      this.orbitDir = this.random.nextBoolean() ? 1 : -1;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 14.0)
         .add(Attributes.ARMOR, 2.0)
         .add(Attributes.MOVEMENT_SPEED, 0.3)
         .add(Attributes.FLYING_SPEED, 0.5)
         .add(Attributes.FOLLOW_RANGE, 64.0);
   }

   @Override
   public UltronUnitKind kind() {
      return UltronUnitKind.DRONE;
   }

   @Override
   public int deathTicks() {
      return 24;
   }

   @Override
   protected double senseRange() {
      return 30.0;
   }

   @Override
   protected double hoverHeight() {
      return 7.0;
   }

   @Override
   protected double cruiseSpeed() {
      return 1.1;
   }

   @Override
   protected void customServerAiStep() {
      if (this.scanCooldown > 0) {
         this.scanCooldown--;
      }
      if (this.markCooldown > 0) {
         this.markCooldown--;
      }
      super.customServerAiStep();
   }

   @Override
   protected void idle() {
      if (this.scanCooldown <= 0 && (this.role() == UltronRole.SCOUT || this.role() == UltronRole.PATROL)) {
         this.scanCooldown = 160 + this.random.nextInt(80);
         this.startMove(UltronMove.DRONE_SCAN, null);
         this.steer = this.steer.scale(0.3);
         return;
      }
      super.idle();
   }

   @Override
   protected void fight(LivingEntity target, double distance, boolean sees) {
      // stand-off orbit above the target; scouts keep wider than escorts
      this.orbit += 0.03 * this.orbitDir;
      double ring = this.role() == UltronRole.SCOUT ? 11.0 : 7.5;
      Vec3 spot = target.position().add(Math.cos(this.orbit) * ring, target.getBbHeight() + 3.0, Math.sin(this.orbit) * ring);
      if (this.move == UltronMove.NONE || this.move == UltronMove.DRONE_SCAN) {
         this.steerTo(spot, 0.3, 3.0);
      } else {
         this.steer = this.steer.scale(0.8); // holds still while it aims
      }
      this.faceTowards(target.position(), this.move == UltronMove.NONE ? 12.0F : 20.0F);
      if (!sees || this.move != UltronMove.NONE || this.attackCooldown > 0) {
         return;
      }
      if (this.markCooldown <= 0 && distance < 24.0 && !target.hasEffect(MobEffects.GLOWING)
         && (this.role() == UltronRole.SCOUT || this.random.nextInt(3) == 0)) {
         this.startMove(UltronMove.DRONE_MARK, target);
         this.markCooldown = 220;
         this.attackCooldown = 40;
      } else if (distance < ZAP_RANGE) {
         this.startMove(UltronMove.DRONE_ZAP, target);
         this.attackCooldown = 28 + this.random.nextInt(18);
      }
   }

   @Override
   protected void onMoveStart(UltronMove m, @Nullable LivingEntity target) {
      if (m == UltronMove.DRONE_SCAN) {
         this.sound(UltronSounds.DRONE_SCAN, 1.0F, 1.0F);
         this.fx(UltronFxS2CPacket.SCAN, this.position(), Vec3.ZERO, (float)SCAN_RANGE, this.getId());
      } else if (m == UltronMove.DRONE_ZAP) {
         this.fx(UltronFxS2CPacket.CHARGE, this.local(0.0, 0.25, 0.35), Vec3.ZERO, 0.4F, this.getId());
      }
   }

   @Override
   protected void onMoveTick(UltronMove m, int tick, @Nullable LivingEntity target) {
      if (m == UltronMove.DRONE_SCAN && tick == 20) {
         UltronUprising.scan(this, SCAN_RANGE);
      }
      if (target != null && m != UltronMove.DRONE_SCAN) {
         this.faceTowards(target.position(), 25.0F);
      }
   }

   @Override
   protected void onContact(UltronMove m, int index, @Nullable LivingEntity target) {
      if (target == null || !target.isAlive()) {
         return;
      }
      Vec3 eye = this.local(0.0, 0.25, 0.4);
      Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
      if (m == UltronMove.DRONE_ZAP) {
         if (this.distanceTo(target) <= ZAP_RANGE + 2.0) {
            Vec3 end = this.beam(eye, at.subtract(eye), ZAP_RANGE + 2.0, BalanceTable.ULTRON_DRONE_ZAP, 0.3);
            this.fx(UltronFxS2CPacket.ZAP, eye, end, 1.0F, this.getId());
            this.sound(UltronSounds.DRONE_ZAP, 1.0F, 1.0F);
         }
      } else if (m == UltronMove.DRONE_MARK && this.sees(target)) {
         UltronUprising.mark(this, target);
         this.fx(UltronFxS2CPacket.MARK, at, eye, 1.0F, target.getId());
         this.sound(UltronSounds.DRONE_MARK, 1.2F, 1.0F);
      }
   }

   @Override
   protected SoundEvent hurtSound() {
      return UltronSounds.DRONE_HURT.get();
   }

   @Override
   protected SoundEvent deathSound() {
      return UltronSounds.DRONE_DEATH.get();
   }
}

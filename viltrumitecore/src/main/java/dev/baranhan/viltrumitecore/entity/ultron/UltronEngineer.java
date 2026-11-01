package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNetwork;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronRole;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronRules;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUnitKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUprising;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
 * Engineer-repairer: a low hexapod with a manipulator arm (weld torch) and a hacking spike. It builds the
 * planned nodes, welds damaged ones, reboots the ones players disabled and takes over golems and Legion
 * suits inside a controller's hold. It does not fight: it scuttles away when hit and goes back to work,
 * which is what the escorts are for.
 */
public class UltronEngineer extends UltronUnit {
   private int weldClock;
   private boolean welding;
   private int fleeUntil;
   @Nullable
   private Vec3 fleeFrom;
   private int hackCooldown;
   @Nullable
   private Entity weldBody;

   public UltronEngineer(EntityType<? extends UltronEngineer> type, Level level) {
      super(type, level);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 28.0)
         .add(Attributes.ARMOR, 6.0)
         .add(Attributes.MOVEMENT_SPEED, 0.24)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
         .add(Attributes.FOLLOW_RANGE, 64.0);
   }

   @Override
   public UltronUnitKind kind() {
      return UltronUnitKind.ENGINEER;
   }

   @Override
   public int deathTicks() {
      return 30;
   }

   @Override
   protected double senseRange() {
      return 20.0;
   }

   @Override
   public float stride(float speed) {
      return 1.0F + 1.1F * Math.min(1.0F, speed / 0.25F);
   }

   @Override
   protected boolean interruptible(UltronMove m) {
      return m != UltronMove.ENGINEER_PLANT;
   }

   @Override
   protected void customServerAiStep() {
      if (this.hackCooldown > 0) {
         this.hackCooldown--;
      }
      super.customServerAiStep();
      if (this.getTarget() != null || this.move != UltronMove.NONE) {
         this.setWelding(false);
      }
   }

   /** The only target an engineer takes is a machine to capture (role CAPTURE). */
   @Nullable
   @Override
   protected LivingEntity findTarget() {
      if (this.role() != UltronRole.CAPTURE || this.hunt == null || !(this.level() instanceof ServerLevel level)) {
         return null;
      }
      return level.getEntity(this.hunt) instanceof LivingEntity e && e.isAlive() && this.distanceToSqr(e) < 48.0 * 48.0
         && UltronUprising.capturable(level, e) ? e : null;
   }

   @Override
   protected void fight(LivingEntity target, double distance, boolean sees) {
      if (this.role() != UltronRole.CAPTURE || !UltronUprising.capturable(this.level(), target)) {
         this.setTarget(null);
         return;
      }
      if (this.move == UltronMove.ENGINEER_HACK) {
         this.stopMoving();
         this.faceTowards(target.position(), 20.0F);
         return;
      }
      double reach = 1.6 + target.getBbWidth() * 0.5;
      if (distance > reach) {
         this.travelTo(target.position(), 1.25);
         return;
      }
      this.stopMoving();
      this.faceTowards(target.position(), 20.0F);
      if (this.move == UltronMove.NONE && this.hackCooldown <= 0) {
         this.startMove(UltronMove.ENGINEER_HACK, target);
         this.hackCooldown = 50;
      }
   }

   @Override
   protected void idle() {
      if (this.fleeUntil > this.tickCount && this.fleeFrom != null) {
         Vec3 away = this.position().subtract(this.fleeFrom);
         away = away.lengthSqr() < 1.0E-3 ? this.facing().scale(-1.0) : new Vec3(away.x, 0.0, away.z).normalize();
         this.travelTo(this.position().add(away.scale(7.0)), 1.35);
         this.setWelding(false);
         return;
      }
      if (this.role() == UltronRole.REPAIR && this.job >= 0) {
         this.work();
         return;
      }
      this.setWelding(false);
      super.idle();
   }

   /** The job: walk to the node, plant it when it is only planned, else weld (build, reboot, repair). */
   private void work() {
      UltronNetwork.Node n = UltronUprising.node(this.level(), this.job);
      if (n == null || n.state == UltronNodeState.DESTROYED) {
         this.finishJob();
         return;
      }
      Vec3 site = new Vec3(n.x, n.y, n.z);
      double dx = site.x - this.getX();
      double dz = site.z - this.getZ();
      double d = Math.sqrt(dx * dx + dz * dz);
      double reach = n.state == UltronNodeState.PLANNED ? 2.2 : 2.9;
      if (d > reach) {
         this.setWelding(false);
         this.travelTo(site, 1.0);
         return;
      }
      this.stopMoving();
      this.faceTowards(site, 15.0F);
      if (n.state == UltronNodeState.PLANNED) {
         this.setWelding(false);
         this.startMove(UltronMove.ENGINEER_PLANT, null);
         return;
      }
      this.weldBody = n.entity != null && this.level() instanceof ServerLevel level ? level.getEntity(n.entity) : null;
      this.setWelding(true);
      this.weldClock++;
      if (this.weldClock % UltronRules.WELD_EVERY == 0 && !UltronUprising.weld(this, this.job)) {
         this.finishJob();
         return;
      }
      if (this.weldClock % 10 == 0) {
         Vec3 torch = this.local(0.0, 0.9, 0.75);
         Vec3 spot = site.add((this.random.nextDouble() - 0.5) * 1.6, 0.8 + this.random.nextDouble() * 2.4, (this.random.nextDouble() - 0.5) * 1.6);
         this.fx(UltronFxS2CPacket.WELD, torch, spot, n.state == UltronNodeState.BUILDING ? n.build : 1.0F, n.kind.ordinal());
      }
      if (this.weldClock % 30 == 1) {
         this.sound(UltronSounds.ENGINEER_WELD, 0.9F, 1.0F);
      }
   }

   private void finishJob() {
      this.setWelding(false);
      this.job = -1;
      this.goal = null;
   }

   private void setWelding(boolean on) {
      if (this.welding != on) {
         this.welding = on;
         this.setFlag(FLAG_BEAM, on);
         if (!on) {
            this.weldBody = null;
         }
      }
   }

   @Nullable
   @Override
   protected Entity idleAim() {
      return this.welding ? this.weldBody : null;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      boolean hit = super.hurt(source, amount);
      Entity attacker = source.getEntity();
      if (hit && this.isAlive() && attacker != null && attacker.distanceToSqr(this) < 10.0 * 10.0) {
         this.fleeUntil = this.tickCount + 40;
         this.fleeFrom = attacker.position();
      }
      return hit;
   }

   @Override
   protected void onMoveStart(UltronMove m, @Nullable LivingEntity target) {
      if (m == UltronMove.ENGINEER_HACK) {
         this.sound(UltronSounds.ENGINEER_HACK, 1.0F, 1.0F);
         this.setFlag(FLAG_BEAM, true);
      } else if (m == UltronMove.ENGINEER_PLANT) {
         this.sound(UltronSounds.ENGINEER_STEP, 1.0F, 0.7F);
      }
   }

   @Override
   protected void onMoveTick(UltronMove m, int tick, @Nullable LivingEntity target) {
      if (m == UltronMove.ENGINEER_HACK && target != null && tick % 8 == 4) {
         Vec3 spike = this.local(0.0, 0.8, 0.8);
         this.fx(UltronFxS2CPacket.HACK, spike, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), tick / (float)m.length, target.getId());
      }
   }

   @Override
   protected void onContact(UltronMove m, int index, @Nullable LivingEntity target) {
      if (m == UltronMove.ENGINEER_HACK) {
         if (target != null && target.isAlive() && this.distanceTo(target) < 4.5F && UltronUprising.capturable(this.level(), target)) {
            UltronUprising.engineerHack(this, target);
         }
      } else if (m == UltronMove.ENGINEER_PLANT && this.job >= 0) {
         UltronNetwork.Node n = UltronUprising.node(this.level(), this.job);
         if (UltronUprising.plant(this, this.job) && n != null) {
            this.fx(UltronFxS2CPacket.FABRICATE, new Vec3(n.x, n.y, n.z), Vec3.ZERO, 1.0F, n.kind.ordinal());
            this.sound(UltronSounds.ENGINEER_PLANT, 1.4F, 1.0F);
         }
      }
   }

   @Override
   protected void onMoveEnd(UltronMove m) {
      if (m == UltronMove.ENGINEER_HACK) {
         this.setFlag(FLAG_BEAM, this.welding);
      }
   }

   @Override
   protected SoundEvent hurtSound() {
      return UltronSounds.ENGINEER_HURT.get();
   }

   @Override
   protected SoundEvent deathSound() {
      return UltronSounds.ENGINEER_DEATH.get();
   }
}

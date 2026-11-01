package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.city.citizen.CitizenCaptivity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriCaptives;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.WarRules;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Chitauri captor: a lean hunter with a tether projector and a stasis cage on its back. It hunts city people
 * (citizens, villagers), slows them with the tether, grabs one, slings it over its shoulder and carries it to a
 * containment field. It only fights when cornered; a hard hit or its death makes it drop the captive (the person is
 * free at once). The carried person stays a normal, saved-or-tracked entity: it is moved to the shoulder every tick,
 * never ridden (a ridden passenger of a never-saved Chitauri would vanish with a chunk save).
 */
public class ChitauriCaptor extends ChitauriSoldier {
   private static final EntityDataAccessor<Integer> CARRIED = SynchedEntityData.defineId(ChitauriCaptor.class, EntityDataSerializers.INT);
   public static final byte EVENT_CAPTURE = 76, EVENT_TETHER = 77, EVENT_DROP = 78, EVENT_DISEMBARK = 79;
   /** Ticks of the grab (reach, clamp, lift onto the shoulder): the person is held still meanwhile. */
   public static final int CAPTURE_TICKS = 26;

   @Nullable
   private LivingEntity carried;
   private int tetherCooldown;

   // client clocks
   public int clientCaptureStart = -1000;
   public int clientTetherStart = -1000;
   public int clientDropStart = -1000;
   public int clientDisembarkStart = -1000;

   public ChitauriCaptor(EntityType<? extends ChitauriCaptor> type, Level level) {
      super(type, level);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, BalanceTable.CHITAURI_CAPTOR_HEALTH)
         .add(Attributes.MOVEMENT_SPEED, 0.3)
         .add(Attributes.FOLLOW_RANGE, 40.0)
         .add(Attributes.ATTACK_DAMAGE, 4.0)
         .add(Attributes.ATTACK_KNOCKBACK, 0.3)
         .add(Attributes.ARMOR, 3.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(CARRIED, -1);
   }

   @Override
   protected void registerRoleGoals() {
      this.goalSelector.addGoal(1, new CarryGoal(this));
      this.goalSelector.addGoal(1, new HuntGoal(this));
   }

   /** Entity id of the carried person (client and server), or -1. */
   public int carriedId() {
      return this.entityData.get(CARRIED);
   }

   public boolean carrying() {
      return this.carriedId() >= 0;
   }

   @Nullable
   public LivingEntity carried() {
      return this.carried;
   }

   /** Weapon profile: short tether pulses, no long fire fights. */
   @Override
   protected int burstSize() {
      return 1 + this.random.nextInt(2);
   }

   @Override
   protected double fireRange() {
      return 22.0;
   }

   @Override
   protected boolean mayFight() {
      return !this.carrying();
   }

   @Override
   public void onDisembark() {
      this.level().broadcastEntityEvent(this, EVENT_DISEMBARK);
   }

   /** People the captor goes for: alive, on foot, not held already, in a loaded chunk. */
   public static boolean prey(@Nullable Entity e) {
      return e instanceof LivingEntity l && l.isAlive() && (e instanceof CitizenEntity || e instanceof AbstractVillager) && !l.isPassenger()
         && !CitizenCaptivity.isHeld(e) && !l.isBaby();
   }

   /** Shoulder point the carried person is moved to (over the left shoulder, lying across it). */
   public Vec3 shoulder() {
      float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
      Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
      return this.position().add(left.scale(0.25)).add(0, 1.38, 0);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }
      if (this.tetherCooldown > 0) {
         this.tetherCooldown--;
      }
      LivingEntity c = this.carried;
      if (c != null) {
         if (!c.isAlive() || c.isRemoved() || c.level() != this.level() || !this.isAlive()) {
            this.drop(false);
         } else {
            Vec3 at = this.shoulder();
            c.setPos(at.x, at.y - 0.55, at.z);
            c.setDeltaMovement(Vec3.ZERO);
            c.fallDistance = 0.0F;
            c.setYRot(this.yBodyRot + 90.0F);
            c.yBodyRot = c.getYRot();
         }
      }
   }

   /** Takes the person onto the shoulder (end of the grab). */
   void pickUp(LivingEntity victim) {
      if (!(this.level() instanceof ServerLevel level) || !ChitauriCaptives.carry(level, this, victim)) {
         return;
      }
      this.carried = victim;
      victim.setNoGravity(true);
      victim.noPhysics = true;
      this.entityData.set(CARRIED, victim.getId());
      ChitauriSounds.play(level, this.position(), ChitauriSounds.CAPTOR_GRAB, 1.2F, 0.95F + this.random.nextFloat() * 0.1F);
   }

   /** Lets go of the carried person; {@code delivered}: into the containment field, else free on the spot. */
   public void drop(boolean delivered) {
      LivingEntity c = this.carried;
      this.carried = null;
      this.entityData.set(CARRIED, -1);
      if (c == null || !(this.level() instanceof ServerLevel level)) {
         return;
      }
      c.setNoGravity(false);
      c.noPhysics = false;
      c.fallDistance = 0.0F;
      this.level().broadcastEntityEvent(this, EVENT_DROP);
      if (delivered) {
         ChitauriCaptives.deliver(level, this, c);
      } else {
         Vec3 at = this.position().add(this.getLookAngle().multiply(1, 0, 1).scale(0.8));
         int y = ChitauriTerrain.surface(level, at.x, at.z);
         if (y != ChitauriTerrain.UNKNOWN && Math.abs(y - this.getY()) < 3) {
            c.setPos(at.x, y, at.z);
         } else {
            c.setPos(this.getX(), this.getY(), this.getZ());
         }
         ChitauriCaptives.release(level, c, ChitauriCaptives.Cause.DROPPED, null);
      }
   }

   @Override
   protected void afterHurt(DamageSource source, float amount) {
      if (this.carrying() && amount >= this.getMaxHealth() * WarRules.DROP_SHARE) {
         this.drop(false); // a hard hit breaks the grip
      }
   }

   @Override
   public void die(DamageSource source) {
      if (!this.level().isClientSide) {
         this.drop(false);
      }
      super.die(source);
   }

   @Override
   public void remove(Entity.RemovalReason reason) {
      if (!this.level().isClientSide && this.carried != null) {
         this.drop(false);
      }
      super.remove(reason);
   }

   @Override
   public void handleEntityEvent(byte id) {
      switch (id) {
         case EVENT_CAPTURE -> this.clientCaptureStart = this.tickCount;
         case EVENT_TETHER -> this.clientTetherStart = this.tickCount;
         case EVENT_DROP -> this.clientDropStart = this.tickCount;
         case EVENT_DISEMBARK -> this.clientDisembarkStart = this.tickCount;
         default -> super.handleEntityEvent(id);
      }
   }

   // ------------------------------------------------------------------ goals

   /** Find a person, tether them from range, close in, grab (the person is held still for the grab). */
   static final class HuntGoal extends Goal {
      private final ChitauriCaptor c;
      @Nullable
      private LivingEntity prey;
      private int grab = -1;
      private int repath;
      private int giveUp;

      HuntGoal(ChitauriCaptor c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         if (this.c.carrying() || this.c.isPassenger() || this.c.severed() || this.c.tickCount % 10 != 0) {
            return false;
         }
         if (!(this.c.level() instanceof ServerLevel level) || !ChitauriCaptives.canTakeMore(level)) {
            return false;
         }
         // cornered: a hero right next to it comes first
         Player near = level.getNearestPlayer(this.c, 5.0);
         if (near != null && ChitauriMob.validTarget(near)) {
            return false;
         }
         LivingEntity best = null;
         double bestD = Double.MAX_VALUE;
         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.c.getBoundingBox().inflate(22.0, 8.0, 22.0), ChitauriCaptor::prey)) {
            if (ChitauriCaptives.claimed(e, this.c)) {
               continue;
            }
            double d = e.distanceToSqr(this.c);
            if (d < bestD) {
               bestD = d;
               best = e;
            }
         }
         this.prey = best;
         return best != null;
      }

      @Override
      public boolean canContinueToUse() {
         return this.prey != null && !this.c.carrying() && (this.grab >= 0 || prey(this.prey)) && this.giveUp < 400 && !this.c.severed();
      }

      @Override
      public void start() {
         this.grab = -1;
         this.repath = 0;
         this.giveUp = 0;
         ChitauriCaptives.claim(this.prey, this.c);
      }

      @Override
      public void stop() {
         if (this.grab >= 0 && this.prey != null && !this.c.carrying()) {
            CitizenCaptivity.release(this.prey); // interrupted grab: the person runs again
         }
         ChitauriCaptives.unclaim(this.prey, this.c);
         this.prey = null;
         this.grab = -1;
         this.c.getNavigation().stop();
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void tick() {
         LivingEntity p = this.prey;
         if (p == null) {
            return;
         }
         this.giveUp++;
         this.c.getLookControl().setLookAt(p, 40.0F, 40.0F);
         double d = this.c.distanceTo(p);
         ServerLevel level = (ServerLevel)this.c.level();
         if (this.grab >= 0) {
            this.c.getNavigation().stop();
            p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
            if (++this.grab >= CAPTURE_TICKS) {
               this.c.pickUp(p);
               this.grab = -1;
            }
            return;
         }
         if (d < 2.3) {
            // the grab: the person is held still (tag) while the clip reaches, clamps and lifts
            this.grab = 0;
            CitizenCaptivity.hold(p, "");
            if (p instanceof net.minecraft.world.entity.Mob pm) {
               pm.getNavigation().stop();
            }
            this.c.level().broadcastEntityEvent(this.c, EVENT_CAPTURE);
            ChitauriSounds.play(level, this.c.position(), ChitauriSounds.CAPTOR_TETHER, 1.0F, 1.3F);
            return;
         }
         if (d < 11.0 && d > 3.5 && this.c.tetherCooldown <= 0 && this.c.getSensing().hasLineOfSight(p)) {
            // the tether: a slowing energy line from the projector
            this.c.tetherCooldown = 70;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 3, false, false));
            this.c.level().broadcastEntityEvent(this.c, EVENT_TETHER);
            ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.TETHER, this.c.muzzle(), p.position().add(0, p.getBbHeight() * 0.6, 0), 1.0F,
               this.c.getId()), this.c.position(), 64);
            ChitauriSounds.play(level, this.c.position(), ChitauriSounds.CAPTOR_TETHER, 1.4F, 0.9F + this.c.random.nextFloat() * 0.15F);
         }
         if (--this.repath <= 0) {
            this.repath = 10;
            this.c.getNavigation().moveTo(p, d > 8.0 ? 1.25 : 1.05);
         }
      }
   }

   /** Carry the person to the nearest containment field (a new one is set up when none has room). */
   static final class CarryGoal extends Goal {
      private final ChitauriCaptor c;
      @Nullable
      private Vec3 dest;
      private int repath;
      private int ticks;

      CarryGoal(ChitauriCaptor c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         return this.c.carrying() && this.c.carried != null && !this.c.isPassenger();
      }

      @Override
      public void start() {
         this.dest = null;
         this.repath = 0;
         this.ticks = 0;
      }

      @Override
      public void stop() {
         this.c.getNavigation().stop();
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void tick() {
         ServerLevel level = (ServerLevel)this.c.level();
         this.ticks++;
         if (this.dest == null || this.ticks % 100 == 0) {
            this.dest = ChitauriCaptives.containmentPoint(level, this.c);
         }
         if (this.dest == null) {
            if (this.ticks > 600) {
               this.c.drop(false); // nowhere to take them: let go
            }
            return;
         }
         double dx = this.dest.x - this.c.getX();
         double dz = this.dest.z - this.c.getZ();
         if (dx * dx + dz * dz < 9.0) {
            this.c.drop(true);
            return;
         }
         if (--this.repath <= 0 || this.c.getNavigation().isDone()) {
            this.repath = 30;
            Vec3 me = this.c.position();
            Vec3 goal = this.dest;
            if (me.distanceTo(goal) > 24.0) {
               Vec3 step = me.add(goal.subtract(me).normalize().scale(20.0));
               Vec3 p = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(this.c, 20, 6, step, Math.PI / 3.0);
               if (p != null) {
                  goal = p;
               }
            }
            this.c.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.1);
         }
         if (this.ticks > 1800) {
            this.c.drop(false); // stuck for 90 s: the person gets away
         }
      }
   }
}

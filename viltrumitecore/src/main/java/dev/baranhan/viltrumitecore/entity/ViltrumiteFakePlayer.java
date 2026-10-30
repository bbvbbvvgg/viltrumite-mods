package dev.baranhan.viltrumitecore.entity;

import com.mojang.authlib.GameProfile;
import dev.baranhan.viltrumitecore.entity.action.ApproachAction;
import dev.baranhan.viltrumitecore.entity.action.BlockAction;
import dev.baranhan.viltrumitecore.entity.action.DashCatchAction;
import dev.baranhan.viltrumitecore.entity.action.GrabComboAction;
import dev.baranhan.viltrumitecore.entity.action.IdleAction;
import dev.baranhan.viltrumitecore.entity.action.MeleeCombatAction;
import dev.baranhan.viltrumitecore.entity.action.ViltrumiteAction;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteStatHolder;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ViltrumiteFakePlayer extends ServerPlayer {
   private LivingEntity target = null;
   private ViltrumiteFakePlayer.TargetMode targetMode = ViltrumiteFakePlayer.TargetMode.PLAYER_AGGRESSIVE;
   private final List<ViltrumiteAction> actionList = new ArrayList<>();
   private ViltrumiteAction currentAction = null;
   private int intelligence = 1;
   public int attackCooldown = 0;
   private int targetSearchDelay = 0;

   public ViltrumiteFakePlayer(
      MinecraftServer server,
      ServerLevel world,
      String name,
      float scale,
      int intelligence,
      ViltrumiteFakePlayer.TargetMode mode,
      float baseDamage,
      float damageIgnoreThreshold,
      float damageReduction,
      float healFactor,
      float maxFlightSpeed,
      float throttleSpeed
   ) {
      super(server, world, new GameProfile(UUID.randomUUID(), name));
      this.setGameMode(GameType.SURVIVAL);
      this.intelligence = intelligence;
      this.targetMode = mode != null ? mode : ViltrumiteFakePlayer.TargetMode.PLAYER_AGGRESSIVE;
      ViltrumiteStatHolder stats = (ViltrumiteStatHolder)this;
      stats.setBaseDamage(baseDamage);
      stats.setDamageIgnoreThreshold(damageIgnoreThreshold);
      stats.setDamageReduction(damageReduction);
      stats.setHealFactor(healFactor);
      ViltrumiteFlightPlayer flight = (ViltrumiteFlightPlayer)this;
      flight.setMaxFlightSpeed(maxFlightSpeed);
      flight.setThrottleSpeed(throttleSpeed);
      ViltrumiteCorePlayer core = (ViltrumiteCorePlayer)this;
      // Squad NPCs (Conquest, Thula, Lucan, Vidor, spawner clones) are real Viltrumites: the legacy
      // kit hero, not the player race. The legacy setViltrumite(true) adapter maps to HOMELANDER since
      // Homelander replaced the race, and his skin provider then overrode the NPC's own skin, model and
      // cape. Set below, once the connection exists: identity only, like a save restore (no hero lifecycle and no greeting).
      core.setChosenRace(true);
      core.setCloneScale(scale);
      this.getEntityData().set(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte)127);
      Connection dummyConnection = new Connection(PacketFlow.SERVERBOUND);
      this.connection = new ServerGamePacketListenerImpl(server, dummyConnection, this);
      dev.baranhan.viltrumitecore.hero.ViltrumiteHero.becomeSquadNpc(this);
      dev.baranhan.viltrumitecore.hero.HeroFlightGrant.sync(this);
      dev.baranhan.viltrumitecore.hero.HeroRegistry.syncSnapshot(this);
      this.refreshDimensions();
      this.actionList.add(new BlockAction(this));
      if (this.getIntelligence() > 1) {
         this.actionList.add(new GrabComboAction(this));
      }

      this.actionList.add(new DashCatchAction(this));
      this.actionList.add(new MeleeCombatAction(this));
      this.actionList.add(new ApproachAction(this));
      this.actionList.add(new IdleAction(this));
   }

   public boolean hurt(DamageSource source, float amount) {
      boolean damaged = super.hurt(source, amount);
      if (damaged && source.getEntity() instanceof LivingEntity attacker) {
         boolean sameFaction = this.targetMode == ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES && attacker instanceof ViltrumiteFakePlayer;
         if (attacker != this && attacker.isAlive() && !sameFaction) {
            this.target = attacker;
            this.targetSearchDelay = 0;
         }
      }

      return damaged;
   }

   public void tick() {
      if (!this.level().isClientSide()) {
         this.tickAI();
      }

      super.tick();
      if (!this.level().isClientSide()) {
         this.doTick();
         if (this.isDeadOrDying()) {
            this.deathTime++;
            if (this.deathTime >= 20) {
               this.discard();
            }
         } else {
            ViltrumiteFlightPlayer flight = (ViltrumiteFlightPlayer)this;
            if (flight.getFlightState() == FlightState.HOVER || flight.getFlightState() == FlightState.NONE) {
               this.move(MoverType.SELF, this.getDeltaMovement());
               Vec3 vel = this.getDeltaMovement();
               if (flight.getFlightState() == FlightState.HOVER) {
                  this.setDeltaMovement(vel.multiply(0.91, 0.91, 0.91));
               } else if (this.onGround()) {
                  this.setDeltaMovement(vel.multiply(0.91, 0.98, 0.91));
               } else {
                  this.setDeltaMovement(vel.multiply(0.91, 0.98, 0.91).add(0.0, -0.08, 0.0));
               }
            }
         }
      }
   }

   private void tickAI() {
      if (this.attackCooldown > 0) {
         this.attackCooldown--;
      }

      if (this.targetSearchDelay > 0) {
         this.targetSearchDelay--;
      }

      if (this.target == null || !this.target.isAlive() || this.targetSearchDelay <= 0) {
         this.findBestTarget();
         this.targetSearchDelay = 20;
      }

      for (ViltrumiteAction action : this.actionList) {
         if (action == this.currentAction) {
            break;
         }

         if (action.canStart()) {
            if (this.currentAction != null) {
               this.currentAction.stop();
            }

            this.currentAction = action;
            this.currentAction.start();
            break;
         }
      }

      if (this.currentAction != null && this.currentAction.shouldStop()) {
         this.currentAction.stop();
         this.currentAction = null;
      }

      if (this.currentAction == null) {
         for (ViltrumiteAction action : this.actionList) {
            if (action.canStart()) {
               this.currentAction = action;
               this.currentAction.start();
               break;
            }
         }
      }

      if (this.currentAction != null) {
         this.currentAction.tick();
      }
   }

   private void findBestTarget() {
      if (this.targetMode != ViltrumiteFakePlayer.TargetMode.NEUTRAL || this.target != null) {
         double searchRadius = 64.0;
         AABB searchBox = this.getBoundingBox().inflate(searchRadius);
         List<LivingEntity> potentialTargets = this.level().getEntitiesOfClass(LivingEntity.class, searchBox, this::isValidTarget);
         LivingEntity bestTarget = null;
         double bestScore = Double.MAX_VALUE;

         for (LivingEntity entity : potentialTargets) {
            double distSqr = this.distanceToSqr(entity);
            boolean canSee = this.hasLineOfSight(entity);
            if (canSee || !(distSqr > 400.0) && !(Math.abs(entity.getY() - this.getY()) > 12.0)) {
               double score = distSqr;
               if (entity instanceof Player) {
                  score = distSqr - 2000.0;
               }

               if (!canSee) {
                  score += 500.0;
               }

               if (entity == this.target) {
                  score -= 300.0;
               }

               if (score < bestScore) {
                  bestScore = score;
                  bestTarget = entity;
               }
            }
         }

         this.target = bestTarget;
      }
   }

   private boolean isValidTarget(LivingEntity entity) {
      if (entity == this || !entity.isAlive()) {
         return false;
      } else if (entity instanceof ArmorStand) {
         return false;
      } else {
         if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
         }

         if (this.targetMode == ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES && entity instanceof ViltrumiteFakePlayer) {
            return false;
         } else if (entity == this.target) {
            return true;
         } else if (entity instanceof Player) {
            return true;
         } else if (this.targetMode == ViltrumiteFakePlayer.TargetMode.PLAYER_AGGRESSIVE) {
            return false;
         } else {
            return this.targetMode == ViltrumiteFakePlayer.TargetMode.HOSTILE_AGGRESSIVE
               ? entity instanceof Monster
               : this.targetMode == ViltrumiteFakePlayer.TargetMode.AGGRESSIVE || this.targetMode == ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES;
         }
      }
   }

   public LivingEntity getTarget() {
      return this.target;
   }

   public void setTarget(LivingEntity newTarget) {
      this.target = newTarget;
   }

   public void lookAtTarget(LivingEntity target) {
      if (target != null) {
         double dX = target.getX() - this.getX();
         double dZ = target.getZ() - this.getZ();
         double dY = target.getEyeY() - this.getEyeY();
         double horizontalDist = Math.sqrt(dX * dX + dZ * dZ);
         float targetYaw = (float)(Math.toDegrees(Math.atan2(dZ, dX)) - 90.0);
         float targetPitch = (float)(-Math.toDegrees(Math.atan2(dY, horizontalDist)));
         this.yRotO = this.getYRot();
         this.xRotO = this.getXRot();
         this.yHeadRotO = this.yHeadRot;
         this.yBodyRotO = this.yBodyRot;
         this.setYRot(targetYaw);
         this.setYHeadRot(targetYaw);
         this.yBodyRot = targetYaw;
         this.setXRot(targetPitch);
      }
   }

   public EntityDimensions getDimensions(Pose pose) {
      float scale = ((ViltrumiteCorePlayer)this).getCloneScale();
      return super.getDimensions(pose).scale(scale);
   }

   public float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
      float scale = ((ViltrumiteCorePlayer)this).getCloneScale();
      return super.getStandingEyeHeight(pose, dimensions) * scale;
   }

   public int getIntelligence() {
      return this.intelligence;
   }

   public void setIntelligence(int intelligence) {
      this.intelligence = intelligence;
   }

   public ViltrumiteFakePlayer.TargetMode getTargetMode() {
      return this.targetMode;
   }

   public void setTargetMode(ViltrumiteFakePlayer.TargetMode targetMode) {
      this.targetMode = targetMode;
   }

   public static enum TargetMode {
      NEUTRAL,
      PLAYER_AGGRESSIVE,
      HOSTILE_AGGRESSIVE,
      AGGRESSIVE,
      AGGRESSIVE_NO_ALLIES;
   }
}

package dev.baranhan.viltrumitecore.mixin;

import com.mojang.brigadier.tree.CommandNode;
import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.BarrageHitS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.GrabbedPosSyncS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.MeltedBlocksS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.PlayerGrabStateSyncS2CPacket;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.util.ChopImpactManager;
import dev.baranhan.viltrumitecore.util.PunchImpactManager;
import dev.baranhan.viltrumitecore.util.ThunderClapManager;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteStatHolder;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Player.class},
   priority = 1200
)
public abstract class PlayerEntityCoreMixin implements ViltrumiteCorePlayer {
   @Unique
   private static final EntityDataAccessor<Boolean> HAS_CHOSEN_RACE = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Boolean> IS_DASHING = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Integer> DASH_TICKS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Integer> PUNCH_TICKS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Boolean> IS_LEFT_ARM_PUNCH = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Float> PUNCH_STRENGTH = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Boolean> TRYING_TO_GRAB = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Integer> GRABBED_TARGET_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Boolean> IS_BLOCKING = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Boolean> IS_SUPER_SPEED = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Integer> CHOP_TICKS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Boolean> IS_LEFT_CHOP = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Integer> CHOP_TYPE = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Integer> THUNDERCLAP_TICKS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Boolean> IS_BARRAGING = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Integer> BARRAGE_TICKS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Boolean> IS_LEFT_BARRAGE_ARM = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Integer> BLOCK_COOLDOWN = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Integer> BARRAGE_COOLDOWN = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Integer> PUNCH_COOLDOWN = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Float> CLONE_SCALE = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final int MAX_DASH_TICKS = 10;
   @Unique
   private float prevDashProgress = 0.0F;
   @Unique
   private float dashProgress = 0.0F;
   @Unique
   private int lastImpactTick = 0;
   @Unique
   private Vec3 calculatedHandPos = null;
   @Unique
   private Vec3 chopHandPos = null;
   @Unique
   private Vec3 serverHandPos = null;
   @Unique
   private Vec3 firstPersonLocalHandPos = null;
   @Unique
   private int internalBlockTicks = 0;
   @Unique
   private int localPunchTicks = 0;
   @Unique
   private boolean localIsDashing = false;
   @Unique
   private int localDashTicks = 0;
   @Unique
   private int localChopTicks = 0;
   @Unique
   private boolean localIsLeftChop = false;
   @Unique
   private int localChopType = 0;
   @Unique
   private final float baseFlySpeed = 0.05F;
   @Unique
   private final float baseWalkSpeed = 0.1F;
   @Unique
   private int localThunderclapTicks = 0;
   @Unique
   private Vec3 calculatedHandOffset = null;
   @Unique
   private float calculatedHandPitch = 0.0F;
   @Unique
   private float calculatedHandYaw = 0.0F;
   @Unique
   private boolean localIsBarraging = false;
   @Unique
   private int localBarrageTicks = 0;
   @Unique
   private boolean localIsLeftBarrageArm = false;
   @Unique
   private float calculatedBodyPitch = 0.0F;
   @Unique
   private float calculatedBodyYaw = 0.0F;
   @Unique
   private static final AttributeModifier SUPER_SPEED_MODIFIER = new AttributeModifier(
      UUID.fromString("9b9a6745-6677-4467-9388-6447814b1622"), "Viltrumite Super Speed", 1.05, Operation.ADDITION
   );

   @Inject(
      method = {"defineSynchedData"},
      at = {@At("TAIL")}
   )
   protected void onInitDataTracker(CallbackInfo ci) {
      Player player = (Player)(Object)this;
      player.getEntityData().define(HAS_CHOSEN_RACE, false);
      player.getEntityData().define(IS_DASHING, false);
      player.getEntityData().define(DASH_TICKS, 0);
      player.getEntityData().define(PUNCH_TICKS, 0);
      player.getEntityData().define(IS_LEFT_ARM_PUNCH, false);
      player.getEntityData().define(PUNCH_STRENGTH, 0.0F);
      player.getEntityData().define(TRYING_TO_GRAB, false);
      player.getEntityData().define(GRABBED_TARGET_ID, -1);
      player.getEntityData().define(IS_BLOCKING, false);
      player.getEntityData().define(IS_SUPER_SPEED, false);
      player.getEntityData().define(CHOP_TICKS, 0);
      player.getEntityData().define(IS_LEFT_CHOP, false);
      player.getEntityData().define(CHOP_TYPE, 0);
      player.getEntityData().define(THUNDERCLAP_TICKS, 0);
      player.getEntityData().define(IS_BARRAGING, false);
      player.getEntityData().define(BARRAGE_TICKS, 0);
      player.getEntityData().define(IS_LEFT_BARRAGE_ARM, false);
      player.getEntityData().define(BLOCK_COOLDOWN, 0);
      player.getEntityData().define(BARRAGE_COOLDOWN, 0);
      player.getEntityData().define(PUNCH_COOLDOWN, 0);
      player.getEntityData().define(CLONE_SCALE, 1.0F);
   }

   @Unique
   private void setInternalDashing(boolean dashing) {
      if (this.isViltrumiteLocal()) {
         this.localIsDashing = dashing;
      }

      ((Player)(Object)this).getEntityData().set(IS_DASHING, dashing);
   }

   @Unique
   private int getInternalDashTicks() {
      return this.isViltrumiteLocal() ? this.localDashTicks : (Integer)((Player)(Object)this).getEntityData().get(DASH_TICKS);
   }

   @Unique
   private void setInternalDashTicks(int ticks) {
      if (this.isViltrumiteLocal()) {
         this.localDashTicks = ticks;
      }

      ((Player)(Object)this).getEntityData().set(DASH_TICKS, ticks);
   }

   @Override
   public boolean isViltrumite() {
      // "Viltrumite" = user of the shared legacy kit (Homelander replaced the race).
      Player self = (Player)(Object)this;
      return dev.baranhan.viltrumitecore.hero.HeroRegistry.get(self).allowsLegacyAbilities(self);
   }

   @Override
   public void setViltrumite(boolean isViltrumite) {
      // Legacy adapter: the boolean only ever toggles HUMAN<->VILTRUMITE and
      // can never convert or clear another hero identity.
      // Live changes on the server run the full hero lifecycle (cleanup, loadout, session).
      if ((Object)this instanceof net.minecraft.server.level.ServerPlayer serverPlayer && (Object)this instanceof HeroPlayer heroPlayer) {
         dev.baranhan.viltrumitecore.hero.HeroRegistry.changeHero(serverPlayer, HeroId.legacySet(heroPlayer.getHeroId(), isViltrumite));
      } else {
         this.viltrumitecore$setLegacyIdentity(isViltrumite);
      }
   }

   /** Identity-only legacy write (NBT load, client): never a lifecycle. */
   @org.spongepowered.asm.mixin.Unique
   private void viltrumitecore$setLegacyIdentity(boolean isViltrumite) {
      if ((Object)this instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroId(HeroId.legacySet(heroPlayer.getHeroId(), isViltrumite));
      }
   }

   @Override
   public boolean hasChosenRace() {
      return (Boolean)((Player)(Object)this).getEntityData().get(HAS_CHOSEN_RACE);
   }

   @Override
   public void setChosenRace(boolean chosen) {
      ((Player)(Object)this).getEntityData().set(HAS_CHOSEN_RACE, chosen);
   }

   @Override
   public boolean isDashing() {
      return this.isViltrumiteLocal() ? this.localIsDashing : (Boolean)((Player)(Object)this).getEntityData().get(IS_DASHING);
   }

   @Override
   public void setDashing(boolean dashing) {
      this.setInternalDashing(dashing);
      if (!dashing) {
         this.setInternalDashTicks(0);
      }
   }

   @Override
   public void startDash() {
      if (!this.isDashing() && !this.isBlocking()) {
         Player player = (Player)(Object)this;
         if ((Object)this instanceof ViltrumiteFlightPlayer flightPlayer
            && (flightPlayer.getFlightState() == FlightState.NONE || flightPlayer.getFlightState() == FlightState.HOVER)) {
            this.setInternalDashing(true);
            this.setInternalDashTicks(0);
            player.level()
               .playSound(
                  player, player.getX(), player.getY(), player.getZ(), (SoundEvent)ViltrumiteCore.DASH_EVENT.get(), SoundSource.PLAYERS, 1.5F, 1.0F
               );
         }
      }
   }

   @Override
   public float getDashProgress(float tickDelta) {
      return Mth.lerp(tickDelta, this.prevDashProgress, this.dashProgress);
   }

   @Override
   public boolean isLeftArmPunch() {
      return (Boolean)((Player)(Object)this).getEntityData().get(IS_LEFT_ARM_PUNCH);
   }

   @Override
   public void setLeftArmPunch(boolean leftArm) {
      ((Player)(Object)this).getEntityData().set(IS_LEFT_ARM_PUNCH, leftArm);
   }

   @Override
   public float getPunchStrength() {
      return (Float)((Player)(Object)this).getEntityData().get(PUNCH_STRENGTH);
   }

   @Override
   public void setPunchStrength(float strength) {
      ((Player)(Object)this).getEntityData().set(PUNCH_STRENGTH, strength);
   }

   @Override
   public int getPunchTicks() {
      return this.isViltrumiteLocal() ? this.localPunchTicks : (Integer)((Player)(Object)this).getEntityData().get(PUNCH_TICKS);
   }

   @Override
   public int getPunchCooldown() {
      return (Integer)((Player)(Object)this).getEntityData().get(PUNCH_COOLDOWN);
   }

   @Override
   public void setPunchCooldown(int ticks) {
      ((Player)(Object)this).getEntityData().set(PUNCH_COOLDOWN, ticks);
   }

   @Override
   public void setPunchTicks(int ticks) {
      if (!this.isBlocking() || ticks <= 0) {
         Player player = (Player)(Object)this;
         if (ticks == 20 && !player.level().isClientSide()) {
            if (this.getPunchCooldown() > 0) {
               if (this.isViltrumiteLocal()) {
                  this.localPunchTicks = 0;
               }

               return;
            }

            this.setPunchCooldown(40);
         }

         if (this.isViltrumiteLocal()) {
            this.localPunchTicks = ticks;
         }

         player.getEntityData().set(PUNCH_TICKS, ticks);
         if (ticks == 20) {
            if ((Object)this instanceof ViltrumiteFlightPlayer flightPlayer && flightPlayer.getFlightState() != FlightState.NONE) {
               this.setPunchStrength(flightPlayer.getFlightThrottle());
               return;
            }

            this.setPunchStrength(0.0F);
         }
      }
   }

   @Override
   public boolean isTryingToGrab() {
      return (Boolean)((Player)(Object)this).getEntityData().get(TRYING_TO_GRAB);
   }

   @Override
   public void setTryingToGrab(boolean trying) {
      if (!this.isBlocking() || !trying) {
         ((Player)(Object)this).getEntityData().set(TRYING_TO_GRAB, trying);
      }
   }

   @Override
   public LivingEntity getGrabbedTarget() {
      int id = (Integer)((Player)(Object)this).getEntityData().get(GRABBED_TARGET_ID);
      if (id == -1) {
         return null;
      } else {
         Entity entity = ((Player)(Object)this).level().getEntity(id);
         return entity instanceof LivingEntity ? (LivingEntity)entity : null;
      }
   }

   @Override
   public void setGrabbedTarget(LivingEntity target) {
      ((Player)(Object)this).getEntityData().set(GRABBED_TARGET_ID, target == null ? -1 : target.getId());
   }

   @Override
   public void releaseTarget() {
      LivingEntity target = this.getGrabbedTarget();
      if (target != null) {
         Player player = (Player)(Object)this;
         if (target instanceof Mob mob) {
            mob.setNoAi(false);
         }

         if (target instanceof ServerPlayer grabbedPlayer) {
            CoreMessages.sendToPlayer(new PlayerGrabStateSyncS2CPacket(false, null), grabbedPlayer);
            ViltrumiteCorePlayer coreTarget = (ViltrumiteCorePlayer)grabbedPlayer;
            if (!coreTarget.isViltrumite() && !grabbedPlayer.isCreative() && !grabbedPlayer.isSpectator()) {
               grabbedPlayer.getAbilities().mayfly = false;
               grabbedPlayer.getAbilities().flying = false;
               grabbedPlayer.onUpdateAbilities();
            }

            grabbedPlayer.connection
               .teleport(grabbedPlayer.getX(), grabbedPlayer.getY(), grabbedPlayer.getZ(), grabbedPlayer.getYRot(), grabbedPlayer.getXRot());
         }

         target.removeTag("ViltrumiteGrabbed");
         target.setYHeadRot(target.getYRot());
         target.yBodyRot = target.getYRot();
         target.yBodyRotO = target.getYRot();
         target.yHeadRotO = target.getYRot();
         this.setGrabbedTarget(null);
      }

      this.setTryingToGrab(false);
   }

   @Override
   public Vec3 getCalculatedHandPos() {
      return this.calculatedHandPos;
   }

   @Override
   public void setCalculatedHandPos(Vec3 pos) {
      this.calculatedHandPos = pos;
   }

   @Override
   public Vec3 getChopHandPos() {
      return this.chopHandPos;
   }

   @Override
   public void setChopHandPos(Vec3 pos) {
      this.chopHandPos = pos;
   }

   @Override
   public Vec3 getServerHandPos() {
      return this.serverHandPos;
   }

   @Override
   public void setServerHandPos(Vec3 pos) {
      this.serverHandPos = pos;
   }

   @Override
   public float getCloneScale() {
      return (Float)((Player)(Object)this).getEntityData().get(CLONE_SCALE);
   }

   @Override
   public void setCloneScale(float scale) {
      ((Player)(Object)this).getEntityData().set(CLONE_SCALE, scale);
   }

   @Override
   public int getBlockCooldown() {
      return (Integer)((Player)(Object)this).getEntityData().get(BLOCK_COOLDOWN);
   }

   @Override
   public void setBlockCooldown(int ticks) {
      ((Player)(Object)this).getEntityData().set(BLOCK_COOLDOWN, ticks);
   }

   @Override
   public boolean isBlocking() {
      return (Boolean)((Player)(Object)this).getEntityData().get(IS_BLOCKING);
   }

   @Override
   public void setBlocking(boolean blocking) {
      if (!((Player)(Object)this).level().isClientSide()) {
         if (blocking) {
            if (this.getBlockCooldown() > 0) {
               ((Player)(Object)this).getEntityData().set(IS_BLOCKING, false);
               return;
            }

            if (!this.isBlocking()) {
               this.internalBlockTicks = 40;
            }
         } else if (this.isBlocking()) {
            this.setBlockCooldown(60);
         }
      }

      ((Player)(Object)this).getEntityData().set(IS_BLOCKING, blocking);
   }

   @Override
   public boolean isSuperSpeed() {
      return (Boolean)((Player)(Object)this).getEntityData().get(IS_SUPER_SPEED);
   }

   @Override
   public void setSuperSpeed(boolean superSpeed) {
      ((Player)(Object)this).getEntityData().set(IS_SUPER_SPEED, superSpeed);
   }

   @Override
   public int getChopTicks() {
      return this.isViltrumiteLocal() ? this.localChopTicks : (Integer)((Player)(Object)this).getEntityData().get(CHOP_TICKS);
   }

   @Override
   public void setChopTicks(int ticks) {
      if (!this.isBlocking() || ticks <= 0) {
         if (this.isViltrumiteLocal()) {
            this.localChopTicks = ticks;
         }

         ((Player)(Object)this).getEntityData().set(CHOP_TICKS, ticks);
      }
   }

   @Override
   public boolean isLeftChop() {
      return this.isViltrumiteLocal() ? this.localIsLeftChop : (Boolean)((Player)(Object)this).getEntityData().get(IS_LEFT_CHOP);
   }

   @Override
   public void setLeftChop(boolean leftChop) {
      if (this.isViltrumiteLocal()) {
         this.localIsLeftChop = leftChop;
      }

      ((Player)(Object)this).getEntityData().set(IS_LEFT_CHOP, leftChop);
   }

   @Override
   public int getChopType() {
      return this.isViltrumiteLocal() ? this.localChopType : (Integer)((Player)(Object)this).getEntityData().get(CHOP_TYPE);
   }

   @Override
   public void setChopType(int type) {
      if (this.isViltrumiteLocal()) {
         this.localChopType = type;
      }

      ((Player)(Object)this).getEntityData().set(CHOP_TYPE, type);
   }

   @Override
   public Vec3 getFirstPersonLocalHandPos() {
      return this.firstPersonLocalHandPos;
   }

   @Override
   public void setFirstPersonLocalHandPos(Vec3 pos) {
      this.firstPersonLocalHandPos = pos;
   }

   @Override
   public int getThunderclapTicks() {
      return this.isViltrumiteLocal() ? this.localThunderclapTicks : (Integer)((Player)(Object)this).getEntityData().get(THUNDERCLAP_TICKS);
   }

   @Override
   public void setThunderclapTicks(int ticks) {
      if (!this.isBlocking() || ticks <= 0) {
         if (this.isViltrumiteLocal()) {
            this.localThunderclapTicks = ticks;
         }

         ((Player)(Object)this).getEntityData().set(THUNDERCLAP_TICKS, ticks);
      }
   }

   @Override
   public Vec3 getCalculatedHandOffset() {
      return this.calculatedHandOffset;
   }

   @Override
   public void setCalculatedHandOffset(Vec3 offset) {
      this.calculatedHandOffset = offset;
   }

   @Override
   public boolean isBarraging() {
      return this.isViltrumiteLocal() ? this.localIsBarraging : (Boolean)((Player)(Object)this).getEntityData().get(IS_BARRAGING);
   }

   @Override
   public void setBarraging(boolean barraging) {
      if (!this.isBlocking() || !barraging) {
         if (!((Player)(Object)this).level().isClientSide()) {
            if (barraging) {
               if (this.getBarrageCooldown() > 0) {
                  if (this.isViltrumiteLocal()) {
                     this.localIsBarraging = false;
                  }

                  ((Player)(Object)this).getEntityData().set(IS_BARRAGING, false);
                  return;
               }
            } else if (this.isBarraging()) {
               this.setBarrageCooldown(80);
            }
         }

         if (this.isViltrumiteLocal()) {
            this.localIsBarraging = barraging;
         }

         ((Player)(Object)this).getEntityData().set(IS_BARRAGING, barraging);
      }
   }

   @Override
   public int getBarrageTicks() {
      return this.isViltrumiteLocal() ? this.localBarrageTicks : (Integer)((Player)(Object)this).getEntityData().get(BARRAGE_TICKS);
   }

   @Override
   public void setBarrageTicks(int ticks) {
      if (this.isViltrumiteLocal()) {
         this.localBarrageTicks = ticks;
      }

      ((Player)(Object)this).getEntityData().set(BARRAGE_TICKS, ticks);
   }

   @Override
   public boolean isLeftBarrageArm() {
      return this.isViltrumiteLocal() ? this.localIsLeftBarrageArm : (Boolean)((Player)(Object)this).getEntityData().get(IS_LEFT_BARRAGE_ARM);
   }

   @Override
   public void setLeftBarrageArm(boolean leftArm) {
      if (this.isViltrumiteLocal()) {
         this.localIsLeftBarrageArm = leftArm;
      }

      ((Player)(Object)this).getEntityData().set(IS_LEFT_BARRAGE_ARM, leftArm);
   }

   @Override
   public int getBarrageCooldown() {
      return (Integer)((Player)(Object)this).getEntityData().get(BARRAGE_COOLDOWN);
   }

   @Override
   public void setBarrageCooldown(int ticks) {
      ((Player)(Object)this).getEntityData().set(BARRAGE_COOLDOWN, ticks);
   }

   @Override
   public float getCalculatedHandPitch() {
      return this.calculatedHandPitch;
   }

   @Override
   public void setCalculatedHandPitch(float pitch) {
      this.calculatedHandPitch = pitch;
   }

   @Override
   public float getCalculatedHandYaw() {
      return this.calculatedHandYaw;
   }

   @Override
   public void setCalculatedHandYaw(float yaw) {
      this.calculatedHandYaw = yaw;
   }

   @Override
   public float getCalculatedBodyPitch() {
      return this.calculatedBodyPitch;
   }

   @Override
   public void setCalculatedBodyPitch(float pitch) {
      this.calculatedBodyPitch = pitch;
   }

   @Override
   public float getCalculatedBodyYaw() {
      return this.calculatedBodyYaw;
   }

   @Override
   public void setCalculatedBodyYaw(float yaw) {
      this.calculatedBodyYaw = yaw;
   }

   @Inject(
      method = {"attack"},
      at = {@At("HEAD")}
   )
   private void onAttackGrabbedTarget(Entity target, CallbackInfo ci) {
      LivingEntity grabbed = this.getGrabbedTarget();
      if (grabbed != null && grabbed.equals(target)) {
         this.releaseTarget();
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void onTick(CallbackInfo ci) {
      Player player = (Player)(Object)this;
      this.prevDashProgress = this.dashProgress;
      this.tickPunch(player);
      this.tickDash(player);
      this.tickDrill(player);
      this.tickGrab(player);
      this.tickBlock(player);
      this.tickSuperSpeed(player);
      this.tickChop(player);
      this.tickStrength(player);
      this.tickThunderclap(player);
      this.tickBarrage(player);
      // Legacy: no vanilla flight outside the shared kit. Server only: the client keeps the server's ability
      // flags. A client-side wipe raced the hero snapshot (abilities packet arrives a tick before SUIT_WORN),
      // cleared the local mayfly, and the server never resent it — survival flight was dead for Iron Man.
      // Heroes with their own flight get mayfly through HeroFlightGrant and are skipped.
      if (!player.level().isClientSide() && !player.isCreative() && !player.isSpectator() && !this.isViltrumite()
         && !player.getTags().contains("ViltrumiteGrabbed")
         && !dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).allowsFlight(player)
         && (player.getAbilities().mayfly || player.getAbilities().flying)) {
         player.getAbilities().mayfly = false;
         player.getAbilities().flying = false;
         player.onUpdateAbilities();
      }

      if (!player.level().isClientSide()) {
         PunchImpactManager.tickMeteorPhysics((ServerLevel)player.level());
         ChopImpactManager.tickBleeding((ServerLevel)player.level());
         ThunderClapManager.tickThunderclaps((ServerLevel)player.level());
         this.checkSpaceTransition((ServerPlayer)player);
      }

      if (player instanceof ViltrumiteFlightPlayer flightPlayer && flightPlayer.getFlightState() != FlightState.NONE && this.isSuperSpeed()) {
         this.setSuperSpeed(false);
      }
   }

   @Unique
   private void tickPunch(Player player) {
      if (!player.level().isClientSide()) {
         int currentCooldown = this.getPunchCooldown();
         if (currentCooldown > 0) {
            this.setPunchCooldown(currentCooldown - 1);
         }
      }

      int currentTicks = this.getPunchTicks();
      if (currentTicks > 0) {
         player.yBodyRot = player.getYRot();
         player.setYHeadRot(player.getYRot());
         if (this.getPunchStrength() > 0.0F && player instanceof ViltrumiteFlightPlayer flightPlayer) {
            float currentThrottle = flightPlayer.getFlightThrottle();
            if (currentThrottle > 0.0F) {
               float brakeForce = this.getPunchStrength() / 3.0F;
               flightPlayer.setFlightThrottle(Math.max(0.0F, currentThrottle - brakeForce));
            } else {
               flightPlayer.setFlightState(FlightState.HOVER);
               flightPlayer.setSpeedLocked(false);
               flightPlayer.setFlightThrottle(0.0F);
               flightPlayer.setFlightAccelerating(false);
            }
         }

         int impactTick = 15;
         if (currentTicks == impactTick) {
            if (!player.level().isClientSide()) {
               PunchImpactManager.executePunch((ServerPlayer)player);
            }

            player.level()
               .playSound(
                  player,
                  player.getX(),
                  player.getY(),
                  player.getZ(),
                  (SoundEvent)ViltrumiteCore.PUNCH_IMPACT_EVENT.get(),
                  SoundSource.PLAYERS,
                  1.5F,
                  1.0F
               );
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.5F);
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
         }

         this.setPunchTicks(currentTicks - 1);
         if (this.getPunchTicks() > 3) {
            player.xCloak = player.getX();
            player.yCloak = player.getY();
            player.zCloak = player.getZ();
         }
      }
   }

   @Unique
   private void tickDash(Player player) {
      if (!this.isDashing()) {
         this.dashProgress = 0.0F;
      } else {
         int currentDashTicks = this.getInternalDashTicks() + 1;
         this.setInternalDashTicks(currentDashTicks);
         if (currentDashTicks <= 5) {
            Vec3 lookVec = player.getLookAngle();
            float dashForce = 4.0F;
            if (player instanceof ViltrumiteFakePlayer) {
               dashForce = 1.0F;
            }

            if (player instanceof ViltrumiteFlightPlayer flightPlayer) {
               if (flightPlayer.getFlightState() == FlightState.CRUISE) {
                  flightPlayer.setFlightThrottle(flightPlayer.getFlightThrottle() + dashForce * 0.1F);
               } else {
                  dashForce /= flightPlayer.getFlightState() == FlightState.NONE ? 2.0F : 1.0F;
                  player.setDeltaMovement(lookVec.x * (double)dashForce, lookVec.y * (double)dashForce, lookVec.z * (double)dashForce);
                  player.hasImpulse = true;
               }
            }

            if (!player.level().isClientSide()) {
               AABB dashBox = player.getBoundingBox().expandTowards(lookVec.scale(1.5)).inflate(0.2);

               for (Entity entity : player.level().getEntities(player, dashBox)) {
                  if (entity instanceof LivingEntity) {
                     LivingEntity livingEntity = (LivingEntity)entity;
                     if (entity != this.getGrabbedTarget() && player instanceof ViltrumiteStatHolder) {
                        ViltrumiteStatHolder statHolder = (ViltrumiteStatHolder)player;
                        if (livingEntity.hurt(player.damageSources().playerAttack(player), statHolder.getBaseDamage() * 0.5F)) {
                           livingEntity.setDeltaMovement(livingEntity.getDeltaMovement().add(lookVec.x * 1.8F, 0.5, lookVec.z * 1.8F));
                           livingEntity.hasImpulse = true;
                        }
                     }
                  }
               }

               int minX = Mth.floor(dashBox.minX);
               int minY = Mth.floor(dashBox.minY);
               int minZ = Mth.floor(dashBox.minZ);
               int maxX = Mth.floor(dashBox.maxX);
               int maxY = Mth.floor(dashBox.maxY);
               int maxZ = Mth.floor(dashBox.maxZ);
               MutableBlockPos mutablePos = new MutableBlockPos();

               for (int x = minX; x <= maxX; x++) {
                  for (int y = minY; y <= maxY; y++) {
                     for (int z = minZ; z <= maxZ; z++) {
                        mutablePos.set(x, y, z);
                        BlockState state = player.level().getBlockState(mutablePos);
                        if (!state.isAir() && state.getDestroySpeed(player.level(), mutablePos) >= 0.0F) {
                           float dropChance = ViltrumiteCoreConfig.INSTANCE.dashBlockDropChance / 100.0F;
                           player.level().destroyBlock(mutablePos, player.level().random.nextFloat() < dropChance, player);
                        }
                     }
                  }
               }
            }
         }

         float half = 5.0F;
         if ((float)currentDashTicks <= half) {
            float t = (float)currentDashTicks / half;
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float tMinus1 = t - 1.0F;
            this.dashProgress = 1.0F + c3 * tMinus1 * tMinus1 * tMinus1 + c1 * tMinus1 * tMinus1;
         } else {
            float t = ((float)currentDashTicks - half) / half;
            float inverseT = 1.0F - t;
            this.dashProgress = inverseT * inverseT * inverseT;
         }

         if (currentDashTicks >= 10) {
            this.setInternalDashing(false);
            this.setInternalDashTicks(0);
            this.dashProgress = 0.0F;
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;tick()V"
      )}
   )
   private void forceNoClipBeforeMovement(CallbackInfo ci) {
      Player player = (Player)(Object)this;
      if (!this.isDashing()) {
         if (player instanceof ViltrumiteFlightPlayer flightPlayer) {
            float throttle = flightPlayer.getFlightThrottle();
            boolean isDrilling = throttle >= 0.6F
               && (flightPlayer.getFlightState() == FlightState.CRUISE || flightPlayer.getFlightState() == FlightState.SONIC);
            if (isDrilling && !player.isSpectator()) {
               boolean impendingCrash = false;
               if (!(player.getY() + player.getDeltaMovement().y < (double)player.level().getMinBuildHeight())
                  && !(player.getY() + player.getDeltaMovement().y > (double)(player.level().getMaxBuildHeight() + 32))) {
                  Vec3 movement = player.getDeltaMovement();
                  AABB nextBox = player.getBoundingBox().expandTowards(movement).inflate(0.1);
                  BlockPos minPos = BlockPos.containing(nextBox.minX, nextBox.minY, nextBox.minZ);
                  BlockPos maxPos = BlockPos.containing(nextBox.maxX, nextBox.maxY, nextBox.maxZ);
                  MutableBlockPos mutablePos = new MutableBlockPos();

                  for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
                     for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                        for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                           mutablePos.set(x, y, z);
                           BlockState state = player.level().getBlockState(mutablePos);
                           if (!state.isAir()) {
                              float hardness = state.getDestroySpeed(player.level(), mutablePos);
                              if (hardness < 0.0F || hardness >= 50.0F) {
                                 impendingCrash = true;
                                 break;
                              }
                           }
                        }

                        if (impendingCrash) {
                           break;
                        }
                     }

                     if (impendingCrash) {
                        break;
                     }
                  }
               } else {
                  impendingCrash = true;
               }

               if (!impendingCrash) {
                  player.noPhysics = true;
               }
            }
         }
      }
   }

   @Unique
   private void tickDrill(Player player) {
      if (!player.level().isClientSide()) {
         if (player instanceof ViltrumiteFlightPlayer flightPlayer) {
            float throttle = flightPlayer.getFlightThrottle();
            float maxFlightSpeed = flightPlayer.getMaxFlightSpeed();
            if (throttle >= 0.6F && (flightPlayer.getFlightState() == FlightState.CRUISE || flightPlayer.getFlightState() == FlightState.SONIC)) {
               ServerLevel serverWorld = (ServerLevel)player.level();
               Vec3 lookVec = player.getLookAngle();
               double length = 15.0 * (double)throttle;
               double radius = 3.0;
               double radiusSq = radius * radius;
               Vec3 centerPos = player.position().add(0.0, (double)player.getBbHeight() / 2.0, 0.0);
               Vec3 startPos = centerPos.subtract(lookVec.scale(4.0));
               Vec3 endPos = centerPos.add(lookVec.scale(length));
               Vec3 lineVec = endPos.subtract(startPos);
               double lineLenSq = lineVec.lengthSqr();
               AABB scanBox = new AABB(startPos, endPos).inflate(radius);
               BlockPos minPos = BlockPos.containing(scanBox.minX, scanBox.minY, scanBox.minZ);
               BlockPos maxPos = BlockPos.containing(scanBox.maxX, scanBox.maxY, scanBox.maxZ);
               MutableBlockPos mutablePos = new MutableBlockPos();
               boolean hitSomething = false;
               int blocksBroken = 0;
               int maxBlocksPerTick = 500;
               List<BlockPos> meltedThisTick = new ArrayList<>();

               for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
                  for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                     for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                        mutablePos.set(x, y, z);
                        Vec3 blockPos = new Vec3((double)x + 0.5, (double)y + 0.5, (double)z + 0.5);
                        Vec3 pToStart = blockPos.subtract(startPos);
                        double t = 0.0;
                        if (lineLenSq > 0.0) {
                           t = pToStart.dot(lineVec) / lineLenSq;
                           t = Mth.clamp(t, 0.0, 1.0);
                        }

                        Vec3 closestPoint = startPos.add(lineVec.scale(t));
                        double distSq = blockPos.distanceToSqr(closestPoint);
                        if (distSq <= radiusSq) {
                           BlockState state = serverWorld.getBlockState(mutablePos);
                           if (!state.isAir() && state.getFluidState().isEmpty()) {
                              float hardness = state.getDestroySpeed(serverWorld, mutablePos);
                              if (hardness >= 0.0F && hardness < 50.0F && blocksBroken < maxBlocksPerTick) {
                                 serverWorld.sendParticles(
                                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                                    (double)x + 0.5,
                                    (double)y + 0.5,
                                    (double)z + 0.5,
                                    2,
                                    0.2,
                                    0.2,
                                    0.2,
                                    0.1
                                 );
                                 serverWorld.destroyBlock(mutablePos, false, player);
                                 hitSomething = true;
                                 blocksBroken++;
                                 meltedThisTick.add(mutablePos.immutable());
                              }
                           }
                        }
                     }
                  }
               }

               if (!meltedThisTick.isEmpty()) {
                  CoreMessages.sendToTracking(new MeltedBlocksS2CPacket(meltedThisTick), player);
                  CoreMessages.sendToPlayer(new MeltedBlocksS2CPacket(meltedThisTick), (ServerPlayer)player);
               }

               if (hitSomething) {
                  if (this.getGrabbedTarget() != null) {
                     this.getGrabbedTarget().hurt(player.damageSources().generic(), 10.0F + throttle * 10.0F);
                  }

                  AABB explosionBox = scanBox.inflate(1.0);

                  for (Entity entity : serverWorld.getEntities(player, explosionBox)) {
                     if (entity instanceof LivingEntity) {
                        LivingEntity living = (LivingEntity)entity;
                        if (entity != this.getGrabbedTarget()) {
                           living.hurt(player.damageSources().playerAttack(player), 5.0F + throttle * maxFlightSpeed);
                           if (dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(living, dev.baranhan.viltrumitecore.hero.control.ControlKind.IMPULSE)) {
                              Vec3 pushDir = living.position().subtract(player.position()).normalize();
                              living.setDeltaMovement(living.getDeltaMovement().add(pushDir.x * 2.0, 0.5, pushDir.z * 2.0));
                              living.hasImpulse = true;
                           }
                        }
                     }
                  }

                  serverWorld.sendParticles(
                     ParticleTypes.CLOUD, player.getX(), player.getY() + (double)player.getBbHeight() / 2.0, player.getZ(), 4, 0.4, 0.4, 0.4, 0.1
                  );
                  serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0F, 0.5F);
                  this.lastImpactTick = player.tickCount;
               }
            }

            if (flightPlayer.getFlightState() != FlightState.NONE && !player.getAbilities().flying) {
               player.getAbilities().flying = true;
               player.onUpdateAbilities();
            }
         }
      }
   }

   @Unique
   private void tickGrab(Player player) {
      if (!player.isRemoved() && player.isAlive()) {
         LivingEntity currentTarget;
         currentTarget = this.getGrabbedTarget();
         if (dev.baranhan.viltrumitecore.hero.control.WorldPause.blocksMovement(player, currentTarget)) {
            return; // Keep the legacy owner, but suspend carry/acquire/terrain grind.
         }
         if (!player.level().isClientSide() && this.isTryingToGrab() && currentTarget == null) {
            Vec3 eyePos = player.getEyePosition();
            Vec3 lookDir = player.getLookAngle().normalize();
            Vec3 grabCenter = eyePos.add(lookDir.scale(1.5));
            AABB grabBox = new AABB(
               grabCenter.x - 1.5,
               grabCenter.y - 1.5,
               grabCenter.z - 1.5,
               grabCenter.x + 1.5,
               grabCenter.y + 1.5,
               grabCenter.z + 1.5
            );
            Iterator targetBox = player.level().getEntities(player, grabBox).iterator();

             while (targetBox.hasNext()) {
                Entity entity = (Entity)targetBox.next();
                if (entity instanceof LivingEntity target && target.isAlive()) {
                  if (dev.baranhan.viltrumitecore.hero.control.WorldPause.blocksMovement(player, target)) {
                     continue;
                  }
                  boolean isAlreadyGrabbed = false;

                  for (Player p : player.level().players()) {
                     if (p instanceof ViltrumiteCorePlayer coreP && coreP.getGrabbedTarget() == target) {
                        isAlreadyGrabbed = true;
                        break;
                     }
                  }

                   if (isAlreadyGrabbed) {
                      continue;
                   }

                   if (target instanceof ViltrumiteCorePlayer targetCore) {
                      // don't grab someone who is already holding a target
                      if (targetCore.getGrabbedTarget() != null) {
                         continue;
                      }

                      if (targetCore.isTryingToGrab()) {
                         targetCore.setTryingToGrab(false);
                      }
                   }

                   // Control precedence: anchored victims can't be grabbed, and a
                   // hero policy may refuse the grab outright (Lion's Heart).
                   if (player.level() instanceof ServerLevel serverLevel
                      && dev.baranhan.viltrumitecore.hero.control.ControlManager.get(serverLevel).isAnchored(target)) {
                      continue;
                   }

                   if (!dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsExternalControl(target, dev.baranhan.viltrumitecore.hero.control.ControlKind.VILTRUMITE_GRAB)) {
                      continue;
                   }

                  this.setGrabbedTarget(target);
                  this.setTryingToGrab(false);
                  player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0F, 0.5F);
                  if (target instanceof Mob mob) {
                     mob.setNoAi(true);
                  }

                  if (target instanceof ServerPlayer grabbedPlayer) {
                     CoreMessages.sendToPlayer(new PlayerGrabStateSyncS2CPacket(true, player.getUUID()), grabbedPlayer);
                     if (grabbedPlayer instanceof ViltrumiteFlightPlayer flightPlayer) {
                        flightPlayer.stopFlight();
                     }
                  }

                  target.addTag("ViltrumiteGrabbed");
                   break;
                }
             }
         }

         if (currentTarget != null) {
            if (!currentTarget.isAlive() || currentTarget.isRemoved()) {
               this.releaseTarget();
               return;
            }

            if (!player.level().isClientSide()) {
               if (currentTarget instanceof ViltrumiteFlightPlayer flightPlayer) {
                  flightPlayer.stopFlight();
               }

               Vec3 handPos = this.getServerHandPos();
               if (handPos == null) {
                  Vec3 eyePos = player.getEyePosition();
                  Vec3 lookDir = player.getLookAngle().normalize();
                  handPos = eyePos.add(lookDir.scale(1.5));
               }

               double neckOffset = (double)currentTarget.getBbHeight() * 0.85;
               currentTarget.setPos(handPos.x, handPos.y - neckOffset, handPos.z);
               currentTarget.setDeltaMovement(player.getDeltaMovement());
               currentTarget.hasImpulse = true;
               currentTarget.fallDistance = 0.0F;
               if (currentTarget instanceof ServerPlayer serverTarget) {
                  CoreMessages.sendToPlayer(new GrabbedPosSyncS2CPacket(handPos, player.getDeltaMovement()), serverTarget);
                  serverTarget.getAbilities().mayfly = true;
                  ServerLevel serverLevel = serverTarget.serverLevel();
                  ChunkPos targetChunkPos = serverTarget.chunkPosition();
                  serverLevel.getChunkSource().addRegionTicket(TicketType.UNKNOWN, targetChunkPos, 2, targetChunkPos);
                  serverLevel.getChunkSource().move(serverTarget);
               }

               float faceYaw = player.getYRot() + 180.0F;
               currentTarget.setYRot(faceYaw);
               currentTarget.setYHeadRot(faceYaw);
               currentTarget.yBodyRot = faceYaw;
               AABB targetBox = currentTarget.getBoundingBox().inflate(0.1);
               BlockPos minPos = BlockPos.containing(targetBox.minX, targetBox.minY, targetBox.minZ);
               BlockPos maxPos = BlockPos.containing(targetBox.maxX, targetBox.maxY, targetBox.maxZ);
               MutableBlockPos mutablePos = new MutableBlockPos();
               int brokenBlockCount = 0;
               ServerLevel serverWorld = (ServerLevel)player.level();
               double playerFootY = player.getY();
               double lookY = player.getLookAngle().y;
               boolean isOnGround = player.onGround();

               for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
                  for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                     for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                        if (!isOnGround || !((double)y <= playerFootY + 0.2) || !(lookY > -0.4)) {
                           mutablePos.set(x, y, z);
                           BlockState state = serverWorld.getBlockState(mutablePos);
                           if (!state.isAir() && state.getFluidState().isEmpty()) {
                              float hardness = state.getDestroySpeed(serverWorld, mutablePos);
                              if (hardness > 0.0F && hardness <= 50.0F) {
                                 serverWorld.destroyBlock(mutablePos, true, player);
                                 brokenBlockCount++;
                              }
                           }
                        }
                     }
                  }
               }

               if (brokenBlockCount > 0) {
                  float grindDamage = (float)brokenBlockCount * 1.0F;
                  // Same fly_into_wall type, but the grabber is the cause so a
                  // hero victim can arm its Counter on the grind.
                  currentTarget.hurt(new net.minecraft.world.damagesource.DamageSource(player.damageSources().flyIntoWall().typeHolder(), null, player), grindDamage);
                  if (!currentTarget.isAlive()) {
                     this.releaseTarget();
                  }
               }
            }
         }
      } else {
         this.releaseTarget();
      }
   }

   @Unique
   private void tickBlock(Player player) {
      if (!player.level().isClientSide()) {
         int currentCooldown = this.getBlockCooldown();
         if (currentCooldown > 0) {
            this.setBlockCooldown(currentCooldown - 1);
         }

         if (this.isBlocking()) {
            if (this.getBlockCooldown() > 0) {
               this.setBlocking(false);
            } else if (this.internalBlockTicks > 0) {
               this.internalBlockTicks--;
            } else {
               this.setBlocking(false);
            }
         }
      }

      if (this.isBlocking() && player instanceof ViltrumiteFlightPlayer flightPlayer) {
         if (flightPlayer.getFlightState() == FlightState.NONE) {
            return;
         }

         float currentThrottle = flightPlayer.getFlightThrottle();
         if (currentThrottle > 0.0F) {
            float brakeForce = 0.15F;
            flightPlayer.setFlightThrottle(Math.max(0.0F, currentThrottle - brakeForce));
         } else {
            flightPlayer.setFlightState(FlightState.HOVER);
            flightPlayer.setSpeedLocked(false);
            flightPlayer.setFlightThrottle(0.0F);
            flightPlayer.setFlightAccelerating(false);
         }
      }
   }

   @Unique
   private void tickSuperSpeed(Player player) {
      AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speedAttribute != null) {
         if (this.isSuperSpeed()) {
            if (!speedAttribute.hasModifier(SUPER_SPEED_MODIFIER)) {
               speedAttribute.addTransientModifier(SUPER_SPEED_MODIFIER);
            }

            if (player.maxUpStep() < 1.0F) {
               player.setMaxUpStep(1.5F);
            }

            player.fallDistance = 0.0F;
            Vec3 vel = player.getDeltaMovement();
            if (vel.horizontalDistanceSqr() > 0.01 && !player.onGround() && vel.y <= 0.0 && !player.getAbilities().flying) {
               boolean groundNear = false;
               BlockPos playerPos = player.blockPosition();

               for (int i = 1; i <= 3; i++) {
                  BlockState state = player.level().getBlockState(playerPos.below(i));
                  if (!state.isAir() && state.getFluidState().isEmpty()) {
                     groundNear = true;
                     break;
                  }
               }

               if (groundNear && vel.y > -0.8) {
                  player.setDeltaMovement(vel.add(0.0, -0.2, 0.0));
                  player.hasImpulse = true;
               }
            }

            this.processSuperSpeedHit(player);
         } else {
            if (speedAttribute.hasModifier(SUPER_SPEED_MODIFIER)) {
               speedAttribute.removeModifier(SUPER_SPEED_MODIFIER.getId());
            }

            if (player.maxUpStep() > 0.6F) {
               player.setMaxUpStep(0.6F);
            }
         }
      }
   }

   @Unique
   private void processSuperSpeedHit(Player player) {
      if (!player.level().isClientSide() && player.isSprinting()) {
         Vec3 lookDir = player.getLookAngle();
         Vec3 forwardDir = new Vec3(lookDir.x, 0.0, lookDir.z).normalize();
         AABB hitBox = player.getBoundingBox().expandTowards(forwardDir.scale(1.5)).inflate(0.5);

         for (Entity entity : player.level().getEntities(player, hitBox)) {
            if (entity instanceof LivingEntity) {
               LivingEntity target = (LivingEntity)entity;
               if (target.isAlive() && target != this.getGrabbedTarget()) {
                  float speedDamage = 5.0F;
                  boolean hit = target.hurt(player.damageSources().playerAttack(player), speedDamage);
                  if (hit) {
                     Vec3 currentMotion = target.getDeltaMovement();
                     target.setDeltaMovement(currentMotion.x + forwardDir.x * 1.5, 0.5, currentMotion.z + forwardDir.z * 1.5);
                     target.hasImpulse = true;
                     player.level()
                        .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 1.2F);
                  }
               }
            }
         }
      }
   }

   @Unique
   private void tickChop(Player player) {
      int currentTicks = this.getChopTicks();
      if (currentTicks <= 0) {
         if (!player.level().isClientSide()) {
            ChopImpactManager.clearChopMemory(player);
         }
      } else {
         if (currentTicks == 20 && !player.level().isClientSide()) {
            ChopImpactManager.clearChopMemory(player);
         }

         player.yBodyRot = player.getYRot();
         player.setYHeadRot(player.getYRot());
         if (player instanceof ViltrumiteFlightPlayer flightPlayer && flightPlayer.getFlightState() != FlightState.NONE) {
            float currentThrottle = flightPlayer.getFlightThrottle();
            if (currentThrottle > 0.0F) {
               float brakeForce = 0.4F;
               flightPlayer.setFlightThrottle(Math.max(0.0F, currentThrottle - brakeForce));
            } else {
               flightPlayer.setFlightState(FlightState.HOVER);
               flightPlayer.setSpeedLocked(false);
               flightPlayer.setFlightThrottle(0.0F);
               flightPlayer.setFlightAccelerating(false);
            }
         }

         if (currentTicks <= 15 && currentTicks >= 10 && !player.level().isClientSide()) {
            ChopImpactManager.tickChopSweep((ServerPlayer)player);
         }

         if (currentTicks == 15) {
            dev.baranhan.viltrumitecore.hero.ChopStyle style = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).chopStyle(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, style.sweepVolume(),
               style.sweepPitch());
         }

         this.setChopTicks(currentTicks - 1);
      }
   }

   @Unique
   private void tickStrength(Player player) {
      AttributeInstance damageAttribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
      if (damageAttribute != null) {
         float damage = 0.0F;
         if (player instanceof ViltrumiteStatHolder statHolder) {
            damage = statHolder.getBaseDamage();
         }

         AttributeModifier attributeModifier = new AttributeModifier(
            UUID.fromString("e7208d13-6453-4cae-908c-9c3f508a6b12"), "Viltrumite Base Strength", (double)damage, Operation.ADDITION
         );
         AttributeModifier current = damageAttribute.getModifier(attributeModifier.getId());
         // Re-add when the strength changed (hero chassis, config screen), not only when missing.
         if (current != null && (!this.isViltrumite() || current.getAmount() != (double)damage)) {
            damageAttribute.removeModifier(attributeModifier.getId());
            current = null;
         }

         if (this.isViltrumite() && current == null) {
            damageAttribute.addTransientModifier(attributeModifier);
         }
      }
   }

   @Unique
   private void tickThunderclap(Player player) {
      int currentTicks = this.getThunderclapTicks();
      if (currentTicks > 0) {
         player.yBodyRot = player.getYRot();
         player.setYHeadRot(player.getYRot());
         if (currentTicks == 11) {
            if (!player.level().isClientSide()) {
               ThunderClapManager.startThunderclap((ServerPlayer)player);
            }

            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.5F);
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.5F);
         }

         this.setThunderclapTicks(currentTicks - 1);
      }
   }

   @Unique
   private void tickBarrage(Player player) {
      if (!player.level().isClientSide()) {
         int currentCooldown = this.getBarrageCooldown();
         if (currentCooldown > 0) {
            this.setBarrageCooldown(currentCooldown - 1);
         }
      }

      boolean holding = this.isBarraging();
      int ticks = this.getBarrageTicks();
      if (holding && !player.level().isClientSide() && ticks >= 80) {
         this.setBarraging(false);
         holding = false;
      }

      if (holding) {
         if (this.getChopTicks() <= 0 && this.getPunchTicks() <= 0 && this.getThunderclapTicks() <= 0 && !this.isBlocking() && !this.isDashing()) {
            if (ticks < 0) {
               ticks = 0;
            }

            this.setBarrageTicks(++ticks);
            this.processBarrageTick(player, ticks);
         } else if (ticks > 0) {
            this.setBarrageTicks(-9);
         }
      } else if (ticks > 0) {
         if (ticks >= 10 && (ticks - 5) % 3 == 2) {
            this.setBarrageTicks(-9);
         } else {
            this.setBarrageTicks(++ticks);
            this.processBarrageTick(player, ticks);
         }
      } else if (ticks < 0) {
         this.setBarrageTicks(ticks + 1);
      }
   }

   @Unique
   private void processBarrageTick(Player player, int ticks) {
      player.yBodyRot = player.getYRot();
      player.setYHeadRot(player.getYRot());
      if (player instanceof ViltrumiteFlightPlayer flightPlayer && flightPlayer.getFlightState() != FlightState.NONE) {
         float throttle = flightPlayer.getFlightThrottle();
         if (throttle > 0.0F) {
            flightPlayer.setFlightThrottle(Math.max(0.0F, throttle - 0.2F));
         }
      }

      if (ticks >= 5) {
         int punchTime = ticks - 5;
         boolean isLeft = (punchTime - 1) / 3 % 2 != 0;
         this.setLeftBarrageArm(isLeft);
         if (punchTime % 3 == 2) {
            if (!player.level().isClientSide()) {
               Vec3 eyePos = player.getEyePosition();
               Vec3 lookDir = player.getLookAngle();
               Vec3 reach = eyePos.add(lookDir.scale(2.5));
               AABB hitBox = new AABB(
                  reach.x - 1.5, reach.y - 1.5, reach.z - 1.5, reach.x + 1.5, reach.y + 1.5, reach.z + 1.5
               );

               for (Entity entity : player.level().getEntities(player, hitBox)) {
                  if (entity instanceof LivingEntity) {
                     LivingEntity target = (LivingEntity)entity;
                     if (target.isAlive() && target != this.getGrabbedTarget()) {
                        boolean var10000;
                        label39: {
                           if (player instanceof ViltrumiteStatHolder statHolder
                              && target.hurt(player.damageSources().playerAttack(player), statHolder.getBaseDamage())) {
                              var10000 = true;
                              break label39;
                           }

                           var10000 = false;
                        }

                        boolean hit = var10000;
                        if (hit) {
                           target.invulnerableTime = 0;
                           Vec3 pushDir = target.position().subtract(player.position()).normalize();
                           target.setDeltaMovement(target.getDeltaMovement().add(pushDir.x * 0.2, pushDir.y * 0.1, pushDir.z * 0.2));
                           target.hasImpulse = true;
                           Vec3 toAttacker = player.position().subtract(target.position()).normalize();
                           Vec3 hitPos = new Vec3(
                              target.getX() + toAttacker.x * ((double)target.getBbWidth() / 2.0),
                              target.getY() + (double)target.getBbHeight() * 0.6,
                              target.getZ() + toAttacker.z * ((double)target.getBbWidth() / 2.0)
                           );
                           CoreMessages.sendToTracking(new BarrageHitS2CPacket(hitPos), player);
                           CoreMessages.sendToPlayer(new BarrageHitS2CPacket(hitPos), (ServerPlayer)player);
                        }
                     }
                  }
               }
            }

            player.level()
               .playSound(
                  null,
                  player.getX(),
                  player.getY(),
                  player.getZ(),
                  SoundEvents.PLAYER_ATTACK_SWEEP,
                  SoundSource.PLAYERS,
                  1.0F,
                  1.5F + player.level().random.nextFloat() * 0.2F
               );
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.2F, 2.0F);
         }
      } else {
         this.setLeftBarrageArm(false);
      }
   }

   @Unique
   public void checkSpaceTransition(ServerPlayer player) {
      double limit = ViltrumiteCoreConfig.INSTANCE.spaceLimitY;
      if (player.getY() > limit && player.yo <= limit && player.getServer() != null) {
         CommandNode<CommandSourceStack> adAstraCommandNode = player.getServer().getCommands().getDispatcher().getRoot().getChild("adastra");
         if (adAstraCommandNode != null) {
            CommandSourceStack source = player.createCommandSourceStack().withPermission(2);
            player.getServer().getCommands().performPrefixedCommand(source, "adastra planets");
         }
      }
   }

   @Inject(
      method = {"addAdditionalSaveData"},
      at = {@At("TAIL")}
   )
   private void onWriteViltrumiteNbt(CompoundTag nbt, CallbackInfo ci) {
      nbt.putBoolean("IsViltrumite", this.isViltrumite());
      nbt.putBoolean("HasChosenRace", this.hasChosenRace());
   }

   @Inject(
      method = {"readAdditionalSaveData"},
      at = {@At("TAIL")}
   )
   private void onReadViltrumiteNbt(CompoundTag nbt, CallbackInfo ci) {
      if (nbt.contains("IsViltrumite")) {
         this.viltrumitecore$setLegacyIdentity(nbt.getBoolean("IsViltrumite"));
      }

      if (nbt.contains("HasChosenRace")) {
         this.setChosenRace(nbt.getBoolean("HasChosenRace"));
      }
   }
}

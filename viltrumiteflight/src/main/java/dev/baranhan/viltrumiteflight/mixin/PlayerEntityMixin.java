package dev.baranhan.viltrumiteflight.mixin;

import dev.baranhan.viltrumiteflight.config.ViltrumiteConfig;
import dev.baranhan.viltrumiteflight.config.ViltrumiteConfigClient;
import dev.baranhan.viltrumiteflight.registry.ModSounds;
import dev.baranhan.viltrumiteflight.util.FlightPermissions;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Player.class},
   priority = 300
)
public abstract class PlayerEntityMixin extends LivingEntity implements ViltrumiteFlightPlayer {
   @Unique
   private static final EntityDataAccessor<Byte> FLIGHT_STATE = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
   @Unique
   private static final EntityDataAccessor<Float> FLIGHT_THROTTLE = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Boolean> FLIGHT_ACCELERATING = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Float> HOVER_FORWARD = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Float> HOVER_SIDEWAYS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Boolean> SPEED_LOCKED = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
   @Unique
   private static final EntityDataAccessor<Integer> FLIGHT_TICKS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Integer> TAKEOFF_TICKS = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<Float> MAX_FLIGHT_SPEED = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Float> THROTTLE_SPEED = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private float prevFlightThrottle = 0.0F;
   @Unique
   private float clientLocalThrottle = 0.0F;
   @Unique
   private float prevClientLocalThrottle = 0.0F;
   @Unique
   private boolean isClientLocalPlayer = false;
   /** Local-client Y at tick start; glide must never end a tick above {@code startY - glideSink}. */
   @Unique
   private double viltrumiteflight$tickStartY = Double.NaN;

   protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level level) {
      super(entityType, level);
   }

   @Override
   public FlightState getFlightState() {
      return FlightState.values()[this.getEntityData().get(FLIGHT_STATE)];
   }

   @Override
   public void setFlightState(FlightState state) {
      Player player = (Player)(Object)this;
      if (state != FlightState.NONE && !FlightPermissions.allowsModFlight(player)) {
         FlightPermissions.resetModFlight(player);
         return;
      }

      FlightState currentState = this.getFlightState();
      if (currentState != state && (state == FlightState.NONE || currentState == FlightState.NONE)) {
         this.setFlightThrottle(0.0F);
         this.prevFlightThrottle = 0.0F;
         this.setFlightAccelerating(false);
         this.clientLocalThrottle = 0.0F;
         this.prevClientLocalThrottle = 0.0F;
         this.setSpeedLocked(false);
      }

      this.getEntityData().set(FLIGHT_STATE, (byte)state.ordinal());
   }

   @Override
   public float getFlightThrottle() {
      return (Float)(Object)this.getEntityData().get(FLIGHT_THROTTLE);
   }

   @Override
   public void setFlightThrottle(float throttle) {
      this.getEntityData().set(FLIGHT_THROTTLE, throttle);
   }

   @Override
   public boolean isFlightAccelerating() {
      return (Boolean)(Object)this.getEntityData().get(FLIGHT_ACCELERATING);
   }

   @Override
   public void setFlightAccelerating(boolean accelerating) {
      this.getEntityData().set(FLIGHT_ACCELERATING, accelerating);
   }

   @Override
   public float getLerpedFlightThrottle(float tickDelta) {
      return this.level().isClientSide() && this.isClientLocalPlayer()
         ? Mth.lerp(tickDelta, this.prevClientLocalThrottle, this.clientLocalThrottle)
         : Mth.lerp(tickDelta, this.prevFlightThrottle, this.getFlightThrottle());
   }

   @Override
   public float getHoverForward() {
      return (Float)(Object)this.getEntityData().get(HOVER_FORWARD);
   }

   @Override
   public void setHoverForward(float forward) {
      this.getEntityData().set(HOVER_FORWARD, forward);
   }

   @Override
   public float getHoverSideways() {
      return (Float)(Object)this.getEntityData().get(HOVER_SIDEWAYS);
   }

   @Override
   public void setHoverSideways(float sideways) {
      this.getEntityData().set(HOVER_SIDEWAYS, sideways);
   }

   @Override
   public boolean isSpeedLocked() {
      return (Boolean)(Object)this.getEntityData().get(SPEED_LOCKED);
   }

   @Override
   public void setSpeedLocked(boolean locked) {
      this.getEntityData().set(SPEED_LOCKED, locked);
   }

   @Override
   public int getFlightTicks() {
      return (Integer)(Object)this.getEntityData().get(FLIGHT_TICKS);
   }

   @Override
   public void setFlightTicks(int ticks) {
      this.getEntityData().set(FLIGHT_TICKS, ticks);
   }

   @Override
   public int getTakeoffTicks() {
      return (Integer)(Object)this.getEntityData().get(TAKEOFF_TICKS);
   }

   @Override
   public void setTakeoffTicks(int ticks) {
      this.getEntityData().set(TAKEOFF_TICKS, ticks);
   }

   @Override
   public float getMaxFlightSpeed() {
      return (Float)(Object)this.getEntityData().get(MAX_FLIGHT_SPEED);
   }

   @Override
   public void setMaxFlightSpeed(float speed) {
      this.getEntityData().set(MAX_FLIGHT_SPEED, speed);
   }

   @Override
   public float getThrottleSpeed() {
      return (Float)(Object)this.getEntityData().get(THROTTLE_SPEED);
   }

   @Override
   public void setThrottleSpeed(float speed) {
      this.getEntityData().set(THROTTLE_SPEED, speed);
   }

   @Override
   public boolean isClientLocalPlayer() {
      return this.isClientLocalPlayer;
   }

   @Override
   public void setClientLocalPlayer(boolean isLocal) {
      this.isClientLocalPlayer = isLocal;
   }

   @Inject(
      method = {"defineSynchedData"},
      at = {@At("TAIL")}
   )
   protected void onDefineSynchedData(CallbackInfo ci) {
      this.getEntityData().define(FLIGHT_STATE, (byte)FlightState.NONE.ordinal());
      this.getEntityData().define(FLIGHT_THROTTLE, 0.0F);
      this.getEntityData().define(FLIGHT_ACCELERATING, false);
      this.getEntityData().define(HOVER_FORWARD, 0.0F);
      this.getEntityData().define(HOVER_SIDEWAYS, 0.0F);
      this.getEntityData().define(SPEED_LOCKED, false);
      this.getEntityData().define(FLIGHT_TICKS, 0);
      this.getEntityData().define(TAKEOFF_TICKS, 0);
      this.getEntityData().define(MAX_FLIGHT_SPEED, ViltrumiteConfig.INSTANCE.maxFlightSpeed);
      this.getEntityData().define(THROTTLE_SPEED, ViltrumiteConfig.INSTANCE.throttleSpeed);
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void onTick(CallbackInfo ci) {
      if ((!this.level().isClientSide() || this.isClientLocalPlayer()) && !FlightPermissions.allowsModFlight((Player)(Object)this)) {
         FlightPermissions.resetModFlight((Player)(Object)this);
         return;
      }

      if (this.getFlightState() == FlightState.NONE) {
         this.stopFlight();
      }

      if (this.getTakeoffTicks() > 0) {
         this.setTakeoffTicks(this.getTakeoffTicks() - 1);
         Vec3 currentMovement = this.getDeltaMovement();
         this.setDeltaMovement(currentMovement.x, 2.8, currentMovement.z);
         this.hasImpulse = true;
      }

      FlightState currentState = this.getFlightState();
      if (currentState != FlightState.NONE) {
         this.setSprinting(false);
         float throttleBeforeTick = this.getFlightThrottle();
         dev.baranhan.viltrumiteflight.util.FlightProfile profile = dev.baranhan.viltrumiteflight.util.FlightProfiles.of((Player)(Object)this);
         if (profile != null) {
            this.viltrumiteflight$profileThrottle(profile, currentState);
         } else if (!this.isSpeedLocked()) {
            float throttleSpeed = this.getThrottleSpeed();
            if (this.isFlightAccelerating()) {
               this.setFlightThrottle(Math.min(1.0F, this.getFlightThrottle() + throttleSpeed));
            } else {
               this.setFlightThrottle(Math.max(0.0F, this.getFlightThrottle() - throttleSpeed * 2.0F));
            }

            if (this.level().isClientSide() && this.isClientLocalPlayer()) {
               if (this.isFlightAccelerating()) {
                  this.clientLocalThrottle = Math.min(1.0F, this.clientLocalThrottle + throttleSpeed);
               } else {
                  this.clientLocalThrottle = Math.max(0.0F, this.clientLocalThrottle - throttleSpeed * 2.0F);
               }
            }

            if (this.level().isClientSide() && this.isClientLocalPlayer()) {
               if (this.getFlightThrottle() != 0.0F
                  && (!this.onGround() && !this.horizontalCollision || currentState != FlightState.CRUISE && currentState != FlightState.SONIC)) {
                  if (Math.abs(this.clientLocalThrottle - this.getFlightThrottle()) > 0.2F) {
                     this.clientLocalThrottle = this.getFlightThrottle();
                  }
               } else {
                  this.clientLocalThrottle = 0.0F;
                  this.prevClientLocalThrottle = 0.0F;
               }
            }
         }

         if (this.getFlightThrottle() > 0.5F) {
            this.setFlightTicks(Math.min(400, this.getFlightTicks() + 1));
         } else {
            this.setFlightTicks(Math.max(0, this.getFlightTicks() - 2));
         }

         if (this.level().isClientSide() && throttleBeforeTick < 0.6F && this.getFlightThrottle() >= 0.6F && ViltrumiteConfigClient.INSTANCE.enableSonicBoomSound) {
            this.level()
               .playLocalSound(
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  (SoundEvent)ModSounds.SONIC_BOOM.get(),
                  SoundSource.PLAYERS,
                  ViltrumiteConfigClient.INSTANCE.sonicBoomVolume,
                  1.0F,
                  false
               );
         }

         if (profile != null) {
            this.viltrumiteflight$profileMotion(profile, currentState);
         } else if (currentState == FlightState.CRUISE || currentState == FlightState.SONIC) {
            this.setDeltaMovement(dev.baranhan.viltrumiteflight.util.FlightMotion.legacyVelocity(this.getLookAngle(), this.getFlightThrottle(),
               this.getMaxFlightSpeed() * dev.baranhan.viltrumiteflight.util.FlightProfiles.speedScale((Player)(Object)this)));
         }

         if (!this.level().isClientSide()) {
            FlightState newState = FlightState.HOVER;
            if (this.getFlightThrottle() >= 0.8F) {
               newState = FlightState.SONIC;
            } else if (this.getFlightThrottle() > 0.0F) {
               newState = FlightState.CRUISE;
            }

            if (currentState != newState) {
               this.setFlightState(newState);
            }

            if ((this.onGround() || this.horizontalCollision) && (currentState == FlightState.CRUISE || currentState == FlightState.SONIC)) {
               this.handleFlightCollision();
            }

            if (this.onGround() && currentState == FlightState.HOVER) {
               this.stopFlight();
            }

            if ((Object)this instanceof ServerPlayer serverPlayer && serverPlayer.isPassenger()) {
               this.stopFlight();
            }
         }
      } else if (this.getFlightTicks() > 0) {
         this.setFlightTicks(Math.max(0, this.getFlightTicks() - 2));
      }
   }

   /**
    * Profile throttle: same thrust rules on both sides (server for state, SONIC
    * and drain; the local client for its own prediction). The Shift lock caps
    * below sonic and glide beats the lock (FlightMotion.throttle).
    */
   @Unique
   private void viltrumiteflight$profileThrottle(dev.baranhan.viltrumiteflight.util.FlightProfile profile, FlightState currentState) {
      boolean ctrl = this.isFlightAccelerating();
      boolean locked = this.isSpeedLocked();
      this.setFlightThrottle(dev.baranhan.viltrumiteflight.util.FlightMotion.throttle(this.getFlightThrottle(), ctrl, locked, profile));
      if (this.level().isClientSide() && this.isClientLocalPlayer()) {
         this.clientLocalThrottle = dev.baranhan.viltrumiteflight.util.FlightMotion.throttle(this.clientLocalThrottle, ctrl, locked, profile);
         if (this.getFlightThrottle() != 0.0F
            && (!this.onGround() && !this.horizontalCollision || currentState != FlightState.CRUISE && currentState != FlightState.SONIC)) {
            if (Math.abs(this.clientLocalThrottle - this.getFlightThrottle()) > 0.2F) {
               this.clientLocalThrottle = this.getFlightThrottle();
            }
         } else {
            this.clientLocalThrottle = 0.0F;
            this.prevClientLocalThrottle = 0.0F;
         }
      }
   }

   /**
    * Profile motion. Movement of the local player is computed on its client
    * (the server does not own a player's velocity), so inertia, turn radius,
    * hover damping and glide run on the local client; the server keeps the
    * same CRUISE/SONIC velocity for its own reads (ram, fly-by).
    */
   @Unique
   private void viltrumiteflight$profileMotion(dev.baranhan.viltrumiteflight.util.FlightProfile profile, FlightState currentState) {
      boolean local = this.level().isClientSide() && this.isClientLocalPlayer();
      if (currentState == FlightState.CRUISE || currentState == FlightState.SONIC) {
         if (!this.level().isClientSide() || local) {
            this.setDeltaMovement(dev.baranhan.viltrumiteflight.util.FlightMotion.velocity(
               this.getDeltaMovement(), this.getLookAngle(), this.getFlightThrottle(), this.getMaxFlightSpeed(), profile));
         }
      } else if (currentState == FlightState.HOVER && local) {
         Vec3 input = new Vec3(this.getHoverSideways(), this.jumping ? 1.0 : (this.isShiftKeyDown() ? -1.0 : 0.0), this.getHoverForward());
         this.setDeltaMovement(dev.baranhan.viltrumiteflight.util.FlightMotion.hover(this.getDeltaMovement(), input, profile));
         if (profile.glide()) {
            this.viltrumiteflight$clampGlide(profile);
         }
      }
   }

   /**
    * Vanilla creative-flight input (Space) still adds lift inside the tick, so
    * a glide-only suit could climb by holding jump. Undo any rise past the
    * sink budget for this tick; {@code move} keeps collisions intact.
    */
   @Unique
   private void viltrumiteflight$clampGlide(dev.baranhan.viltrumiteflight.util.FlightProfile profile) {
      double startY = this.viltrumiteflight$tickStartY;
      if (Double.isNaN(startY) || this.onGround()) {
         return;
      }

      double floor = startY - profile.glideSink();
      if (this.getY() > floor) {
         this.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.0, floor - this.getY(), 0.0));
      }
   }

   @Override
   public void stopFlight() {
      this.setFlightState(FlightState.NONE);
      this.setSpeedLocked(false);
      this.setFlightThrottle(0.0F);
      this.prevFlightThrottle = 0.0F;
      this.setFlightAccelerating(false);
      if (!this.level().isClientSide() && (Object)this instanceof ServerPlayer serverPlayer
         && !serverPlayer.isCreative() && !serverPlayer.isSpectator() && !serverPlayer.getTags().contains("ViltrumiteGrabbed")
         && serverPlayer.getAbilities().flying) {
         serverPlayer.getAbilities().flying = false;
         serverPlayer.onUpdateAbilities();
      }

      this.clientLocalThrottle = 0.0F;
      this.prevClientLocalThrottle = 0.0F;
   }

   @Override
   public void resetModFlight() {
      this.setFlightState(FlightState.NONE);
      this.setFlightThrottle(0.0F);
      this.setFlightAccelerating(false);
      this.setHoverForward(0.0F);
      this.setHoverSideways(0.0F);
      this.setSpeedLocked(false);
      this.setFlightTicks(0);
      this.setTakeoffTicks(0);
      this.prevFlightThrottle = 0.0F;
      this.clientLocalThrottle = 0.0F;
      this.prevClientLocalThrottle = 0.0F;
   }

   @Unique
   public void switchToHover() {
      if (!FlightPermissions.allowsModFlight((Player)(Object)this)) {
         FlightPermissions.resetModFlight((Player)(Object)this);
         return;
      }

      this.setFlightState(FlightState.HOVER);
      this.setSpeedLocked(false);
      this.setFlightThrottle(0.0F);
      this.prevFlightThrottle = 0.0F;
      this.setFlightAccelerating(false);
      this.clientLocalThrottle = 0.0F;
      this.prevClientLocalThrottle = 0.0F;
   }

   @Override
   public void handleFlightCollision() {
      if (!FlightPermissions.allowsModFlight((Player)(Object)this)) {
         FlightPermissions.resetModFlight((Player)(Object)this);
         return;
      }

      this.switchToHover();
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void onTickHead(CallbackInfo ci) {
      if ((!this.level().isClientSide() || this.isClientLocalPlayer()) && !FlightPermissions.allowsModFlight((Player)(Object)this)) {
         FlightPermissions.resetModFlight((Player)(Object)this);
      }

      if (this.level().isClientSide()) {
         this.prevFlightThrottle = this.getFlightThrottle();
         this.prevClientLocalThrottle = this.clientLocalThrottle;
         this.viltrumiteflight$tickStartY = this.isClientLocalPlayer() ? this.getY() : Double.NaN;
      }
   }

   @Inject(
      method = {"addAdditionalSaveData"},
      at = {@At("TAIL")}
   )
   private void writeCustomData(CompoundTag nbt, CallbackInfo ci) {
      nbt.putString("OmnimanFlightState", this.getFlightState().name());
      nbt.putFloat("OmnimanFlightThrottle", this.getFlightThrottle());
   }

   @Inject(
      method = {"readAdditionalSaveData"},
      at = {@At("TAIL")}
   )
   private void readCustomData(CompoundTag nbt, CallbackInfo ci) {
      if (nbt.contains("OmnimanFlightState")) {
         try {
            this.setFlightState(FlightState.valueOf(nbt.getString("OmnimanFlightState")));
         } catch (IllegalArgumentException var4) {
            this.setFlightState(FlightState.NONE);
         }
      }

      if (nbt.contains("OmnimanFlightThrottle")) {
         this.setFlightThrottle(nbt.getFloat("OmnimanFlightThrottle"));
      }

      if (!FlightPermissions.allowsModFlight((Player)(Object)this)) {
         FlightPermissions.resetModFlight((Player)(Object)this);
      }
   }
}

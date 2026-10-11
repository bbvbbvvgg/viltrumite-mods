package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.IronManVeronica;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Veronica capsule (spec §12.1–§12.2): falls like a meteor onto the drop
 * point, explodes ~1 TNT (mobGriefing respected) and then stays for good
 * (user decision 2026-10-10): saved with the chunk, through owner death,
 * logout and dimension change. It flies away only when its online owner is
 * no longer Iron Man or called a newer pod.
 */
public class VeronicaPodEntity extends Entity {
   public static final int LEAVE_TICKS = 60;
   public static final double FALL_HEIGHT = 120.0;
   public static final double FALL_SPEED = 2.6;
   public static final double MENU_RANGE = 64.0;
   public static final float IMPACT_POWER = 4.0F;
   private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(VeronicaPodEntity.class, EntityDataSerializers.OPTIONAL_UUID);
   private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(VeronicaPodEntity.class, EntityDataSerializers.BYTE);
   private int phaseTicks;
   /** Client: tickCount when the synced phase last changed (open / leave animations). */
   private int clientPhaseStart;

   public enum Phase {
      FALLING,
      LANDED,
      LEAVING
   }

   public VeronicaPodEntity(EntityType<? extends VeronicaPodEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   /** Spawn the capsule high above the drop point; it falls straight down. */
   public static VeronicaPodEntity drop(ServerPlayer owner, Vec3 ground) {
      VeronicaPodEntity pod = new VeronicaPodEntity(ViltrumiteEntities.VERONICA_POD.get(), owner.level());
      pod.entityData.set(OWNER, Optional.of(owner.getUUID()));
      pod.setPos(ground.x, Math.min(ground.y + FALL_HEIGHT, owner.level().getMaxBuildHeight() + 32.0), ground.z);
      pod.setYRot(owner.getRandom().nextFloat() * 360.0F);
      pod.setDeltaMovement(0.0, -FALL_SPEED, 0.0);
      owner.level().addFreshEntity(pod);
      return pod;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(OWNER, Optional.empty());
      this.entityData.define(PHASE, (byte)Phase.FALLING.ordinal());
   }

   public Optional<UUID> ownerId() {
      return this.entityData.get(OWNER);
   }

   public boolean ownedBy(@Nullable Entity entity) {
      return entity != null && this.ownerId().map(entity.getUUID()::equals).orElse(false);
   }

   public Phase phase() {
      byte id = this.entityData.get(PHASE);
      return id >= 0 && id < Phase.values().length ? Phase.values()[id] : Phase.FALLING;
   }

   /** Client: ticks since the synced phase changed (animations). */
   public int clientPhaseAge() {
      return this.tickCount - this.clientPhaseStart;
   }

   /** Server: ticks in the current phase. */
   public int phaseTicks() {
      return this.phaseTicks;
   }

   public boolean landed() {
      return this.phase() == Phase.LANDED;
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (PHASE.equals(key)) {
         this.clientPhaseStart = this.tickCount;
      }
   }

   private void setPhase(Phase phase) {
      this.entityData.set(PHASE, (byte)phase.ordinal());
      this.phaseTicks = 0;
   }

   /** Fall speed while settling into a crater (server only). */
   private double settleSpeed;

   @Override
   public void tick() {
      super.tick();
      Vec3 motion = this.getDeltaMovement();
      if (this.level().isClientSide()) {
         this.setPos(this.position().add(motion));
         return;
      }

      this.phaseTicks++;
      Phase phase = this.phase();
      if (phase != Phase.LEAVING && this.retired()) {
         this.leave();
         phase = Phase.LEAVING;
      }

      switch (phase) {
         case FALLING -> this.fall(motion);
         case LANDED -> this.settle();
         case LEAVING -> {
            double up = Math.min(3.0, 0.05 + this.phaseTicks * 0.06);
            this.setDeltaMovement(0.0, up, 0.0);
            this.setPos(this.position().add(0.0, up, 0.0));
            if (this.phaseTicks >= LEAVE_TICKS || this.getY() > this.level().getMaxBuildHeight() + 64) {
               this.discard();
            }
         }
      }
   }

   private void fall(Vec3 motion) {
      Vec3 from = this.position();
      Vec3 to = from.add(motion);
      BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
      if (hit.getType() == HitResult.Type.MISS && to.y > this.level().getMinBuildHeight()) {
         this.setPos(to);
         return;
      }

      Vec3 at = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
      this.setPos(at.x, at.y, at.z);
      this.setDeltaMovement(Vec3.ZERO);
      this.setPhase(Phase.LANDED);
      ServerPlayer owner = IronManOwned.owner(this, this.ownerId());
      // Blast credited to the owner: PvP/team rules and kill credit apply like any of his hits.
      this.level().explode(this, this.damageSources().explosion(this, owner), null, at.x, at.y + 0.5, at.z, IMPACT_POWER, false, Level.ExplosionInteraction.MOB);
      if (owner != null) {
         dev.baranhan.viltrumitecore.hero.fx.HeroFx.shockwave(owner, at, 1.2F, this.level().getBlockState(BlockPos.containing(at).below()), 6.0F);
         IronManVeronica.onPodLanded(owner, this);
      }
   }

   /**
    * Landed: rests on what is under it. The landing blast digs a crater under
    * the pod (and players may dig too), so it drops into it instead of hanging
    * in the air.
    */
   private void settle() {
      this.setDeltaMovement(Vec3.ZERO);
      Vec3 from = this.position();
      double step = Math.min(1.2, 0.1 + this.settleSpeed);
      Vec3 to = from.add(0.0, -step, 0.0);
      if (to.y <= this.level().getMinBuildHeight()) {
         return;
      }

      BlockHitResult hit = this.level().clip(new ClipContext(from.add(0.0, 0.05, 0.0), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
      if (hit.getType() == HitResult.Type.MISS) {
         this.setPos(to);
         this.settleSpeed = Math.min(1.1, this.settleSpeed + 0.08);
         return;
      }

      Vec3 at = hit.getLocation();
      if (from.y - at.y > 1.0E-3) {
         this.setPos(from.x, at.y, from.z);
      }

      this.settleSpeed = 0.0;
   }

   /** Only an online owner decides (IronManVeronica.retired); offline owners keep the pod. */
   private boolean retired() {
      UUID id = this.ownerId().orElse(null);
      if (id == null || this.level().getServer() == null) {
         return id == null;
      }

      ServerPlayer owner = this.level().getServer().getPlayerList().getPlayer(id);
      return owner != null && IronManVeronica.retired(owner, this.getUUID());
   }

   /** Fly up and away (replaced or retired). */
   public void leave() {
      if (this.phase() == Phase.LEAVING) {
         return;
      }

      this.setPhase(Phase.LEAVING);
      ServerPlayer owner = IronManOwned.owner(this, this.ownerId());
      if (owner != null) {
         IronManVeronica.onPodLeft(owner, this);
      }
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 256.0 * 256.0;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      if (tag.hasUUID("Owner")) {
         this.entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
      }

      int phase = tag.getByte("Phase");
      if (phase == Phase.LEAVING.ordinal() || !tag.hasUUID("Owner")) {
         this.discard();
         return;
      }

      this.entityData.set(PHASE, (byte)(phase == Phase.FALLING.ordinal() ? Phase.FALLING : Phase.LANDED).ordinal());
      if (this.phase() == Phase.FALLING) {
         this.setDeltaMovement(0.0, -FALL_SPEED, 0.0);
      }
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      this.ownerId().ifPresent(id -> tag.putUUID("Owner", id));
      tag.putByte("Phase", (byte)this.phase().ordinal());
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return new ClientboundAddEntityPacket(this);
   }
}

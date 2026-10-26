package dev.baranhan.viltrumitecore.hero.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.HeroImpulse;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.LiftLeases;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of the Reid draw (spec §7.3, REINHARD OVERHAUL item 15). The server owns the
 * timeline; clients only render it. From the press: the lift ({@link GravityLift}) and the scene
 * packet (START, re-sent every {@link ReinhardRules#REID_SCENE_REFRESH} ticks with the same start
 * time, so a late joiner sees the right moment and a refresh never restarts a running scene). At
 * {@link ReinhardRules#REID_RELEASE_TICK}, when the tip clears the mouth: RELEASE (the clients' light
 * and sound accent), the ground wave that pushes (no damage), the chat line, the far sound for every
 * player who does not see the scene; the lift lets go. At {@link ReinhardRules#REID_DRAW_TICKS} the
 * blade is in the guard: END (clients let the light and the sound decay). Death, logout, hero or
 * dimension change, a control or a cut-short draw: ABORT (clients fade everything out at once).
 * The draw's sounds are client-side ({@code ReidDrawAudio}, tied to the same clock), so nothing here
 * plays twice. Never changes a block. Server only, except the pure stage helpers.
 */
public final class ReidDrawEvent {
   public enum Stage {
      GATHER,
      CRACKS,
      TREMBLE,
      FINAL;
   }

   private final ServerLevel level;
   private final int casterId;
   private final UUID casterUuid;
   private final BlockPos origin;
   /** Reinhard's exact position at the press: the scene (beam, cracks, wave) is centred on him, not on his block. */
   private final Vec3 center;
   private final long startTime;
   private final GravityLift lift = new GravityLift(UUID.randomUUID(), LiftLeases.SERVER);
   private final Map<UUID, Entity> lifted = new HashMap<>();
   private boolean released;
   private boolean ended;

   private ReidDrawEvent(ServerPlayer caster) {
      this.level = caster.serverLevel();
      this.casterId = caster.getId();
      this.casterUuid = caster.getUUID();
      this.origin = caster.blockPosition();
      this.center = caster.position();
      this.startTime = this.level.getGameTime();
   }

   /** Pure: the stage of tick {@code t} of the draw: the final starts at the release. */
   public static Stage stageAt(int t) {
      if (t >= ReinhardRules.REID_RELEASE_TICK) {
         return Stage.FINAL;
      }

      if (t >= ReinhardRules.REID_TREMBLE_TICK) {
         return Stage.TREMBLE;
      }

      return t >= ReinhardRules.REID_CRACK_START ? Stage.CRACKS : Stage.GATHER;
   }

   /** Pure: outward push of the final wave at a horizontal distance (no damage). */
   public static double waveKnockback(double distance) {
      return distance <= ReinhardRules.REID_WAVE_RADIUS ? ReinhardRules.REID_WAVE_KNOCKBACK : 0.0;
   }

   /** Pure: the scene packet is re-sent to everyone in range on these ticks (late joiners). */
   public static boolean refreshes(int t) {
      return t > 0 && t % ReinhardRules.REID_SCENE_REFRESH == 0;
   }

   public static ReidDrawEvent start(ServerPlayer caster) {
      ReidDrawEvent event = new ReidDrawEvent(caster);
      event.broadcast(ReidSceneS2CPacket.START);
      event.liftTick(caster, 0);
      return event;
   }

   public BlockPos origin() {
      return this.origin;
   }

   public Vec3 center() {
      return this.center;
   }

   public long startTime() {
      return this.startTime;
   }

   /** Ticks 1..119 of the draw. He stands still (the client clears his input; the server copy loses its horizontal speed). */
   public void tick(ServerPlayer caster, int t) {
      if (this.ended) {
         return;
      }

      Vec3 motion = caster.getDeltaMovement();
      caster.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
      if (!this.released) {
         this.liftTick(caster, t);
      }

      if (refreshes(t)) {
         this.broadcast(ReidSceneS2CPacket.START);
      }

      if (t >= ReinhardRules.REID_RELEASE_TICK && !this.released) {
         this.release(caster);
      }
   }

   /** Tick 96: the tip clears the mouth. Once per draw. */
   private void release(ServerPlayer caster) {
      this.released = true;
      HeroFx.shockwave(caster, this.center, 1.5F, this.level.getBlockState(this.origin.below()), (float)ReinhardRules.REID_WAVE_RADIUS);
      this.wave(caster);
      this.endLift();
      this.broadcast(ReidSceneS2CPacket.RELEASE);
      Component line = Component.translatable("chat.viltrumitecore.reinhard.reid_drawn");
      var farSound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ReinhardSounds.REID_FAR.get());
      for (ServerPlayer player : this.level.getServer().getPlayerList().getPlayers()) {
         player.sendSystemMessage(line);
         // Players who see the scene hear its own release accent; everyone else hears it from afar.
         if (!this.seesScene(player)) {
            player.connection.send(new ClientboundSoundPacket(farSound, SoundSource.AMBIENT, player.getX(), player.getY(), player.getZ(),
               ReinhardRules.REID_FAR_VOLUME, 1.0F, player.getRandom().nextLong()));
         }
      }
   }

   /** Tick 120: the blade is in the Sword Drawn guard; the scene decays on the clients. */
   public void finish(ServerPlayer caster) {
      if (this.ended) {
         return;
      }

      if (!this.released) {
         this.release(caster);
      }

      this.end(ReidSceneS2CPacket.END);
   }

   /** Death, logout, hero / dimension change, control, a cut-short draw: the lift ends and every client fades the scene out. */
   public void abort() {
      if (!this.ended) {
         this.end(ReidSceneS2CPacket.ABORT);
      }
   }

   public boolean released() {
      return this.released;
   }

   public boolean ended() {
      return this.ended;
   }

   private void end(int phase) {
      this.ended = true;
      this.endLift();
      this.broadcast(phase);
   }

   private void endLift() {
      this.lift.end(id -> {
         Entity entity = this.lifted.get(id);
         if (entity == null) {
            entity = this.level.getEntity(id);
         }

         return entity == null || entity.isRemoved() ? null : liftee(entity);
      });
      this.lifted.clear();
   }

   private void liftTick(ServerPlayer caster, int t) {
      if (t % ReinhardRules.REID_LIFT_SCAN_INTERVAL == 0) {
         double r = ReinhardRules.REID_LIFT_RADIUS;
         AABB box = new AABB(this.origin).inflate(r, r, r);
         for (Entity entity : this.level.getEntities((Entity)null, box, e -> this.mayLift(caster, e))) {
            if (entity.distanceToSqr(this.center) <= r * r) {
               this.lifted.putIfAbsent(entity.getUUID(), entity);
            }
         }
      }

      ControlManager controls = ControlManager.get(this.level);
      for (Entity entity : this.lifted.values()) {
         if (entity.isRemoved() || entity.level() != this.level) {
            continue;
         }

         // An entity under a control: the control owns its flags (plan); it is not moved.
         if (entity instanceof LivingEntity living && (controls.isControlled(living) || !HeroRegistry.allowsImpulse(living))) {
            continue;
         }

         this.lift.step(t, liftee(entity));
      }
   }

   private boolean mayLift(ServerPlayer caster, Entity entity) {
      if (entity == caster || entity.isSpectator() || !entity.isAlive()) {
         return false;
      }

      if (entity instanceof LivingEntity living) {
         return HeroImpulse.mayLiftLiving(entity.getClass()) && HeroRegistry.allowsImpulse(living)
            && !ControlManager.get(this.level).isControlled(living);
      }

      return HeroImpulse.mayMoveNonLiving(entity);
   }

   /** Final wave: everyone movable within 24 is pushed outward (no damage). */
   private void wave(ServerPlayer caster) {
      Vec3 center = this.center;
      double r = ReinhardRules.REID_WAVE_RADIUS;
      for (LivingEntity living : this.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r), e -> e != caster && !e.isSpectator())) {
         Vec3 flat = new Vec3(living.getX() - center.x, 0.0, living.getZ() - center.z);
         double strength = waveKnockback(flat.length());
         if (strength <= 0.0 || !HeroImpulse.mayLiftLiving(living.getClass()) || !HeroRegistry.allowsImpulse(living)) {
            continue;
         }

         Vec3 dir = flat.lengthSqr() < 1.0E-6 ? caster.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : flat.normalize();
         living.knockback(strength, -dir.x, -dir.z);
         living.hurtMarked = true;
      }
   }

   /** The scene's audience: players of this level within the scene range, and always the caster. */
   private boolean seesScene(ServerPlayer player) {
      double range = ReinhardRules.REID_SCENE_RANGE;
      return player.getUUID().equals(this.casterUuid)
         || player.level() == this.level && player.distanceToSqr(this.center) <= range * range;
   }

   private void broadcast(int phase) {
      ReidSceneS2CPacket packet = new ReidSceneS2CPacket(this.casterId, this.center, this.startTime, phase);
      for (ServerPlayer player : this.level.players()) {
         if (this.seesScene(player)) {
            CoreMessages.sendToPlayer(packet, player);
         }
      }
   }

   @Nullable
   static GravityLift.Liftee liftee(Entity entity) {
      LiftLeases.Body body = LiftLeases.body(entity);
      return new GravityLift.Liftee() {
         @Override
         public UUID id() {
            return body.id();
         }

         @Override
         public boolean noGravity() {
            return body.noGravity();
         }

         @Override
         public void setNoGravity(boolean value) {
            body.setNoGravity(value);
         }

         @Override
         public double y() {
            return body.y();
         }

         @Override
         public net.minecraft.nbt.CompoundTag persistentData() {
            return body.persistentData();
         }

         @Override
         public void liftVelocity(double vy) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x, vy, motion.z);
            entity.hurtMarked = true;
         }

         @Override
         public void nudgeDown() {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x, -0.1, motion.z);
            entity.hurtMarked = true;
         }
      };
   }
}

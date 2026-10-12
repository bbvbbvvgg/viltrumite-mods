package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dash Behind (spec §6.2, Knight slot 4): to a safe spot
 * {@link ReinhardRules#BEHIND_OFFSET} blocks behind the living entity under the
 * crosshair (≤ {@link ReinhardRules#BEHIND_RANGE}), turned to its back.
 * Client-predicted: {@code client.reinhard.DashBehindClient} moves the player and
 * sends {@code ReinhardDashBehindC2SPacket} (target id + spot) once per key edge;
 * the server re-resolves the target, checks with its own look ray that it really
 * is under the crosshair ({@link #onCrosshair}), and finds its own spot
 * ({@link SafeSpot}) behind the usual gate. No spot → refusal, no cooldown, the client snapped back.
 * Pure parts ({@link #validTarget}, {@link #onCrosshair}, {@link #validCandidate}, {@link #wanted},
 * {@link #choose}, {@link #start}) are unit tested; the rest is server only
 * except {@link #find} and {@link #lookTarget} (both sides).
 */
public final class DashBehind implements AbilityHandler {
   /** What the packet asks for: the target the client saw and the spot it moved to. */
   public record Request(int targetId, Vec3 candidate) {
   }

   /** What the server knows about the packet's target id. */
   public record TargetFacts(boolean found, boolean living, boolean alive, boolean self, boolean spectator, double distance) {
   }

   public static boolean validTarget(TargetFacts f) {
      return f.found() && f.living() && f.alive() && !f.self() && !f.spectator()
         && f.distance() <= ReinhardRules.BEHIND_RANGE + ReinhardRules.BEHIND_TOLERANCE;
   }

   /**
    * The server's own look ray (eye along the look, up to the range + tolerance, stopped by
    * the first block at {@code blockDistance}) passes through the target's box widened by
    * {@link ReinhardRules#BEHIND_AIM_TOLERANCE}. Pure.
    */
   public static boolean onCrosshair(Vec3 eye, Vec3 look, AABB targetBox, double blockDistance) {
      if (look.lengthSqr() < 1.0E-8) {
         return false;
      }

      double reach = Math.min(ReinhardRules.BEHIND_RANGE + ReinhardRules.BEHIND_TOLERANCE, blockDistance);
      AABB box = targetBox.inflate(ReinhardRules.BEHIND_AIM_TOLERANCE);
      if (box.contains(eye)) {
         return true;
      }

      return box.clip(eye, eye.add(look.normalize().scale(reach))).isPresent();
   }

   /** Server: {@link #onCrosshair} with the player's eye, look and the blocks of his level (a multipart target: any part). */
   static boolean onCrosshair(ServerPlayer player, LivingEntity target) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      double reach = ReinhardRules.BEHIND_RANGE + ReinhardRules.BEHIND_TOLERANCE;
      HitResult block = player.level().clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      double blockDistance = block.getType() == HitResult.Type.MISS ? reach : block.getLocation().distanceTo(eye);
      if (onCrosshair(eye, look, target.getBoundingBox(), blockDistance)) {
         return true;
      }

      if (target.isMultipartEntity() && target.getParts() != null) {
         for (net.minecraftforge.entity.PartEntity<?> part : target.getParts()) {
            if (onCrosshair(eye, look, part.getBoundingBox(), blockDistance)) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean validCandidate(@Nullable Vec3 v) {
      return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }

   /** The wanted feet: the target's feet minus its horizontal look × the offset. */
   public static Vec3 wanted(Vec3 targetFeet, float targetYaw) {
      double yaw = Math.toRadians(targetYaw);
      return targetFeet.add(Math.sin(yaw) * ReinhardRules.BEHIND_OFFSET, 0.0, -Math.cos(yaw) * ReinhardRules.BEHIND_OFFSET);
   }

   /** The client's spot when it passes the server probe and lies within 1 block of the server answer; else the answer. */
   public static Optional<Vec3> choose(Vec3 client, Optional<Vec3> server, Predicate<Vec3> serverOk) {
      if (server.isEmpty()) {
         return Optional.empty();
      }

      if (validCandidate(client) && client.distanceTo(server.get()) <= ReinhardRules.BEHIND_CLIENT_TOLERANCE && serverOk.test(client)) {
         return Optional.of(client);
      }

      return server;
   }

   /** The state side: cooldown and the action lock only when a spot exists. */
   public static boolean start(ReinhardState state, Optional<Vec3> spot, int targetId) {
      if (spot.isEmpty()) {
         return false;
      }

      state.cooldowns[ReinhardAbility.DASH_BEHIND.ordinal()] = ReinhardRules.BEHIND_COOLDOWN;
      state.action.start(ReinhardAbility.DASH_BEHIND, ReinhardRules.BEHIND_ACTION_TICKS, targetId);
      return true;
   }

   /** Yaw (degrees, Minecraft convention) looking from {@code from} towards {@code to}. */
   public static float yawTowards(Vec3 from, Vec3 to) {
      return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
   }

   public static float pitchTowards(Vec3 from, Vec3 to) {
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      return (float) -Math.toDegrees(Math.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)));
   }

   /** Both sides: the living entity under the crosshair within range. */
   @Nullable
   public static LivingEntity lookTarget(Player player) {
      return dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets.lookTarget(player, ReinhardRules.BEHIND_RANGE, Entity::isAlive);
   }

   /** Both sides: the safe spot behind {@code target} for {@code mover}. */
   public static Optional<Vec3> find(Level level, Entity mover, LivingEntity target) {
      return SafeSpot.find(wanted(target.position(), target.getYRot()), new SafeSpot.LevelProbe(level, mover), ReinhardRules.BEHIND_SEARCH,
         ReinhardRules.BEHIND_SEARCH_STEP);
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      Request request = state.behindRequest;
      if (request == null || !validCandidate(request.candidate())) {
         return false;
      }

      Entity entity = player.level().getEntity(request.targetId());
      LivingEntity target = entity instanceof LivingEntity living ? living : null;
      if (!validTarget(new TargetFacts(entity != null, target != null, entity != null && entity.isAlive(), entity == player,
         entity != null && entity.isSpectator(), entity == null ? 0.0 : entity.distanceTo(player))) || !onCrosshair(player, target)) {
         return false;
      }

      SafeSpot.LevelProbe probe = new SafeSpot.LevelProbe(player.level(), player);
      Optional<Vec3> spot = choose(request.candidate(), find(player.level(), player, target), v -> SafeSpot.ok(v, probe));
      Vec3 from = player.position();
      if (!start(state, spot, target.getId())) {
         return false;
      }

      Vec3 to = spot.get();
      Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
      float yaw = yawTowards(to, aim);
      float pitch = pitchTowards(to.add(0.0, player.getEyeHeight(), 0.0), aim);
      afterimage(player, from);
      player.stopRiding();
      player.connection.teleport(to.x, to.y, to.z, yaw, pitch);
      player.setYHeadRot(yaw);
      player.setDeltaMovement(Vec3.ZERO);
      player.resetFallDistance();
      HeroFx.flash(player, to.add(0.0, 0.9, 0.0));
      ReinhardSounds.playAt(player, from, ReinhardSounds.DASH_BEHIND.get(), 0.7F, 1.5F);
      ReinhardSounds.playAt(player, to, ReinhardSounds.DASH_BEHIND.get(), 1.0F, 1.5F);
      return true;
   }

   /** Refused: the client may have moved already, so it is put back where the server has it. */
   @Override
   public void refused(ServerPlayer player, ReinhardState state, ReinhardActions.Refusal refusal) {
      player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
   }

   /** A white puff column where he stood (everyone nearby sees it). */
   private static void afterimage(ServerPlayer player, Vec3 feet) {
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }

      for (int i = 0; i < 6; i++) {
         level.sendParticles(ParticleTypes.POOF, feet.x, feet.y + 0.3 * i, feet.z, 2, 0.18, 0.1, 0.18, 0.0);
      }
   }
}

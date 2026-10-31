package dev.baranhan.viltrumitecore.hero.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.AbilityHandler;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.Strikes;
import dev.baranhan.viltrumitecore.hero.secret.HeroSecrets;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchS2CPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reinhard's secret THE FINAL PUNCH (Shift + N; docs/design/2026-10-31-reinhard-final-punch.md).
 * Server authoritative: picks a registered target ({@link FinalPunchTargets}) on the look ray,
 * freezes it face to face with Reinhard for the 22 s speech / windup (one FREEZE control, one
 * effect id), lands one hit at {@link FinalPunchRules#CONTACT}, holds the hit-stop and launches the
 * body. Clients only render (camera, fist sun, warp, the knocked-off jaw) from the snapshot and
 * {@link ReinhardFinalPunchS2CPacket}. No block is ever changed.
 */
public final class FinalPunch implements AbilityHandler {
   public static final String SECRET_ID = "reinhard:final_punch";
   public static final HeroSecrets.Secret SECRET = HeroSecrets.register(new HeroSecrets.Secret(SECRET_ID, "reinhard", "shift+ability_6",
      "secret.viltrumitecore.reinhard.final_punch", "secret.viltrumitecore.reinhard.final_punch.trigger",
      "secret.viltrumitecore.reinhard.final_punch.targets", "secret.viltrumitecore.reinhard.final_punch.effect"));
   /** Lateral offset of the fist from Reinhard's center at contact (right of his look). */
   public static final double FIST_RIGHT = 0.13;
   /** Distance of the fist from Reinhard's center at contact. */
   public static final double FIST_REACH = 1.0;

   /** The running scene on the caster's state; transient. */
   public static final class Scene {
      final int targetId;
      final UUID targetUuid;
      final UUID effectId;
      final FinalPunchTargets.Entry entry;
      final ServerLevel level;
      final float yaw;
      final Vec3 anchor;
      boolean released;
      Vec3 velocity;
      int sustain;
      int sustained;

      Scene(int targetId, UUID targetUuid, UUID effectId, FinalPunchTargets.Entry entry, ServerLevel level, float yaw, Vec3 anchor) {
         this.targetId = targetId;
         this.targetUuid = targetUuid;
         this.effectId = effectId;
         this.entry = entry;
         this.level = level;
         this.yaw = yaw;
         this.anchor = anchor;
      }

      public int targetId() {
         return this.targetId;
      }

      /** The target is still frozen in front of Reinhard (before the contact). */
      public boolean holding() {
         return !this.released;
      }
   }

   /** Pure: where the target's feet stand (face to face, its face on the fist line). */
   public static Vec3 anchor(Vec3 caster, float yaw, float faceDepth) {
      Vec3 flat = Vec3.directionFromRotation(0, yaw);
      Vec3 right = new Vec3(-flat.z, 0, flat.x);
      return caster.add(flat.scale(FIST_REACH + faceDepth - 0.05)).add(right.scale(FIST_RIGHT));
   }

   /** Pure: the jaw point of a target standing at {@code feet} and facing back along {@code yaw}. */
   public static Vec3 jawPoint(Vec3 feet, float yaw, FinalPunchTargets.Entry entry) {
      Vec3 flat = Vec3.directionFromRotation(0, yaw);
      return feet.add(0, entry.headHeight(), 0).subtract(flat.scale(entry.faceDepth()));
   }

   /** Server: is this entity a legal target for this caster right now (registry, hit rules, control rules). */
   public static boolean valid(ServerPlayer player, @Nullable Entity entity) {
      if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive() || entity.level() != player.level()) return false;
      if (FinalPunchTargets.of(entity) == null || !ReinhardTargets.canHit(player, entity)) return false;
      if (!HeroRegistry.allowsExternalControl(living, ControlKind.FREEZE) || living.getTags().contains("ViltrumiteGrabbed")) return false;
      return !ControlManager.get(player.serverLevel()).isControlled(living);
   }

   /** Server: the client's hint when it still sits in the look cone, else the server's own pick. */
   @Nullable
   static LivingEntity resolve(ServerPlayer player, int hint) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Entity hinted = hint >= 0 ? player.level().getEntity(hint) : null;
      if (valid(player, hinted) && player.hasLineOfSight(hinted)
         && FinalPunchTargets.withinGrace(look, center(hinted).subtract(eye), FinalPunchRules.TARGET_RADIUS, FinalPunchRules.TARGET_CONE_DEGREES)) {
         return (LivingEntity) hinted;
      }

      double r = FinalPunchRules.TARGET_RADIUS;
      List<FinalPunchTargets.Candidate> candidates = new ArrayList<>();
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r), e -> FinalPunchTargets.of(e) != null)) {
         candidates.add(new FinalPunchTargets.Candidate(e.getId(), center(e).subtract(eye), valid(player, e), player.hasLineOfSight(e)));
      }
      int id = FinalPunchTargets.pick(look, candidates, r, FinalPunchRules.TARGET_CONE_DEGREES);
      return id >= 0 && player.level().getEntity(id) instanceof LivingEntity living ? living : null;
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      if (state.finalPunch != null) return false;
      LivingEntity target = resolve(player, state.finalPunchHint);
      FinalPunchTargets.Entry entry = FinalPunchTargets.of(target);
      if (target == null || entry == null) return false;

      float yaw = player.getYRot();
      Vec3 at = anchor(player.position(), yaw, entry.faceDepth());
      AABB box = target.getBoundingBox().move(at.subtract(target.position()));
      if (!player.level().noCollision(target, box)) return false;

      ServerLevel level = player.serverLevel();
      ControlManager manager = ControlManager.get(level);
      UUID effect = UUID.randomUUID();
      Vec3 from = target.position();
      float fromYaw = target.getYRot();
      target.stopRiding();
      place(target, at, yaw + 180.0F);
      long expiry = level.getGameTime() + FinalPunchRules.LENGTH + 40L;
      if (!manager.tryAcquire(target, player.getUUID(), effect, ControlKind.FREEZE, expiry)) {
         place(target, from, fromYaw);
         return false;
      }

      Scene scene = new Scene(target.getId(), target.getUUID(), effect, entry, level, yaw, at);
      state.finalPunch = scene;
      state.action.start(ReinhardAbility.FINAL_PUNCH, FinalPunchRules.LENGTH, target.getId());
      // Re-activation is impossible while the scene runs (busy) and for the cooldown afterwards.
      state.cooldowns[ReinhardAbility.FINAL_PUNCH.ordinal()] = FinalPunchRules.cooldownTicks(ViltrumiteCoreConfig.INSTANCE.reinhardFinalPunchCooldownSeconds);
      player.setDeltaMovement(Vec3.ZERO);
      player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, 0.0F);
      CoreMessages.sendToTrackingAndSelf(packet(ReinhardFinalPunchS2CPacket.START, player, scene, jawPoint(at, yaw, entry), false), player);
      HeroSecrets.discover(player, SECRET_ID, level.getGameTime());
      return true;
   }

   private static ReinhardFinalPunchS2CPacket packet(int type, ServerPlayer player, Scene scene, Vec3 at, boolean landed) {
      return new ReinhardFinalPunchS2CPacket(type, player.getId(), scene.targetId, scene.entry.jaw().ordinal(), scene.entry.kind().ordinal(),
         scene.yaw, at, Vec3.directionFromRotation(0, scene.yaw), landed);
   }

   private static void place(LivingEntity target, Vec3 at, float yaw) {
      if (target instanceof ServerPlayer victim) {
         victim.connection.teleport(at.x, at.y, at.z, yaw, 0.0F);
      } else {
         target.moveTo(at.x, at.y, at.z, yaw, 0.0F);
         target.setYHeadRot(yaw);
         target.yBodyRot = yaw;
      }
      target.setDeltaMovement(Vec3.ZERO);
      target.resetFallDistance();
      target.hurtMarked = true;
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      Scene scene = state.finalPunch;
      if (scene == null) return;
      if (!state.action.is(ReinhardAbility.FINAL_PUNCH)) {
         // The timeline ended by itself (tick LENGTH) or was dropped elsewhere.
         end(player, state, ReleaseReason.NORMAL_END, !state.action.done());
         return;
      }

      int t = state.action.elapsed();
      Entity entity = scene.level.getEntity(scene.targetUuid);
      LivingEntity target = entity instanceof LivingEntity living && living.isAlive() ? living : null;
      // After the blow a body that is dying still flies: its death pose lasts 20 ticks before it is removed.
      LivingEntity body = entity instanceof LivingEntity living && !living.isRemoved() ? living : null;
      switch (FinalPunchRules.beat(t)) {
         case HOLD -> hold(player, state, scene, target, t);
         case CONTACT -> contact(player, scene, target);
         case HIT_STOP -> {
            if (body != null && HeroRegistry.allowsImpulse(body)) move(body, Vec3.ZERO);
         }
         case LAUNCH -> launch(player, scene, body);
         default -> {
         }
      }
      sustain(scene, body);
   }

   private static void hold(ServerPlayer player, ReinhardState state, Scene scene, @Nullable LivingEntity target, int t) {
      ControlManager manager = ControlManager.get(scene.level);
      if (target == null || target.level() != player.level() || !scene.effectId.equals(manager.effectIdOf(target))
         || target.position().distanceToSqr(scene.anchor) > FinalPunchRules.LOST_DISTANCE * FinalPunchRules.LOST_DISTANCE) {
         // Death, logout, another dimension, or the freeze ended elsewhere.
         end(player, state, ReleaseReason.TARGET_LOST, true);
         return;
      }

      if (!(target instanceof Player)) {
         float face = scene.yaw + 180.0F;
         target.setYRot(face);
         target.setYHeadRot(face);
         target.yBodyRot = face;
         target.setXRot(t < FinalPunchRules.LOOK_UP ? 0.0F : -6.0F);
      }

      int line = FinalPunchRules.lineAt(t);
      if (line >= 0) FinalPunchLines.say(player, line);
      if (FinalPunchRules.stormPulse(t)) storm(player, scene, target, FinalPunchRules.storm(t));
   }

   /** Phase IV: loose things around him lift and swirl (items, orbs, carts, boats, falling blocks, animals); never blocks, players or the target. */
   private static void storm(ServerPlayer player, Scene scene, LivingEntity target, double power) {
      if (power <= 0.0) return;
      Vec3 c = player.position();
      int moved = 0;
      AABB area = player.getBoundingBox().inflate(FinalPunchRules.STORM_RADIUS);
      for (Entity e : scene.level.getEntities(player, area, FinalPunch::stormy)) {
         if (e == target || moved >= FinalPunchRules.STORM_MAX_ENTITIES) continue;
         if (e instanceof LivingEntity living && !HeroRegistry.allowsImpulse(living)) continue;
         Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
         double d = out.length();
         if (d < 0.5 || d > FinalPunchRules.STORM_RADIUS) continue;
         out = out.scale(1.0 / d);
         Vec3 swirl = new Vec3(-out.z, 0, out.x);
         double falloff = 1.0 - d / FinalPunchRules.STORM_RADIUS * 0.6;
         Vec3 push = out.scale(0.10 * power).add(swirl.scale(0.22 * power)).add(0, 0.10 + 0.16 * power, 0).scale(falloff);
         e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(push));
         e.hasImpulse = true;
         e.hurtMarked = true;
         e.resetFallDistance();
         moved++;
      }
   }

   static boolean stormy(Entity e) {
      return e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof AbstractMinecart || e instanceof Boat
         || e instanceof FallingBlockEntity || e instanceof Animal;
   }

   private static void contact(ServerPlayer player, Scene scene, @Nullable LivingEntity target) {
      release(player, scene, ReleaseReason.NORMAL_END);
      if (target == null) return;
      Vec3 at = jawPoint(target.position(), scene.yaw, scene.entry);
      boolean landed = Strikes.hit(player, target, FinalPunchRules.damage(ViltrumiteCoreConfig.INSTANCE.reinhardFinalPunchDamage));
      if (HeroRegistry.allowsImpulse(target)) move(target, Vec3.ZERO);
      ReinhardFinalPunchS2CPacket impact = packet(ReinhardFinalPunchS2CPacket.IMPACT, player, scene, at, landed);
      CoreMessages.sendToTrackingAndSelf(impact, player);
      if (target instanceof ServerPlayer victim && !victim.hasDisconnected()) CoreMessages.sendToPlayer(impact, victim);
      HeroFx.hitStop(player, at, FinalPunchRules.HIT_STOP_TICKS, player.getId(), target.getId());
      // No HeroFx.flash: the scene draws its own soft impact light, shake and screen flash on every
      // tracking client (FinalPunchVfx.impact); the shared pixel flash was a hard white square in the close-up.
      HeroFx.sonicRing(player, at);
      BlockState ground = scene.level.getBlockState(target.blockPosition().below());
      HeroFx.shockwave(player, target.position(), 2.5F, ground.isAir() ? null : ground, 9.0F);
      ReinhardSounds.playAt(player, at, ReinhardSounds.FINAL_PUNCH_IMPACT.get(), 4.0F, 1.0F);
      ReinhardSounds.playAt(player, at, scene.entry.kind() == FinalPunchTargets.Kind.GOLEM
         ? ReinhardSounds.FINAL_PUNCH_CLANG.get() : ReinhardSounds.FINAL_PUNCH_JAW.get(), 0.5F, 1.0F);
   }

   private static void launch(ServerPlayer player, Scene scene, @Nullable LivingEntity target) {
      if (target == null || !HeroRegistry.allowsImpulse(target)) return;
      ViltrumiteCoreConfig cfg = ViltrumiteCoreConfig.INSTANCE;
      Vec3 flat = Vec3.directionFromRotation(0, scene.yaw);
      double up = Double.isFinite(cfg.reinhardFinalPunchLaunchUp) ? Math.max(0.0, Math.min(2.0, cfg.reinhardFinalPunchLaunchUp)) : 0.0;
      scene.velocity = flat.scale(FinalPunchRules.launchSpeed(cfg.reinhardFinalPunchLaunchSpeed)).add(0, up, 0);
      scene.sustain = FinalPunchRules.sustainTicks(cfg.reinhardFinalPunchLaunchTicks);
      scene.sustained = 0;
      target.resetFallDistance();
      HeroFx.airTrail(player, target);
   }

   private static void sustain(Scene scene, @Nullable LivingEntity target) {
      if (scene.sustain <= 0) return;
      boolean blocked = target != null && scene.sustained > 1 && (target.horizontalCollision || target.verticalCollision && scene.sustained > 3);
      if (target == null || blocked || !HeroRegistry.allowsImpulse(target) || scene.velocity == null) {
         scene.sustain = 0;
         return;
      }
      move(target, scene.velocity);
      target.resetFallDistance();
      scene.sustain--;
      scene.sustained++;
   }

   static void move(LivingEntity target, Vec3 velocity) {
      target.setDeltaMovement(velocity);
      target.hasImpulse = true;
      target.hurtMarked = true;
      if (target instanceof ServerPlayer hit) hit.connection.send(new ClientboundSetEntityMotionPacket(hit));
   }

   static Vec3 center(Entity e) {
      return e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
   }

   /** Releases the freeze once (same effect id); a frozen player gets a position handshake. */
   private static void release(ServerPlayer player, Scene scene, ReleaseReason reason) {
      if (scene.released) return;
      scene.released = true;
      ControlManager.get(scene.level).release(scene.targetUuid, scene.effectId, reason);
      Entity released = scene.level.getEntity(scene.targetUuid);
      if (released instanceof ServerPlayer victim && victim.isAlive() && reason != ReleaseReason.NORMAL_END) {
         victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
      }
   }

   /** Ends the scene: release, and a forced end tells every client to restore (camera, jaw stays a visual). */
   private static void end(ServerPlayer player, ReinhardState state, ReleaseReason reason, boolean forced) {
      Scene scene = state.finalPunch;
      if (scene == null) return;
      release(player, scene, reason);
      state.finalPunch = null;
      if (forced) {
         if (FinalPunchRules.beat(Math.max(0, state.action.elapsed())) == FinalPunchRules.Beat.HOLD) HeroSpeech.interrupt(player, null);
         CoreMessages.sendToTrackingAndSelf(packet(ReinhardFinalPunchS2CPacket.CANCEL, player, scene, scene.anchor, false), player);
         if (state.action.is(ReinhardAbility.FINAL_PUNCH)) state.action.cancel();
      }
   }

   @Override
   public void cancel(ServerPlayer player, ReinhardState state, @Nullable CleanupReason reason) {
      if (state.finalPunch != null) end(player, state, GrabThrow.releaseReason(reason), true);
   }
}

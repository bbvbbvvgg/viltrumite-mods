package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server fatalities (decisions 53, 66, 69, 85, 90-94, 96, 216). The death is
 * decided at the scene start: the victim is doomed (no other damage, no
 * escape, logout = death) and dies exactly once, by Thor's hand, when the
 * scene ends (or at once when the scene breaks off). Both bodies move only
 * along the shared {@link FatalityPlan}; when the hit spot does not fit the
 * scene, a nearby safe spot is reached by walking there in the intro, and
 * without one the scene is skipped (the hit kills normally). Hero changes
 * asked for during the scene wait for its end. Server main thread.
 */
final class FatalitySessions {
   static final class Session {
      final UUID thor;
      final int thorId;
      final UUID victim;
      final int victimId;
      final UUID effectId = UUID.randomUUID();
      final ServerLevel level;
      final FatalityPlan plan;
      final boolean rehearsal;
      int tick;

      Session(ServerPlayer thor, LivingEntity victim, FatalityPlan plan, boolean rehearsal) {
         this.thor = thor.getUUID();
         this.thorId = thor.getId();
         this.victim = victim.getUUID();
         this.victimId = victim.getId();
         this.level = thor.serverLevel();
         this.plan = plan;
         this.rehearsal = rehearsal;
      }
   }

   private static final Map<UUID, Session> SESSIONS = new HashMap<>();
   /** Victims whose death is decided (not rehearsals). */
   private static final Set<UUID> DOOMED = new HashSet<>();
   /** The one kill in progress: its death event passes. */
   private static final Set<UUID> EXECUTING = new HashSet<>();
   private static final Map<UUID, FatalityScene> LAST = new HashMap<>();
   /** Hero changes asked for during a scene, applied at its end (decision 216: deferred to the scene end). */
   private static final Map<UUID, HeroId> DEFERRED = new LinkedHashMap<>();

   private FatalitySessions() {
   }

   @Nullable
   static Session of(UUID thor) {
      return SESSIONS.get(thor);
   }

   @Nullable
   static Session victimOf(UUID victim) {
      for (Session session : SESSIONS.values()) {
         if (session.victim.equals(victim)) {
            return session;
         }
      }
      return null;
   }

   static boolean doomed(UUID entity) {
      return DOOMED.contains(entity);
   }

   static boolean executing(UUID entity) {
      return EXECUTING.contains(entity);
   }

   static boolean participant(UUID entity) {
      return SESSIONS.containsKey(entity) || victimOf(entity) != null;
   }

   static boolean defer(ServerPlayer player, HeroId id) {
      if (!participant(player.getUUID())) {
         return false;
      }

      DEFERRED.put(player.getUUID(), id);
      player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.viltrumitecore.thor.scene.hero_change_deferred"), true);
      return true;
   }

   @Nullable
   static FatalityScene last(UUID thor) {
      return LAST.get(thor);
   }

   /**
    * Try to begin a fatality on {@code victim}. False = no scene (no space,
    * the timeline refused, the control refused): the caller lets the hit kill
    * normally.
    */
   static boolean start(ServerPlayer thor, ThorState state, LivingEntity victim, FatalityScene scene, boolean rehearsal) {
      if (SESSIONS.containsKey(thor.getUUID()) || victimOf(victim.getUUID()) != null || victim == thor || victim.level() != thor.level()) {
         return false;
      }

      Vec3 victimPos = victim.position();
      Vec3 velocity = victim.getDeltaMovement();
      double above = victimPos.y - SceneFx.ground(thor.serverLevel(), victimPos).y;
      float toThor = SceneFrame.yawTo(victimPos, thor.position());
      SceneEntry entry = SceneEntry.classify(victim.onGround(), above, velocity.y, velocity.horizontalDistance(), victim.getYRot(), toThor);
      Optional<Vec3> origin = origin(thor, victim, scene);
      if (origin.isEmpty()) {
         return false;
      }

      float frameYaw = SceneFrame.yawTo(origin.get(), victimPos);
      if (Float.isNaN(frameYaw)) {
         frameYaw = thor.getYRot();
      }

      FatalityPlan plan = FatalityPlan.of(scene, entry, thor.position(), thor.getYRot(), victimPos, victim.getYRot(), velocity, origin.get(), frameYaw,
         ThorSceneConfig.fatalityIntroMin, ThorSceneConfig.fatalityIntroMax, ThorSceneConfig.fatalityMaxStep, ThorSceneConfig.fatalityTurnRate);
      Session session = new Session(thor, victim, plan, rehearsal);
      ControlManager control = ControlManager.get(session.level);
      if (!control.tryAcquire(victim, thor.getUUID(), session.effectId, ControlKind.PULL, thor.level().getGameTime() + plan.length() + 200L)) {
         return false;
      }

      if (ThorScenes.begin(thor, state, ThorScenes.FATALITY, victim.getId()) != ThorActions.Refusal.NONE
         || !ThorBridge.lockScene(state, ThorScenes.OWNER)) {
         control.release(victim.getUUID(), session.effectId, ReleaseReason.INTERRUPTED);
         ThorScenes.stopMove(state, ThorScenes.FATALITY);
         return false;
      }

      ThorBridge.acquireCamera(state, ThorScenes.OWNER);
      ThorBridge.closeGrabWindow(state);
      ThorScenes.stopFlight(thor);
      ThorScenes.stopFlight(victim);
      if (!rehearsal) {
         DOOMED.add(victim.getUUID());
      }

      if (victim.getHealth() <= 0.0F) {
         victim.setHealth(1.0F);
      }

      LAST.put(thor.getUUID(), scene);
      SESSIONS.put(thor.getUUID(), session);
      sync(thor, session);
      return true;
   }

   /** Where the main part plays: Thor's spot when the scene fits there, else the nearest safe spot within the radius. */
   private static Optional<Vec3> origin(ServerPlayer thor, LivingEntity victim, FatalityScene scene) {
      ServerLevel level = thor.serverLevel();
      Vec3 start = thor.position();
      SafeSpot.Probe probe = new SafeSpot.Probe() {
         @Override
         public boolean fits(Vec3 feet) {
            float yaw = SceneFrame.yawTo(feet, victim.position());
            if (Float.isNaN(yaw)) {
               yaw = thor.getYRot();
            }
            return sceneFits(level, thor, victim, feet, yaw, scene);
         }

         @Override
         public boolean insideBorder(Vec3 feet) {
            return level.getWorldBorder().isWithinBounds(feet.x, feet.z);
         }

         @Override
         public boolean groundBelow(Vec3 feet, int maxDrop) {
            BlockPos below = BlockPos.containing(feet.x, feet.y - 0.05, feet.z);
            return level.isLoaded(below) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
         }
      };
      if (SafeSpot.ok(start, probe)) {
         return Optional.of(start);
      }

      Vec3 floor = SceneFx.ground(level, start);
      return SafeSpot.find(floor, probe, ThorSceneConfig.fatalityRelocateRadius, 1.0).map(spot -> {
         Vec3 snapped = SceneFx.ground(level, spot.add(0, 0.5, 0));
         return new Vec3(Math.floor(spot.x) + 0.5, snapped.y, Math.floor(spot.z) + 0.5);
      });
   }

   /** Both bodies fit along the frame and the scene's headroom is free. */
   static boolean sceneFits(ServerLevel level, ServerPlayer thor, LivingEntity victim, Vec3 feet, float yaw, FatalityScene scene) {
      Vec3 anchor = SceneFrame.world(feet, yaw, scene.victim(0));
      if (!level.noCollision(thor, thor.getBoundingBox().move(feet.subtract(thor.position())))
         || !level.noCollision(victim, victim.getBoundingBox().move(anchor.subtract(victim.position())))) {
         return false;
      }

      Vec3 far = SceneFrame.world(feet, yaw, new SceneFrame.Offset(scene.reach(), 0, 0));
      AABB column = new AABB(Math.min(feet.x, far.x) - 0.4, feet.y + 0.1, Math.min(feet.z, far.z) - 0.4, Math.max(feet.x, far.x) + 0.4,
         feet.y + scene.headroom(), Math.max(feet.z, far.z) + 0.4);
      return level.noCollision(column);
   }

   static void tick(MinecraftServer server) {
      for (Session session : new ArrayList<>(SESSIONS.values())) {
         ServerPlayer thor = server.getPlayerList().getPlayer(session.thor);
         Entity entity = session.level.getEntity(session.victim);
         LivingEntity victim = entity instanceof LivingEntity living && living.isAlive() ? living : null;
         if (thor == null || !thor.isAlive() || thor.level() != session.level || ThorState.of(thor) == null || victim == null
            || victim.level() != session.level) {
            abort(thor, session);
            continue;
         }

         session.tick++;
         Vec3 at = session.plan.victimAt(session.tick);
         victim.setPos(at.x, at.y, at.z);
         victim.setDeltaMovement(Vec3.ZERO);
         victim.resetFallDistance();
         victim.hurtMarked = true;
         thor.resetFallDistance();
         if (DOOMED.contains(session.victim) && victim.getHealth() < 1.0F) {
            victim.setHealth(1.0F);
         }

         FatalityScene.Beat beat = session.plan.scene().beatAt((int)session.plan.mainTick(session.tick));
         if (beat != null) {
            SceneFx.beat(thor, victim, session.plan, beat);
         }

         if (session.tick >= session.plan.length()) {
            finish(thor, session, victim);
         } else if (session.tick % 10 == 0) {
            sync(thor, session);
         }
      }
   }

   private static void finish(ServerPlayer thor, Session session, LivingEntity victim) {
      close(thor, session);
      if (!session.rehearsal) {
         execute(thor, victim);
      }
      applyDeferred(session);
   }

   /** The scene broke off (Thor gone, dimension, interrupt): a decided death still happens, once. */
   static void abort(@Nullable ServerPlayer thor, Session session) {
      if (!SESSIONS.containsKey(session.thor)) {
         return;
      }

      close(thor, session);
      Entity entity = session.level.getEntity(session.victim);
      if (!session.rehearsal && entity instanceof LivingEntity victim && victim.isAlive()) {
         execute(thor, victim);
      }
      DOOMED.remove(session.victim);
      applyDeferred(session);
   }

   static void abortFor(UUID participant) {
      Session session = SESSIONS.get(participant);
      if (session == null) {
         session = victimOf(participant);
      }

      if (session != null) {
         MinecraftServer server = session.level.getServer();
         abort(server.getPlayerList().getPlayer(session.thor), session);
      }
   }

   private static void close(@Nullable ServerPlayer thor, Session session) {
      SESSIONS.remove(session.thor);
      ControlManager.get(session.level).release(session.victim, session.effectId, ReleaseReason.NORMAL_END);
      Entity victim = session.level.getEntity(session.victim);
      if (victim instanceof ServerPlayer player && session.rehearsal) {
         player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
      }

      if (thor != null) {
         ThorState state = ThorState.of(thor);
         if (state != null) {
            ThorBridge.unlockScene(state, ThorScenes.OWNER);
            ThorBridge.releaseCamera(state, ThorScenes.OWNER);
            ThorScenes.stopMove(state, ThorScenes.FATALITY);
         }
         ThorScenes.send(thor, victim, SceneSync.end(SceneSync.Kind.FATALITY, session.thorId, session.victimId));
      } else if (victim instanceof ServerPlayer player) {
         dev.baranhan.viltrumitecore.network.CoreMessages.sendToPlayer(
            new dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneS2CPacket(SceneSync.end(SceneSync.Kind.FATALITY, session.thorId, session.victimId)),
            player);
      }
   }

   /** The single kill of a decided death, credited to Thor when he is still here. */
   static void execute(@Nullable ServerPlayer thor, LivingEntity victim) {
      UUID id = victim.getUUID();
      DOOMED.remove(id);
      if (!victim.isAlive()) {
         return;
      }

      EXECUTING.add(id);
      try {
         DamageSource source = thor != null ? victim.damageSources().playerAttack(thor) : victim.damageSources().generic();
         victim.setHealth(0.0F);
         victim.die(source);
      } finally {
         EXECUTING.remove(id);
      }
   }

   private static void applyDeferred(Session session) {
      MinecraftServer server = session.level.getServer();
      for (UUID id : new UUID[]{session.thor, session.victim}) {
         HeroId next = DEFERRED.remove(id);
         ServerPlayer player = next == null ? null : server.getPlayerList().getPlayer(id);
         if (player != null) {
            HeroRegistry.changeHero(player, next);
         }
      }
   }

   static void clear() {
      SESSIONS.clear();
      DOOMED.clear();
      EXECUTING.clear();
      DEFERRED.clear();
   }

   static void sync(ServerPlayer thor, Session session) {
      ThorScenes.send(thor, session.level.getEntity(session.victim), SceneSync.fatality(session.thorId, session.victimId, session.plan, session.tick,
         session.rehearsal));
   }
}

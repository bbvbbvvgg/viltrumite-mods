package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.HeroHeldTargets;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Server grabs (decisions 15, 59-62, 64, 65, 88, 117, 122-126). Thor holds the
 * victim in front of him with the shared PULL control (no second hold
 * system) and the shared held-target seam (the victim's client and every
 * tracker render the same anchor); the victim can only look and press its
 * one resist key; Thor walks and turns, never flies or teleports (the scene
 * lock refuses every other Thor press) and picks one of three actions.
 * Mobs hitting Thor do not interrupt the hold. Server main thread.
 */
final class GrabSessions {
   static final class Session {
      final UUID thor;
      final int thorId;
      final UUID victim;
      final int victimId;
      final UUID effectId = UUID.randomUUID();
      final ServerLevel level;
      final GrabFlow flow;
      boolean released;

      Session(ServerPlayer thor, LivingEntity victim, GrabFlow flow) {
         this.thor = thor.getUUID();
         this.thorId = thor.getId();
         this.victim = victim.getUUID();
         this.victimId = victim.getId();
         this.level = thor.serverLevel();
         this.flow = flow;
      }
   }

   private static final Map<UUID, Session> SESSIONS = new HashMap<>();
   /** The shared PULL control is leased in slices and renewed while the grab lasts (no hold time limit). */
   private static final long CONTROL_LEASE = 200L;

   private GrabSessions() {
   }

   @Nullable
   static Session of(UUID thor) {
      return SESSIONS.get(thor);
   }

   @Nullable
   static Session victimOf(UUID victim) {
      for (Session session : SESSIONS.values()) {
         if (!session.released && session.victim.equals(victim)) {
            return session;
         }
      }
      return null;
   }

   static boolean holding(Session session) {
      return !session.released && (session.flow.stage() == GrabFlow.Stage.HOLD || session.flow.stage() == GrabFlow.Stage.ACTION);
   }

   /** Why a grab cannot begin on this target, or null when it can. */
   @Nullable
   static String refusal(ServerPlayer thor, @Nullable LivingEntity target) {
      if (target == null || !target.isAlive() || target == thor || target.level() != thor.level()) {
         return "target";
      } else if (target.isSpectator() || HeroHeldTargets.holder(target) != null || ThorScenes.inScene(target.getUUID())) {
         return "busy";
      } else if (!HeroRegistry.allowsExternalControl(target, ControlKind.PULL)) {
         return "immune";
      }
      return null;
   }

   /**
    * Begin a hold (the GRAB move is already admitted on Thor's timeline).
    * False when the shared control refuses the target.
    */
   static boolean start(ServerPlayer thor, ThorState state, LivingEntity target) {
      if (refusal(thor, target) != null) {
         return false;
      }

      ResistKey key = target instanceof ServerPlayer ? ResistKey.pick(thor.getRandom().nextInt()) : null;
      Session session = new Session(thor, target, new GrabFlow(key));
      long expiry = thor.level().getGameTime() + CONTROL_LEASE;
      if (!ControlManager.get(session.level).tryAcquire(target, thor.getUUID(), session.effectId, ControlKind.PULL, expiry)) {
         return false;
      }

      if (!ThorBridge.lockScene(state, ThorScenes.OWNER)) {
         ControlManager.get(session.level).release(target.getUUID(), session.effectId, ReleaseReason.INTERRUPTED);
         return false;
      }

      ThorBridge.closeGrabWindow(state);
      ThorScenes.stopFlight(thor);
      ThorScenes.stopFlight(target);
      if (target instanceof Mob mob) {
         mob.getNavigation().stop();
      }

      SESSIONS.put(session.thor, session);
      SceneFx.sound(thor, thor.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
      sync(thor, session);
      return true;
   }

   /** Thor's mouse press while holding: the one action. */
   static void choose(ServerPlayer thor, Session session, GrabAction action) {
      if (session.flow.commit(action)) {
         SceneFx.sound(thor, thor.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.7F);
         sync(thor, session);
      }
   }

   /** The victim's resist press (validated: right key, resistible, rate-capped in the flow). */
   static void resist(ServerPlayer victim, ResistKey key) {
      Session session = victimOf(victim.getUUID());
      if (session != null && session.flow.resist(key, ThorSceneConfig.resistPerPress, ThorSceneConfig.resistMaxPerSecond)) {
         ServerPlayer thor = session.level.getServer().getPlayerList().getPlayer(session.thor);
         if (thor != null) {
            sync(thor, session);
         }
      }
   }

   static void tick(net.minecraft.server.MinecraftServer server) {
      for (Session session : new ArrayList<>(SESSIONS.values())) {
         ServerPlayer thor = server.getPlayerList().getPlayer(session.thor);
         Entity entity = session.level.getEntity(session.victim);
         LivingEntity victim = entity instanceof LivingEntity living ? living : null;
         ThorState state = thor == null ? null : ThorState.of(thor);
         if (thor == null || state == null || !thor.isAlive() || thor.level() != session.level) {
            end(thor, session, ReleaseReason.CASTER_DEATH);
            continue;
         }

         if (!session.released && (victim == null || !victim.isAlive() || victim.level() != session.level
            || victim.distanceTo(thor) > ThorSceneConfig.holdBreakDistance)) {
            end(thor, session, ReleaseReason.TARGET_LOST);
            continue;
         }

         GrabFlow.Stage before = session.flow.stage();
         GrabFlow.Outcome outcome = session.flow.tick(ThorSceneConfig.resistDecay, ThorSceneConfig.grabRecoverTicks);
         switch (outcome) {
            case ESCAPED -> {
               escape(thor, session, victim);
               continue;
            }
            case PUNCH -> SceneFx.barragePunch(thor, victim, session.flow.punch());
            case RELEASE -> release(thor, session, victim);
            case END -> {
               end(thor, session, ReleaseReason.NORMAL_END);
               continue;
            }
            case NONE -> {
            }
         }

         if (holding(session) && victim != null) {
            if (session.flow.ticks() % 20 == 0) {
               ControlManager.get(session.level).refresh(session.victim, session.thor, session.effectId, ControlKind.PULL,
                  session.level.getGameTime() + CONTROL_LEASE);
            }
            hold(thor, session, victim);
         }

         boolean changed = before != session.flow.stage();
         if (changed || session.flow.stage() == GrabFlow.Stage.HOLD && session.flow.ticks() % 2 == 0) {
            sync(thor, session);
         }
      }
   }

   /** The authoritative anchor of a held body (also the shared held-target position on the server). */
   static Vec3 anchor(ServerPlayer thor, Session session, LivingEntity victim) {
      return SceneFrame.world(thor.position(), thor.getYRot(),
         GrabPose.at(session.flow.stage(), session.flow.action(), session.flow.ticks(), victim.getBbWidth(), victim.getBbHeight()));
   }

   private static void hold(ServerPlayer thor, Session session, LivingEntity victim) {
      Vec3 at = anchor(thor, session, victim);
      victim.setPos(at.x, at.y, at.z);
      victim.setDeltaMovement(Vec3.ZERO);
      victim.resetFallDistance();
      victim.hurtMarked = true;
      if (victim instanceof Mob mob) {
         mob.getNavigation().stop();
      }
   }

   private static void escape(ServerPlayer thor, Session session, @Nullable LivingEntity victim) {
      end(thor, session, ReleaseReason.INTERRUPTED);
      if (victim != null && HeroRegistry.allowsImpulse(victim)) {
         Vec3 away = SceneFrame.forward(thor.getYRot()).scale(0.7).add(0, 0.3, 0);
         SceneFx.move(victim, away);
      }
      SceneFx.sound(thor, thor.position(), SoundEvents.SHIELD_BLOCK, 0.9F, 0.8F);
   }

   /** The action's release beat: slam impact, throw launch or barrage finisher. The victim leaves the hand. */
   private static void release(ServerPlayer thor, Session session, @Nullable LivingEntity victim) {
      if (victim == null) {
         return;
      }

      GrabAction action = session.flow.action();
      Vec3 last = SceneFrame.world(thor.position(), thor.getYRot(),
         GrabPose.at(GrabFlow.Stage.ACTION, action, action == null ? 0 : action.releaseTick(), victim.getBbWidth(), victim.getBbHeight()));
      releaseControl(session, victim, ReleaseReason.NORMAL_END);
      if (action == null) {
         return;
      }

      switch (action) {
         case SLAM -> SceneFx.slam(thor, victim, last);
         case THROW -> {
            float pitch = Math.max(-30.0F, Math.min(20.0F, thor.getXRot()));
            Vec3 dir = Vec3.directionFromRotation(pitch, thor.getYRot());
            victim.setPos(last.x, last.y, last.z);
            ThrownBodies.launch(thor, victim, dir.scale(ThorSceneConfig.throwSpeed).add(0, 0.25, 0), ThorSceneConfig.throwImpactDamage);
         }
         case BARRAGE -> {
            SceneFx.barrageFinisher(thor, victim);
            Vec3 dir = SceneFrame.forward(thor.getYRot());
            ThrownBodies.launch(thor, victim, dir.scale(ThorSceneConfig.barrageKnockSpeed).add(0, 0.35, 0), ThorSceneConfig.throwImpactDamage * 0.5F);
         }
      }
   }

   private static void releaseControl(Session session, @Nullable LivingEntity victim, ReleaseReason reason) {
      if (session.released) {
         return;
      }

      session.released = true;
      ControlManager.get(session.level).release(session.victim, session.effectId, reason);
      if (victim instanceof ServerPlayer player) {
         // Hand prediction back: one teleport handshake to where the body is now.
         player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
      }
   }

   /** End the grab: control and lock released, the GRAB move stopped, the clients told. */
   static void end(@Nullable ServerPlayer thor, Session session, ReleaseReason reason) {
      if (SESSIONS.remove(session.thor) == null) {
         return;
      }

      Entity entity = session.level.getEntity(session.victim);
      releaseControl(session, entity instanceof LivingEntity living ? living : null, reason);
      session.flow.abort();
      if (thor != null) {
         ThorState state = ThorState.of(thor);
         if (state != null) {
            ThorBridge.unlockScene(state, ThorScenes.OWNER);
            ThorScenes.stopMove(thor, state, ThorScenes.GRAB);
         }
         ThorScenes.send(thor, entity, SceneSync.end(SceneSync.Kind.GRAB, session.thorId, session.victimId));
      }
   }

   static void endFor(ServerPlayer thor) {
      Session session = SESSIONS.get(thor.getUUID());
      if (session != null) {
         end(thor, session, ReleaseReason.INTERRUPTED);
      }
   }

   static void endVictim(UUID victim, ReleaseReason reason) {
      Session session = victimOf(victim);
      if (session != null) {
         end(session.level.getServer().getPlayerList().getPlayer(session.thor), session, reason);
      }
   }

   static void clear() {
      SESSIONS.clear();
   }

   private static void sync(ServerPlayer thor, Session session) {
      ThorScenes.send(thor, session.level.getEntity(session.victim), SceneSync.grab(session.thorId, session.victimId, session.flow));
   }
}

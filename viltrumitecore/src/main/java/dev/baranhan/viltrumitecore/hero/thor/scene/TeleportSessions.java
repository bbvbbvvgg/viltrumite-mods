package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorFeedback;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Server half of the V point selection (decisions 14, 29, 41-47, 71, 72, 120): the scene
 * lock and camera lease are held, Thor stays put (held at his spot by the
 * server, no NoGravity flag that could stick to a save, C-M5), the world
 * goes on and he can be hurt (C-M4: invulnerability is not in the approved
 * canon; OPEN(owner)). The selection is bounded (teleportLimitTicks). The
 * client's pick is only an intent: the server judges it again (loaded,
 * solid top, SafeSpot fit and border, no fluid and no hazard at the feet,
 * under a ceiling dimension's roof, build height, inside the camera's area,
 * the first thing the camera ray meets with fluids counted, C-M3).
 * Server main thread.
 */
final class TeleportSessions {
   static final class Session {
      final UUID thor;
      final int thorId;
      final ServerLevel level;
      final Vec3 origin;
      final Vec3 eye;
      final Vec3 camera;
      int tick;

      Session(ServerPlayer thor, Vec3 camera) {
         this.thor = thor.getUUID();
         this.thorId = thor.getId();
         this.level = thor.serverLevel();
         this.origin = thor.position();
         this.eye = thor.getEyePosition();
         this.camera = camera;
      }
   }

   private static final Map<UUID, Session> SESSIONS = new HashMap<>();

   private TeleportSessions() {
   }

   @Nullable
   static Session of(UUID thor) {
      return SESSIONS.get(thor);
   }

   static TeleportRules.Refusal gate(ServerPlayer thor, ThorState state) {
      return TeleportRules.gate(ThorBridge.hammerHeld(thor), ThorScenes.fullFlight(thor), ThorBridge.sceneLocked(state));
   }

   /** Begin (TELEPORT_SELECT admitted): false with feedback when refused. */
   static boolean start(ServerPlayer thor, ThorState state) {
      TeleportRules.Refusal refusal = gate(thor, state);
      if (refusal != TeleportRules.Refusal.NONE) {
         ThorFeedback.refuse(thor, Component.translatable("hud.viltrumitecore.thor.teleport.refused." + refusal.name().toLowerCase(java.util.Locale.ROOT)));
         return false;
      }

      if (!ThorBridge.lockScene(state, ThorScenes.OWNER)) {
         return false;
      }

      ThorBridge.acquireCamera(state, ThorScenes.OWNER);
      ThorScenes.stopFlight(thor);
      // M2: no teleport out of a saddle (the vehicle would pull the rider back).
      thor.stopRiding();
      Session session = new Session(thor, TeleportRules.camera(thor.getEyePosition(), ThorSceneConfig.teleportCameraHeight, thor.level().getMaxBuildHeight()));
      thor.setDeltaMovement(Vec3.ZERO);
      SESSIONS.put(session.thor, session);
      SceneFx.sound(thor, thor.position(), SoundEvents.BEACON_ACTIVATE, 0.6F, 1.6F);
      sync(thor, session);
      return true;
   }

   static void tick(MinecraftServer server) {
      for (Session session : new ArrayList<>(SESSIONS.values())) {
         ServerPlayer thor = server.getPlayerList().getPlayer(session.thor);
         if (thor == null || !thor.isAlive() || thor.level() != session.level || ThorState.of(thor) == null) {
            end(thor, session);
            continue;
         }

         session.tick++;
         if (thor.position().distanceToSqr(session.origin) > 0.25) {
            thor.teleportTo(session.origin.x, session.origin.y, session.origin.z);
         }
         thor.setDeltaMovement(Vec3.ZERO);
         thor.resetFallDistance();
         if (TeleportRules.expired(session.tick, ThorSceneConfig.teleportLimitTicks)) {
            thor.displayClientMessage(Component.translatable("hud.viltrumitecore.thor.teleport.timeout"), true);
            end(thor, session);
         } else if (session.tick % 20 == 0) {
            sync(thor, session);
         }
      }
   }

   static void cancel(ServerPlayer thor) {
      Session session = SESSIONS.get(thor.getUUID());
      if (session != null) {
         end(thor, session);
      }
   }

   /** The client picked a block; judged here again. */
   static void confirm(ServerPlayer thor, BlockPos pos) {
      Session session = SESSIONS.get(thor.getUUID());
      if (session == null) {
         return;
      }

      if (!ThorBridge.hammerHeld(thor)) {
         // Decision 42: Mjölnir must still be in the hand when the point is confirmed.
         end(thor, session);
         ThorFeedback.refuse(thor, Component.translatable("hud.viltrumitecore.thor.teleport.refused.no_hammer"));
         return;
      }

      Vec3 feet = judge(thor, session, pos);
      if (feet == null) {
         ThorFeedback.refuse(thor, Component.translatable("hud.viltrumitecore.thor.teleport.unsafe"));
         return;
      }

      Vec3 from = thor.position();
      end(thor, session);
      thor.stopRiding();
      thor.teleportTo(feet.x, feet.y, feet.z);
      thor.resetFallDistance();
      HeroFx.flash(thor, from.add(0, 1, 0), 0.8F);
      HeroFx.flash(thor, feet.add(0, 1, 0), 0.8F);
      SceneFx.sound(thor, from, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 1.4F);
      SceneFx.sound(thor, feet, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.0F);
   }

   /** Feet position on the picked block, or null when the point fails a rule. */
   @Nullable
   static Vec3 judge(ServerPlayer thor, Session session, BlockPos pos) {
      ServerLevel level = session.level;
      if (!level.isLoaded(pos) || !level.isLoaded(pos.above(2))) {
         return null;
      }

      VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
      boolean solid = !shape.isEmpty() && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
      Vec3 feet = new Vec3(pos.getX() + 0.5, pos.getY() + (shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y)), pos.getZ() + 0.5);
      boolean height = TeleportRules.heightAllowed(feet.y, thor.getBbHeight(), level.getMinBuildHeight(), level.getMaxBuildHeight(),
         level.dimensionType().hasCeiling(), level.dimensionType().logicalHeight());
      boolean safe = solid && height && SafeSpot.ok(feet, new SafeSpot.LevelProbe(level, thor)) && !hazard(level, pos);
      boolean inView = TeleportRules.inView(session.camera, feet, ThorSceneConfig.teleportMaxRadius);
      boolean visible = inView && visible(level, session.camera, pos, feet);
      return TeleportRules.judge(solid, safe, inView, visible) == TeleportRules.Target.OK ? feet : null;
   }

   /** The picked block is the first thing the camera ray meets (blocks the camera sits in are skipped, like the client). */
   static boolean visible(BlockGetter level, Vec3 camera, BlockPos pos, Vec3 feet) {
      Vec3 target = new Vec3(feet.x, feet.y - 0.01, feet.z);
      Vec3 start = firstAir(level, camera, target);
      if (start == null) {
         return false;
      }

      // M3: fluids count: a pick at the bottom of a lava or water lake is not what the camera sees (the surface is).
      BlockHitResult hit = level.clip(new ClipContext(start, target.add(target.subtract(start).normalize().scale(0.2)), ClipContext.Block.COLLIDER,
         ClipContext.Fluid.ANY, null));
      return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
   }

   /** The first point along the ray outside any collision shape (the camera may sit inside rock), or null. */
   @Nullable
   static Vec3 firstAir(BlockGetter level, Vec3 from, Vec3 to) {
      Vec3 dir = to.subtract(from);
      double length = dir.length();
      if (length < 1.0E-6) {
         return null;
      }

      dir = dir.scale(1.0 / length);
      for (double d = 0; d < length; d += 0.5) {
         Vec3 at = from.add(dir.scale(d));
         BlockPos pos = BlockPos.containing(at);
         if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return at;
         }
      }
      return null;
   }

   /**
    * M3: fluid in the feet or head block, or a hurting block under / at the
    * feet (lava, fire, magma, cactus, berry bush, powder snow, ...): the vanilla
    * walk evaluator's damage types.
    */
   static boolean hazard(ServerLevel level, BlockPos ground) {
      BlockPos feet = ground.above();
      if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) {
         return true;
      }

      net.minecraft.world.level.pathfinder.BlockPathTypes type = net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getBlockPathTypeStatic(level,
         feet.mutable());
      return TeleportRules.hazardous(type);
   }

   /** Every selection ends (server stopping, before the save). */
   static void endAll(MinecraftServer server) {
      for (Session session : new ArrayList<>(SESSIONS.values())) {
         end(server.getPlayerList().getPlayer(session.thor), session);
      }
   }

   private static void end(@Nullable ServerPlayer thor, Session session) {
      if (SESSIONS.remove(session.thor) == null) {
         return;
      }

      if (thor != null) {
         ThorState state = ThorState.of(thor);
         if (state != null) {
            ThorBridge.unlockScene(state, ThorScenes.OWNER);
            ThorBridge.releaseCamera(state, ThorScenes.OWNER);
            ThorScenes.stopMove(thor, state, ThorScenes.TELEPORT);
         }
         sendOwn(thor, SceneSync.end(SceneSync.Kind.TELEPORT, session.thorId, -1));
      }
   }

   static void endFor(ServerPlayer thor) {
      Session session = SESSIONS.get(thor.getUUID());
      if (session != null) {
         end(thor, session);
      }
   }

   private static void sendOwn(ServerPlayer thor, SceneSync sync) {
      dev.baranhan.viltrumitecore.network.CoreMessages.sendToPlayer(new dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneS2CPacket(sync), thor);
   }

   static void clear() {
      SESSIONS.clear();
   }

   /** NIT-5: the selection camera is Thor's own; only his client gets it (no camera position leak to trackers). */
   private static void sync(ServerPlayer thor, Session session) {
      sendOwn(thor, SceneSync.teleport(session.thorId, session.eye, session.camera, session.tick, ThorSceneConfig.teleportRiseTicks,
         ThorSceneConfig.teleportLimitTicks));
   }
}

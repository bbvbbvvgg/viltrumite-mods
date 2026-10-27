package dev.baranhan.viltrumitecore.hero.thor.hammer;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.HammerAccess;
import dev.baranhan.viltrumitecore.hero.thor.bridge.HammerView;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.OpenRaceScreenS2CPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Session A: the one world Mjölnir. The single writer of the saved hammer
 * (MjolnirSavedData) and of every Thor's projected hammer state; the
 * physical MjolnirEntity is only its body while the chunk is loaded (no
 * save, UUID = the logical id, so a second body cannot exist). Server main
 * thread only. Everyone else reads through {@link #view} (installed as the
 * bridge HammerAccess) or {@link #phase}.
 */
public final class MjolnirWorld {
   public static final String OWNER = "A";
   public static final String SLOT_TAKEN = "hud.viltrumitecore.thor.slot_taken";
   public static final String SLOT_TAKEN_LOGIN = "hud.viltrumitecore.thor.slot_taken_login";
   public static final String NOT_OWNER = "hud.viltrumitecore.thor.not_owner";
   public static final String NO_HAMMER = "hud.viltrumitecore.thor.no_hammer";
   public static final String RECALL_WAIT = "hud.viltrumitecore.thor.recall_wait";
   static final String OVERWORLD = "minecraft:overworld";
   private static final Logger LOGGER = LogUtils.getLogger();

   /** Outcome of a recall press. */
   public enum Recall {
      STARTED,
      /** Asked while the hammer cannot leave yet (first fall / death impact in the air): it comes once it lands. */
      PENDING,
      /** Already in the hand or on its way. */
      ALREADY,
      NOT_OWNER,
      NO_HAMMER
   }

   private MjolnirWorld() {
   }

   // --- store ---

   @Nullable
   static MinecraftServer server() {
      return ServerLifecycleHooks.getCurrentServer();
   }

   static MjolnirRecord record(MinecraftServer server) {
      return MjolnirSavedData.get(server).record;
   }

   static void dirty(MinecraftServer server) {
      MjolnirSavedData.get(server).setDirty();
   }

   /** Rich world phase for readers (B / C, HUD, tests on a server); NOT_CREATED without a server. */
   public static MjolnirPhase phase() {
      MinecraftServer server = server();
      return server == null ? MjolnirPhase.NOT_CREATED : record(server).phase();
   }

   /** Copy of the saved record (dev command, readers). */
   public static MjolnirRecord snapshot(MinecraftServer server) {
      return MjolnirRecord.load(record(server).save());
   }

   /** The one phase writer: validated by the state machine, saved, announced (HAMMER_STATE_CHANGED). */
   static boolean go(MinecraftServer server, MjolnirRecord record, MjolnirPhase next) {
      MjolnirPhase before = record.phase;
      if (!record.go(next)) {
         LOGGER.debug("Mjolnir: refused transition {} -> {}", before, next);
         return false;
      }

      dirty(server);
      post(ThorEventType.HAMMER_STATE_CHANGED, record, null, next == MjolnirPhase.IN_HAND);
      return true;
   }

   static void post(ThorEventType type, MjolnirRecord record, @Nullable ThorMove move, boolean inHand) {
      UUID actor = record.activeOwner != null ? record.activeOwner : record.lastOwner != null ? record.lastOwner : record.logicalId;
      if (actor != null) {
         ThorEvents.post(new ThorEvent(ThorEvents.nextId(), type, actor, null, move, 0, inHand, 0.0F, true));
      }
   }

   // --- dimensions / bodies ---

   static String dim(Level level) {
      return level.dimension().location().toString();
   }

   @Nullable
   static ResourceKey<Level> key(@Nullable String dimension) {
      ResourceLocation id = dimension == null ? null : ResourceLocation.tryParse(dimension);
      return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
   }

   @Nullable
   static ServerLevel level(MinecraftServer server, MjolnirRecord record) {
      ResourceKey<Level> key = key(record.dimension);
      ServerLevel level = key == null ? null : server.getLevel(key);
      return level;
   }

   static Vec3 position(MjolnirRecord record) {
      return new Vec3(record.x, record.y, record.z);
   }

   /** The physical body, when one is loaded. */
   @Nullable
   public static MjolnirEntity entity(MinecraftServer server) {
      MjolnirRecord record = record(server);
      return entity(server, record);
   }

   @Nullable
   static MjolnirEntity entity(MinecraftServer server, MjolnirRecord record) {
      ServerLevel level = level(server, record);
      if (level == null || record.logicalId == null) {
         return null;
      }

      return level.getEntity(record.logicalId) instanceof MjolnirEntity hammer && hammer.isAlive() ? hammer : null;
   }

   /** Is this body the logical hammer as saved now (else it removes itself). */
   static boolean owns(MjolnirEntity hammer) {
      MinecraftServer server = hammer.getServer();
      if (server == null) {
         return false;
      }

      MjolnirRecord record = record(server);
      return record.phase.physical() && hammer.getUUID().equals(record.logicalId) && dim(hammer.level()).equals(record.dimension);
   }

   static boolean ticking(ServerLevel level, Vec3 at) {
      return level.isPositionEntityTicking(BlockPos.containing(at));
   }

   /** Spawn the body where the record says, if that chunk ticks entities; returns the body or null. */
   @Nullable
   static MjolnirEntity materialize(MinecraftServer server, MjolnirRecord record) {
      MjolnirEntity existing = entity(server, record);
      if (existing != null) {
         return existing;
      }

      ServerLevel level = level(server, record);
      if (level == null || record.logicalId == null || !record.phase.physical() || !ticking(level, position(record))) {
         return null;
      }

      MjolnirEntity hammer = new MjolnirEntity(ViltrumiteEntities.MJOLNIR.get(), level);
      hammer.setUUID(record.logicalId);
      hammer.moveTo(record.x, record.y, record.z, record.yaw, record.pitch);
      hammer.setDeltaMovement(record.vx, record.vy, record.vz);
      hammer.syncFrom(record);
      return level.addFreshEntity(hammer) ? hammer : null;
   }

   /** Remove the body (it is spawned again where the record now says). */
   static void unload(MinecraftServer server, MjolnirRecord record) {
      if (record.logicalId == null) {
         return;
      }

      for (ServerLevel level : server.getAllLevels()) {
         if (level.getEntity(record.logicalId) instanceof MjolnirEntity hammer) {
            hammer.discard();
         }
      }
   }

   /** The body moved: the record follows it. */
   static void track(MjolnirEntity hammer) {
      MinecraftServer server = hammer.getServer();
      if (server == null) {
         return;
      }

      MjolnirRecord record = record(server);
      Vec3 velocity = hammer.getDeltaMovement();
      record.place(dim(hammer.level()), hammer.getX(), hammer.getY(), hammer.getZ());
      record.velocity(velocity.x, velocity.y, velocity.z);
      record.yaw = hammer.getYRot();
      record.pitch = hammer.getXRot();
      dirty(server);
   }

   // --- owners ---

   /** The active Thor if online and still Thor, else null (an offline owner never blocks the slot). */
   @Nullable
   static ServerPlayer owner(MinecraftServer server, MjolnirRecord record) {
      if (record.activeOwner == null) {
         return null;
      }

      ServerPlayer player = server.getPlayerList().getPlayer(record.activeOwner);
      return player != null && ThorState.of(player) != null ? player : null;
   }

   public static boolean isOwner(Player player) {
      MinecraftServer server = player.getServer();
      return server != null && player.getUUID().equals(record(server).activeOwner);
   }

   /** One active Thor: free slot, the holder himself, or a holder who is gone. */
   public static boolean admits(ServerPlayer player) {
      MjolnirRecord record = record(player.server);
      return record.activeOwner == null || record.activeOwner.equals(player.getUUID()) || owner(player.server, record) == null;
   }

   private static void claim(MinecraftServer server, MjolnirRecord record, ServerPlayer player) {
      UUID id = player.getUUID();
      if (!id.equals(record.activeOwner) || !id.equals(record.lastOwner)) {
         record.activeOwner = id;
         record.lastOwner = id;
         dirty(server);
      }
   }

   /** The previous holder is gone: an attached or homing hammer falls first (№3.2: no instant re-aim). */
   private static void loosen(MinecraftServer server, MjolnirRecord record) {
      if (record.phase == MjolnirPhase.IN_HAND || record.phase.homing()) {
         record.velocity(0.0, -0.4, 0.0);
         if (go(server, record, MjolnirPhase.FALLING)) {
            record.lying = false;
            MjolnirEntity hammer = entity(server, record);
            if (hammer == null) {
               materialize(server, record);
            }
         }
      }
   }

   /**
    * A player became Thor (the transformation follows): the slot is his;
    * a never-created hammer is created once in his hand and the first fall
    * is cancelled for good (№191); an existing one is called by the scene.
    */
   public static void onEnter(ServerPlayer player, ThorState state) {
      MinecraftServer server = player.server;
      MjolnirRecord record = record(server);
      if (!player.getUUID().equals(record.activeOwner)) {
         loosen(server, record);
      }

      claim(server, record, player);
      if (!record.exists()) {
         createInHand(server, record, player, state);
      }
   }

   private static void createInHand(MinecraftServer server, MjolnirRecord record, ServerPlayer player, ThorState state) {
      if (!record.create(UUID.randomUUID(), MjolnirPhase.IN_HAND)) {
         return;
      }

      record.fallCancelled = !record.fallDone;
      record.place(dim(player.level()), player.getX(), player.getY(), player.getZ());
      dirty(server);
      state.hammer = HammerState.IN_HAND;
      state.hammerDetached = false;
      post(ThorEventType.HAMMER_STATE_CHANGED, record, null, true);
   }

   /** Login: arm the first fall; a returning Thor takes a free slot, or yields to the active one. */
   public static void onLogin(ServerPlayer player) {
      MinecraftServer server = player.server;
      MjolnirRecord record = record(server);
      MjolnirFirstFall.arm(server, record);
      ThorState state = ThorState.of(player);
      if (state == null) {
         return;
      }

      if (!admits(player)) {
         demote(player);
         return;
      }

      if (!player.getUUID().equals(record.activeOwner)) {
         loosen(server, record);
      }

      claim(server, record, player);
      if (!record.exists()) {
         createInHand(server, record, player, state);
      }

      project(player, state);
   }

   /** A former Thor logged in while another Thor holds the slot: he is not Thor any more and may choose again. */
   private static void demote(ServerPlayer player) {
      HeroRegistry.changeHero(player, HeroId.HUMAN);
      if (player instanceof ViltrumiteCorePlayer corePlayer) {
         corePlayer.setChosenRace(false);
      }

      CoreMessages.sendToPlayer(new OpenRaceScreenS2CPacket(), player);
      player.displayClientMessage(Component.translatable(SLOT_TAKEN_LOGIN), false);
   }

   /** Death / logout / hero change of the active Thor (§3.5). */
   public static void onCleanup(ServerPlayer player, ThorState state, CleanupReason reason) {
      MinecraftServer server = player.server;
      MjolnirRecord record = record(server);
      if (!player.getUUID().equals(record.activeOwner)) {
         return;
      }

      ServerLevel level = player.serverLevel();
      Vec3 at = player.position().add(0.0, 1.0, 0.0);
      switch (reason) {
         case DEATH, ERASED -> {
            if (record.phase == MjolnirPhase.IN_HAND) {
               // PLACEHOLDER for the [ОТКРЫТО] death with the hammer in the hand (MjolnirTuning.DEATH_IN_HAND_DROPS).
               if (MjolnirTuning.DEATH_IN_HAND_DROPS) {
                  release(server, record, level, at, -0.2);
               }
            } else if (record.phase == MjolnirPhase.RETURNING) {
               // №169: on to the fixed death point, then the strike.
               record.death = new double[]{player.getX(), player.getY(), player.getZ()};
               record.deathDimension = dim(level);
               go(server, record, MjolnirPhase.DEATH_IMPACT);
            }

            // The dead / erased Thor comes back as Thor (№208): the slot stays his, the hammer waits for his recall.
         }
         case DISCONNECT -> {
            // Falls heavily where he left.
            if (record.phase == MjolnirPhase.IN_HAND) {
               release(server, record, level, at, -0.6);
            } else {
               loosen(server, record);
            }

            record.activeOwner = null;
         }
         case HERO_CHANGE -> {
            // Falls briefly and heavily nearby (№207).
            if (record.phase == MjolnirPhase.IN_HAND) {
               Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
               Vec3 near = look.lengthSqr() < 1.0E-6 ? at : at.add(look.normalize().scale(MjolnirTuning.DROP_NEAR));
               release(server, record, level, near, -0.8);
            } else {
               loosen(server, record);
            }

            record.activeOwner = null;
         }
      }

      dirty(server);
      state.hammer = HammerState.HOLSTERED;
      state.hammerDetached = record.exists();
   }

   /** The attached hammer leaves the hand and falls from {@code at}. */
   private static void release(MinecraftServer server, MjolnirRecord record, ServerLevel level, Vec3 at, double down) {
      record.place(dim(level), at.x, at.y, at.z);
      record.velocity(0.0, down, 0.0);
      record.lying = false;
      if (go(server, record, MjolnirPhase.FALLING)) {
         materialize(server, record);
      }
   }

   /** The attached hammer goes along with its Thor. */
   public static void onDimensionChange(ServerPlayer player, ThorState state) {
      MinecraftServer server = player.server;
      MjolnirRecord record = record(server);
      if (player.getUUID().equals(record.activeOwner) && record.phase == MjolnirPhase.IN_HAND) {
         record.place(dim(player.level()), player.getX(), player.getY(), player.getZ());
         dirty(server);
      }

      project(player, state);
   }

   /** Every tick: the Thor's synced hammer state is a projection of the world hammer. */
   public static void project(ServerPlayer player, ThorState state) {
      MinecraftServer server = player.server;
      MjolnirRecord record = record(server);
      if (!player.getUUID().equals(record.activeOwner)) {
         state.hammer = HammerState.HOLSTERED;
         state.hammerDetached = record.exists();
         return;
      }

      switch (record.phase) {
         case NOT_CREATED -> {
            state.hammer = HammerState.HOLSTERED;
            state.hammerDetached = false;
         }
         case IN_HAND -> {
            state.hammer = state.hammer == HammerState.HOLSTERED ? HammerState.HOLSTERED : HammerState.IN_HAND;
            state.hammerDetached = false;
            if (player.distanceToSqr(record.x, record.y, record.z) > 1.0 || !dim(player.level()).equals(record.dimension)) {
               record.place(dim(player.level()), player.getX(), player.getY(), player.getZ());
               dirty(server);
            }
         }
         default -> {
            state.hammer = record.phase.personal();
            state.hammerDetached = record.phase.detached();
         }
      }
   }

   /** Bridge HammerAccess (installed by MjolnirModule): read-only copy for B / C. */
   public static HammerView view(UUID thor, @Nullable ThorState state) {
      MinecraftServer server = server();
      if (server == null || !server.isSameThread()) {
         return HammerAccess.FOUNDATION.view(thor, state);
      }

      MjolnirRecord record = record(server);
      boolean owner = thor.equals(record.activeOwner);
      HammerState phase;
      if (owner && record.phase == MjolnirPhase.IN_HAND) {
         phase = state != null && state.hammer == HammerState.HOLSTERED ? HammerState.HOLSTERED : HammerState.IN_HAND;
      } else {
         phase = record.phase == MjolnirPhase.IN_HAND ? HammerState.HOLSTERED : record.phase.personal();
      }

      boolean inHand = owner && record.phase == MjolnirPhase.IN_HAND && phase == HammerState.IN_HAND;
      return new HammerView(record.logicalId, record.activeOwner, phase, inHand, record.exists() ? key(record.dimension) : null,
         record.exists() ? position(record) : null);
   }

   // --- throw / recall / catch / pick up ---

   /** Mjölnir leaves the active Thor's hand (HAMMER_THROW release). */
   public static boolean launch(ServerPlayer thor, ThorState state, boolean charged) {
      MinecraftServer server = thor.server;
      MjolnirRecord record = record(server);
      if (!thor.getUUID().equals(record.activeOwner) || record.phase != MjolnirPhase.IN_HAND) {
         return false;
      }

      ServerLevel level = thor.serverLevel();
      Vec3 look = thor.getLookAngle();
      Vec3 eye = thor.getEyePosition();
      Vec3 start = MjolnirFlight.palm(eye, thor.getYRot()).add(look.scale(0.6));
      BlockHitResult blocked = level.clip(new ClipContext(eye, start, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, thor));
      if (blocked.getType() != HitResult.Type.MISS) {
         start = blocked.getLocation().subtract(look.scale(0.3));
      }

      double speed = charged ? MjolnirTuning.THROW_SPEED_CHARGED : MjolnirTuning.THROW_SPEED;
      record.place(dim(level), start.x, start.y, start.z);
      record.velocity(look.x * speed, look.y * speed, look.z * speed);
      record.originX = eye.x;
      record.originY = eye.y;
      record.originZ = eye.z;
      record.charged = charged;
      record.lying = false;
      record.recallPending = false;
      if (!go(server, record, MjolnirPhase.THROWN)) {
         return false;
      }

      state.hammer = HammerState.THROWN;
      state.hammerDetached = false;
      MjolnirEntity hammer = materialize(server, record);
      if (hammer != null) {
         hammer.beginThrow(thor, eye, look, charged);
      }

      thor.swing(InteractionHand.MAIN_HAND, true);
      level.playSound(null, thor.getX(), thor.getEyeY(), thor.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.2F, charged ? 0.6F : 0.8F);
      post(ThorEventType.THROW, record, ThorMove.HAMMER_THROW, false);
      return true;
   }

   /** MMB with the hammer away: call it from any distance, chunk or dimension (§5.1). */
   public static Recall recall(ServerPlayer thor) {
      MinecraftServer server = thor.server;
      MjolnirRecord record = record(server);
      if (!record.exists()) {
         return Recall.NO_HAMMER;
      } else if (!thor.getUUID().equals(record.activeOwner)) {
         return Recall.NOT_OWNER;
      }

      switch (record.phase) {
         case IN_HAND, RETURNING, DIMENSION_TRANSFER -> {
            return Recall.ALREADY;
         }
         case WORLD_FALLING, DEATH_IMPACT, VOID_RECOVERY -> {
            record.recallPending = true;
            dirty(server);
            return Recall.PENDING;
         }
         default -> {
         }
      }

      MjolnirEntity hammer = entity(server, record);
      boolean ripped = record.phase == MjolnirPhase.RESTING && record.embedded;
      MjolnirLift.rip(server, position(record));
      record.recallPending = false;
      record.lying = false;
      record.charged = false;
      if (!dim(thor.level()).equals(record.dimension)) {
         transfer(server, record, thor);
      } else if (go(server, record, MjolnirPhase.RETURNING)) {
         if (hammer != null) {
            hammer.beginReturn(ripped);
         }
      } else {
         return Recall.ALREADY;
      }

      post(ThorEventType.RECALL, record, ThorMove.RECALL, false);
      return Recall.STARTED;
   }

   /**
    * №205: the same hammer hops to the Thor's dimension without a scene and
    * appears in the air behind him, then flies in physically.
    */
   static void transfer(MinecraftServer server, MjolnirRecord record, ServerPlayer thor) {
      if (!go(server, record, MjolnirPhase.DIMENSION_TRANSFER)) {
         return;
      }

      unload(server, record);
      ServerLevel level = thor.serverLevel();
      Vec3 at = MjolnirFlight.transferPoint(thor.position(), thor.getYRot());
      double y = Mth.clamp(at.y, level.getMinBuildHeight() + 2.0, level.getMaxBuildHeight() - 2.0);
      record.place(dim(level), at.x, y, at.z);
      record.velocity(0.0, 0.0, 0.0);
      go(server, record, MjolnirPhase.RETURNING);
      MjolnirEntity hammer = materialize(server, record);
      if (hammer != null) {
         hammer.beginReturn(false);
      }
   }

   /** The handle reached the palm (№155–157): back in the hand, a brief slow-down, even mid-knockback. */
   static void caught(MinecraftServer server, MjolnirRecord record, ServerPlayer thor, MjolnirEntity hammer) {
      if (!go(server, record, MjolnirPhase.IN_HAND)) {
         return;
      }

      record.place(dim(thor.level()), thor.getX(), thor.getY(), thor.getZ());
      record.velocity(0.0, 0.0, 0.0);
      hammer.discard();
      ThorState state = ThorState.of(thor);
      if (state != null) {
         state.hammer = HammerState.IN_HAND;
         state.hammerDetached = false;
      }

      thor.setDeltaMovement(thor.getDeltaMovement().scale(MjolnirTuning.CATCH_SLOW));
      thor.hurtMarked = true;
      Vec3 palm = MjolnirFlight.palm(thor.getEyePosition(), thor.getYRot());
      ServerLevel level = thor.serverLevel();
      level.playSound(null, palm.x, palm.y, palm.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.6F, 0.6F);
      level.playSound(null, palm.x, palm.y, palm.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.5F);
      MjolnirFx.catchFlash(level, palm);
      post(ThorEventType.CATCH, record, ThorMove.RECALL, true);
   }

   /** RMB on the resting model by the active Thor: he simply picks it up (§3.4). */
   static boolean pickUp(ServerPlayer thor, MjolnirEntity hammer) {
      MinecraftServer server = thor.server;
      MjolnirRecord record = record(server);
      if (!thor.getUUID().equals(record.activeOwner) || record.phase != MjolnirPhase.RESTING || !hammer.getUUID().equals(record.logicalId)) {
         return false;
      }

      MjolnirLift.rip(server, hammer.position());
      if (!go(server, record, MjolnirPhase.IN_HAND)) {
         return false;
      }

      record.lying = false;
      record.embedded = false;
      record.place(dim(thor.level()), thor.getX(), thor.getY(), thor.getZ());
      hammer.discard();
      ThorState state = ThorState.of(thor);
      if (state != null) {
         state.hammer = HammerState.IN_HAND;
         state.hammerDetached = false;
      }

      thor.serverLevel().playSound(null, thor.getX(), thor.getY(), thor.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 0.7F);
      post(ThorEventType.CATCH, record, null, true);
      return true;
   }

   // --- server tick ---

   /** Once per server tick (MjolnirEvents): first fall, owner check, unloaded flight, transfers, recovery. */
   public static void tick(MinecraftServer server) {
      MjolnirRecord record = record(server);
      if (record.activeOwner != null && server.getPlayerList().getPlayer(record.activeOwner) == null) {
         // Offline: the slot is free (crash without a logout event).
         record.activeOwner = null;
         dirty(server);
      }

      MjolnirFirstFall.tick(server, record);
      if (!record.exists()) {
         return;
      }

      ServerPlayer owner = owner(server, record);
      ServerLevel level = level(server, record);
      if (level == null) {
         // The saved dimension is gone: recover in the overworld.
         record.place(OVERWORLD, record.x, record.y, record.z);
         level = server.overworld();
         dirty(server);
      }

      MjolnirEntity hammer = entity(server, record);
      if (hammer != null && !ticking(level, hammer.position())) {
         // The body drifted into a chunk that does not tick: continue without it.
         hammer.discard();
         hammer = null;
      }

      switch (record.phase) {
         case IN_HAND -> {
            if (owner == null) {
               release(server, record, level, position(record).add(0.0, 1.0, 0.0), -0.4);
            }
         }
         case RETURNING -> {
            if (owner == null) {
               loosen(server, record);
            } else if (!dim(owner.level()).equals(record.dimension)) {
               transfer(server, record, owner);
            } else if (hammer == null && materialize(server, record) == null) {
               remoteReturn(server, record, owner, level);
            }
         }
         case DIMENSION_TRANSFER -> {
            // Saved mid-hop (crash): finish it through the state machine, never by a raw write.
            if (go(server, record, MjolnirPhase.RETURNING)) {
               if (owner != null) {
                  transfer(server, record, owner);
               } else {
                  loosen(server, record);
               }
            }
         }
         case DEATH_IMPACT -> deathFlight(server, record, level, hammer);
         case VOID_RECOVERY -> recover(server, record);
         case THROWN -> {
            if (hammer == null && materialize(server, record) == null) {
               // Out of the loaded world: the throw ends, it drops when its chunk loads again.
               go(server, record, MjolnirPhase.FALLING);
            }
         }
         case WORLD_FALLING, FALLING, RESTING -> {
            if (hammer == null) {
               materialize(server, record);
            }
         }
         default -> {
         }
      }

      if (record.recallPending && owner != null && record.phase == MjolnirPhase.RESTING) {
         recall(owner);
      }
   }

   /** Far return through chunks that do not tick: straight line, simplified, no physics (§5.1). */
   private static void remoteReturn(MinecraftServer server, MjolnirRecord record, ServerPlayer owner, ServerLevel level) {
      Vec3 from = position(record);
      Vec3 palm = MjolnirFlight.palm(owner.getEyePosition(), owner.getYRot());
      double distance = from.distanceTo(palm);
      Vec3 next = MjolnirFlight.step(from, palm, MjolnirFlight.remoteSpeed(distance));
      record.place(dim(level), next.x, next.y, next.z);
      Vec3 velocity = next.subtract(from);
      record.velocity(velocity.x, velocity.y, velocity.z);
      dirty(server);
      materialize(server, record);
   }

   /** №169: straight to the fixed death point, then the strike stronger than the first fall. */
   private static void deathFlight(MinecraftServer server, MjolnirRecord record, ServerLevel level, @Nullable MjolnirEntity hammer) {
      if (record.death == null || record.deathDimension == null) {
         go(server, record, MjolnirPhase.RESTING);
         return;
      }

      if (!record.deathDimension.equals(record.dimension)) {
         ServerLevel target = level(server, keyed(record.deathDimension));
         if (target == null) {
            go(server, record, MjolnirPhase.RESTING);
            return;
         }

         unload(server, record);
         record.place(record.deathDimension, record.death[0], Mth.clamp(record.death[1] + MjolnirTuning.TRANSFER_UP, target.getMinBuildHeight() + 2.0,
            target.getMaxBuildHeight() - 2.0), record.death[2]);
         dirty(server);
         return;
      }

      if (hammer != null || materialize(server, record) != null) {
         return;
      }

      Vec3 from = position(record);
      Vec3 point = new Vec3(record.death[0], record.death[1], record.death[2]);
      double distance = from.distanceTo(point);
      double speed = MjolnirFlight.remoteSpeed(distance);
      if (distance <= speed) {
         // Nobody is there to see it: the strike still happens (blocks only).
         Vec3 rest = MjolnirImpact.deathStrike(level, point, null, record.activeOwner);
         settle(server, record, level, rest);
         return;
      }

      Vec3 next = MjolnirFlight.step(from, point, speed);
      record.place(record.dimension, next.x, next.y, next.z);
      dirty(server);
   }

   private static MjolnirRecord keyed(String dimension) {
      MjolnirRecord probe = new MjolnirRecord();
      probe.dimension = dimension;
      return probe;
   }

   /** After a strike / landing: lies on its side at {@code rest}, the void-recovery point is updated. */
   static void settle(MinecraftServer server, MjolnirRecord record, ServerLevel level, Vec3 rest) {
      record.place(dim(level), rest.x, rest.y, rest.z);
      record.velocity(0.0, 0.0, 0.0);
      record.lying = true;
      record.embedded = false;
      record.safe = new double[]{rest.x, rest.y, rest.z};
      record.safeDimension = dim(level);
      if (record.phase == MjolnirPhase.WORLD_FALLING) {
         record.fallDone = true;
      }

      go(server, record, MjolnirPhase.RESTING);
      dirty(server);
   }

   /** Rests embedded in a block after a normal throw (rip on recall). */
   static void embed(MinecraftServer server, MjolnirRecord record, MjolnirEntity hammer) {
      record.embedded = true;
      record.lying = false;
      record.safe = new double[]{hammer.getX(), hammer.getY(), hammer.getZ()};
      record.safeDimension = dim(hammer.level());
      track(hammer);
      record.velocity(0.0, 0.0, 0.0);
      go(server, record, MjolnirPhase.RESTING);
      record.embedded = true;
   }

   /** Below the world: emergency recovery to the last resting point or the world spawn (PLACEHOLDER, [ОТКРЫТО]). */
   static void voidFall(MinecraftServer server, MjolnirRecord record) {
      if (record.phase.physical() && record.phase != MjolnirPhase.VOID_RECOVERY && go(server, record, MjolnirPhase.VOID_RECOVERY)) {
         unload(server, record);
      }
   }

   private static void recover(MinecraftServer server, MjolnirRecord record) {
      ServerLevel level = record.safe != null && record.safeDimension != null ? level(server, keyed(record.safeDimension)) : null;
      Vec3 at;
      if (level != null) {
         at = new Vec3(record.safe[0], record.safe[1] + 1.0, record.safe[2]);
      } else {
         level = server.overworld();
         BlockPos spawn = level.getSharedSpawnPos();
         int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
         at = new Vec3(spawn.getX() + 0.5, top + 1.0, spawn.getZ() + 0.5);
      }

      record.place(dim(level), at.x, at.y, at.z);
      record.velocity(0.0, -0.2, 0.0);
      record.lying = false;
      go(server, record, MjolnirPhase.FALLING);
      materialize(server, record);
   }

   // --- dev ---

   /** Dev: the first fall now (skips the timer; still once per save). */
   public static boolean forceFirstFall(MinecraftServer server, ServerPlayer near) {
      MjolnirRecord record = record(server);
      if (record.exists() || record.fallCancelled) {
         return false;
      }

      return MjolnirFirstFall.tryStart(server, record, near);
   }

   /** Dev: put the hammer at a point (created there when it never existed). Same hammer, never a second one. */
   public static void place(ServerPlayer near, Vec3 at) {
      MinecraftServer server = near.server;
      MjolnirRecord record = record(server);
      if (!record.exists()) {
         record.create(UUID.randomUUID(), MjolnirPhase.WORLD_FALLING);
         record.fallCancelled = true;
         record.place(dim(near.level()), at.x, at.y, at.z);
         settle(server, record, near.serverLevel(), at);
         materialize(server, record);
         return;
      }

      if (record.phase == MjolnirPhase.IN_HAND || record.phase.homing()) {
         loosen(server, record);
      }

      unload(server, record);
      record.place(dim(near.level()), at.x, at.y, at.z);
      record.velocity(0.0, 0.0, 0.0);
      if (record.phase != MjolnirPhase.FALLING) {
         go(server, record, MjolnirPhase.FALLING);
      }

      materialize(server, record);
   }
}

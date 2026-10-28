package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
   /** The one writer of where Mjölnir is on Thor's state (ThorState.HammerWriter, claimed once). */
   private static final ThorState.HammerWriter HAMMER = ThorState.claimHammerWriter(OWNER);
   /** A-m3: bridge events wait here until the change that caused them is fully written ({@link #flush}). */
   private static final MjolnirEventQueue<ThorEvent> EVENTS = new MjolnirEventQueue<>();

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

      announce(server, record);
      return true;
   }

   /** The phase changed: saved, and HAMMER_STATE_CHANGED queued (delivered by {@link #flush}). */
   private static void announce(MinecraftServer server, MjolnirRecord record) {
      dirty(server);
      post(ThorEventType.HAMMER_STATE_CHANGED, record, null, record.phase == MjolnirPhase.IN_HAND);
   }

   /**
    * Queue a bridge event; it is delivered by {@link #flush} once the whole
    * change is written (A-m3). A-m4: the bridge names a Thor as
    * {@code attackerId}, so only the active holder is named; while nobody
    * holds the slot (the first fall, an ownerless hammer) no event is posted.
    */
   static void post(ThorEventType type, MjolnirRecord record, @Nullable ThorMove move, boolean inHand) {
      UUID actor = record.activeOwner;
      if (actor != null) {
         // Where: the hammer's own place (dimension, position); no entity ids (the body may be unloaded).
         ThorEvent.Where where = record.dimension == null ? ThorEvent.Where.UNKNOWN
            : new ThorEvent.Where(-1, -1, record.dimension, record.x, record.y, record.z);
         EVENTS.add(new ThorEvent(ThorEvents.nextId(), type, actor, null, move, 0, inHand, 0.0F, true).at(where));
      }
   }

   /** Deliver the queued events: at the end of every public entry point and of the world / body tick. */
   static void flush() {
      EVENTS.flush(ThorEvents::post);
   }

   /** Server stopped: nothing of the old world is delivered into the next one. */
   public static void clearTransient() {
      EVENTS.clear();
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
         UUID previous = record.activeOwner;
         if (!id.equals(previous)) {
            EVENTS.add(new ThorEvent(ThorEvents.nextId(), ThorEventType.HAMMER_OWNER_CHANGED, id, previous, null, 0, false, 0.0F, true)
               .at(ThorEvent.Where.of(player, null)));
         }

         record.activeOwner = id;
         record.lastOwner = id;
         dirty(server);
      }
   }

   /**
    * The previous holder is gone: an attached, thrown or homing hammer falls
    * first (№3.2 / №213: no instant re-aim, nobody inherits a flight, no
    * ownerless hits). Rules: {@link MjolnirOwnership#loosen}.
    */
   private static void loosen(MinecraftServer server, MjolnirRecord record) {
      if (MjolnirOwnership.loosen(record)) {
         announce(server, record);
         materialize(server, record);
      }
   }

   /** Somebody else takes the slot: the old holder's flight falls and his pending recall is forgotten. */
   private static void changeOwner(MinecraftServer server, MjolnirRecord record) {
      MjolnirPhase before = record.phase;
      if (MjolnirOwnership.changeOwner(record)) {
         dirty(server);
         if (record.phase != before) {
            announce(server, record);
            materialize(server, record);
         }
      }
   }

   /** The body found its thrower gone (logout / hero change between two world ticks): it falls at once. */
   static void throwerGone(MinecraftServer server, MjolnirRecord record) {
      if (record.phase == MjolnirPhase.THROWN && owner(server, record) == null) {
         loosen(server, record);
      }
   }

   /**
    * A player became Thor (the transformation follows): the slot is his;
    * a never-created hammer is created once in his hand and the first fall
    * is cancelled for good (№191); an existing one is called by the scene.
    */
   public static void onEnter(ServerPlayer player, ThorState state) {
      try {
         MinecraftServer server = player.server;
         MjolnirRecord record = record(server);
         if (!player.getUUID().equals(record.activeOwner)) {
            changeOwner(server, record);
         }

         claim(server, record, player);
         if (!record.exists()) {
            createInHand(server, record, player, state);
         }
      } finally {
         flush();
      }
   }

   private static void createInHand(MinecraftServer server, MjolnirRecord record, ServerPlayer player, ThorState state) {
      if (!record.create(UUID.randomUUID(), MjolnirPhase.IN_HAND)) {
         return;
      }

      record.fallCancelled = !record.fallDone;
      record.place(dim(player.level()), player.getX(), player.getY(), player.getZ());
      dirty(server);
      HAMMER.project(state, HammerState.IN_HAND, false);
      post(ThorEventType.HAMMER_STATE_CHANGED, record, null, true);
   }

   /** Login: arm the first fall; a returning Thor takes a free slot, or yields to the active one. */
   public static void onLogin(ServerPlayer player) {
      try {
         login(player);
      } finally {
         flush();
      }
   }

   private static void login(ServerPlayer player) {
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
         changeOwner(server, record);
      }

      claim(server, record, player);
      if (!record.exists()) {
         createInHand(server, record, player, state);
      }

      project(player, state);
   }

   /**
    * Approved by the author (session A review): a former Thor who logs in
    * while another Thor holds the slot becomes Human and gets the race
    * screen to choose again (№211: he never displaces the active Thor).
    */
   private static void demote(ServerPlayer player) {
      HeroRegistry.changeHero(player, HeroId.HUMAN);
      if (player instanceof ViltrumiteCorePlayer corePlayer) {
         corePlayer.setChosenRace(false);
      }

      CoreMessages.sendToPlayer(new OpenRaceScreenS2CPacket(), player);
      player.displayClientMessage(Component.translatable(SLOT_TAKEN_LOGIN), false);
   }

   /** Death / logout / hero change of the active Thor (§3.5). Rules: {@link MjolnirOwnership#onCleanup}. */
   public static void onCleanup(ServerPlayer player, ThorState state, CleanupReason reason) {
      try {
         cleanup(player, state, reason);
      } finally {
         flush();
      }
   }

   private static void cleanup(ServerPlayer player, ThorState state, CleanupReason reason) {
      MinecraftServer server = player.server;
      MjolnirRecord record = record(server);
      if (!player.getUUID().equals(record.activeOwner)) {
         return;
      }

      ServerLevel level = player.serverLevel();
      Vec3 at = player.position().add(0.0, 1.0, 0.0);
      switch (MjolnirOwnership.onCleanup(record.phase, reason, MjolnirTuning.DEATH_IN_HAND_DROPS)) {
         // Approved: death with the hammer in the hand drops it at the death point (MjolnirTuning.DEATH_IN_HAND_DROPS);
         // a logout drops it heavily where he left; a hero change briefly and heavily nearby (№207).
         case DROP -> release(server, record, level, reason == CleanupReason.HERO_CHANGE ? dropNear(player, level, at) : at, switch (reason) {
            case DEATH, ERASED -> -0.2;
            case DISCONNECT -> -0.6;
            case HERO_CHANGE -> -0.8;
         });
         case DEATH_STRIKE -> {
            // №169: on to the fixed death point, then the strike.
            record.death = new double[]{player.getX(), player.getY(), player.getZ()};
            record.deathDimension = dim(level);
            go(server, record, MjolnirPhase.DEATH_IMPACT);
         }
         case LOOSEN -> loosen(server, record);
         case NONE -> {
            // The dead / erased Thor comes back as Thor (№208): the slot stays his, the hammer waits for his recall.
         }
      }

      if (MjolnirOwnership.freesSlot(reason)) {
         record.activeOwner = null;
      }

      dirty(server);
      HAMMER.project(state, HammerState.HOLSTERED, record.exists());
   }

   /** №207 drop point in front of him, never inside the wall he faces (A-m10). */
   private static Vec3 dropNear(ServerPlayer player, ServerLevel level, Vec3 at) {
      Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
      if (look.lengthSqr() < 1.0E-6) {
         return at;
      }

      Vec3 dir = look.normalize();
      Vec3 near = at.add(dir.scale(MjolnirTuning.DROP_NEAR));
      BlockHitResult blocked = level.clip(new ClipContext(at, near, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return blocked.getType() == HitResult.Type.MISS ? near : blocked.getLocation().subtract(dir.scale(0.3));
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
      try {
         MinecraftServer server = player.server;
         MjolnirRecord record = record(server);
         if (player.getUUID().equals(record.activeOwner) && record.phase == MjolnirPhase.IN_HAND) {
            record.place(dim(player.level()), player.getX(), player.getY(), player.getZ());
            dirty(server);
         }

         project(player, state);
      } finally {
         flush();
      }
   }

   /** Every tick: the Thor's synced hammer state is a projection of the world hammer. */
   public static void project(ServerPlayer player, ThorState state) {
      MinecraftServer server = player.server;
      MjolnirRecord record = record(server);
      if (!player.getUUID().equals(record.activeOwner)) {
         HAMMER.project(state, HammerState.HOLSTERED, record.exists());
         return;
      }

      switch (record.phase) {
         case NOT_CREATED -> {
            HAMMER.project(state, HammerState.HOLSTERED, false);
         }
         case IN_HAND -> {
            HAMMER.project(state, inHandProjection(state.hammer(), technicalSwap(state)), false);
            if (player.distanceToSqr(record.x, record.y, record.z) > 1.0 || !dim(player.level()).equals(record.dimension)) {
               record.place(dim(player.level()), player.getX(), player.getY(), player.getZ());
               dirty(server);
            }
         }
         default -> {
            HAMMER.project(state, record.phase.personal(), record.phase.detached());
         }
      }
   }

   /**
    * Pure: the projection of a world hammer in the active Thor's hand. The
    * store has no belt phase (an item in the hand only moves the drawn
    * attachment, MjolnirPlace.BELT), so HOLSTERED survives only while a
    * technical DRAW / HOLSTER swaps belt and hand; any other HOLSTERED (an
    * End-return clone, an old save, a dev write) is corrected to IN_HAND
    * instead of stranding Mjölnir on the belt with no way back (RC-2).
    */
   public static HammerState inHandProjection(HammerState current, boolean technicalSwap) {
      return current == HammerState.HOLSTERED && technicalSwap ? HammerState.HOLSTERED : HammerState.IN_HAND;
   }

   private static boolean technicalSwap(ThorState state) {
      return state.timeline.is(ThorMove.DRAW) || state.timeline.is(ThorMove.HOLSTER);
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
         phase = state == null ? HammerState.IN_HAND : inHandProjection(state.hammer(), technicalSwap(state));
      } else {
         phase = record.phase == MjolnirPhase.IN_HAND ? HammerState.HOLSTERED : record.phase.personal();
      }

      boolean inHand = owner && record.phase == MjolnirPhase.IN_HAND && phase == HammerState.IN_HAND;
      // Detached: lying / falling in the world away from its Thor; airborne: any flight (thrown, homing, falling, first fall, death strike).
      boolean detached = record.phase.detached();
      boolean airborne = switch (record.phase) {
         case WORLD_FALLING, THROWN, FALLING, RETURNING, DEATH_IMPACT, DIMENSION_TRANSFER, VOID_RECOVERY -> true;
         default -> false;
      };
      return new HammerView(record.logicalId, record.activeOwner, phase, inHand, record.exists() ? key(record.dimension) : null,
         record.exists() ? position(record) : null, detached, airborne);
   }

   // --- throw / recall / catch / pick up ---

   /** Mjölnir leaves the active Thor's hand (HAMMER_THROW release). */
   public static boolean launch(ServerPlayer thor, ThorState state, boolean charged) {
      try {
         return throwOut(thor, state, charged);
      } finally {
         flush();
      }
   }

   private static boolean throwOut(ServerPlayer thor, ThorState state, boolean charged) {
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
      record.clearFlight();
      if (!go(server, record, MjolnirPhase.THROWN)) {
         return false;
      }

      HAMMER.project(state, HammerState.THROWN, false);
      MjolnirEntity hammer = materialize(server, record);
      if (hammer != null) {
         hammer.beginThrow(thor, eye, look, charged);
      }

      thor.swing(InteractionHand.MAIN_HAND, true);
      ThorSounds.playAt(level, thor.getEyePosition(), charged ? ThorCue.THROW_CHARGED : ThorCue.THROW);
      post(ThorEventType.THROW, record, ThorMove.HAMMER_THROW, false);
      return true;
   }

   /** MMB with the hammer away: call it from any distance, chunk or dimension (§5.1). */
   public static Recall recall(ServerPlayer thor) {
      try {
         return call(thor);
      } finally {
         flush();
      }
   }

   private static Recall call(ServerPlayer thor) {
      MinecraftServer server = thor.server;
      MjolnirRecord record = record(server);
      if (!record.exists()) {
         return Recall.NO_HAMMER;
      } else if (!thor.getUUID().equals(record.activeOwner)) {
         return Recall.NOT_OWNER;
      }

      if (MjolnirOwnership.landsOnRecall(record.phase, entity(server, record) != null)) {
         // A-M1: nobody simulates this first fall (its chunk does not tick): it is put on the ground now, then called.
         ServerLevel level = level(server, record);
         if (level != null && MjolnirOwnership.remoteFallStep(record, firstFallGround(level, record), true)) {
            landFirstFall(server, record, level);
         }
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
      // Stuck in a block or planted head down in the ground (MjolnirPose): out with the rip jerk.
      boolean ripped = record.phase == MjolnirPhase.RESTING && (record.embedded || record.lying);
      MjolnirLift.rip(server, position(record));
      record.recallPending = false;
      record.lying = false;
      record.charged = false;
      // A-m13: the flight facts live in the record, so a body spawned later keeps them (hit list, rip).
      record.clearFlight();
      record.ripPending = ripped;
      if (!dim(thor.level()).equals(record.dimension)) {
         transfer(server, record, thor);
      } else if (go(server, record, MjolnirPhase.RETURNING)) {
         if (hammer != null) {
            hammer.beginReturn();
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
      record.clearFlight();
      ServerLevel level = thor.serverLevel();
      Vec3 at = MjolnirFlight.transferPoint(thor.position(), thor.getYRot());
      double y = Mth.clamp(at.y, level.getMinBuildHeight() + 2.0, level.getMaxBuildHeight() - 2.0);
      record.place(dim(level), at.x, y, at.z);
      record.velocity(0.0, 0.0, 0.0);
      go(server, record, MjolnirPhase.RETURNING);
      MjolnirEntity hammer = materialize(server, record);
      if (hammer != null) {
         hammer.beginReturn();
      }
   }

   /** The handle reached the palm (№155–157): back in the hand, a brief slow-down, even mid-knockback. */
   static void caught(MinecraftServer server, MjolnirRecord record, ServerPlayer thor, MjolnirEntity hammer) {
      if (!go(server, record, MjolnirPhase.IN_HAND)) {
         return;
      }

      record.place(dim(thor.level()), thor.getX(), thor.getY(), thor.getZ());
      record.velocity(0.0, 0.0, 0.0);
      record.clearFlight();
      record.voidAttempts = 0;
      hammer.discard();
      ThorState state = ThorState.of(thor);
      if (state != null) {
         HAMMER.project(state, HammerState.IN_HAND, false);
      }

      thor.setDeltaMovement(thor.getDeltaMovement().scale(MjolnirTuning.CATCH_SLOW));
      thor.hurtMarked = true;
      Vec3 palm = MjolnirFlight.palm(thor.getEyePosition(), thor.getYRot());
      ServerLevel level = thor.serverLevel();
      ThorSounds.playAt(level, palm, ThorCue.CATCH);
      MjolnirFx.catchFlash(level, palm);
      post(ThorEventType.CATCH, record, ThorMove.RECALL, true);
   }

   /** RMB on the resting model by the active Thor: he simply picks it up (§3.4). */
   static boolean pickUp(ServerPlayer thor, MjolnirEntity hammer) {
      try {
         return takeUp(thor, hammer);
      } finally {
         flush();
      }
   }

   private static boolean takeUp(ServerPlayer thor, MjolnirEntity hammer) {
      MinecraftServer server = thor.server;
      MjolnirRecord record = record(server);
      if (!thor.getUUID().equals(record.activeOwner) || record.phase != MjolnirPhase.RESTING || !hammer.getUUID().equals(record.logicalId)) {
         return false;
      }

      // №212 / A-m16: only the recall rips it from a lifter; a lift attempt simply ends when the body is gone (MjolnirLift.tick).
      if (!go(server, record, MjolnirPhase.IN_HAND)) {
         return false;
      }

      boolean planted = hammer.planted();
      Vec3 rest = hammer.position();
      record.lying = false;
      record.embedded = false;
      record.clearFlight();
      record.voidAttempts = 0;
      record.place(dim(thor.level()), thor.getX(), thor.getY(), thor.getZ());
      hammer.discard();
      if (planted) {
         // Out of the ground: a puff of the block it stood in (the cracks go with the body).
         BlockState ground = thor.serverLevel().getBlockState(BlockPos.containing(rest.x, rest.y - 0.05, rest.z));
         if (!ground.isAir()) {
            thor.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
               rest.x, rest.y + 0.05, rest.z, 14, 0.2, 0.05, 0.2, 0.1);
         }
      }
      ThorState state = ThorState.of(thor);
      if (state != null) {
         HAMMER.project(state, HammerState.IN_HAND, false);
      }

      ThorSounds.play(thor, ThorCue.PICKUP);
      post(ThorEventType.CATCH, record, null, true);
      return true;
   }

   // --- server tick ---

   /** Once per server tick (MjolnirEvents): first fall, owner check, unloaded flight, transfers, recovery. */
   public static void tick(MinecraftServer server) {
      try {
         worldTick(server);
      } finally {
         flush();
      }
   }

   private static void worldTick(MinecraftServer server) {
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
            if (owner == null) {
               // A-M6 / №213: the thrower left or is not Thor any more: the throw falls, nobody inherits it.
               loosen(server, record);
            } else if (hammer == null && materialize(server, record) == null) {
               // Out of the loaded world: the throw ends, it drops when its chunk loads again.
               go(server, record, MjolnirPhase.FALLING);
            }
         }
         case WORLD_FALLING -> {
            if (hammer == null && materialize(server, record) == null) {
               // A-M1: no body (nobody near): the fall goes on in the store and always ends on the ground.
               remoteFirstFall(server, record, level, owner);
            }
         }
         case FALLING, RESTING -> {
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

   /** A-M1: one step of a first fall without a body; on the ground it settles like the visible one. */
   private static void remoteFirstFall(MinecraftServer server, MjolnirRecord record, ServerLevel level, @Nullable ServerPlayer owner) {
      boolean landed = MjolnirOwnership.remoteFallStep(record, firstFallGround(level, record), record.recallPending && owner != null);
      dirty(server);
      if (landed) {
         landFirstFall(server, record, level);
      }
   }

   /** Where a first fall without a body ends: the live surface when its chunk is loaded, else the site surface saved at the start. */
   static double firstFallGround(ServerLevel level, MjolnirRecord record) {
      BlockPos column = BlockPos.containing(record.x, record.y, record.z);
      if (level.isLoaded(column)) {
         return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
      }

      // MjolnirFirstFall.tryStart saved the site surface (top block + 1) as the origin.
      return record.originY;
   }

   /** The first fall reached the ground without a body: the impact where the chunk is loaded, then it rests (fall done). */
   private static void landFirstFall(MinecraftServer server, MjolnirRecord record, ServerLevel level) {
      Vec3 at = position(record);
      Vec3 rest = level.isLoaded(BlockPos.containing(at)) ? MjolnirImpact.firstFall(level, null, at) : at;
      settle(server, record, level, rest);
   }

   /** Far return through chunks that do not tick: straight line, simplified, no physics (§5.1). */
   private static void remoteReturn(MinecraftServer server, MjolnirRecord record, ServerPlayer owner, ServerLevel level) {
      // Out of its block remotely: a body spawned later does not play the rip in mid-air.
      record.ripPending = false;
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

   /** After a strike / landing: stands planted upright at {@code rest} (MjolnirPose; record.lying = planted), the void-recovery point is updated. */
   static void settle(MinecraftServer server, MjolnirRecord record, ServerLevel level, Vec3 rest) {
      record.place(dim(level), rest.x, rest.y, rest.z);
      record.velocity(0.0, 0.0, 0.0);
      record.lying = true;
      record.embedded = false;
      record.safe = new double[]{rest.x, rest.y, rest.z};
      record.safeDimension = dim(level);
      record.voidAttempts = 0;
      record.parked = false;
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

   /**
    * One void recovery (A-m17, №217 open, PLACEHOLDER): back to the last
    * resting point, then to the world spawn, and when that fails too it is
    * parked at the spawn (rests without support until recalled), so a void
    * below every target never loops forever. A real landing resets the ladder.
    */
   private static void recover(MinecraftServer server, MjolnirRecord record) {
      record.voidAttempts++;
      ServerLevel safeLevel = record.safe != null && record.safeDimension != null ? level(server, keyed(record.safeDimension)) : null;
      MjolnirOwnership.VoidStep step = MjolnirOwnership.voidStep(record.voidAttempts, safeLevel != null);
      ServerLevel level;
      Vec3 at;
      if (step == MjolnirOwnership.VoidStep.SAFE) {
         level = safeLevel;
         at = new Vec3(record.safe[0], record.safe[1] + 1.0, record.safe[2]);
      } else {
         level = server.overworld();
         BlockPos spawn = level.getSharedSpawnPos();
         int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
         at = new Vec3(spawn.getX() + 0.5, Math.max(top, level.getMinBuildHeight() + 1) + 1.0, spawn.getZ() + 0.5);
      }

      record.place(dim(level), at.x, at.y, at.z);
      record.velocity(0.0, step == MjolnirOwnership.VoidStep.PARK ? 0.0 : -0.2, 0.0);
      record.lying = step == MjolnirOwnership.VoidStep.PARK;
      if (step == MjolnirOwnership.VoidStep.PARK) {
         // OPEN(owner): №217 the last-resort place of a hammer the void keeps taking (parked at the spawn, recall works).
         LOGGER.warn("Mjolnir: void recovery failed {} times, parked at the world spawn {}", record.voidAttempts - 1, at);
         go(server, record, MjolnirPhase.RESTING);
         record.parked = true;
      } else {
         go(server, record, MjolnirPhase.FALLING);
      }

      materialize(server, record);
   }

   // --- operator recovery (production) ---

   /** Outcome of {@code /mjolnir recover}. */
   public enum Recovery {
      NO_HAMMER,
      ALREADY_IN_HAND,
      /** Put on the ground at the active Thor and called back to him. */
      CALLED_TO_OWNER,
      /** No active Thor online: put on the ground at its last resting point (or the world spawn). */
      SETTLED
   }

   /**
    * Operator recovery of the one logical hammer (A-M1, production safe):
    * whatever limbo it is in (a flight nobody simulates, a pending fall, a
    * lost transfer), the same record is settled on firm ground and, when its
    * active Thor is online, called back to him. Never creates a second
    * hammer, never changes the owner.
    */
   public static Recovery forceRecover(MinecraftServer server) {
      try {
         return rescue(server);
      } finally {
         flush();
      }
   }

   private static Recovery rescue(MinecraftServer server) {
      MjolnirRecord record = record(server);
      if (!record.exists()) {
         return Recovery.NO_HAMMER;
      } else if (record.phase == MjolnirPhase.IN_HAND) {
         return Recovery.ALREADY_IN_HAND;
      }

      unload(server, record);
      if (record.phase == MjolnirPhase.DIMENSION_TRANSFER) {
         go(server, record, MjolnirPhase.RETURNING);
      }

      ServerPlayer owner = owner(server, record);
      ServerLevel level;
      Vec3 at;
      if (owner != null) {
         level = owner.serverLevel();
         at = owner.position();
      } else if (record.safe != null && record.safeDimension != null && level(server, keyed(record.safeDimension)) != null) {
         level = level(server, keyed(record.safeDimension));
         at = new Vec3(record.safe[0], record.safe[1], record.safe[2]);
      } else {
         level = server.overworld();
         BlockPos spawn = level.getSharedSpawnPos();
         int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
         at = new Vec3(spawn.getX() + 0.5, top, spawn.getZ() + 0.5);
      }

      record.recallPending = false;
      record.death = null;
      record.deathDimension = null;
      record.clearFlight();
      if (record.phase != MjolnirPhase.RESTING) {
         settle(server, record, level, at);
      } else {
         record.place(dim(level), at.x, at.y, at.z);
         record.velocity(0.0, 0.0, 0.0);
         record.embedded = false;
         record.lying = true;
         dirty(server);
      }

      if (record.phase != MjolnirPhase.RESTING) {
         LOGGER.warn("Mjolnir: recovery could not settle the hammer from {}", record.phase);
         return Recovery.SETTLED;
      }

      if (owner != null) {
         call(owner);
         return Recovery.CALLED_TO_OWNER;
      }

      materialize(server, record);
      return Recovery.SETTLED;
   }

   // --- dev ---

   /** Dev: the first fall now (skips the timer; still once per save). */
   public static boolean forceFirstFall(MinecraftServer server, ServerPlayer near) {
      try {
         MjolnirRecord record = record(server);
         if (record.exists() || record.fallCancelled) {
            return false;
         }

         return MjolnirFirstFall.tryStart(server, record, near);
      } finally {
         flush();
      }
   }

   /** Dev: put the hammer at a point (created there when it never existed). Same hammer, never a second one. */
   public static void place(ServerPlayer near, Vec3 at) {
      try {
         put(near, at);
      } finally {
         flush();
      }
   }

   private static void put(ServerPlayer near, Vec3 at) {
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

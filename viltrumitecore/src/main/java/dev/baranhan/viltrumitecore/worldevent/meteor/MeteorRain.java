package dev.baranhan.viltrumitecore.worldevent.meteor;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.event.MeteorSpawnEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Endless meteor rain around one player (manual, {@code /meteor start}): the existing
 * {@link MeteorEntity} keeps falling on the regular fall line with its explosion, magma
 * crater, sounds and VFX until someone stops it. No phases, no end, no rewards (rain meteors
 * drop no gun parts). Off by default; state is memory-only, so a server restart ends every rain,
 * and the initiator leaving ends theirs. Stopping only ends new launches: meteors already in
 * the air finish their fall.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class MeteorRain {
   private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

   private MeteorRain() {
   }

   public static final class Session {
      final UUID owner;
      final ResourceKey<Level> dimension;
      final long startTick;
      final Set<UUID> inFlight = new HashSet<>();
      int cooldown;
      long launched;

      Session(UUID owner, ResourceKey<Level> dimension, long startTick) {
         this.owner = owner;
         this.dimension = dimension;
         this.startTick = startTick;
      }

      public UUID owner() {
         return this.owner;
      }

      public ResourceKey<Level> dimension() {
         return this.dimension;
      }

      public long launched() {
         return this.launched;
      }

      public int inFlight() {
         return this.inFlight.size();
      }

      public long ageTicks(MinecraftServer server) {
         return server.getTickCount() - this.startTick;
      }
   }

   /** False when this player already has a rain. */
   public static boolean start(ServerPlayer player) {
      if (SESSIONS.containsKey(player.getUUID())) {
         return false;
      }

      Session session = new Session(player.getUUID(), player.level().dimension(), player.server.getTickCount());
      session.cooldown = 20;
      SESSIONS.put(player.getUUID(), session);
      return true;
   }

   /** Ends new launches for this player's rain; meteors in the air keep falling. */
   public static boolean stop(UUID owner) {
      return SESSIONS.remove(owner) != null;
   }

   public static int stopAll() {
      int count = SESSIONS.size();
      SESSIONS.clear();
      return count;
   }

   @Nullable
   public static Session session(UUID owner) {
      return SESSIONS.get(owner);
   }

   public static Collection<Session> sessions() {
      return Collections.unmodifiableCollection(new ArrayList<>(SESSIONS.values()));
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent event) {
      if (event.phase != Phase.END || SESSIONS.isEmpty()) {
         return;
      }

      MinecraftServer server = event.getServer();
      Iterator<Session> it = SESSIONS.values().iterator();
      while (it.hasNext()) {
         Session session = it.next();
         ServerPlayer owner = server.getPlayerList().getPlayer(session.owner);
         if (owner == null) {
            it.remove();
            continue;
         }

         ServerLevel level = server.getLevel(session.dimension);
         if (level == null) {
            it.remove();
            continue;
         }

         prune(level, session);
         // The rain belongs to the world where it started: it pauses while the player is elsewhere,
         // in spectator or dead, and resumes on return.
         if (owner.level() != level || !owner.isAlive() || owner.isSpectator()) {
            continue;
         }

         if (--session.cooldown > 0) {
            continue;
         }

         session.cooldown = MeteorRainRules.nextDelay(level.random);
         int burst = MeteorRainRules.burstSize(level.random, session.inFlight.size());
         for (int i = 0; i < burst; i++) {
            MeteorEntity meteor = launchNear(level, owner);
            if (meteor != null) {
               session.inFlight.add(meteor.getUUID());
               session.launched++;
            }
         }
      }
   }

   /** Forget landed or unloaded meteors; remove one that never lands (void, stuck). */
   private static void prune(ServerLevel level, Session session) {
      Iterator<UUID> it = session.inFlight.iterator();
      while (it.hasNext()) {
         Entity entity = level.getEntity(it.next());
         if (entity == null || entity.isRemoved()) {
            it.remove();
         } else if (entity.tickCount > MeteorRainRules.MAX_METEOR_AGE_TICKS || entity.getY() < level.getMinBuildHeight() - 64) {
            entity.discard();
            it.remove();
         }
      }
   }

   @Nullable
   private static MeteorEntity launchNear(ServerLevel level, ServerPlayer owner) {
      for (int attempt = 0; attempt < MeteorRainRules.PLACEMENT_TRIES; attempt++) {
         double[] offset = MeteorRainRules.landingOffset(level.random);
         int x = (int)Math.floor(owner.getX() + offset[0]);
         int z = (int)Math.floor(owner.getZ() + offset[1]);
         BlockPos column = new BlockPos(x, owner.getBlockY(), z);
         // Never load chunks for the rain: the landing column must already be loaded and the
         // spawn point (up the fixed fall line) must be in an entity-ticking chunk, else it would hang.
         if (!level.hasChunkAt(column)) {
            continue;
         }

         int groundY = level.getHeight(Types.WORLD_SURFACE, x, z);
         if (groundY <= level.getMinBuildHeight() || groundY >= 240) {
            continue;
         }

         BlockPos impact = new BlockPos(x, groundY, z);
         Vec3 spawn = MeteorSpawnEvent.spawnPoint(impact);
         if (!level.isPositionEntityTicking(BlockPos.containing(spawn.x, groundY, spawn.z)) || !level.isPositionEntityTicking(impact)) {
            continue;
         }

         MeteorEntity meteor = MeteorSpawnEvent.launch(level, impact, false);
         if (meteor != null) {
            return meteor;
         }
      }

      return null;
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      SESSIONS.remove(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         SESSIONS.values().removeIf(session -> session.dimension.equals(level.dimension()));
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      SESSIONS.clear();
   }
}

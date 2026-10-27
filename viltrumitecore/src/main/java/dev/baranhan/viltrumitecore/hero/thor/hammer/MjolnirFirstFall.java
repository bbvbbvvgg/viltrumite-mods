package dev.baranhan.viltrumitecore.hero.thor.hammer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * §3.3: one first fall per save, about 60 s after the first player join;
 * cancelled for good by an early Thor choice (the hammer is then created in
 * his hand). A random present player is chosen; near him, at a safe
 * distance, an open, breakable surface; without one it waits and retries.
 */
final class MjolnirFirstFall {
   private MjolnirFirstFall() {
   }

   /** Pure: is the first fall due now (armed, never happened, not cancelled, timer over)? */
   static boolean due(MjolnirRecord record, long now) {
      return record.fallArmedAt >= 0L && !record.exists() && !record.fallCancelled && !record.fallDone
         && now - record.fallArmedAt >= MjolnirTuning.FIRST_FALL_DELAY;
   }

   /** Pure: the timer starts once, at the first join, and only while the hammer never existed. */
   static boolean arm(MjolnirRecord record, long now) {
      if (record.fallArmedAt >= 0L || record.exists() || record.fallCancelled || record.fallDone) {
         return false;
      }

      record.fallArmedAt = now;
      return true;
   }

   static void arm(MinecraftServer server, MjolnirRecord record) {
      if (arm(record, server.overworld().getGameTime())) {
         MjolnirWorld.dirty(server);
      }
   }

   static void tick(MinecraftServer server, MjolnirRecord record) {
      long now = server.overworld().getGameTime();
      if (!due(record, now) || (now - record.fallArmedAt) % MjolnirTuning.FALL_RETRY != 0L) {
         return;
      }

      List<ServerPlayer> present = new ArrayList<>();
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (player.isAlive() && !player.isSpectator()) {
            present.add(player);
         }
      }

      if (!present.isEmpty()) {
         tryStart(server, record, present.get(server.overworld().random.nextInt(present.size())));
      }
   }

   /** Look for a site near {@code chosen}; start the fall when one is found (else wait, nothing is spent). */
   static boolean tryStart(MinecraftServer server, MjolnirRecord record, ServerPlayer chosen) {
      ServerLevel level = chosen.serverLevel();
      BlockPos site = site(level, chosen, level.random);
      if (site == null) {
         return false;
      }

      if (!record.create(UUID.randomUUID(), MjolnirPhase.WORLD_FALLING)) {
         return false;
      }

      double x = site.getX() + 0.5;
      double z = site.getZ() + 0.5;
      double top = Math.min(site.getY() + MjolnirTuning.FALL_HEIGHT, level.getMaxBuildHeight() + 64.0);
      record.originX = x;
      record.originY = site.getY() + 1.0;
      record.originZ = z;
      record.place(MjolnirWorld.dim(level), x, top, z);
      record.velocity(0.0, -MjolnirTuning.FALL_SPEED, 0.0);
      record.lying = false;
      MjolnirWorld.dirty(server);
      MjolnirWorld.post(dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType.HAMMER_STATE_CHANGED, record, null, false);
      // Heard by the whole dimension: thunder seconds before the impact (§3.3.4).
      level.playSound(null, x, site.getY(), z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10000.0F, 0.5F);
      MjolnirWorld.materialize(server, record);
      return true;
   }

   @Nullable
   static BlockPos site(ServerLevel level, ServerPlayer chosen, RandomSource random) {
      if (level.dimensionType().hasCeiling()) {
         return null;
      }

      for (int i = 0; i < MjolnirTuning.FALL_SITE_TRIES; i++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double distance = MjolnirTuning.FALL_SITE_MIN + random.nextDouble() * (MjolnirTuning.FALL_SITE_MAX - MjolnirTuning.FALL_SITE_MIN);
         int x = (int)Math.floor(chosen.getX() + Math.cos(angle) * distance);
         int z = (int)Math.floor(chosen.getZ() + Math.sin(angle) * distance);
         BlockPos surface = surface(level, x, z);
         if (surface != null && open(level, surface) && clear(level, surface)) {
            return surface;
         }
      }

      return null;
   }

   @Nullable
   private static BlockPos surface(ServerLevel level, int x, int z) {
      BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
      if (!level.isPositionEntityTicking(column)) {
         return null;
      }

      int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      return top <= level.getMinBuildHeight() + 1 ? null : new BlockPos(x, top - 1, z);
   }

   /** Open sky over the whole crater, firm dry ground, flat enough, and the blocks may break (not protected). */
   private static boolean open(ServerLevel level, BlockPos surface) {
      BlockState ground = level.getBlockState(surface);
      if (!ground.isFaceSturdy(level, surface, Direction.UP) || !level.getFluidState(surface).isEmpty()
         || !level.getFluidState(surface.above()).isEmpty() || ground.getDestroySpeed(level, surface) < 0.0F) {
         return false;
      }

      int r = MjolnirTuning.CRATER_RADIUS;
      int[][] probes = {{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}};
      for (int[] probe : probes) {
         BlockPos at = surface(level, surface.getX() + probe[0], surface.getZ() + probe[1]);
         if (at == null || Math.abs(at.getY() - surface.getY()) > MjolnirTuning.FALL_SITE_FLATNESS || !level.canSeeSky(at.above())) {
            return false;
         }
      }

      return true;
   }

   /** Safe distance: nobody stands closer than the clearance to the impact. */
   private static boolean clear(ServerLevel level, BlockPos surface) {
      double limit = MjolnirTuning.FALL_SITE_CLEARANCE * MjolnirTuning.FALL_SITE_CLEARANCE;
      for (ServerPlayer player : level.players()) {
         if (player.distanceToSqr(surface.getX() + 0.5, player.getY(), surface.getZ() + 0.5) < limit) {
            return false;
         }
      }

      return true;
   }
}

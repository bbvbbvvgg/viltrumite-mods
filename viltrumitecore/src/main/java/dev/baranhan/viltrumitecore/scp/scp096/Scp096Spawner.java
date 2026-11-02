package dev.baranhan.viltrumitecore.scp.scp096;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Natural spawn of SCP-096 (design §2): very rare, only inside the prepared city, at the edge of what a player
 * simulates, outdoors on the pedestrian grid, never in front of a camera, never loading a chunk. No message,
 * no boss bar, no title, no global sound. Also admits loading instances (no duplicates) and drops stale records.
 */
public final class Scp096Spawner {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/scp096");
   private static final RandomSource RANDOM = RandomSource.create();
   private static final Map<UUID, Integer> STALE = new HashMap<>();

   /** Outcome of one natural attempt (also shown by /scp096 natural check|force). */
   public enum Result {
      SPAWNED, DISABLED, NO_CITY, PEACEFUL, EXISTS, COOLDOWN, ROLL_FAILED, NO_PLAYER, NO_SITE
   }

   /** Result and, when spawned, the creature. */
   public record Attempt(Result result, @Nullable Scp096Entity spawned) {
   }

   private Scp096Spawner() {
   }

   static void tick(MinecraftServer server) {
      if (server.getTickCount() % 20 != 7) {
         return;
      }
      Scp096SavedData data = Scp096SavedData.get(server);
      dropStale(server, data);
      if (!data.naturalEnabled()) {
         return;
      }
      long now = server.overworld().getGameTime();
      if (now < data.nextCheck()) {
         return;
      }
      data.setNextCheck(now + Scp096Rules.checkIntervalTicks(ViltrumiteCoreConfig.INSTANCE.scp096NaturalCheckSeconds));
      Attempt a = attempt(server, false, false);
      LOG.debug("SCP-096 natural check: {}", a.result());
   }

   /** One natural attempt now. {@code force} skips the roll and the cooldown (QA), never the other rules. */
   public static Attempt attempt(MinecraftServer server, boolean force, boolean manualCheck) {
      Scp096SavedData data = Scp096SavedData.get(server);
      if (!data.naturalEnabled() && !force) {
         return new Attempt(Result.DISABLED, null);
      }
      ServerLevel level = null;
      CityManager city = null;
      for (ServerLevel l : server.getAllLevels()) {
         CityManager c = CityManager.of(l);
         if (c != null) {
            level = l;
            city = c;
            break;
         }
      }
      if (city == null) {
         return new Attempt(Result.NO_CITY, null);
      }
      if (level.getDifficulty() == Difficulty.PEACEFUL) {
         return new Attempt(Result.PEACEFUL, null);
      }
      if (data.natural() != null) {
         return new Attempt(Result.EXISTS, null);
      }
      long now = server.overworld().getGameTime();
      long cooldown = Scp096Rules.cooldownTicks(ViltrumiteCoreConfig.INSTANCE.scp096NaturalCooldownMinutes);
      if (!force && data.lastNaturalSpawn() != Long.MIN_VALUE && now - data.lastNaturalSpawn() < cooldown) {
         return new Attempt(Result.COOLDOWN, null);
      }
      if (!force && !Scp096SpawnRules.roll(RANDOM.nextDouble(), Scp096Rules.chance(ViltrumiteCoreConfig.INSTANCE.scp096NaturalChancePercent))) {
         return new Attempt(Result.ROLL_FAILED, null);
      }
      List<ServerPlayer> inCity = new ArrayList<>();
      for (ServerPlayer p : level.players()) {
         if (!p.isSpectator() && city.profile().inArena(p.getBlockX(), p.getBlockY(), p.getBlockZ())) {
            inCity.add(p);
         }
      }
      if (inCity.isEmpty()) {
         return new Attempt(Result.NO_PLAYER, null);
      }
      ServerPlayer anchor = inCity.get(RANDOM.nextInt(inCity.size()));
      double r = Scp096SpawnRules.radius(server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance());
      double minR = Scp096SpawnRules.ringMin(r);
      double maxR = Scp096SpawnRules.ringMax(r);
      final ServerLevel lv = level;
      final CityManager ct = city;
      WalkGrid grid = city.grid();
      for (int round = 0; round < 6; round++) {
         long cell = grid.randomCell(RANDOM::nextDouble, anchor.getX(), anchor.getZ(), minR, maxR, false, 24,
            (x, feetY, z) -> validSite(lv, ct, x, feetY, z, r));
         if (cell == Long.MIN_VALUE) {
            continue;
         }
         int x = WalkGrid.unpackX(cell);
         int z = WalkGrid.unpackZ(cell);
         int feetY = grid.feetY(x, z);
         Scp096Entity e = Scp096Entities.SCP096.get().create(level);
         if (e == null) {
            return new Attempt(Result.NO_SITE, null);
         }
         ServerPlayer nearest = nearestPlayer(level, x + 0.5, z + 0.5);
         float yaw = nearest == null ? RANDOM.nextFloat() * 360.0F : Scp096SpawnRules.yawAwayFrom(nearest.getX() - (x + 0.5), nearest.getZ() - (z + 0.5));
         e.moveTo(x + 0.5, feetY, z + 0.5, yaw, 0.0F);
         e.setYHeadRot(yaw);
         e.yBodyRot = yaw;
         e.setNatural(true);
         e.setState(Scp096State.SIT);
         if (!level.addFreshEntity(e)) {
            return new Attempt(Result.NO_SITE, null);
         }
         data.touch(e);
         data.markNaturalSpawn(now);
         LOG.debug("SCP-096 natural spawn at {} {} {} in {} ({} blocks from {}, ring {}..{})", x, feetY, z, level.dimension().location(),
            Math.round(Math.sqrt(anchor.distanceToSqr(x + 0.5, feetY, z + 0.5))), anchor.getGameProfile().getName(), Math.round(minR), Math.round(maxR));
         return new Attempt(Result.SPAWNED, e);
      }
      return new Attempt(Result.NO_SITE, null);
   }

   /** Outdoor pedestrian cell at the edge of the simulated area, out of every camera, nothing loaded for it. */
   static boolean validSite(ServerLevel level, CityManager city, int x, int feetY, int z, double r) {
      BlockPos feet = new BlockPos(x, feetY, z);
      if (!city.profile().inArena(x, feetY, z) || !level.isPositionEntityTicking(feet)) {
         return false;
      }
      BlockPos below = feet.below();
      if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(feet).isEmpty()) {
         return false;
      }
      AABB body = new AABB(x + 0.5 - 0.5, feetY + 0.01, z + 0.5 - 0.5, x + 0.5 + 0.5, feetY + Scp096Rules.HEIGHT + 0.1, z + 0.5 + 0.5);
      if (!level.noCollision(body) || !level.canSeeSky(feet.above(2))) {
         return false;
      }
      Vec3 head = new Vec3(x + 0.5, feetY + 1.3, z + 0.5);
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator()) {
            continue;
         }
         double dx = head.x - p.getX();
         double dz = head.z - p.getZ();
         double d2 = dx * dx + dz * dz;
         if (d2 < Scp096SpawnRules.MIN_PLAYER_DISTANCE * Scp096SpawnRules.MIN_PLAYER_DISTANCE) {
            return false;
         }
         if (d2 > r * r * 1.7) {
            continue;
         }
         Vec3 look = p.getViewVector(1.0F);
         if (!Scp096SpawnRules.outsideView(look.x, look.z, dx, dz)) {
            Vec3 eye = p.getEyePosition();
            HitResult hit = level.clip(new ClipContext(eye, head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) {
               return false;
            }
         }
      }
      return true;
   }

   @Nullable
   private static ServerPlayer nearestPlayer(ServerLevel level, double x, double z) {
      ServerPlayer best = null;
      double bd = Double.MAX_VALUE;
      for (ServerPlayer p : level.players()) {
         double d = (p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z);
         if (!p.isSpectator() && d < bd) {
            bd = d;
            best = p;
         }
      }
      return best;
   }

   /** EntityJoinLevel: false drops the joining copy (despawned while unloaded, or a second natural one). */
   static boolean admit(Scp096Entity e) {
      if (!(e.level() instanceof ServerLevel sl)) {
         return true;
      }
      Scp096SavedData data = Scp096SavedData.get(sl.getServer());
      if (data.consumeRemoval(e.getUUID())) {
         return false;
      }
      if (e.natural()) {
         Scp096SavedData.Instance n = data.natural();
         if (n != null && !n.id.equals(e.getUUID())) {
            return false;
         }
      }
      data.touch(e);
      return true;
   }

   /** Records whose chunk is loaded and entity-ticking but hold no entity for 10 s are forgotten. */
   private static void dropStale(MinecraftServer server, Scp096SavedData data) {
      List<UUID> gone = new ArrayList<>();
      for (Scp096SavedData.Instance i : data.instances()) {
         ServerLevel level = levelOf(server, i.dimension);
         if (level == null) {
            continue;
         }
         BlockPos pos = BlockPos.containing(i.x, i.y, i.z);
         if (level.getEntity(i.id) == null && level.isPositionEntityTicking(pos)) {
            int n = STALE.merge(i.id, 20, Integer::sum);
            if (n > 200) {
               gone.add(i.id);
            }
         } else {
            STALE.remove(i.id);
         }
      }
      for (UUID id : gone) {
         STALE.remove(id);
         data.forget(id);
      }
   }

   @Nullable
   static ServerLevel levelOf(MinecraftServer server, String dimension) {
      ResourceLocation id = ResourceLocation.tryParse(dimension);
      if (id == null) {
         return null;
      }
      for (ServerLevel l : server.getAllLevels()) {
         if (l.dimension().location().equals(id)) {
            return l;
         }
      }
      return null;
   }
}

package dev.baranhan.viltrumitecore.t1000;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The rare scheduler (design §3-4). Every {@link T1000Rules#SCHEDULER_PERIOD} ticks: at most one dormant instance
 * comes back near a player (after its delay and the startup grace); on the configured interval one natural roll.
 * Every appearance is inside the prepared city, on a pedestrian cell in an already ticking chunk, among
 * citizens, behind every camera. Nothing is announced, nothing loads a chunk, nothing spawns in bulk.
 */
public final class T1000Spawner {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/t1000");
   private static final RandomSource RANDOM = RandomSource.create();

   /** Result and, when spawned, the body. */
   public record Attempt(T1000SpawnRules.Result result, @Nullable T1000Entity spawned) {
   }

   private T1000Spawner() {
   }

   static void tick(MinecraftServer server) {
      if (server.getTickCount() % 20 != 11) {
         return;
      }
      T1000SavedData data = T1000SavedData.get(server);
      long now = server.overworld().getGameTime();
      if (now >= data.nextWakeCheck()) {
         data.setNextWakeCheck(now + T1000Rules.SCHEDULER_PERIOD);
         wakeOne(server, data, now);
      }
      if (!data.naturalEnabled() || now < data.nextCheck()) {
         return;
      }
      data.setNextCheck(now + T1000Rules.checkIntervalTicks(ViltrumiteCoreConfig.INSTANCE.t1000NaturalCheckSeconds));
      Attempt a = attempt(server, false);
      LOG.debug("T-1000 natural check: {}", a.result());
   }

   @Nullable
   private static CityManager cityLevel(MinecraftServer server, ServerLevel[] out) {
      for (ServerLevel l : server.getAllLevels()) {
         CityManager c = CityManager.of(l);
         if (c != null && c.enabled()) {
            out[0] = l;
            return c;
         }
      }
      return null;
   }

   /** One natural attempt now. {@code force} skips the roll, the respawn delay and the startup grace (QA). */
   public static Attempt attempt(MinecraftServer server, boolean force) {
      T1000SavedData data = T1000SavedData.get(server);
      ServerLevel[] lv = new ServerLevel[1];
      CityManager city = cityLevel(server, lv);
      ServerLevel level = lv[0];
      long now = server.overworld().getGameTime();
      ViltrumiteCoreConfig cfg = ViltrumiteCoreConfig.INSTANCE;
      T1000SpawnRules.Result gate = T1000SpawnRules.gate(data.naturalEnabled(), city != null, level != null && level.getDifficulty() == Difficulty.PEACEFUL,
         data.instances().size(), T1000Rules.maxActive(cfg.t1000MaxActive), now, data.lastDestroyed(),
         T1000Rules.respawnDelayTicks(cfg.t1000RespawnDelayMinutes), server.getTickCount(), force);
      if (gate != T1000SpawnRules.Result.SPAWNED) {
         return new Attempt(gate, null);
      }
      if (!force && !T1000SpawnRules.roll(RANDOM.nextDouble(), T1000Rules.chance(cfg.t1000NaturalChancePercent))) {
         return new Attempt(T1000SpawnRules.Result.ROLL_FAILED, null);
      }
      Vec3 site = site(level, city, true);
      if (site == null) {
         return new Attempt(sitePlayers(level, city).isEmpty() ? T1000SpawnRules.Result.NO_PLAYER : T1000SpawnRules.Result.NO_SITE, null);
      }
      T1000Entity e = T1000Entities.T1000.get().create(level);
      if (e == null) {
         return new Attempt(T1000SpawnRules.Result.NO_SITE, null);
      }
      e.setNatural(true);
      e.setDisguise(T1000Disguises.CITIZEN, T1000Disguises.freshSeed(RANDOM, RANDOM.nextInt(4) == 0));
      place(level, e, site);
      if (!level.addFreshEntity(e)) {
         return new Attempt(T1000SpawnRules.Result.NO_SITE, null);
      }
      data.markNaturalSpawn(now);
      LOG.debug("T-1000 natural appearance in {}", level.dimension().location());
      return new Attempt(T1000SpawnRules.Result.SPAWNED, e);
   }

   /** One dormant instance comes back (oldest first) when its delay is over and a site exists. */
   private static void wakeOne(MinecraftServer server, T1000SavedData data, long now) {
      List<T1000SavedData.Instance> sleeping = data.dormant();
      if (sleeping.isEmpty()) {
         return;
      }
      ServerLevel[] lv = new ServerLevel[1];
      CityManager city = cityLevel(server, lv);
      ServerLevel level = lv[0];
      if (city == null || level.getDifficulty() == Difficulty.PEACEFUL) {
         return;
      }
      T1000SavedData.Instance pick = null;
      for (T1000SavedData.Instance i : sleeping) {
         if (T1000SpawnRules.mayWake(now, i.dormantSince, server.getTickCount()) && (pick == null || i.dormantSince < pick.dormantSince)) {
            pick = i;
         }
      }
      if (pick == null || level.getEntity(pick.id) != null) {
         return;
      }
      Vec3 site = site(level, city, false);
      if (site == null) {
         return;
      }
      T1000Entity e = T1000Entities.T1000.get().create(level);
      if (e == null) {
         return;
      }
      e.setUUID(pick.id);
      e.setNatural(pick.natural);
      e.setDisguise(pick.disguiseKind, pick.disguise);
      e.restore(pick.integrity, pick.core);
      place(level, e, site);
      if (level.addFreshEntity(e)) {
         LOG.debug("T-1000 returned in {}", level.dimension().location());
      }
   }

   private static void place(ServerLevel level, T1000Entity e, Vec3 site) {
      float yaw = RANDOM.nextFloat() * 360.0F;
      e.moveTo(site.x, site.y, site.z, yaw, 0.0F);
      e.setYHeadRot(yaw);
      e.yBodyRot = yaw;
   }

   private static List<ServerPlayer> sitePlayers(ServerLevel level, CityManager city) {
      List<ServerPlayer> in = new ArrayList<>();
      for (ServerPlayer p : level.players()) {
         if (!p.isSpectator() && city.profile().inArena(p.getBlockX(), p.getBlockY(), p.getBlockZ())) {
            in.add(p);
         }
      }
      return in;
   }

   /** A pedestrian cell near a random player in the city, among citizens (natural), behind every camera. */
   @Nullable
   static Vec3 site(ServerLevel level, CityManager city, boolean needCrowd) {
      List<ServerPlayer> in = sitePlayers(level, city);
      if (in.isEmpty()) {
         return null;
      }
      ServerPlayer anchor = in.get(RANDOM.nextInt(in.size()));
      WalkGrid grid = city.grid();
      for (int round = 0; round < 4; round++) {
         long cell = grid.randomCell(RANDOM::nextDouble, anchor.getX(), anchor.getZ(), T1000SpawnRules.RING_MIN, T1000SpawnRules.RING_MAX, false, 24,
            (x, feetY, z) -> validSite(level, city, x, feetY, z, needCrowd));
         if (cell != Long.MIN_VALUE) {
            int x = WalkGrid.unpackX(cell);
            int z = WalkGrid.unpackZ(cell);
            return new Vec3(x + 0.5, grid.feetY(x, z), z + 0.5);
         }
      }
      return null;
   }

   static boolean validSite(ServerLevel level, CityManager city, int x, int feetY, int z, boolean needCrowd) {
      BlockPos feet = new BlockPos(x, feetY, z);
      if (!city.profile().inArena(x, feetY, z) || !level.isPositionEntityTicking(feet)) {
         return false;
      }
      BlockPos below = feet.below();
      if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(feet).isEmpty()) {
         return false;
      }
      AABB body = new AABB(x + 0.2, feetY + 0.01, z + 0.2, x + 0.8, feetY + T1000Rules.HEIGHT + 0.1, z + 0.8);
      if (!level.noCollision(body) || city.dangerMap().dangerous(x + 0.5, feetY, z + 0.5, city.now())) {
         return false;
      }
      Vec3 head = new Vec3(x + 0.5, feetY + 1.6, z + 0.5);
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator()) {
            continue;
         }
         double dx = head.x - p.getX();
         double dz = head.z - p.getZ();
         double d2 = dx * dx + dz * dz;
         if (d2 < T1000SpawnRules.MIN_PLAYER_DISTANCE * T1000SpawnRules.MIN_PLAYER_DISTANCE) {
            return false;
         }
         if (d2 > 160.0 * 160.0) {
            continue;
         }
         Vec3 look = p.getViewVector(1.0F);
         if (!T1000SpawnRules.outsideView(look.x, look.z, dx, dz)) {
            HitResult hit = level.clip(new ClipContext(p.getEyePosition(), head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) {
               return false;
            }
         }
      }
      if (needCrowd) {
         AABB around = new AABB(head, head).inflate(T1000SpawnRules.CROWD_RADIUS);
         int crowd = level.getEntitiesOfClass(CitizenEntity.class, around, c -> !(c instanceof T1000Entity)).size();
         return crowd >= T1000SpawnRules.MIN_CROWD;
      }
      return true;
   }

   /** /t1000 spawn: an operator places one at a point (no rules but the city's world). */
   @Nullable
   public static T1000Entity spawnAt(ServerLevel level, Vec3 at) {
      T1000Entity e = T1000Entities.T1000.get().create(level);
      if (e == null) {
         return null;
      }
      e.setDisguise(T1000Disguises.CITIZEN, T1000Disguises.freshSeed(RANDOM, RANDOM.nextInt(4) == 0));
      place(level, e, at);
      return level.addFreshEntity(e) ? e : null;
   }
}

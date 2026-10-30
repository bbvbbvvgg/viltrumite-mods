package dev.baranhan.viltrumitecore.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Keeps the streets around players populated while the city is enabled: every second,
 * each player inside the arena gets citizens up to perPlayer x density within 64 blocks
 * (a few per second, on preferred cells 24-64 blocks away in entity-ticking chunks),
 * capped per level. Citizens more than 96 blocks (manual: 128) from every player are
 * removed; chunk unloads remove them too (they are never saved).
 */
final class CityPopulation {
   static final int NEAR = 64;
   static final int FAR = 96;
   static final int MANUAL_FAR = 128;
   static final int SPAWNS_PER_PLAYER_PER_SECOND = 3;
   private final CityManager city;

   CityPopulation(CityManager city) {
      this.city = city;
   }

   /** Citizens wanted around one player. */
   static int targetPerPlayer(int perPlayer, double density) {
      return (int)Math.round(Math.max(0.0, perPlayer * density));
   }

   /** Level-wide cap: the profile cap scaled by density, never above {@link CityManager#ABSOLUTE_MAX}. */
   static int levelCap(int maxTotal, double density) {
      return (int)Math.min(CityManager.ABSOLUTE_MAX, Math.round(maxTotal * Math.max(1.0, density)));
   }

   void tick(long now) {
      if (now % 20 != 7) {
         return;
      }

      ServerLevel level = this.city.level;
      List<ServerPlayer> players = new ArrayList<>();
      for (ServerPlayer p : level.players()) {
         if (!p.isSpectator() && this.city.profile.inArena(p.getBlockX(), level.getMinBuildHeight(), p.getBlockZ())) {
            players.add(p);
         }
      }

      // despawn: far from everyone
      for (CitizenEntity c : this.city.citizens()) {
         double best = Double.MAX_VALUE;
         for (ServerPlayer p : level.players()) {
            best = Math.min(best, p.distanceToSqr(c));
         }

         int far = c.manual ? MANUAL_FAR : FAR;
         if (best > (double)far * far) {
            c.discard();
         }
      }

      if (!this.city.enabled || this.city.restore.running() || players.isEmpty()) {
         return;
      }

      int cap = levelCap(this.city.profile.maxTotal, this.city.density);
      int per = Math.min(targetPerPlayer(this.city.profile.perPlayer, this.city.density), cap);
      int total = this.city.citizenCount();
      // density lowered: trim auto citizens nobody is looking at closely
      if (total > cap + 4) {
         int excess = total - cap;
         for (CitizenEntity c : this.city.citizens()) {
            if (excess <= 0) {
               break;
            }

            if (!c.manual && level.getNearestPlayer(c, 40.0) == null) {
               c.discard();
               excess--;
            }
         }

         return;
      }

      for (ServerPlayer p : players) {
         if (total >= cap) {
            return;
         }

         int near = level.getEntitiesOfClass(CitizenEntity.class, new AABB(p.blockPosition()).inflate(NEAR), c -> true).size();
         int want = Math.min(Math.min(per - near, SPAWNS_PER_PLAYER_PER_SECOND), cap - total);
         for (int i = 0; i < want; i++) {
            if (this.spawnNear(p.position(), 24.0, 64.0, false) != null) {
               total++;
            }
         }
      }
   }

   /** One citizen on a free preferred cell in the ring around a point; null when no cell was found. */
   CitizenEntity spawnNear(Vec3 at, double minR, double maxR, boolean manual) {
      ServerLevel level = this.city.level;
      WalkGrid grid = this.city.grid;
      long now = this.city.now();
      long cell = grid.randomCell(level.random::nextDouble, at.x, at.z, minR, maxR, true, 24, (x, y, z) -> {
         BlockPos pos = new BlockPos(x, y, z);
         if (!level.isPositionEntityTicking(pos) || this.city.danger.dangerous(x + 0.5, y, z + 0.5, now) || !level.getFluidState(pos).isEmpty()) {
            return false;
         }

         double fy = floorY(level, pos);
         return !Double.isNaN(fy) && level.noCollision(ViltrumiteEntities.CITIZEN.get().getAABB(x + 0.5, fy + 0.01, z + 0.5));
      });
      if (cell == Long.MIN_VALUE) {
         return null;
      }

      int x = WalkGrid.unpackX(cell);
      int z = WalkGrid.unpackZ(cell);
      int y = grid.feetY(x, z);
      CitizenEntity c = ViltrumiteEntities.CITIZEN.get().create(level);
      if (c == null) {
         return null;
      }

      c.moveTo(x + 0.5, floorY(level, new BlockPos(x, y, z)) + 0.01, z + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
      c.setSkin(level.random.nextInt(CitizenEntity.SKINS));
      c.manual = manual;
      c.finalizeSpawn(level, level.getCurrentDifficultyAt(c.blockPosition()), MobSpawnType.EVENT, null, null);
      level.addFreshEntity(c);
      return c;
   }

   /** Standing height on the feet block (slab, carpet or the block below), NaN when there is no floor. */
   static double floorY(ServerLevel level, BlockPos feet) {
      VoxelShape own = level.getBlockState(feet).getCollisionShape(level, feet);
      if (!own.isEmpty()) {
         double top = own.max(Direction.Axis.Y);
         return top <= 0.5 ? feet.getY() + top : Double.NaN;
      }

      BlockPos below = feet.below();
      VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
      return under.isEmpty() || under.max(Direction.Axis.Y) < 0.99 ? Double.NaN : feet.getY();
   }

   int populate(Vec3 at, int n) {
      int cap = CityManager.ABSOLUTE_MAX - this.city.citizenCount();
      int made = 0;
      for (int i = 0; i < Math.min(n, cap); i++) {
         if (this.spawnNear(at, 4.0, 48.0, true) != null) {
            made++;
         }
      }

      return made;
   }

   int clearAll() {
      int n = 0;
      for (CitizenEntity c : this.city.citizens()) {
         c.discard();
         n++;
      }

      return n;
   }
}

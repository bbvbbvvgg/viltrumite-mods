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
 * each player inside the arena gets citizens up to perPlayer x density x crowd scale
 * (config {@code cityCrowdScale}) within {@link #NEAR} blocks (a few per second, on free
 * preferred sidewalk/plaza cells 20-64 blocks away in entity-ticking chunks, never inside
 * walls, water or a danger anybody can see), capped per level (config
 * {@code cityMaxCitizens}, at most {@link CityManager#ABSOLUTE_MAX}). Some arrive as small
 * groups that walk together. Citizens more than {@link #FAR} blocks (manual: 128) from
 * every player, or left in chunks that stopped ticking entities, are removed; chunk unloads remove them too (they are never saved), so a
 * long session never accumulates entities.
 */
final class CityPopulation {
   static final int NEAR = 64;
   static final int FAR = 100;
   static final int MANUAL_FAR = 128;
   /** Trimming never removes a citizen this close to a player (it would vanish in plain sight). */
   static final int TRIM_KEEP = 40;
   static final int SPAWNS_PER_PLAYER_PER_SECOND = 8;
   /** Share of spawns that bring one or two companions. */
   static final float GROUP_CHANCE = 0.3F;
   /** People upstairs in tall buildings (issue #138): the most per player, whatever the config says. */
   static final int OCCUPANT_MAX = 16;
   static final int OCCUPANTS_PER_PASS = 2;
   static final double OCCUPANT_MIN_R = 10.0;
   static final double OCCUPANT_MAX_R = 48.0;
   /** Lowest occupied floor above the street: a fall from there is a real fall. */
   static final int OCCUPANT_FLOOR = 9;
   /** Only buildings at least this tall (above the street) get occupants. */
   static final int OCCUPANT_TALL = 20;
   private final CityManager city;

   CityPopulation(CityManager city) {
      this.city = city;
   }

   /** Citizens wanted around one player. */
   static int targetPerPlayer(int perPlayer, double density) {
      return (int)Math.round(Math.max(0.0, perPlayer * density));
   }

   /** Citizens wanted around one player with the crowd scale of the config. */
   static int targetPerPlayer(int perPlayer, double density, double crowdScale) {
      return targetPerPlayer(perPlayer, density * clampScale(crowdScale));
   }

   /** Level-wide cap: the profile cap scaled by density, never above {@link CityManager#ABSOLUTE_MAX}. */
   static int levelCap(int maxTotal, double density) {
      return (int)Math.min(CityManager.ABSOLUTE_MAX, Math.round(maxTotal * Math.max(1.0, density)));
   }

   /** Level cap with the crowd scale and the configured hard cap (a hard cap below 1 means no extra limit). */
   static int levelCap(int maxTotal, double density, double crowdScale, int hardCap) {
      int cap = levelCap(maxTotal, Math.max(1.0, density) * clampScale(crowdScale));
      return hardCap > 0 ? Math.min(cap, hardCap) : cap;
   }

   /**
    * Whether a citizen goes away: farther than {@link #FAR} (manual: {@link #MANUAL_FAR}) from
    * every player, or standing in a chunk that does not tick entities (loaded beyond the
    * simulation distance) while somebody is in the level - such citizens would freeze
    * mid-flight, stay "fleeing" forever and count against the cap. Console
    * {@code /city populate} with nobody online: manual citizens wait for the first player.
    *
    * @param bestSq squared distance to the nearest player, {@link Double#MAX_VALUE} with nobody in the level
    */
   static boolean shouldDespawn(boolean manual, double bestSq, boolean entityTicking) {
      if (bestSq == Double.MAX_VALUE) {
         return !manual;
      }

      int far = manual ? MANUAL_FAR : FAR;
      return bestSq > (double)far * far || !entityTicking;
   }

   /**
    * Automatic citizens to remove this second: everything above the level cap, and - when the
    * density was lowered - the automatic crowd above what the players want (per player x
    * players, a quarter more for the 64-100 block ring where walkers drift). Manual
    * citizens (/city populate) count against the cap only.
    */
   static int trimCount(int total, int auto, int cap, int per, int players) {
      int overCap = total > cap + 4 ? total - cap : 0;
      int want = Math.min(cap, per * Math.max(0, players));
      int keep = want + want / 4 + 4;
      int overWant = auto > keep ? auto - keep : 0;
      return Math.max(overCap, overWant);
   }

   /**
    * Occupants wanted around one player this pass (config {@code cityOccupants}, clamped to
    * {@link #OCCUPANT_MAX}; at most {@link #OCCUPANTS_PER_PASS} new ones every 5 s).
    */
   static int occupantsWanted(int configured, int present) {
      int want = Math.max(0, Math.min(OCCUPANT_MAX, configured)) - present;
      return Math.max(0, Math.min(OCCUPANTS_PER_PASS, want));
   }

   /** Floors an occupant may stand on: at least {@link #OCCUPANT_FLOOR} above the street, a few below the top; empty range = no. */
   static int[] occupantFloors(int street, int top) {
      if (top - street < OCCUPANT_TALL) {
         return null;
      }

      return new int[]{street + OCCUPANT_FLOOR, top - 3};
   }

   static double clampScale(double s) {
      return Double.isNaN(s) ? 1.0 : Math.max(0.0, Math.min(4.0, s));
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

         if (shouldDespawn(c.manual, best, level.isPositionEntityTicking(c.blockPosition())) && !dev.baranhan.viltrumitecore.city.citizen.CitizenCaptivity.isHeld(c)) {
            c.discard();
         }
      }

      if (!this.city.enabled || this.city.restore.running() || players.isEmpty()) {
         return;
      }

      dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig cfg = dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig.INSTANCE;
      int cap = levelCap(this.city.profile.maxTotal, this.city.density, cfg.cityCrowdScale, cfg.cityMaxCitizens);
      int per = Math.min(targetPerPlayer(this.city.profile.perPlayer, this.city.density, cfg.cityCrowdScale), cap);
      int total = this.city.citizenCount();
      int auto = 0;
      for (CitizenEntity c : this.city.citizens()) {
         if (!c.manual) {
            auto++;
         }
      }

      // density lowered (or above the level cap): trim automatic citizens nobody is looking
      // at closely, the farthest first
      int excess = trimCount(total, auto, cap, per, players.size());
      if (excess > 0) {
         List<CitizenEntity> spare = new ArrayList<>();
         List<Double> dist = new ArrayList<>();
         for (CitizenEntity c : this.city.citizens()) {
            if (c.manual || dev.baranhan.viltrumitecore.city.citizen.CitizenCaptivity.isHeld(c) || c.citizenState().peril()
               || dev.baranhan.viltrumitecore.city.citizen.WindowPeril.busy(c)) {
               continue; // never trimmed mid-fall, hanging on or at a window
            }

            double best = Double.MAX_VALUE;
            for (ServerPlayer p : level.players()) {
               best = Math.min(best, p.distanceToSqr(c));
            }

            if (best > TRIM_KEEP * TRIM_KEEP) {
               spare.add(c);
               dist.add(best);
            }
         }

         Integer[] order = new Integer[spare.size()];
         for (int i = 0; i < order.length; i++) {
            order[i] = i;
         }

         java.util.Arrays.sort(order, (a, b) -> Double.compare(dist.get(b), dist.get(a)));
         for (int i = 0; i < order.length && i < excess; i++) {
            spare.get(order[i]).discard();
         }

         return;
      }

      if (now % 100 == 47 && cfg.cityOccupants > 0) {
         total += this.occupy(players, cap - total, cfg.cityOccupants);
      }

      for (ServerPlayer p : players) {
         if (total >= cap) {
            return;
         }

         int near = level.getEntitiesOfClass(CitizenEntity.class, new AABB(p.blockPosition()).inflate(NEAR), c -> true).size();
         int want = Math.min(Math.min(per - near, SPAWNS_PER_PLAYER_PER_SECOND), cap - total);
         for (int i = 0; i < want; i++) {
            CitizenEntity c = this.spawnNear(p.position(), 20.0, 64.0, false);
            if (c != null) {
               total++;
               if (i + 1 < want && level.random.nextFloat() < GROUP_CHANCE) {
                  int extra = level.random.nextFloat() < 0.7F ? 1 : 2;
                  for (int k = 0; k < extra && i + 1 < want; k++) {
                     CitizenEntity f = this.spawnNear(c.position(), 0.8, 3.0, false);
                     if (f != null) {
                        f.leaderFollow(c);
                        total++;
                        i++;
                     }
                  }
               }
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
         if (!level.isPositionEntityTicking(pos) || this.city.danger.noticed(x + 0.5, y, z + 0.5, now) != null || !level.getFluidState(pos).isEmpty()) {
            return false;
         }

         // near a ruin people come back step by step: nobody while it is fresh, more and more as it quiets down
         float welcome = dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.welcome(level, x + 0.5, y, z + 0.5);
         if (welcome < 1.0F && level.random.nextFloat() >= welcome) {
            return false;
         }

         double fy = floorY(level, pos);
         // the whole body (tallest citizens included) must fit: never inside a wall, a sign or a car
         return !Double.isNaN(fy) && level.noCollision(ViltrumiteEntities.CITIZEN.get().getAABB(x + 0.5, fy + 0.01, z + 0.5).inflate(0.05, 0.15, 0.05).move(0.0, 0.15, 0.0));
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
      c.setSkin(level.random.nextInt());
      c.manual = manual;
      c.finalizeSpawn(level, level.getCurrentDifficultyAt(c.blockPosition()), MobSpawnType.EVENT, null, null);
      level.addFreshEntity(c);
      dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.onCitizenSpawn(level, c);
      return c;
   }

   /** Tops up the people upstairs around every player; returns how many arrived. */
   private int occupy(List<ServerPlayer> players, int room, int configured) {
      int made = 0;
      for (ServerPlayer p : players) {
         if (made >= room) {
            break;
         }

         int present = this.city.level.getEntitiesOfClass(CitizenEntity.class, new AABB(p.blockPosition()).inflate(NEAR), c -> c.occupant).size();
         int want = Math.min(occupantsWanted(configured, present), room - made);
         for (int i = 0; i < want; i++) {
            if (this.spawnUpstairs(p.position(), OCCUPANT_MIN_R, OCCUPANT_MAX_R, false) != null) {
               made++;
            }
         }
      }

      return made;
   }

   /**
    * One occupant on a floor of a tall building in the ring around a point: a column whose top
    * is {@link #OCCUPANT_TALL}+ above the nearest street, a structure floor at least
    * {@link #OCCUPANT_FLOOR} above it, under a roof, room for the whole body, no danger, no
    * fresh ruin. Null when nothing was found in a few tries.
    */
   CitizenEntity spawnUpstairs(Vec3 at, double minR, double maxR, boolean manual) {
      ServerLevel level = this.city.level;
      WalkGrid grid = this.city.grid;
      long now = this.city.now();
      dev.baranhan.viltrumitecore.world.collapse.CollapseAnalyzer.Volume vol = dev.baranhan.viltrumitecore.world.collapse.StructuralDamage.volume(level);
      for (int tries = 0; tries < 12; tries++) {
         double a = level.random.nextDouble() * Math.PI * 2.0;
         double r = minR + level.random.nextDouble() * (maxR - minR);
         int x = (int)Math.floor(at.x + Math.cos(a) * r);
         int z = (int)Math.floor(at.z + Math.sin(a) * r);
         if (!this.city.profile.inArena(x, level.getMinBuildHeight(), z) || !level.isPositionEntityTicking(new BlockPos(x, (int)Math.floor(at.y), z))) {
            continue;
         }

         long cell = grid.nearestCell(x, z, 12, null);
         if (cell == Long.MIN_VALUE) {
            continue;
         }

         int street = grid.feetY(WalkGrid.unpackX(cell), WalkGrid.unpackZ(cell));
         int[] floors = occupantFloors(street, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
         if (floors == null || floors[1] < floors[0]) {
            continue;
         }

         // a random height, then down to the first floor that fits
         for (int y = floors[0] + level.random.nextInt(floors[1] - floors[0] + 1); y >= floors[0]; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            double fy = floorY(level, feet);
            if (Double.isNaN(fy) || vol.at(x, y - 1, z) != dev.baranhan.viltrumitecore.world.collapse.CollapseAnalyzer.STRUCTURE
               || level.canSeeSky(feet) || !level.getFluidState(feet).isEmpty()) {
               continue;
            }

            if (!level.noCollision(ViltrumiteEntities.CITIZEN.get().getAABB(x + 0.5, fy + 0.01, z + 0.5).inflate(0.05, 0.15, 0.05).move(0.0, 0.15, 0.0))) {
               continue;
            }

            if (!manual && (seen(level, x + 0.5, fy + 1.0, z + 0.5) || crowdedFloor(level, x + 0.5, fy, z + 0.5))) {
               break; // never appears in plain sight (a window in front of a player); spread over floors and buildings
            }

            if (this.city.danger.noticed(x + 0.5, y, z + 0.5, now) != null
               || dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.avoid(level, x + 0.5, y, z + 0.5)) {
               break;
            }

            CitizenEntity c = ViltrumiteEntities.CITIZEN.get().create(level);
            if (c == null) {
               return null;
            }

            c.moveTo(x + 0.5, fy + 0.01, z + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            c.setSkin(level.random.nextInt());
            c.manual = manual;
            c.occupant = true;
            c.finalizeSpawn(level, level.getCurrentDifficultyAt(c.blockPosition()), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(c);
            dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.onCitizenSpawn(level, c);
            return c;
         }
      }

      return null;
   }

   /** Occupants on one floor of one building at most (the rest go to other floors and buildings). */
   static final int OCCUPANTS_PER_FLOOR = 3;
   /** A player closer than this who looks toward a spot (and sees it) would watch someone appear there. */
   static final double SEEN_RANGE = 96.0;

   /** True when the eyes look toward the spot: inside a cone of about 70 degrees half-angle around the view. */
   static boolean inView(double lookX, double lookY, double lookZ, double dx, double dy, double dz) {
      double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (d < 1.0E-6) {
         return true;
      }
      return (lookX * dx + lookY * dy + lookZ * dz) / d > 0.34;
   }

   /** Some player in range looks toward the spot and nothing blocks the line (a pop-in would be seen). */
   private static boolean seen(ServerLevel level, double x, double y, double z) {
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator() || p.distanceToSqr(x, y, z) > SEEN_RANGE * SEEN_RANGE) {
            continue;
         }
         Vec3 eye = p.getEyePosition();
         Vec3 look = p.getLookAngle();
         if (!inView(look.x, look.y, look.z, x - eye.x, y - eye.y, z - eye.z)) {
            continue;
         }
         if (level.clip(new net.minecraft.world.level.ClipContext(eye, new Vec3(x, y, z), net.minecraft.world.level.ClipContext.Block.VISUAL,
            net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return true;
         }
      }
      return false;
   }

   private static boolean crowdedFloor(ServerLevel level, double x, double y, double z) {
      return level.getEntitiesOfClass(CitizenEntity.class, new AABB(x - 10.0, y - 1.0, z - 10.0, x + 10.0, y + 2.0, z + 10.0), c -> c.occupant).size()
         >= OCCUPANTS_PER_FLOOR;
   }

   /** {@code /city occupants}: people upstairs around a point (manual, like /city populate). */
   int occupants(Vec3 at, int n) {
      int cap = CityManager.ABSOLUTE_MAX - this.city.citizenCount();
      int made = 0;
      for (int i = 0; i < Math.min(n, cap); i++) {
         if (this.spawnUpstairs(at, 0.0, 32.0, true) != null) {
            made++;
         }
      }

      return made;
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

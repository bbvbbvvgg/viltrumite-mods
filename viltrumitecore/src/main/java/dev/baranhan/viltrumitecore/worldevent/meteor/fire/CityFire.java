package dev.baranhan.viltrumitecore.worldevent.meteor.fire;

import dev.baranhan.viltrumitecore.block.MeteorFireBlock;
import dev.baranhan.viltrumitecore.block.ViltrumiteBlocks;
import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.restore.RestoreQueue;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The spreading city fire of the meteor rain (Meteor Rain 2.0), server side. Memory only: a restart ends
 * every spot and the {@link MeteorFireBlock} flames then go out by themselves.
 *
 * <p>Safety: every world read checks {@code isLoaded} first (fire never loads a chunk); spots of an
 * unloading chunk are dropped; spots, brands and the burnt memory are capped; a tick visits at most
 * {@link FireRules#VISITS_PER_TICK} spots and writes at most {@link FireRules#WRITES_PER_TICK} blocks.
 * All writes are plain {@code setBlock} calls, so the living city journal records them and {@code /city
 * reset} restores them; a reset (seen through the city's {@link RestoreQueue}) clears every spot and
 * brand first, so nothing re-ignites from old queues.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class CityFire {
   private static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/CityFire");
   /** Ticks between two keep-alive checks of a flame block. */
   public static final int FLAME_CHECK_TICKS = 30;
   private static final boolean QA_LOG = Boolean.getBoolean("viltrumitecore.cityfire.log");
   private static final Map<ResourceKey<Level>, Field> FIELDS = new HashMap<>();
   private static final Direction[] FLAME_ORDER = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

   private CityFire() {
   }

   static final class Spot {
      final long pos;
      final int material;
      float heat;
      int fuel;
      int smoulder;
      long lastVisit;

      Spot(long pos, int material, float heat, int fuel, long now) {
         this.pos = pos;
         this.material = material;
         this.heat = heat;
         this.fuel = fuel;
         this.lastVisit = now;
      }

      boolean burning() {
         return this.fuel > 0;
      }
   }

   record Brand(BlockPos target, long landTick) {
   }

   static final class Field {
      final Long2ObjectOpenHashMap<Spot> spots = new Long2ObjectOpenHashMap<>();
      final ArrayDeque<Long> order = new ArrayDeque<>();
      final LongOpenHashSet burnt = new LongOpenHashSet();
      final LongArrayFIFOQueue burntOrder = new LongArrayFIFOQueue();
      final List<Brand> brands = new ArrayList<>();
      final Set<UUID> zoneViewers = new HashSet<>();
      final Random random = new Random();
      double wind;
      long lastBrand;
      long resetSeen = Long.MIN_VALUE;
      int writes;
      long nanos;
      long visits;

      Field(long seed) {
         this.random.setSeed(seed);
         this.wind = this.random.nextDouble() * Math.PI * 2.0;
      }

      boolean canWrite() {
         return this.writes < FireRules.WRITES_PER_TICK;
      }

      void clear() {
         this.spots.clear();
         this.order.clear();
         this.brands.clear();
      }

      void remember(long pos) {
         if (this.burnt.add(pos)) {
            this.burntOrder.enqueue(pos);
            while (this.burntOrder.size() > FireRules.BURNT_MEMORY) {
               this.burnt.remove(this.burntOrder.dequeueLong());
            }
         }
      }
   }

   // ------------------------------------------------------------------ public API

   /** A rain meteor hit the ground at {@code at}: light spots around the crater and on facades it faces. */
   public static void meteorImpact(ServerLevel level, Vec3 at) {
      if (blocked(level)) {
         return;
      }

      Field f = field(level);
      long now = level.getGameTime();
      Random r = f.random;
      int budget = FireRules.IMPACT_WRITES;
      int count = FireRules.IMPACT_SPOTS_MIN + r.nextInt(FireRules.IMPACT_SPOTS_MAX - FireRules.IMPACT_SPOTS_MIN + 1);
      for (int i = 0; i < count; i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double d = FireRules.IMPACT_RING_MIN + r.nextDouble() * (FireRules.IMPACT_RING_MAX - FireRules.IMPACT_RING_MIN);
         int x = (int)Math.floor(at.x + Math.cos(a) * d);
         int z = (int)Math.floor(at.z + Math.sin(a) * d);
         BlockPos column = new BlockPos(x, (int)at.y, z);
         if (!level.isLoaded(column)) {
            continue;
         }

         int top = level.getHeight(Types.MOTION_BLOCKING, x, z) - 1;
         if (top <= level.getMinBuildHeight()) {
            continue;
         }

         Spot s = ignite(level, f, new BlockPos(x, top, z), 0.85F, now);
         if (s != null && budget > 0) {
            budget -= flames(level, f, s, BlockPos.of(s.pos), 2, true);
         }
      }

      // facades: horizontal rays at a few heights above the crater
      for (int i = 0; i < FireRules.IMPACT_FACADE_RAYS; i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         Vec3 from = at.add(0.0, 1.5 + r.nextDouble() * 6.0, 0.0);
         BlockPos wall = ray(level, from, Math.cos(a), r.nextDouble() * 0.3 - 0.1, Math.sin(a), FireRules.FACADE_RAY_RANGE);
         if (wall != null) {
            Spot s = ignite(level, f, wall, 0.9F, now);
            if (s != null && budget > 0) {
               budget -= flames(level, f, s, wall, 2, true);
            }
         }
      }
   }

   /** True when a live (open burning) spot touches the cell {@code pos}: the flame there stays lit. */
   public static boolean sustains(ServerLevel level, BlockPos pos) {
      Field f = FIELDS.get(level.dimension());
      if (f == null || f.spots.isEmpty()) {
         return false;
      }

      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (Direction d : Direction.values()) {
         Spot s = f.spots.get(m.setWithOffset(pos, d).asLong());
         if (s != null && s.burning()) {
            return true;
         }
      }

      return false;
   }

   public static int spotCount(ServerLevel level) {
      Field f = FIELDS.get(level.dimension());
      return f == null ? 0 : f.spots.size();
   }

   // ------------------------------------------------------------------ tick

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent event) {
      if (event.phase != Phase.END || FIELDS.isEmpty()) {
         return;
      }

      MinecraftServer server = event.getServer();
      Iterator<Map.Entry<ResourceKey<Level>, Field>> it = FIELDS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<ResourceKey<Level>, Field> e = it.next();
         ServerLevel level = server.getLevel(e.getKey());
         if (level == null) {
            it.remove();
            continue;
         }

         Field f = e.getValue();
         if (resetting(level, f)) {
            continue;
         }

         long t0 = System.nanoTime();
         tickField(level, f);
         f.nanos += System.nanoTime() - t0;
         long now = level.getGameTime();
         if (now % FireRules.ZONE_SYNC_TICKS == 0L) {
            syncZones(level, f, now % FireRules.DANGER_TICKS == 0L);
         }

         if (QA_LOG && now % 600L == 0L) {
            LOG.info("City fire {}: {} spots, {} brands, {} visits, {} ms total CPU in the last 30 s", level.dimension().location(),
               f.spots.size(), f.brands.size(), f.visits, f.nanos / 1_000_000L);
            f.nanos = 0L;
            f.visits = 0L;
         }

         if (f.spots.isEmpty() && f.brands.isEmpty() && f.zoneViewers.isEmpty()) {
            it.remove();
         }
      }
   }

   private static void tickField(ServerLevel level, Field f) {
      long now = level.getGameTime();
      f.writes = 0;
      if (now % 20L == 0L) {
         f.wind += (f.random.nextDouble() - 0.5) * 0.08;
      }

      landBrands(level, f, now);
      int visits = Math.min(FireRules.VISITS_PER_TICK, f.order.size());
      int guard = visits * 3 + 8;
      while (visits > 0 && guard-- > 0 && !f.order.isEmpty() && f.canWrite()) {
         long key = f.order.pollFirst();
         Spot s = f.spots.get(key);
         if (s == null) {
            continue; // removed earlier: drop the stale order entry
         }

         visits--;
         f.visits++;
         if (visit(level, f, s, now)) {
            f.order.addLast(key);
         } else {
            f.spots.remove(key);
         }
      }
   }

   /** One visit of a spot; false = forget it. */
   private static boolean visit(ServerLevel level, Field f, Spot s, long now) {
      BlockPos pos = BlockPos.of(s.pos);
      if (!level.isLoaded(pos) || !level.shouldTickBlocksAt(pos)) {
         return false;
      }

      int dt = (int)Math.max(1L, Math.min(200L, now - s.lastVisit));
      s.lastVisit = now;
      BlockState state = level.getBlockState(pos);
      if (FireMaterial.of(state, level, pos) == FireRules.INERT) {
         // the surface is gone (exploded, burnt, mined): the fire falls onto what is left around it
         if (s.burning() && f.random.nextInt(2) == 0) {
            relocate(level, f, s, pos, now);
         }
         return false;
      }

      if (wet(level, pos)) {
         s.heat -= 0.03F * dt;
         s.fuel -= dt * 2;
         if (s.heat <= 0.05F) {
            return false;
         }
      }

      if (!s.burning()) {
         s.smoulder -= dt;
         s.heat *= (float)Math.pow(0.985, dt / 10.0);
         return s.smoulder > 0;
      }

      int crowd = neighbours(f, pos);
      s.heat = FireRules.heatStep(s.heat, FireRules.targetHeat(s.material, crowd), dt);
      s.fuel -= dt;
      flames(level, f, s, pos, s.heat > 0.7F ? 2 : 1, false);
      Random r = f.random;
      if (s.material == FireRules.FRAGILE && f.canWrite() && r.nextFloat() < FireRules.shatterChance(s.heat, dt)) {
         // a window bursts or leaves flash away: the opening lets the fire inside
         dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.hint(level, dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause.METEOR, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6.0F);
         level.destroyBlock(pos, false);
         f.writes++;
         flare(level, pos, 0.6F);
         relocate(level, f, s, pos, now);
         return false;
      }

      if (f.spots.size() < FireRules.MAX_SPOTS && r.nextFloat() < FireRules.spreadChance(s.heat, dt)) {
         for (int attempt = 0; attempt < 3; attempt++) {
            int[] o = FireRules.spreadOffset(r);
            BlockPos target = pos.offset(o[0], o[1], o[2]);
            int mat = catchable(level, f, target);
            if (mat != FireRules.INERT && r.nextFloat() < FireRules.catchFactor(mat) * (0.5F + 0.5F * s.heat)) {
               Spot child = ignite(level, f, target, 0.35F + 0.35F * s.heat, now);
               if (child != null) {
                  flames(level, f, child, target, 1, false);
               }
               break;
            }
         }
      }

      if (f.brands.size() < FireRules.MAX_BRANDS && now - f.lastBrand >= FireRules.BRAND_GAP_TICKS
         && r.nextFloat() < FireRules.brandChance(s.heat, dt)) {
         throwBrand(level, f, pos, now);
      }

      if (s.fuel <= 0) {
         burnOut(level, f, s, pos, state);
         s.smoulder = FireRules.SMOULDER_TICKS + r.nextInt(200);
         s.fuel = 0;
      }

      return true;
   }

   /** Writes up to {@code max} missing flames into the open cells around a spot; returns writes made. */
   private static int flames(ServerLevel level, Field f, Spot s, BlockPos pos, int max, boolean impact) {
      MeteorFireBlock block = (MeteorFireBlock)ViltrumiteBlocks.METEOR_FIRE.get();
      int made = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int start = s.material == FireRules.GROUND || f.random.nextInt(3) > 0 ? 0 : 1 + f.random.nextInt(4);
      for (int i = 0; i < FLAME_ORDER.length && made < max; i++) {
         Direction d = FLAME_ORDER[(start + i) % FLAME_ORDER.length];
         m.setWithOffset(pos, d);
         if (!level.isLoaded(m)) {
            continue;
         }

         BlockState there = level.getBlockState(m);
         if (there.getBlock() instanceof BaseFireBlock) {
            continue;
         }

         if (!there.isAir() || !impact && !f.canWrite()) {
            continue;
         }

         BlockState flame = block.stateAt(level, m);
         if (MeteorFireBlock.anchored(flame, level, m)) {
            level.setBlock(m, flame, Block.UPDATE_ALL);
            f.writes++;
            made++;
         }
      }

      return made;
   }

   private static void burnOut(ServerLevel level, Field f, Spot s, BlockPos pos, BlockState state) {
      f.remember(s.pos);
      if (!f.canWrite()) {
         return;
      }

      BlockState above = level.isLoaded(pos.above()) ? level.getBlockState(pos.above()) : null;
      boolean roof = above != null && (above.isAir() || above.getBlock() instanceof BaseFireBlock);
      BlockState left = FireMaterial.burnt(state, s.material, f.random, roof);
      if (left == null) {
         return;
      }

      if (left.isAir()) {
         // burnt out: the aftermath of this ruin is the meteor fire's
         dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.hint(level, dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause.METEOR, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6.0F);
         level.destroyBlock(pos, false);
      } else {
         level.setBlock(pos, left, Block.UPDATE_ALL);
      }
      f.writes++;
   }

   /** The fire drops onto a neighbour surface of a spot whose block is gone. */
   private static void relocate(ServerLevel level, Field f, Spot s, BlockPos pos, long now) {
      for (int attempt = 0; attempt < 4; attempt++) {
         BlockPos next = pos.offset(f.random.nextInt(3) - 1, -f.random.nextInt(3), f.random.nextInt(3) - 1);
         if (catchable(level, f, next) != FireRules.INERT) {
            Spot child = ignite(level, f, next, Math.max(0.3F, s.heat * 0.8F), now);
            if (child != null) {
               flames(level, f, child, next, 1, false);
            }
            return;
         }
      }
   }

   private static void throwBrand(ServerLevel level, Field f, BlockPos from, long now) {
      Random r = f.random;
      double angle = FireRules.brandAngle(f.wind, r);
      double along = Math.cos(angle - f.wind);
      double dist = FireRules.brandDistance(along, r);
      double dx = Math.cos(angle);
      double dz = Math.sin(angle);
      BlockPos target = null;
      if (r.nextInt(5) < 2) {
         // straight at a facade across the street
         target = ray(level, new Vec3(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5), dx, -0.05, dz, (int)Math.ceil(dist) + 4);
      }

      if (target == null) {
         int x = (int)Math.floor(from.getX() + 0.5 + dx * dist);
         int z = (int)Math.floor(from.getZ() + 0.5 + dz * dist);
         target = landing(level, x, from.getY(), z);
      }

      if (target == null || target.distManhattan(from) < 4) {
         return;
      }

      int ticks = FireRules.brandFlightTicks(Math.sqrt(from.distSqr(target)));
      f.brands.add(new Brand(target, now + ticks));
      f.lastBrand = now;
      Vec3 a = Vec3.atCenterOf(from).add(0.0, 0.6, 0.0);
      Vec3 b = Vec3.atCenterOf(target).add(0.0, 0.6, 0.0);
      MeteorRainFxS2CPacket packet = new MeteorRainFxS2CPacket(MeteorRainFxS2CPacket.BRAND, a, 1.0F,
         new float[]{(float)b.x, (float)b.y, (float)b.z, ticks});
      sendNear(level, a, 192.0, packet);
   }

   /** The first block a brand could land on in this column, scanning down from a little above the source. */
   private static BlockPos landing(ServerLevel level, int x, int fromY, int z) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, fromY + 4, z);
      if (!level.isLoaded(m)) {
         return null;
      }

      int min = Math.max(level.getMinBuildHeight(), fromY - 18);
      for (int y = fromY + 4; y >= min; y--) {
         m.setY(y);
         BlockState s = level.getBlockState(m);
         if (!s.isAir() && !(s.getBlock() instanceof BaseFireBlock)) {
            return s.getFluidState().isEmpty() ? m.immutable() : null;
         }
      }

      return null;
   }

   private static void landBrands(ServerLevel level, Field f, long now) {
      if (f.brands.isEmpty()) {
         return;
      }

      Iterator<Brand> it = f.brands.iterator();
      while (it.hasNext()) {
         Brand b = it.next();
         if (b.landTick > now) {
            continue;
         }

         it.remove();
         if (f.spots.size() >= FireRules.MAX_SPOTS || !level.isLoaded(b.target)) {
            continue;
         }

         Spot s = ignite(level, f, b.target, 0.72F, now);
         if (s != null) {
            flames(level, f, s, b.target, 2, true);
            flare(level, b.target, 0.8F);
         }
      }
   }

   // ------------------------------------------------------------------ helpers

   /** A new spot at {@code pos} if it is a loaded, burnable, exposed surface not burning and not burnt. */
   private static Spot ignite(ServerLevel level, Field f, BlockPos pos, float heat, long now) {
      int mat = catchable(level, f, pos);
      if (mat == FireRules.INERT || f.spots.size() >= FireRules.MAX_SPOTS) {
         return null;
      }

      long key = pos.asLong();
      Spot s = new Spot(key, mat, Math.min(1.0F, heat), FireRules.fuelTicks(mat, f.random), now);
      f.spots.put(key, s);
      f.order.addLast(key);
      return s;
   }

   /** Material of a surface a new spot may take, or INERT (unloaded, burning, burnt, unexposed, protected). */
   private static int catchable(ServerLevel level, Field f, BlockPos pos) {
      long key = pos.asLong();
      if (f.spots.containsKey(key) || f.burnt.contains(key) || !level.isLoaded(pos)) {
         return FireRules.INERT;
      }

      BlockState state = level.getBlockState(pos);
      int mat = FireMaterial.of(state, level, pos);
      if (mat == FireRules.INERT || !exposed(level, pos)) {
         return FireRules.INERT;
      }

      return mat;
   }

   private static boolean exposed(ServerLevel level, BlockPos pos) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (Direction d : Direction.values()) {
         m.setWithOffset(pos, d);
         if (level.isLoaded(m)) {
            BlockState s = level.getBlockState(m);
            if (s.isAir() || s.getBlock() instanceof BaseFireBlock) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean wet(ServerLevel level, BlockPos pos) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (Direction d : Direction.values()) {
         m.setWithOffset(pos, d);
         if (level.isLoaded(m)) {
            FluidState fluid = level.getFluidState(m);
            if (!fluid.isEmpty() && fluid.is(net.minecraft.tags.FluidTags.WATER)) {
               return true;
            }
         }
      }

      return level.isRainingAt(pos.above());
   }

   private static int neighbours(Field f, BlockPos pos) {
      int n = 0;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               if ((dx | dy | dz) != 0) {
                  Spot s = f.spots.get(BlockPos.asLong(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                  if (s != null && s.burning()) {
                     n++;
                  }
               }
            }
         }
      }

      return n;
   }

   /** Steps a ray through loaded blocks only; the first non-air, non-fire block, or null. */
   private static BlockPos ray(ServerLevel level, Vec3 from, double dx, double dy, double dz, int range) {
      double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (len < 1.0E-6) {
         return null;
      }

      dx /= len;
      dy /= len;
      dz /= len;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (double t = 1.0; t <= range; t += 0.5) {
         m.set(from.x + dx * t, from.y + dy * t, from.z + dz * t);
         if (!level.isLoaded(m)) {
            return null;
         }

         BlockState s = level.getBlockState(m);
         if (!s.isAir() && !(s.getBlock() instanceof BaseFireBlock)) {
            return s.getFluidState().isEmpty() ? m.immutable() : null;
         }
      }

      return null;
   }

   private static void flare(ServerLevel level, BlockPos pos, float power) {
      Vec3 at = Vec3.atCenterOf(pos);
      sendNear(level, at, 96.0, new MeteorRainFxS2CPacket(MeteorRainFxS2CPacket.FLARE, at, power, null));
   }

   private static void sendNear(ServerLevel level, Vec3 at, double range, MeteorRainFxS2CPacket packet) {
      double r2 = range * range;
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(at) <= r2) {
            CoreMessages.sendToPlayer(packet, p);
         }
      }
   }

   private static boolean blocked(ServerLevel level) {
      CityManager city = CityManager.of(level);
      return city != null && city.restoreQueue().running();
   }

   /** True while the city of this level is being reset; the first sight of a reset clears the field. */
   private static boolean resetting(ServerLevel level, Field f) {
      CityManager city = CityManager.of(level);
      if (city == null) {
         return false;
      }

      RestoreQueue q = city.restoreQueue();
      long started = q.startedAt();
      boolean fresh = f.resetSeen != Long.MIN_VALUE && started != f.resetSeen;
      if (f.resetSeen == Long.MIN_VALUE) {
         f.resetSeen = started;
      }

      if (q.running() || fresh) {
         f.resetSeen = started;
         if (!f.spots.isEmpty() || !f.brands.isEmpty()) {
            LOG.info("City reset: cleared {} fire spots and {} brands", f.spots.size(), f.brands.size());
            f.clear();
         }
         return q.running();
      }

      return false;
   }

   private static Field field(ServerLevel level) {
      return FIELDS.computeIfAbsent(level.dimension(), key -> new Field(level.getSeed() ^ level.getGameTime()));
   }

   // ------------------------------------------------------------------ zones (clients, citizens)

   private static void syncZones(ServerLevel level, Field f, boolean danger) {
      Map<Long, float[]> cells = new HashMap<>();
      for (Spot s : f.spots.values()) {
         int x = BlockPos.getX(s.pos);
         int y = BlockPos.getY(s.pos);
         int z = BlockPos.getZ(s.pos);
         long cell = BlockPos.asLong(Math.floorDiv(x, FireRules.ZONE_CELL), Math.floorDiv(y, FireRules.ZONE_CELL), Math.floorDiv(z, FireRules.ZONE_CELL));
         float[] c = cells.computeIfAbsent(cell, k -> new float[6]);
         c[0] += x + 0.5F;
         c[1] += y + 1.0F;
         c[2] += z + 0.5F;
         c[3] += s.burning() ? s.heat : s.heat * 0.25F;
         c[4] += 1.0F;
         c[5] = Math.max(c[5], s.burning() ? 1.0F : 0.0F);
      }

      List<float[]> zones = new ArrayList<>(cells.size());
      for (float[] c : cells.values()) {
         float n = c[4];
         float intensity = FireRules.zoneIntensity((int)n, c[3] / n);
         if (c[5] == 0.0F) {
            intensity *= 0.35F; // only smouldering: smoke, no glow
         }
         zones.add(new float[]{c[0] / n, c[1] / n, c[2] / n, intensity, Math.min(12.0F, 2.0F + (float)Math.sqrt(n) * 1.2F), n});
      }

      zones.sort((a, b) -> Float.compare(b[3], a[3]));
      if (danger) {
         CityManager city = CityManager.of(level);
         if (city != null) {
            long cityNow = city.now();
            for (float[] z : zones) {
               if (z[5] >= 3.0F && z[3] > 0.15F) {
                  city.dangerMap().add(z[0], z[1], z[2], FireRules.dangerRadius((int)z[5]), cityNow, 100);
               }
            }
         }
      }

      double r2 = FireRules.ZONE_RANGE * FireRules.ZONE_RANGE;
      for (ServerPlayer p : level.players()) {
         List<float[]> near = new ArrayList<>();
         for (float[] z : zones) {
            double dx = z[0] - p.getX();
            double dz = z[2] - p.getZ();
            if (dx * dx + dz * dz <= r2) {
               near.add(z);
               if (near.size() >= MeteorRainFxS2CPacket.MAX_ZONES) {
                  break;
               }
            }
         }

         if (near.isEmpty() && !f.zoneViewers.contains(p.getUUID())) {
            continue;
         }

         float[] data = new float[near.size() * MeteorRainFxS2CPacket.ZONE_STRIDE];
         for (int i = 0; i < near.size(); i++) {
            System.arraycopy(near.get(i), 0, data, i * MeteorRainFxS2CPacket.ZONE_STRIDE, MeteorRainFxS2CPacket.ZONE_STRIDE);
         }

         CoreMessages.sendToPlayer(new MeteorRainFxS2CPacket(MeteorRainFxS2CPacket.FIRE_ZONES, p.position(), 0.0F, data), p);
         if (near.isEmpty()) {
            f.zoneViewers.remove(p.getUUID());
         } else {
            f.zoneViewers.add(p.getUUID());
         }
      }

      f.zoneViewers.removeIf(id -> level.getPlayerByUUID(id) == null);
   }

   // ------------------------------------------------------------------ lifecycle

   /** A player punched a flame out: the spots it touches lose heat and fuel (ordinary firefighting). */
   @SubscribeEvent
   public static void onBreak(BlockEvent.BreakEvent event) {
      if (!(event.getLevel() instanceof ServerLevel level) || !(event.getState().getBlock() instanceof BaseFireBlock)) {
         return;
      }

      Field f = FIELDS.get(level.dimension());
      if (f == null) {
         return;
      }

      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (Direction d : Direction.values()) {
         Spot s = f.spots.get(m.setWithOffset(event.getPos(), d).asLong());
         if (s != null && s.burning()) {
            s.heat = Math.max(0.0F, s.heat - 0.3F);
            s.fuel = (int)(s.fuel * 0.6F) - 60;
            if (s.fuel <= 0) {
               s.fuel = 0;
               s.smoulder = FireRules.SMOULDER_TICKS / 3;
            }
         }
      }
   }

   @SubscribeEvent
   public static void onChunkUnload(ChunkEvent.Unload event) {
      if (!(event.getLevel() instanceof ServerLevel level)) {
         return;
      }

      Field f = FIELDS.get(level.dimension());
      if (f == null || f.spots.isEmpty() && f.brands.isEmpty()) {
         return;
      }

      int cx = event.getChunk().getPos().x;
      int cz = event.getChunk().getPos().z;
      f.spots.values().removeIf(s -> BlockPos.getX(s.pos) >> 4 == cx && BlockPos.getZ(s.pos) >> 4 == cz);
      f.brands.removeIf(b -> b.target.getX() >> 4 == cx && b.target.getZ() >> 4 == cz);
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         FIELDS.remove(level.dimension());
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      for (Field f : FIELDS.values()) {
         f.zoneViewers.remove(event.getEntity().getUUID());
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      FIELDS.clear();
   }
}

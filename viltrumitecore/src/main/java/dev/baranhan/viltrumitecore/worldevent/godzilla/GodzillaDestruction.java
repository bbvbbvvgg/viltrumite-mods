package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.hero.CarveBudget;
import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.QuietBlockRemoval;
import dev.baranhan.viltrumitecore.world.collapse.CollapseCause;
import dev.baranhan.viltrumitecore.world.collapse.StructuralDamage;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathEvent;
import dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * Every block the kaiju removes goes through here: the cause scope {@code godzilla} (StructuralDamage feeds the
 * partial collapses), mobGriefing, loaded chunks only (never loads one), inside the city arena when there is a city
 * (so the city journal records every change and {@code /city reset} restores it), a per-tick budget that falls with
 * the server's tick time, a per-session cap and a cap on flying debris. Big hits are queued as smash jobs worked
 * through over the next ticks, inner blocks first. Reports to the city systems (ruin zones, the power grid, the
 * danger map, structural impacts) are throttled per area.
 */
public final class GodzillaDestruction {
   public static final CollapseCause CAUSE = CollapseCause.custom("godzilla", 1.0F);
   /** Positions one smash job may scan per tick (the scan is cheap: whole empty sections are skipped). */
   static final int SCAN_PER_TICK = 60000;
   /** Jobs kept at once; the oldest low-priority ones are dropped first. */
   static final int MAX_JOBS = 48;
   /** Debris still flying after this many ticks is removed (it is stuck or frozen in a chunk nobody ticks). */
   static final int DEBRIS_LIFE = 600;

   /** A queued smash: an ellipsoid (vertical radius = radius / squash) removed inner blocks first. */
   static final class Smash {
      final Vec3 centre;
      final double radius;
      final double squash;
      final float hardness;
      final Vec3 push;
      final float debris;
      final int priority;
      final String kind;
      long[] cells = new long[0];
      int[] order = new int[0];
      int count;
      int next;
      boolean scanned;
      int removed;
      final List<BlockPos> sample = new ArrayList<>();

      Smash(Vec3 centre, double radius, double squash, float hardness, Vec3 push, float debris, int priority, String kind) {
         this.centre = centre;
         this.radius = radius;
         this.squash = squash;
         this.hardness = hardness;
         this.push = push;
         this.debris = debris;
         this.priority = priority;
         this.kind = kind;
      }

      boolean done() {
         return this.scanned && this.next >= this.count;
      }
   }

   private final String sessionId;
   private final List<Smash> jobs = new ArrayList<>();
   /** Debris thrown by the event, with the game time of the throw (debris outside the ticking range never land). */
   private final ArrayDeque<Flying> flying = new ArrayDeque<>();
   /** The way out: the whole session budget is open (see {@link GodzillaRules#RETREAT_RESERVE}). */
   private boolean retreat;
   private final Long2LongOpenHashMap reported = new Long2LongOpenHashMap();
   private final Long2LongOpenHashMap infra = new Long2LongOpenHashMap();
   private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
   private int destroyed;
   private int budget;
   private int usedThisTick;
   /** Smoothed share of the tick budget used (the walk wades through dense blocks, see {@link GodzillaRules#wade}). */
   private float load;
   private long tick;
   private boolean griefing = true;
   private int buildingsHit;

   GodzillaDestruction(String sessionId) {
      this.sessionId = sessionId;
      this.reported.defaultReturnValue(Long.MIN_VALUE);
      this.infra.defaultReturnValue(Long.MIN_VALUE);
   }

   public int destroyed() {
      return this.destroyed;
   }

   public int jobs() {
      return this.jobs.size();
   }

   public int flying() {
      return this.flying.size();
   }

   public int buildingsHit() {
      return this.buildingsHit;
   }

   public float load() {
      return this.load;
   }

   public boolean exhausted() {
      return this.destroyed >= GodzillaRules.blockCap(this.retreat);
   }

   /** The rampage has used its share of the session budget (the event sends the kaiju away). */
   public boolean sated() {
      return GodzillaRules.sated(this.destroyed);
   }

   /** Start of a server tick: the budget of this tick and the queued jobs. */
   void tick(ServerLevel level, boolean rage, boolean retreat) {
      this.tick = level.getGameTime();
      this.retreat = retreat;
      this.griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
      float used = this.budget > 0 ? Math.min(1.0F, this.usedThisTick / (float)this.budget) : 0.0F;
      this.load += (used - this.load) * 0.08F;
      this.budget = CarveBudget.perTick(GodzillaRules.blockBudget(rage), level.getServer().getAverageTickTime());
      this.usedThisTick = 0;
      this.pruneFlying();
      if (this.exhausted()) {
         this.jobs.clear(); // nothing more may break: queued smashes would wait forever
      }
      if (this.jobs.isEmpty()) {
         return;
      }
      this.jobs.sort((a, b) -> Integer.compare(b.priority, a.priority));
      Iterator<Smash> it = this.jobs.iterator();
      while (it.hasNext() && this.left() > 0) {
         Smash s = it.next();
         this.work(level, s, this.left());
         if (s.done()) {
            it.remove();
            this.finished(level, s);
         }
      }
      if (this.reported.size() > 4096) {
         this.reported.clear();
      }
      if (this.infra.size() > 4096) {
         this.infra.clear();
      }
   }

   private int left() {
      return Math.max(0, Math.min(this.budget - this.usedThisTick, GodzillaRules.blockCap(this.retreat) - this.destroyed));
   }

   /** Queues a smash (the job starts working this tick if the budget allows). */
   void queue(Vec3 centre, double radius, double squash, float hardness, Vec3 push, float debris, int priority, String kind) {
      if (!this.griefing || this.exhausted()) {
         return;
      }
      if (this.jobs.size() >= MAX_JOBS) {
         Smash weakest = null;
         for (Smash s : this.jobs) {
            if (weakest == null || s.priority < weakest.priority) {
               weakest = s;
            }
         }
         if (weakest == null || weakest.priority > priority) {
            return;
         }
         this.jobs.remove(weakest);
      }
      this.jobs.add(new Smash(centre, radius, squash, hardness, push, debris, priority, kind));
   }

   /** Removes what it can now (feet, claws, the beam): at most {@code limit} blocks. Returns blocks removed. */
   int smashNow(ServerLevel level, Vec3 centre, double radius, double squash, float hardness, Vec3 push, float debris, int limit, String kind) {
      if (!this.griefing || this.exhausted()) {
         return 0;
      }
      Smash s = new Smash(centre, radius, squash, hardness, push, debris, 100, kind);
      this.work(level, s, Math.min(limit, this.left()));
      if (s.removed > 0) {
         this.finished(level, s);
      }
      return s.removed;
   }

   private void work(ServerLevel level, Smash s, int limit) {
      if (!s.scanned) {
         this.scan(level, s);
      }
      if (limit <= 0 || s.count == 0) {
         return;
      }
      int[] used = {0};
      RandomSource random = level.getRandom();
      StructuralDamage.run(CAUSE, () -> {
         BlockPos.MutableBlockPos pos = this.cursor;
         while (s.next < s.count && used[0] < limit) {
            long p = s.cells[s.order[s.next++]];
            pos.set(p);
            if (!level.isLoaded(pos)) {
               continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
               continue;
            }
            float h = state.getDestroySpeed(level, pos);
            if (h < 0.0F || h > s.hardness) {
               continue;
            }
            BlockPos at = pos.immutable();
            if (s.debris > 0.0F && this.flying.size() < GodzillaRules.MAX_DEBRIS && random.nextFloat() < s.debris
               && HeroDebris.canLaunch(level, at, state)) {
               Vec3 out = Vec3.atCenterOf(at).subtract(s.centre);
               double len = out.length();
               out = len < 1.0E-3 ? new Vec3(0.0, 1.0, 0.0) : out.scale(1.0 / len);
               Vec3 vel = new Vec3(out.x, Math.abs(out.y) + 0.5, out.z).normalize().scale(0.45 + random.nextDouble() * 0.55).add(s.push);
               this.flying.add(new Flying(HeroDebris.launchBlock(level, at, state, vel, 2.0F, 16, HeroDestruction.CLEAR_FLAGS), this.tick));
            } else {
               QuietBlockRemoval.run(() -> level.setBlock(at, Blocks.AIR.defaultBlockState(), HeroDestruction.CLEAR_FLAGS));
            }
            used[0]++;
            if (s.sample.size() < 64 && (s.removed & 7) == 0) {
               s.sample.add(at);
            }
            s.removed++;
         }
      });
      this.usedThisTick += used[0];
      this.destroyed += used[0];
   }

   /** Collects the breakable blocks of the job (inner first). Skips unloaded chunks, empty sections, the outside of the city. */
   private void scan(ServerLevel level, Smash s) {
      s.scanned = true;
      CityManager city = CityManager.of(level);
      CityProfile arena = city != null ? city.profile() : null;
      double r = s.radius;
      double ry = s.radius / Math.max(0.1, s.squash);
      int minX = Mth.floor(s.centre.x - r);
      int maxX = Mth.floor(s.centre.x + r);
      int minY = Math.max(level.getMinBuildHeight(), Mth.floor(s.centre.y - ry));
      int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(s.centre.y + ry));
      int minZ = Mth.floor(s.centre.z - r);
      int maxZ = Mth.floor(s.centre.z + r);
      if (minY > maxY) {
         return;
      }
      long[] cells = new long[256];
      float[] keys = new float[256];
      int n = 0;
      int scanned = 0;
      double r2 = r * r;
      double sq2 = s.squash * s.squash;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      outer:
      for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
         for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
               continue;
            }
            int x0 = Math.max(minX, cx << 4);
            int x1 = Math.min(maxX, (cx << 4) + 15);
            int z0 = Math.max(minZ, cz << 4);
            int z1 = Math.min(maxZ, (cz << 4) + 15);
            for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
               int idx = chunk.getSectionIndexFromSectionY(sy);
               if (idx < 0 || idx >= chunk.getSectionsCount()) {
                  continue;
               }
               LevelChunkSection section = chunk.getSection(idx);
               if (section.hasOnlyAir()) {
                  continue;
               }
               int y0 = Math.max(minY, sy << 4);
               int y1 = Math.min(maxY, (sy << 4) + 15);
               for (int y = y1; y >= y0; y--) {
                  double dy = (y + 0.5 - s.centre.y);
                  double dy2 = dy * dy * sq2;
                  if (dy2 > r2) {
                     continue;
                  }
                  for (int x = x0; x <= x1; x++) {
                     double dx = x + 0.5 - s.centre.x;
                     double dxy = dx * dx + dy2;
                     if (dxy > r2) {
                        continue;
                     }
                     for (int z = z0; z <= z1; z++) {
                        double dz = z + 0.5 - s.centre.z;
                        double d2 = dxy + dz * dz;
                        if (d2 > r2) {
                           continue;
                        }
                        if (++scanned > SCAN_PER_TICK) {
                           break outer;
                        }
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (state.isAir() || !state.getFluidState().isEmpty()) {
                           continue;
                        }
                        if (arena != null && !arena.inArena(x, y, z)) {
                           continue;
                        }
                        pos.set(x, y, z);
                        float h = state.getDestroySpeed(level, pos);
                        if (h < 0.0F || h > s.hardness) {
                           continue;
                        }
                        if (n == cells.length) {
                           cells = java.util.Arrays.copyOf(cells, n * 2);
                           keys = java.util.Arrays.copyOf(keys, n * 2);
                        }
                        cells[n] = BlockPos.asLong(x, y, z);
                        keys[n] = (float)d2;
                        n++;
                     }
                  }
               }
            }
         }
      }
      int[] order = new int[n];
      for (int i = 0; i < n; i++) {
         order[i] = i;
      }
      float[] k = keys;
      IntArrays.quickSort(order, (a, b) -> Float.compare(k[a], k[b]));
      s.cells = cells;
      s.order = order;
      s.count = n;
   }

   /** A finished (or immediate) smash: dust for the clients, the city systems told. */
   private void finished(ServerLevel level, Smash s) {
      if (s.removed <= 0) {
         return;
      }
      CityAftermath.hint(level, AftermathCause.GODZILLA, s.centre.x, s.centre.y, s.centre.z, (float)s.radius);
      if ("beam".equals(s.kind)) {
         return; // the beam reports its own hot impacts; the clients draw them from the synced beam end
      }
      float radius = (float)Math.min(s.radius, 2.0 + Math.cbrt(s.removed) * 1.2);
      GodzillaNet.smash(level, s.centre, radius, s.removed, s.kind);
      if (s.removed >= 24) {
         this.buildingsHit++;
         this.report(level, s.centre, (float)Math.max(4.0, s.radius), s.removed, s.kind, s.sample, s.removed >= 400);
      }
   }

   /**
    * Ruin zone, grid damage, danger and a structural impact for a hit, at most once per area cell per interval
    * ({@code force}: the cell's throttle is skipped, for the heaviest hits).
    */
   void report(ServerLevel level, Vec3 at, float radius, int blocks, String kind, List<BlockPos> sample, boolean force) {
      long cell = cell(at);
      if (force || this.tick - this.reported.get(cell) >= GodzillaRules.AFTERMATH_INTERVAL) {
         this.reported.put(cell, this.tick);
         CityAftermath.registerDamage(level, AftermathEvent.of(level, "godzilla:" + this.sessionId + ":" + cell, BlockPos.containing(at), radius,
            GodzillaRules.severity(blocks), AftermathCause.GODZILLA, sample));
         StructuralDamage.impact(level, at, CAUSE);
      }
      if (this.tick - this.infra.get(cell) >= GodzillaRules.INFRA_INTERVAL) {
         this.infra.put(cell, this.tick);
         CityInfrastructure.damage(level, at, radius, GodzillaRules.severity(blocks), "godzilla", "godzilla:" + kind);
      }
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.danger(at.x, at.y, at.z, radius * 3.0F, 200);
      }
   }

   static long cell(Vec3 at) {
      int c = GodzillaRules.REPORT_CELL;
      return BlockPos.asLong(Mth.floor(at.x / c), Mth.floor(at.y / c), Mth.floor(at.z / c));
   }

   /** The roar's pressure wave: window panes in front of the head shatter (a few per tick, budgeted). */
   int shatter(ServerLevel level, Vec3 from, Vec3 dir, double range, int limit) {
      if (!this.griefing || this.exhausted()) {
         return 0;
      }
      RandomSource random = level.getRandom();
      int done = 0;
      CityManager city = CityManager.of(level);
      CityProfile arena = city != null ? city.profile() : null;
      for (int tries = 0; tries < limit * 6 && done < limit && this.left() > 0; tries++) {
         double d = 12.0 + random.nextDouble() * (range - 12.0);
         double yaw = (random.nextDouble() - 0.5) * 1.4;
         double pitch = (random.nextDouble() - 0.6) * 0.9;
         double cos = Math.cos(yaw);
         double sin = Math.sin(yaw);
         Vec3 flat = new Vec3(dir.x * cos - dir.z * sin, 0.0, dir.x * sin + dir.z * cos).normalize();
         Vec3 p = from.add(flat.scale(d * Math.cos(pitch))).add(0.0, d * Math.sin(pitch), 0.0);
         this.cursor.set(p.x, p.y, p.z);
         if (!level.isLoaded(this.cursor) || arena != null && !arena.inArena(this.cursor.getX(), this.cursor.getY(), this.cursor.getZ())) {
            continue;
         }
         // walk a few blocks around the point: panes come in walls
         for (int k = 0; k < 6 && done < limit; k++) {
            BlockPos q = this.cursor.offset(random.nextInt(5) - 2, random.nextInt(5) - 2, random.nextInt(5) - 2);
            BlockState state = level.getBlockState(q);
            if (state.getSoundType() == SoundType.GLASS && state.getDestroySpeed(level, q) >= 0.0F) {
               StructuralDamage.run(CAUSE, () -> level.destroyBlock(q, false));
               done++;
               this.usedThisTick++;
               this.destroyed++;
            }
         }
      }
      return done;
   }

   private void pruneFlying() {
      Iterator<Flying> it = this.flying.iterator();
      while (it.hasNext()) {
         Flying f = it.next();
         if (f.block.isRemoved()) {
            it.remove();
         } else if (this.tick - f.at > DEBRIS_LIFE) {
            f.block.discard(); // still in the air after 30 s: stuck, or frozen outside the ticking range
            it.remove();
         }
      }
   }

   /** Stop / cleanup: queued work dropped, debris still in the air removed (it is the event's). */
   void clear() {
      this.jobs.clear();
      for (Flying f : this.flying) {
         if (!f.block.isRemoved()) {
            f.block.discard();
         }
      }
      this.flying.clear();
   }

   private record Flying(FallingBlockEntity block, long at) {
   }

}

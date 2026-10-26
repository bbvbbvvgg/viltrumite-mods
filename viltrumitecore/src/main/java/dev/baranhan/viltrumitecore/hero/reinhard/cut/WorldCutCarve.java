package dev.baranhan.viltrumitecore.hero.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** One lazy carve. Every visited position spends budget, including air and rejected blocks. */
public final class WorldCutCarve implements CarveJobs.Job {
   /** World boundary; the queue/cursor algorithm works without a live level. */
   public interface Probe {
      boolean loaded(BlockPos pos);
      boolean canDestroy(BlockPos pos);
      boolean solid(BlockPos pos);
      boolean canLaunch(BlockPos pos);
      boolean clear(BlockPos pos);
      boolean launch(BlockPos pos, Vec3 velocity);
   }

   private final WorldCutGeometry.Wave wave;
   @Nullable private final Probe probe;
   private final Vec3 origin;
   private final Vec3 dir;
   private int solidRemoved;
   private int debris;
   private int failures;
   private long firstTick=-1,lastTick=-1; private int visited;
   public long firstTick() {return firstTick;}
   public long lastTick() {return lastTick;}
   public int visited() {return visited;}

   public WorldCutCarve(WorldCutShape shape, int[] surface, int minY, int maxY, Predicate<BlockPos> border) {
      this(shape, surface, minY, maxY, WorldCutGeometry.ColumnTop.SLICE, border);
   }

   /** Each footprint column is carved from its own top (see {@link WorldCutSurface#tops}). */
   public WorldCutCarve(WorldCutShape shape, int[] surface, int minY, int maxY, WorldCutGeometry.ColumnTop top,
                        Predicate<BlockPos> border) {
      this(shape.cells(minY, maxY, a -> surface[a], top, border), null, shape.origin(), shape.dirXZ());
   }

   public WorldCutCarve(WorldCutGeometry.Wave wave, @Nullable Probe probe, Vec3 origin, Vec3 dir) {
      this.wave = Objects.requireNonNull(wave);
      this.probe = probe;
      this.origin = origin;
      this.dir = dir;
   }

   @Override public int quantum() { return 250; }

   @Override
   public boolean step(@Nullable ServerLevel level, int budget) {
      if (!wave.hasNext()) return true;
      if (budget <= 0) return false;
      if(firstTick<0 && level!=null)firstTick=level.getGameTime();
      Probe world = probe != null ? probe : new LiveProbe(Objects.requireNonNull(level));
      for (int used = 0; used < budget && wave.hasNext(); used++) {
         WorldCutGeometry.Cell cell = wave.next();visited++;
         if (!wave.carve(cell)) continue;
         try {
            visit(world, cell);
         } catch (RuntimeException e) {
            // One misbehaving block (a modded block entity, a broken state) must not end the whole job:
            // CarveJobs drops a job that throws, which would leave the rest of the trench standing.
            failures++;
         }
      }
      // No neighbour-update pass, no drops or block particles at completion.
      if(!wave.hasNext() && level!=null)lastTick=level.getGameTime();
      return !wave.hasNext();
   }

   private void visit(Probe world, WorldCutGeometry.Cell cell) {
      BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
      if (!world.loaded(pos) || !world.canDestroy(pos)) return;
      boolean solid = world.solid(pos);
      boolean fly = solid && cell.down() < ReinhardRules.CUT_DEBRIS_LAYERS
         && debris < ReinhardRules.CUT_DEBRIS_MAX
         && (solidRemoved + 1) % Math.round(1.0 / ReinhardRules.CUT_DEBRIS_SHARE) == 0
         && world.canLaunch(pos);
      boolean removed;
      if (fly) {
         removed = world.launch(pos, velocity(cell));
         if (removed) debris++;
      } else {
         removed = world.clear(pos);
      }
      if (removed && solid) solidRemoved++;
   }

   /** Cells whose block threw while being removed; they are skipped, the job goes on. */
   public int failures() { return failures; }

   private Vec3 velocity(WorldCutGeometry.Cell cell) {
      double side = (cell.x() + .5 - origin.x) * -dir.z + (cell.z() + .5 - origin.z) * dir.x;
      double outward = Math.copySign(ReinhardRules.CUT_DEBRIS_OUTWARD, side);
      return new Vec3(-dir.z * outward + dir.x * ReinhardRules.CUT_DEBRIS_FORWARD,
         ReinhardRules.CUT_DEBRIS_UP, dir.x * outward + dir.z * ReinhardRules.CUT_DEBRIS_FORWARD);
   }

   @Override public int priority() { return ReinhardRules.CUT_CARVE_PRIORITY; }
   @Override public int remainingWork() { return wave.remainingWork(); }
   public int frontAlong() { return wave.frontAlong(); }
   public int debrisCount() { return debris; }

   private record LiveProbe(ServerLevel level) implements Probe {
      public boolean loaded(BlockPos pos) {
         return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
            && level.getWorldBorder().isWithinBounds(pos) && level.isLoaded(pos);
      }
      public boolean canDestroy(BlockPos pos) { return HeroDestruction.canDestroy(level, pos); }
      public boolean solid(BlockPos pos) { return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); }
      public boolean canLaunch(BlockPos pos) { return HeroDebris.canLaunch(level, pos, level.getBlockState(pos)); }
      public boolean clear(BlockPos pos) { return HeroDestruction.clearBlock(level, pos); }
      public boolean launch(BlockPos pos, Vec3 velocity) {
         // launchBlock removes the source itself: never clear it a second time.
         HeroDebris.launchBlock(level, pos, level.getBlockState(pos), velocity, 0, 0, HeroDestruction.CLEAR_FLAGS);
         return true;
      }
   }
}

package dev.baranhan.viltrumitecore.hero.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.Iterator;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fixed cast geometry; terrain is supplied by a loaded-column snapshot, never queried here. */
public record WorldCutShape(Vec3 origin, Vec3 dirXZ, long seed) {
   public WorldCutShape {
      dirXZ = new Vec3(dirXZ.x, 0, dirXZ.z);
      dirXZ = dirXZ.lengthSqr() < 1E-8 ? new Vec3(0, 0, 1) : dirXZ.normalize();
      if (!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)
         || !Double.isFinite(dirXZ.x) || !Double.isFinite(dirXZ.z)) {
         throw new IllegalArgumentException("Non-finite world cut");
      }
   }

   public Vec3 axis(double along) {
      return origin.add(dirXZ.scale(ReinhardRules.CUT_START_OFFSET + along));
   }

   public WorldCutGeometry geometry() {
      Vec3 start = axis(0);
      return new WorldCutGeometry(start.x, origin.y, start.z, dirXZ.x, dirXZ.z, seed,
         ReinhardRules.CUT_LENGTH, ReinhardRules.CUT_WIDTH, ReinhardRules.CUT_DEPTH_MIN,
         ReinhardRules.CUT_DEPTH_MAX, ReinhardRules.CUT_ABOVE);
   }

   public int depthAt(int along) { return geometry().depthAt(along); }
   public int volume() { return geometry().volume(); }
   public boolean contains(int along, int across, int down) { return geometry().contains(along, across, down); }

   public AABB hitBox(int along) { return hitBox(along, (int) Math.floor(origin.y) - 1); }
   public AABB hitBox(int along, int surface) {
      WorldCutGeometry.Bounds b = geometry().hitBox(along, surface);
      return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
   }

   public Iterator<BlockPos> wave(int minY, int maxY) {
      return wave(minY, maxY, a -> (int) Math.floor(origin.y) - 1, p -> true);
   }

   public WorldCutGeometry.Wave cells(int minY, int maxY, IntUnaryOperator surface, Predicate<BlockPos> border) {
      return geometry().wave(minY, maxY, surface, c -> border.test(new BlockPos(c.x(), c.y(), c.z())));
   }

   public Iterator<BlockPos> wave(int minY, int maxY, IntUnaryOperator surface, Predicate<BlockPos> border) {
      Iterator<WorldCutGeometry.Cell> cells = cells(minY, maxY, surface, border);
      return new Iterator<>() {
         public boolean hasNext() { return cells.hasNext(); }
         public BlockPos next() {
            WorldCutGeometry.Cell c = cells.next();
            return new BlockPos(c.x(), c.y(), c.z());
         }
      };
   }
}

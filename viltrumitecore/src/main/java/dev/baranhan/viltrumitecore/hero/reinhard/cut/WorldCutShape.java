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

   /** SAT: exact horizontal slice/box overlap, not just the rotated slice's enclosing AABB. */
   public boolean intersects(AABB box, int along, int surface) {
      if (!hitBox(along, surface).intersects(box)) return false;
      Vec3 centre = axis(along + .5);
      double dx=(box.minX+box.maxX)*.5-centre.x, dz=(box.minZ+box.maxZ)*.5-centre.z;
      double hx=(box.maxX-box.minX)*.5, hz=(box.maxZ-box.minZ)*.5;
      double projectedAlong=Math.abs(dx*dirXZ.x+dz*dirXZ.z);
      double projectedAcross=Math.abs(-dx*dirXZ.z+dz*dirXZ.x);
      // Use the widest actual terrace touched by the target, not the broad-phase box.
      int down = Math.max(0, (int)Math.floor(surface + 1 - box.maxY));
      double half = geometry().halfWidth(along, down) + .5;
      return projectedAlong < .5+hx*Math.abs(dirXZ.x)+hz*Math.abs(dirXZ.z)
         && projectedAcross < half+hx*Math.abs(dirXZ.z)+hz*Math.abs(dirXZ.x);
   }

   public Iterator<BlockPos> wave(int minY, int maxY) {
      return wave(minY, maxY, a -> (int) Math.floor(origin.y) - 1, p -> true);
   }

   public WorldCutGeometry.Wave cells(int minY, int maxY, IntUnaryOperator surface, Predicate<BlockPos> border) {
      return geometry().wave(minY, maxY, surface, c -> border.test(new BlockPos(c.x(), c.y(), c.z())));
   }

   public Iterator<BlockPos> wave(int minY, int maxY, IntUnaryOperator surface, Predicate<BlockPos> border) {
      WorldCutGeometry.Wave cells = cells(minY, maxY, surface, border);
      // Convenience enumeration of the actual scar. Production uses cells() so that
      // rejected mask candidates still consume the shared server visit budget.
      return new Iterator<>() {
         private WorldCutGeometry.Cell ready;
         public boolean hasNext() {
            while(ready==null && cells.hasNext()) {var c=cells.next();if(cells.carve(c))ready=c;}
            return ready!=null;
         }
         public BlockPos next() {
            if(!hasNext())throw new java.util.NoSuchElementException();
            var c=ready;ready=null;return new BlockPos(c.x(),c.y(),c.z());
         }
      };
   }
}

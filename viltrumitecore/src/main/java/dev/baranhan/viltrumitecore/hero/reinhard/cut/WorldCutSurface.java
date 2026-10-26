package dev.baranhan.viltrumitecore.hero.reinhard.cut;

import java.util.Arrays;
import java.util.List;

/**
 * Where the ordinary World Cut meets the terrain, per slice and per column (REINHARD 3b, item 6). Pure: the level
 * is behind {@link Heights}, so the queue/raster can be tested on a synthetic world.
 *
 * <p>The old snapshot sampled one height per slice, at the axis, and every column of the 17-block slice was carved
 * from that height down. Terrain standing higher than the axis inside the footprint (a bump, the uphill side of a
 * slope, a plant or a snow layer one block up, a tree next to the axis) was never visited, while everything under it
 * was removed: floating islands, single blocks and bushes over the trench. Now
 * <ul>
 *    <li>the slice's reference ground is the median ground height of its columns (terrain, trunks and water, not
 *    leaves, plants or snow layers), so one trunk or a canopy over the axis does not lift a whole slice and leave a
 *    fin in the floor;</li>
 *    <li>every column is carved from its own highest non-air block (plants, snow, leaves included), but never more
 *    than {@code above} blocks over the reference (sky bases and cliff tops stay; the cut's height above the ground
 *    is the same {@code CUT_ABOVE} its entity hit box always had).</li>
 * </ul>
 * The trench's mask, depth and terraces are still measured from the reference, so its shape does not change.
 */
public final class WorldCutSurface {
   /** A height a provider does not know: an unloaded chunk or a column outside the world border. */
   public static final int UNKNOWN = Integer.MIN_VALUE;

   /** Heights of block columns: the y of a column's top block, or {@link #UNKNOWN}. */
   @FunctionalInterface
   public interface Heights {
      int at(int x, int z);
   }

   private WorldCutSurface() {
   }

   /** Reference ground of slice {@code along}: the (upper) median of the known ground heights of the columns inside
    *  the trench's top layer; {@code fallback} when none of them is known. */
   public static int reference(WorldCutGeometry geometry, int along, Heights ground, int fallback) {
      List<WorldCutGeometry.Column> columns = geometry.columns(along);
      int[] heights = new int[columns.size()];
      int n = 0;
      for (WorldCutGeometry.Column column : columns) {
         if (!geometry.masked(along, column.across(), 0)) continue;
         int y = ground.at(column.x(), column.z());
         if (y != UNKNOWN) heights[n++] = y;
      }
      if (n == 0) return fallback;
      Arrays.sort(heights, 0, n);
      return heights[n / 2];
   }

   /** {@link #reference} for every slice of the cut. */
   public static int[] references(WorldCutGeometry geometry, Heights ground, int fallback) {
      int[] surface = new int[geometry.length()];
      for (int a = 0; a < surface.length; a++) surface[a] = reference(geometry, a, ground, fallback);
      return surface;
   }

   /** Column tops for the carve: the column's highest non-air block, at most {@code above} blocks over the slice's
    *  reference; an unknown column starts at the reference (its blocks are not loaded anyway). */
   public static WorldCutGeometry.ColumnTop tops(Heights highest, int above) {
      return (x, z, along, surface) -> {
         int top = highest.at(x, z);
         return top == UNKNOWN ? surface : Math.min(top, surface + above);
      };
   }
}

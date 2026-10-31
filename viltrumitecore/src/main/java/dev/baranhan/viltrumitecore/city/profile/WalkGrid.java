package dev.baranhan.viltrumitecore.city.profile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The prepared pedestrian network of a world: lazily loaded {@link WalkTile}s
 * ({@code viltrumite_city/walk/r.X.Z.bin}) in a small LRU. Missing files are "no
 * pedestrian surface" (outside the city). Pure: tests feed tiles directly.
 */
public final class WalkGrid {
   private static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/city");
   public static final int NONE = Integer.MIN_VALUE;
   private static final int CACHE = 48;

   private final Path folder;
   private final Map<Long, WalkTile> tiles = new LinkedHashMap<>(64, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Long, WalkTile> e) {
         return this.size() > CACHE && WalkGrid.this.folder != null;
      }
   };
   private long lastKey = Long.MIN_VALUE;
   private WalkTile lastTile;

   /** @param folder the walk folder, or null for an in-memory grid (tests) */
   public WalkGrid(Path folder) {
      this.folder = folder;
   }

   public static long key(int rx, int rz) {
      return (long)rx << 32 | rz & 0xFFFFFFFFL;
   }

   public void put(int rx, int rz, WalkTile tile) {
      this.tiles.put(key(rx, rz), tile);
      this.lastKey = Long.MIN_VALUE;
   }

   public WalkTile tile(int rx, int rz) {
      long k = key(rx, rz);
      if (k == this.lastKey) {
         return this.lastTile;
      }

      WalkTile t = this.tiles.get(k);
      if (t == null) {
         t = this.load(rx, rz);
         this.tiles.put(k, t);
      }

      this.lastKey = k;
      this.lastTile = t;
      return t;
   }

   private WalkTile load(int rx, int rz) {
      if (this.folder == null) {
         return WalkTile.EMPTY;
      }

      Path f = this.folder.resolve("r." + rx + "." + rz + ".bin");
      if (!Files.isRegularFile(f)) {
         return WalkTile.EMPTY;
      }

      try (InputStream in = Files.newInputStream(f)) {
         return WalkTile.read(in);
      } catch (IOException e) {
         LOG.warn("City walk tile {} unreadable: {}", f, e.toString());
         return WalkTile.EMPTY;
      }
   }

   /** Raw cell at block x/z, 0 when none. */
   public int cell(int x, int z) {
      return this.tile(x >> 9, z >> 9).cell(x & 511, z & 511);
   }

   /** Feet block Y of the pedestrian surface at x/z, or {@link #NONE}. */
   public int feetY(int x, int z) {
      int c = this.cell(x, z);
      return c == 0 ? NONE : WalkTile.feetY(c);
   }

   /** Someone with feet in this block stands on the pedestrian surface. */
   public boolean standable(int x, int feetY, int z) {
      int c = this.cell(x, z);
      return c != 0 && WalkTile.feetY(c) == feetY;
   }

   /**
    * Air above a lower grid cell of this column, at most {@code maxDrop} blocks up: a path may
    * step or drop down through it (off a bench, a planter or a log top, down a curb). The
    * landing itself must still be {@link #standable}.
    */
   public boolean dropTo(int x, int y, int z, int maxDrop) {
      int c = this.cell(x, z);
      if (c == 0) {
         return false;
      }

      int dy = y - WalkTile.feetY(c);
      return dy > 0 && dy <= maxDrop;
   }

   public boolean preferred(int x, int z) {
      int c = this.cell(x, z);
      return c != 0 && WalkTile.cls(c) == WalkTile.PREFERRED;
   }

   /**
    * A random cell in the ring [minR, maxR] around x/z that passes the filter (packed as
    * {@code x, z} in a long), or {@link Long#MIN_VALUE} after the given number of tries.
    */
   public long randomCell(DoubleSupplier rnd, double cx, double cz, double minR, double maxR, boolean preferredOnly, int tries, CellFilter filter) {
      for (int i = 0; i < tries; i++) {
         double a = rnd.getAsDouble() * Math.PI * 2.0;
         double r = minR + (maxR - minR) * Math.sqrt(rnd.getAsDouble());
         int x = (int)Math.floor(cx + Math.cos(a) * r);
         int z = (int)Math.floor(cz + Math.sin(a) * r);
         int c = this.cell(x, z);
         if (c == 0 || preferredOnly && WalkTile.cls(c) != WalkTile.PREFERRED) {
            continue;
         }

         if (filter == null || filter.test(x, WalkTile.feetY(c), z)) {
            return pack(x, z);
         }
      }

      return Long.MIN_VALUE;
   }

   /** Nearest cell to x/z within radius (square spiral), packed, or {@link Long#MIN_VALUE}. */
   public long nearestCell(int x, int z, int radius, IntPredicate feetYOk) {
      for (int r = 0; r <= radius; r++) {
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                  continue;
               }

               int c = this.cell(x + dx, z + dz);
               if (c != 0 && (feetYOk == null || feetYOk.test(WalkTile.feetY(c)))) {
                  return pack(x + dx, z + dz);
               }
            }
         }
      }

      return Long.MIN_VALUE;
   }

   public static long pack(int x, int z) {
      return (long)x << 32 | z & 0xFFFFFFFFL;
   }

   public static int unpackX(long p) {
      return (int)(p >> 32);
   }

   public static int unpackZ(long p) {
      return (int)p;
   }

   public interface CellFilter {
      boolean test(int x, int feetY, int z);
   }
}

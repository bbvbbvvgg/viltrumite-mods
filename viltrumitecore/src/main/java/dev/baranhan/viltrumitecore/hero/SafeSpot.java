package dev.baranhan.viltrumitecore.hero;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared, hero-neutral search for a spot an entity can be teleported to (first
 * user: Reinhard's Dash Behind). A spot is safe when the entity's box fits
 * there, it is inside the world border and solid ground lies within
 * {@link #MAX_DROP} blocks below (never over the void). The wanted spot is
 * tried first, then every grid point within the radius, nearest first.
 * The search ({@link #find}) is pure over a {@link Probe}; {@link LevelProbe}
 * is the Minecraft side and works on both the client and the server.
 */
public final class SafeSpot {
   /** Deepest fall a spot may have under it. */
   public static final int MAX_DROP = 6;
   private static final Map<Long, List<Vec3>> OFFSETS = new ConcurrentHashMap<>();

   private SafeSpot() {
   }

   /** The world checks of one candidate (feet position). */
   public interface Probe {
      boolean fits(Vec3 feet);

      boolean insideBorder(Vec3 feet);

      boolean groundBelow(Vec3 feet, int maxDrop);
   }

   public static boolean ok(Vec3 feet, Probe probe) {
      return probe.fits(feet) && probe.insideBorder(feet) && probe.groundBelow(feet, MAX_DROP);
   }

   /** The wanted spot if safe, else the nearest safe grid point ({@code step} apart) within {@code radius}; empty = none. */
   public static Optional<Vec3> find(Vec3 wanted, Probe probe, double radius, double step) {
      for (Vec3 offset : offsets(radius, step)) {
         Vec3 feet = wanted.add(offset);
         if (ok(feet, probe)) {
            return Optional.of(feet);
         }
      }

      return Optional.empty();
   }

   /** Grid offsets within the radius, nearest first (ties: smallest |y| first, then a fixed order); the origin leads. */
   static List<Vec3> offsets(double radius, double step) {
      long key = Double.doubleToLongBits(radius) * 31L + Double.doubleToLongBits(step);
      return OFFSETS.computeIfAbsent(key, k -> build(radius, step));
   }

   private static List<Vec3> build(double radius, double step) {
      int n = Math.max(0, (int) Math.floor(radius / step + 1.0E-9));
      List<Vec3> list = new ArrayList<>();
      for (int i = -n; i <= n; i++) {
         for (int j = -n; j <= n; j++) {
            for (int k = -n; k <= n; k++) {
               Vec3 v = new Vec3(i * step, j * step, k * step);
               if (v.length() <= radius + 1.0E-9) {
                  list.add(v);
               }
            }
         }
      }

      list.sort(Comparator.<Vec3>comparingDouble(Vec3::lengthSqr).thenComparingDouble(v -> Math.abs(v.y)).thenComparingDouble(v -> v.y)
         .thenComparingDouble(v -> v.x).thenComparingDouble(v -> v.z));
      return List.copyOf(list);
   }

   /** The Minecraft probe: the entity's own box, the level's border and its blocks. */
   public record LevelProbe(Level level, Entity entity) implements Probe {
      private AABB box(Vec3 feet) {
         return this.entity.getDimensions(this.entity.getPose()).makeBoundingBox(feet);
      }

      @Override
      public boolean fits(Vec3 feet) {
         return this.level.noCollision(this.entity, this.box(feet));
      }

      @Override
      public boolean insideBorder(Vec3 feet) {
         return this.level.getWorldBorder().isWithinBounds(this.box(feet));
      }

      @Override
      public boolean groundBelow(Vec3 feet, int maxDrop) {
         int top = Mth.floor(feet.y - 1.0E-3);
         int x = Mth.floor(feet.x);
         int z = Mth.floor(feet.z);
         for (int y = top; y >= top - maxDrop && y >= this.level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty()) {
               return true;
            }
         }

         return false;
      }
   }
}

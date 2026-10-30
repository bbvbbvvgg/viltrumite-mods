package dev.baranhan.viltrumitecore.hero.captain.assemble;

import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where the circle stands (spec §5.7): six places 60° apart, back to back and facing out, Steve on place 0
 * keeping his facing. The radius shrinks 3.4 → 2.2 until the places fit (city streets, corridors, caves);
 * a place that still does not fit is searched with {@link SafeSpot} near it. The geometry is pure (tested);
 * {@link #plan} is the level side.
 */
public final class FormationPlanner {
   public static final double RADIUS_STEP = 0.3;

   private FormationPlanner() {
   }

   /** Flat unit direction of a yaw (degrees, Minecraft convention). */
   public static Vec3 dir(double yawDeg) {
      double r = Math.toRadians(yawDeg);
      return new Vec3(-Math.sin(r), 0, Math.cos(r));
   }

   /** Circle centre: behind Steve by the radius, so Steve stands on the rim facing out. */
   public static Vec3 center(Vec3 steve, double yawDeg, double radius) {
      return steve.subtract(dir(yawDeg).scale(radius));
   }

   /** Outward facing yaw of place {@code slot} (0 = Steve). */
   public static double slotYaw(double yawDeg, int slot) {
      return Mth.wrapDegrees(yawDeg + (slot == 0 ? 0.0 : CaptainRules.slotAngle(slot - 1)));
   }

   /** Feet position of place {@code slot} (0 = Steve's own spot). */
   public static Vec3 slot(Vec3 center, double yawDeg, double radius, int slot) {
      return center.add(dir(slotYaw(yawDeg, slot)).scale(radius));
   }

   /** Radii tried, largest first. */
   public static double[] radii() {
      int n = (int)Math.round((CaptainRules.CIRCLE_RADIUS - CaptainRules.CIRCLE_RADIUS_MIN) / RADIUS_STEP) + 1;
      double[] r = new double[n];
      for (int i = 0; i < n; i++) {
         r[i] = Math.max(CaptainRules.CIRCLE_RADIUS_MIN, CaptainRules.CIRCLE_RADIUS - i * RADIUS_STEP);
      }
      return r;
   }

   /** Quinjet path parameter: {@code v} in [-1, 1] (0 = overhead) at scene tick t; slow over the circle. */
   public static double quinjetV(int t) {
      double u = Mth.clamp((t - CaptainRules.QUINJET_START) / (double)(CaptainRules.QUINJET_END - CaptainRules.QUINJET_START), 0.0, 1.0);
      double u0 = (CaptainRules.QUINJET_OVERHEAD - CaptainRules.QUINJET_START) / (double)(CaptainRules.QUINJET_END - CaptainRules.QUINJET_START);
      return u < u0 ? -(1.0 - u / u0) : (u - u0) / (1.0 - u0);
   }

   /** Distance along the Quinjet line for path parameter v (half length {@code half}); 0 overhead. */
   public static double quinjetS(double v, double half) {
      return half * (0.35 * v + 0.65 * v * v * v);
   }

   /** Fall progress curve (0..1 → 0..1), accelerating like a drop. */
   public static double fall(double tau) {
      double t = Mth.clamp(tau, 0.0, 1.0);
      return t * t;
   }

   /** Ease-out (arrivals braking with thrusters). */
   public static double brake(double tau) {
      double t = Mth.clamp(tau, 0.0, 1.0);
      return 1.0 - Math.pow(1.0 - t, 2.4);
   }

   /** Point on a jump arc from a to b with the given apex height above the higher end. */
   public static Vec3 arc(Vec3 a, Vec3 b, double tau, double apex) {
      double t = Mth.clamp(tau, 0.0, 1.0);
      Vec3 p = a.lerp(b, t);
      double top = Math.max(a.y, b.y) + apex;
      // quadratic through a.y (t=0), b.y (t=1) with the peak at top
      double h = 4.0 * (top - (a.y + b.y) * 0.5) * t * (1.0 - t);
      return new Vec3(p.x, a.y + (b.y - a.y) * t + h, p.z);
   }

   // --- level side ---

   /** The computed circle: places 0..5, their facing, the radius used and whether there is sky above. */
   public record Plan(Vec3 center, double radius, double yaw, Vec3[] places, boolean sky, double ceiling) {
      public Vec3 place(AvengerKind kind) {
         return this.places[kind.slot()];
      }

      public float yawOf(AvengerKind kind) {
         return (float)slotYaw(this.yaw, kind.slot());
      }
   }

   public static Plan plan(Level level, Vec3 steve, double yawDeg) {
      double bestR = CaptainRules.CIRCLE_RADIUS;
      int bestOk = -1;
      Vec3[] best = null;
      for (double r : radii()) {
         Vec3 c = center(steve, yawDeg, r);
         Vec3[] places = new Vec3[6];
         places[0] = steve;
         int ok = 0;
         for (AvengerKind k : AvengerKind.values()) {
            Vec3 want = slot(c, yawDeg, r, k.slot());
            Optional<Vec3> stand = stand(level, k, want, steve);
            places[k.slot()] = stand.orElse(null);
            if (stand.isPresent()) {
               ok++;
            }
         }
         if (ok > bestOk) {
            bestOk = ok;
            bestR = r;
            best = places;
         }
         if (ok == 5) {
            break;
         }
      }
      Vec3 c = center(steve, yawDeg, bestR);
      for (AvengerKind k : AvengerKind.values()) {
         if (best[k.slot()] == null) {
            Vec3 want = slot(c, yawDeg, bestR, k.slot());
            best[k.slot()] = SafeSpot.find(want, new BoxProbe(level, k), 3.0, 0.5)
               .or(() -> SafeSpot.find(steve, new BoxProbe(level, k), 3.0, 0.5))
               .orElse(steve);
         }
      }
      double ceiling = clearAbove(level, c.add(0, 1.0, 0), 48);
      boolean sky = ceiling >= 24 && level.canSeeSky(BlockPos.containing(steve.x, steve.y + 2.0, steve.z));
      return new Plan(c, bestR, yawDeg, best, sky, ceiling);
   }

   /** A standing spot at {@code want} (feet snapped to the floor within ±2 blocks) in sight of Steve. */
   static Optional<Vec3> stand(Level level, AvengerKind k, Vec3 want, Vec3 steve) {
      BoxProbe probe = new BoxProbe(level, k);
      int base = Mth.floor(want.y + 1.0E-3);
      for (int dy : new int[]{0, 1, -1, 2, -2}) {
         Vec3 feet = new Vec3(want.x, base + dy, want.z);
         // a full or partial block under the feet: stand on its top
         BlockPos below = BlockPos.containing(feet.x, feet.y - 0.5, feet.z);
         var shape = level.getBlockState(below).getCollisionShape(level, below);
         if (shape.isEmpty()) {
            continue;
         }
         feet = new Vec3(feet.x, below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), feet.z);
         if (probe.fits(feet) && probe.insideBorder(feet)
            && visible(level, steve.add(0, 1.2, 0), feet.add(0, 1.2, 0))) {
            return Optional.of(feet);
         }
      }
      return Optional.empty();
   }

   static boolean visible(Level level, Vec3 a, Vec3 b) {
      return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)).getType() == HitResult.Type.MISS;
   }

   /** Free height above a point (blocks, up to max). */
   public static double clearAbove(Level level, Vec3 at, double max) {
      Vec3 top = at.add(0, max, 0);
      var hit = level.clip(new ClipContext(at, top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
      return hit.getType() == HitResult.Type.MISS ? max : hit.getLocation().y - at.y;
   }

   /** Free distance along a flat direction at chest height (blocks, up to max). */
   public static double clearAlong(Level level, Vec3 from, Vec3 dir, double max) {
      Vec3 to = from.add(dir.scale(max));
      var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
      return hit.getType() == HitResult.Type.MISS ? max : hit.getLocation().distanceTo(from);
   }

   /** Ground height (feet y) at x/z from the heightmap, or {@code fallback} when the chunk is not loaded. */
   public static double ground(Level level, double x, double z, double fallback) {
      int bx = Mth.floor(x), bz = Mth.floor(z);
      if (!level.hasChunkAt(new BlockPos(bx, 0, bz))) {
         return fallback;
      }
      return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
   }

   /** Probe with the box of an Avenger kind (the entity does not exist yet). */
   public record BoxProbe(Level level, AvengerKind kind) implements SafeSpot.Probe {
      private AABB box(Vec3 feet) {
         double w = this.kind.width * 0.5;
         return new AABB(feet.x - w, feet.y, feet.z - w, feet.x + w, feet.y + this.kind.height, feet.z + w);
      }

      @Override
      public boolean fits(Vec3 feet) {
         return this.level.noCollision(this.box(feet));
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
         for (int y = top; y >= top - Math.min(maxDrop, 2) && y >= this.level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty()) {
               return true;
            }
         }
         return false;
      }
   }
}

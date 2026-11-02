package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure;
import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.world.collapse.CollapseCause;
import dev.baranhan.viltrumitecore.world.collapse.StructuralDamage;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathEvent;
import dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Every block the war removes (pure server helper around the shared gates): mobGriefing and
 * unbreakable blocks ({@link HeroDestruction#canDestroy}), loaded chunks only, no block entities or
 * fluids, a hardness cap per hit and one budget for the whole event ({@link ThanosWarRules#BLOCK_BUDGET}).
 * Removal runs under the structural-collapse cause {@code thanos}, so buildings that lose their
 * support collapse through {@code StructuralDamage} / {@code CityCollapseEvent} on their own; big hits
 * are also reported to the city's grid and the disaster aftermath.
 */
public final class ThanosWarDestruction {
   public static final CollapseCause CAUSE = CollapseCause.custom("thanos", 1.0F);
   private final ServerLevel level;
   private int budget = ThanosWarRules.BLOCK_BUDGET;
   private int removed;
   private int incidents;

   public ThanosWarDestruction(ServerLevel level) {
      this.level = level;
   }

   public int left() {
      return this.budget;
   }

   public int removed() {
      return this.removed;
   }

   /** May this block go (gates, hardness)? */
   public boolean removable(BlockPos pos, float maxHardness) {
      if (!this.level.isLoaded(pos) || !HeroDestruction.canDestroy(this.level, pos)) {
         return false;
      }

      BlockState state = this.level.getBlockState(pos);
      float hardness = state.getDestroySpeed(this.level, pos);
      return hardness >= 0.0F && hardness <= maxHardness && !state.hasBlockEntity() && state.getFluidState().isEmpty();
   }

   private boolean clear(BlockPos pos) {
      if (this.budget <= 0) {
         return false;
      }

      boolean done = StructuralDamage.call(CAUSE, () -> HeroDestruction.clearBlock(this.level, pos));
      if (done) {
         this.budget--;
         this.removed++;
      }

      return done;
   }

   /**
    * A bowl around {@code center} (deeper in the middle), at most {@code max} blocks; a few of them
    * are thrown out as falling debris. Returns the blocks removed.
    */
   public int crater(Vec3 center, double radius, float maxHardness, int max) {
      int out = 0;
      int r = (int)Math.ceil(radius);
      BlockPos c = BlockPos.containing(center);
      RandomSource random = this.level.random;
      int thrown = 0;
      for (int dy = 1; dy >= -r && out < max; dy--) {
         for (int dx = -r; dx <= r && out < max; dx++) {
            for (int dz = -r; dz <= r && out < max; dz++) {
               double d2 = dx * dx + dz * dz + (dy > 0 ? dy * dy * 4.0 : dy * dy * 1.6);
               if (d2 > radius * radius) {
                  continue;
               }

               BlockPos pos = c.offset(dx, dy, dz);
               if (!this.removable(pos, maxHardness)) {
                  continue;
               }

               BlockState state = this.level.getBlockState(pos);
               if (thrown < 3 && this.budget > 0 && random.nextInt(4) == 0 && HeroDebris.canLaunch(this.level, pos, state)) {
                  Vec3 out2 = new Vec3(dx + random.nextGaussian() * 0.3, 0.0, dz + random.nextGaussian() * 0.3);
                  out2 = out2.lengthSqr() < 1.0E-4 ? new Vec3(0.3, 0.0, 0.0) : out2.normalize().scale(0.35 + random.nextDouble() * 0.3);
                  final Vec3 v = new Vec3(out2.x, 0.55 + random.nextDouble() * 0.35, out2.z);
                  StructuralDamage.run(CAUSE, () -> HeroDebris.launchBlock(this.level, pos, state, v, 0.0F, 0));
                  this.budget--;
                  this.removed++;
                  thrown++;
                  out++;
               } else if (this.clear(pos)) {
                  out++;
               }
            }
         }
      }

      return out;
   }

   /**
    * A corridor along {@code dir} from {@code from} (Cull Obsidian's charge): {@code width} x
    * {@code height} blocks, at most {@code max}. Returns the blocks removed.
    */
   public int corridor(Vec3 from, Vec3 dir, double length, int width, int height, float maxHardness, int max) {
      Vec3 d = new Vec3(dir.x, 0.0, dir.z);
      if (d.lengthSqr() < 1.0E-6) {
         return 0;
      }

      d = d.normalize();
      Vec3 side = new Vec3(-d.z, 0.0, d.x);
      int out = 0;
      for (double s = 0.0; s <= length && out < max; s += 0.7) {
         for (int w = 0; w < width && out < max; w++) {
            double off = w - (width - 1) * 0.5;
            for (int h = 0; h < height && out < max; h++) {
               BlockPos pos = BlockPos.containing(from.add(d.scale(s)).add(side.scale(off)).add(0.0, h + 0.2, 0.0));
               if (this.removable(pos, maxHardness) && this.clear(pos)) {
                  out++;
               }
            }
         }
      }

      return out;
   }

   /** Tears one block out for a telekinetic throw: its state, or null (gates, budget). */
   @Nullable
   public BlockState lift(BlockPos pos, float maxHardness) {
      if (this.budget <= 0 || !this.removable(pos, maxHardness)) {
         return null;
      }

      BlockState state = this.level.getBlockState(pos);
      if (!HeroDebris.canLaunch(this.level, pos, state)) {
         return null;
      }

      return this.clear(pos) ? state : null;
   }

   /**
    * Up to {@code count} blocks Ebony Maw can tear out near {@code around}: solid, exposed (air on
    * one side), preferring walls and roofs above the ground. May return fewer.
    */
   public List<BlockPos> liftable(Vec3 around, double radius, int count, float maxHardness) {
      List<BlockPos> out = new ArrayList<>();
      RandomSource random = this.level.random;
      BlockPos c = BlockPos.containing(around);
      int r = (int)Math.ceil(radius);
      for (int tries = 0; tries < 220 && out.size() < count; tries++) {
         BlockPos pos = c.offset(random.nextInt(2 * r + 1) - r, random.nextInt(r + 4) - 2, random.nextInt(2 * r + 1) - r);
         if (out.contains(pos) || !this.removable(pos, maxHardness) || this.level.getBlockState(pos).isAir()) {
            continue;
         }

         BlockState state = this.level.getBlockState(pos);
         if (!state.isCollisionShapeFullBlock(this.level, pos) || !HeroDebris.canLaunch(this.level, pos, state)) {
            continue;
         }

         boolean exposed = false;
         for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (this.level.getBlockState(pos.relative(dir)).isAir()) {
               exposed = true;
               break;
            }
         }

         if (exposed) {
            out.add(pos);
         }
      }

      return out;
   }

   /**
    * A heavy hit at {@code at}: the collapse ledger settles faster there, the city grid takes the
    * hit (surge, blackout) and the aftermath remembers the ruin.
    */
   public void impact(Vec3 at, float radius, float severity, AftermathCause cause, boolean grid) {
      StructuralDamage.impact(this.level, at, CAUSE);
      if (grid) {
         CityInfrastructure.damage(this.level, at, radius, severity, "thanos", "thanos_war");
      }

      if (CityAftermath.enabled()) {
         CityAftermath.registerDamage(this.level, AftermathEvent.of(this.level, "thanos_war_" + this.incidents++, BlockPos.containing(at), radius, severity, cause,
            List.of()));
      }
   }
}

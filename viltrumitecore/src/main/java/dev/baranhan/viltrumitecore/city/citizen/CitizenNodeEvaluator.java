package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Vanilla walking rules, restricted to the prepared pedestrian network: a node is only
 * accepted where the offline analysis found an outdoor street-level surface at exactly
 * that height. Interiors, roofs, wall tops, water and drops are simply not in the grid,
 * and terrain destroyed since (craters) fails the vanilla half of the check.
 */
public class CitizenNodeEvaluator extends WalkNodeEvaluator {
   private WalkGrid grid;

   @Override
   public void prepare(PathNavigationRegion region, Mob mob) {
      super.prepare(region, mob);
      CityManager city = CityManager.of(mob.level());
      this.grid = city == null ? null : city.grid();
   }

   @Override
   public void done() {
      super.done();
      this.grid = null;
   }

   @Override
   public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z, Mob mob) {
      if (this.grid != null && !this.grid.standable(x, y, z)) {
         return BlockPathTypes.BLOCKED;
      }

      return super.getBlockPathType(level, x, y, z, mob);
   }
}

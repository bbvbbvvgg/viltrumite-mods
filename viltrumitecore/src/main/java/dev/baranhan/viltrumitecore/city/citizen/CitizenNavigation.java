package dev.baranhan.viltrumitecore.city.citizen;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Ground navigation on the pedestrian grid; no doors, swims only to get out of water. */
public class CitizenNavigation extends GroundPathNavigation {
   public CitizenNavigation(Mob mob, Level level) {
      super(mob, level);
   }

   @Override
   protected PathFinder createPathFinder(int maxVisitedNodes) {
      this.nodeEvaluator = new CitizenNodeEvaluator();
      this.nodeEvaluator.setCanPassDoors(false);
      this.nodeEvaluator.setCanOpenDoors(false);
      this.nodeEvaluator.setCanFloat(true);
      return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
   }
}

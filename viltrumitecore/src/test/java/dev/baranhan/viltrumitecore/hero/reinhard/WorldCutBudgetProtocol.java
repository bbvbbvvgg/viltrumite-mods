package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.CarveBudget;
import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutCarve;
import java.util.function.IntToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Test-only workload driver; calls the real queue, budget and carve cursor. No live level. */
final class WorldCutBudgetProtocol {
   record Result(int ticks,int maxVisited,int changed,boolean everyTickWithinBudget) {}
   private static final class CountingWorld implements WorldCutCarve.Probe {
      int visited, changed;
      public boolean loaded(BlockPos p) { visited++; return true; }
      public boolean canDestroy(BlockPos p) { return true; }
      public boolean solid(BlockPos p) { return true; }
      public boolean canLaunch(BlockPos p) { return false; }
      public boolean clear(BlockPos p) { changed++; return true; }
      public boolean launch(BlockPos p,Vec3 v) { throw new AssertionError("No live debris in budget protocol"); }
   }
   static Result run(int cuts,int positions,IntToDoubleFunction load) {
      CarveJobs.Queue queue=new CarveJobs.Queue(); CountingWorld world=new CountingWorld();
      for(int i=0;i<cuts;i++) queue.submit(new WorldCutCarve(WorldCutCarveTest.wave(positions),world,Vec3.ZERO,new Vec3(0,0,1)));
      int tick=0,max=0; boolean bounded=true;
      while(queue.size()>0) {
         if(tick>=10000) throw new AssertionError("Queue did not finish");
         int budget=CarveBudget.perTick(CarveJobs.BASE_BUDGET,load.applyAsDouble(tick)),before=world.visited;
         queue.tick(null,budget); int used=world.visited-before;
         max=Math.max(max,used); bounded &= used<=budget; tick++;
      }
      return new Result(tick,max,world.changed,bounded);
   }
}

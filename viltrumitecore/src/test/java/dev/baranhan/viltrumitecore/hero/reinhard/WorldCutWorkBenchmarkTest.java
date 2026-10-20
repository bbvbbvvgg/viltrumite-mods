package dev.baranhan.viltrumitecore.hero.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.*;
import java.nio.file.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;

/** Deterministic queue-work model, NOT an in-game TPS or wall-time benchmark. */
class WorldCutWorkBenchmarkTest {
   @Test void exportVisitCountsAndCompletionPhases() throws Exception {
      StringBuilder csv=new StringBuilder("yaw,pitch,old_visits,new_visits,ground_visits,diagonal_voxels,old_carve_ticks,new_carve_ticks,ground_carve_ticks,old_done_tick,new_done_tick,ground_done_tick\n");
      for(int yaw:new int[]{0,45,90,135})for(int pitch:new int[]{-35,0,35}) {
         Vec3 origin=new Vec3(.5,100,.5),f=Vec3.directionFromRotation(pitch,yaw);
         var g=new DiagonalCutGeometry(origin,f,61);var b=g.bounds();
         int old=((int)Math.ceil(b.maxX)-(int)Math.floor(b.minX))*((int)Math.ceil(b.maxY)-(int)Math.floor(b.minY))*((int)Math.ceil(b.maxZ)-(int)Math.floor(b.minZ));
         var w=g.wave(-64,320);int visits=w.remainingWork(),inside=0;
         while(w.hasNext()){var c=w.next();if(w.carve(c))inside++;}
         var ground=new WorldCutShape(origin,f,61).geometry().wave(-64,320);
         int groundVisits=ground.remainingWork();
         int oldTicks=(old+1999)/2000,newTicks=(visits+1999)/2000,groundTicks=(groundVisits+1999)/2000;
         assertTrue(visits<old,"thin-slab raster must reduce visits at yaw="+yaw+" pitch="+pitch);
         csv.append(yaw+","+pitch+","+old+","+visits+","+groundVisits+","+inside+","+oldTicks+","+newTicks+","+groundTicks+","+Math.max(110,80+oldTicks)+","+Math.max(110,80+newTicks)+","+Math.max(110,80+groundTicks)+"\n");
      }
      Path output=Path.of("build/reports/reinhard-carve.csv");Files.createDirectories(output.getParent());Files.writeString(output,csv);
   }
}

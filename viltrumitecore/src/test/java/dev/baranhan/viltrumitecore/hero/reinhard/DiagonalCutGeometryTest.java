package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.*;
import dev.baranhan.viltrumitecore.hero.CarveJobs;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;

class DiagonalCutGeometryTest {
   static DiagonalCutGeometry geometry(Vec3 f) { return new DiagonalCutGeometry(new Vec3(.5,100,.5),f,61); }
   @Test void tiltedBasisIsOrthonormalForAllViewAxes() {
      for(Vec3 f:List.of(new Vec3(0,0,1),new Vec3(1,.4,1),new Vec3(-1,-.4,0),new Vec3(0,1,0))) {
         var g=geometry(f);assertEquals(1,g.span().length(),1E-9);assertEquals(1,g.normal().length(),1E-9);
         assertEquals(0,g.span().dot(g.forward()),1E-9);assertEquals(0,g.span().dot(g.normal()),1E-9);
         assertTrue(g.contains(g.point(100,0,0)));assertFalse(g.contains(g.point(100,0,3)));
         assertFalse(g.contains(g.point(-1,0,0)));assertFalse(g.contains(g.point(201,0,0)));
      }
   }
   @Test void cutsAirAndVerticalStructuresNotHeightmapOnly() {
      var g=geometry(new Vec3(0,0,1));
      assertTrue(g.contains(g.point(100,20,0)));assertTrue(g.contains(g.point(100,-20,0)));
      assertTrue(Math.abs(g.point(100,20,0).y-g.point(100,-20,0).y)>25);
      assertTrue(g.intersects(new AABB(g.point(100,0,0),g.point(100,0,0)).inflate(.5),110));
      assertFalse(g.intersects(new AABB(g.point(150,0,0),g.point(150,0,0)).inflate(.5),100));
   }
   @Test void entityHitsFollowTheTaperedWidth() {
      for(Vec3 f:List.of(new Vec3(0,0,1),new Vec3(.7,.2,.7))) {
         var g=geometry(f);
         // Near the start the wound is ~5 blocks wide: 20 blocks across must be safe.
         assertTrue(g.width(2)<8);
         assertFalse(g.intersects(new AABB(g.point(2,20,0),g.point(2,20,0)).inflate(.3,.9,.3),200));
         assertFalse(g.intersects(new AABB(g.point(196,20,0),g.point(196,20,0)).inflate(.3,.9,.3),200));
         // Inside the local width it still hits; in the middle the full span applies.
         assertTrue(g.intersects(new AABB(g.point(2,g.width(2)-.5,0),g.point(2,g.width(2)-.5,0)).inflate(.3,.9,.3),200));
         assertTrue(g.intersects(new AABB(g.point(100,g.width(100)-.5,0),g.point(100,g.width(100)-.5,0)).inflate(.3,.9,.3),200));
      }
   }
   @Test void maxWidthBoundsEverySampledWidth() {
      for(long seed:new long[]{0,61,1023,777}) {
         var g=new DiagonalCutGeometry(new Vec3(.5,100,.5),new Vec3(0,0,1),seed);
         for(double lo=-2;lo<DiagonalCutGeometry.LENGTH+2;lo+=3.7) for(double len:new double[]{0,.6,2.5,9}) {
            double hi=lo+len,bound=g.maxWidth(Math.max(0,lo),hi),max=0;
            for(double x=Math.max(0,lo);x<=hi;x+=.01) max=Math.max(max,g.width(x));
            assertTrue(bound+1E-9>=max,"seed "+seed+" ["+lo+","+hi+"]");
            assertTrue(bound<=DiagonalCutGeometry.HALF_SPAN+1E-9);
         }
      }
   }
   @Test void finiteInputAndDeterministicTaper() {
      assertThrows(IllegalArgumentException.class,()->geometry(new Vec3(Double.NaN,0,1)));
      var g=geometry(new Vec3(0,0,1));assertTrue(g.width(0)<g.width(100));assertTrue(g.width(199)<g.width(100));
      assertEquals(g.width(90),geometry(new Vec3(0,0,1)).width(90));
   }
   @Test void cursorAccountsForEveryCandidateClipsAndNeverRepeats() {
      for(Vec3 f:List.of(new Vec3(0,0,1),new Vec3(.7,.2,.7),new Vec3(-.7,0,.7))) {
         var g=geometry(f);var w=g.wave(98,102);int total=w.remainingWork(),seen=0,inside=0;
         Set<Long> unique=new HashSet<>();
         while(w.hasNext()) {
            var c=w.next();assertTrue(c.y()>=98&&c.y()<102);assertTrue(unique.add(BlockPos.asLong(c.x(),c.y(),c.z())));
            if(w.carve(c)) {inside++;assertTrue(g.contains(new Vec3(c.x()+.5,c.y()+.5,c.z()+.5)));}
            seen++;assertEquals(total-seen,w.remainingWork());
         }
         assertEquals(total,seen);assertTrue(inside>100);assertTrue(inside<seen);assertThrows(NoSuchElementException.class,w::next);
      }
      assertFalse(geometry(new Vec3(0,0,1)).wave(400,401).hasNext());
   }
   @Test void twoActualCarvesShareOneBudgetAndBothAdvance() {
      class Probe implements WorldCutCarve.Probe {
         int visited;
         public boolean loaded(BlockPos p){visited++;return false;}
         public boolean canDestroy(BlockPos p){throw new AssertionError("unloaded probe reached");}
         public boolean solid(BlockPos p){return true;}public boolean canLaunch(BlockPos p){return false;}
         public boolean clear(BlockPos p){throw new AssertionError();}public boolean launch(BlockPos p,Vec3 v){throw new AssertionError();}
      }
      Probe a=new Probe(),b=new Probe();var g=geometry(new Vec3(0,0,1));
      var wa=g.wave(98,102);var wb=g.wave(98,102);int before=wa.remainingWork();
      var ja=new WorldCutCarve(wa,a,g.origin(),g.forward());var jb=new WorldCutCarve(wb,b,g.origin(),g.forward());
      CarveJobs.Queue queue=new CarveJobs.Queue();queue.submit(ja);queue.submit(jb);queue.tick(null,2000);
      assertEquals(1000,before-wa.remainingWork());assertEquals(1000,before-wb.remainingWork());
      assertTrue(queue.contains(ja));assertTrue(queue.contains(jb));assertTrue(a.visited+b.visited<=2000);
   }
}

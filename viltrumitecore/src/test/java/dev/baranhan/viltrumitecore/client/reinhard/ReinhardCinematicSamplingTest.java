package dev.baranhan.viltrumitecore.client.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import org.junit.jupiter.api.Test;
class ReinhardCinematicSamplingTest {
   @Test void projectionUsesProductionTracksWithoutMutatingCachedSkeleton() throws Exception {
      var clips=ReinhardSecondPassTest.clips();
      for(double t:new double[]{0,12,20,66,73,80,87,100}) {
         var live=ReinhardSecondPassTest.body();var expected=ReinhardSecondPassTest.body();
         var root=live.getBone("body");var grip=live.getBone("reid_grip");root.rotY=.271F;grip.posX=7;
         var sampledRoot=ReinhardTitanBlade.sample(root,null,clips.get("world_cut"),t/20);
         var sampledGrip=ReinhardTitanBlade.sample(grip,sampledRoot,clips.get("world_cut"),t/20);
         AnimationController.seek(clips.get("world_cut"),expected,t/20);
         var a=ReinhardGeometry.frame(sampledGrip);var b=ReinhardGeometry.frame(expected.getBone("reid_grip"));
         assertTrue(a.equals(b,1E-5F),"same production grip at tick "+t);
         assertEquals(.271F,root.rotY);assertEquals(7,grip.posX);
      }
   }
}

package dev.baranhan.viltrumitecore.client.render.erasure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ErasureStrokeTest {
   private static ErasurePath line(double length) {
      return ErasurePath.smooth(List.of(Vec3.ZERO, new Vec3(length / 2, 0, 0), new Vec3(length, 0, 0)));
   }

   @Test void vertexColourRadiusStaysNormalisedAtAnyScale() {
      for (float scale : new float[]{1F, 7.5F, 40F}) {
         ErasureStroke stroke = new ErasureStroke(line(3 * scale), 0F, (float)(3 * scale), scale, 0.3F, ErasureStyle.CYAN).update(0.2F);
         for (boolean shell : new boolean[]{false, true})
            for (ErasureStroke.Ring ring : stroke.rings(shell)) {
               assertTrue(ring.colourRadius() >= 0 && ring.colourRadius() <= 1, "scale " + scale + " shell " + shell);
            }
      }
   }

   @Test void scaleOneKeepsTheOriginalSizes() {
      ErasureStroke stroke = new ErasureStroke(line(3), 0.3F, ErasureStyle.CYAN).update(0.05F);
      assertEquals(0.34F, stroke.width, 1E-6);
      assertEquals(0.17F * (0.4F + 0.6F * stroke.aura), stroke.auraRadius(), 1E-6);
   }

   @Test void originalTimeline() {
      ErasureStroke stroke = new ErasureStroke(line(3), 0.3F, ErasureStyle.CYAN);
      assertTrue(stroke.update(-0.1F).auraVisible());
      assertFalse(stroke.tubeVisible());
      assertEquals(stroke.length(), stroke.update(0.1F).headS(), 1E-4);
      assertTrue(stroke.update(0.3F).tubeVisible());
      assertEquals(1F, stroke.update(0.75F).decay(), 1E-6);
      assertTrue(stroke.update(0.76F).finished());
   }

   @Test void continuationHasNoPreAura() {
      ErasureStroke stroke = new ErasureStroke(line(3), 0F, 3F, 1F, 0F, ErasureStyle.CYAN, false).update(-0.1F);
      assertFalse(stroke.auraVisible());
   }

   @Test void offsetWindowSlidesAlongThePath() {
      ErasurePath path = line(30);
      ErasureStroke stroke = new ErasureStroke(path, 10F, 5F, 1F, 0F, ErasureStyle.CYAN).update(0.2F);
      assertEquals(10.0, stroke.pointAt(0).x, 1E-3);
      assertTrue(stroke.bounds().maxX < 16.5);
   }
}

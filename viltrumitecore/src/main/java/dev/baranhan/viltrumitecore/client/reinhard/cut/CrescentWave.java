package dev.baranhan.viltrumitecore.client.reinhard.cut;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * The flying blade crescent of Reinhard's aerial diagonal strikes (the Cut Rain waves): a row of light pixels on an
 * arc of +-1.2 rad across {@code span}, its middle leading and its ends bowed back along the flight direction.
 * Shared so the diagonal World Cut sends out the same wave (REINHARD OVERHAUL item 14); Cut Rain draws exactly what
 * it drew before.
 */
public final class CrescentWave {
   /** Half the arc, radians: the ends sit at sin(1.2) = 0.93 of the radius across and lag (1 - cos 1.2) * bow. */
   static final double HALF_ARC = 1.2;

   private CrescentWave() {
   }

   /**
    * Point {@code i} of {@code n} on the crescent.
    *
    * @param centre the leading middle of the crescent
    * @param span   unit direction across the crescent
    * @param dir    unit flight direction
    * @param radius half width across, blocks (before the sin of the arc)
    * @param bow    how far the ends lag behind, blocks (before 1 - cos of the arc)
    */
   static Vec3 point(Vec3 centre, Vec3 span, Vec3 dir, double radius, double bow, int i, int n) {
      double a = -HALF_ARC + i * 2 * HALF_ARC / (n - 1);
      return centre.add(span.scale(Math.sin(a) * radius)).add(dir.scale((Math.cos(a) - 1) * bow));
   }

   /** Draws the crescent as {@code n} camera-facing pixels into a POSITION_COLOR quad buffer. */
   static void draw(BufferBuilder buffer, Vec3 eye, Camera camera, Vec3 centre, Vec3 span, Vec3 dir, double radius,
                    double bow, int n, float size, int r, int g, int b, int alpha) {
      if (alpha <= 0) return;
      for (int i = 0; i < n; i++) {
         PixelVfx.billboardPixel(buffer, eye, camera, point(centre, span, dir, radius, bow, i, n), size, r, g, b, alpha);
      }
   }
}

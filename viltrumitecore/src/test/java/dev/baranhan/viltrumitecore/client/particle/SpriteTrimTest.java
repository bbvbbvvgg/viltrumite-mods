package dev.baranhan.viltrumitecore.client.particle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SpriteTrimTest {
   private static final Path SMOKE = Path.of("src/main/binassets/assets/viltrumitecore/textures/particle");

   @Test
   void emptyImageHasNoBounds() {
      assertNull(SpriteTrim.bounds(8, 8, 1, (x, y) -> false));
   }

   @Test
   void boundsArePaddedAndClamped() {
      // one visible texel at (3, 5) in an 8x8 image, padded by one texel
      float[] b = SpriteTrim.bounds(8, 8, 1, (x, y) -> x == 3 && y == 5);
      assertArrayEquals(new float[]{2 / 8F, 5 / 8F, 4 / 8F, 7 / 8F}, b, 1.0E-6F);
      // a texel on the border cannot pad outside the image
      float[] e = SpriteTrim.bounds(8, 8, 1, (x, y) -> x == 0 && y == 7);
      assertArrayEquals(new float[]{0F, 2 / 8F, 6 / 8F, 1F}, e, 1.0E-6F);
   }

   @Test
   void fullImageMapsToTheWholeInsetQuad() {
      assertArrayEquals(SpriteTrim.FULL, SpriteTrim.toQuad(new float[]{0F, 1F, 0F, 1F}, 0.01F), 1.0E-6F);
      assertArrayEquals(SpriteTrim.FULL, SpriteTrim.toQuad(new float[]{0.01F, 0.99F, 0.01F, 0.99F}, 0.01F), 1.0E-6F);
      assertEquals(SpriteTrim.FULL, SpriteTrim.toQuad(null, 0.01F));
   }

   @Test
   void quadFractionsKeepTheSameTexelPerPoint() {
      // a quad point s shows image fraction inset + s * (1 - 2 inset): the trimmed corners must land on the same texels
      float inset = 0.01F;
      float[] image = {0.25F, 0.75F, 0.125F, 0.875F};
      float[] q = SpriteTrim.toQuad(image, inset);
      for (int i = 0; i < 4; i++) {
         assertEquals(image[i], inset + q[i] * (1 - 2 * inset), 1.0E-6F);
      }
   }

   /** The shipped smoke sprites: every visible texel stays inside the trim, and the trim really saves fill. */
   @Test
   void smokeSpritesTrimOnlyTransparentTexels() throws Exception {
      double total = 0;
      for (int i = 0; i < 8; i++) {
         String b64 = Files.readString(SMOKE.resolve("meteor_smoke_" + i + ".png.b64"), StandardCharsets.US_ASCII).replaceAll("\\s", "");
         BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
         int w = img.getWidth(), h = img.getHeight();
         float[] b = SpriteTrim.bounds(w, h, 1, (x, y) -> (img.getRGB(x, y) >>> 24) != 0);
         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               if ((img.getRGB(x, y) >>> 24) != 0) {
                  // the whole bilinear footprint (texel +-1) of every visible texel is inside the rectangle
                  assertTrue((x - 1) / (float)w >= b[0] - 1.0E-6F || b[0] == 0F, "u0 sprite " + i);
                  assertTrue((x + 2) / (float)w <= b[1] + 1.0E-6F || b[1] == 1F, "u1 sprite " + i);
                  assertTrue((y - 1) / (float)h >= b[2] - 1.0E-6F || b[2] == 0F, "v0 sprite " + i);
                  assertTrue((y + 2) / (float)h <= b[3] + 1.0E-6F || b[3] == 1F, "v1 sprite " + i);
               }
            }
         }
         float[] q = SpriteTrim.toQuad(b, MeteorSmokeParticle.UV_INSET);
         total += (q[1] - q[0]) * (q[3] - q[2]);
      }
      double mean = total / 8;
      assertTrue(mean < 0.65, "mean trimmed area " + mean);
   }
}

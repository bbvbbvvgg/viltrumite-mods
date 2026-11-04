package dev.baranhan.viltrumitecore.client.particle;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The part of a particle sprite that can ever show up on screen: the rectangle around every texel with alpha
 * above zero, padded by one texel for the linear filter's footprint.
 *
 * <p>The soft smoke puffs are round clouds in a square 64 px sprite: only 25-42 % of the texels have any alpha
 * and the bounding rectangle covers 43-74 % (mean 58 %) of the quad. Everything outside it samples alpha 0 and is
 * discarded by {@code soft_particle.fsh}, so rasterizing it is pure wasted fill rate on screens full of
 * overlapping smoke (see {@code docs/performance/BOTTLENECKS.md}). Drawing only this rectangle gives the same
 * pixels with ~42 % fewer fragments. Bounds are read from the loaded texture, so a resource pack with opaque
 * corners simply gets the full quad back.
 */
public final class SpriteTrim {
   /** No trim: the whole inset quad. */
   public static final float[] FULL = new float[]{0.0F, 1.0F, 0.0F, 1.0F};
   private static final Map<SpriteContents, float[]> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

   @FunctionalInterface
   public interface Opaque {
      boolean test(int x, int y);
   }

   private SpriteTrim() {
   }

   /**
    * Pure: the visible rectangle of a {@code width x height} image in texel-edge fractions {u0, u1, v0, v1}
    * (0..1 of the image), padded by {@code pad} texels; {@code null} when nothing is visible.
    */
   public static float[] bounds(int width, int height, int pad, Opaque visible) {
      int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            if (visible.test(x, y)) {
               if (x < minX) minX = x;
               if (x > maxX) maxX = x;
               if (y < minY) minY = y;
               if (y > maxY) maxY = y;
            }
         }
      }

      if (maxX < 0) {
         return null;
      }

      return new float[]{
         Math.max(0, minX - pad) / (float)width,
         Math.min(width, maxX + 1 + pad) / (float)width,
         Math.max(0, minY - pad) / (float)height,
         Math.min(height, maxY + 1 + pad) / (float)height
      };
   }

   /**
    * Pure: maps image fractions to the quad of a sprite drawn with a UV inset ({@code inset} = 0.01 means the quad
    * shows 1 %..99 % of the image). Returns quad fractions {s0, s1, t0, t1} clamped to 0..1.
    */
   public static float[] toQuad(float[] image, float inset) {
      if (image == null) {
         return FULL;
      }

      float span = 1.0F - 2.0F * inset;
      return new float[]{
         clamp01((image[0] - inset) / span),
         clamp01((image[1] - inset) / span),
         clamp01((image[2] - inset) / span),
         clamp01((image[3] - inset) / span)
      };
   }

   /** Quad fractions of the visible part of {@code sprite} drawn with {@code inset}; {@link #FULL} when unknown. */
   public static float[] of(TextureAtlasSprite sprite, float inset) {
      if (sprite == null) {
         return FULL;
      }

      SpriteContents contents = sprite.contents();
      float[] image = CACHE.computeIfAbsent(contents, SpriteTrim::scan);
      return image.length == 0 ? FULL : toQuad(image, inset);
   }

   private static float[] scan(SpriteContents contents) {
      try {
         int[] frames = contents.getUniqueFrames().toArray();
         if (frames.length == 0) {
            frames = new int[]{0};
         }

         final int[] all = frames;
         // pad 1 texel: a linearly filtered fragment reads texels up to one texel away from its sample point
         float[] b = bounds(contents.width(), contents.height(), 1, (x, y) -> {
            for (int f : all) {
               if (!contents.isTransparent(f, x, y)) {
                  return true;
               }
            }
            return false;
         });
         return b == null ? FULL.clone() : b;
      } catch (RuntimeException e) {
         // image already released (resource reload) or an unusual sprite: draw the full quad
         return new float[0];
      }
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
   }
}

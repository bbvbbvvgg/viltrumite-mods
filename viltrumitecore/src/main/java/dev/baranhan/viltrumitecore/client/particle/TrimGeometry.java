package dev.baranhan.viltrumitecore.client.particle;

/**
 * Pure geometry for trimmed smoke quads drawn inside fog (no Minecraft types, unit-tested).
 *
 * <p>Vanilla draws a particle quad as two triangles split along the diagonal from corner (-1, -1) to (1, 1)
 * (QUADS index order 0-1-2, 2-3-0) and interpolates the per-vertex fog distance linearly over each triangle. To
 * reproduce that exactly on a smaller rectangle, the rectangle is cut along the same diagonal and every new vertex
 * carries the distance the original triangle would have interpolated there ({@link #fogAt}). The distance travels to
 * {@code soft_particle.vsh} in the unused high bits of the lightmap coordinates ({@link #packLight}).
 */
final class TrimGeometry {
   /** Fog distance resolution in the packed lightmap bits: 1/64 block, up to {@link #MAX_FOG_DISTANCE}. */
   static final float FOG_STEP = 64.0F;
   /** 14 bits (7 per lightmap short) minus the "no value" code 0. */
   static final float MAX_FOG_DISTANCE = (16383 - 1) / FOG_STEP;

   private TrimGeometry() {
   }

   /**
    * Clip the rectangle [x0, x1] × [y0, y1] (corners in vanilla order (x0,y0) (x0,y1) (x1,y1) (x1,y0)) to the side of
    * the line y = x that holds the triangle (-1,-1)(-1,1)(1,1) ({@code upper}: y ≥ x) or (1,1)(1,-1)(-1,-1)
    * ({@code !upper}: y ≤ x). Writes x, y pairs into {@code out} (room for 5 points) in the same winding and returns
    * the point count (0, 3, 4 or 5). Points on the line belong to both sides, so the halves share exact vertices.
    */
   static int clip(float x0, float y0, float x1, float y1, boolean upper, float[] out) {
      float[] px = {x0, x0, x1, x1};
      float[] py = {y0, y1, y1, y0};
      int n = 0;
      for (int i = 0; i < 4; i++) {
         int j = (i + 1) & 3;
         float hi = side(px[i], py[i], upper), hj = side(px[j], py[j], upper);
         if (hi >= 0.0F) {
            out[n++] = px[i];
            out[n++] = py[i];
         }
         if (hi > 0.0F && hj < 0.0F || hi < 0.0F && hj > 0.0F) {
            // same t for both halves: (-a) / (-b) == a / b exactly in IEEE arithmetic
            float t = hi / (hi - hj);
            float ix = px[i] + t * (px[j] - px[i]);
            out[n++] = ix;
            out[n++] = ix; // the point lies on y = x
         }
      }
      return n / 2;
   }

   private static float side(float x, float y, boolean upper) {
      return upper ? y - x : x - y;
   }

   /**
    * Fog distance that the original full quad interpolates at plane point (x, y), from its corner distances
    * d0 (-1,-1), d1 (-1,1), d2 (1,1), d3 (1,-1).
    */
   static float fogAt(float x, float y, float d0, float d1, float d2, float d3) {
      float sx = (x + 1.0F) * 0.5F, sy = (y + 1.0F) * 0.5F;
      return y >= x ? d0 + (d2 - d1) * sx + (d1 - d0) * sy : d0 + (d3 - d0) * sx + (d2 - d3) * sy;
   }

   /**
    * Packed light (as for {@code VertexConsumer.uv2(int)}) with a fog distance in bits 8..14 of both 16-bit halves.
    * The lightmap itself only uses bits 0..7 (0..240), so {@code soft_particle.vsh} masks them back. Distance code 0
    * means "none" (the shader computes the fog distance as vanilla does).
    */
   static int packLight(int light, float fogDistance) {
      int code = Math.round(fogDistance * FOG_STEP) + 1;
      int u = light & 0xFF | (code & 0x7F) << 8;
      int v = light >>> 16 & 0xFF | (code >>> 7 & 0x7F) << 8;
      return u | v << 16;
   }

   /** The distance stored by {@link #packLight} (for tests). */
   static float unpackFog(int packed) {
      int code = (packed >>> 8 & 0x7F) | (packed >>> 24 & 0x7F) << 7;
      return code == 0 ? -1.0F : (code - 1) / FOG_STEP;
   }
}

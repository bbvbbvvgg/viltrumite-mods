package dev.baranhan.viltrumitecore.client.thanos;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * World Erasure's model-cell dissolve ({@code client/reinhard/erasure/ErasureDissolveRenderer}),
 * generalised for the Thanos event: any model quad stream in, model-pixel cells out (1/16 block),
 * each cell with a threshold from a {@link Rule} on its world position plus stable noise. Cells
 * past the front are dropped (and become {@link Mote}s), the band at the front glows in the
 * effect's colour. {@code forming} runs it the other way (a figure condensing out of light).
 * Positions arrive in the space of the caller's pose; {@code toWorld} turns them into
 * camera-relative world space (the inverse view rotation in the level pass, identity when the
 * caller already draws in camera-relative world space).
 */
public final class ThanosCells implements VertexConsumer {
   /** Threshold 0..1 of a cell at a world position (camera-relative + cam) with its noise 0..1. */
   public interface Rule {
      float threshold(double wx, double wy, double wz, float noise);
   }

   /** A released cell: its world position, threshold and two random drift values -0.5..0.5. */
   public record Mote(float x, float y, float z, float threshold, float drift, float spin) {
   }

   public static final int MAX_MOTES = 4000;
   private final VertexConsumer out;
   private final VertexConsumer shell;
   private final Matrix3f toWorld;
   private final double camX;
   private final double camY;
   private final double camZ;
   private final Rule rule;
   private final float front;
   private final float edge;
   private final float edgeR;
   private final float edgeG;
   private final float edgeB;
   private final float hot;
   private final float ash;
   private final boolean forming;
   private final long seed;
   private final List<Mote> motes;
   private final float[] px = new float[4];
   private final float[] py = new float[4];
   private final float[] pz = new float[4];
   private final float[] cu = new float[4];
   private final float[] cv = new float[4];
   private final int[] col = new int[4];
   private final int[] ovl = new int[4];
   private final int[] lit = new int[4];
   private final float[] nx = new float[4];
   private final float[] ny = new float[4];
   private final float[] nz = new float[4];
   private final Vector3f scratch = new Vector3f();
   private int n;
   private double vx;
   private double vy;
   private double vz;
   private int r = 255;
   private int g = 255;
   private int b = 255;
   private int a = 255;
   private float u;
   private float v;
   private int overlay = OverlayTexture.NO_OVERLAY;
   private int light;
   private float nnx;
   private float nny = 1.0F;
   private float nnz;

   /**
    * @param shell additive glow buffer for the hot band (null: no shell)
    * @param hot   white light over every remaining cell 0..1
    * @param ash   darkening of the cells near the front 0..1 (crumbling to ash)
    */
   public ThanosCells(VertexConsumer out, VertexConsumer shell, Matrix3f toWorld, double camX, double camY, double camZ, Rule rule, float front,
      float edge, int edgeRgb, float hot, float ash, boolean forming, long seed, List<Mote> motes) {
      this.out = out;
      this.shell = shell;
      this.toWorld = toWorld;
      this.camX = camX;
      this.camY = camY;
      this.camZ = camZ;
      this.rule = rule;
      this.front = front;
      this.edge = Math.max(1.0E-3F, edge);
      this.edgeR = ThanosView.r(edgeRgb);
      this.edgeG = ThanosView.g(edgeRgb);
      this.edgeB = ThanosView.b(edgeRgb);
      this.hot = hot;
      this.ash = ash;
      this.forming = forming;
      this.seed = seed;
      this.motes = motes;
   }

   /** Stable hash noise 0..1 of a quantized cell (same hash as World Erasure's cells). */
   public static float noise(int x, int y, int z, long seed) {
      long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
      h ^= h >>> 29;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 32;
      return (h >>> 40) / (float)(1 << 24);
   }

   /** Is a cell with this threshold drawn at the front? */
   public static boolean shown(float threshold, float front, boolean forming) {
      return forming ? threshold <= front : threshold >= front;
   }

   /** Hot band 0..1 of a shown cell (1 right at the front). */
   public static float band(float threshold, float front, float edge, boolean forming) {
      float d = forming ? front - threshold : threshold - front;
      if (d < 0.0F || d >= edge) {
         return 0.0F;
      }

      return 1.0F - d / edge;
   }

   @Override
   public VertexConsumer vertex(double x, double y, double z) {
      this.vx = x;
      this.vy = y;
      this.vz = z;
      return this;
   }

   @Override
   public VertexConsumer color(int red, int green, int blue, int alpha) {
      this.r = red;
      this.g = green;
      this.b = blue;
      this.a = alpha;
      return this;
   }

   @Override
   public VertexConsumer uv(float tu, float tv) {
      this.u = tu;
      this.v = tv;
      return this;
   }

   @Override
   public VertexConsumer overlayCoords(int ou, int ov) {
      this.overlay = ou & 0xFFFF | ov << 16;
      return this;
   }

   @Override
   public VertexConsumer uv2(int lu, int lv) {
      this.light = lu & 0xFFFF | lv << 16;
      return this;
   }

   @Override
   public VertexConsumer normal(float x, float y, float z) {
      this.nnx = x;
      this.nny = y;
      this.nnz = z;
      return this;
   }

   @Override
   public void endVertex() {
      this.push((float)this.vx, (float)this.vy, (float)this.vz, this.r << 24 | this.g << 16 | this.b << 8 | this.a, this.u, this.v, this.overlay,
         this.light, this.nnx, this.nny, this.nnz);
   }

   @Override
   public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float texU, float texV, int overlayUV, int lightmapUV,
      float normalX, float normalY, float normalZ) {
      this.push(x, y, z, (int)(red * 255.0F) << 24 | (int)(green * 255.0F) << 16 | (int)(blue * 255.0F) << 8 | (int)(alpha * 255.0F), texU, texV,
         overlayUV, lightmapUV, normalX, normalY, normalZ);
   }

   @Override
   public void defaultColor(int red, int green, int blue, int alpha) {
   }

   @Override
   public void unsetDefaultColor() {
   }

   private void push(float x, float y, float z, int c, float tu, float tv, int o, int l, float ax, float ay, float az) {
      this.px[this.n] = x;
      this.py[this.n] = y;
      this.pz[this.n] = z;
      this.col[this.n] = c;
      this.cu[this.n] = tu;
      this.cv[this.n] = tv;
      this.ovl[this.n] = o;
      this.lit[this.n] = l;
      this.nx[this.n] = ax;
      this.ny[this.n] = ay;
      this.nz[this.n] = az;
      if (++this.n == 4) {
         this.n = 0;
         this.quad();
      }
   }

   private void quad() {
      float ea = this.dist(0, 1);
      float eb = this.dist(0, 3);
      int na = Mth.clamp(Math.round(ea * 16.0F), 1, 24);
      int nb = Mth.clamp(Math.round(eb * 16.0F), 1, 24);
      float red = (this.col[0] >>> 24) / 255.0F;
      float green = (this.col[0] >>> 16 & 255) / 255.0F;
      float blue = (this.col[0] >>> 8 & 255) / 255.0F;
      float alpha = (this.col[0] & 255) / 255.0F;
      for (int i = 0; i < na; i++) {
         for (int j = 0; j < nb; j++) {
            float s0 = i / (float)na;
            float s1 = (i + 1) / (float)na;
            float t0 = j / (float)nb;
            float t1 = (j + 1) / (float)nb;
            float sm = (s0 + s1) * 0.5F;
            float tm = (t0 + t1) * 0.5F;
            this.scratch.set(lerp(this.px, sm, tm), lerp(this.py, sm, tm), lerp(this.pz, sm, tm));
            this.toWorld.transform(this.scratch);
            double wx = this.scratch.x + this.camX;
            double wy = this.scratch.y + this.camY;
            double wz = this.scratch.z + this.camZ;
            float noise = noise(Mth.floor(wx * 16.0), Mth.floor(wy * 16.0), Mth.floor(wz * 16.0), this.seed);
            float threshold = this.rule.threshold(wx, wy, wz, noise);
            if (!shown(threshold, this.front, this.forming)) {
               if (!this.forming && this.motes != null && this.motes.size() < MAX_MOTES) {
                  float second = noise(Mth.floor(wz * 16.0), Mth.floor(wx * 16.0), Mth.floor(wy * 16.0), ~this.seed);
                  if (second < 0.5F) {
                     this.motes.add(new Mote((float)wx, (float)wy, (float)wz, threshold, second - 0.25F, noise - 0.5F));
                  }
               }

               continue;
            }

            float band = band(threshold, this.front, this.edge, this.forming);
            float white = Math.max(this.hot, band * 0.8F);
            int o = white > 0.0F ? OverlayTexture.pack(OverlayTexture.u(white), OverlayTexture.WHITE_OVERLAY_V) : this.ovl[0];
            float dark = 1.0F - this.ash * band;
            float[][] corner = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
            for (float[] k : corner) {
               this.out.vertex(lerp(this.px, k[0], k[1]), lerp(this.py, k[0], k[1]), lerp(this.pz, k[0], k[1]), red * dark, green * dark, blue * dark, alpha,
                  lerp(this.cu, k[0], k[1]), lerp(this.cv, k[0], k[1]), o, this.lit[0], this.nx[0], this.ny[0], this.nz[0]);
            }

            float shellAlpha = 0.85F * band + 0.12F * this.hot;
            if (this.shell != null && shellAlpha > 0.02F) {
               float lift = 0.012F + 0.02F * band;
               for (float[] k : corner) {
                  this.shell.vertex(lerp(this.px, k[0], k[1]) + this.nx[0] * lift, lerp(this.py, k[0], k[1]) + this.ny[0] * lift,
                     lerp(this.pz, k[0], k[1]) + this.nz[0] * lift, this.edgeR * shellAlpha, this.edgeG * shellAlpha, this.edgeB * shellAlpha, 1.0F,
                     0.5F, 0.5F, OverlayTexture.NO_OVERLAY, 0xF000F0, this.nx[0], this.ny[0], this.nz[0]);
               }
            }
         }
      }
   }

   private float dist(int i, int j) {
      float dx = this.px[j] - this.px[i];
      float dy = this.py[j] - this.py[i];
      float dz = this.pz[j] - this.pz[i];
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** Bilinear over the quad: s along 0->1, t along 0->3. */
   private static float lerp(float[] q, float s, float t) {
      return (1.0F - s) * (1.0F - t) * q[0] + s * (1.0F - t) * q[1] + s * t * q[2] + (1.0F - s) * t * q[3];
   }
}

package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The target's end on its REAL model, at its real world position, for every viewer: the entity's
 * own renderer is run (frozen pose, partial tick 0) into a buffer that splits every model quad
 * into model-pixel cells, lights them white (overlay + full light) and drops the cells the
 * dissolve front has passed; the dropped cells rise as light. Drawn after the glass, so the
 * target hangs alone in the void, depth cleared, with the level's own projection and view.
 */
public final class ErasureDissolveRenderer {
   private static boolean drawing;
   private static final Matrix4f PROJECTION = new Matrix4f();
   private static boolean projectionValid;
   /** Last frame's cells (absolute world coords): the light keeps rising after the entity is gone. */
   private static List<Cell> lastCells = List.of();
   private static int lastTarget = Integer.MIN_VALUE;
   private ErasureDissolveRenderer() {}

   /** The scene's own draw of the target is running (the level-render hide must let it through). */
   public static boolean drawing() {return drawing;}

   static void captureProjection(Matrix4f projection) {PROJECTION.set(projection); projectionValid = true;}
   static void reset() {lastCells = List.of(); lastTarget = Integer.MIN_VALUE; projectionValid = false;}

   record Cell(float x, float y, float z, float threshold, float drift, float spin, boolean mote) {}

   /**
    * @param entity the frozen target or null (gone: only the rising light and the last flash)
    * @param focus  target centre (packet metadata) for the bloom
    */
   static void render(LivingEntity entity, int targetId, Vec3 focus, float focusSize, float t, boolean erase, long seed, PoseStack view) {
      if (!projectionValid) return;
      if (targetId != lastTarget) {lastCells = List.of(); lastTarget = targetId;}
      Minecraft mc = Minecraft.getInstance();
      Camera camera = mc.gameRenderer.getMainCamera();
      Vec3 cam = camera.getPosition();
      float glow = ErasureDissolve.glow(t, erase), front = ErasureDissolve.front(t, erase);
      PoseStack modelView = RenderSystem.getModelViewStack();
      RenderSystem.backupProjectionMatrix();
      RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
      modelView.pushPose();
      modelView.setIdentity();
      modelView.mulPoseMatrix(view.last().pose());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
      try {
         if (entity != null && entity.isAlive() || entity != null && !erase) {
            List<Cell> cells = new ArrayList<>();
            drawModel(mc, entity, cam, glow, front, t, erase, seed, cells);
            lastCells = cells;
         }
         drawLight(camera, cam, focus, focusSize, glow, front, t, erase);
      } finally {
         drawing = false;
         RenderSystem.disableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.depthMask(true);
         RenderSystem.enableDepthTest();
         RenderSystem.enableCull();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.restoreProjectionMatrix();
         Lighting.setupFor3DItems();
      }
   }

   private static void drawModel(Minecraft mc, LivingEntity entity, Vec3 cam, float glow, float front, float t, boolean erase, long seed, List<Cell> cells) {
      var dispatcher = mc.getEntityRenderDispatcher();
      double x = Mth.lerp(0, entity.xOld, entity.getX()) - cam.x, y = Mth.lerp(0, entity.yOld, entity.getY()) - cam.y,
         z = Mth.lerp(0, entity.zOld, entity.getZ()) - cam.z;
      int real = dispatcher.getPackedLightCoords(entity, 0);
      int block = LightTexture.block(real), sky = LightTexture.sky(real);
      int light = LightTexture.pack(Math.round(Mth.lerp(Math.min(1, glow * 1.5F), block, 15)), Math.round(Mth.lerp(Math.min(1, glow * 1.5F), sky, 15)));
      // Normals and lights both in camera-relative world space: the same shading as the level pass.
      if (mc.level != null && mc.level.effects().constantAmbientLight()) Lighting.setupNetherLevel(new Matrix4f());
      else Lighting.setupLevel(new Matrix4f());
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      Frame frame = new Frame(cam, (float)(y + cam.y), Math.max(.1F, entity.getBbHeight()), glow, front, t, erase, seed, cells);
      MultiBufferSource split = type -> type.format().equals(DefaultVertexFormat.NEW_ENTITY) && type.mode() == VertexFormat.Mode.QUADS
         ? new Splitter(buffers.getBuffer(type), frame) : Discard.INSTANCE;
      boolean shadow = true;
      drawing = true;
      try {
         dispatcher.setRenderShadow(false);
         shadow = false;
         RenderSystem.runAsFancy(() -> dispatcher.render(entity, x, y, z, Mth.lerp(0, entity.yRotO, entity.getYRot()), 0, new PoseStack(), split, light));
         buffers.endBatch();
      } finally {
         drawing = false;
         if (!shadow) dispatcher.setRenderShadow(true);
      }
      // Halo shell and the white-hot band, additive over the model.
      if (frame.shell.isEmpty()) return;
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      float[] s = frame.shell.array();
      for (int i = 0; i + 15 < frame.shell.size(); i += 16) {
         float a = s[i + 12];
         float r = s[i + 13], g = s[i + 14], bl = s[i + 15];
         for (int k = 0; k < 4; k++) b.vertex(s[i + k * 3], s[i + k * 3 + 1], s[i + k * 3 + 2]).color(r, g, bl, a).endVertex();
      }
      BufferUploader.drawWithShader(b.end());
   }

   private static void drawLight(Camera camera, Vec3 cam, Vec3 focus, float size, float glow, float front, float t, boolean erase) {
      Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
      RenderSystem.depthMask(false);
      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      float fx = (float)(focus.x - cam.x), fy = (float)(focus.y - cam.y), fz = (float)(focus.z - cam.z);
      // soft bloom around the body; the last spark when the final cell goes
      float bloom = .3F * glow * (1 - .6F * Math.min(1, front));
      float spark = erase ? pulse(t, dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.ERASE_REMOVE_TICK - 2, 2, 9) : 0;
      if (bloom > .005F) disc(b, fx, fy, fz, left, up, size * .85F + .5F, .92F, .95F, 1, bloom);
      if (bloom > .005F) disc(b, fx, fy, fz, left, up, size * .45F + .2F, 1, 1, 1, bloom * .8F);
      if (spark > .005F) {
         disc(b, fx, fy, fz, left, up, size * 1.3F + 1, .9F, .95F, 1, .55F * spark);
         disc(b, fx, fy, fz, left, up, .35F + size * .2F, 1, 1, 1, spark);
      }
      // rising light from the released cells
      for (Cell c : lastCells) {
         if (!c.mote()) continue;
         float age = ErasureDissolve.released(c.threshold(), t, erase);
         if (age < 0 || age >= ErasureDissolve.MOTE_LIFE) continue;
         float life = age / ErasureDissolve.MOTE_LIFE;
         float mx = c.x() + c.drift() * age * .012F + (float)Math.sin(age * .25F + c.spin() * 6) * .04F * life;
         float my = c.y() + age * .028F + age * age * .0011F;
         float mz = c.z() + c.spin() * age * .012F + (float)Math.cos(age * .25F + c.drift() * 6) * .04F * life;
         float a = (1 - life) * (1 - life) * .95F, r = .025F + .02F * (1 - life);
         disc(b, (float)(mx - cam.x), (float)(my - cam.y), (float)(mz - cam.z), left, up, r * 2.2F, .75F, .85F, 1, a * .35F);
         disc(b, (float)(mx - cam.x), (float)(my - cam.y), (float)(mz - cam.z), left, up, r, 1, 1, 1, a);
      }
      BufferUploader.drawWithShader(b.end());
   }

   static float pulse(float t, float at, float rise, float fall) {
      if (t < at - rise || t >= at + fall) return 0;
      if (t < at) {float u = (t - (at - rise)) / rise; return u * u;}
      float u = (t - at) / fall; return (1 - u) * (1 - u);
   }

   private static void disc(BufferBuilder b, float x, float y, float z, Vector3f left, Vector3f up, float radius, float r, float g, float bl, float a) {
      int n = 14;
      for (int i = 0; i < n; i++) {
         double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
         float c0 = (float)Math.cos(a0) * radius, s0 = (float)Math.sin(a0) * radius, c1 = (float)Math.cos(a1) * radius, s1 = (float)Math.sin(a1) * radius;
         b.vertex(x, y, z).color(r, g, bl, a).endVertex();
         b.vertex(x + left.x() * c0 + up.x() * s0, y + left.y() * c0 + up.y() * s0, z + left.z() * c0 + up.z() * s0).color(r, g, bl, 0).endVertex();
         b.vertex(x + left.x() * c1 + up.x() * s1, y + left.y() * c1 + up.y() * s1, z + left.z() * c1 + up.z() * s1).color(r, g, bl, 0).endVertex();
      }
   }

   /** Per-frame state shared by the split buffers of one model draw. */
   private static final class Frame {
      final Vec3 cam; final float feet, height, glow, front, t; final boolean erase; final long seed;
      final List<Cell> cells; final FloatList shell = new FloatList();
      Frame(Vec3 cam, float feet, float height, float glow, float front, float t, boolean erase, long seed, List<Cell> cells) {
         this.cam = cam; this.feet = feet; this.height = height; this.glow = glow; this.front = front; this.t = t; this.erase = erase; this.seed = seed; this.cells = cells;
      }
   }

   /** Tiny growable float array (no boxing per vertex). */
   private static final class FloatList {
      private float[] data = new float[4096]; private int size;
      void add(float v) {if (size == data.length) data = java.util.Arrays.copyOf(data, size * 2); data[size++] = v;}
      boolean isEmpty() {return size == 0;}
      int size() {return size;}
      float[] array() {return data;}
   }

   /** Model quads in, model-pixel cells out (lit white, released ones dropped). */
   private static final class Splitter implements VertexConsumer {
      private final VertexConsumer out; private final Frame f;
      private final float[] px = new float[4], py = new float[4], pz = new float[4], cu = new float[4], cv = new float[4];
      private final int[] col = new int[4], ovl = new int[4], lit = new int[4];
      private final float[] nx = new float[4], ny = new float[4], nz = new float[4];
      private int n;
      private double vx, vy, vz; private int r = 255, g = 255, b = 255, a = 255; private float u, v; private int overlay = OverlayTexture.NO_OVERLAY, light;
      private float nnx, nny = 1, nnz;
      Splitter(VertexConsumer out, Frame f) {this.out = out; this.f = f;}
      public VertexConsumer vertex(double x, double y, double z) {vx = x; vy = y; vz = z; return this;}
      public VertexConsumer color(int r, int g, int b, int a) {this.r = r; this.g = g; this.b = b; this.a = a; return this;}
      public VertexConsumer uv(float u, float v) {this.u = u; this.v = v; return this;}
      public VertexConsumer overlayCoords(int u, int v) {overlay = u & 0xFFFF | v << 16; return this;}
      public VertexConsumer uv2(int u, int v) {light = u & 0xFFFF | v << 16; return this;}
      public VertexConsumer normal(float x, float y, float z) {nnx = x; nny = y; nnz = z; return this;}
      public void endVertex() {
         push((float)vx, (float)vy, (float)vz, r << 24 | g << 16 | b << 8 | a, u, v, overlay, light, nnx, nny, nnz);
      }
      @Override public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float texU, float texV,
         int overlayUV, int lightmapUV, float normalX, float normalY, float normalZ) {
         push(x, y, z, (int)(red * 255) << 24 | (int)(green * 255) << 16 | (int)(blue * 255) << 8 | (int)(alpha * 255), texU, texV, overlayUV, lightmapUV, normalX, normalY, normalZ);
      }
      public void defaultColor(int r, int g, int b, int a) {}
      public void unsetDefaultColor() {}

      private void push(float x, float y, float z, int c, float tu, float tv, int o, int l, float ax, float ay, float az) {
         px[n] = x; py[n] = y; pz[n] = z; col[n] = c; cu[n] = tu; cv[n] = tv; ovl[n] = o; lit[n] = l; nx[n] = ax; ny[n] = ay; nz[n] = az;
         if (++n == 4) {n = 0; quad();}
      }

      private void quad() {
         float ea = dist(0, 1), eb = dist(0, 3);
         int na = Mth.clamp(Math.round(ea * 16), 1, 24), nb = Mth.clamp(Math.round(eb * 16), 1, 24);
         float red = (col[0] >>> 24) / 255F, green = (col[0] >>> 16 & 255) / 255F, blue = (col[0] >>> 8 & 255) / 255F, alpha = (col[0] & 255) / 255F;
         float hot = f.glow;
         for (int i = 0; i < na; i++) for (int j = 0; j < nb; j++) {
            float s0 = i / (float)na, s1 = (i + 1) / (float)na, t0 = j / (float)nb, t1 = (j + 1) / (float)nb;
            float sm = (s0 + s1) * .5F, tm = (t0 + t1) * .5F;
            float cx = lerp(px, sm, tm), cy = lerp(py, sm, tm), cz = lerp(pz, sm, tm);
            double wx = cx + f.cam.x, wy = cy + f.cam.y, wz = cz + f.cam.z;
            float noise = ErasureDissolve.noise(Mth.floor(wx * 16), Mth.floor(wy * 16), Mth.floor(wz * 16), f.seed);
            float threshold = ErasureDissolve.threshold((float)(wy - f.feet) / f.height, noise);
            boolean released = f.front > threshold;
            float second = ErasureDissolve.noise(Mth.floor(wz * 16), Mth.floor(wx * 16), Mth.floor(wy * 16), ~f.seed);
            if (f.cells.size() < 6000) f.cells.add(new Cell((float)wx, (float)wy, (float)wz, threshold, second - .5F, noise - .5F, second < .45F));
            if (released) continue;
            float edge = ErasureDissolve.edge(threshold, f.t, f.erase);
            float white = Math.max(hot, edge);
            int o = white > 0 ? OverlayTexture.pack(OverlayTexture.u(white), OverlayTexture.WHITE_OVERLAY_V) : ovl[0];
            float[][] corner = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
            for (float[] k : corner) {
               out.vertex(lerp(px, k[0], k[1]), lerp(py, k[0], k[1]), lerp(pz, k[0], k[1]), red, green, blue, alpha,
                  lerp(cu, k[0], k[1]), lerp(cv, k[0], k[1]), o, lit[0], nx[0], ny[0], nz[0]);
            }
            // additive shell: a soft white aura while lit, white-hot right at the front
            float shellAlpha = .10F * hot + .75F * edge;
            if (shellAlpha > .01F) {
               float lift = .012F + .02F * hot;
               for (float[] k : corner) {
                  f.shell.add(lerp(px, k[0], k[1]) + nx[0] * lift); f.shell.add(lerp(py, k[0], k[1]) + ny[0] * lift); f.shell.add(lerp(pz, k[0], k[1]) + nz[0] * lift);
               }
               f.shell.add(shellAlpha); f.shell.add(1); f.shell.add(edge > 0 ? .96F : .97F); f.shell.add(edge > 0 ? .86F : 1);
            }
         }
      }

      private float dist(int i, int j) {
         float dx = px[j] - px[i], dy = py[j] - py[i], dz = pz[j] - pz[i];
         return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
      /** Bilinear over the quad: s along 0->1, t along 0->3. */
      private static float lerp(float[] q, float s, float t) {
         return (1 - s) * (1 - t) * q[0] + s * (1 - t) * q[1] + s * t * q[2] + (1 - s) * t * q[3];
      }
   }

   private enum Discard implements VertexConsumer {
      INSTANCE;
      public VertexConsumer vertex(double x, double y, double z) {return this;} public VertexConsumer color(int r, int g, int b, int a) {return this;}
      public VertexConsumer uv(float u, float v) {return this;} public VertexConsumer overlayCoords(int u, int v) {return this;}
      public VertexConsumer uv2(int u, int v) {return this;} public VertexConsumer normal(float x, float y, float z) {return this;}
      public void endVertex() {} public void defaultColor(int r, int g, int b, int a) {} public void unsetDefaultColor() {}
   }
}

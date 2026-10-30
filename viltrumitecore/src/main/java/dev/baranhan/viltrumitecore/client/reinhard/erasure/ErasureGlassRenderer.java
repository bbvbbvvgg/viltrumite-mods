package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Draws {@link ErasureGlass} over the frame, after the dash_impact chain graded it: the graded
 * frozen frame is copied once per frame and becomes the glass texture; the web grows over it,
 * the cells come loose, burst and tumble into the void as thick lit shards, then fly back and
 * snap home. Screen = plane z = -1 of a 90° camera (so a shard can turn and recede in depth).
 */
public final class ErasureGlassRenderer {
   @Nullable private static TextureTarget capture;
   private static final Vector3f LIGHT = new Vector3f(-.45F, .7F, .55F).normalize();
   private ErasureGlassRenderer() {}

   /** Called from the renderLevel TAIL seam with the scene time (ticks + partial). */
   static void render(ErasureGlass glass, float t) {
      boolean lines = ErasureGlass.linesVisible(t), shards = ErasureGlass.shardsVisible(t);
      if (!lines && !shards) return;
      Minecraft mc = Minecraft.getInstance();
      RenderTarget main = mc.getMainRenderTarget();
      if (main.width <= 0 || main.height <= 0) return;
      if (shards && !copyFrame(main)) shards = false;
      main.bindWrite(true);
      float aspect = main.width / (float)main.height;
      RenderSystem.backupProjectionMatrix();
      RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.PI / 2, aspect, .01F, 200F), VertexSorting.DISTANCE_TO_ORIGIN);
      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      modelView.setIdentity();
      RenderSystem.applyModelViewMatrix();
      RenderSystem.setShaderColor(1, 1, 1, 1);
      RenderSystem.disableCull();
      try {
         if (shards) {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            if (ErasureGlass.voidVisible(t)) drawVoid(aspect, t);
            drawShards(glass, t, aspect);
         }
         if (lines) drawLines(glass, t, main.height);
      } finally {
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.depthFunc(GL11.GL_LEQUAL);
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.restoreProjectionMatrix();
      }
   }

   private static boolean copyFrame(RenderTarget main) {
      try {
         if (capture == null) {
            capture = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            capture.setFilterMode(GL11.GL_LINEAR);
         } else if (capture.width != main.width || capture.height != main.height) {
            capture.resize(main.width, main.height, Minecraft.ON_OSX);
            capture.setFilterMode(GL11.GL_LINEAR);
         }
         GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
         GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, capture.frameBufferId);
         GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, capture.width, capture.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
         GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
         return true;
      } catch (RuntimeException error) {
         com.mojang.logging.LogUtils.getLogger().warn("World Erasure glass capture failed; cracks only", error);
         release();
         return false;
      }
   }

   public static void release() {
      if (capture != null) {RenderSystem.recordRenderCall(capture::destroyBuffers); capture = null;}
   }

   private static void drawVoid(float aspect, float t) {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.disableBlend();
      RenderSystem.depthMask(false);
      RenderSystem.disableDepthTest();
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      float z = -50, w = aspect * 50, h = 50;
      // a deep, faintly blue centre falling off to black
      int rings = 24;
      for (int i = 0; i < rings; i++) {
         double a0 = Math.PI * 2 * i / rings, a1 = Math.PI * 2 * (i + 1) / rings;
         float r = 1.6F;
         b.vertex(0, 0, z).color(.035F, .04F, .07F, 1).endVertex();
         b.vertex((float)Math.cos(a0) * w * r, (float)Math.sin(a0) * h * r, z).color(0, 0, .004F, 1).endVertex();
         b.vertex((float)Math.cos(a1) * w * r, (float)Math.sin(a1) * h * r, z).color(0, 0, .004F, 1).endVertex();
      }
      BufferUploader.drawWithShader(b.end());
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(true);
   }

   private record Placed(ErasureGlass.Shard shard, ErasureGlass.Pose pose, Quaternionf rotation, float depth) {}

   private static void drawShards(ErasureGlass glass, float t, float aspect) {
      List<Placed> placed = new ArrayList<>();
      for (ErasureGlass.Shard s : glass.shards()) {
         ErasureGlass.Pose p = ErasureGlass.pose(s, t);
         if (p.alpha() <= .01F) continue;
         Quaternionf q = new Quaternionf().fromAxisAngleRad(s.ax(), s.ay(), s.az(), p.angle());
         placed.add(new Placed(s, p, q, -1 + p.dz()));
      }
      placed.sort(Comparator.comparingDouble(Placed::depth));
      // dim the debris while the target is alone in the void, light it again for the return
      float dim = voidDim(t);
      RenderSystem.enableDepthTest();
      RenderSystem.depthFunc(GL11.GL_LEQUAL);
      RenderSystem.depthMask(true);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      // edges (thickness): steel glass walls
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      Vector3f n = new Vector3f(), va = new Vector3f(), vb = new Vector3f();
      for (Placed pl : placed) {
         ErasureGlass.Shard s = pl.shard();
         if (pl.pose().dz() > -.002F && pl.pose().angle() == 0) continue;
         n.set(0, 0, 1).rotate(pl.rotation());
         float h = s.thickness();
         float shade = (.35F + .65F * Math.abs(n.dot(LIGHT))) * dim;
         float a = pl.pose().alpha();
         float[] poly = s.poly();
         int count = poly.length / 2;
         for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            place(va, s, pl, poly[i * 2], poly[i * 2 + 1]);
            place(vb, s, pl, poly[j * 2], poly[j * 2 + 1]);
            float ex = va.x - n.x * h, ey = va.y - n.y * h, ez = va.z - n.z * h;
            float fx = vb.x - n.x * h, fy = vb.y - n.y * h, fz = vb.z - n.z * h;
            float r = .5F * shade, g = .66F * shade, bl = .82F * shade;
            b.vertex(va.x, va.y, va.z).color(r, g, bl, a).endVertex();
            b.vertex(vb.x, vb.y, vb.z).color(r, g, bl, a).endVertex();
            b.vertex(fx, fy, fz).color(r * .6F, g * .6F, bl * .6F, a).endVertex();
            b.vertex(va.x, va.y, va.z).color(r, g, bl, a).endVertex();
            b.vertex(fx, fy, fz).color(r * .6F, g * .6F, bl * .6F, a).endVertex();
            b.vertex(ex, ey, ez).color(r * .6F, g * .6F, bl * .6F, a).endVertex();
         }
      }
      BufferUploader.drawWithShader(b.end());
      // faces: the frozen frame itself
      RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
      RenderSystem.setShaderTexture(0, capture.getColorTextureId());
      b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
      Vector3f c = new Vector3f();
      for (Placed pl : placed) {
         ErasureGlass.Shard s = pl.shard();
         n.set(0, 0, 1).rotate(pl.rotation());
         place(c, s, pl, s.cx(), s.cy());
         Vector3f toCamera = new Vector3f(c).negate().normalize();
         float facing = n.dot(toCamera);
         float shade = (facing >= 0 ? .62F + .38F * facing : .45F + .25F * -facing) * (.8F + .2F * Math.abs(n.dot(LIGHT)));
         if (pl.pose().dz() > -.002F && pl.pose().angle() == 0 || t < ReinhardRules.ERASE_SHATTER_START) shade = 1;
         shade *= dim;
         float a = pl.pose().alpha();
         float[] poly = s.poly();
         int count = poly.length / 2;
         float cu = u(s.cx(), aspect), cv = v(s.cy());
         for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            place(va, s, pl, poly[i * 2], poly[i * 2 + 1]);
            place(vb, s, pl, poly[j * 2], poly[j * 2 + 1]);
            b.vertex(c.x, c.y, c.z).uv(cu, cv).color(shade, shade, shade, a).endVertex();
            b.vertex(va.x, va.y, va.z).uv(u(poly[i * 2], aspect), v(poly[i * 2 + 1])).color(shade, shade, shade, a).endVertex();
            b.vertex(vb.x, vb.y, vb.z).uv(u(poly[j * 2], aspect), v(poly[j * 2 + 1])).color(shade, shade, shade, a).endVertex();
         }
      }
      BufferUploader.drawWithShader(b.end());
      // light: glints on turning faces, cold rims on every edge, the snap flash on return
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
      RenderSystem.depthMask(false);
      RenderSystem.depthFunc(GL11.GL_LEQUAL);
      b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      Vector3f ia = new Vector3f(), ib = new Vector3f();
      boolean loose = t >= ReinhardRules.ERASE_SHATTER_START;
      for (Placed pl : placed) {
         ErasureGlass.Shard s = pl.shard();
         n.set(0, 0, 1).rotate(pl.rotation());
         place(c, s, pl, s.cx(), s.cy());
         Vector3f toCamera = new Vector3f(c).negate().normalize();
         Vector3f half = new Vector3f(LIGHT).add(toCamera).normalize();
         float spec = (float)Math.pow(Math.max(0, Math.abs(n.dot(half))), 36) * .55F * pl.pose().alpha() * dim;
         float rim = (loose ? .22F * dim * pl.pose().alpha() : 0) + pl.pose().rim() * .9F;
         float[] poly = s.poly();
         int count = poly.length / 2;
         if (spec > .01F) for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            place(va, s, pl, poly[i * 2], poly[i * 2 + 1]);
            place(vb, s, pl, poly[j * 2], poly[j * 2 + 1]);
            b.vertex(c.x, c.y, c.z).color(1, 1, 1, spec).endVertex();
            b.vertex(va.x, va.y, va.z).color(.9F, .95F, 1, spec * .4F).endVertex();
            b.vertex(vb.x, vb.y, vb.z).color(.9F, .95F, 1, spec * .4F).endVertex();
         }
         if (rim > .01F) {
            float inset = Math.min(.35F, .006F / Math.max(.02F, (float)Math.sqrt(s.area())));
            for (int i = 0; i < count; i++) {
               int j = (i + 1) % count;
               float ax = poly[i * 2], ay = poly[i * 2 + 1], bx = poly[j * 2], by = poly[j * 2 + 1];
               place(va, s, pl, ax, ay); place(vb, s, pl, bx, by);
               place(ia, s, pl, ax + (s.cx() - ax) * inset, ay + (s.cy() - ay) * inset);
               place(ib, s, pl, bx + (s.cx() - bx) * inset, by + (s.cy() - by) * inset);
               b.vertex(va.x, va.y, va.z).color(.8F, .9F, 1, rim).endVertex();
               b.vertex(vb.x, vb.y, vb.z).color(.8F, .9F, 1, rim).endVertex();
               b.vertex(ib.x, ib.y, ib.z).color(.6F, .75F, 1, 0).endVertex();
               b.vertex(va.x, va.y, va.z).color(.8F, .9F, 1, rim).endVertex();
               b.vertex(ib.x, ib.y, ib.z).color(.6F, .75F, 1, 0).endVertex();
               b.vertex(ia.x, ia.y, ia.z).color(.6F, .75F, 1, 0).endVertex();
            }
         }
      }
      BufferUploader.drawWithShader(b.end());
      RenderSystem.depthMask(true);
      RenderSystem.defaultBlendFunc();
   }

   /** The debris dims while the target is alone in the void, and lights up for the return. */
   static float voidDim(float t) {
      float start = ReinhardRules.ERASE_GLOW_START + 6;
      float back = ReinhardRules.ERASE_DARK_END;
      if (t < start) return 1;
      if (t < back) return 1 - .7F * ErasureGlass.smooth((t - start) / 14);
      return .3F + .7F * ErasureGlass.smooth((t - back) / 10);
   }

   /** Shard-local plane point (px,py) placed by the pose: rotate about the centre, then move. */
   private static void place(Vector3f out, ErasureGlass.Shard s, Placed pl, float px, float py) {
      out.set(px - s.cx(), py - s.cy(), 0).rotate(pl.rotation());
      out.add(s.cx() + pl.pose().dx(), s.cy() + pl.pose().dy(), -1 + pl.pose().dz());
   }

   private static float u(float x, float aspect) {return (x / aspect + 1) * .5F;}
   private static float v(float y) {return (y + 1) * .5F;}

   private static void drawLines(ErasureGlass glass, float t, int screenHeight) {
      float c = ErasureGlass.crackPhase(t), close = ErasureGlass.closing(t);
      float flicker = .5F + .5F * (float)Math.sin(t * 2.7F) * (float)Math.sin(t * 1.3F + 1.1F);
      float glow = ErasureGlass.lineGlow(t, flicker);
      if (glow <= .01F) return;
      float px = 2F / Math.max(1, screenHeight);
      RenderSystem.disableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      float z = -1;
      for (ErasureGlass.Segment s : glass.segments()) {
         float f = ErasureGlass.grown(s, c, close);
         if (f <= 0) continue;
         float x1 = s.x0() + (s.x1() - s.x0()) * f, y1 = s.y0() + (s.y1() - s.y0()) * f;
         float dx = x1 - s.x0(), dy = y1 - s.y0(), len = (float)Math.hypot(dx, dy);
         if (len < 1e-5F) continue;
         float nx = -dy / len, ny = dx / len;
         float kind = s.kind() == ErasureGlass.Kind.RAY ? 1 : s.kind() == ErasureGlass.Kind.RING ? .85F : .65F;
         float halo = s.width() * 3.2F * px, core = Math.max(.6F, s.width() * .55F) * px;
         quad(b, s.x0(), s.y0(), x1, y1, nx, ny, halo, z, .45F, .65F, 1, .16F * glow * kind);
         quad(b, s.x0(), s.y0(), x1, y1, nx, ny, core * 1.8F, z, .75F, .85F, 1, .35F * glow * kind);
         quad(b, s.x0(), s.y0(), x1, y1, nx, ny, core, z, 1, .98F, .96F, Math.min(1, .95F * glow * kind));
         if (f < 1 && close >= 1) {
            // the running tip of a growing crack
            float r = s.width() * 2.4F * px;
            quad(b, x1 - dx / len * r, y1 - dy / len * r, x1 + dx / len * r, y1 + dy / len * r, nx, ny, r, z, 1, 1, 1, .55F * glow);
         }
      }
      BufferUploader.drawWithShader(b.end());
      RenderSystem.defaultBlendFunc();
      RenderSystem.depthMask(true);
      RenderSystem.enableDepthTest();
   }

   /** A soft line: full alpha on the centre line, zero at both edges. */
   private static void quad(BufferBuilder b, float x0, float y0, float x1, float y1, float nx, float ny, float w, float z,
      float r, float g, float bl, float a) {
      float hx = nx * w * .5F, hy = ny * w * .5F;
      for (int side = -1; side <= 1; side += 2) {
         float ox = hx * side, oy = hy * side;
         b.vertex(x0, y0, z).color(r, g, bl, a).endVertex();
         b.vertex(x1, y1, z).color(r, g, bl, a).endVertex();
         b.vertex(x1 + ox, y1 + oy, z).color(r, g, bl, 0).endVertex();
         b.vertex(x0, y0, z).color(r, g, bl, a).endVertex();
         b.vertex(x1 + ox, y1 + oy, z).color(r, g, bl, 0).endVertex();
         b.vertex(x0 + ox, y0 + oy, z).color(r, g, bl, 0).endVertex();
      }
   }
}

package dev.baranhan.viltrumitecore.client.thanos;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.Frame;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.V;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * What the snap scene draws into the level frame at {@code AFTER_LEVEL} (render thread): the
 * universe dissolve pass ({@link ThanosDissolve}; a plain void fade when a shader pack owns the
 * frame), then the figures that hang in the void - Thanos (posed by the snap clips, burnt away in
 * gold when the restore starts) and Od Lagna (condensing out of white-gold light, speaking,
 * dissolving into the golden front) - and the loose bits: fragments of the world at the dissolve
 * front in their own map colours, motes of light around Od Lagna and in the restore. At most
 * {@link #MAX_BITS} bits live at once; the world itself is never changed.
 */
final class ThanosSceneDraw {
   static final int MAX_BITS = 1200;
   static final int HOT = 0xFF9E48;
   static final int GOLD = 0xFFD27A;
   static final int WHITE_GOLD = 0xFFF1C8;
   /** Ticks of Thanos's golden burn-away after the restore starts. */
   static final float BURN = 40.0F;
   private static final List<Bit> BITS = new ArrayList<>();
   private static RandomSource random = RandomSource.create();
   private static float reach = 160.0F;
   private static long seed;

   /** A loose bit: a fragment of the world (its map colour) or a mote of light (additive). */
   static final class Bit {
      double x;
      double y;
      double z;
      double xo;
      double yo;
      double zo;
      double vx;
      double vy;
      double vz;
      float size;
      int rgb;
      int age;
      int life;
      boolean light;
   }

   private ThanosSceneDraw() {
   }

   static void start(Frame f, long s, float r) {
      BITS.clear();
      seed = s;
      random = RandomSource.create(s);
      reach = r;
   }

   static void clear() {
      BITS.clear();
      ThanosDissolve.release();
   }

   static int bits() {
      return BITS.size();
   }

   /** Where the world comes apart from: the gauntlet (near) or the player (far). */
   static V dissolveOrigin(Frame f) {
      return f.near() ? f.gauntlet() : f.origin().up(1.0);
   }

   /** Where the world comes back from: Od Lagna. */
   static V restoreOrigin(Frame f) {
      return f.od().up(1.6);
   }

   /** Shader front at t; below 0 before the dissolve (nothing glows ahead of it yet). */
   static float front(float t) {
      if (t < ThanosSceneTimeline.DISSOLVE_START) {
         return -0.2F;
      }

      return ThanosSceneTimeline.dissolve(Math.min(t, ThanosSceneTimeline.SPEECH_END - 0.01F)) * 1.14F - 0.04F;
   }

   /** Shader restore front at t (0 = not yet; a sliver above 0 holds the void). */
   static float restore(float t) {
      if (t < ThanosSceneTimeline.SPEECH_END) {
         return 0.0F;
      }

      return Math.max(1.0E-4F, ThanosSceneTimeline.restore(t) * 1.14F);
   }

   /** Blocks from the origin at which a front value stands (the shader's threshold, mean noise). */
   static double radius(float front, float reach) {
      return Math.max(0.0, (front - 0.08) / 0.84 * reach);
   }

   static ThanosDissolve.Params params(Frame f, float t) {
      float freeze = ThanosSceneTimeline.smooth(t / 10.0F) * (1.0F - ThanosSceneTimeline.restore(t));
      float shockRadius = 0.0F;
      float shockStrength = 0.0F;
      float s = ThanosSceneTimeline.SNAP;
      if (t >= s && t < s + 50.0F) {
         shockRadius = (t - s) * 1.6F;
         shockStrength = (float)Math.pow(1.0F - (t - s) / 50.0F, 1.5);
      }

      return new ThanosDissolve.Params(ThanosSceneClient.vec(dissolveOrigin(f)), ThanosSceneClient.vec(restoreOrigin(f)), front(t), restore(t), reach,
         ThanosSceneTimeline.light(t), freeze, shockRadius, shockStrength, ThanosSceneTimeline.gold(t), t);
   }

   // ---- tick: the loose bits ----------------------------------------------------------------

   static void tick(Minecraft mc, Frame f, float t0, float t) {
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      Iterator<Bit> it = BITS.iterator();
      while (it.hasNext()) {
         Bit b = it.next();
         b.xo = b.x;
         b.yo = b.y;
         b.zo = b.z;
         b.x += b.vx;
         b.y += b.vy;
         b.z += b.vz;
         if (b.light) {
            b.vx *= 0.96;
            b.vz *= 0.96;
            b.vy = b.vy * 0.96 + 0.0015;
         } else {
            b.vx *= 0.985;
            b.vz *= 0.985;
            b.vy = b.vy * 0.985 + 0.0022;
         }

         if (++b.age >= b.life) {
            it.remove();
         }
      }

      if (t >= ThanosSceneTimeline.DISSOLVE_START && t < ThanosSceneTimeline.DISSOLVE_END + 10) {
         spawnFragments(level, f, t, 30);
      }

      if (t >= ThanosSceneTimeline.VOID_END - 10 && t < ThanosSceneTimeline.ARRIVAL_END + 10) {
         spawnGathering(f, 10);
      }

      if (t >= ThanosSceneTimeline.SPEECH_END && t < ThanosSceneTimeline.RESTORE_END - 20) {
         spawnRestore(level, f, t, 18);
         if (f.near() && t < ThanosSceneTimeline.THANOS_GONE + BURN) {
            spawnBurn(f, t, 10);
         }
      }
   }

   private static boolean room() {
      return BITS.size() < MAX_BITS;
   }

   /** Fragments of the world where the dissolve front passes this tick (surface points, map colours). */
   private static void spawnFragments(ClientLevel level, Frame f, float t, int tries) {
      V o = dissolveOrigin(f);
      double r0 = radius(front(t), reach);
      for (int i = 0; i < tries && room(); i++) {
         double r = r0 + (random.nextDouble() - 0.5) * 5.0;
         if (r < 0.8) {
            continue;
         }

         double a = random.nextDouble() * Math.PI * 2.0;
         int x = Mth.floor(o.x() + Math.cos(a) * r);
         int z = Mth.floor(o.z() + Math.sin(a) * r);
         if (!level.hasChunk(x >> 4, z >> 4)) {
            continue;
         }

         int y;
         boolean top = random.nextFloat() < 0.7F;
         if (top) {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
         } else {
            y = Mth.floor(o.y() + (random.nextDouble() - 0.35) * Math.min(r, 20.0));
         }

         BlockPos pos = new BlockPos(x, y, z);
         BlockState state = level.getBlockState(pos);
         if (state.isAir()) {
            continue;
         }

         MapColor color = state.getMapColor(level, pos);
         if (color == null || color.col == 0) {
            continue;
         }

         Bit b = new Bit();
         b.x = x + random.nextDouble();
         b.y = top ? y + 1.02 : y + random.nextDouble();
         b.z = z + random.nextDouble();
         double dx = b.x - o.x();
         double dz = b.z - o.z();
         double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
         double out = 0.015 + random.nextDouble() * 0.035;
         b.vx = dx / len * out + (random.nextDouble() - 0.5) * 0.02;
         b.vz = dz / len * out + (random.nextDouble() - 0.5) * 0.02;
         b.vy = 0.03 + random.nextDouble() * 0.06;
         b.size = 0.05F + random.nextFloat() * 0.09F;
         b.rgb = shade(color.col, 0.8F + 0.35F * random.nextFloat());
         b.life = 30 + random.nextInt(40);
         b.light = false;
         finish(b);
      }
   }

   /** White-gold light gathering into Od Lagna while he forms. */
   private static void spawnGathering(Frame f, int count) {
      V od = f.od();
      for (int i = 0; i < count && room(); i++) {
         double a = random.nextDouble() * Math.PI * 2.0;
         double r = 2.5 + random.nextDouble() * 4.0;
         double h = random.nextDouble() * ThanosSceneShots.OD_HEIGHT * 1.1;
         Bit b = new Bit();
         b.x = od.x() + Math.cos(a) * r;
         b.y = od.y() + h;
         b.z = od.z() + Math.sin(a) * r;
         int life = 18 + random.nextInt(14);
         // reach the body (a column around his axis) at the end of the life
         b.vx = (od.x() + Math.cos(a) * 0.3 - b.x) / life;
         b.vz = (od.z() + Math.sin(a) * 0.3 - b.z) / life;
         b.vy = (od.y() + ThanosSceneShots.OD_HEIGHT * 0.55 - b.y) / life * 0.4;
         b.size = 0.035F + random.nextFloat() * 0.05F;
         b.rgb = random.nextFloat() < 0.6F ? WHITE_GOLD : GOLD;
         b.life = life;
         b.light = true;
         finish(b);
      }
   }

   /** Gold motes rising where the restore front brings the world back. */
   private static void spawnRestore(ClientLevel level, Frame f, float t, int tries) {
      V o = restoreOrigin(f);
      double r0 = radius(restore(t), reach);
      for (int i = 0; i < tries && room(); i++) {
         double r = Math.max(0.5, r0 + (random.nextDouble() - 0.5) * 4.0);
         double a = random.nextDouble() * Math.PI * 2.0;
         int x = Mth.floor(o.x() + Math.cos(a) * r);
         int z = Mth.floor(o.z() + Math.sin(a) * r);
         double y = level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : o.y();
         if (Math.abs(y - o.y()) > 40.0) {
            y = o.y();
         }

         Bit b = new Bit();
         b.x = x + random.nextDouble();
         b.y = y + random.nextDouble() * 1.5;
         b.z = z + random.nextDouble();
         b.vx = (random.nextDouble() - 0.5) * 0.02;
         b.vz = (random.nextDouble() - 0.5) * 0.02;
         b.vy = 0.02 + random.nextDouble() * 0.05;
         b.size = 0.04F + random.nextFloat() * 0.07F;
         b.rgb = random.nextFloat() < 0.5F ? GOLD : WHITE_GOLD;
         b.life = 20 + random.nextInt(30);
         b.light = true;
         finish(b);
      }
   }

   /** Thanos burning away: gold sparks off his body as the front runs down it. */
   private static void spawnBurn(Frame f, float t, int count) {
      float k = Mth.clamp((t - ThanosSceneTimeline.THANOS_GONE) / BURN, 0.0F, 1.0F);
      if (t < ThanosSceneTimeline.THANOS_GONE) {
         return;
      }

      V o = f.origin();
      double h = 3.8 * (1.0 - k);
      for (int i = 0; i < count && room(); i++) {
         double a = random.nextDouble() * Math.PI * 2.0;
         double r = random.nextDouble() * 0.9;
         Bit b = new Bit();
         b.x = o.x() + Math.cos(a) * r;
         b.y = o.y() + Math.max(0.0, h + (random.nextDouble() - 0.5) * 0.6);
         b.z = o.z() + Math.sin(a) * r;
         b.vx = Math.cos(a) * 0.02 + (random.nextDouble() - 0.5) * 0.02;
         b.vz = Math.sin(a) * 0.02 + (random.nextDouble() - 0.5) * 0.02;
         b.vy = 0.04 + random.nextDouble() * 0.05;
         b.size = 0.04F + random.nextFloat() * 0.06F;
         b.rgb = random.nextFloat() < 0.5F ? GOLD : HOT;
         b.life = 20 + random.nextInt(20);
         b.light = true;
         finish(b);
      }
   }

   private static void finish(Bit b) {
      b.xo = b.x;
      b.yo = b.y;
      b.zo = b.z;
      BITS.add(b);
   }

   static int shade(int rgb, float k) {
      int r = Mth.clamp(Math.round((rgb >> 16 & 255) * k), 0, 255);
      int g = Mth.clamp(Math.round((rgb >> 8 & 255) * k), 0, 255);
      int b = Mth.clamp(Math.round((rgb & 255) * k), 0, 255);
      return r << 16 | g << 8 | b;
   }

   // ---- render ------------------------------------------------------------------------------

   static void render(RenderLevelStageEvent event, Frame f, float t) {
      Minecraft mc = Minecraft.getInstance();
      if (t < 0.0F || mc.level == null) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      float partial = event.getPartialTick();
      if (!ThanosDissolve.render(event, params(f, t))) {
         fallbackVoid(t);
      }

      PoseStack view = new PoseStack();
      PixelVfx.levelView(view, event);
      Matrix3f toWorld = new Matrix3f(view.last().normal()).invert();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      Lighting.setupLevel(new Matrix4f(view.last().pose()));
      try {
         if (f.near()) {
            drawThanos(mc, view, buffers, f, t, cam, toWorld);
         }

         drawOdLagna(view, buffers, f, t, cam, toWorld);
         buffers.endBatch();
      } finally {
         RenderSystem.enableCull();
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
      }

      drawBits(event, camera, cam, f, t, partial);
   }

   /** Shader pack or no program: the void as a full-screen fade (depth cleared once it is whole). */
   private static void fallbackVoid(float t) {
      float d = t < ThanosSceneTimeline.SPEECH_END ? ThanosSceneTimeline.dissolve(t) : 1.0F - ThanosSceneTimeline.restore(t);
      float gold = ThanosSceneTimeline.gold(t);
      if (d <= 0.001F && gold <= 0.001F) {
         return;
      }

      RenderSystem.backupProjectionMatrix();
      PoseStack mv = RenderSystem.getModelViewStack();
      mv.pushPose();
      mv.setIdentity();
      RenderSystem.applyModelViewMatrix();
      RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      try {
         BufferBuilder buf = Tesselator.getInstance().getBuilder();
         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         int a = Mth.clamp(Math.round(d * 255.0F), 0, 255);
         quadNdc(buf, 5, 3, 9, a);
         Tesselator.getInstance().end();
         if (gold > 0.001F) {
            RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            quadNdc(buf, 255, 210, 122, Math.round(gold * 70.0F));
            Tesselator.getInstance().end();
         }
      } finally {
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         RenderSystem.enableDepthTest();
         mv.popPose();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.restoreProjectionMatrix();
      }

      if (d >= 0.98F) {
         RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
      }
   }

   private static void quadNdc(BufferBuilder buf, int r, int g, int b, int a) {
      buf.vertex(-1.0, -1.0, 0.0).color(r, g, b, a).endVertex();
      buf.vertex(1.0, -1.0, 0.0).color(r, g, b, a).endVertex();
      buf.vertex(1.0, 1.0, 0.0).color(r, g, b, a).endVertex();
      buf.vertex(-1.0, 1.0, 0.0).color(r, g, b, a).endVertex();
   }

   /** Body yaw whose forward is the flat direction d. */
   static float yawOf(V d) {
      return (float)Math.toDegrees(Math.atan2(-d.x(), d.z()));
   }

   /** Light of Thanos: the world's at his place while it stands, his stones' glow in the void. */
   private static int sceneLight(Minecraft mc, V o, float t) {
      int world = LevelRenderer.getLightColor(mc.level, BlockPos.containing(o.x(), o.y() + 2.5, o.z()));
      float v = ThanosSceneTimeline.smooth((t - ThanosSceneTimeline.DISSOLVE_START - 10.0F) / 60.0F) * (1.0F - ThanosSceneTimeline.restore(t));
      int block = Math.max(LightTexture.block(world), Math.round(13.0F * v));
      int sky = Math.round(LightTexture.sky(world) * (1.0F - v));
      return LightTexture.pack(block, sky);
   }

   private static void drawThanos(Minecraft mc, PoseStack view, MultiBufferSource.BufferSource buffers, Frame f, float t, Vec3 cam, Matrix3f toWorld) {
      float gone = ThanosSceneTimeline.THANOS_GONE;
      if (t >= gone + BURN) {
         return;
      }

      BakedGeoModel model = AnimCache.model(ThanosView.GEO);
      if (model == null) {
         return;
      }

      ThanosRenderer.Look look = ThanosRenderer.poseSnap(model, t);
      V o = f.origin();
      view.pushPose();
      view.translate(o.x() - cam.x, o.y() - cam.y, o.z() - cam.z);
      view.mulPose(Axis.YP.rotationDegrees(180.0F - yawOf(f.fwd())));
      try {
         if (t < gone) {
            ThanosRenderer.draw(model, view, buffers, sceneLight(mc, o, t), OverlayTexture.NO_OVERLAY, 1.0F, look, true);
         } else {
            float k = ThanosSceneTimeline.smooth((t - gone) / BURN);
            float feet = (float)o.y();
            ThanosCells.Rule rule = (wx, wy, wz, noise) -> 0.6F * (1.0F - Mth.clamp((float)(wy - feet) / 3.8F, 0.0F, 1.0F)) + 0.4F * noise;
            ThanosRenderer.drawCells(model, view, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, look, toWorld, cam, rule, k * 1.12F, 0.09F,
               GOLD, 0.25F + 0.5F * k, 0.0F, false, seed, null);
         }
      } finally {
         view.popPose();
         model.resetBones();
      }
   }

   private static void drawOdLagna(PoseStack view, MultiBufferSource.BufferSource buffers, Frame f, float t, Vec3 cam, Matrix3f toWorld) {
      float presence = ThanosSceneTimeline.odLagna(t);
      if (presence <= 0.001F) {
         return;
      }

      BakedGeoModel model = ThanosOdLagna.model();
      if (model == null) {
         return;
      }

      List<float[]> saved = ThanosOdLagna.save(model);
      V od = f.od();
      float scale = (float)ThanosSceneShots.OD_SCALE;
      float feet = (float)(od.y() + ThanosOdLagna.hover(t));
      view.pushPose();
      try {
         ThanosOdLagna.pose(model, t);
         view.translate(od.x() - cam.x, feet - cam.y, od.z() - cam.z);
         view.mulPose(Axis.YP.rotationDegrees(180.0F - yawOf(f.toThanos())));
         view.scale(scale, scale, scale);
         float height = (float)ThanosSceneShots.OD_HEIGHT;
         if (t < ThanosSceneTimeline.ARRIVAL_END) {
            // condensing out of white-gold light, from the feet up
            ThanosCells.Rule rule = (wx, wy, wz, noise) -> 0.55F * Mth.clamp((float)(wy - feet) / height, 0.0F, 1.0F) + 0.45F * noise;
            ThanosRenderer.drawCells(model, ThanosView.OD_LAGNA, view, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, toWorld, cam, rule,
               presence * 1.12F, 0.12F, WHITE_GOLD, 0.25F + 0.6F * (1.0F - presence), 0.0F, true, ~seed, null);
         } else if (presence < 0.999F && t > ThanosSceneTimeline.SPEECH_END) {
            // giving himself to the golden front that brings the world back
            ThanosCells.Rule rule = (wx, wy, wz, noise) -> 0.35F * (1.0F - Mth.clamp((float)(wy - feet) / height, 0.0F, 1.0F)) + 0.65F * noise;
            ThanosRenderer.drawCells(model, ThanosView.OD_LAGNA, view, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, toWorld, cam, rule,
               (1.0F - presence) * 1.12F, 0.1F, GOLD, 0.3F + 0.5F * (1.0F - presence), 0.0F, false, ~seed, null);
         } else {
            float glow = 0.8F + 0.2F * (float)Math.sin(t * 0.08F);
            ThanosOdLagna.draw(model, view, buffers, 1.0F, glow);
         }
      } finally {
         view.popPose();
         ThanosOdLagna.restore(model, saved);
      }
   }

   /** Fragments (alpha blended), then every light: hot rims, motes, Od Lagna's halo. */
   private static void drawBits(RenderLevelStageEvent event, Camera camera, Vec3 cam, Frame f, float t, float partial) {
      float presence = ThanosSceneTimeline.odLagna(t);
      if (BITS.isEmpty() && presence <= 0.001F) {
         return;
      }

      PoseStack mv = RenderSystem.getModelViewStack();
      mv.pushPose();
      mv.setIdentity();
      PixelVfx.levelView(mv, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buf = tess.getBuilder();
      try {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Bit b : BITS) {
            if (!b.light) {
               float life = (b.age + partial) / b.life;
               int alpha = Math.round(255.0F * Mth.clamp(1.0F - life * life, 0.0F, 1.0F));
               float size = b.size * (1.0F - 0.5F * life);
               pixel(buf, cam, camera, b, partial, size, b.rgb, alpha);
            }
         }

         tess.end();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Bit b : BITS) {
            float life = (b.age + partial) / b.life;
            if (b.light) {
               float a = (float)Math.sin(Math.PI * Mth.clamp(life, 0.0F, 1.0F));
               pixel(buf, cam, camera, b, partial, b.size, b.rgb, Math.round(220.0F * a));
            } else if (b.age < 12) {
               // the hot rim of a fresh fragment
               float a = 1.0F - (b.age + partial) / 12.0F;
               pixel(buf, cam, camera, b, partial, b.size * 1.6F, HOT, Math.round(150.0F * a));
            }
         }

         if (presence > 0.001F) {
            V od = f.od();
            Vec3 head = new Vec3(od.x(), od.y() + ThanosOdLagna.hover(t) + ThanosSceneShots.OD_HEIGHT * 0.86, od.z());
            Vec3 chest = new Vec3(od.x(), od.y() + ThanosOdLagna.hover(t) + ThanosSceneShots.OD_HEIGHT * 0.6, od.z());
            Vec3 toCam = cam.subtract(chest).normalize();
            float pulse = 0.85F + 0.15F * (float)Math.sin(t * 0.07F);
            // his light: a wide soft glow behind him and a halo behind the head
            ThanosFxDraw.disc(buf, cam, chest.subtract(toCam.scale(0.6)), toCam, 4.2 * presence, 40, WHITE_GOLD, Math.round(70 * presence * pulse), 0);
            ThanosFxDraw.ring(buf, cam, head.subtract(toCam.scale(0.35)), toCam, 0.95, 0.07, 48, GOLD, Math.round(170 * presence * pulse), t * 0.01);
            ThanosFxDraw.disc(buf, cam, head.subtract(toCam.scale(0.4)), toCam, 1.25, 32, GOLD, Math.round(60 * presence), 0);
         }

         if (f.near() && t >= ThanosSceneTimeline.STONES_START && t < ThanosSceneTimeline.DISSOLVE_START + 20) {
            // the six stones flare around the gauntlet (a soft glow per lit stone)
            int lit = ThanosSceneTimeline.stonesLit(t);
            Vec3 g = ThanosSceneClient.vec(f.stones());
            Vec3 toCam = cam.subtract(g).normalize();
            float k = t > ThanosSceneTimeline.SNAP ? Math.max(0.0F, 1.0F - (t - ThanosSceneTimeline.SNAP) / 35.0F) : 1.0F;
            ThanosStone[] stones = ThanosStone.values();
            for (int i = 0; i < lit && i < stones.length; i++) {
               ThanosFxDraw.disc(buf, cam, g.add(toCam.scale(0.2)), toCam, 0.32 + 0.05 * i, 20, stones[i].rgb, Math.round(40 * k), 0);
            }
         }

         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         mv.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static void pixel(BufferBuilder buf, Vec3 cam, Camera camera, Bit b, float partial, float size, int rgb, int alpha) {
      if (alpha <= 2) {
         return;
      }

      Vec3 at = new Vec3(Mth.lerp(partial, b.xo, b.x), Mth.lerp(partial, b.yo, b.y), Mth.lerp(partial, b.zo, b.z));
      if (at.distanceToSqr(cam) > reach * reach) {
         return;
      }

      PixelVfx.billboardPixel(buf, cam, camera, at, size, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Math.min(255, alpha));
   }
}

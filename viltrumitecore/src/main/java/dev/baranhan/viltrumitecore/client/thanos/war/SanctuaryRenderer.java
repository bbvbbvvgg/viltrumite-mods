package dev.baranhan.viltrumitecore.client.thanos.war;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the Sanctuary II from {@link SanctuaryMesh}: the hull through its own program
 * ({@code shaders/core/sanctuary_hull}: daylight, aerial haze, the glow of guns and fires on the
 * belly) and the lights additively, the level of detail by distance. Beyond the depth range the ship
 * is drawn nearer and smaller by the same factor (same angle on screen), so it never clips while it
 * comes in from the horizon. The shadow is a soft grid on the heightmap under it; the cloud bank it
 * breaks through is a set of soft discs. Render thread only.
 */
public final class SanctuaryRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/thanos_war");
   static final ResourceLocation SHADER = new ResourceLocation("viltrumitecore", "sanctuary_hull");
   static final double LOD0 = 260.0;
   static final double LOD1 = 560.0;
   private static final int SHADOW_CELL = 4;
   private static final int SHADOW_HALF = 26;
   @Nullable
   private static ShaderInstance program;
   @Nullable
   private static SanctuaryMesh mesh;
   private static boolean failed;
   /** Profiling (QA command / logs): last frame's level of detail, quads and nanoseconds. */
   static int lastLod = -1;
   static int lastQuads;
   static long lastNanos;
   static long maxNanos;

   private SanctuaryRenderer() {
   }

   @Nullable
   private static SanctuaryMesh mesh() {
      if (mesh == null && !failed) {
         try {
            long t0 = System.nanoTime();
            mesh = SanctuaryMesh.build();
            LOGGER.info("Sanctuary II mesh built: {} / {} / {} quads in {} ms", mesh.quads[0], mesh.quads[1], mesh.quads[2], (System.nanoTime() - t0) / 1000000L);
         } catch (RuntimeException e) {
            failed = true;
            LOGGER.error("Sanctuary II mesh failed to build; the ship stays invisible", e);
         }
      }

      return mesh;
   }

   static int lod(double distance) {
      return distance < LOD0 ? 0 : distance < LOD1 ? 1 : 2;
   }

   /** The factor that keeps a far ship inside the depth range (1 = drawn where it is). */
   static double fit(double distance, double depthFar) {
      double max = Math.max(80.0, depthFar * 0.92 - 100.0);
      return distance > max ? max / distance : 1.0;
   }

   /** Hull and lights (after the solid terrain: the city in front of it hides it, it hides the sky). */
   static void draw(RenderLevelStageEvent event, ClientLevel level, Vec3 pos, float yaw, float pitch, float roll, float flash) {
      SanctuaryMesh m = mesh();
      if (m == null) {
         return;
      }

      long t0 = System.nanoTime();
      Minecraft mc = Minecraft.getInstance();
      Camera camera = event.getCamera();
      Vec3 rel = pos.subtract(camera.getPosition());
      double d = rel.length();
      double k = fit(d, mc.gameRenderer.getDepthFar());
      int lod = lod(d);
      VertexBuffer hull = m.hull(lod);
      VertexBuffer glow = m.glow(lod);
      if (hull == null) {
         return;
      }

      PoseStack pose = event.getPoseStack();
      pose.pushPose();
      pose.translate(rel.x * k, rel.y * k, rel.z * k);
      pose.mulPose(Axis.YP.rotationDegrees(-yaw));
      pose.mulPose(Axis.XP.rotationDegrees(pitch));
      pose.mulPose(Axis.ZP.rotationDegrees(roll));
      pose.scale((float)k, (float)k, (float)k);
      Matrix4f mv = new Matrix4f(pose.last().pose());
      pose.popPose();
      Matrix4f proj = event.getProjectionMatrix();
      float partial = event.getPartialTick();
      float day = level.getSkyDarken(partial);
      float light = Mth.clamp(0.28F + 0.82F * (day - 0.2F) / 0.8F, 0.28F, 1.1F);
      float rain = level.getRainLevel(partial);
      float[] fog = RenderSystem.getShaderFogColor();
      float haze = (float)Mth.clamp((d - 150.0) / 760.0, 0.0, 0.72) + rain * 0.15F;
      try {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.disableCull();
         RenderSystem.disableBlend();
         RenderSystem.setShaderColor(light * 0.97F, light * 0.95F, light, 1.0F);
         ShaderInstance shader = program;
         if (shader != null) {
            shader.safeGetUniform("Haze").set(fog[0], fog[1], fog[2], haze);
            shader.safeGetUniform("Flash").set(1.0F, 0.55F, 0.25F, Mth.clamp(flash, 0.0F, 1.0F));
         } else {
            shader = GameRenderer.getPositionColorShader();
         }

         if (shader != null) {
            hull.bind();
            hull.drawWithShader(mv, proj, shader);
         }

         ShaderInstance plain = GameRenderer.getPositionColorShader();
         if (glow != null && plain != null) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Mth.clamp(1.0F - haze * 0.7F, 0.2F, 1.0F));
            glow.bind();
            glow.drawWithShader(mv, proj, plain);
         }
      } finally {
         VertexBuffer.unbind();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.depthMask(true);
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         RenderSystem.enableCull();
      }

      lastLod = lod;
      lastQuads = m.quads[lod];
      lastNanos = System.nanoTime() - t0;
      maxNanos = Math.max(maxNanos, lastNanos);
   }

   /** The shadow on the ground and the cloud bank (after the translucent terrain). */
   static void atmosphere(RenderLevelStageEvent event, ClientLevel level, @Nullable Vec3 ship, float yaw, float shadow, List<ThanosWarClient.Puff> puffs) {
      if ((ship == null || shadow <= 0.01F) && puffs.isEmpty()) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      float partial = event.getPartialTick();
      PoseStack view = RenderSystem.getModelViewStack();
      view.pushPose();
      view.setIdentity();
      PixelVfx.levelView(view, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buf = tess.getBuilder();
      try {
         if (ship != null && shadow > 0.01F && Math.abs(ship.x - cam.x) < 300.0 && Math.abs(ship.z - cam.z) < 300.0) {
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.0F, -10.0F);
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            shadowGrid(buf, cam, level, ship, yaw, shadow);
            tess.end();
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
         }

         if (!puffs.isEmpty()) {
            Vec3 cloud = level.getCloudColor(partial);
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (ThanosWarClient.Puff p : puffs) {
               puff(buf, cam, camera, p, cloud, partial);
            }

            tess.end();
         }
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
         view.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static void shadowGrid(BufferBuilder buf, Vec3 cam, ClientLevel level, Vec3 ship, float yaw, float strength) {
      double r = yaw * Mth.DEG_TO_RAD;
      double cos = Math.cos(r);
      double sin = Math.sin(r);
      int cx = Mth.floor(ship.x / SHADOW_CELL);
      int cz = Mth.floor(ship.z / SHADOW_CELL);
      for (int i = -SHADOW_HALF; i < SHADOW_HALF; i++) {
         for (int j = -SHADOW_HALF; j < SHADOW_HALF; j++) {
            double x0 = (cx + i) * SHADOW_CELL;
            double z0 = (cz + j) * SHADOW_CELL;
            double x1 = x0 + SHADOW_CELL;
            double z1 = z0 + SHADOW_CELL;
            float a00 = cover(ship, cos, sin, x0, z0);
            float a10 = cover(ship, cos, sin, x1, z0);
            float a11 = cover(ship, cos, sin, x1, z1);
            float a01 = cover(ship, cos, sin, x0, z1);
            if (a00 + a10 + a11 + a01 < 0.02F) {
               continue;
            }

            int hx = Mth.floor((x0 + x1) * 0.5);
            int hz = Mth.floor((z0 + z1) * 0.5);
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, hx, hz) + 0.04;
            if (y > ship.y - 8.0 || Math.abs(y - cam.y) > 160.0) {
               continue;
            }

            int k = (int)(150.0F * strength);
            buf.vertex(x0 - cam.x, y - cam.y, z0 - cam.z).color(4, 4, 10, (int)(k * a00)).endVertex();
            buf.vertex(x0 - cam.x, y - cam.y, z1 - cam.z).color(4, 4, 10, (int)(k * a01)).endVertex();
            buf.vertex(x1 - cam.x, y - cam.y, z1 - cam.z).color(4, 4, 10, (int)(k * a11)).endVertex();
            buf.vertex(x1 - cam.x, y - cam.y, z0 - cam.z).color(4, 4, 10, (int)(k * a10)).endVertex();
         }
      }
   }

   /** Shadow cover at world (x, z): the ship's footprint in its own frame. */
   static float cover(Vec3 ship, double cos, double sin, double x, double z) {
      double dx = x - ship.x;
      double dz = z - ship.z;
      // inverse of the model rotation (local +z = forward (-sin, cos), local +x = (cos, sin))
      double lx = dx * cos + dz * sin;
      double lz = -dx * sin + dz * cos;
      return SanctuaryMesh.footprint(lx, lz);
   }

   private static void puff(BufferBuilder buf, Vec3 cam, Camera camera, ThanosWarClient.Puff p, Vec3 cloud, float partial) {
      float alpha = p.alpha(partial);
      if (alpha <= 0.01F) {
         return;
      }

      Vec3 c = p.at(partial);
      if (c.distanceToSqr(cam) > 900.0 * 900.0) {
         return;
      }

      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      int r = (int)(Mth.clamp(cloud.x * p.shade, 0.0, 1.0) * 255.0);
      int g = (int)(Mth.clamp(cloud.y * p.shade, 0.0, 1.0) * 255.0);
      int b = (int)(Mth.clamp(cloud.z * p.shade, 0.0, 1.0) * 255.0);
      int inner = (int)(alpha * 255.0F);
      double rad = p.radius;
      int segs = 14;
      double cx = c.x - cam.x;
      double cy = c.y - cam.y;
      double cz = c.z - cam.z;
      for (int i = 0; i < segs; i++) {
         double a0 = Math.PI * 2.0 * i / segs + p.spin;
         double a1 = Math.PI * 2.0 * (i + 1) / segs + p.spin;
         double w0 = rad * (0.85 + 0.15 * Math.sin(a0 * 3.0 + p.spin * 7.0));
         double w1 = rad * (0.85 + 0.15 * Math.sin(a1 * 3.0 + p.spin * 7.0));
         double x0 = cx + (left.x() * Math.cos(a0) + up.x() * Math.sin(a0)) * w0;
         double y0 = cy + (left.y() * Math.cos(a0) + up.y() * Math.sin(a0)) * w0;
         double z0 = cz + (left.z() * Math.cos(a0) + up.z() * Math.sin(a0)) * w0;
         double x1 = cx + (left.x() * Math.cos(a1) + up.x() * Math.sin(a1)) * w1;
         double y1 = cy + (left.y() * Math.cos(a1) + up.y() * Math.sin(a1)) * w1;
         double z1 = cz + (left.z() * Math.cos(a1) + up.z() * Math.sin(a1)) * w1;
         buf.vertex(cx, cy, cz).color(r, g, b, inner).endVertex();
         buf.vertex(cx, cy, cz).color(r, g, b, inner).endVertex();
         buf.vertex(x0, y0, z0).color(r, g, b, 0).endVertex();
         buf.vertex(x1, y1, z1).color(r, g, b, 0).endVertex();
      }
   }

   /** Frees the buffers (log out, resource reload). */
   static void close() {
      if (mesh != null) {
         mesh.close();
         mesh = null;
      }

      failed = false;
   }

   /** Mod bus: the hull program. */
   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Registrar {
      private Registrar() {
      }

      @SubscribeEvent
      public static void onRegisterShaders(RegisterShadersEvent event) {
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_COLOR), loaded -> program = loaded);
         } catch (IOException e) {
            program = null;
            LOGGER.error("Sanctuary II hull shader failed to load; drawing it with the plain colour program", e);
         }
      }
   }
}

package dev.baranhan.viltrumitecore.client.reinhard.saw;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography;
import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawShell;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Saw Sphere VFX (overhaul item 19), camera-relative POSITION_COLOR geometry after particles:
 * <ul>
 * <li>the shell — a cyan translucent dome drawn only where its barrier cell is active (the same
 * mask as the collision), brighter at grazing angles, with rising energy bands, a glowing seam
 * where it meets real blocks, a bright front while it expands, cracks in phase III and outward
 * flying shards at the shatter;</li>
 * <li>the saws — huge horizontal gold discs: a translucent body that reads as a whole cutting
 * area (rotating spokes), a solid rim band, a bright toothed edge and a spin blur; a ground ring
 * telegraphs a low disc; each swing throws a tether from the blade to the disc it drives;</li>
 * <li>the final swing — a horizontal shock ring from Reinhard.</li>
 * </ul>
 * Never drawn in a shader's shadow pass. Low particle settings thin out decorations only; the
 * discs, the shell and the telegraphs stay.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class SawSphereRenderer {
   static final int LAT = 36;
   static final int LON = 144;
   static final int TEETH = 28;
   private static final double MAX_VIEW = 220.0;

   private SawSphereRenderer() {
   }

   /** Per scene, built once from the fixed mask: quad activity, seams, crack paths and shard drift. */
   static final class Mesh {
      final float[] dx = new float[(LAT + 1) * (LON + 1)];
      final float[] dy = new float[(LAT + 1) * (LON + 1)];
      final float[] dz = new float[(LAT + 1) * (LON + 1)];
      /** 0 inactive, 1 active, 2 active seam (borders an inactive quad or the ground). */
      final byte[] quad = new byte[LAT * LON];
      final float[][] cracks;
      final float[] shardSpeed;
      final float[] shardPhase;

      Mesh(SawSphereClient.Scene scene) {
         SawShell shell = scene.shell;
         double r = SawChoreography.RADIUS;
         for (int i = 0; i <= LAT; i++) {
            double phi = Math.PI / 2.0 * i / LAT;
            for (int j = 0; j <= LON; j++) {
               double theta = 2.0 * Math.PI * j / LON;
               int k = i * (LON + 1) + j;
               this.dx[k] = (float) (Math.sin(phi) * Math.cos(theta));
               this.dy[k] = (float) Math.cos(phi);
               this.dz[k] = (float) (Math.sin(phi) * Math.sin(theta));
            }
         }
         boolean[] on = new boolean[LAT * LON];
         for (int i = 0; i < LAT; i++) {
            double phi = Math.PI / 2.0 * (i + 0.5) / LAT;
            for (int j = 0; j < LON; j++) {
               double theta = 2.0 * Math.PI * (j + 0.5) / LON;
               double x = scene.cx + r * Math.sin(phi) * Math.cos(theta), y = scene.y0 + r * Math.cos(phi), z = scene.cz + r * Math.sin(phi) * Math.sin(theta);
               on[i * LON + j] = y > scene.y0 && shell.activeAt(x, y, z);
            }
         }
         for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
               if (!on[i * LON + j]) continue;
               boolean seam = i == LAT - 1 || (i > 0 && !on[(i - 1) * LON + j]) || (i < LAT - 1 && !on[(i + 1) * LON + j])
                  || !on[i * LON + Math.floorMod(j - 1, LON)] || !on[i * LON + (j + 1) % LON];
               this.quad[i * LON + j] = (byte) (seam ? 2 : 1);
            }
         }
         Random random = new Random(scene.seed());
         this.cracks = new float[16][];
         for (int c = 0; c < this.cracks.length; c++) {
            int steps = 18 + random.nextInt(14);
            float[] path = new float[steps * 3];
            double phi = 0.15 + random.nextDouble() * 1.3, theta = random.nextDouble() * Math.PI * 2.0;
            double heading = random.nextDouble() * Math.PI * 2.0;
            for (int s = 0; s < steps; s++) {
               path[s * 3] = (float) (Math.sin(phi) * Math.cos(theta));
               path[s * 3 + 1] = (float) Math.cos(phi);
               path[s * 3 + 2] = (float) (Math.sin(phi) * Math.sin(theta));
               heading += (random.nextDouble() - 0.5) * 1.1;
               double step = 0.055;
               phi = Mth.clamp(phi + Math.cos(heading) * step, 0.05, Math.PI / 2.0 - 0.02);
               theta += Math.sin(heading) * step / Math.max(0.2, Math.sin(phi));
            }
            this.cracks[c] = path;
         }
         this.shardSpeed = new float[(LAT / 3) * (LON / 3)];
         this.shardPhase = new float[this.shardSpeed.length];
         for (int i = 0; i < this.shardSpeed.length; i++) {
            this.shardSpeed[i] = 0.18F + random.nextFloat() * 0.32F;
            this.shardPhase[i] = random.nextFloat();
         }
      }
   }

   private static Mesh mesh(SawSphereClient.Scene scene) {
      if (scene.renderCache instanceof Mesh mesh) {
         return mesh;
      }
      Mesh mesh = new Mesh(scene);
      scene.renderCache = mesh;
      return mesh;
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SawSphereClient.scenes().isEmpty()
         || dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      Camera camera = event.getCamera();
      Vec3 eye = camera.getPosition();
      float partial = event.getPartialTick();
      boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
      PoseStack stack = RenderSystem.getModelViewStack();
      stack.pushPose();
      stack.setIdentity();
      PixelVfx.rotateCamera(stack, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder b = tess.getBuilder();
      try {
         b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (SawSphereClient.Scene scene : SawSphereClient.scenes()) {
            if (scene.ended || eye.distanceToSqr(scene.cx, scene.y0, scene.cz) > MAX_VIEW * MAX_VIEW) continue;
            double t = scene.age(partial);
            shell(b, eye, scene, mesh(scene), t, false);
            for (int s = 0; s < SawChoreography.COUNT; s++) sawBody(b, eye, scene, s, t, minimal);
         }
         tess.end();

         RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
         b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (SawSphereClient.Scene scene : SawSphereClient.scenes()) {
            if (scene.ended || eye.distanceToSqr(scene.cx, scene.y0, scene.cz) > MAX_VIEW * MAX_VIEW) continue;
            double t = scene.age(partial);
            Mesh mesh = mesh(scene);
            shell(b, eye, scene, mesh, t, true);
            cracks(b, eye, scene, mesh, t);
            for (int s = 0; s < SawChoreography.COUNT; s++) sawGlow(b, eye, scene, s, t, minimal);
            tether(b, eye, mc, scene, t, partial);
            finale(b, eye, scene, t);
         }
         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         stack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static void vertex(BufferBuilder b, Vec3 eye, double x, double y, double z, int r, int g, int bl, int a) {
      b.vertex((float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z)).color(r, g, bl, Mth.clamp(a, 0, 255)).endVertex();
   }

   /** Shell visibility over the scene: full after the expansion, flicker while cracking, gone at the shatter (shards take over). */
   static double shellAlpha(double t) {
      if (t >= SawChoreography.SHATTER) return 0.0;
      double fade = SawChoreography.clamp01((t - SawChoreography.EXPAND_START) / 4.0);
      if (t >= SawChoreography.CRACK_START) fade *= 0.8 + 0.2 * Math.sin(t * 2.7);
      return fade;
   }

   /** The dome: translucent fill (glow = false) or the additive seams, bands and expansion front (glow = true); shards after the shatter. */
   private static void shell(BufferBuilder b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double t, boolean glow) {
      boolean shards = t >= SawChoreography.SHATTER;
      double k = shards ? 1.0 - SawChoreography.clamp01((t - SawChoreography.SHATTER) / (SawChoreography.LENGTH - SawChoreography.SHATTER)) : shellAlpha(t);
      if (k <= 0.004) return;
      double radius = SawChoreography.shellRadius(t);
      double expand = 1.0 - SawChoreography.clamp01((t - SawChoreography.EXPAND_START) / (SawChoreography.EXPAND_END - SawChoreography.EXPAND_START));
      double dt = shards ? t - SawChoreography.SHATTER : 0.0;
      for (int i = 0; i < LAT; i++) {
         double band = 0.5 + 0.5 * Math.sin(i * 0.55 - t * 0.21);
         band = band * band * band * band * band * band;
         for (int j = 0; j < LON; j++) {
            byte state = mesh.quad[i * LON + j];
            if (state == 0) continue;
            int a00 = i * (LON + 1) + j, a01 = a00 + 1, a10 = a00 + LON + 1, a11 = a10 + 1;
            double ox = 0, oy = 0, oz = 0, shrink = 1.0;
            if (shards) {
               int shard = Math.min(mesh.shardSpeed.length - 1, (i / 3) * (LON / 3) + j / 3);
               double v = mesh.shardSpeed[shard];
               int mid = a00;
               ox = mesh.dx[mid] * v * dt;
               oy = mesh.dy[mid] * v * dt - 0.025 * dt * dt;
               oz = mesh.dz[mid] * v * dt;
               shrink = 1.0 - 0.5 * SawChoreography.clamp01(dt / 12.0) * mesh.shardPhase[shard];
            }
            double cxq = (mesh.dx[a00] + mesh.dx[a11]) * 0.5, cyq = (mesh.dy[a00] + mesh.dy[a11]) * 0.5, czq = (mesh.dz[a00] + mesh.dz[a11]) * 0.5;
            int alpha;
            int r, g, bl;
            if (!glow) {
               double px = scene.cx + cxq * radius - eye.x, py = scene.y0 + cyq * radius - eye.y, pz = scene.cz + czq * radius - eye.z;
               double len = Math.sqrt(px * px + py * py + pz * pz) + 1.0E-6;
               double facing = Math.abs((px * cxq + py * cyq + pz * czq) / len);
               double fres = 1.0 - facing;
               alpha = (int) (k * (34 + 120 * fres * fres * fres + 26 * band));
               r = 70;
               g = 185;
               bl = 255;
            } else {
               double seam = state == 2 ? 1.0 : 0.0;
               alpha = (int) (k * (seam * 150 + band * 55 + expand * 170));
               if (shards) alpha = (int) (k * 90);
               if (alpha <= 2) continue;
               r = 150;
               g = 230;
               bl = 255;
            }
            quadOnSphere(b, eye, scene, mesh, radius, a00, a01, a11, a10, ox, oy, oz, shrink, cxq, cyq, czq, r, g, bl, alpha);
         }
      }
   }

   private static void quadOnSphere(BufferBuilder b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double radius, int v0, int v1, int v2, int v3,
      double ox, double oy, double oz, double shrink, double cxq, double cyq, double czq, int r, int g, int bl, int a) {
      int[] vs = {v0, v1, v2, v3};
      for (int v : vs) {
         double x = cxq + (mesh.dx[v] - cxq) * shrink, y = cyq + (mesh.dy[v] - cyq) * shrink, z = czq + (mesh.dz[v] - czq) * shrink;
         vertex(b, eye, scene.cx + x * radius + ox, scene.y0 + y * radius + oy, scene.cz + z * radius + oz, r, g, bl, a);
      }
   }

   /** Phase III cracks: white ribbons running over the intact shell, growing until it shatters. */
   private static void cracks(BufferBuilder b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double t) {
      if (t < SawChoreography.CRACK_START || t >= SawChoreography.SHATTER) return;
      double grow = SawChoreography.clamp01((t - SawChoreography.CRACK_START) / (SawChoreography.SHATTER - SawChoreography.CRACK_START - 2));
      double r = SawChoreography.RADIUS + 0.03;
      for (float[] path : mesh.cracks) {
         int steps = path.length / 3;
         int shown = (int) (steps * grow);
         for (int s = 0; s + 1 < shown; s++) {
            double ax = path[s * 3], ay = path[s * 3 + 1], az = path[s * 3 + 2];
            double bx = path[s * 3 + 3], by = path[s * 3 + 4], bz = path[s * 3 + 5];
            double mx = scene.cx + (ax + bx) * 0.5 * r, my = scene.y0 + (ay + by) * 0.5 * r, mz = scene.cz + (az + bz) * 0.5 * r;
            if (!scene.shell.activeAt(mx, my, mz)) continue;
            double tx = bx - ax, ty = by - ay, tz = bz - az;
            double wx = ty * az - tz * ay, wy = tz * ax - tx * az, wz = tx * ay - ty * ax;
            double wl = Math.sqrt(wx * wx + wy * wy + wz * wz) + 1.0E-6;
            double w = 0.07 * (1.0 - (double) s / steps) + 0.02;
            wx = wx / wl * w;
            wy = wy / wl * w;
            wz = wz / wl * w;
            int alpha = (int) (230 * (0.6 + 0.4 * grow));
            vertex(b, eye, scene.cx + ax * r - wx, scene.y0 + ay * r - wy, scene.cz + az * r - wz, 235, 250, 255, alpha);
            vertex(b, eye, scene.cx + ax * r + wx, scene.y0 + ay * r + wy, scene.cz + az * r + wz, 235, 250, 255, alpha);
            vertex(b, eye, scene.cx + bx * r + wx, scene.y0 + by * r + wy, scene.cz + bz * r + wz, 235, 250, 255, alpha);
            vertex(b, eye, scene.cx + bx * r - wx, scene.y0 + by * r - wy, scene.cz + bz * r - wz, 235, 250, 255, alpha);
         }
      }
   }

   /** Translucent body (two faces, rotating spokes), the rim band and a ground telegraph under a low disc. */
   private static void sawBody(BufferBuilder b, Vec3 eye, SawSphereClient.Scene scene, int s, double t, boolean minimal) {
      double alpha = SawChoreography.alpha(s, t);
      if (alpha <= 0.004) return;
      double[] p = new double[3];
      SawChoreography.position(s, t, scene.cx, scene.y0, scene.cz, scene.anchorYaw, p);
      double rs = SawChoreography.SAW_RADIUS, h = SawChoreography.HALF_THICKNESS;
      double spin = SawChoreography.DISC_SPIN * t + s * 1.3;
      int seg = minimal ? 20 : 32;
      double[] rings = {0.0, 0.45, 0.8, 1.0};
      int[] ringAlpha = {28, 52, 86, 120};
      for (int face = -1; face <= 1; face += 2) {
         double y = p[1] + face * h;
         for (int q = 0; q < seg; q++) {
            double a0 = 2.0 * Math.PI * q / seg, a1 = 2.0 * Math.PI * (q + 1) / seg;
            double spoke0 = 0.72 + 0.28 * Math.cos(6.0 * (a0 - spin)), spoke1 = 0.72 + 0.28 * Math.cos(6.0 * (a1 - spin));
            for (int ring = 0; ring + 1 < rings.length; ring++) {
               double ri = rings[ring] * rs, ro = rings[ring + 1] * rs;
               int ai0 = (int) (alpha * ringAlpha[ring] * spoke0), ao0 = (int) (alpha * ringAlpha[ring + 1] * spoke0);
               int ai1 = (int) (alpha * ringAlpha[ring] * spoke1), ao1 = (int) (alpha * ringAlpha[ring + 1] * spoke1);
               vertex(b, eye, p[0] + Math.cos(a0) * ri, y, p[2] + Math.sin(a0) * ri, 255, 196, 84, ai0);
               vertex(b, eye, p[0] + Math.cos(a0) * ro, y, p[2] + Math.sin(a0) * ro, 255, 206, 96, ao0);
               vertex(b, eye, p[0] + Math.cos(a1) * ro, y, p[2] + Math.sin(a1) * ro, 255, 206, 96, ao1);
               vertex(b, eye, p[0] + Math.cos(a1) * ri, y, p[2] + Math.sin(a1) * ri, 255, 196, 84, ai1);
            }
         }
      }
      int band = (int) (alpha * 170);
      for (int q = 0; q < seg; q++) {
         double a0 = 2.0 * Math.PI * q / seg, a1 = 2.0 * Math.PI * (q + 1) / seg;
         vertex(b, eye, p[0] + Math.cos(a0) * rs, p[1] - h, p[2] + Math.sin(a0) * rs, 255, 222, 140, band);
         vertex(b, eye, p[0] + Math.cos(a0) * rs, p[1] + h, p[2] + Math.sin(a0) * rs, 255, 222, 140, band);
         vertex(b, eye, p[0] + Math.cos(a1) * rs, p[1] + h, p[2] + Math.sin(a1) * rs, 255, 222, 140, band);
         vertex(b, eye, p[0] + Math.cos(a1) * rs, p[1] - h, p[2] + Math.sin(a1) * rs, 255, 222, 140, band);
      }
      double above = p[1] - scene.y0;
      if (above < 6.0 && SawChoreography.cutting(s, t)) {
         int ground = (int) (alpha * 90 * (1.0 - above / 6.0));
         ring(b, eye, p[0], scene.y0 + 0.04, p[2], rs - 0.08, rs + 0.08, 40, 255, 120, 60, ground);
      }
   }

   private static void ring(BufferBuilder b, Vec3 eye, double x, double y, double z, double ri, double ro, int seg, int r, int g, int bl, int a) {
      if (a <= 1) return;
      for (int q = 0; q < seg; q++) {
         double a0 = 2.0 * Math.PI * q / seg, a1 = 2.0 * Math.PI * (q + 1) / seg;
         vertex(b, eye, x + Math.cos(a0) * ri, y, z + Math.sin(a0) * ri, r, g, bl, a);
         vertex(b, eye, x + Math.cos(a0) * ro, y, z + Math.sin(a0) * ro, r, g, bl, a);
         vertex(b, eye, x + Math.cos(a1) * ro, y, z + Math.sin(a1) * ro, r, g, bl, a);
         vertex(b, eye, x + Math.cos(a1) * ri, y, z + Math.sin(a1) * ri, r, g, bl, a);
      }
   }

   /** Additive: the toothed edge (top and bottom), a spin blur ring and the hot core. */
   private static void sawGlow(BufferBuilder b, Vec3 eye, SawSphereClient.Scene scene, int s, double t, boolean minimal) {
      double alpha = SawChoreography.alpha(s, t);
      if (alpha <= 0.004) return;
      double[] p = new double[3];
      SawChoreography.position(s, t, scene.cx, scene.y0, scene.cz, scene.anchorYaw, p);
      double rs = SawChoreography.SAW_RADIUS, h = SawChoreography.HALF_THICKNESS;
      double spin = SawChoreography.DISC_SPIN * t + s * 1.3;
      double fresh = 1.0 - SawChoreography.clamp01((t - SawChoreography.materializeTick(s)) / (SawChoreography.FADE * 2.0));
      int teeth = (int) (alpha * (215 + 40 * fresh));
      for (int face = -1; face <= 1; face += 2) {
         double y = p[1] + face * h * 0.7;
         for (int q = 0; q < TEETH; q++) {
            double a0 = spin + 2.0 * Math.PI * q / TEETH, a1 = spin + 2.0 * Math.PI * (q + 1) / TEETH;
            double tip = rs + 0.34;
            vertex(b, eye, p[0] + Math.cos(a0) * (rs - 0.05), y, p[2] + Math.sin(a0) * (rs - 0.05), 255, 236, 170, teeth);
            vertex(b, eye, p[0] + Math.cos(a0) * tip, y, p[2] + Math.sin(a0) * tip, 255, 250, 225, teeth);
            vertex(b, eye, p[0] + Math.cos(a1) * (rs + 0.02), y, p[2] + Math.sin(a1) * (rs + 0.02), 255, 236, 170, teeth);
            vertex(b, eye, p[0] + Math.cos(a1) * (rs - 0.05), y, p[2] + Math.sin(a1) * (rs - 0.05), 255, 236, 170, teeth);
         }
      }
      if (!minimal) ring(b, eye, p[0], p[1], p[2], rs + 0.05, rs + 0.42, 32, 255, 214, 120, (int) (alpha * 46));
      ring(b, eye, p[0], p[1] + h + 0.005, p[2], 0.0, 0.55, 12, 255, 250, 230, (int) (alpha * 150));
   }

   /** Each swing: a fading ribbon from the blade to the disc it launched or drives, and a pulse on the disc. */
   private static void tether(BufferBuilder b, Vec3 eye, Minecraft mc, SawSphereClient.Scene scene, double t, float partial) {
      int saw = scene.tetherSaw();
      double dt = t - scene.tetherTick();
      if (saw < 0 || dt < 0 || dt >= 6.0) return;
      double fade = 1.0 - dt / 6.0;
      Entity caster = mc.level.getEntity(scene.casterId);
      Vec3 base = caster != null ? caster.getPosition(partial) : new Vec3(scene.cx, scene.y0 + SawChoreography.hover(t), scene.cz);
      double yaw = Math.toRadians(SawChoreography.modelYaw(scene.anchorYaw, t));
      Vec3 blade = base.add(-Math.sin(yaw) * 1.3, 1.25, Math.cos(yaw) * 1.3);
      double[] p = new double[3];
      SawChoreography.position(saw, t, scene.cx, scene.y0, scene.cz, scene.anchorYaw, p);
      double dx = p[0] - blade.x, dz = p[2] - blade.z;
      double len = Math.sqrt(dx * dx + dz * dz) + 1.0E-6;
      double wx = -dz / len * 0.14 * fade, wz = dx / len * 0.14 * fade;
      int a = (int) (210 * fade);
      vertex(b, eye, blade.x - wx, blade.y, blade.z - wz, 255, 244, 200, a);
      vertex(b, eye, blade.x + wx, blade.y, blade.z + wz, 255, 244, 200, a);
      vertex(b, eye, p[0] + wx, p[1], p[2] + wz, 255, 226, 150, a / 2);
      vertex(b, eye, p[0] - wx, p[1], p[2] - wz, 255, 226, 150, a / 2);
      double rs = SawChoreography.SAW_RADIUS * (1.0 + 0.25 * dt / 6.0);
      ring(b, eye, p[0], p[1], p[2], rs, rs + 0.18, 36, 255, 240, 190, (int) (150 * fade));
   }

   /** The final swing: a horizontal shock ring from Reinhard out to the shell. */
   private static void finale(BufferBuilder b, Vec3 eye, SawSphereClient.Scene scene, double t) {
      double dt = t - SawChoreography.FINAL_SWING;
      if (dt < 0 || dt > 12) return;
      double u = dt / 12.0;
      double r = 0.8 + (SawChoreography.RADIUS - 1.0) * (1.0 - (1.0 - u) * (1.0 - u));
      double y = scene.y0 + SawChoreography.hover(t) + 1.2;
      ring(b, eye, scene.cx, y, scene.cz, r - 0.5 * (1.0 - u) - 0.1, r, 64, 255, 248, 220, (int) (230 * (1.0 - u)));
      ring(b, eye, scene.cx, y, scene.cz, r * 0.7, r - 0.1, 64, 160, 230, 255, (int) (70 * (1.0 - u)));
   }
}

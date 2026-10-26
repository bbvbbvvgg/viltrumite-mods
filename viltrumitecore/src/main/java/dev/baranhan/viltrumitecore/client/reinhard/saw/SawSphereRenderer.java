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
      /** Energy lattice segments (unit directions, 6 floats each) on active shell cells. */
      final float[] lattice;
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
         this.lattice = buildLattice(scene);
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

   /** Frequency of the geodesic dome whose hexagonal dual is the lattice: cells about 2 blocks wide. */
   static final int GEODESIC = 7;

   /** The Goldberg (hexagon / pentagon) cell edges of a frequency-{@link #GEODESIC} icosphere with a
    * pentagon at the crown: every small triangle contributes centroid -> edge-midpoint segments,
    * and the two halves of each cell edge meet at the shared midpoint. Kept above the ground where
    * both ends and the middle are on active barrier cells (the collision mask). */
   static float[] buildLattice(SawSphereClient.Scene scene) {
      double p = (1 + Math.sqrt(5)) / 2;
      double[][] v = {{-1, p, 0}, {1, p, 0}, {-1, -p, 0}, {1, -p, 0}, {0, -1, p}, {0, 1, p}, {0, -1, -p}, {0, 1, -p}, {p, 0, -1}, {p, 0, 1}, {-p, 0, -1}, {-p, 0, 1}};
      int[][] faces = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
         {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
      double th = Math.atan2(1, p);
      for (double[] q : v) {
         double x = q[0] * Math.cos(th) - q[1] * Math.sin(th), y = q[0] * Math.sin(th) + q[1] * Math.cos(th);
         q[0] = x;
         q[1] = y;
      }
      int f = GEODESIC;
      float[] out = new float[faces.length * f * f * 3 * 6];
      int n = 0;
      double r = SawChoreography.RADIUS;
      for (int[] face : faces) {
         double[] a = v[face[0]], b = v[face[1]], c = v[face[2]];
         for (int i = 0; i < f; i++) {
            for (int j = 0; i + j < f; j++) {
               for (int down = 0; down < 2; down++) {
                  if (down == 1 && i + j >= f - 1) continue;
                  int[][] tri = down == 0 ? new int[][]{{i, j}, {i + 1, j}, {i, j + 1}} : new int[][]{{i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                  double[][] pts = new double[3][];
                  for (int k = 0; k < 3; k++) pts[k] = unit(a[0] + (b[0] - a[0]) * tri[k][0] / f + (c[0] - a[0]) * tri[k][1] / f,
                     a[1] + (b[1] - a[1]) * tri[k][0] / f + (c[1] - a[1]) * tri[k][1] / f, a[2] + (b[2] - a[2]) * tri[k][0] / f + (c[2] - a[2]) * tri[k][1] / f);
                  double[] cen = unit(pts[0][0] + pts[1][0] + pts[2][0], pts[0][1] + pts[1][1] + pts[2][1], pts[0][2] + pts[1][2] + pts[2][2]);
                  for (int k = 0; k < 3; k++) {
                     double[] m = unit(pts[k][0] + pts[(k + 1) % 3][0], pts[k][1] + pts[(k + 1) % 3][1], pts[k][2] + pts[(k + 1) % 3][2]);
                     double[] mid = unit(cen[0] + m[0], cen[1] + m[1], cen[2] + m[2]);
                     if (cen[1] < 0.012 || m[1] < 0.012) continue;
                     if (!on(scene, cen, r) || !on(scene, m, r) || !on(scene, mid, r)) continue;
                     out[n++] = (float) cen[0]; out[n++] = (float) cen[1]; out[n++] = (float) cen[2];
                     out[n++] = (float) m[0]; out[n++] = (float) m[1]; out[n++] = (float) m[2];
                  }
               }
            }
         }
      }
      return java.util.Arrays.copyOf(out, n);
   }

   private static double[] unit(double x, double y, double z) {
      double l = Math.sqrt(x * x + y * y + z * z);
      return new double[]{x / l, y / l, z / l};
   }

   private static boolean on(SawSphereClient.Scene scene, double[] d, double r) {
      return scene.shell.activeAt(scene.cx + d[0] * r, scene.y0 + d[1] * r, scene.cz + d[2] * r);
   }

   static Mesh mesh(SawSphereClient.Scene scene) {
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
      Out out = (x, y, z, r, g, bl, a) -> b.vertex(x, y, z).color(r, g, bl, a).endVertex();
      try {
         b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (SawSphereClient.Scene scene : SawSphereClient.scenes()) {
            if (scene.ended || eye.distanceToSqr(scene.cx, scene.y0, scene.cz) > MAX_VIEW * MAX_VIEW) continue;
            double t = scene.age(partial);
            shell(out, eye, scene, mesh(scene), t, false);
            for (int s = 0; s < SawChoreography.COUNT; s++) sawBody(out, eye, scene, s, t, minimal);
         }
         tess.end();

         RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
         b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (SawSphereClient.Scene scene : SawSphereClient.scenes()) {
            if (scene.ended || eye.distanceToSqr(scene.cx, scene.y0, scene.cz) > MAX_VIEW * MAX_VIEW) continue;
            double t = scene.age(partial);
            Mesh mesh = mesh(scene);
            shell(out, eye, scene, mesh, t, true);
            lattice(out, eye, scene, mesh, t, minimal);
            cracks(out, eye, scene, mesh, t);
            for (int s = 0; s < SawChoreography.COUNT; s++) sawGlow(out, eye, scene, s, t, minimal);
            tether(out, eye, mc, scene, t, partial);
            finale(out, eye, scene, t);
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

   /** One camera-relative POSITION_COLOR vertex (a BufferBuilder in game, a recorder in the CPU preview test). */
   @FunctionalInterface
   interface Out {
      void v(float x, float y, float z, int r, int g, int b, int a);
   }

   private static void vertex(Out b, Vec3 eye, double x, double y, double z, int r, int g, int bl, int a) {
      b.v((float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z), Mth.clamp(r, 0, 255), Mth.clamp(g, 0, 255), Mth.clamp(bl, 0, 255), Mth.clamp(a, 0, 255));
   }

   /** Everything of one scene except the tether (it needs the caster entity): fill pass, then the additive pass. */
   static void drawScene(Out fill, Out glow, Vec3 eye, SawSphereClient.Scene scene, double t, boolean minimal) {
      Mesh mesh = mesh(scene);
      shell(fill, eye, scene, mesh, t, false);
      for (int s = 0; s < SawChoreography.COUNT; s++) sawBody(fill, eye, scene, s, t, minimal);
      shell(glow, eye, scene, mesh, t, true);
      lattice(glow, eye, scene, mesh, t, minimal);
      cracks(glow, eye, scene, mesh, t);
      for (int s = 0; s < SawChoreography.COUNT; s++) sawGlow(glow, eye, scene, s, t, minimal);
      finale(glow, eye, scene, t);
   }

   /** Shell visibility over the scene: full after the expansion, flicker while cracking, gone at the shatter (shards take over). */
   static double shellAlpha(double t) {
      if (t >= SawChoreography.SHATTER) return 0.0;
      double fade = SawChoreography.clamp01((t - SawChoreography.EXPAND_START) / 4.0);
      if (t >= SawChoreography.CRACK_START) fade *= 0.8 + 0.2 * Math.sin(t * 2.7);
      return fade;
   }

   /** Shell colour: deep blue where the dome faces the camera, cyan-white at grazing angles. */
   private static final int[] DEEP = {36, 112, 236}, RIM = {160, 232, 255};

   /** Grazing factor 0 (facing the camera) .. 1 (edge-on) of the unit direction {@code v} at {@code p}. */
   private static double grazing(Vec3 eye, double px, double py, double pz, double nx, double ny, double nz) {
      double vx = px - eye.x, vy = py - eye.y, vz = pz - eye.z;
      double len = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-6;
      return 1.0 - Math.abs((vx * nx + vy * ny + vz * nz) / len);
   }

   /** The dome: per-vertex fresnel fill (glow = false), or the additive mask seams and the
    * expansion flash (glow = true); after the shatter both become triangular shards. */
   private static void shell(Out b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double t, boolean glow) {
      if (t >= SawChoreography.SHATTER) {
         shards(b, eye, scene, mesh, t, glow);
         return;
      }
      double k = shellAlpha(t);
      if (k <= 0.004) return;
      double radius = SawChoreography.shellRadius(t);
      double expand = 1.0 - SawChoreography.clamp01((t - SawChoreography.EXPAND_START) / (SawChoreography.EXPAND_END - SawChoreography.EXPAND_START));
      int[] corner = new int[4];
      for (int i = 0; i < LAT; i++) {
         for (int j = 0; j < LON; j++) {
            byte state = mesh.quad[i * LON + j];
            if (state == 0) continue;
            int a00 = i * (LON + 1) + j, a01 = a00 + 1, a10 = a00 + LON + 1, a11 = a10 + 1;
            corner[0] = a00; corner[1] = a01; corner[2] = a11; corner[3] = a10;
            if (!glow) {
               for (int v : corner) {
                  double nx = mesh.dx[v], ny = mesh.dy[v], nz = mesh.dz[v];
                  double px = scene.cx + nx * radius, py = scene.y0 + ny * radius, pz = scene.cz + nz * radius;
                  double f = grazing(eye, px, py, pz, nx, ny, nz);
                  double rim = f * f * f;
                  double foot = Math.max(0.0, 1.0 - ny / 0.14);
                  int a = (int) (k * (20 + 150 * rim + 40 * foot * foot));
                  double c = Math.min(1.0, f * f + 0.6 * foot * foot);
                  vertex(b, eye, px, py, pz, lerp(DEEP[0], RIM[0], c), lerp(DEEP[1], RIM[1], c), lerp(DEEP[2], RIM[2], c), a);
               }
            } else if (expand > 0.01) {
               // the expansion flash: the growing dome lit from inside, brightest at its rim
               for (int v : corner) {
                  double nx = mesh.dx[v], ny = mesh.dy[v], nz = mesh.dz[v];
                  double px = scene.cx + nx * radius, py = scene.y0 + ny * radius, pz = scene.cz + nz * radius;
                  double f = grazing(eye, px, py, pz, nx, ny, nz);
                  vertex(b, eye, px, py, pz, 170, 235, 255, (int) (k * expand * (40 + 150 * f * f)));
               }
            }
            if (glow && state == 2) seams(b, eye, scene, mesh, radius, i, j, k);
         }
      }
   }

   private static int lerp(int a, int b, double f) {
      return (int) Math.round(a + (b - a) * f);
   }

   /** Glowing seam strips on the sides of quad (i, j) that meet the ground or a hole of the mask:
    * bright on the edge, fading out a little way into the quad. */
   private static void seams(Out b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double radius, int i, int j, double k) {
      int a00 = i * (LON + 1) + j, a01 = a00 + 1, a10 = a00 + LON + 1, a11 = a10 + 1;
      int edge = (int) (k * 190);
      // side: the edge (e0, e1) and the opposite edge (o0, o1) it fades toward
      if (i == LAT - 1 || mesh.quad[(i + 1) * LON + j] == 0) seam(b, eye, scene, mesh, radius, a10, a11, a00, a01, i == LAT - 1 ? 0.8 : 0.45, edge);
      if (i > 0 && mesh.quad[(i - 1) * LON + j] == 0) seam(b, eye, scene, mesh, radius, a00, a01, a10, a11, 0.45, edge);
      if (mesh.quad[i * LON + Math.floorMod(j - 1, LON)] == 0) seam(b, eye, scene, mesh, radius, a00, a10, a01, a11, 0.45, edge);
      if (mesh.quad[i * LON + (j + 1) % LON] == 0) seam(b, eye, scene, mesh, radius, a01, a11, a00, a10, 0.45, edge);
   }

   private static void seam(Out b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double radius, int e0, int e1, int o0, int o1, double depth, int a) {
      double r = radius + 0.02;
      double ix0 = mesh.dx[e0] + (mesh.dx[o0] - mesh.dx[e0]) * depth, iy0 = mesh.dy[e0] + (mesh.dy[o0] - mesh.dy[e0]) * depth, iz0 = mesh.dz[e0] + (mesh.dz[o0] - mesh.dz[e0]) * depth;
      double ix1 = mesh.dx[e1] + (mesh.dx[o1] - mesh.dx[e1]) * depth, iy1 = mesh.dy[e1] + (mesh.dy[o1] - mesh.dy[e1]) * depth, iz1 = mesh.dz[e1] + (mesh.dz[o1] - mesh.dz[e1]) * depth;
      vertex(b, eye, scene.cx + mesh.dx[e0] * r, scene.y0 + mesh.dy[e0] * r, scene.cz + mesh.dz[e0] * r, 190, 245, 255, a);
      vertex(b, eye, scene.cx + mesh.dx[e1] * r, scene.y0 + mesh.dy[e1] * r, scene.cz + mesh.dz[e1] * r, 190, 245, 255, a);
      vertex(b, eye, scene.cx + ix1 * r, scene.y0 + iy1 * r, scene.cz + iz1 * r, 90, 190, 255, 0);
      vertex(b, eye, scene.cx + ix0 * r, scene.y0 + iy0 * r, scene.cz + iz0 * r, 90, 190, 255, 0);
   }

   /** Energy lattice: the hexagonal (Goldberg) cells of a geodesic dome as thin camera-facing
    * lines just outside the fill, with bright pulses rising from the ground to the crown. */
   static void lattice(Out b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double t, boolean minimal) {
      if (t >= SawChoreography.SHATTER) return;
      double k = shellAlpha(t);
      if (k <= 0.004) return;
      double radius = SawChoreography.shellRadius(t) + 0.05;
      double expand = 1.0 - SawChoreography.clamp01((t - SawChoreography.EXPAND_START) / (SawChoreography.EXPAND_END - SawChoreography.EXPAND_START));
      double crack = t >= SawChoreography.CRACK_START ? SawChoreography.clamp01((t - SawChoreography.CRACK_START) / (SawChoreography.SHATTER - SawChoreography.CRACK_START)) : 0.0;
      double wave = (t / 46.0) % 1.0 * 1.5 - 0.25;
      float[] seg = mesh.lattice;
      int step = minimal ? 12 : 6;
      for (int s = 0; s < seg.length; s += step) {
         double ax = seg[s], ay = seg[s + 1], az = seg[s + 2], bx = seg[s + 3], by = seg[s + 4], bz = seg[s + 5];
         double pax = scene.cx + ax * radius, pay = scene.y0 + ay * radius, paz = scene.cz + az * radius;
         double pbx = scene.cx + bx * radius, pby = scene.y0 + by * radius, pbz = scene.cz + bz * radius;
         // camera-facing width
         double tx = pbx - pax, ty = pby - pay, tz = pbz - paz;
         double vx = (pax + pbx) * 0.5 - eye.x, vy = (pay + pby) * 0.5 - eye.y, vz = (paz + pbz) * 0.5 - eye.z;
         double wx = ty * vz - tz * vy, wy = tz * vx - tx * vz, wz = tx * vy - ty * vx;
         double wl = Math.sqrt(wx * wx + wy * wy + wz * wz) + 1.0E-6;
         double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
         double w = 0.035 + 0.0025 * dist;
         wx = wx / wl * w;
         wy = wy / wl * w;
         wz = wz / wl * w;
         int aa = latticeAlpha(eye, pax, pay, paz, ax, ay, az, wave, k, expand, crack);
         int ab = latticeAlpha(eye, pbx, pby, pbz, bx, by, bz, wave, k, expand, crack);
         if (aa <= 2 && ab <= 2) continue;
         vertex(b, eye, pax - wx, pay - wy, paz - wz, 140, 225, 255, aa);
         vertex(b, eye, pax + wx, pay + wy, paz + wz, 140, 225, 255, aa);
         vertex(b, eye, pbx + wx, pby + wy, pbz + wz, 140, 225, 255, ab);
         vertex(b, eye, pbx - wx, pby - wy, pbz - wz, 140, 225, 255, ab);
      }
   }

   private static int latticeAlpha(Vec3 eye, double px, double py, double pz, double nx, double ny, double nz, double wave, double k, double expand, double crack) {
      double f = grazing(eye, px, py, pz, nx, ny, nz);
      double pulse = Math.exp(-Math.pow((ny - wave) / 0.07, 2));
      double base = 34 + 150 * pulse + 120 * expand;
      // phase III: the lattice stutters as the shell gives way
      if (crack > 0) base *= 1.0 - 0.6 * crack * (0.5 + 0.5 * Math.sin(px * 1.7 + pz * 1.3 + crack * 40.0));
      return (int) (k * base * (0.35 + 0.65 * f));
   }

   /** After the shatter: the dome breaks into triangular shards (two per 2 x 2 quads) flying out,
    * tumbling and falling; the glow pass adds a glint that sparkles as each one turns. */
   private static void shards(Out b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double t, boolean glow) {
      double dt = t - SawChoreography.SHATTER;
      double k = 1.0 - SawChoreography.clamp01(dt / (SawChoreography.LENGTH - SawChoreography.SHATTER));
      if (k <= 0.004) return;
      double radius = SawChoreography.RADIUS;
      int n = 0;
      for (int i = 0; i + 2 <= LAT; i += 2) {
         for (int j = 0; j + 2 <= LON; j += 2, n++) {
            if (mesh.quad[(i + 1) * LON + j + 1] == 0 && mesh.quad[i * LON + j] == 0) continue;
            int v00 = i * (LON + 1) + j, v02 = v00 + 2, v20 = v00 + 2 * (LON + 1), v22 = v20 + 2;
            for (int half = 0; half < 2; half++) {
               int id = (n * 2 + half) % mesh.shardSpeed.length;
               int p0 = v00, p1 = half == 0 ? v02 : v22, p2 = half == 0 ? v22 : v20;
               double cxs = (mesh.dx[p0] + mesh.dx[p1] + mesh.dx[p2]) / 3.0, cys = (mesh.dy[p0] + mesh.dy[p1] + mesh.dy[p2]) / 3.0, czs = (mesh.dz[p0] + mesh.dz[p1] + mesh.dz[p2]) / 3.0;
               double v = mesh.shardSpeed[id], phase = mesh.shardPhase[id];
               double ox = cxs * v * dt, oy = cys * v * dt - 0.022 * dt * dt, oz = czs * v * dt;
               double shrink = 1.0 - 0.6 * SawChoreography.clamp01(dt / 12.0) * phase;
               double spin = (phase - 0.5) * 0.5 * dt;
               double cos = Math.cos(spin), sin = Math.sin(spin);
               double f = grazing(eye, scene.cx + cxs * radius, scene.y0 + cys * radius, scene.cz + czs * radius, cxs, cys, czs);
               int a;
               int r, g, bl;
               if (glow) {
                  double glint = Math.pow(0.5 + 0.5 * Math.sin(phase * 40.0 + dt * (0.6 + phase)), 8);
                  a = (int) (k * (24 + 200 * glint));
                  r = 200; g = 245; bl = 255;
               } else {
                  a = (int) (k * (70 + 110 * f));
                  r = lerp(DEEP[0], RIM[0], 0.5 + 0.5 * f); g = lerp(DEEP[1], RIM[1], 0.5 + 0.5 * f); bl = 255;
               }
               int[] tri = {p0, p1, p2, p2};
               for (int vtx : tri) {
                  // shrink toward the centroid, then turn about the radial axis through it (Rodrigues, axis = centroid dir)
                  double x = (mesh.dx[vtx] - cxs) * shrink, y = (mesh.dy[vtx] - cys) * shrink, z = (mesh.dz[vtx] - czs) * shrink;
                  double al = Math.sqrt(cxs * cxs + cys * cys + czs * czs) + 1.0E-6;
                  double ux = cxs / al, uy = cys / al, uz = czs / al;
                  double dot = ux * x + uy * y + uz * z;
                  double rx = x * cos + (uy * z - uz * y) * sin + ux * dot * (1 - cos);
                  double ry = y * cos + (uz * x - ux * z) * sin + uy * dot * (1 - cos);
                  double rz = z * cos + (ux * y - uy * x) * sin + uz * dot * (1 - cos);
                  vertex(b, eye, scene.cx + (cxs + rx) * radius + ox, scene.y0 + (cys + ry) * radius + oy, scene.cz + (czs + rz) * radius + oz, r, g, bl, a);
               }
            }
         }
      }
   }

   /** Phase III cracks: white ribbons running over the intact shell, growing until it shatters. */
   private static void cracks(Out b, Vec3 eye, SawSphereClient.Scene scene, Mesh mesh, double t) {
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
            // soft cyan halo around the white core
            int halo = alpha / 4;
            vertex(b, eye, scene.cx + ax * r - wx * 4, scene.y0 + ay * r - wy * 4, scene.cz + az * r - wz * 4, 120, 210, 255, 0);
            vertex(b, eye, scene.cx + ax * r, scene.y0 + ay * r, scene.cz + az * r, 150, 225, 255, halo);
            vertex(b, eye, scene.cx + bx * r, scene.y0 + by * r, scene.cz + bz * r, 150, 225, 255, halo);
            vertex(b, eye, scene.cx + bx * r - wx * 4, scene.y0 + by * r - wy * 4, scene.cz + bz * r - wz * 4, 120, 210, 255, 0);
            vertex(b, eye, scene.cx + ax * r, scene.y0 + ay * r, scene.cz + az * r, 150, 225, 255, halo);
            vertex(b, eye, scene.cx + ax * r + wx * 4, scene.y0 + ay * r + wy * 4, scene.cz + az * r + wz * 4, 120, 210, 255, 0);
            vertex(b, eye, scene.cx + bx * r + wx * 4, scene.y0 + by * r + wy * 4, scene.cz + bz * r + wz * 4, 120, 210, 255, 0);
            vertex(b, eye, scene.cx + bx * r, scene.y0 + by * r, scene.cz + bz * r, 150, 225, 255, halo);
            vertex(b, eye, scene.cx + ax * r - wx, scene.y0 + ay * r - wy, scene.cz + az * r - wz, 235, 250, 255, alpha);
            vertex(b, eye, scene.cx + ax * r + wx, scene.y0 + ay * r + wy, scene.cz + az * r + wz, 235, 250, 255, alpha);
            vertex(b, eye, scene.cx + bx * r + wx, scene.y0 + by * r + wy, scene.cz + bz * r + wz, 235, 250, 255, alpha);
            vertex(b, eye, scene.cx + bx * r - wx, scene.y0 + by * r - wy, scene.cz + bz * r - wz, 235, 250, 255, alpha);
         }
      }
   }

   /** Translucent body (two faces, rotating spokes), the rim band and a ground telegraph under a low disc. */
   private static void sawBody(Out b, Vec3 eye, SawSphereClient.Scene scene, int s, double t, boolean minimal) {
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

   private static void ring(Out b, Vec3 eye, double x, double y, double z, double ri, double ro, int seg, int r, int g, int bl, int a) {
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
   private static void sawGlow(Out b, Vec3 eye, SawSphereClient.Scene scene, int s, double t, boolean minimal) {
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
   private static void tether(Out b, Vec3 eye, Minecraft mc, SawSphereClient.Scene scene, double t, float partial) {
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
   private static void finale(Out b, Vec3 eye, SawSphereClient.Scene scene, double t) {
      double dt = t - SawChoreography.FINAL_SWING;
      if (dt < 0 || dt > 12) return;
      double u = dt / 12.0;
      double r = 0.8 + (SawChoreography.RADIUS - 1.0) * (1.0 - (1.0 - u) * (1.0 - u));
      double y = scene.y0 + SawChoreography.hover(t) + 1.2;
      ring(b, eye, scene.cx, y, scene.cz, r - 0.5 * (1.0 - u) - 0.1, r, 64, 255, 248, 220, (int) (230 * (1.0 - u)));
      ring(b, eye, scene.cx, y, scene.cz, r * 0.7, r - 0.1, 64, 160, 230, 255, (int) (70 * (1.0 - u)));
   }
}

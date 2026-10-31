package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.ElectricVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriPhase;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriRules;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriPortalS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client side of the invasion portal. Holds the last {@link ChitauriPortalS2CPacket} (advancing the phase tick between
 * packets), darkens and tints the sky, and draws the portal in the sky: storm clouds gathering into a vortex, a pressure
 * ripple and crackle where the sky tears, the beam from the anchor, then the opening hole — a dark interior with far
 * stars, nebula and tunnel rings projected through the opening (real parallax, never outside the rim), rotating swirl
 * layers, a flickering white-blue rim with wide glow — and the collapse with its implosion flash. An emergency stop
 * collapses it in a second; without packets for 20 s it closes on its own; a level change clears it. Render thread.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ChitauriPortalClient {
   static final double VIEW_RANGE = 720.0;
   private static final int TIMEOUT = 400;
   private static final int COLLAPSE = 24;
   private static final int STARS = 260;
   private static final int PUFFS = 170;
   private static final Object TINT = new Object();
   private static boolean active;
   private static double cx, cy, cz;
   private static double ax, ay, az;
   private static float radius = ChitauriRules.PORTAL_RADIUS;
   private static ChitauriPhase phase = ChitauriPhase.ENDED;
   private static int phaseTick;
   private static int phaseLength;
   private static long seed;
   private static int clock;
   private static int lastPacket;
   private static int collapse = -1;
   private static float collapseOpen;
   private static float collapseDark;
   private static boolean imploded;
   private static ClientLevel level;
   private static double[] normal = {0, -1, 0};
   private static double[] axisA = {1, 0, 0};
   private static double[] axisB = {0, 0, 1};

   private ChitauriPortalClient() {
   }

   /** The portal plane for clipping things that are still inside it (null when no portal is open). */
   public record Plane(double cx, double cy, double cz, double[] n, double radius) {
      /** False while {@code (x, y, z)} is behind the opening (inside the tube behind the disc), true otherwise. */
      public boolean inFront(double x, double y, double z, double margin) {
         return !PortalGeometry.behind(new double[]{x, y, z}, new double[]{this.cx, this.cy, this.cz}, this.n, this.radius * 1.15, margin, 90.0);
      }
   }

   @Nullable
   public static Plane plane() {
      return active && open(0.0F) > 0.02F ? new Plane(cx, cy, cz, normal, radius) : null;
   }

   public static boolean active() {
      return active;
   }

   public static Vec3 centre() {
      return new Vec3(cx, cy, cz);
   }

   public static void accept(ChitauriPortalS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      level = mc.level;
      if (!p.active()) {
         stop(true);
         return;
      }
      boolean fresh = !active || p.seed() != seed;
      active = true;
      collapse = -1;
      cx = p.cx();
      cy = p.cy();
      cz = p.cz();
      ax = p.ax();
      ay = p.ay();
      az = p.az();
      radius = p.radius() > 1.0F ? p.radius() : ChitauriRules.PORTAL_RADIUS;
      ChitauriPhase next = ChitauriPhase.byId(p.phase());
      if (next != phase || fresh) {
         imploded = false;
      }
      phase = next;
      phaseTick = p.phaseTick();
      phaseLength = Math.max(1, p.phaseLength());
      seed = p.seed();
      lastPacket = clock;
      normal = ChitauriRules.portalNormal(p.tiltYaw());
      double[][] basis = PortalGeometry.basis(normal);
      axisA = basis[0];
      axisB = basis[1];
      SkyTint.INSTANCE.push(TINT, 90, ChitauriPortalClient::tint);
   }

   /** Closes the portal: a short collapse when it was visible ({@code animate}), else at once. */
   static void stop(boolean animate) {
      if (active && animate) {
         collapseOpen = open(0.0F);
         collapseDark = darkness(0.0F);
         collapse = collapseOpen > 0.01F || collapseDark > 0.01F ? COLLAPSE : -1;
      } else if (!animate) {
         collapse = -1;
      }
      active = false;
      if (collapse < 0) {
         SkyTint.INSTANCE.remove(TINT);
      }
   }

   static void clear() {
      active = false;
      collapse = -1;
      phase = ChitauriPhase.ENDED;
      SkyTint.INSTANCE.remove(TINT);
   }

   /** Portal opening 0..1 at the partial tick. */
   static float open(float partial) {
      if (active) {
         return ChitauriRules.portalOpen(phase, Math.min(phaseLength, phaseTick + partial));
      }
      if (collapse >= 0) {
         return collapseOpen * smooth((collapse - partial) / COLLAPSE);
      }
      return 0.0F;
   }

   /** Sky darkness 0..1. */
   static float darkness(float partial) {
      if (active) {
         return ChitauriRules.skyDark(phase, Math.min(phaseLength, phaseTick + partial));
      }
      if (collapse >= 0) {
         return collapseDark * smooth((collapse - partial) / COLLAPSE);
      }
      return 0.0F;
   }

   /** The anchor beam 0..1: shoots up at the beam beat, holds through the fight, is cut at the sever beat. */
   static float beam(float partial) {
      if (!active) {
         return 0.0F;
      }
      float t = Math.min(phaseLength, phaseTick + partial);
      return switch (phase) {
         case OPENING -> Mth.clamp((t - ChitauriRules.BEAT_BEAM) / 8.0F, 0.0F, 1.0F);
         case GROUND_ASSAULT, AIR_ASSAULT, LEVIATHAN, CLIMAX -> 1.0F;
         case CLOSING -> 1.0F - Mth.clamp((t - ChitauriRules.BEAT_SEVER) / 10.0F, 0.0F, 1.0F);
         default -> 0.0F;
      };
   }

   /** Pre-opening distortion 0..1 (the sky starts to tear before the beam opens it). */
   static float tear(float partial) {
      if (!active || phase != ChitauriPhase.OPENING) {
         return 0.0F;
      }
      float t = phaseTick + partial;
      return smooth(t / 80.0F) * (1.0F - smooth((t - ChitauriRules.BEAT_BEAM - 40.0F) / 60.0F));
   }

   private static SkyTint.Tint tint() {
      float d = darkness(0.0F);
      return new SkyTint.Tint(1.0F - 0.42F * d, 1.0F - 0.36F * d, 1.0F - 0.14F * d, 0.58F * d, 1.0F);
   }

   // ------------------------------------------------------------------ tick

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != level) {
         if (active || collapse >= 0) {
            clear();
         }
         level = mc.level;
      }
      if (mc.level == null || mc.isPaused()) {
         return;
      }
      clock++;
      if (active) {
         int before = phaseTick;
         if (phaseTick < phaseLength) {
            phaseTick++;
         }
         if (clock - lastPacket > TIMEOUT) {
            stop(true);
         } else {
            beats(mc, before, phaseTick);
         }
      } else if (collapse >= 0) {
         if (--collapse < 0) {
            SkyTint.INSTANCE.remove(TINT);
         }
      }
      if (active || collapse >= 0) {
         ambient(mc);
      }
   }

   /** One-shot moments the client adds on top of the server's strikes: the beam ignition and the implosion. */
   private static void beats(Minecraft mc, int before, int now) {
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      Vec3 c = centre();
      Vec3 anchor = new Vec3(ax, ay, az);
      if (phase == ChitauriPhase.OPENING && before < ChitauriRules.BEAT_BEAM && now >= ChitauriRules.BEAT_BEAM) {
         FlashOverlay.flash(0.35F * CameraShake.falloff(eye.distanceTo(anchor), 160.0), 8);
         CameraShake.addAt(anchor, 0.6F, 90.0);
         ElectricVfx.lightPool(anchor, 9.0F, 1.4F, 30, seed);
         ElectricVfx.skyFlash(c, radius * 2.5F, 14, seed + 1);
      }
      if (phase == ChitauriPhase.OPENING && before < ChitauriRules.BEAT_PORTAL_FULL && now >= ChitauriRules.BEAT_PORTAL_FULL) {
         ElectricVfx.skyFlash(c, radius * 3.0F, 18, seed + 2);
         CameraShake.addAt(c, 0.35F, 260.0);
      }
      if (phase == ChitauriPhase.CLOSING && !imploded && now > 40 && ChitauriRules.portalOpen(phase, now) < 0.04F) {
         imploded = true;
         FlashOverlay.flash(0.45F * CameraShake.falloff(eye.distanceTo(c), 320.0), 10);
         ElectricVfx.skyFlash(c, radius * 3.5F, 16, seed + 3);
         CameraShake.addAt(c, 0.5F, 320.0);
         ChitauriVfx.pulse(c, normal, 0.0F, radius * 4.0F, 26, 0.8F);
      }
   }

   /** Sky life the server does not send: crackle while the sky tears, faint rim discharges, the anchor glow. */
   private static void ambient(Minecraft mc) {
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      Vec3 c = centre();
      if (eye.distanceToSqr(c) > VIEW_RANGE * VIEW_RANGE) {
         return;
      }
      float open = open(0.0F);
      float tear = tear(0.0F);
      long s = seed * 31L + clock;
      if (tear > 0.2F && clock % 7 == 0) {
         ElectricVfx.crackle(c, 3.0F + 6.0F * tear, 3 + (int)(4 * tear), 8, s);
      }
      if (tear > 0.3F && clock % 37 == 0) {
         ElectricVfx.skyFlash(c, radius * 2.0F, 8, s);
      }
      if (open > 0.1F && clock % 9 == 0) {
         double a0 = hash(s, 1) * Math.PI * 2.0;
         double a1 = a0 + 0.25 + hash(s, 2) * 0.6;
         double r = radius * open;
         Vec3 p0 = disc(r * 1.01, a0);
         Vec3 p1 = disc(r * (1.0 + 0.1 * hash(s, 3)), a1);
         ElectricVfx.arc(p0, p1, 0.35F, 6, 2, s);
      }
      float beam = beam(0.0F);
      if (beam > 0.5F && clock % 12 == 0) {
         Vec3 anchor = new Vec3(ax, ay + 0.2, az);
         if (eye.distanceToSqr(anchor) < 160.0 * 160.0) {
            ElectricVfx.lightPool(anchor, 5.0F, 0.9F, 14, s);
            ElectricVfx.sparks(anchor.add(0, 1.0, 0), 6, 0.35F, 10, s);
         }
      }
   }

   private static Vec3 disc(double r, double angle) {
      return new Vec3(cx + (axisA[0] * Math.cos(angle) + axisB[0] * Math.sin(angle)) * r, cy + (axisA[1] * Math.cos(angle) + axisB[1] * Math.sin(angle)) * r,
         cz + (axisA[2] * Math.cos(angle) + axisB[2] * Math.sin(angle)) * r);
   }

   // ------------------------------------------------------------------ render

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_LEVEL || !active && collapse < 0) {
         return;
      }
      float partial = event.getPartialTick();
      float open = open(partial);
      float dark = darkness(partial);
      float beam = beam(partial);
      float tear = tear(partial);
      if (open <= 0.001F && dark <= 0.001F && beam <= 0.001F && tear <= 0.001F) {
         return;
      }
      Camera camera = event.getCamera();
      Vec3 eye = camera.getPosition();
      if (eye.distanceToSqr(cx, cy, cz) > VIEW_RANGE * VIEW_RANGE) {
         return;
      }
      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      modelView.setIdentity();
      PixelVfx.levelView(modelView, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buf = tess.getBuilder();
      Frame f = new Frame(eye, camera, (clock + partial) / 20.0F, open, dark, beam, tear);
      try {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
         buf.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         clouds(buf, f, false);
         interior(buf, f);
         tess.end();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buf.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         clouds(buf, f, true);
         space(buf, f);
         tunnel(buf, f);
         swirl(buf, f);
         rim(buf, f);
         ripples(buf, f);
         beamRibbon(buf, f);
         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   /** Per-frame values shared by the draw passes. */
   private static final class Frame {
      final Vec3 eye;
      final Camera camera;
      final float time;
      final float open;
      final float dark;
      final float beam;
      final float tear;
      final float side;
      final Matrix4f m = new Matrix4f();
      final double[] eyeArr;
      final double[] c = {cx, cy, cz};
      final Vector3f rc;

      Frame(Vec3 eye, Camera camera, float time, float open, float dark, float beam, float tear) {
         this.eye = eye;
         this.camera = camera;
         this.time = time;
         this.open = open;
         this.dark = dark;
         this.beam = beam;
         this.tear = tear;
         this.eyeArr = new double[]{eye.x, eye.y, eye.z};
         double s = (eye.x - cx) * normal[0] + (eye.y - cy) * normal[1] + (eye.z - cz) * normal[2];
         this.side = s >= 0.0 ? 1.0F : -1.0F;
         this.rc = new Vector3f((float)(cx - eye.x), (float)(cy - eye.y), (float)(cz - eye.z));
      }

      /** Camera-relative point at in-plane (u, v) plus {@code lift} along the normal towards the viewer. */
      Vector3f at(double u, double v, double lift) {
         double l = lift * this.side;
         return new Vector3f((float)(cx + axisA[0] * u + axisB[0] * v + normal[0] * l - this.eye.x),
            (float)(cy + axisA[1] * u + axisB[1] * v + normal[1] * l - this.eye.y),
            (float)(cz + axisA[2] * u + axisB[2] * v + normal[2] * l - this.eye.z));
      }

      void vertex(BufferBuilder buf, double u, double v, double lift, int r, int g, int b, int a) {
         Vector3f p = this.at(u, v, lift);
         GlowDraw.vertex(buf, this.m, p.x, p.y, p.z, r, g, b, a);
      }
   }

   /**
    * Storm vortex around the opening: dark puffs (normal pass) and their portal-lit undersides (glow pass). Each puff
    * is two soft overlapping discs (a body and an offset lobe) so the ring reads as billowing cloud, not as tiles.
    */
   private static void clouds(BufferBuilder buf, Frame f, boolean glow) {
      float density = Math.max(f.dark, f.open);
      if (density <= 0.01F) {
         return;
      }
      Vector3f left = f.camera.getLeftVector();
      Vector3f up = f.camera.getUpVector();
      for (int i = 0; i < PUFFS; i++) {
         float h1 = hash(seed, i * 5 + 1);
         float h2 = hash(seed, i * 5 + 2);
         float h3 = hash(seed, i * 5 + 3);
         float h4 = hash(seed, i * 5 + 4);
         float ring = (float)Math.sqrt(h1);
         double r = radius * (1.12 + 2.9 * ring) * (0.85 + 0.15 * f.dark);
         double angle = h2 * Math.PI * 2.0 + f.time * (0.03 + 0.16 * (1.0 - ring)) + ring * 1.8;
         double lift = (h3 - 0.5) * 7.0 + (1.0 - ring) * 2.0;
         Vector3f p = f.at(Math.cos(angle) * r, Math.sin(angle) * r, lift);
         float size = (6.0F + 9.0F * h4) * (0.7F + 0.5F * ring);
         // the lobe sits along the swirl direction, so neighbouring puffs merge into curved bands
         double lobeAngle = angle + (0.35 + 0.4 * h3) * size / Math.max(8.0, r);
         Vector3f lobe = f.at(Math.cos(lobeAngle) * r, Math.sin(lobeAngle) * r, lift + 1.0);
         float fade = puffFade(p, size);
         if (fade <= 0.01F) {
            continue;
         }
         if (glow) {
            float lit = f.open * (1.0F - ring) * (1.0F - ring);
            float flash = hash(seed + clock / 3, i) > 0.97F ? 1.0F : 0.0F;
            int a = (int)((60 * lit + 80 * flash * f.dark) * fade);
            if (a > 2) {
               GlowDraw.disc(buf, f.m, p, left, up, size * 0.9F, 0.25F, 8, 70, 115, 230, a);
            }
         } else {
            int shade = 24 + (int)(20 * h3);
            int a = (int)(175 * density * (0.55F + 0.45F * ring) * fade);
            GlowDraw.disc(buf, f.m, p, left, up, size, 0.45F, 8, shade, shade + 3, shade + 12, a);
            GlowDraw.disc(buf, f.m, lobe, left, up, size * (0.55F + 0.25F * h2), 0.4F, 8, shade + 6, shade + 9, shade + 18, (int)(a * 0.8F));
         }
      }
   }

   /** Pure: puffs thin out when the eye is inside or right next to one, so flying through the storm never blanks the view. */
   static float puffFade(Vector3f rel, float size) {
      float d = rel.length();
      return Mth.clamp((d - size * 0.6F) / (size * 1.6F), 0.0F, 1.0F);
   }

   /** The dark of space inside the opening, and the darkening lens while the sky tears. */
   private static void interior(BufferBuilder buf, Frame f) {
      double r = radius * f.open;
      if (f.tear > 0.01F && f.open < 0.2F) {
         double lens = radius * (0.12 + 0.2 * f.tear);
         fan(buf, f, lens, 0.0, 10, 14, 30, (int)(150 * f.tear), 20, 30, 60, 0);
      }
      if (r < 0.05) {
         return;
      }
      fan(buf, f, r * 0.72, 0.0, 3, 5, 14, 250, 8, 14, 40, 246);
      ringBand(buf, f, r * 0.72, r * 1.0, 0.0, 8, 14, 40, 246, 22, 48, 120, 236);
   }

   /** Far stars and nebula behind the opening, projected onto it along the eye ray (parallax, clipped to the rim). */
   private static void space(BufferBuilder buf, Frame f) {
      double r = radius * f.open;
      if (r < 0.5) {
         return;
      }
      double[] depth = {-normal[0] * f.side, -normal[1] * f.side, -normal[2] * f.side};
      // nebula: large soft clouds far behind
      for (int i = 0; i < 9; i++) {
         double d = 260.0 + 420.0 * hash(seed, 900 + i);
         double lat = radius * 1.6 * Math.sqrt(hash(seed, 920 + i));
         double ang = hash(seed, 940 + i) * Math.PI * 2.0 + f.time * 0.004;
         double[] s = point(depth, d, Math.cos(ang) * lat, Math.sin(ang) * lat);
         double[] x = PortalGeometry.project(f.eyeArr, s, f.c, normal);
         if (x == null) {
            continue;
         }
         double[] uv = PortalGeometry.planar(x, f.c, axisA, axisB);
         double scale = dist(f.eyeArr, x) / dist(f.eyeArr, s);
         double rad = (70.0 + 90.0 * hash(seed, 960 + i)) * scale;
         int[][] tints = {{40, 70, 190}, {95, 55, 170}, {30, 110, 190}, {70, 90, 210}};
         int[] t = tints[i % tints.length];
         clippedBlob(buf, f, uv[0], uv[1], rad, r * 0.97, t[0], t[1], t[2], (int)(46 * f.open));
      }
      // stars
      for (int i = 0; i < STARS; i++) {
         double d = 50.0 + 520.0 * hash(seed, 2 * i + 3) * hash(seed, 2 * i + 4);
         double lat = radius * 1.9 * Math.sqrt(hash(seed, 3000 + i));
         double ang = hash(seed, 4000 + i) * Math.PI * 2.0;
         double[] s = point(depth, d, Math.cos(ang) * lat, Math.sin(ang) * lat);
         double[] x = PortalGeometry.project(f.eyeArr, s, f.c, normal);
         if (x == null) {
            continue;
         }
         double[] uv = PortalGeometry.planar(x, f.c, axisA, axisB);
         if (uv[0] * uv[0] + uv[1] * uv[1] > r * r * 0.9) {
            continue;
         }
         float tw = 0.6F + 0.4F * Mth.sin(f.time * (1.5F + 3.0F * hash(seed, 5000 + i)) + i);
         float size = (0.09F + 0.22F * hash(seed, 6000 + i)) * (float)Math.max(1.0, dist(f.eyeArr, x) / 90.0);
         int warm = hash(seed, 7000 + i) > 0.85F ? 1 : 0;
         int a = (int)(235 * tw * f.open);
         Vector3f p = f.at(uv[0], uv[1], 0.08);
         squareInPlane(buf, f, p, size, warm == 1 ? 255 : 205, warm == 1 ? 220 : 225, 255, a);
      }
   }

   /** Rings receding into the opening (projected like the stars): the depth of the tunnel and its slow turn. */
   private static void tunnel(BufferBuilder buf, Frame f) {
      double r = radius * f.open;
      if (r < 1.0) {
         return;
      }
      double[] depth = {-normal[0] * f.side, -normal[1] * f.side, -normal[2] * f.side};
      int segs = 48;
      double[][] inner = new double[segs + 1][];
      double[][] outer = new double[segs + 1][];
      for (int k = 1; k <= 7; k++) {
         double d = k * 8.5;
         double rr = r * (1.0 - 0.085 * k);
         double w = 0.018 + 0.006 * k;
         boolean ok = true;
         for (int j = 0; j <= segs; j++) {
            double ang = j / (double)segs * Math.PI * 2.0;
            inner[j] = projected(f, depth, d, Math.cos(ang) * rr * (1.0 - w), Math.sin(ang) * rr * (1.0 - w), r * 0.985);
            outer[j] = projected(f, depth, d, Math.cos(ang) * rr * (1.0 + w), Math.sin(ang) * rr * (1.0 + w), r * 0.985);
            if (inner[j] == null || outer[j] == null) {
               ok = false;
               break;
            }
         }
         if (!ok) {
            continue;
         }
         float fade = (1.0F - k / 8.0F) * f.open;
         float spin = f.time * (0.35F + 0.08F * k) * (k % 2 == 0 ? 1.0F : -1.0F);
         for (int j = 0; j < segs; j++) {
            double ang = (j + 0.5) / segs * Math.PI * 2.0;
            float streak = 0.35F + 0.65F * Math.max(0.0F, Mth.sin((float)(ang * 3.0) + spin));
            int a = (int)(85 * fade * streak);
            if (a <= 1) {
               continue;
            }
            int g = 120 + 12 * k;
            f.vertex(buf, inner[j][0], inner[j][1], 0.1, 60, g, 255, a);
            f.vertex(buf, outer[j][0], outer[j][1], 0.1, 60, g, 255, a);
            f.vertex(buf, outer[j + 1][0], outer[j + 1][1], 0.1, 60, g, 255, a);
            f.vertex(buf, inner[j + 1][0], inner[j + 1][1], 0.1, 60, g, 255, a);
         }
      }
   }

   /** Three counter-rotating layers of spiral arms between the dark and the rim. */
   private static void swirl(BufferBuilder buf, Frame f) {
      double r = radius * f.open;
      if (r < 1.0) {
         return;
      }
      int[] arms = {6, 9, 4};
      float[] inner = {0.48F, 0.62F, 0.7F};
      float[] twist = {2.3F, -1.7F, 3.1F};
      float[] speed = {0.32F, -0.47F, 0.21F};
      int[] alpha = {60, 42, 72};
      float closing = phase == ChitauriPhase.CLOSING && active ? 1.0F + 1.5F * (1.0F - f.open) : 1.0F;
      int steps = 14;
      for (int layer = 0; layer < 3; layer++) {
         for (int arm = 0; arm < arms[layer]; arm++) {
            double base = arm / (double)arms[layer] * Math.PI * 2.0 + hash(seed, 100 + layer * 16 + arm) * 0.5 + f.time * speed[layer] * closing;
            double pu = 0, pv = 0, pw = 0;
            for (int st = 0; st <= steps; st++) {
               double u = st / (double)steps;
               double rho = r * (inner[layer] + (1.02 - inner[layer]) * u);
               double th = base + twist[layer] * u;
               double cu = Math.cos(th) * rho, cv = Math.sin(th) * rho;
               double w = r * (0.012 + 0.05 * u);
               if (st > 0) {
                  double tu = cu - pu, tv = cv - pv;
                  double tl = Math.sqrt(tu * tu + tv * tv);
                  if (tl > 1.0E-6) {
                     double nu = -tv / tl, nv = tu / tl;
                     float a0 = (float)Math.pow(Math.sin(Math.PI * (st - 1) / steps), 0.8);
                     float a1 = (float)Math.pow(Math.sin(Math.PI * st / steps), 0.8);
                     int g0 = 120 + (int)(80 * (st - 1) / (float)steps);
                     int g1 = 120 + (int)(80 * st / (float)steps);
                     int al0 = (int)(alpha[layer] * a0 * f.open);
                     int al1 = (int)(alpha[layer] * a1 * f.open);
                     f.vertex(buf, pu - nu * pw, pv - nv * pw, 0.15, 70, g0, 255, al0);
                     f.vertex(buf, pu + nu * pw, pv + nv * pw, 0.15, 70, g0, 255, al0);
                     f.vertex(buf, cu + nu * w, cv + nv * w, 0.15, 70, g1, 255, al1);
                     f.vertex(buf, cu - nu * w, cv - nv * w, 0.15, 70, g1, 255, al1);
                  }
               }
               pu = cu;
               pv = cv;
               pw = w;
            }
         }
      }
   }

   /** White-hot flickering rim, its blue glow and a wide halo; brighter while it opens or collapses. */
   private static void rim(BufferBuilder buf, Frame f) {
      double r = radius * f.open;
      if (r < 0.05) {
         return;
      }
      float surge = 1.0F + 0.7F * (1.0F - f.open);
      int segs = 72;
      for (int i = 0; i < segs; i++) {
         double a0 = i / (double)segs * Math.PI * 2.0;
         double a1 = (i + 1) / (double)segs * Math.PI * 2.0;
         float n0 = flicker(i, f.time);
         float n1 = flicker(i + 1 == segs ? 0 : i + 1, f.time);
         double j0 = 1.0 + 0.012 * (n0 - 0.6), j1 = 1.0 + 0.012 * (n1 - 0.6);
         band(buf, f, r * 0.93, r * 0.985 * j0, r * 0.985 * j1, r * 1.035, a0, a1, 205, 232, 255, (int)(240 * n0 * Math.min(1.0F, surge)),
            (int)(240 * n1 * Math.min(1.0F, surge)), 0.22);
      }
      ringBand3(buf, f, r * 0.84, r * 1.0, r * 1.5, 60, 130, 255, (int)(Math.min(255.0F, 120 * surge) * Math.sqrt(f.open)), 0.2);
      ringBand3(buf, f, r * 0.98, r * 1.05, r * 2.5, 50, 95, 210, (int)(48 * surge * f.open), 0.25);
   }

   /** Pressure ripples where the sky tears (before and while it opens). */
   private static void ripples(BufferBuilder buf, Frame f) {
      if (f.tear <= 0.01F) {
         return;
      }
      for (int k = 0; k < 3; k++) {
         float ph = ((f.time * 20.0F + k * 22.0F) % 66.0F) / 66.0F;
         double rr = radius * (0.1 + 0.95 * ph);
         int a = (int)(70 * f.tear * (1.0F - ph));
         ringBand3(buf, f, rr * 0.9, rr, rr * 1.08, 110, 160, 255, a, 0.3);
      }
   }

   /** The beam from the anchor into the portal: wide glow, inner glow, white core and pulses running up. */
   private static void beamRibbon(BufferBuilder buf, Frame f) {
      if (f.beam <= 0.001F) {
         return;
      }
      Vec3 from = new Vec3(ax, ay + 1.2, az);
      Vec3 to = centre();
      if (phase == ChitauriPhase.OPENING) {
         to = from.add(to.subtract(from).scale(f.beam));
      } else if (phase == ChitauriPhase.CLOSING) {
         from = to.add(from.subtract(to).scale(f.beam));
      }
      Vector3f p0 = rel(from, f.eye);
      Vector3f p1 = rel(to, f.eye);
      // face the ribbon to the closest point of the beam (the midpoint twists it edge-on near the base) and thin it out
      // when the eye stands in it: an additive white column a block from the camera would blank the whole screen
      Vector3f closest = closestOnSegment(p0, p1);
      float near = beamNearFade(closest.length());
      Vector3f toCam = new Vector3f(closest).negate();
      float pulse = 0.9F + 0.1F * Mth.sin(f.time * 9.0F);
      GlowDraw.ribbon(buf, f.m, p0, p1, toCam, 3.4F * pulse, 4.2F * pulse, 60, 120, 255, (int)(70 * near), (int)(50 * near));
      GlowDraw.ribbon(buf, f.m, p0, p1, toCam, 1.15F, 1.4F, 120, 180, 255, (int)(170 * near), (int)(150 * near));
      GlowDraw.ribbon(buf, f.m, p0, p1, toCam, 0.34F, 0.4F, 235, 245, 255, (int)(255 * near), (int)(255 * near));
      Vector3f left = f.camera.getLeftVector();
      Vector3f up = f.camera.getUpVector();
      for (int k = 0; k < 5; k++) {
         float u = (f.time * 0.9F + k / 5.0F) % 1.0F;
         Vector3f p = new Vector3f(p0).lerp(p1, u);
         float pf = beamNearFade(p.length());
         GlowDraw.billboard(buf, f.m, p, left, up, 1.5F, 0.3F, 160, 210, 255, (int)(150 * Mth.sin(u * Mth.PI) * pf));
      }
      float base = beamNearFade(p0.length() - 2.0F);
      GlowDraw.billboard(buf, f.m, p0, left, up, 4.0F, 0.15F, 90, 150, 255, (int)(120 * base));
      GlowDraw.billboard(buf, f.m, p0, left, up, 1.4F, 0.45F, 225, 240, 255, (int)(230 * base));
   }

   /** Pure: beam brightness by the eye's distance to it — 0.15 inside the column, full from 9 blocks out. */
   static float beamNearFade(float distance) {
      float t = Mth.clamp((distance - 1.0F) / 8.0F, 0.0F, 1.0F);
      return 0.15F + 0.85F * t * t * (3.0F - 2.0F * t);
   }

   /** Pure: the point of segment p0–p1 (camera-relative) closest to the eye at the origin. */
   static Vector3f closestOnSegment(Vector3f p0, Vector3f p1) {
      Vector3f d = new Vector3f(p1).sub(p0);
      float len2 = d.lengthSquared();
      float t = len2 < 1.0E-6F ? 0.0F : Mth.clamp(-p0.dot(d) / len2, 0.0F, 1.0F);
      return new Vector3f(p0).add(d.mul(t));
   }

   // ------------------------------------------------------------------ shape helpers

   private static double[] point(double[] depth, double d, double u, double v) {
      return new double[]{cx + depth[0] * d + axisA[0] * u + axisB[0] * v, cy + depth[1] * d + axisA[1] * u + axisB[1] * v,
         cz + depth[2] * d + axisA[2] * u + axisB[2] * v};
   }

   @Nullable
   private static double[] projected(Frame f, double[] depth, double d, double u, double v, double clamp) {
      double[] x = PortalGeometry.project(f.eyeArr, point(depth, d, u, v), f.c, normal);
      if (x == null) {
         return null;
      }
      double[] uv = PortalGeometry.planar(x, f.c, axisA, axisB);
      PortalGeometry.clampDisc(uv, clamp);
      return uv;
   }

   /** Soft blob in the plane with its vertices kept inside the opening. */
   private static void clippedBlob(BufferBuilder buf, Frame f, double u, double v, double rad, double clip, int r, int g, int b, int a) {
      if (a <= 1) {
         return;
      }
      int segs = 20;
      double[] c = {u, v};
      PortalGeometry.clampDisc(c, clip);
      for (int i = 0; i < segs; i++) {
         double a0 = i / (double)segs * Math.PI * 2.0;
         double a1 = (i + 1) / (double)segs * Math.PI * 2.0;
         double[] q0 = {u + Math.cos(a0) * rad, v + Math.sin(a0) * rad};
         double[] q1 = {u + Math.cos(a1) * rad, v + Math.sin(a1) * rad};
         PortalGeometry.clampDisc(q0, clip);
         PortalGeometry.clampDisc(q1, clip);
         f.vertex(buf, c[0], c[1], 0.05, r, g, b, a);
         f.vertex(buf, q0[0], q0[1], 0.05, r, g, b, 0);
         f.vertex(buf, q1[0], q1[1], 0.05, r, g, b, 0);
         f.vertex(buf, c[0], c[1], 0.05, r, g, b, a);
      }
   }

   /** Filled disc (fan) in the portal plane with a centre and an edge colour. */
   private static void fan(BufferBuilder buf, Frame f, double r, double lift, int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1) {
      int segs = 64;
      for (int i = 0; i < segs; i++) {
         double t0 = i / (double)segs * Math.PI * 2.0;
         double t1 = (i + 1) / (double)segs * Math.PI * 2.0;
         f.vertex(buf, 0, 0, lift, r0, g0, b0, a0);
         f.vertex(buf, Math.cos(t0) * r, Math.sin(t0) * r, lift, r1, g1, b1, a1);
         f.vertex(buf, Math.cos(t1) * r, Math.sin(t1) * r, lift, r1, g1, b1, a1);
         f.vertex(buf, 0, 0, lift, r0, g0, b0, a0);
      }
   }

   /** Annulus with an inner and an outer colour. */
   private static void ringBand(BufferBuilder buf, Frame f, double r0, double r1, double lift, int ri, int gi, int bi, int ai, int ro, int go, int bo,
      int ao) {
      int segs = 64;
      for (int i = 0; i < segs; i++) {
         double t0 = i / (double)segs * Math.PI * 2.0;
         double t1 = (i + 1) / (double)segs * Math.PI * 2.0;
         f.vertex(buf, Math.cos(t0) * r0, Math.sin(t0) * r0, lift, ri, gi, bi, ai);
         f.vertex(buf, Math.cos(t0) * r1, Math.sin(t0) * r1, lift, ro, go, bo, ao);
         f.vertex(buf, Math.cos(t1) * r1, Math.sin(t1) * r1, lift, ro, go, bo, ao);
         f.vertex(buf, Math.cos(t1) * r0, Math.sin(t1) * r0, lift, ri, gi, bi, ai);
      }
   }

   /** Soft ring: transparent at r0 and r2, full at r1. */
   private static void ringBand3(BufferBuilder buf, Frame f, double r0, double r1, double r2, int r, int g, int b, int a, double lift) {
      if (a <= 1) {
         return;
      }
      int segs = 64;
      for (int i = 0; i < segs; i++) {
         double t0 = i / (double)segs * Math.PI * 2.0;
         double t1 = (i + 1) / (double)segs * Math.PI * 2.0;
         band(buf, f, r0, r1, r1, r2, t0, t1, r, g, b, a, a, lift);
      }
   }

   /** One sector of a soft band: alpha 0 at {@code rIn}, {@code a0}/{@code a1} at the peak radii, 0 at {@code rOut}. */
   private static void band(BufferBuilder buf, Frame f, double rIn, double peak0, double peak1, double rOut, double t0, double t1, int r, int g, int b,
      int a0, int a1, double lift) {
      double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
      f.vertex(buf, c0 * rIn, s0 * rIn, lift, r, g, b, 0);
      f.vertex(buf, c0 * peak0, s0 * peak0, lift, r, g, b, a0);
      f.vertex(buf, c1 * peak1, s1 * peak1, lift, r, g, b, a1);
      f.vertex(buf, c1 * rIn, s1 * rIn, lift, r, g, b, 0);
      f.vertex(buf, c0 * peak0, s0 * peak0, lift, r, g, b, a0);
      f.vertex(buf, c0 * rOut, s0 * rOut, lift, r, g, b, 0);
      f.vertex(buf, c1 * rOut, s1 * rOut, lift, r, g, b, 0);
      f.vertex(buf, c1 * peak1, s1 * peak1, lift, r, g, b, a1);
   }

   private static void squareInPlane(BufferBuilder buf, Frame f, Vector3f p, float size, int r, int g, int b, int a) {
      float ux = (float)axisA[0] * size, uy = (float)axisA[1] * size, uz = (float)axisA[2] * size;
      float vx = (float)axisB[0] * size, vy = (float)axisB[1] * size, vz = (float)axisB[2] * size;
      GlowDraw.vertex(buf, f.m, p.x - ux - vx, p.y - uy - vy, p.z - uz - vz, r, g, b, a);
      GlowDraw.vertex(buf, f.m, p.x + ux - vx, p.y + uy - vy, p.z + uz - vz, r, g, b, a);
      GlowDraw.vertex(buf, f.m, p.x + ux + vx, p.y + uy + vy, p.z + uz + vz, r, g, b, a);
      GlowDraw.vertex(buf, f.m, p.x - ux + vx, p.y - uy + vy, p.z - uz + vz, r, g, b, a);
   }

   private static Vector3f rel(Vec3 p, Vec3 eye) {
      return new Vector3f((float)(p.x - eye.x), (float)(p.y - eye.y), (float)(p.z - eye.z));
   }

   private static double dist(double[] a, double[] b) {
      double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** Rim brightness 0.25..1 per segment: two travelling noise waves and a fast shimmer. */
   static float flicker(int i, float time) {
      float w = 0.6F + 0.25F * Mth.sin(i * 1.73F + time * 6.5F) * Mth.sin(i * 0.41F - time * 2.9F) + 0.15F * Mth.sin(i * 5.1F + time * 23.0F);
      return Mth.clamp(w, 0.25F, 1.0F);
   }

   /** Deterministic 0..1 hash of (seed, i). */
   static float hash(long s, int i) {
      long h = s * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
      h ^= h >>> 31;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 29;
      return (h >>> 40) / (float)(1L << 24);
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }
}

package dev.baranhan.viltrumitecore.client.jinwoo;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFlags;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFx;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooMove;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

/**
 * Jinwoo's client VFX, all own pixel/geo work (no vanilla particles): smoke pool (cap {@link #SMOKE_CAP}),
 * additive sparks, contact flashes, parry ring, step afterimages, remnant wisps, authority threads,
 * shadow waves, command markers, summon/recall streams, exchange puddles, charge streaks, the domain's
 * black mirror and the shadow soldiers' ambient smoke (LOD: none beyond 32 blocks). Client only.
 */
public final class JinwooVfx {
   public static final int SMOKE_CAP = 1200;
   public static final int SPARK_CAP = 600;
   public static final double SMOKE_LOD = 32.0;
   static final Pool SMOKE = new Pool(SMOKE_CAP);
   static final Pool SPARK = new Pool(SPARK_CAP);
   private static final Random RNG = new Random();
   private static final Map<Integer, Remnant> REMNANTS = new HashMap<>();
   private static final List<Ring> RINGS = new ArrayList<>();
   private static final List<Mark> MARKS = new ArrayList<>();
   private static final List<Stream> STREAMS = new ArrayList<>();
   private static final List<Puddle> PUDDLES = new ArrayList<>();
   private static final List<Ghost> AFTERIMAGES = new ArrayList<>();
   private static final List<Streak> STREAKS = new ArrayList<>();
   private static final Map<Integer, Threads> THREADS = new HashMap<>();
   private static final Map<Integer, Vec3> PALMS = new HashMap<>();
   private static final Domain DOMAIN = new Domain();
   private static long tick;

   record Remnant(int id, Vec3 pos, int kind, int tier, long start) {
   }

   record Ring(Vec3 c, double radius, long start, int life, int type) {
   }

   record Mark(int target, Vec3 pos, int order, long start) {
   }

   record Stream(Vec3 from, Vec3 to, long start, int life) {
   }

   record Puddle(Vec3 pos, long start, int life) {
   }

   record Ghost(Vec3 pos, float yaw, long start, int life) {
   }

   record Streak(Vec3 from, Vec3 to, long start) {
   }

   static final class Threads {
      int[] ids;
      long start;
      long release = -1;
   }

   /** Struct-of-arrays particle pool; a full pool overwrites its oldest slot. */
   static final class Pool {
      final int cap;
      final float[] x, y, z, vx, vy, vz, age, life, size, grow, drag, lift;
      final int[] color;
      final boolean[] alive;
      int next;
      int count;

      Pool(int cap) {
         this.cap = cap;
         x = new float[cap];
         y = new float[cap];
         z = new float[cap];
         vx = new float[cap];
         vy = new float[cap];
         vz = new float[cap];
         age = new float[cap];
         life = new float[cap];
         size = new float[cap];
         grow = new float[cap];
         drag = new float[cap];
         lift = new float[cap];
         color = new int[cap];
         alive = new boolean[cap];
      }

      void spawn(double px, double py, double pz, double ux, double uy, double uz, int lifeTicks, float sz, float growth, float dr, float lf, int argb) {
         int i = next;
         next = (next + 1) % cap;
         if (!alive[i]) {
            count++;
         }
         alive[i] = true;
         x[i] = (float)px;
         y[i] = (float)py;
         z[i] = (float)pz;
         vx[i] = (float)ux;
         vy[i] = (float)uy;
         vz[i] = (float)uz;
         age[i] = 0;
         life[i] = Math.max(1, lifeTicks);
         size[i] = sz;
         grow[i] = growth;
         drag[i] = dr;
         lift[i] = lf;
         color[i] = argb;
      }

      void tick() {
         for (int i = 0; i < cap; i++) {
            if (!alive[i]) {
               continue;
            }
            age[i]++;
            if (age[i] >= life[i]) {
               alive[i] = false;
               count--;
               continue;
            }
            x[i] += vx[i];
            y[i] += vy[i];
            z[i] += vz[i];
            vx[i] *= drag[i];
            vy[i] = vy[i] * drag[i] + lift[i];
            vz[i] *= drag[i];
         }
      }

      void render(BufferBuilder buffer, Vec3 cam, Camera camera, float partial, boolean fadeIn) {
         for (int i = 0; i < cap; i++) {
            if (!alive[i]) {
               continue;
            }
            float t = (age[i] + partial) / life[i];
            if (t >= 1) {
               continue;
            }
            int c = color[i];
            int a = (c >>> 24) & 0xFF;
            float env = fadeIn ? Math.min(1, t * 6) * (1 - t) : (1 - t);
            int alpha = (int)(a * env);
            if (alpha <= 1) {
               continue;
            }
            float s = size[i] + grow[i] * (age[i] + partial);
            PixelVfx.billboardPixel(buffer, cam, camera, new Vec3(x[i] + vx[i] * partial, y[i] + vy[i] * partial, z[i] + vz[i] * partial), s,
               (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, alpha);
         }
      }

      void clear() {
         java.util.Arrays.fill(alive, false);
         count = 0;
      }
   }

   private JinwooVfx() {
   }

   public static int smokeCount() {
      return SMOKE.count;
   }

   static void palm(int entityId, Vec3 at) {
      PALMS.put(entityId, at);
   }

   // ---------------------------------------------------------------- events

   public static void onEvent(int type, int source, int arg, Vec3 a, Vec3 b, int[] ids) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      long now = mc.level.getGameTime();
      switch (type) {
         case JinwooFx.CONTACT -> contact(a, arg);
         case JinwooFx.PARRY -> {
            RINGS.add(new Ring(a, 1.6, now, 8, 4));
            sparks(a, 26, 0.32, arg == 0 ? 0xFFB8FFFF : 0xFFC9A0FF, 8);
            CameraShake.addAt(a, 0.5F, 12.0);
         }
         case JinwooFx.STEP -> step(a, b, source, now);
         case JinwooFx.REMNANT_ADD -> REMNANTS.put(arg, new Remnant(arg, a, (int)b.x, (int)b.y, now));
         case JinwooFx.REMNANT_REMOVE -> {
            REMNANTS.remove(arg);
            if (b.x > 0.5) {
               for (int i = 0; i < 30; i++) {
                  SMOKE.spawn(a.x + gauss(0.3), a.y + 0.1, a.z + gauss(0.3), gauss(0.03), 0.12 + RNG.nextDouble() * 0.1, gauss(0.03), 22, 0.12F, 0.01F, 0.9F, 0.0F,
                     0xC0140C22);
               }
               sparks(a.add(0, 0.6, 0), 18, 0.2, 0xFFA070FF, 14);
            } else if (b.x > -0.5) {
               ash(a);
            }
         }
         case JinwooFx.AUTHORITY -> {
            if (arg == 1) {
               Threads t = new Threads();
               t.ids = ids;
               t.start = now;
               THREADS.put(source, t);
            } else {
               Threads t = THREADS.get(source);
               if (t != null) {
                  t.release = now;
               }
            }
         }
         case JinwooFx.WAVE -> {
            RINGS.add(new Ring(a, b.x, now, arg == 3 ? 18 : 12, arg));
            int n = arg == 3 ? 120 : 40;
            for (int i = 0; i < n; i++) {
               double ang = RNG.nextDouble() * Math.PI * 2;
               double sp = b.x / (arg == 3 ? 16.0 : 9.0);
               SMOKE.spawn(a.x, a.y + 0.15, a.z, Math.cos(ang) * sp, 0.02, Math.sin(ang) * sp, 16 + RNG.nextInt(8), 0.25F, 0.025F, 0.86F, 0.002F,
                  0xB0100A1C);
            }
            CameraShake.addAt(a, arg == 3 ? 1.2F : 0.6F, arg == 3 ? 40 : 16);
         }
         case JinwooFx.LANDING -> {
            RINGS.add(new Ring(a, b.x, now, 10, 1));
            for (int i = 0; i < 16; i++) {
               double ang = RNG.nextDouble() * Math.PI * 2;
               SMOKE.spawn(a.x, a.y + 0.1, a.z, Math.cos(ang) * 0.12, 0.02, Math.sin(ang) * 0.12, 14, 0.18F, 0.02F, 0.85F, 0.0F, 0x90100A1C);
            }
         }
         case JinwooFx.COMMAND -> MARKS.add(new Mark(arg, a, (int)b.x, now));
         case JinwooFx.STREAM -> STREAMS.add(new Stream(a, b, now, 14));
         case JinwooFx.EXCHANGE -> {
            PUDDLES.add(new Puddle(a, now, 26));
            PUDDLES.add(new Puddle(b, now, 26));
         }
         case JinwooFx.EXTRACT_FAIL -> {
            for (int i = 0; i < 18; i++) {
               SMOKE.spawn(a.x + gauss(0.2), a.y + 0.3, a.z + gauss(0.2), gauss(0.05), 0.05, gauss(0.05), 18, 0.15F, 0.015F, 0.85F, 0.0F, 0xB0201828);
            }
            if (arg == 1) {
               ash(a);
            }
         }
         case JinwooFx.CHARGE -> STREAKS.add(new Streak(a, b, now));
         default -> {
         }
      }
   }

   private static void contact(Vec3 a, int blade) {
      int c = blade == 0 ? 0xFF7CF2EE : blade == 1 ? 0xFFB070FF : 0xFFD8C0FF;
      sparks(a, blade == 2 ? 16 : 10, 0.22, c, 7);
      SPARK.spawn(a.x, a.y, a.z, 0, 0, 0, 4, 0.32F, -0.06F, 1, 0, 0xE0FFFFFF);
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.position().distanceToSqr(a) < 36) {
         CameraShake.add(0.25F);
      }
   }

   private static void step(Vec3 from, Vec3 to, int source, long now) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level.getEntity(source);
      float yaw = e != null ? e.getYRot() : 0;
      Vec3 d = to.subtract(from);
      if (d.horizontalDistanceSqr() > 0.01) {
         yaw = (float)(Mth.atan2(d.z, d.x) * (180 / Math.PI)) - 90.0F;
      }
      for (int i = 0; i < 4; i++) {
         AFTERIMAGES.add(new Ghost(from.lerp(to, i / 4.0), yaw, now + i, 10));
      }
      for (int i = 0; i < 40; i++) {
         Vec3 p = from.lerp(to, RNG.nextDouble());
         SMOKE.spawn(p.x + gauss(0.2), p.y + 0.2 + RNG.nextDouble() * 1.4, p.z + gauss(0.2), gauss(0.01), 0.01, gauss(0.01), 14 + RNG.nextInt(8), 0.14F,
            0.012F, 0.9F, 0.0F, 0xA0140C24);
      }
   }

   private static void sparks(Vec3 a, int n, double speed, int color, int life) {
      for (int i = 0; i < n; i++) {
         Vec3 v = new Vec3(gauss(1), gauss(1), gauss(1)).normalize().scale(speed * (0.4 + RNG.nextDouble()));
         SPARK.spawn(a.x, a.y, a.z, v.x, v.y, v.z, life + RNG.nextInt(4), 0.04F, -0.002F, 0.82F, -0.01F, color);
      }
   }

   private static void ash(Vec3 a) {
      for (int i = 0; i < 24; i++) {
         SMOKE.spawn(a.x + gauss(0.3), a.y + 0.8 + RNG.nextDouble() * 0.6, a.z + gauss(0.3), gauss(0.01), -0.03, gauss(0.01), 26, 0.06F, 0.0F, 0.96F,
            -0.002F, 0xD0302838);
      }
   }

   static double gauss(double s) {
      return RNG.nextGaussian() * s;
   }

   // ---------------------------------------------------------------- tick

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      JinwooMotion.tick();
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.isPaused()) {
         return;
      }
      JinwooUltScene.tick(mc);
      tick++;
      long now = level.getGameTime();
      SMOKE.tick();
      SPARK.tick();
      RINGS.removeIf(r -> now - r.start > r.life);
      MARKS.removeIf(m -> now - m.start > 40);
      PUDDLES.removeIf(p -> now - p.start > p.life);
      AFTERIMAGES.removeIf(g -> now - g.start > g.life);
      STREAKS.removeIf(s -> now - s.start > 16);
      THREADS.values().removeIf(t -> t.release >= 0 && now - t.release > 6 || now - t.start > JinwooRules.AUTHORITY_HOLD + 40);
      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      // remnants: dark wisps over the place of death
      for (Remnant r : REMNANTS.values()) {
         if (r.pos.distanceToSqr(cam) > 64 * 64 || (tick + r.id) % 2 != 0) {
            continue;
         }
         SMOKE.spawn(r.pos.x + gauss(0.18), r.pos.y + 0.1, r.pos.z + gauss(0.18), gauss(0.006), 0.035 + RNG.nextDouble() * 0.02, gauss(0.006), 30,
            0.09F, 0.006F, 0.97F, 0.0008F, 0xA0180E2A);
         if (RNG.nextInt(4) == 0) {
            SPARK.spawn(r.pos.x + gauss(0.15), r.pos.y + 0.3, r.pos.z + gauss(0.15), 0, 0.03, 0, 20, 0.025F, 0, 0.98F, 0, 0xFF9C6BFF);
         }
      }
      // streams: smoke flows along the ground from a to b
      Iterator<Stream> it = STREAMS.iterator();
      while (it.hasNext()) {
         Stream s = it.next();
         long age = now - s.start;
         if (age > s.life) {
            it.remove();
            continue;
         }
         for (int k = 0; k < 4; k++) {
            double t = Math.min(1, (age + RNG.nextDouble()) / s.life);
            Vec3 p = s.from.lerp(s.to, t);
            SMOKE.spawn(p.x + gauss(0.15), p.y + 0.08, p.z + gauss(0.15), 0, 0.01, 0, 12, 0.16F, 0.01F, 0.9F, 0, 0xB0100A1C);
         }
      }
      // shadows: ambient smoke trails (LOD 32), sentinel puddles drawn in render
      for (Entity e : level.entitiesForRendering()) {
         if (e instanceof ShadowSoldierEntity s && !s.isInvisible() && s.distanceToSqr(cam) < SMOKE_LOD * SMOKE_LOD) {
            float h = s.getBbHeight();
            int rate = s.walkAnimation.speed() > 0.2F ? 2 : 5;
            if ((tick + s.getId()) % rate == 0) {
               SMOKE.spawn(s.getX() + gauss(0.2), s.getY() + RNG.nextDouble() * h * 0.8, s.getZ() + gauss(0.2), gauss(0.004), 0.025, gauss(0.004), 18,
                  0.07F, 0.008F, 0.95F, 0.001F, 0x90120A20);
            }
            float rise = s.tickCount - s.clientRiseStart;
            if (rise >= 0 && rise < JinwooRules.SHADOW_RISE && rise % 2 == 0) {
               double ang = rise * 0.7 + s.getId();
               for (int k = 0; k < 3; k++) {
                  double a = ang + k * 2.1;
                  SMOKE.spawn(s.getX() + Math.cos(a) * 0.6, s.getY() + rise / 24.0 * h, s.getZ() + Math.sin(a) * 0.6, -Math.sin(a) * 0.05, 0.06,
                     Math.cos(a) * 0.05, 14, 0.12F, 0.01F, 0.92F, 0.0F, 0xC0100A1C);
               }
            }
            if (s.clientDissolveStart >= 0 && rise > 2) {
               for (int k = 0; k < 3; k++) {
                  SMOKE.spawn(s.getX() + gauss(0.3), s.getY() + RNG.nextDouble() * h * 0.6, s.getZ() + gauss(0.3), gauss(0.01), -0.02, gauss(0.01), 16,
                     0.12F, 0.01F, 0.9F, -0.002F, 0xC0100A1C);
               }
            }
         }
      }
      // Jinwoo: shadow gathering into the palms on a draw, eye wisps in the ult
      for (Player p : level.players()) {
         HeroPublicSnapshot s = JinwooView.of(p);
         if (s == null || p.distanceToSqr(cam) > SMOKE_LOD * SMOKE_LOD) {
            continue;
         }
         JinwooMove move = JinwooView.move(s);
         Vec3 palm = PALMS.getOrDefault(p.getId(), p.position().add(0, 1.0, 0));
         if (move == JinwooMove.DRAW && s.actionElapsed() < JinwooRules.DRAW_TICKS - 2) {
            for (int k = 0; k < 3; k++) {
               Vec3 o = new Vec3(gauss(0.5), gauss(0.4), gauss(0.5));
               Vec3 from = palm.add(o);
               SMOKE.spawn(from.x, from.y, from.z, -o.x * 0.18, -o.y * 0.18, -o.z * 0.18, 6, 0.08F, -0.006F, 0.95F, 0, 0xC0100A1C);
            }
         } else if (move == JinwooMove.SHEATH && s.actionElapsed() < 4) {
            for (int k = 0; k < 4; k++) {
               SMOKE.spawn(palm.x, palm.y, palm.z, gauss(0.05), 0.02, gauss(0.05), 12, 0.06F, 0.012F, 0.9F, 0, 0xB0100A1C);
            }
         }
         if (move == JinwooMove.AUTHORITY_GRIP && tick % 2 == 0) {
            SPARK.spawn(palm.x + gauss(0.1), palm.y + gauss(0.1), palm.z + gauss(0.1), 0, 0.01, 0, 10, 0.03F, 0, 0.9F, 0, 0xFFB080FF);
         }
         int domain = JinwooView.domainLeft(s);
         if (domain > 0 && s.actionTarget() != null && tick % 2 == 0) {
            Vec3 c = s.actionTarget();
            double r = JinwooRules.domainRadius(JinwooRules.ULT_DOMAIN - domain);
            for (int k = 0; k < 4; k++) {
               double ang = RNG.nextDouble() * Math.PI * 2;
               double rr = Math.sqrt(RNG.nextDouble()) * r;
               Vec3 at = new Vec3(c.x + Math.cos(ang) * rr, DOMAIN.height(level, c, c.x + Math.cos(ang) * rr, c.z + Math.sin(ang) * rr) + 0.05,
                  c.z + Math.sin(ang) * rr);
               SMOKE.spawn(at.x, at.y, at.z, 0, 0.02, 0, 26, 0.1F, 0.008F, 0.97F, 0.0006F, 0x7010081C);
            }
         }
      }
   }

   // ---------------------------------------------------------------- render

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         renderModels(event);
         return;
      }
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      float partial = event.getPartialTick();
      long now = level.getGameTime();
      PoseStack mv = RenderSystem.getModelViewStack();
      mv.pushPose();
      mv.setIdentity();
      PixelVfx.rotateCamera(mv, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buffer = tess.getBuilder();
      try {
         // pass 1: dark (alpha blended): domain mirror, puddles, smoke
         RenderSystem.defaultBlendFunc();
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Player p : level.players()) {
            HeroPublicSnapshot s = JinwooView.of(p);
            if (s != null && JinwooView.domainLeft(s) > 0 && s.actionTarget() != null) {
               DOMAIN.renderDark(buffer, level, cam, s.actionTarget(), JinwooRules.ULT_DOMAIN - JinwooView.domainLeft(s) + partial, JinwooView.domainLeft(s));
            }
         }
         for (Puddle pd : PUDDLES) {
            float t = (now - pd.start + partial) / pd.life;
            float r = (float)(Math.min(1, t * 4) * 1.3);
            disc(buffer, cam, pd.pos.add(0, 0.03, 0), r, 6, 4, 12, (int)(200 * (1 - Math.max(0, t - 0.6) / 0.4)));
         }
         for (Entity e : level.entitiesForRendering()) {
            if (e instanceof ShadowSoldierEntity s && s.distanceToSqr(cam) < 48 * 48) {
               if (s.isInvisible()) {
                  disc(buffer, cam, s.position().add(0, 0.03, 0), 0.6F, 6, 4, 12, 90);
               } else {
                  float rise = s.tickCount + partial - s.clientRiseStart;
                  if (rise >= 0 && rise < JinwooRules.SHADOW_RISE + 6) {
                     float k = rise < 6 ? rise / 6 : rise > JinwooRules.SHADOW_RISE ? 1 - (rise - JinwooRules.SHADOW_RISE) / 6 : 1;
                     disc(buffer, cam, s.position().add(0, 0.03, 0), 1.1F * Math.min(1, rise / 6 + 0.2F) * s.kind().scale(s.elite()), 6, 4, 12,
                        (int)(210 * k));
                  } else if (s.clientDissolveStart >= 0) {
                     disc(buffer, cam, s.position().add(0, 0.03, 0), 0.9F, 6, 4, 12, 160);
                  }
               }
            }
         }
         SMOKE.render(buffer, cam, camera, partial, true);
         tess.end();

         // pass 2: additive glow
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         SPARK.render(buffer, cam, camera, partial, false);
         DaggerTrails.renderWorld(buffer, cam);
         for (Player p : level.players()) {
            HeroPublicSnapshot s = JinwooView.of(p);
            if (s != null && JinwooView.domainLeft(s) > 0 && s.actionTarget() != null) {
               DOMAIN.renderGlow(buffer, level, cam, camera, s.actionTarget(), JinwooRules.ULT_DOMAIN - JinwooView.domainLeft(s) + partial, now + partial);
            }
         }
         for (Ring r : RINGS) {
            ring(buffer, cam, camera, r, now, partial);
         }
         for (Puddle pd : PUDDLES) {
            float t = (now - pd.start + partial) / pd.life;
            circle(buffer, cam, camera, pd.pos.add(0, 0.05, 0), (float)(Math.min(1, t * 4) * 1.3), 0.05F, 150, 80, 255, (int)(220 * (1 - t)));
         }
         for (Remnant r : REMNANTS.values()) {
            float pulse = 0.6F + 0.4F * (float)Math.sin((now + partial) * 0.2 + r.id);
            PixelVfx.crossGlow(buffer, cam, camera, r.pos.add(0, 0.35, 0), 0.18F * pulse + 0.06F, 150, 90, 255, (int)(150 * pulse));
         }
         threads(buffer, cam, camera, level, now, partial);
         marks(buffer, cam, camera, level, now, partial);
         streaks(buffer, cam, camera, now, partial);
         JinwooUltScene.renderGlow(buffer, cam, camera, partial);
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

   /** Flat disc on the ground (camera-relative), dark centre, given rgb and alpha. */
   static void disc(BufferBuilder b, Vec3 cam, Vec3 c, float r, int red, int green, int blue, int alpha) {
      if (alpha <= 0 || r <= 0.01F) {
         return;
      }
      int seg = 14;
      float cx = (float)(c.x - cam.x), cy = (float)(c.y - cam.y), cz = (float)(c.z - cam.z);
      for (int i = 0; i < seg; i++) {
         double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
         b.vertex(cx, cy, cz).color(red, green, blue, alpha).endVertex();
         b.vertex(cx + Math.cos(a0) * r, cy, cz + Math.sin(a0) * r).color(red, green, blue, alpha / 3).endVertex();
         b.vertex(cx + Math.cos(a1) * r, cy, cz + Math.sin(a1) * r).color(red, green, blue, alpha / 3).endVertex();
         b.vertex(cx, cy, cz).color(red, green, blue, alpha).endVertex();
      }
   }

   /** Thin flat ring on the ground. */
   static void circle(BufferBuilder b, Vec3 cam, Camera camera, Vec3 c, float r, float w, int red, int green, int blue, int alpha) {
      if (alpha <= 0 || r <= 0.02F) {
         return;
      }
      int seg = Math.max(16, (int)(r * 10));
      float cx = (float)(c.x - cam.x), cy = (float)(c.y - cam.y), cz = (float)(c.z - cam.z);
      for (int i = 0; i < seg; i++) {
         double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
         float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0), c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
         b.vertex(cx + c0 * (r - w), cy, cz + s0 * (r - w)).color(red, green, blue, 0).endVertex();
         b.vertex(cx + c0 * r, cy, cz + s0 * r).color(red, green, blue, alpha).endVertex();
         b.vertex(cx + c1 * r, cy, cz + s1 * r).color(red, green, blue, alpha).endVertex();
         b.vertex(cx + c1 * (r - w), cy, cz + s1 * (r - w)).color(red, green, blue, 0).endVertex();
         b.vertex(cx + c0 * r, cy, cz + s0 * r).color(red, green, blue, alpha).endVertex();
         b.vertex(cx + c0 * (r + w), cy, cz + s0 * (r + w)).color(red, green, blue, 0).endVertex();
         b.vertex(cx + c1 * (r + w), cy, cz + s1 * (r + w)).color(red, green, blue, 0).endVertex();
         b.vertex(cx + c1 * r, cy, cz + s1 * r).color(red, green, blue, alpha).endVertex();
      }
   }

   private static void ring(BufferBuilder b, Vec3 cam, Camera camera, Ring r, long now, float partial) {
      float t = Math.min(1, (now - r.start + partial) / r.life);
      float ease = 1 - (1 - t) * (1 - t);
      float radius = (float)(r.radius * ease);
      int alpha = (int)(230 * (1 - t));
      if (r.type == 4) {
         // parry: a vertical flash ring facing the camera
         PixelVfx.crossGlow(b, cam, camera, r.c, 0.5F + radius * 0.4F, 200, 255, 255, alpha);
         return;
      }
      circle(b, cam, camera, r.c.add(0, 0.08, 0), radius, 0.18F + radius * 0.03F, 150, 80, 255, alpha);
      if (r.type == 3) {
         circle(b, cam, camera, r.c.add(0, 0.1, 0), radius * 0.92F, 0.4F, 90, 140, 255, alpha / 2);
      }
   }

   private static void threads(BufferBuilder b, Vec3 cam, Camera camera, ClientLevel level, long now, float partial) {
      for (Map.Entry<Integer, Threads> e : THREADS.entrySet()) {
         Entity owner = level.getEntity(e.getKey());
         if (owner == null) {
            continue;
         }
         Threads t = e.getValue();
         float fade = t.release >= 0 ? 1 - Math.min(1, (now - t.release + partial) / 6.0F) : Math.min(1, (now - t.start + partial) / 4.0F);
         Vec3 palm = PALMS.getOrDefault(owner.getId(), owner.getPosition(partial).add(0, 1.3, 0));
         if (owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            palm = owner.getEyePosition(partial).add(owner.getViewVector(partial).scale(0.6)).add(0, -0.35, 0);
         }
         for (int id : t.ids) {
            Entity target = level.getEntity(id);
            if (target == null) {
               continue;
            }
            Vec3 to = target.getPosition(partial).add(0, target.getBbHeight() * 0.55, 0);
            int steps = (int)Math.max(8, palm.distanceTo(to) * 6);
            for (int k = 0; k <= steps; k++) {
               double u = k / (double)steps;
               double wob = Math.sin(u * Math.PI) * 0.18 * Math.sin((now + partial) * 0.6 + u * 9 + id);
               Vec3 p = palm.lerp(to, u).add(0, wob, wob * 0.5);
               PixelVfx.billboardPixel(b, cam, camera, p, 0.035F, 170, 110, 255, (int)(190 * fade));
            }
            PixelVfx.crossGlow(b, cam, camera, to, 0.35F, 150, 90, 255, (int)(120 * fade));
         }
      }
   }

   private static void marks(BufferBuilder b, Vec3 cam, Camera camera, ClientLevel level, long now, float partial) {
      for (Mark m : MARKS) {
         float t = (now - m.start + partial) / 40.0F;
         int alpha = (int)(230 * (1 - t) * Math.min(1, t * 8));
         Vec3 at = m.pos;
         if (m.target >= 0) {
            Entity e = level.getEntity(m.target);
            if (e != null) {
               at = e.getPosition(partial).add(0, m.order == 0 ? e.getBbHeight() + 0.6 : 0.05, 0);
            }
         }
         switch (m.order) {
            case 0 -> { // attack: red chevron over the target
               float bob = (float)Math.sin((now + partial) * 0.4) * 0.08F;
               for (int k = 0; k < 6; k++) {
                  float o = k * 0.06F;
                  PixelVfx.billboardPixel(b, cam, camera, at.add(-0.3 + o, 0.3 - o + bob, 0), 0.04F, 255, 70, 90, alpha);
                  PixelVfx.billboardPixel(b, cam, camera, at.add(0.3 - o, 0.3 - o + bob, 0), 0.04F, 255, 70, 90, alpha);
               }
               PixelVfx.crossGlow(b, cam, camera, at.add(0, bob, 0), 0.25F, 255, 60, 80, alpha / 2);
            }
            case 1 -> circle(b, cam, camera, at.add(0, 0.05, 0), 3.0F * Math.min(1, t * 4), 0.15F, 120, 150, 255, alpha);
            case 2 -> {
               circle(b, cam, camera, at.add(0, 0.05, 0), 0.9F, 0.1F, 110, 170, 255, alpha);
               PixelVfx.crossGlow(b, cam, camera, at.add(0, 0.1, 0), 0.4F, 110, 170, 255, alpha / 2);
            }
            default -> circle(b, cam, camera, at.add(0, 0.05, 0), 3.0F * (1 - t), 0.1F, 90, 90, 160, alpha / 2);
         }
      }
   }

   private static void streaks(BufferBuilder b, Vec3 cam, Camera camera, long now, float partial) {
      for (Streak s : STREAKS) {
         float t = (now - s.start + partial) / 16.0F;
         Vec3 dir = s.to.subtract(s.from);
         Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
         float head = Math.min(1, t * 1.6F);
         for (int lane = -3; lane <= 3; lane++) {
            Vec3 o = side.scale(lane * 0.6).add(0, 0.6 + Math.abs(lane) * 0.12, 0);
            Vec3 a = s.from.add(o).add(dir.scale(Math.max(0, head - 0.35)));
            Vec3 c = s.from.add(o).add(dir.scale(head));
            PixelVfx.beamDots(b, cam, camera, a, c, 0.25F, 0.05F, 140, 100, 255, (int)(200 * (1 - t)));
         }
      }
   }

   /** Step afterimages and the ult ghost legion (geo models, after the entities). */
   private static void renderModels(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      long now = mc.level.getGameTime();
      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      BakedGeoModel body = AnimCache.model(JinwooView.BODY);
      if (body != null && !AFTERIMAGES.isEmpty()) {
         Animation step = AnimCache.animation(JinwooView.COMBAT, "step");
         for (Ghost g : AFTERIMAGES) {
            float t = (now - g.start + partial) / g.life;
            if (t < 0 || t >= 1) {
               continue;
            }
            // an afterimage around the eye would cover the whole first-person view
            if (g.pos.add(0, 1.4, 0).distanceToSqr(cam) < 2.6 * 2.6) {
               continue;
            }
            body.resetBones();
            if (step != null) {
               AnimationController.overlay(step, body, 0.15, 1.0F);
            }
            stack.pushPose();
            stack.translate(g.pos.x - cam.x, g.pos.y - cam.y, g.pos.z - cam.z);
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - g.yaw));
            stack.scale(JinwooRenderer.PLAYER_SCALE, JinwooRenderer.PLAYER_SCALE, JinwooRenderer.PLAYER_SCALE);
            float a = 0.45F * (1 - t);
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.entityTranslucent(JinwooView.SKIN)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 0.25F, 0.18F, 0.45F, a, null);
            stack.popPose();
         }
      }
      JinwooUltScene.renderModels(stack, buffers, cam, partial);
      buffers.endBatch();
   }

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         clear();
      }
   }

   public static void clear() {
      SMOKE.clear();
      SPARK.clear();
      REMNANTS.clear();
      RINGS.clear();
      MARKS.clear();
      STREAMS.clear();
      PUDDLES.clear();
      AFTERIMAGES.clear();
      STREAKS.clear();
      THREADS.clear();
      PALMS.clear();
      DOMAIN.reset();
      DaggerTrails.clear();
      JinwooUltScene.clear();
   }

   /**
    * The Monarch's domain: a black mirror over the terrain (one quad per block column top, heights
    * cached per centre) with purple veins, and an edge ring.
    */
   static final class Domain {
      static final int R = (int)Math.ceil(JinwooRules.ULT_RADIUS) + 1;
      static final int N = 2 * R + 1;
      private final float[] heights = new float[N * N];
      private Vec3 center;

      void reset() {
         center = null;
      }

      private void ensure(ClientLevel level, Vec3 c) {
         if (center != null && center.distanceToSqr(c) < 0.01) {
            return;
         }
         center = c;
         int bx = Mth.floor(c.x), bz = Mth.floor(c.z), by = Mth.floor(c.y);
         for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
               heights[i * N + j] = surface(level, bx + i - R, by, bz + j - R);
            }
         }
      }

      static float surface(ClientLevel level, int x, int y, int z) {
         BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
         for (int dy = 4; dy >= -6; dy--) {
            p.set(x, y + dy, z);
            if (level.getBlockState(p).isFaceSturdy(level, p, Direction.UP) && !level.getBlockState(p.above()).isFaceSturdy(level, p.above(), Direction.DOWN)) {
               return y + dy + 1;
            }
         }
         return Float.NaN;
      }

      double height(ClientLevel level, Vec3 c, double x, double z) {
         ensure(level, c);
         int i = Mth.floor(x) - Mth.floor(c.x) + R, j = Mth.floor(z) - Mth.floor(c.z) + R;
         if (i < 0 || j < 0 || i >= N || j >= N || Float.isNaN(heights[i * N + j])) {
            return c.y;
         }
         return heights[i * N + j];
      }

      void renderDark(BufferBuilder b, ClientLevel level, Vec3 cam, Vec3 c, float elapsed, int left) {
         ensure(level, c);
         double r = JinwooRules.domainRadius((int)elapsed);
         float fadeOut = Math.min(1, left / 40.0F);
         int bx = Mth.floor(c.x), bz = Mth.floor(c.z);
         for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
               float h = heights[i * N + j];
               if (Float.isNaN(h)) {
                  continue;
               }
               int x = bx + i - R, z = bz + j - R;
               double d = Math.sqrt((x + 0.5 - c.x) * (x + 0.5 - c.x) + (z + 0.5 - c.z) * (z + 0.5 - c.z));
               if (d > r) {
                  continue;
               }
               float edge = (float)Math.min(1, (r - d) / 2.0);
               int a = (int)(165 * edge * fadeOut);
               float y = (float)(h + 0.015 - cam.y);
               float x0 = (float)(x - cam.x), z0 = (float)(z - cam.z);
               b.vertex(x0, y, z0).color(6, 3, 12, a).endVertex();
               b.vertex(x0, y, z0 + 1).color(6, 3, 12, a).endVertex();
               b.vertex(x0 + 1, y, z0 + 1).color(6, 3, 12, a).endVertex();
               b.vertex(x0 + 1, y, z0).color(6, 3, 12, a).endVertex();
            }
         }
      }

      void renderGlow(BufferBuilder b, ClientLevel level, Vec3 cam, Camera camera, Vec3 c, float elapsed, float time) {
         ensure(level, c);
         double r = JinwooRules.domainRadius((int)elapsed);
         // veins: 7 crooked rays from the centre, pulsing outward
         for (int ray = 0; ray < 7; ray++) {
            double base = ray * Math.PI * 2 / 7 + 0.4;
            for (double d = 0.6; d < r; d += 0.35) {
               double ang = base + Math.sin(d * 0.7 + ray) * 0.25;
               double x = c.x + Math.cos(ang) * d, z = c.z + Math.sin(ang) * d;
               float pulse = 0.5F + 0.5F * (float)Math.sin(d * 0.8 - time * 0.25);
               Vec3 p = new Vec3(x, height(level, c, x, z) + 0.03, z);
               PixelVfx.billboardPixel(b, cam, camera, p, 0.05F, 150, 70, 255, (int)(60 + 120 * pulse));
            }
         }
         circle(b, cam, camera, new Vec3(c.x, c.y + 0.05, c.z), (float)r, 0.35F, 150, 80, 255, 200);
      }
   }

   static ShadowKind kindOf(int ordinal) {
      return ShadowKind.byOrdinal(ordinal);
   }
}

package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Every light of THE FINAL PUNCH, drawn on the rendered right hand (FinalPunchClient#recordFist)
 * and around the caster: sparks, the fire shell, the orbiting rings and their pulses, electric
 * arcs, light pouring in, the small sun (compressed in the silence), the strike trail, the
 * impact (flash, radial streaks, shock ring, burst) and the smoke of the lowered fist. Phase IV
 * chaos: vortex speed streaks, ground air waves, giant energy rings and client debris shards that
 * hang in the air through the silence (visual slow time) and are blown away by the punch. The
 * world itself is never edited: no block is broken, nothing here touches the server.
 */
public final class FinalPunchVfx {
   static final double RANGE = 96.0;
   static final int MAX_PARTICLES = 420;
   static final int MAX_SHARDS = 72;
   private static final int[] WHITE = {255, 252, 240};
   private static final int[] GOLD = {255, 214, 120};
   private static final int[] ORANGE = {255, 140, 40};
   private static final int[] RED = {220, 50, 16};
   private static final int[] PALE = {220, 236, 255};

   enum Kind {
      SPARK,
      INFLOW,
      EMBER,
      BURST,
      SMOKE
   }

   static final class Particle {
      Kind kind;
      Vec3 pos;
      Vec3 vel;
      double age;
      double life;
      double size;
   }

   static final class Shard {
      double radius;
      double angle;
      double height;
      double rise;
      double omega;
      double spin;
      double spinRate;
      double size;
      int r;
      int g;
      int b;
      Vec3 blast;
      Vec3 pos;
   }

   /** The scene's own particles (client only, capped). */
   static final class State {
      final List<Particle> particles = new ArrayList<>();
      final List<Shard> shards = new ArrayList<>();
      double vortexClock;
      int spawned;

      void clear() {
         this.particles.clear();
         this.shards.clear();
      }
   }

   private FinalPunchVfx() {
   }

   // ------------------------------------------------------------------ simulation

   static void tick(FinalPunchClient.Scene scene, ClientLevel level) {
      State s = scene.vfx;
      double t = scene.ticks;
      double dt = FinalPunchCues.timeScale(t);
      s.vortexClock += dt * (0.4 + FinalPunchCues.storm(t));
      Vec3 fist = scene.fist(0.0F);
      long seed = scene.seed + scene.ticks * 7919L;
      int n = 0;
      if (!scene.cancelled() && dt > 0) {
         int sparks = (int) Math.round(FinalPunchCues.sparks(t) * (1.5 + 2.5 * FinalPunchCues.charge(t)));
         for (int i = 0; i < sparks; i++) spawn(s, Kind.SPARK, fist, randomDir(seed, n++).scale(0.08 + 0.16 * FinalPunchCues.hash(seed, n++)).add(0, 0.05, 0), 8 + 8 * FinalPunchCues.hash(seed, n++), 0.03);
         double fire = FinalPunchCues.fire(t);
         if (fire > 0 && t < FinalPunchRules.CONTACT) {
            int embers = (int) Math.round(fire * 2.0);
            for (int i = 0; i < embers; i++) spawn(s, Kind.EMBER, fist.add(randomDir(seed, n++).scale(0.12)), new Vec3(0, 0.03 + 0.04 * FinalPunchCues.hash(seed, n++), 0).add(randomDir(seed, n++).scale(0.015)), 10 + 10 * FinalPunchCues.hash(seed, n++), 0.05);
         }
         double inflow = FinalPunchCues.concentration(t);
         if (inflow > 0) {
            int count = (int) Math.round(1 + 4 * inflow);
            for (int i = 0; i < count; i++) {
               Vec3 from = fist.add(randomDir(seed, n++).scale(1.8 + 1.4 * FinalPunchCues.hash(seed, n++)));
               double life = 9 + 4 * FinalPunchCues.hash(seed, n++);
               spawn(s, Kind.INFLOW, from, fist.subtract(from).scale(1.0 / life), life, 0.05);
            }
         }
         double smoke = FinalPunchCues.smoke(t);
         if (smoke > 0 && scene.ticks % 2 == 0) {
            spawn(s, Kind.SMOKE, fist.add(randomDir(seed, n++).scale(0.1)), new Vec3(0, 0.025, 0).add(randomDir(seed, n++).scale(0.01)), 30 + 14 * FinalPunchCues.hash(seed, n++), 0.12);
         }
         shards(scene, s, level, t, seed);
      }

      for (Iterator<Particle> it = s.particles.iterator(); it.hasNext(); ) {
         Particle p = it.next();
         p.age += dt;
         p.pos = p.pos.add(p.vel.scale(dt));
         switch (p.kind) {
            case SPARK, BURST -> p.vel = p.vel.scale(Math.pow(0.93, dt)).add(0, -0.012 * dt, 0);
            case EMBER -> p.vel = p.vel.scale(Math.pow(0.97, dt));
            case SMOKE -> {
               p.vel = p.vel.scale(Math.pow(0.97, dt));
               p.size += 0.012 * dt;
            }
            default -> {
            }
         }
         if (p.age >= p.life) it.remove();
      }

      for (Shard shard : s.shards) {
         if (t >= FinalPunchRules.LAUNCH && shard.blast != null) {
            shard.pos = shard.pos.add(shard.blast);
            shard.blast = shard.blast.scale(0.9).add(0, -0.04, 0);
         } else if (dt > 0) {
            shard.angle += shard.omega * dt * (0.5 + FinalPunchCues.storm(Math.min(t, FinalPunchRules.SILENCE - 1)));
            shard.height = Math.min(shard.rise, shard.height + 0.05 * dt);
            shard.radius = Math.max(1.6, shard.radius - 0.01 * dt);
         }
         shard.spin += shard.spinRate * dt;
      }
   }

   private static void shards(FinalPunchClient.Scene scene, State s, ClientLevel level, double t, long seed) {
      if (t < FinalPunchRules.WARP || t >= FinalPunchRules.SILENCE || s.shards.size() >= MAX_SHARDS) return;
      int want = (int) Math.round(MAX_SHARDS * FinalPunchCues.storm(t));
      Vec3 c = scene.feet();
      for (int i = 0; s.shards.size() < want && i < 3; i++) {
         int k = s.spawned++;
         Shard shard = new Shard();
         shard.radius = 2.0 + 5.5 * FinalPunchCues.hash(scene.seed, 1000 + k * 5);
         shard.angle = Math.PI * 2 * FinalPunchCues.hash(scene.seed, 1001 + k * 5);
         shard.height = 0.05;
         shard.rise = 0.4 + 3.8 * FinalPunchCues.hash(scene.seed, 1002 + k * 5);
         shard.omega = 0.05 + 0.07 * FinalPunchCues.hash(scene.seed, 1003 + k * 5);
         shard.spinRate = 0.2 + 0.4 * FinalPunchCues.hash(scene.seed, 1004 + k * 5);
         shard.size = 0.05 + 0.1 * FinalPunchCues.hash(scene.seed, 2000 + k);
         Vec3 at = c.add(Math.cos(shard.angle) * shard.radius, -0.5, Math.sin(shard.angle) * shard.radius);
         BlockPos pos = BlockPos.containing(at);
         BlockState state = level.getBlockState(pos);
         int rgb = state.isAir() ? 0x7A6A55 : state.getMapColor(level, pos).col;
         if (rgb == 0) rgb = 0x7A6A55;
         shard.r = rgb >> 16 & 255;
         shard.g = rgb >> 8 & 255;
         shard.b = rgb & 255;
         s.shards.add(shard);
      }
   }

   private static void spawn(State s, Kind kind, Vec3 pos, Vec3 vel, double life, double size) {
      if (s.particles.size() >= MAX_PARTICLES) return;
      Particle p = new Particle();
      p.kind = kind;
      p.pos = pos;
      p.vel = vel;
      p.life = life;
      p.size = size;
      s.particles.add(p);
   }

   static Vec3 randomDir(long seed, int i) {
      double u = FinalPunchCues.hash(seed, i * 3) * 2 - 1;
      double a = FinalPunchCues.hash(seed, i * 3 + 1) * Math.PI * 2;
      double r = Math.sqrt(Math.max(0, 1 - u * u));
      return new Vec3(r * Math.cos(a), u, r * Math.sin(a));
   }

   /** The impact: burst sparks, the shards' blast, the caster's controlled shake and flash. */
   static void impact(FinalPunchClient.Scene scene) {
      State s = scene.vfx;
      Vec3 at = scene.impactAt != null ? scene.impactAt : scene.jawAt;
      long seed = scene.seed ^ 0x5EEDL;
      for (int i = 0; i < 90; i++) {
         Vec3 d = randomDir(seed, i);
         Vec3 v = d.add(scene.dir.scale(1.2)).normalize().scale(0.25 + 0.55 * FinalPunchCues.hash(seed, 500 + i));
         spawn(s, Kind.BURST, at, v, 10 + 14 * FinalPunchCues.hash(seed, 900 + i), 0.04);
      }
      Vec3 c = scene.feet();
      int k = 0;
      for (Shard shard : s.shards) {
         shard.pos = c.add(Math.cos(shard.angle) * shard.radius, shard.height, Math.sin(shard.angle) * shard.radius);
         Vec3 out = new Vec3(Math.cos(shard.angle), 0, Math.sin(shard.angle));
         shard.blast = out.scale(0.5 + 0.6 * FinalPunchCues.hash(seed, 3000 + k)).add(scene.dir.scale(0.3)).add(0, 0.15 + 0.2 * FinalPunchCues.hash(seed, 3100 + k), 0);
         k++;
      }
      Minecraft mc = Minecraft.getInstance();
      Vec3 view = mc.gameRenderer.getMainCamera().getPosition();
      double d = view.distanceTo(at);
      if (scene.isLocalCaster() || d < 24) {
         // Strong but controlled: one cinematic hit, falling off with distance for onlookers.
         float power = scene.isLocalCaster() ? 1.0F : (float) Math.max(0.0, 1.0 - d / 24.0);
         CameraShake.cinematicAt(at, 2.6F * power, 32.0);
         FlashOverlay.flash(0.55F * power, 6);
      }
   }

   // ------------------------------------------------------------------ render

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
      FinalPunchClient.nextFrame();
      if (dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass() || !FinalPunchClient.any()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      Camera camera = event.getCamera();
      Vec3 eye = camera.getPosition();
      float partial = event.getPartialTick();
      List<FinalPunchClient.Scene> scenes = new ArrayList<>();
      for (FinalPunchClient.Scene scene : FinalPunchClient.all()) {
         if (scene.feet().distanceToSqr(eye) < RANGE * RANGE && scene.fade(partial) > 0.002F) scenes.add(scene);
      }
      if (scenes.isEmpty()) return;

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
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         FinalPunchDraw draw = new FinalPunchDraw(buffer, eye, camera.getLeftVector(), camera.getUpVector());
         RenderSystem.defaultBlendFunc();
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (FinalPunchClient.Scene scene : scenes) {
            draw.fade = scene.fade(partial);
            solid(draw, scene, partial);
         }
         tesselator.end();

         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (FinalPunchClient.Scene scene : scenes) {
            draw.fade = scene.fade(partial);
            light(draw, scene, partial);
         }
         tesselator.end();
      } finally {
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         RenderSystem.enableCull();
         RenderSystem.depthMask(true);
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   /** Alpha-blended pass: debris shards, smoke. */
   private static void solid(FinalPunchDraw d, FinalPunchClient.Scene scene, float partial) {
      double t = scene.time(partial);
      double debris = FinalPunchCues.debris(t);
      if (debris > 0) {
         Vec3 c = scene.feet();
         for (Shard shard : scene.vfx.shards) {
            Vec3 p = shard.pos != null && t >= FinalPunchRules.CONTACT ? shard.pos
               : c.add(Math.cos(shard.angle) * shard.radius, shard.height + 0.08 * Math.sin(shard.spin), Math.sin(shard.angle) * shard.radius);
            d.shard(p, shard.size, shard.spin, shard.spin * 0.7, shard.r, shard.g, shard.b, (float) debris);
         }
      }
      for (Particle p : scene.vfx.particles) {
         if (p.kind != Kind.SMOKE) continue;
         double life = p.age / p.life;
         float a = (float) (0.32 * Math.sin(Math.PI * Math.min(1.0, life)) * FinalPunchCues.smoke(t));
         d.glow(p.pos, p.size, 92, 90, 96, a);
      }
   }

   /** Additive pass: every light. */
   private static void light(FinalPunchDraw d, FinalPunchClient.Scene scene, float partial) {
      double t = scene.time(partial);
      Vec3 fist = scene.fist(partial);
      Vec3 toTarget = scene.jawAt.subtract(fist);
      Vec3 axis = toTarget.lengthSqr() > 1.0E-4 ? toTarget.normalize() : scene.dir;
      chaos(d, scene, t);
      fist(d, scene, t, fist, axis);
      particles(d, scene, t);
      impactLight(d, scene, t);
   }

   private static void fist(FinalPunchDraw d, FinalPunchClient.Scene scene, double t, Vec3 fist, Vec3 axis) {
      double charge = FinalPunchRules.charge(Math.min(t, FinalPunchRules.CONTACT - 1));
      double after = FinalPunchCues.afterglow(t);
      double compress = FinalPunchCues.compress(t);
      double flicker = 0.85 + 0.15 * Math.sin(t * 1.7) * Math.sin(t * 0.63 + 1.0);
      // Core heat of the fist (from the first sparks to the lowered fist).
      if (t >= FinalPunchRules.SPARKS) {
         double heat = Math.min(1.0, 0.25 + charge) * after;
         d.glow(fist, (0.16 + 0.32 * charge) * flicker * (1 - 0.4 * compress), 255, 170, 70, (float) (0.55 * heat));
      }

      double fire = FinalPunchCues.fire(t);
      if (fire > 0) {
         int flames = 6 + (int) Math.round(10 * fire);
         for (int i = 0; i < flames; i++) {
            double a = Math.PI * 2 * i / flames + t * 0.05;
            double h1 = FinalPunchCues.hash(scene.seed, 40 + i);
            Vec3 radial = d.left.scale(Math.cos(a)).add(d.up.scale(Math.sin(a)));
            Vec3 base = fist.add(radial.scale(0.07 + 0.05 * h1));
            double lick = 0.5 + 0.5 * Math.sin(t * (0.6 + 0.5 * h1) + h1 * 20);
            double h = (0.16 + 0.3 * fire * (0.6 + 0.4 * lick)) * (1 - 0.6 * compress);
            Vec3 dir = new Vec3(0, 1, 0).scale(0.85).add(radial.scale(0.55)).normalize();
            Vec3 tip = base.add(dir.scale(h)).add(d.left.scale(0.04 * Math.sin(t * 0.9 + i)));
            d.flame(base, tip, h * 0.5, i % 3 == 0 ? WHITE : GOLD, i % 2 == 0 ? ORANGE : RED, (float) (0.75 * fire));
         }
         d.glow(fist, 0.45 * fire * (1 - 0.5 * compress), 255, 110, 30, (float) (0.35 * fire));
      }

      double rings = FinalPunchCues.rings(t);
      if (rings > 0) {
         int count = FinalPunchCues.ringCount(t);
         for (int j = 0; j < count; j++) {
            double spin = t * (0.05 + 0.025 * j) + j * 2.1;
            Vec3 n = new Vec3(Math.cos(spin), 0.6 * Math.sin(spin * 0.7 + j), Math.sin(spin)).normalize();
            Vec3 u = FinalPunchDraw.perpendicular(n);
            Vec3 v = n.cross(u).normalize();
            int[] col = j == 0 ? GOLD : j == 1 ? WHITE : new int[]{150, 200, 255};
            double radius = (0.26 + 0.1 * j) * (1 + 0.15 * charge) * (1 - 0.35 * compress);
            d.ring(fist, u, v, radius, 0.035, 28, col[0], col[1], col[2], (float) (0.8 * rings), 3, t * 0.4 * (j + 1));
         }
      }

      double pulse = FinalPunchCues.ringPulseAge(t);
      if (pulse >= 0 && t < FinalPunchRules.SILENCE) {
         double k = pulse / FinalPunchCues.RING_PULSE_TICKS;
         Vec3 u = FinalPunchDraw.perpendicular(axis);
         Vec3 v = axis.cross(u).normalize();
         d.ring(fist, u, v, 0.3 + 2.4 * k, 0.12 + 0.2 * k, 40, 255, 226, 160, (float) ((1 - k) * 0.7 * (0.5 + 0.5 * charge)));
      }

      double arcs = FinalPunchCues.arcs(t);
      if (arcs > 0) {
         int count = 2 + (int) Math.round(6 * arcs);
         long s = scene.seed + (long) Math.floor(t / 2) * 31L;
         for (int i = 0; i < count; i++) {
            Vec3 to = fist.add(FinalPunchVfx.randomDir(s, i).scale(0.35 + 1.1 * arcs * FinalPunchCues.hash(s, 70 + i)));
            d.arc(fist, to, s + i, 7, 0.22, 0.018, (float) (0.9 * arcs));
         }
      }

      double sun = FinalPunchCues.sun(t);
      if (sun > 0) {
         double size = (0.2 + 0.12 * sun) * (1 - 0.35 * compress);
         d.glow(fist, (0.9 + 1.5 * sun) * (1 - 0.6 * compress) * flicker, 255, 220, 130, (float) (0.38 * sun));
         int rays = 14;
         for (int i = 0; i < rays; i++) {
            double a = Math.PI * 2 * i / rays + t * 0.02;
            double len = (0.5 + 0.7 * FinalPunchCues.hash(scene.seed, 200 + i)) * sun * (1 - 0.7 * compress) * (0.8 + 0.2 * Math.sin(t * 0.5 + i));
            Vec3 dir = d.left.scale(Math.cos(a)).add(d.up.scale(Math.sin(a)));
            d.streak(fist.add(dir.scale(size * 0.6)), fist.add(dir.scale(size + len)), 0.07, 0.0, GOLD, (float) (0.45 * sun), ORANGE, 0.0F);
         }
         d.disc(fist, size, 0.45, 255, 252, 236, (float) Math.min(1.0, 0.9 * sun + 0.3 * compress));
         d.glow(fist, size * 2.2, 255, 255, 255, (float) (0.5 * sun + 0.4 * compress));
      }

      double trail = FinalPunchCues.trail(t);
      List<Vec3> points = scene.trail();
      if (trail > 0 && points.size() > 1) {
         for (int i = 1; i < points.size(); i++) {
            double k0 = (double) (i - 1) / (points.size() - 1);
            double k1 = (double) i / (points.size() - 1);
            d.streak(points.get(i - 1), points.get(i), 0.3 * k0, 0.3 * k1, GOLD, (float) (0.5 * trail * k0), WHITE, (float) (0.7 * trail * k1));
         }
         d.streak(points.get(points.size() - 1), fist, 0.3, 0.3, WHITE, (float) (0.7 * trail), WHITE, (float) (0.8 * trail));
      }
   }

   private static void particles(FinalPunchDraw d, FinalPunchClient.Scene scene, double t) {
      for (Particle p : scene.vfx.particles) {
         double life = Math.min(1.0, p.age / p.life);
         switch (p.kind) {
            case SPARK, BURST -> {
               Vec3 tail = p.pos.subtract(p.vel.scale(p.kind == Kind.BURST ? 2.5 : 1.6));
               float a = (float) (1 - life);
               d.streak(tail, p.pos, 0.0, p.kind == Kind.BURST ? 0.06 : 0.035, ORANGE, 0.0F, WHITE, a);
            }
            case INFLOW -> {
               Vec3 tail = p.pos.subtract(p.vel.scale(3.0));
               float a = (float) (0.7 * Math.sin(Math.PI * life));
               d.streak(tail, p.pos, 0.0, 0.04, GOLD, 0.0F, WHITE, a);
            }
            case EMBER -> d.glow(p.pos, p.size * (1 - life), 255, 150, 50, (float) (0.8 * (1 - life)));
            default -> {
            }
         }
      }
   }

   private static void chaos(FinalPunchDraw d, FinalPunchClient.Scene scene, double t) {
      double storm = FinalPunchCues.storm(t);
      double hang = t >= FinalPunchRules.SILENCE && t < FinalPunchRules.CONTACT ? 0.25 * FinalPunchCues.debris(t) : 0.0;
      Vec3 c = scene.feet();
      double level = Math.max(storm, hang);
      if (level <= 0) return;
      // Vortex speed streaks around Reinhard (frozen, faint, in the silence).
      int streaks = 10 + (int) Math.round(30 * storm);
      double clock = scene.vfx.vortexClock;
      for (int i = 0; i < streaks; i++) {
         double r = 1.8 + 6.0 * FinalPunchCues.hash(scene.seed, 300 + i);
         double y = 0.1 + 3.8 * FinalPunchCues.hash(scene.seed, 400 + i);
         double w = 0.16 + 0.22 * FinalPunchCues.hash(scene.seed, 500 + i);
         double a0 = Math.PI * 2 * FinalPunchCues.hash(scene.seed, 600 + i) + w * clock * 1.6;
         double span = 0.35 + 0.45 * FinalPunchCues.hash(scene.seed, 700 + i);
         Vec3 prev = null;
         for (int k = 0; k <= 4; k++) {
            double a = a0 - span * k / 4.0;
            Vec3 p = c.add(Math.cos(a) * r, y + 0.15 * Math.sin(a * 2 + i), Math.sin(a) * r);
            if (prev != null) {
               float head = (float) (level * 0.55 * (1 - (k - 1) / 4.0));
               float tail = (float) (level * 0.55 * (1 - k / 4.0));
               d.streak(prev, p, 0.05, 0.05, PALE, head, PALE, tail);
            }
            prev = p;
         }
      }
      if (storm <= 0) return;
      // Ground air waves.
      for (int k = 0; k < 10; k++) {
         double age = FinalPunchCues.airWaveAge(t, k);
         if (age < 0) continue;
         double f = age / FinalPunchCues.AIR_WAVE_TICKS;
         d.ring(c.add(0, 0.06, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.0 + 9.0 * f, 0.45, 48, 230, 240, 255, (float) ((1 - f) * 0.4 * storm));
      }
      // Giant energy rings around him.
      double[] heights = {0.4, 1.5, 2.7};
      double[] radii = {3.4, 4.7, 6.0};
      for (int j = 0; j < 3; j++) {
         double tilt = 0.12 * Math.sin(t * 0.05 + j * 2.0);
         Vec3 u = new Vec3(Math.cos(tilt), Math.sin(tilt), 0);
         Vec3 v = new Vec3(0, Math.sin(tilt * 0.7), Math.cos(tilt * 0.7));
         int[] col = j == 1 ? WHITE : GOLD;
         d.ring(c.add(0, heights[j], 0), u, v, radii[j] * (0.7 + 0.3 * storm), 0.18, 64, col[0], col[1], col[2], (float) (0.32 * storm), 5, (j % 2 == 0 ? 1 : -1) * t * 0.08);
      }
   }

   private static void impactLight(FinalPunchDraw d, FinalPunchClient.Scene scene, double t) {
      double age = FinalPunchCues.impact(t);
      if (age < 0 || !scene.impacted()) return;
      Vec3 at = scene.impactAt != null ? scene.impactAt : scene.jawAt;
      boolean held = t < FinalPunchRules.LAUNCH;
      double k = held ? 0.0 : (t - FinalPunchRules.LAUNCH) / (40.0 - FinalPunchRules.HIT_STOP_TICKS);
      double ease = 1 - Math.pow(1 - Math.min(1.0, k), 3);
      float fadeOut = (float) (1 - Math.min(1.0, k));
      // The flash: frozen big in the hit-stop, then it blows out and fades.
      d.glow(at, held ? 1.8 : 1.8 + 3.5 * ease, 255, 250, 235, held ? 0.95F : 0.9F * fadeOut * fadeOut);
      d.disc(at, held ? 0.45 : 0.45 * (1 - ease), 0.5, 255, 255, 255, held ? 1.0F : fadeOut);
      // Radial streaks, mostly along the punch.
      for (int i = 0; i < 44; i++) {
         Vec3 dir = randomDir(scene.seed ^ 0xABCL, i).add(scene.dir.scale(i % 3 == 0 ? 0.0 : 1.4)).normalize();
         double r0 = held ? 0.3 : 0.3 + 5.0 * ease;
         double len = (0.8 + 1.6 * FinalPunchCues.hash(scene.seed, 800 + i)) * (held ? 0.6 : 1 - 0.5 * ease);
         d.streak(at.add(dir.scale(r0)), at.add(dir.scale(r0 + len)), 0.08, 0.0, WHITE, 0.85F * fadeOut, GOLD, 0.0F);
      }
      // The shock ring (horizontal) and a vertical halo across the punch.
      d.ring(at, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.6 + (held ? 0.4 : 11.0 * ease), 0.8, 56, 255, 236, 200, 0.6F * fadeOut);
      Vec3 u = FinalPunchDraw.perpendicular(scene.dir);
      Vec3 v = scene.dir.cross(u).normalize();
      d.ring(at, u, v, 0.5 + (held ? 0.3 : 4.0 * ease), 0.35, 48, 255, 255, 255, 0.7F * fadeOut);
   }
}

package dev.baranhan.viltrumitecore.client.godzilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client side of the kaiju event: the one-shot effects of {@link GodzillaFxS2CPacket} and the hot zones the breath
 * leaves. Everything uses the mod's own smoke, dust, ember and spark particles and additive geometry; counts fall
 * with the camera distance ({@link #detail}) and a per-tick particle budget keeps a rampage from flooding the
 * particle engine. Footfalls, stomps and falls shake the camera mildly through {@link CameraShake} (scaled by the
 * player's shake setting). Hot zones glow on the ground, smoulder and throw embers until they cool down or the
 * event clears them. Sounds: {@link GodzillaAudio}.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class GodzillaClient {
   /** Particles the event may start per client tick (all effects together). */
   public static final int PARTICLE_BUDGET = 220;
   /** Hot zones kept at once (oldest dropped first; close ones merge). */
   public static final int MAX_HOT = 24;
   /** Hot zones farther than this from the camera neither smoke nor glow. */
   public static final double HOT_RANGE = 320.0;
   /** Strongest shake any kaiju effect asks for (the fall); the walk stays well below it. */
   public static final float MAX_SHAKE = 1.1F;

   private static final List<Hot> HOT = new ArrayList<>();
   private static int budget = PARTICLE_BUDGET;
   private static int lastHit = -100;
   private static int clock;
   private static ClientLevel lastLevel;

   private GodzillaClient() {
   }

   /** Pure: share of the full effect drawn at a camera distance (near / district / far). */
   public static float detail(double distance) {
      if (distance <= GodzillaRules.FX_NEAR) {
         return 1.0F;
      }
      if (distance <= GodzillaRules.FX_FAR) {
         return 0.35F;
      }
      return 0.12F;
   }

   /** Pure: camera shake of a footfall at a distance (mild: a felt thud near the feet, nothing across the city). */
   public static float stepShake(double distance, float power) {
      return Math.min(0.55F, 0.45F * power * CameraShake.falloff(distance, 150.0));
   }

   public static void accept(GodzillaFxS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      Vec3 at = p.at();
      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      double d = cam.distanceTo(at);
      GodzillaAudio.fx(p, d);
      float detail = detail(d);
      RandomSource r = mc.level.random;
      switch (p.kind()) {
         case GodzillaFxS2CPacket.SMASH -> smash(mc, r, at, p.power(), p.extra(), detail);
         case GodzillaFxS2CPacket.STOMP -> stomp(mc, r, at, p.power(), detail);
         case GodzillaFxS2CPacket.TAIL_HIT -> tailHit(mc, r, at, p.d(), p.power(), detail);
         case GodzillaFxS2CPacket.CLAW_HIT -> clawHit(mc, r, at, p.d(), detail);
         case GodzillaFxS2CPacket.FALL_IMPACT -> fall(mc, r, at, p.d(), p.power(), detail);
         case GodzillaFxS2CPacket.HOT -> hot(at, p.power(), p.extra());
         case GodzillaFxS2CPacket.BEAM_BLAST -> blast(mc, r, at, p.d(), p.power(), detail);
         case GodzillaFxS2CPacket.ROAR_WAVE -> roar(mc, r, at, p.d(), p.power(), detail);
         case GodzillaFxS2CPacket.DEATH -> {
            if (p.power() > 0.0F) {
               fall(mc, r, at, p.d(), p.power(), detail);
            } else {
               haze(mc, r, at, 40.0F, detail);
            }
         }
         case GodzillaFxS2CPacket.RETREAT -> {
            if (p.power() < 0.0F) {
               haze(mc, r, at, 30.0F, detail);
            }
         }
         case GodzillaFxS2CPacket.ARRIVE -> {
            CameraShake.addAt(at, 0.5F, 700.0);
            haze(mc, r, at, 26.0F, detail);
         }
         case GodzillaFxS2CPacket.CLEAR -> clear();
         case GodzillaFxS2CPacket.HIT -> hit(mc, r, at, p.power(), detail);
         case GodzillaFxS2CPacket.RISE -> rise(mc, r, at, detail);
         case GodzillaFxS2CPacket.STEP -> step(mc, r, at, p.power(), d, detail);
         default -> {
         }
      }
   }

   // ------------------------------------------------------------------ one-shots

   private static void smash(Minecraft mc, RandomSource r, Vec3 at, float radius, int blocks, float detail) {
      int n = count(6 + (int)(Math.sqrt(Math.max(0, blocks)) * 1.2), detail, 48);
      float rad = Math.max(2.0F, radius);
      for (int i = 0; i < n; i++) {
         Vec3 p = at.add(sphere(r, rad));
         Vec3 v = p.subtract(at).normalize().scale(0.18 + r.nextDouble() * 0.3).add(0.0, 0.05 + r.nextDouble() * 0.1, 0.0);
         spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), p, v, 2.0F + rad * 0.12F + r.nextFloat());
      }
      for (int i = 0; i < n / 3; i++) {
         spawn(mc, ViltrumiteParticles.COLLAPSE_PLUME.get(), at.add(sphere(r, rad * 0.7F)), new Vec3(0.0, 0.18 + r.nextDouble() * 0.2, 0.0),
            3.0F + r.nextFloat() * 2.0F);
      }
      for (int i = 0; i < n / 4; i++) {
         spawn(mc, ViltrumiteParticles.WIRE_SPARK.get(), at.add(sphere(r, rad * 0.6F)), sphere(r, 0.5F).add(0.0, 0.3, 0.0), 1.0F);
      }
      CameraShake.addAt(at, Math.min(0.6F, 0.2F + blocks / 5000.0F), 160.0);
   }

   private static void stomp(Minecraft mc, RandomSource r, Vec3 at, float wave, float detail) {
      int n = count(44, detail, 44);
      for (int i = 0; i < n; i++) {
         double a = Mth.TWO_PI * (i + r.nextFloat() * 0.5) / n;
         double rr = 4.0 + r.nextDouble() * 5.0;
         Vec3 p = at.add(Math.cos(a) * rr, 0.6 + r.nextDouble() * 1.5, Math.sin(a) * rr);
         double sp = 0.9 + r.nextDouble() * 0.6;
         spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), p, new Vec3(Math.cos(a) * sp, 0.03, Math.sin(a) * sp), 3.0F + r.nextFloat() * 1.5F);
      }
      for (int i = 0; i < n / 5; i++) {
         spawn(mc, ViltrumiteParticles.COLLAPSE_PLUME.get(), at.add(sphere(r, 6.0F)).add(0.0, 2.0, 0.0), new Vec3(0.0, 0.25 + r.nextDouble() * 0.2, 0.0),
            4.0F);
      }
      CameraShake.addAt(at, Math.min(MAX_SHAKE, 0.9F), wave * 6.0);
   }

   private static void tailHit(Minecraft mc, RandomSource r, Vec3 at, Vec3 side, float radius, float detail) {
      int n = count(14, detail, 14);
      for (int i = 0; i < n; i++) {
         Vec3 v = side.scale((r.nextBoolean() ? 1 : -1) * (0.3 + r.nextDouble() * 0.5)).add(0.0, 0.1 + r.nextDouble() * 0.2, 0.0);
         spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), at.add(sphere(r, Math.max(2.0F, radius))), v, 2.6F + r.nextFloat());
      }
      CameraShake.addAt(at, 0.45F, 170.0);
   }

   private static void clawHit(Minecraft mc, RandomSource r, Vec3 at, Vec3 push, float detail) {
      int n = count(12, detail, 12);
      for (int i = 0; i < n; i++) {
         spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), at.add(sphere(r, 4.0F)), push.scale(0.3 + r.nextDouble() * 0.4), 2.4F + r.nextFloat());
      }
      for (int i = 0; i < n / 2; i++) {
         spawn(mc, ViltrumiteParticles.WIRE_SPARK.get(), at.add(sphere(r, 2.0F)), push.scale(0.4).add(sphere(r, 0.4F)).add(0.0, 0.3, 0.0), 1.2F);
      }
      CameraShake.addAt(at, 0.4F, 150.0);
   }

   private static void fall(Minecraft mc, RandomSource r, Vec3 at, Vec3 left, float size, float detail) {
      int n = count(80, detail, 80);
      Vec3 fw = new Vec3(-left.z, 0.0, left.x);
      for (int i = 0; i < n; i++) {
         double a = Mth.TWO_PI * (i + r.nextFloat() * 0.5) / n;
         double rr = 10.0 + r.nextDouble() * size * 0.3;
         Vec3 p = at.add(Math.cos(a) * rr, 0.8 + r.nextDouble() * 2.0, Math.sin(a) * rr);
         double sp = 1.0 + r.nextDouble() * 0.7;
         spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), p, new Vec3(Math.cos(a) * sp, 0.04, Math.sin(a) * sp), 4.0F + r.nextFloat() * 2.0F);
      }
      for (int i = 0; i < n / 3; i++) {
         double along = (r.nextDouble() - 0.5) * size * 1.2;
         Vec3 p = at.add(fw.scale(along)).add(left.scale(size * 0.25 + (r.nextDouble() - 0.5) * 12.0)).add(0.0, 2.0 + r.nextDouble() * 6.0, 0.0);
         spawn(mc, ViltrumiteParticles.COLLAPSE_PLUME.get(), p, new Vec3(0.0, 0.25 + r.nextDouble() * 0.25, 0.0), 5.0F + r.nextFloat() * 3.0F);
      }
      CameraShake.addAt(at, MAX_SHAKE, 420.0);
   }

   private static void blast(Minecraft mc, RandomSource r, Vec3 at, Vec3 dir, float power, float detail) {
      int n = count(12, detail, 12);
      for (int i = 0; i < n; i++) {
         spawn(mc, ViltrumiteParticles.METEOR_BILLOW.get(), at.add(sphere(r, 4.0F * power)), sphere(r, 0.25F).add(0.0, 0.2 + r.nextDouble() * 0.3, 0.0),
            (2.5F + r.nextFloat() * 1.5F) * power);
      }
      for (int i = 0; i < n * 2; i++) {
         Vec3 v = dir.scale(-0.4 - r.nextDouble() * 0.5).add(sphere(r, 0.6F)).add(0.0, 0.4 + r.nextDouble() * 0.5, 0.0);
         spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), at, v, 1.4F + r.nextFloat());
      }
      spawn(mc, ViltrumiteParticles.FIRE_FLARE.get(), at.add(0.0, 1.0, 0.0), Vec3.ZERO, 4.0F * power);
      CameraShake.addAt(at, 0.4F, 140.0);
   }

   private static void roar(Minecraft mc, RandomSource r, Vec3 mouth, Vec3 dir, float reach, float detail) {
      Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
      if (flat.lengthSqr() < 1.0E-4) {
         return;
      }
      flat = flat.normalize();
      int n = count(36, detail, 36);
      for (int i = 0; i < n; i++) {
         double spread = (r.nextDouble() - 0.5) * 1.1;
         Vec3 d = new Vec3(flat.x * Math.cos(spread) - flat.z * Math.sin(spread), 0.0, flat.x * Math.sin(spread) + flat.z * Math.cos(spread));
         double along = 18.0 + r.nextDouble() * reach * 0.8;
         int x = Mth.floor(mouth.x + d.x * along);
         int z = Mth.floor(mouth.z + d.z * along);
         int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
         double sp = 0.7 + r.nextDouble() * 0.6;
         spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), new Vec3(x + 0.5, ground + 0.6, z + 0.5), d.scale(sp).add(0.0, 0.05, 0.0), 2.6F + r.nextFloat());
      }
      CameraShake.addAt(mouth, 0.55F, reach * 3.5);
   }

   private static void hit(Minecraft mc, RandomSource r, Vec3 at, float dealt, float detail) {
      if (clock - lastHit < 2) {
         return;
      }
      lastHit = clock;
      int n = count(3 + (int)Math.min(8.0F, dealt / 10.0F), detail, 11);
      for (int i = 0; i < n; i++) {
         spawn(mc, ViltrumiteParticles.WIRE_SPARK.get(), at, sphere(r, 0.6F), 1.0F);
      }
      spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), at, sphere(r, 0.1F), 1.8F);
   }

   private static void rise(Minecraft mc, RandomSource r, Vec3 hips, float detail) {
      int n = count(36, detail, 36);
      for (int i = 0; i < n; i++) {
         Vec3 p = hips.add(sphere(r, 24.0F)).add(0.0, 10.0, 0.0);
         spawn(mc, ViltrumiteParticles.RUIN_DUST.get(), p, new Vec3(0.0, -0.15 - r.nextDouble() * 0.2, 0.0), 3.0F + r.nextFloat() * 1.5F);
      }
      haze(mc, r, new Vec3(hips.x, hips.y - GodzillaRules.HEIGHT * 0.3, hips.z), 22.0F, detail);
   }

   private static void step(Minecraft mc, RandomSource r, Vec3 foot, float power, double distance, float detail) {
      int n = count(16, detail, 16);
      for (int i = 0; i < n; i++) {
         double a = Mth.TWO_PI * (i + r.nextFloat() * 0.5) / n;
         double rr = 4.0 + r.nextDouble() * 4.0;
         double sp = 0.35 + r.nextDouble() * 0.35;
         spawn(mc, ViltrumiteParticles.COLLAPSE_DUST.get(), foot.add(Math.cos(a) * rr, 0.5 + r.nextDouble(), Math.sin(a) * rr),
            new Vec3(Math.cos(a) * sp, 0.02, Math.sin(a) * sp), 2.4F + r.nextFloat());
      }
      CameraShake.add(stepShake(distance, power));
   }

   private static void haze(Minecraft mc, RandomSource r, Vec3 at, float radius, float detail) {
      int n = count(20, detail, 20);
      for (int i = 0; i < n; i++) {
         Vec3 p = at.add((r.nextDouble() - 0.5) * radius * 2.0, 1.0 + r.nextDouble() * 4.0, (r.nextDouble() - 0.5) * radius * 2.0);
         spawn(mc, ViltrumiteParticles.RUIN_HAZE.get(), p, new Vec3(0.0, 0.02, 0.0), 5.0F + r.nextFloat() * 3.0F);
      }
   }

   // ------------------------------------------------------------------ hot zones

   /** Pure: heat of a hot zone (1 fresh .. 0 cold) at an age out of its life. */
   public static float heat(int age, int life) {
      if (life <= 0 || age >= life) {
         return 0.0F;
      }
      float t = age / (float)life;
      return (float)Math.pow(1.0F - t, 1.5);
   }

   static void hot(Vec3 at, float radius, int life) {
      for (Hot h : HOT) {
         if (h.at.distanceToSqr(at) < h.radius * h.radius) {
            h.radius = Math.min(9.0F, Math.max(h.radius, radius) + 0.25F);
            h.age = Math.min(h.age, life / 10);
            return;
         }
      }
      if (HOT.size() >= MAX_HOT) {
         HOT.remove(0);
      }
      HOT.add(new Hot(at, Math.max(2.0F, radius), Math.max(40, life)));
   }

   public static int hotZones() {
      return HOT.size();
   }

   public static void clear() {
      HOT.clear();
      GodzillaAudio.stopAll();
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         HOT.clear();
      }
      if (mc.level == null || mc.isPaused()) {
         return;
      }
      clock++;
      budget = PARTICLE_BUDGET;
      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      RandomSource r = mc.level.random;
      Iterator<Hot> it = HOT.iterator();
      while (it.hasNext()) {
         Hot h = it.next();
         h.age++;
         float heat = heat(h.age, h.life);
         if (heat <= 0.0F) {
            it.remove();
            continue;
         }
         if (h.at.distanceToSqr(cam) > HOT_RANGE * HOT_RANGE) {
            continue;
         }
         if (r.nextFloat() < 0.35F * heat + 0.05F) {
            spawn(mc, ViltrumiteParticles.FIRE_SMOKE.get(), h.at.add((r.nextDouble() - 0.5) * h.radius, 0.6, (r.nextDouble() - 0.5) * h.radius),
               new Vec3(0.0, 0.06, 0.0), 1.6F + h.radius * 0.2F);
         }
         if (r.nextFloat() < 0.5F * heat) {
            spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), h.at.add((r.nextDouble() - 0.5) * h.radius, 0.3, (r.nextDouble() - 0.5) * h.radius),
               new Vec3((r.nextDouble() - 0.5) * 0.08, 0.12 + r.nextDouble() * 0.15, (r.nextDouble() - 0.5) * 0.08), 0.9F);
         }
         if (heat > 0.6F && r.nextFloat() < 0.25F) {
            spawn(mc, ViltrumiteParticles.METEOR_FLAME.get(), h.at.add((r.nextDouble() - 0.5) * h.radius * 0.6, 0.4, (r.nextDouble() - 0.5) * h.radius * 0.6),
               new Vec3(0.0, 0.08, 0.0), 1.5F);
         }
      }
      beamImpacts(mc, r, cam);
   }

   /** Continuous fire and sparks where a beam is hitting (the server's blast packets come every few ticks). */
   private static void beamImpacts(Minecraft mc, RandomSource r, Vec3 cam) {
      for (Entity en : mc.level.entitiesForRendering()) {
         if (!(en instanceof GodzillaEntity g) || !g.flag(GodzillaEntity.FLAG_BEAM)) {
            continue;
         }
         Vec3 end = g.clientBeamEnd;
         float detail = detail(end.distanceTo(cam));
         int n = count(4, detail, 4);
         for (int i = 0; i < n; i++) {
            spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), end.add(sphere(r, 2.0F)), sphere(r, 0.5F).add(0.0, 0.35, 0.0), 1.2F);
         }
         if (r.nextFloat() < 0.5F * detail + 0.1F) {
            spawn(mc, ViltrumiteParticles.FIRE_SMOKE.get(), end.add(sphere(r, 3.0F)), new Vec3(0.0, 0.15, 0.0), 3.0F);
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HOT.isEmpty()) {
         return;
      }
      Vec3 cam = event.getCamera().getPosition();
      float partial = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      Vector3f ax = new Vector3f(1.0F, 0.0F, 0.0F);
      Vector3f bx = new Vector3f(0.0F, 0.0F, 1.0F);
      Vector3f c = new Vector3f();
      for (Hot h : HOT) {
         if (h.at.distanceToSqr(cam) > HOT_RANGE * HOT_RANGE) {
            continue;
         }
         float heat = heat(h.age, h.life);
         float flick = 0.85F + 0.15F * Mth.sin((h.age + partial) * 0.7F + h.seed);
         c.set((float)(h.at.x - cam.x), (float)(h.at.y + 0.12 - cam.y), (float)(h.at.z - cam.z));
         GlowDraw.disc(vc, m, c, ax, bx, h.radius * 1.5F, 0.2F, 255, 90, 25, (int)(110 * heat * flick));
         GlowDraw.disc(vc, m, c, ax, bx, h.radius * 0.7F, 0.4F, 255, 190, 110, (int)(170 * heat * heat * flick));
      }
      buffers.endBatch(RenderType.lightning());
   }

   // ------------------------------------------------------------------ helpers

   private static int count(int full, float detail, int cap) {
      return Math.min(cap, Math.max(1, Math.round(full * detail)));
   }

   private static Vec3 sphere(RandomSource r, float radius) {
      double x;
      double y;
      double z;
      do {
         x = r.nextDouble() * 2.0 - 1.0;
         y = r.nextDouble() * 2.0 - 1.0;
         z = r.nextDouble() * 2.0 - 1.0;
      } while (x * x + y * y + z * z > 1.0);
      return new Vec3(x * radius, y * radius, z * radius);
   }

   private static void spawn(Minecraft mc, ParticleOptions type, Vec3 at, Vec3 v, float scale) {
      if (budget <= 0) {
         return;
      }
      budget--;
      Particle particle = mc.particleEngine.createParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
      if (particle != null && scale != 1.0F) {
         particle.scale(scale);
      }
   }

   private static final class Hot {
      final Vec3 at;
      final int life;
      final float seed;
      float radius;
      int age;

      Hot(Vec3 at, float radius, int life) {
         this.at = at;
         this.radius = radius;
         this.life = life;
         this.seed = (float)((at.x * 0.37 + at.z * 0.91) % 6.28);
      }
   }
}

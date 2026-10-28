package dev.baranhan.viltrumitecore.client.dio;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.HitStop;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * DIO's own pixel VFX (no vanilla particles): contact flashes and shards,
 * shock rings, blood streams, sun smoke, roller dust/debris/fire. Particles
 * inside a running stopped-time field hang where they are until time moves
 * again. Bounded list, cleared on a level change.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class DioFxClient {
   static final int MAX = 3000;
   private static final List<Mote> MOTES = new ArrayList<>();
   private static final Random RANDOM = new Random();
   private static ClientLevel lastLevel;

   private DioFxClient() {
   }

   /** One pixel. {@code glow} draws additive, otherwise alpha blended. */
   static final class Mote {
      Vec3 pos;
      Vec3 vel;
      final int life;
      int age;
      final float size;
      final float grow;
      final int r, g, b, alpha;
      final double gravity;
      final double drag;
      final boolean glow;
      final boolean freezes;

      Mote(Vec3 pos, Vec3 vel, int life, float size, float grow, int r, int g, int b, int alpha, double gravity, double drag, boolean glow, boolean freezes) {
         this.pos = pos;
         this.vel = vel;
         this.life = Math.max(1, life);
         this.size = size;
         this.grow = grow;
         this.r = r;
         this.g = g;
         this.b = b;
         this.alpha = alpha;
         this.gravity = gravity;
         this.drag = drag;
         this.glow = glow;
         this.freezes = freezes;
      }

      float fade(float partial) {
         float u = Math.min(1.0F, (this.age + partial) / this.life);
         return 1.0F - u * u;
      }
   }

   private static void add(Mote mote) {
      if (MOTES.size() < MAX) {
         MOTES.add(mote);
      }
   }

   private static Vec3 rnd(double scale) {
      double x = RANDOM.nextGaussian(), y = RANDOM.nextGaussian(), z = RANDOM.nextGaussian();
      double len = Math.max(1.0E-4, Math.sqrt(x * x + y * y + z * z));
      return new Vec3(x / len * scale, y / len * scale, z / len * scale);
   }

   private static double jitter(double a, double b) {
      return a + RANDOM.nextDouble() * (b - a);
   }

   // --- emitters --------------------------------------------------------------------------------

   /** Gold-white star burst with a short hot core. */
   static void burst(Vec3 at, Vec3 dir, int count, double speed, float size, int r, int g, int b) {
      Vec3 d = dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize();
      add(new Mote(at, Vec3.ZERO, 3, size * 3.2F, 0.4F, 255, 255, 240, 255, 0, 0, true, true));
      for (int i = 0; i < count; i++) {
         Vec3 v = rnd(speed * jitter(0.4, 1.0)).add(d.scale(speed * 0.8));
         add(new Mote(at, v, (int)jitter(5, 11), size * (float)jitter(0.6, 1.1), -0.3F, r, g, b, 230, 0.015, 0.82, true, true));
      }
   }

   /** Flat ring of pixels perpendicular to {@code axis}, expanding. */
   static void ring(Vec3 at, Vec3 axis, double radiusSpeed, int count, float size, int r, int g, int b, int alpha, int life) {
      Vec3 n = axis.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : axis.normalize();
      Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
      Vec3 a = n.cross(u).normalize(), c = n.cross(a).normalize();
      for (int i = 0; i < count; i++) {
         double angle = Math.PI * 2 * i / count;
         Vec3 v = a.scale(Math.cos(angle) * radiusSpeed).add(c.scale(Math.sin(angle) * radiusSpeed));
         add(new Mote(at, v, life, size, 0.0F, r, g, b, alpha, 0, 0.8, true, true));
      }
   }

   /** Speed streak along a direction: a dotted line that fades from the tail. */
   static void streak(Vec3 from, Vec3 dir, double length, int dots, float size, int r, int g, int b, int alpha) {
      Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
      for (int i = 0; i < dots; i++) {
         double u = i / (double)dots;
         add(new Mote(from.add(d.scale(length * u)), d.scale(0.05), 2 + (int)(u * 4), size * (float)(0.5 + u * 0.6), 0, r, g, b, (int)(alpha * (0.3 + 0.7 * u)), 0, 1, true, true));
      }
   }

   static void dust(Vec3 at, int count, double speed, double up, float size, int r, int g, int b, int life) {
      for (int i = 0; i < count; i++) {
         double angle = RANDOM.nextDouble() * Math.PI * 2;
         double s = speed * jitter(0.5, 1.0);
         Vec3 v = new Vec3(Math.cos(angle) * s, up * jitter(0.2, 1.0), Math.sin(angle) * s);
         int shade = (int)jitter(-18, 18);
         add(new Mote(at.add(Math.cos(angle) * 0.4, jitter(0.0, 0.3), Math.sin(angle) * 0.4), v, (int)(life * jitter(0.7, 1.2)), size * (float)jitter(0.7, 1.3), 0.9F,
            clamp(r + shade), clamp(g + shade), clamp(b + shade), 170, -0.002, 0.88, false, true));
      }
   }

   static void debris(Vec3 at, int count, double speed, float size, int r, int g, int b) {
      for (int i = 0; i < count; i++) {
         Vec3 v = rnd(speed * jitter(0.4, 1.0)).add(0, speed * jitter(0.3, 0.9), 0);
         add(new Mote(at, v, (int)jitter(18, 34), size * (float)jitter(0.6, 1.4), 0, r, g, b, 255, 0.06, 0.97, false, true));
      }
   }

   private static int clamp(int c) {
      return Math.max(0, Math.min(255, c));
   }

   /** Client-timed stand contact (TheWorldRenderer, on the clip contact key = server hit tick). */
   public static void fist(Vec3 at, Vec3 dir, float power) {
      add(new Mote(at, Vec3.ZERO, 2, 0.32F * power, 0.6F, 255, 250, 225, 235, 0, 0, true, true));
      ring(at, dir, 0.16 * power, 12, 0.035F, 255, 225, 140, 200, 4);
      streak(at.subtract(dir.normalize().scale(0.9 * power)), dir, 0.8 * power, 5, 0.03F, 255, 240, 200, 120);
   }

   /** Afterimage glints on a barrage (client side, no packet). */
   public static void barrageSpark(Vec3 at, Vec3 dir) {
      add(new Mote(at, dir.normalize().scale(0.08), 2, 0.12F, 0.5F, 255, 230, 150, 200, 0, 1, true, true));
   }

   // --- packets ---------------------------------------------------------------------------------

   public static void accept(DioFxS2CPacket p) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null) {
         return;
      }

      Vec3 at = new Vec3(p.x(), p.y(), p.z());
      Vec3 dir = new Vec3(p.dx(), p.dy(), p.dz());
      float power = p.power();
      switch (p.kind()) {
         case DioFxS2CPacket.STAND_HIT -> {
            burst(at, dir, 12, 0.32, 0.05F, 255, 214, 110);
            ring(at, dir, 0.22, 16, 0.04F, 255, 240, 190, 210, 5);
            if (p.extra() == 1) {
               blood(at, dir, 4);
            }

            HitStop.trigger(p.sourceId(), power > 0.6F ? 3 : 2);
            CameraShake.addAt(at, 0.18F + 0.25F * power, 14.0);
         }
         case DioFxS2CPacket.STAND_MISS -> {
            Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
            streak(at.subtract(d.scale(1.2)), d, 1.4 * power, 7, 0.028F, 235, 235, 255, 110);
            ring(at, d, 0.1 * power, 10, 0.028F, 230, 236, 255, 120, 4);
         }
         case DioFxS2CPacket.BARRAGE_HIT -> {
            burst(at.add(rnd(0.25)), dir, 5, 0.22, 0.04F, 255, 220, 120);
            if (RANDOM.nextInt(3) == 0) {
               ring(at, dir, 0.16, 10, 0.03F, 255, 240, 190, 170, 4);
            }

            if (p.extra() == 1 && RANDOM.nextInt(3) == 0) {
               blood(at, dir, 2);
            }

            CameraShake.addAt(at, 0.12F, 10.0);
         }
         case DioFxS2CPacket.FINISHER -> {
            burst(at, dir, 28, 0.5, 0.06F, 255, 210, 90);
            ring(at, dir, 0.32, 24, 0.05F, 255, 245, 200, 230, 6);
            ring(at, dir, 0.18, 16, 0.04F, 255, 190, 80, 200, 8);
            streak(at, dir, 2.6, 10, 0.05F, 255, 230, 160, 160);
            if (p.extra() == 1) {
               blood(at, dir, 8);
            }

            HitStop.trigger(p.sourceId(), 3);
            CameraShake.addAt(at, 0.7F, 18.0);
            near(at, 6.0, 0.12F, 3);
         }
         case DioFxS2CPacket.KNIFE_HIT -> {
            burst(at, dir.scale(-1), 8, 0.2, 0.03F, 230, 236, 255);
            if (p.extra() == 1) {
               blood(at, dir.scale(-1), 5);
            } else {
               dust(at, 5, 0.04, 0.05, 0.05F, 120, 110, 100, 14);
            }
         }
         case DioFxS2CPacket.LEDGER_BURST -> {
            int hits = Math.max(1, Math.min(40, p.extra()));
            for (int i = 0; i < hits; i++) {
               Vec3 spot = at.add(rnd(0.45 * jitter(0.3, 1.0)));
               add(new Mote(spot, Vec3.ZERO, 3 + i % 4, 0.22F, 0.5F, 255, 236, 170, 230, 0, 0, true, false));
            }

            burst(at, dir, 10 + hits * 2, 0.35 + 0.1 * power, 0.055F, 255, 200, 90);
            ring(at, dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : dir, 0.3 + 0.05 * power, 22, 0.05F, 255, 230, 160, 220, 7);
            blood(at, dir, 4 + hits / 2);
            CameraShake.addAt(at, 0.4F + 0.15F * power, 20.0);
         }
         case DioFxS2CPacket.DRAIN_PULSE -> {
            double len = Math.max(0.3, dir.length());
            Vec3 d = dir.scale(1.0 / len);
            for (int i = 0; i < 14; i++) {
               double speed = jitter(0.16, 0.26);
               int life = (int)Math.ceil(len / speed);
               add(new Mote(at.add(rnd(0.12)), d.scale(speed).add(rnd(0.015)), life, 0.035F, 0, 190, 12, 22, 235, 0, 1, false, true));
            }

            add(new Mote(at, Vec3.ZERO, 4, 0.16F, 0.3F, 220, 30, 40, 160, 0, 0, true, true));
         }
         case DioFxS2CPacket.ROLLER_SLAM -> {
            Vec3 ground = at.add(0, 0.1, 0);
            dust(ground, 70, 0.55, 0.12, 0.16F, 150, 138, 120, 30);
            debris(ground.add(0, 0.4, 0), 36, 0.45, 0.09F, 110, 104, 98);
            ring(ground, new Vec3(0, 1, 0), 0.75, 48, 0.08F, 255, 230, 170, 230, 10);
            ring(ground, new Vec3(0, 1, 0), 0.45, 36, 0.06F, 255, 255, 255, 200, 8);
            burst(ground.add(0, 1.0, 0), new Vec3(0, 1, 0), 30, 0.6, 0.07F, 255, 220, 120);
            CameraShake.addAt(at, 1.4F, 48.0);
            near(at, 20.0, 0.22F, 4);
         }
         case DioFxS2CPacket.ROLLER_PUNCH -> {
            Vec3 top = at.add(rnd(0.6)).add(0, 0.4, 0);
            burst(top, new Vec3(0, 1, 0), 8, 0.3, 0.05F, 255, 215, 110);
            debris(top, 4, 0.22, 0.06F, 236, 190, 40);
            ring(top, new Vec3(0, 1, 0), 0.24, 12, 0.04F, 255, 240, 190, 180, 4);
            CameraShake.addAt(at, 0.22F, 24.0);
         }
         case DioFxS2CPacket.ROLLER_EXPLODE -> {
            for (int i = 0; i < 90; i++) {
               Vec3 v = rnd(jitter(0.15, 0.7)).add(0, jitter(0.05, 0.3), 0);
               boolean hot = i % 3 != 0;
               add(new Mote(at.add(rnd(0.6)), v, (int)jitter(10, 22), (float)jitter(0.08, 0.2), 0.8F, 255, hot ? (int)jitter(150, 230) : 90, hot ? 40 : 20,
                  230, -0.004, 0.86, true, false));
            }

            for (int i = 0; i < 40; i++) {
               Vec3 v = rnd(jitter(0.05, 0.25)).add(0, jitter(0.05, 0.18), 0);
               int grey = (int)jitter(30, 70);
               add(new Mote(at.add(rnd(1.0)), v, (int)jitter(30, 55), (float)jitter(0.2, 0.4), 1.4F, grey, grey, grey, 190, -0.006, 0.9, false, false));
            }

            debris(at, 30, 0.7, 0.12F, 236, 190, 40);
            debris(at, 20, 0.6, 0.1F, 60, 60, 66);
            ring(at.add(0, -0.8, 0), new Vec3(0, 1, 0), 0.9, 56, 0.09F, 255, 200, 110, 230, 10);
            CameraShake.addAt(at, 1.8F, 56.0);
            near(at, 28.0, 0.55F, 8);
         }
         case DioFxS2CPacket.GUARD_BLOCK -> {
            burst(at, dir, 10, 0.25, 0.045F, 200, 225, 255);
            ring(at, dir, 0.2, 14, 0.04F, 220, 235, 255, 200, 5);
            CameraShake.addAt(at, 0.25F, 10.0);
         }
         case DioFxS2CPacket.DEFLECT -> {
            burst(at, dir, 8, 0.3, 0.04F, 255, 240, 200);
            streak(at, dir, 1.2, 6, 0.03F, 255, 240, 210, 150);
         }
         case DioFxS2CPacket.FROZEN_HIT -> {
            // A blow written into the ledger: the impact hangs in stopped time until the resume.
            add(new Mote(at, Vec3.ZERO, 200, 0.16F * Math.min(2.0F, power), 0, 255, 230, 160, 210, 0, 0, true, true));
            ring(at, dir, 0.1, 10, 0.035F, 255, 235, 170, 200, 6);
            for (int i = 0; i < 5; i++) {
               add(new Mote(at, rnd(0.12).add(dir.normalize().scale(0.1)), 200, 0.035F, 0, 255, 210, 120, 220, 0, 0.5, true, true));
            }
         }
         case DioFxS2CPacket.SUMMON -> {
            Entity source = client.level.getEntity(p.sourceId());
            float yaw = source == null ? 0.0F : source.getYRot();
            Vec3 back = Vec3.directionFromRotation(0.0F, yaw).scale(-0.5);
            for (int i = 0; i < 34; i++) {
               Vec3 pos = at.add(back).add(jitter(-0.5, 0.5), jitter(-0.9, 1.0), jitter(-0.5, 0.5));
               boolean gold = i % 2 == 0;
               add(new Mote(pos, new Vec3(0, jitter(0.01, 0.05), 0).add(rnd(0.02)), (int)jitter(10, 18), 0.035F, 0, gold ? 255 : 170, gold ? 214 : 120,
                  gold ? 90 : 230, 210, 0, 0.95, true, true));
            }

            ring(at.add(back), new Vec3(0, 1, 0), 0.12, 20, 0.035F, 255, 225, 140, 180, 8);
         }
         case DioFxS2CPacket.KNIFE_STOP -> {
            add(new Mote(at, Vec3.ZERO, 4, 0.12F, 0.2F, 235, 245, 255, 220, 0, 0, true, false));
            ring(at, dir, 0.08, 8, 0.025F, 220, 235, 255, 180, 5);
         }
         default -> {
         }
      }
   }

   private static void near(Vec3 at, double range, float strength, int ticks) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.player.position().distanceTo(at) <= range) {
         FlashOverlay.flash(strength, ticks);
      }
   }

   static void blood(Vec3 at, Vec3 dir, int count) {
      Vec3 d = dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize();
      for (int i = 0; i < count; i++) {
         Vec3 v = d.scale(jitter(0.08, 0.2)).add(rnd(0.08)).add(0, 0.06, 0);
         add(new Mote(at, v, (int)jitter(12, 22), 0.035F * (float)jitter(0.7, 1.3), 0, (int)jitter(140, 200), 8, 16, 240, 0.03, 0.95, false, true));
      }
   }

   // --- tick & render ---------------------------------------------------------------------------

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         lastLevel = client.level;
         MOTES.clear();
      }

      if (client.level == null || client.isPaused()) {
         return;
      }

      boolean fields = TimeStopClient.anyActive();
      Iterator<Mote> it = MOTES.iterator();
      while (it.hasNext()) {
         Mote m = it.next();
         if (m.freezes && fields && TimeStopClient.stoppedAt(m.pos)) {
            continue;
         }

         m.age++;
         if (m.age >= m.life) {
            it.remove();
            continue;
         }

         m.pos = m.pos.add(m.vel);
         m.vel = m.vel.scale(m.drag).add(0, -m.gravity, 0);
      }

      sunAndDrain(client);
   }

   /** Sunlit DIO smokes; a held drain keeps a thin blood thread. */
   private static void sunAndDrain(Minecraft client) {
      if (client.player == null || client.level.getGameTime() % 2 != 0) {
         return;
      }

      for (Player player : client.level.players()) {
         HeroPublicSnapshot snapshot = DioView.of(player);
         if (snapshot == null || player.isInvisible() || player.distanceToSqr(client.player) > 64 * 64) {
            continue;
         }

         if (snapshot.heroFlag(DioRules.FLAG_SUN)) {
            for (int i = 0; i < 2; i++) {
               Vec3 at = player.position().add(jitter(-0.3, 0.3), jitter(0.2, player.getBbHeight()), jitter(-0.3, 0.3));
               int grey = (int)jitter(90, 140);
               add(new Mote(at, new Vec3(jitter(-0.01, 0.01), jitter(0.02, 0.05), jitter(-0.01, 0.01)), (int)jitter(14, 26), 0.05F, 1.6F, grey, grey,
                  grey + 6, 120, -0.001, 0.97, false, false));
            }
         }

         if (snapshot.heroFlag(DioRules.FLAG_DRAIN_HOLD) && snapshot.controlTargetId() >= 0) {
            Entity target = client.level.getEntity(snapshot.controlTargetId());
            if (target != null) {
               Vec3 from = target.getEyePosition().add(0, -0.25, 0);
               Vec3 to = player.getEyePosition().add(0, -0.3, 0);
               Vec3 d = to.subtract(from);
               double len = Math.max(0.2, d.length());
               add(new Mote(from, d.scale(0.2 / len), (int)Math.ceil(len / 0.2), 0.03F, 0, 170, 10, 20, 220, 0, 1, false, true));
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MOTES.isEmpty() || ShaderCompat.isShadowPass()) {
         return;
      }

      float partial = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      boolean fields = TimeStopClient.anyActive();
      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      modelView.setIdentity();
      PixelVfx.rotateCamera(modelView, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            if (glow) {
               RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
            } else {
               RenderSystem.defaultBlendFunc();
            }

            buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Mote m : MOTES) {
               if (m.glow != glow) {
                  continue;
               }

               boolean hanging = m.freezes && fields && TimeStopClient.stoppedAt(m.pos);
               float p = hanging ? 0.0F : partial;
               Vec3 pos = hanging ? m.pos : m.pos.add(m.vel.scale(p));
               float fade = m.fade(p);
               float size = m.size * (1.0F + m.grow * Math.min(1.0F, (m.age + p) / m.life));
               int alpha = (int)(m.alpha * fade);
               if (alpha > 2) {
                  PixelVfx.billboardPixel(buffer, cam, camera, pos, size, m.r, m.g, m.b, alpha);
               }
            }

            tesselator.end();
         }
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   /** Test/QA hook: live pixel count. */
   public static int count() {
      return MOTES.size();
   }
}

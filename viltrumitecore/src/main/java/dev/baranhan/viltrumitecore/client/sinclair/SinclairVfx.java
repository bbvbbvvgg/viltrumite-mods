package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.ActCardEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.ActPropEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.DecoyEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.HexHatEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.HexMarkEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SparkEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.VexingBoltEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Sinclair's world effects as additive pixel quads ({@link PixelVfx}, no vanilla particles):
 * spark streaks, the growing Vexing Bolt orb, Savannah's ribbons, the decoy's burst of cards and
 * doves, the hex circle and poof, the acts' beam/suction/seam/shockwaves, the capture card's flight
 * and the stage spotlights ({@link StageLight}). Everything derives from synced entity data and
 * the snapshot, so every viewer sees the same beats.
 */
public final class SinclairVfx {
   private SinclairVfx() {
   }

   static final double NEAR = 2.0;

   /** One batch of camera-facing pixels. */
   record Ctx(BufferBuilder buffer, Vec3 cam, Camera camera, float partialTick, float seconds) {
      void dot(Vec3 at, float size, int rgb, int alpha) {
         if (alpha > 2) {
            // Near the lens (first-person wand/palm anchors) a pixel keeps the apparent size it has at
            // NEAR blocks instead of filling the screen (playtest 2026-10-28, shots 06-09).
            double d = at.distanceTo(this.cam);
            if (d < 0.2) {
               return;
            }

            float s = d < NEAR ? (float) (size * d / NEAR) : size;
            PixelVfx.billboardPixel(this.buffer, this.cam, this.camera, at, s, SinclairPalette.r(rgb), SinclairPalette.g(rgb), SinclairPalette.b(rgb), Math.min(255, alpha));
         }
      }

      void line(Vec3 from, Vec3 to, float spacing, float size, int rgb, int alpha) {
         if (alpha > 2) {
            PixelVfx.beamDots(this.buffer, this.cam, this.camera, from, to, spacing, size, SinclairPalette.r(rgb), SinclairPalette.g(rgb), SinclairPalette.b(rgb), Math.min(255, alpha));
         }
      }

      void ring(Vec3 center, double radius, int count, double phase, float size, int rgb, int alpha) {
         for (int i = 0; i < count; i++) {
            double a = phase + Math.PI * 2.0 * i / count;
            this.dot(center.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius), size, rgb, alpha);
         }
      }
   }

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      boolean any = false;
      for (Player player : level.players()) {
         if (SinclairView.of(player) != null) {
            any = true;
            break;
         }
      }

      if (!any) {
         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof SparkEntity || entity instanceof VexingBoltEntity || entity instanceof AssistantEntity || entity instanceof DecoyEntity
               || entity instanceof HexHatEntity || entity instanceof HexMarkEntity || entity instanceof ActPropEntity || entity instanceof ActCardEntity) {
               any = true;
               break;
            }
         }
      }

      if (!any) {
         return;
      }

      Camera camera = event.getCamera();
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
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         Ctx ctx = new Ctx(buffer, camera.getPosition(), camera, event.getPartialTick(), RealTime.seconds());
         for (Entity entity : level.entitiesForRendering()) {
            if (entity.position().distanceToSqr(ctx.cam()) > 160.0 * 160.0) {
               continue;
            }

            if (entity instanceof SparkEntity spark) {
               spark(ctx, spark);
            } else if (entity instanceof VexingBoltEntity bolt) {
               bolt(ctx, bolt);
            } else if (entity instanceof AssistantEntity assistant) {
               assistant(ctx, assistant);
            } else if (entity instanceof DecoyEntity decoy) {
               decoy(ctx, decoy);
            } else if (entity instanceof HexHatEntity hat) {
               hexHat(ctx, hat);
            } else if (entity instanceof HexMarkEntity mark) {
               hexMark(ctx, mark);
            } else if (entity instanceof ActPropEntity prop) {
               prop(ctx, prop);
            } else if (entity instanceof ActCardEntity card) {
               card(ctx, card);
            }
         }

         for (Player player : level.players()) {
            HeroPublicSnapshot snapshot = SinclairView.of(player);
            if (snapshot != null && player.position().distanceToSqr(ctx.cam()) < 160.0 * 160.0) {
               boolean veil = SinclairView.flag(snapshot, SinclairFlags.VEIL);
               boolean ally = mc.player != null && (mc.player == player || mc.player.isSpectator() || player.isAlliedTo(mc.player));
               if (!veil || ally) {
                  sinclair(ctx, player, snapshot, level);
               }
               StageLight.render(ctx, player, snapshot, level);
            }
         }

         tesselator.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   static Vec3 pos(Entity entity, float partialTick) {
      return entity.getPosition(partialTick);
   }

   // --- entities --------------------------------------------------------------------------------
   private static void spark(Ctx c, SparkEntity spark) {
      Vec3 at = pos(spark, c.partialTick());
      Vec3 back = new Vec3(spark.xo - spark.getX(), spark.yo - spark.getY(), spark.zo - spark.getZ());
      double len = back.length();
      Vec3 dir = len < 1.0E-4 ? Vec3.ZERO : back.scale(1.0 / len);
      for (int i = 0; i < 7; i++) {
         float k = i / 6.0F;
         c.dot(at.add(dir.scale(k * 1.1)), 0.07F - 0.045F * k, SinclairPalette.mix(SinclairPalette.GOLD, SinclairPalette.GHOST, k), (int) (230 * (1.0F - k * 0.8F)));
      }

      c.dot(at, 0.13F, SinclairPalette.LAVENDER, 70);
   }

   private static void bolt(Ctx c, VexingBoltEntity bolt) {
      Vec3 at = pos(bolt, c.partialTick());
      float age = bolt.age() + c.partialTick();
      float grow = Mth.clamp(age / SinclairRules.BOLT_RAMP_TICKS, 0.0F, 1.0F);
      float radius = 0.16F + 0.16F * grow;
      PixelVfx.sphereShell(c.buffer(), c.cam(), c.camera(), at, radius, c.seconds() * 2.0F, 0x9B, 0x6B, 0xFF, 150);
      c.dot(at, 0.08F + 0.05F * grow, SinclairPalette.GOLD, 230);
      c.dot(at, radius * 1.6F, SinclairPalette.GHOST, 60);
      for (int i = 0; i < 3; i++) {
         double a = age * 0.6 + i * Math.PI * 2.0 / 3.0;
         c.dot(at.add(Math.cos(a) * radius * 1.5, Math.sin(a * 1.3) * radius * 0.8, Math.sin(a) * radius * 1.5), 0.035F, SinclairPalette.GOLD, 200);
      }

      Vec3 back = new Vec3(bolt.xo - bolt.getX(), bolt.yo - bolt.getY(), bolt.zo - bolt.getZ());
      for (int i = 1; i <= 8; i++) {
         double wob = Math.sin(age * 0.8 + i) * 0.06;
         c.dot(at.add(back.scale(i * 0.55)).add(0.0, wob, 0.0), radius * (0.7F - i * 0.07F), SinclairPalette.mix(SinclairPalette.GHOST, SinclairPalette.LAVENDER, i / 8.0F),
            150 - i * 16);
      }

      if (bolt.redirected()) {
         float since = bolt.age() - bolt.redirectAge() + c.partialTick();
         if (since < 6.0F) {
            c.ring(at, 0.3 + since * 0.25, 18, since * 0.4, 0.05F, SinclairPalette.GOLD, (int) (220 * (1.0F - since / 6.0F)));
         }
      }
   }

   private static void assistant(Ctx c, AssistantEntity a) {
      Vec3 at = pos(a, c.partialTick());
      float t = SinclairEntityRenderers.ticks(a, a.stateStart(), c.partialTick());
      int state = a.state();
      if (state == AssistantEntity.APPEAR && t < 16.0F) {
         ribbons(c, at, t / 16.0F, false);
      } else if ((state == AssistantEntity.LEAVE || state == AssistantEntity.DEATH) && t < 20.0F) {
         ribbons(c, at, t / 20.0F, true);
      } else if (state == AssistantEntity.SWAP_OUT || state == AssistantEntity.SWAP_IN) {
         c.ring(at.add(0.0, 1.0, 0.0), 0.8 - t * 0.08, 16, t * 0.9, 0.05F, SinclairPalette.LAVENDER, 180);
      }

      // Smoke trail under the dress: slow drifting wisps.
      for (int i = 0; i < 5; i++) {
         double ph = c.seconds() * 1.3 + i * 1.7 + a.getId();
         double h = (ph % 1.0);
         c.dot(at.add(Math.sin(ph * 2.0) * 0.25, 0.25 - h * 0.35, Math.cos(ph * 1.7) * 0.25), 0.07F, SinclairPalette.SAVANNAH, (int) (90 * (1.0 - h)));
      }
   }

   /** Savannah's spiral of violet ribbons (appear: winds in; leave/death: unwinds and scatters). */
   private static void ribbons(Ctx c, Vec3 at, float k, boolean out) {
      for (int r = 0; r < 3; r++) {
         for (int i = 0; i < 14; i++) {
            float u = i / 13.0F;
            double radius = out ? 0.3 + k * 1.4 * u : 1.0 - k * 0.7;
            double a = r * Math.PI * 2.0 / 3.0 + u * 5.0 + k * (out ? 3.0 : 8.0);
            double y = u * 2.0 * (out ? 1.0 + k : k + 0.2);
            int alpha = (int) (200 * (out ? 1.0F - k : Math.min(1.0F, k * 3.0F)) * (1.0F - u * 0.4F));
            c.dot(at.add(Math.cos(a) * radius, y, Math.sin(a) * radius), 0.06F, r == 1 ? SinclairPalette.LAVENDER : SinclairPalette.SAVANNAH, alpha);
         }
      }
   }

   private static void decoy(Ctx c, DecoyEntity decoy) {
      if (!decoy.revealed()) {
         return;
      }

      float t = SinclairEntityRenderers.ticks(decoy, decoy.revealTick(), c.partialTick());
      if (t > 20.0F) {
         return;
      }

      Vec3 at = pos(decoy, c.partialTick()).add(0.0, 1.0, 0.0);
      float fade = 1.0F - t / 20.0F;
      for (int i = 0; i < 18; i++) {
         double a = i * 2.399;
         double up = 0.15 + (i % 5) * 0.05;
         Vec3 v = new Vec3(Math.cos(a) * 0.22, up, Math.sin(a) * 0.22);
         Vec3 p = at.add(v.scale(t)).add(0.0, -0.012 * t * t, 0.0);
         int rgb = i % 3 == 0 ? SinclairPalette.GOLD : i % 3 == 1 ? SinclairPalette.IVORY : SinclairPalette.VELVET;
         c.dot(p, 0.09F, rgb, (int) (230 * fade));
         c.dot(p.add(0.0, 0.1, 0.0), 0.06F, SinclairPalette.IVORY, (int) (200 * fade));
      }

      // Ghost doves: two-dot wings flapping as they climb away.
      for (int i = 0; i < 5; i++) {
         double a = i * 1.257 + 0.3;
         Vec3 p = at.add(Math.cos(a) * t * 0.12, t * 0.1 + i * 0.05, Math.sin(a) * t * 0.12);
         double flap = Math.sin(t * 1.8 + i) * 0.12;
         c.dot(p, 0.07F, SinclairPalette.IVORY, (int) (240 * fade));
         c.dot(p.add(Math.sin(a) * 0.16, flap, -Math.cos(a) * 0.16), 0.06F, SinclairPalette.IVORY, (int) (200 * fade));
         c.dot(p.add(-Math.sin(a) * 0.16, flap, Math.cos(a) * 0.16), 0.06F, SinclairPalette.IVORY, (int) (200 * fade));
      }

      c.ring(at.add(0.0, -0.9, 0.0), 0.5 + t * 0.15, 24, t * 0.2, 0.05F, SinclairPalette.GHOST, (int) (180 * fade));
   }

   private static void hexHat(Ctx c, HexHatEntity hat) {
      Vec3 ground = pos(hat, c.partialTick()).add(0.0, 0.05, 0.0);
      float age = hat.tickCount + c.partialTick();
      float in = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
      if (hat.poofTick() < 0) {
         c.ring(ground, SinclairRules.HEX_RADIUS, 56, age * 0.05, 0.06F, SinclairPalette.GHOST, (int) (170 * in));
         c.ring(ground, SinclairRules.HEX_RADIUS * 0.72, 32, -age * 0.08, 0.05F, SinclairPalette.LAVENDER, (int) (120 * in));
         for (int i = 0; i < 6; i++) {
            double a = age * 0.05 + i * Math.PI / 3.0;
            Vec3 p = ground.add(Math.cos(a) * SinclairRules.HEX_RADIUS * 0.86, 0.0, Math.sin(a) * SinclairRules.HEX_RADIUS * 0.86);
            c.dot(p, 0.12F, SinclairPalette.GOLD, (int) (150 * in));
         }

         c.line(ground.add(0.0, SinclairRules.HEX_HAT_HEIGHT - 0.3, 0.0), ground, 0.35F, 0.04F, SinclairPalette.LAVENDER, (int) (90 * in));
      } else {
         float p = SinclairEntityRenderers.ticks(hat, hat.poofTick(), c.partialTick());
         float fade = 1.0F - p / HexHatEntity.AFTER_POOF;
         c.ring(ground, SinclairRules.HEX_RADIUS * (0.4 + p * 0.12), 60, 0.0, 0.08F, SinclairPalette.GHOST, (int) (220 * fade));
         for (int i = 0; i < 30; i++) {
            double a = i * 2.399;
            double r = p * 0.4 * (0.5 + (i % 4) * 0.2);
            c.dot(ground.add(Math.cos(a) * r, 0.3 + p * 0.15 * (i % 3), Math.sin(a) * r), 0.09F, i % 2 == 0 ? SinclairPalette.IVORY : SinclairPalette.LAVENDER,
               (int) (220 * fade));
         }
      }
   }

   private static void hexMark(Ctx c, HexMarkEntity mark) {
      LivingEntity target = mark.target();
      if (target == null) {
         return;
      }

      Vec3 at = pos(target, c.partialTick()).add(0.0, 0.62, 0.0);
      float age = mark.age() + c.partialTick();
      for (int i = 0; i < 2; i++) {
         double a = age * 0.25 + i * Math.PI;
         c.dot(at.add(Math.cos(a) * 0.3, Math.sin(age * 0.2 + i) * 0.05, Math.sin(a) * 0.3), 0.04F, SinclairPalette.GOLD, 200);
      }

      float left = mark.duration() - age;
      if (left < 4.0F && left > 0.0F) {
         c.ring(at.add(0.0, -0.3, 0.0), 0.2 + (4.0F - left) * 0.25, 20, 0.0, 0.06F, SinclairPalette.LAVENDER, (int) (60 * left));
      }
   }

   private static void card(Ctx c, ActCardEntity card) {
      Vec3 at = pos(card, c.partialTick());
      Vec3 back = new Vec3(card.xo - card.getX(), card.yo - card.getY(), card.zo - card.getZ());
      int color = card.color() == 0 ? SinclairPalette.GHOST : card.color();
      for (int i = 1; i <= 6; i++) {
         c.dot(at.add(back.scale(i * 0.35)), 0.06F - i * 0.006F, color, 170 - i * 24);
      }
   }

   private static void prop(Ctx c, ActPropEntity prop) {
      float s = ActPropRenderer.sceneTick(prop, c.partialTick());
      Vec3 at = pos(prop, c.partialTick());
      int color = prop.color() == 0 ? SinclairPalette.GHOST : prop.color();
      switch (prop.kind()) {
         case ActPropEntity.NOVA_HAT -> {
            if (s < ActScript.NOVA.reveal()) {
               float k = (s - ActScript.NOVA_THROW) / (ActScript.NOVA.reveal() - ActScript.NOVA_THROW);
               for (int i = 0; i < 24; i++) {
                  double u = (i / 24.0 + k * 1.5) % 1.0;
                  double r = 4.0 * (1.0 - u);
                  double a = i * 2.1 + u * 6.0;
                  c.dot(at.add(Math.cos(a) * r, (1.0 - u) * 1.2 - 0.4, Math.sin(a) * r), 0.06F, i % 2 == 0 ? color : SinclairPalette.GOLD, (int) (200 * u));
               }
            } else {
               float b = s - ActScript.NOVA.reveal();
               if (b < 10.0F) {
                  float fade = 1.0F - b / 10.0F;
                  c.ring(at, ActScript.NOVA_RADIUS * Math.min(1.0, b / 5.0), 64, 0.0, 0.1F, color, (int) (230 * fade));
                  c.ring(at.add(0.0, 0.3, 0.0), ActScript.NOVA_RADIUS * Math.min(1.0, b / 7.0), 48, 0.3, 0.07F, SinclairPalette.GOLD, (int) (200 * fade));
                  // Burst flash as a starburst of fine motes (a single big billboard read as a flat square in playtest).
                  double reach = 0.4 + 1.6 * Math.min(1.0, b / 4.0);
                  for (int i = 0; i < 16; i++) {
                     double a = i * (Math.PI * 2.0 / 16.0) + b * 0.15;
                     for (int j = 1; j <= 3; j++) {
                        double r = reach * j / 3.0;
                        c.dot(at.add(Math.cos(a) * r, 0.5 + Math.sin(a * 2.0) * 0.2 * j, Math.sin(a) * r), 0.07F, SinclairPalette.IVORY, (int) (190 * fade));
                     }
                  }
               }
            }
         }
         case ActPropEntity.CUT_LINE -> {
            Vec3 dir = Vec3.directionFromRotation(0.0F, prop.getYRot());
            Vec3 end = at.add(dir.scale(prop.reach()));
            Vec3 ground = at.add(0.0, 0.06, 0.0);
            if (s < ActScript.CUT.reveal()) {
               int pulse = (int) (120 + 80 * Math.sin(s * 1.4));
               c.line(ground, end.add(0.0, 0.06, 0.0), 0.3F, 0.06F, color, pulse);
               // The two saw strokes: a bright tooth runs along the line and throws sparks.
               for (int saw : ActScript.CUT_SAWS) {
                  float d = s - saw;
                  if (d >= 0.0F && d < 6.0F) {
                     float k = d / 6.0F;
                     Vec3 tooth = ground.add(dir.scale(prop.reach() * (saw == ActScript.CUT_SAWS[0] ? k : 1.0F - k)));
                     c.dot(tooth.add(0.0, 0.1, 0.0), 0.22F, SinclairPalette.IVORY, 230);
                     for (int i = 0; i < 8; i++) {
                        double a = i * 0.785 + s;
                        c.dot(tooth.add(Math.cos(a) * 0.3 * k, 0.15 + (i % 3) * 0.12 * (1.0F - k), Math.sin(a) * 0.3 * k), 0.05F, SinclairPalette.GOLD, (int) (220 * (1.0F - k)));
                     }
                  }
               }
            } else if (s < ActScript.CUT.pauseEnd()) {
               float k = (s - ActScript.CUT.reveal()) / (ActScript.CUT.pauseEnd() - ActScript.CUT.reveal());
               float pass = Mth.clamp((s - ActScript.CUT.reveal()) / 4.0F, 0.0F, 1.0F);
               c.line(ground, ground.add(dir.scale(prop.reach() * pass)), 0.12F, 0.08F, SinclairPalette.GOLD, (int) (240 * (1.0F - k)));
               c.line(ground, ground.add(dir.scale(prop.reach() * pass)), 0.4F, 0.2F, color, (int) (90 * (1.0F - k)));
               if (s < ActScript.CUT.reveal() + 5.0F) {
                  for (int i = 0; i < 20; i++) {
                     double along = prop.reach() * pass * (i / 20.0);
                     double rise = (s - ActScript.CUT.reveal()) * (0.15 + (i % 3) * 0.08);
                     c.dot(ground.add(dir.scale(along)).add(0.0, rise, 0.0), 0.06F, SinclairPalette.IVORY, 200);
                  }
               }
            }
         }
         case ActPropEntity.AEGIS_CURTAIN -> {
            float reveal = ActScript.AEGIS.reveal();
            if (s < reveal) {
               float in = Mth.clamp(s / 6.0F, 0.0F, 1.0F);
               c.ring(at.add(0.0, 2.65 * in, 0.0), ActPropRenderer.CURTAIN_RADIUS, 40, s * 0.02, 0.05F, SinclairPalette.GOLD, (int) (200 * in));
            } else {
               float b = s - reveal;
               if (b < 10.0F) {
                  float fade = 1.0F - b / 10.0F;
                  c.ring(at.add(0.0, 0.2, 0.0), ActScript.AEGIS_RADIUS * Math.min(1.0, b / 4.0), 60, 0.0, 0.1F, SinclairPalette.VELVET, (int) (240 * fade));
                  c.ring(at.add(0.0, 1.0, 0.0), ActScript.AEGIS_RADIUS * Math.min(1.0, b / 5.0), 40, 0.2, 0.07F, SinclairPalette.GOLD, (int) (200 * fade));
               }

               // Finish: velvet shreds and gold sequins rain down around the stage.
               float rain = ActScript.AEGIS.pauseEnd() - reveal;
               if (b < rain) {
                  float k = b / rain;
                  for (int i = 0; i < 36; i++) {
                     double a = i * 2.399 + b * 0.05;
                     double r = ActPropRenderer.CURTAIN_RADIUS + 0.4 + (i % 5) * 0.35 * k;
                     double y = 2.8 - ((k * 2.6 + (i % 7) * 0.13) % 2.8);
                     c.dot(at.add(Math.cos(a) * r, y, Math.sin(a) * r), i % 3 == 0 ? 0.08F : 0.05F, i % 3 == 0 ? SinclairPalette.GOLD : SinclairPalette.VELVET, (int) (220 * (1.0F - k)));
                  }
               }
            }
         }
         case ActPropEntity.FINALE_CHEST -> {
            float land = s - ActScript.FINALE_LAND;
            if (land >= 0.0F && land < 10.0F) {
               c.ring(at.add(0.0, 0.1, 0.0), 0.6 + land * 0.25, 40, 0.0, 0.1F, SinclairPalette.IVORY, (int) (180 * (1.0F - land / 10.0F)));
            }

            for (int hit : ActScript.FINALE_SWORDS) {
               float d = s - hit;
               if (d >= 0.0F && d < 5.0F) {
                  for (int i = 0; i < 10; i++) {
                     double a = i * 0.628 + hit;
                     c.dot(at.add(Math.cos(a) * d * 0.2, 1.3 + d * 0.12, Math.sin(a) * d * 0.2), 0.06F, SinclairPalette.GOLD, (int) (220 * (1.0F - d / 5.0F)));
                  }
               }
            }

            float rattle = s - ActScript.FINALE.hushStart();
            if (rattle >= 0.0F && s < ActScript.FINALE.reveal()) {
               // The chest rattles in the hush: dust puffs at its feet, light leaks from the lid.
               for (int i = 0; i < 12; i++) {
                  double a = i * 0.524 + s * 0.7;
                  c.dot(at.add(Math.cos(a) * 0.8, 0.08 + Math.abs(Math.sin(s * 2.0 + i)) * 0.2, Math.sin(a) * 0.8), 0.05F, SinclairPalette.IVORY, 120);
               }

               c.line(at.add(-0.6, 1.0, 0.0), at.add(0.6, 1.0, 0.0), 0.08F, 0.05F, SinclairPalette.GOLD, (int) (120 + 100 * Math.sin(s * 3.0)));
            }

            float b = s - ActScript.FINALE.reveal();
            if (b >= 0.0F && b < 12.0F) {
               float fade = 1.0F - b / 12.0F;
               for (int i = 0; i < 40; i++) {
                  double a = i * 2.399;
                  double up = 0.1 + (i % 5) * 0.06;
                  c.dot(at.add(Math.cos(a) * b * 0.25, 1.0 + up * b, Math.sin(a) * b * 0.25), 0.09F, i % 2 == 0 ? color : SinclairPalette.GOLD, (int) (230 * fade));
               }

               c.dot(at.add(0.0, 1.0, 0.0), 1.6F * fade, SinclairPalette.IVORY, (int) (110 * fade));
            }
         }
         default -> {
         }
      }
   }

   // --- Sinclair himself ------------------------------------------------------------------------
   private static void sinclair(Ctx c, Player player, HeroPublicSnapshot snapshot, ClientLevel level) {
      float pt = c.partialTick();
      SinclairMove move = SinclairView.move(snapshot);
      double t = SinclairView.moveTicks(player, snapshot, pt);
      int flags = snapshot.heroFlags();
      Vec3 tip = SinclairAnchors.wandTip(player, pt);
      Vec3 hand = SinclairAnchors.leftHand(player, pt);
      int color = SinclairView.color(snapshot);
      boolean trigger = SinclairFlags.has(flags, SinclairFlags.TRIGGER_HELD);
      c.dot(tip, trigger ? 0.07F : 0.045F, SinclairPalette.GOLD, trigger ? 200 : 110);
      c.dot(tip, 0.14F, SinclairPalette.GHOST, trigger ? 70 : 30);
      int sinceShot = SinclairView.extra(snapshot, SinclairFlags.EXTRA_SINCE_SHOT);
      if (sinceShot < 3) {
         c.ring(tip, 0.08 + sinceShot * 0.08, 10, sinceShot, 0.03F, SinclairPalette.GOLD, 200 - sinceShot * 60);
      }

      if (SinclairFlags.has(flags, SinclairFlags.CARD_HELD)) {
         for (int i = 0; i < 4; i++) {
            double a = c.seconds() * 2.0 + i * Math.PI / 2.0;
            c.dot(hand.add(Math.cos(a) * 0.16, 0.1 + Math.sin(a * 0.7) * 0.05, Math.sin(a) * 0.16), 0.03F, color, 180);
         }

         c.dot(hand.add(0.0, 0.12, 0.0), 0.2F, color, 50);
      }

      if (SinclairView.extra(snapshot, SinclairFlags.EXTRA_OVATION) > 0) {
         Vec3 feet = SinclairVfx.pos(player, pt);
         for (int i = 0; i < 10; i++) {
            double u = (c.seconds() * 0.8 + i / 10.0) % 1.0;
            double a = i * 2.399 + c.seconds();
            c.dot(feet.add(Math.cos(a) * 0.6, u * 2.2, Math.sin(a) * 0.6), 0.05F, SinclairPalette.GOLD, (int) (200 * (1.0 - u)));
         }
      }

      if (move == null) {
         return;
      }

      switch (move) {
         case BOLT_CAST -> {
            if (t < SinclairRules.BOLT_WINDUP) {
               float k = (float) (t / SinclairRules.BOLT_WINDUP);
               for (int i = 0; i < 10; i++) {
                  double a = i * 0.628 + t * 0.5;
                  double r = 0.6 * (1.0 - k);
                  c.dot(tip.add(Math.cos(a) * r, Math.sin(a * 1.3) * r * 0.6, Math.sin(a) * r), 0.04F, SinclairPalette.GHOST, (int) (100 + 140 * k));
               }

               c.dot(tip, 0.06F + 0.12F * k, SinclairPalette.LAVENDER, (int) (160 * k));
            }
         }
         case BOLT_SNAP, SNAP -> snapStar(c, hand, t - (move == SinclairMove.SNAP ? 4.0 : 3.0));
         case HEX_CAST -> {
            for (int i = 0; i < 6; i++) {
               double a = t * 0.9 + i;
               c.dot(hand.add(Math.cos(a) * 0.25, 0.15 + Math.sin(a) * 0.1, Math.sin(a) * 0.25), 0.04F, SinclairPalette.LAVENDER, 200);
            }
         }
         case SWAP -> c.ring(SinclairVfx.pos(player, pt).add(0.0, 1.0, 0.0), 0.9 - t * 0.12, 20, t * 0.9, 0.05F, SinclairPalette.LAVENDER, 200);
         case REFUSE -> c.dot(tip, 0.12F, 0x8A8A9A, (int) (120 * (1.0 - t / 8.0)));
         case CAPTURE -> capture(c, player, snapshot, level, hand, t);
         case COPY -> copy(c, player, snapshot, level, hand, t);
         case ACT_BEAM -> beam(c, player, snapshot, hand, t, color);
         case ACT_NOVA -> {
            // Tension: the hat spins on the fingers, gold rings wind up around it.
            if (t >= ActScript.NOVA_HAT_OFF && t < ActScript.NOVA_THROW) {
               float k = (float) ((t - ActScript.NOVA_HAT_OFF) / (ActScript.NOVA_THROW - ActScript.NOVA_HAT_OFF));
               for (int i = 0; i < 14; i++) {
                  double a = t * (0.5 + k * 0.9) + i * Math.PI * 2.0 / 14.0;
                  c.dot(hand.add(Math.cos(a) * (0.32 + 0.1 * k), 0.12 + Math.sin(a * 2.0) * 0.04, Math.sin(a) * (0.32 + 0.1 * k)), 0.035F,
                     i % 2 == 0 ? SinclairPalette.GOLD : color, (int) (120 + 110 * k));
               }
            }
         }
         case ACT_CUT -> {
            // Tension and hush: the card blade glows at the wand tip, brightest before the grand cut.
            if (t < ActScript.CUT.reveal()) {
               boolean hush = t >= ActScript.CUT.hushStart();
               float k = (float) Math.min(1.0, t / ActScript.CUT_TELEGRAPH);
               c.dot(tip, (hush ? 0.16F : 0.08F) + 0.06F * k, SinclairPalette.VELVET, hush ? 220 : 150);
               c.dot(tip, 0.05F, SinclairPalette.IVORY, 230);
               if (hush) {
                  c.ring(tip, 0.25 + 0.1 * Math.sin(t * 1.3), 10, t * 0.6, 0.03F, SinclairPalette.GOLD, 200);
               }
            }
         }
         case ACT_BARRAGE -> {
            if (t < ActScript.BARRAGE.pauseStart()) {
               Vec3 look = player.getViewVector(pt);
               Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
               Vec3 crown = SinclairVfx.pos(player, pt).add(0.0, player.getBbHeight() + 0.9, 0.0);
               int volleys = ActScript.BARRAGE_VOLLEYS.length;
               for (int i = 0; i < volleys * 3; i++) {
                  double a = Math.toRadians(-66.0 + 12.0 * i);
                  float open = (float) Mth.clamp(t / ActScript.BARRAGE.main(), 0.0, 1.0);
                  Vec3 p = hand.add(right.scale(Math.sin(a * open) * 0.38)).add(0.0, 0.2 + Math.cos(a * open) * 0.3, 0.0);
                  int volley = Math.min(volleys - 1, i / 3);
                  boolean gone = t >= ActScript.BARRAGE_VOLLEYS[volley];
                  if (volley == volleys - 1 && t >= ActScript.BARRAGE.hushStart()) {
                     // The last three gather overhead and spin in the hush.
                     double k = Mth.clamp((t - ActScript.BARRAGE.hushStart()) / 5.0, 0.0, 1.0);
                     double spin = t * 0.7 + (i % 3) * Math.PI * 2.0 / 3.0;
                     p = p.lerp(crown.add(Math.cos(spin) * 0.35, Math.sin(t * 0.4) * 0.05, Math.sin(spin) * 0.35), k);
                  }

                  if (!gone) {
                     c.dot(p, 0.05F, i % 2 == 0 ? SinclairPalette.IVORY : color, 200);
                     c.dot(p, 0.11F, color, 40);
                  }
               }
            }
         }
         default -> {
         }
      }
   }

   private static void snapStar(Ctx c, Vec3 hand, double since) {
      if (since < 0.0 || since > 5.0) {
         return;
      }

      float fade = (float) (1.0 - since / 5.0);
      for (int i = 0; i < 8; i++) {
         double a = i * Math.PI / 4.0;
         double r = 0.08 + since * 0.08;
         c.dot(hand.add(Math.cos(a) * r, Math.sin(a) * r, 0.0), 0.035F, SinclairPalette.GOLD, (int) (240 * fade));
      }

      c.dot(hand, 0.1F * fade, SinclairPalette.IVORY, (int) (220 * fade));
   }

   /** Ultimate capture: the volunteer's card flies from them into the left palm on tick 14. */
   private static void capture(Ctx c, Player player, HeroPublicSnapshot snapshot, ClientLevel level, Vec3 hand, double t) {
      Entity volunteer = snapshot.controlTargetId() >= 0 ? level.getEntity(snapshot.controlTargetId()) : null;
      if (volunteer == null) {
         return;
      }

      Vec3 from = SinclairVfx.pos(volunteer, c.partialTick()).add(0.0, volunteer.getBbHeight() * 0.6, 0.0);
      double k = (t - SinclairPoseRules.CAPTURE_CARD_TICK) / 4.0;
      if (k < 0.0) {
         float build = (float) Mth.clamp(t / SinclairPoseRules.CAPTURE_CARD_TICK, 0.0, 1.0);
         c.dot(from, 0.15F + 0.2F * build, SinclairPalette.IVORY, (int) (160 * build));
         return;
      }

      if (k <= 1.0) {
         Vec3 p = from.lerp(hand, k).add(0.0, Math.sin(k * Math.PI) * 0.8, 0.0);
         c.dot(p, 0.12F, SinclairPalette.IVORY, 240);
         c.dot(p, 0.22F, SinclairView.color(snapshot), 90);
         for (int i = 1; i < 6; i++) {
            double kk = Math.max(0.0, k - i * 0.05);
            c.dot(from.lerp(hand, kk).add(0.0, Math.sin(kk * Math.PI) * 0.8, 0.0), 0.05F, SinclairPalette.GOLD, 180 - i * 30);
         }
      }
   }

   /**
    * BEAM "Footlight Ray": tension — motes rise from a ring of footlights at Sinclair's feet and pour into the
    * card mirror in his left palm; main — the ray from the palm to the synced focus, a ripple runs along it on
    * every damage pulse; hush — the ray thins to a quivering thread while the palm gathers light; climax —
    * the overdrive flare, three times as wide, with a burst ring at the end; finish — embers drift down.
    */
   private static void beam(Ctx c, Player player, HeroPublicSnapshot snapshot, Vec3 hand, double t, int color) {
      Vec3 end = snapshot.actionTarget();
      Vec3 feet = SinclairVfx.pos(player, c.partialTick());
      if (t < ActScript.BEAM_START) {
         float k = (float) (t / ActScript.BEAM_START);
         for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2.0 / 16.0 + t * 0.05;
            Vec3 lamp = feet.add(Math.cos(a) * 1.3, 0.08, Math.sin(a) * 1.3);
            c.dot(lamp, 0.06F, SinclairPalette.GOLD, (int) (90 + 120 * k));
            double u = (t * 0.06 + i * 0.37) % 1.0;
            Vec3 p = lamp.lerp(hand, u * u).add(0.0, Math.sin(u * Math.PI) * 0.4, 0.0);
            c.dot(p, 0.04F, i % 2 == 0 ? SinclairPalette.IVORY : color, (int) (220 * k * (0.4 + 0.6 * u)));
         }

         c.dot(hand, 0.1F + 0.3F * k, SinclairPalette.IVORY, (int) (200 * k));
         c.dot(hand, 0.3F + 0.4F * k, color, (int) (70 * k));
         return;
      }

      if (end == null) {
         return;
      }

      Vec3 dir = end.subtract(hand);
      double len = dir.length();
      Vec3 n = len > 1.0E-3 ? dir.scale(1.0 / len) : new Vec3(0.0, 0.0, 1.0);
      Vec3 side = Math.abs(n.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 up = side.cross(n);
      if (t < ActScript.BEAM_END) {
         float pulse = (float) (0.85 + 0.15 * Math.sin(t * 2.2));
         float ignite = (float) Mth.clamp((t - ActScript.BEAM_START) / 3.0, 0.0, 1.0);
         c.line(hand, hand.add(n.scale(len * ignite)), 0.12F, 0.07F * pulse, SinclairPalette.IVORY, 240);
         c.line(hand, hand.add(n.scale(len * ignite)), 0.3F, 0.22F * pulse, color, 110);
         c.line(hand, hand.add(n.scale(len * ignite)), 0.5F, 0.4F, SinclairPalette.GOLD, 35);
         for (int i = 0; i < 40; i++) {
            double along = (i / 40.0) * len * ignite;
            double a = along * 2.5 - t * 0.9;
            c.dot(hand.add(n.scale(along)).add(side.scale(Math.cos(a) * 0.22)).add(up.scale(Math.sin(a) * 0.22)), 0.035F, SinclairPalette.GOLD, 200);
         }

         // A ripple runs down the ray on each damage pulse.
         double since = (t - ActScript.BEAM_START) % ActScript.BEAM_PERIOD;
         Vec3 ripple = hand.add(n.scale(Math.min(len, since / ActScript.BEAM_PERIOD * len)));
         for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0 + t;
            c.dot(ripple.add(side.scale(Math.cos(a) * 0.32)).add(up.scale(Math.sin(a) * 0.32)), 0.05F, SinclairPalette.IVORY, (int) (200 * (1.0 - since / ActScript.BEAM_PERIOD)));
         }

         c.dot(end, 0.6F * pulse, color, 140);
         c.dot(end, 0.25F, SinclairPalette.IVORY, 220);
         c.dot(hand, 0.3F, SinclairPalette.IVORY, 160);
         return;
      }

      if (t < ActScript.BEAM_BURST) {
         // The hush: a thin quivering thread, the palm gathering light.
         float k = (float) ((t - ActScript.BEAM_END) / (ActScript.BEAM_BURST - ActScript.BEAM_END));
         for (int i = 0; i < 48; i++) {
            double along = i / 48.0 * len;
            double wob = Math.sin(along * 3.0 + t * 3.1) * 0.04 * (1.0 - k);
            c.dot(hand.add(n.scale(along)).add(side.scale(wob)), 0.025F, SinclairPalette.IVORY, 200);
         }

         for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0 + t * 0.8;
            double r = 0.7 * (1.0 - k) + 0.12;
            c.dot(hand.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)), 0.04F, SinclairPalette.GOLD, (int) (120 + 120 * k));
         }

         c.dot(hand, 0.2F + 0.5F * k, SinclairPalette.IVORY, (int) (120 + 120 * k));
         return;
      }

      if (t < ActScript.BEAM_BURST_END) {
         // The overdrive flare: wide, white-hot, fading out.
         float b = (float) ((t - ActScript.BEAM_BURST) / (ActScript.BEAM_BURST_END - ActScript.BEAM_BURST));
         float fade = 1.0F - b * b;
         c.line(hand, end, 0.1F, 0.2F, SinclairPalette.IVORY, (int) (255 * fade));
         c.line(hand, end, 0.25F, 0.55F, color, (int) (150 * fade));
         c.line(hand, end, 0.45F, 1.0F, SinclairPalette.GOLD, (int) (60 * fade));
         for (int i = 0; i < 64; i++) {
            double along = (i / 64.0) * len;
            double a = along * 1.6 - t * 1.4;
            double r = 0.5 + 0.25 * Math.sin(t + i);
            c.dot(hand.add(n.scale(along)).add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)), 0.06F, SinclairPalette.GOLD, (int) (220 * fade));
         }

         double ring = 0.4 + b * 2.4;
         for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2.0 / 28.0;
            c.dot(end.add(side.scale(Math.cos(a) * ring)).add(up.scale(Math.sin(a) * ring)), 0.09F, SinclairPalette.IVORY, (int) (230 * fade));
         }

         c.dot(end, 1.4F * fade, color, (int) (150 * fade));
         c.dot(hand, 0.7F * fade, SinclairPalette.IVORY, (int) (220 * fade));
         return;
      }

      if (t < ActScript.BEAM.pauseEnd() + 6) {
         // Embers drifting down along where the ray was.
         float k = (float) ((t - ActScript.BEAM_BURST_END) / (ActScript.BEAM.pauseEnd() + 6 - ActScript.BEAM_BURST_END));
         for (int i = 0; i < 24; i++) {
            double along = ((i * 0.618) % 1.0) * len;
            c.dot(hand.add(n.scale(along)).add(0.0, -k * (0.6 + (i % 4) * 0.3), 0.0), 0.04F, i % 2 == 0 ? SinclairPalette.GOLD : color, (int) (180 * (1.0 - k)));
         }
      }
   }

   /**
    * Copy cinematic in the world (what bystanders and the staged cameras see): the volunteer stands in a
    * slowly turning lavender clock ring (held), a ribbon of motes runs from them into the card Sinclair holds
    * high, and the card glows over his palm until it folds home.
    */
   private static void copy(Ctx c, Player player, HeroPublicSnapshot snapshot, ClientLevel level, Vec3 hand, double t) {
      float in = (float) dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic.cameraWeight(t);
      int color = SinclairView.color(snapshot);
      Entity volunteer = snapshot.controlTargetId() >= 0 ? level.getEntity(snapshot.controlTargetId()) : null;
      Vec3 feet = SinclairVfx.pos(player, c.partialTick());
      c.ring(feet.add(0.0, 0.05, 0.0), 0.9, 28, -t * 0.03, 0.04F, SinclairPalette.GOLD, (int) (170 * in));
      if (volunteer != null) {
         Vec3 vf = SinclairVfx.pos(volunteer, c.partialTick());
         double r = Math.max(0.8, volunteer.getBbWidth() * 1.2);
         c.ring(vf.add(0.0, 0.05, 0.0), r, 36, t * 0.02, 0.045F, SinclairPalette.LAVENDER, (int) (190 * in));
         for (int h = 0; h < 12; h++) {
            double a = t * 0.02 + h * Math.PI / 6.0;
            c.dot(vf.add(Math.cos(a) * r * 0.85, 0.05, Math.sin(a) * r * 0.85), 0.07F, SinclairPalette.IVORY, (int) (200 * in));
         }

         if (t < dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic.FOLD_START) {
            Vec3 chest = vf.add(0.0, volunteer.getBbHeight() * 0.6, 0.0);
            for (int i = 0; i < 18; i++) {
               double u = (t * 0.035 + i / 18.0) % 1.0;
               Vec3 p = chest.lerp(hand, u).add(0.0, Math.sin(u * Math.PI) * 0.9, 0.0);
               c.dot(p, 0.045F, i % 3 == 0 ? SinclairPalette.GOLD : color, (int) (200 * in * Math.sin(u * Math.PI)));
            }
         }
      }

      float fold = (float) dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic.cardFlight(t);
      c.dot(hand.add(0.0, 0.18, 0.0), 0.35F * (1.0F - 0.6F * fold), color, (int) (110 * in));
      c.dot(hand.add(0.0, 0.18, 0.0), 0.12F, SinclairPalette.IVORY, (int) (220 * in));
   }

   /** Shape of the snapshot's act (the poser uses the move, the HUD the shape). */
   static ActShape actShape(HeroPublicSnapshot snapshot) {
      return SinclairView.shape(snapshot);
   }
}

package dev.baranhan.viltrumitecore.client.thanos;

import static dev.baranhan.viltrumitecore.client.thanos.ThanosFx.*;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Mind;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Power;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Reality;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Time;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Vector3f;

/**
 * Geometry and per-effect drawing of {@link ThanosFx} (split out to keep both files small): flat
 * rings, walls, ribbons, discs and pixels in camera-relative POSITION_COLOR quads, and the look of
 * every stone effect, the Space gates, the Reality zone, the Time dome and the arrival portal.
 */
final class ThanosFxDraw {
   private ThanosFxDraw() {
   }

   // ---- geometry helpers ------------------------------------------------------------------------

   static Vec3 arrivalCenter(Fx f) {
      Vec3 look = new Vec3(f.dir.x, 0.0, f.dir.z);
      look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
      return f.pos.add(look.scale(-0.6)).add(0.0, 2.2, 0.0);
   }

   /** Portal opening of the arrival 0..1: opens fast, holds while he steps through, closes. */
   static float arrivalOpen(float age) {
      if (age < 14.0F) {
         return ThanosPose.smooth(age / 14.0F);
      }

      return age < 56.0F ? 1.0F : 1.0F - ThanosPose.smooth((age - 56.0F) / 16.0F);
   }

   /** Point on a circle of {@code radius} around {@code c} in the plane normal to {@code normal}. */
   static Vec3 ringPoint(Vec3 c, Vec3 normal, double radius, double ang) {
      Vec3 n = normal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : normal.normalize();
      Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
      Vec3 a = n.cross(u).normalize();
      Vec3 b = n.cross(a).normalize();
      return c.add(a.scale(Math.cos(ang) * radius)).add(b.scale(Math.sin(ang) * radius));
   }

   @Nullable
   static Vec3[] tetherEnds(ClientLevel level, Fx f, float partial) {
      Entity hero = level.getEntity(f.b);
      if (hero == null) {
         return null;
      }

      Vec3 end = hero.getPosition(partial).add(0.0, hero.getBbHeight() * 0.6, 0.0);
      Vec3 start = f.pos;
      if (f.power > 0.5F) {
         ThanosRenderer.Anchors a = ThanosRenderer.fresh(f.a);
         if (a != null && a.stones()[ThanosStone.SOUL.ordinal()] != null) {
            start = a.stones()[ThanosStone.SOUL.ordinal()];
         }
      }

      return new Vec3[]{start, end};
   }

   static void quad(BufferBuilder buf, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
      double x3, double y3, double z3, int rgb, int a0, int a1) {
      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      buf.vertex(x0 - cam.x, y0 - cam.y, z0 - cam.z).color(r, g, b, a0).endVertex();
      buf.vertex(x1 - cam.x, y1 - cam.y, z1 - cam.z).color(r, g, b, a0).endVertex();
      buf.vertex(x2 - cam.x, y2 - cam.y, z2 - cam.z).color(r, g, b, a1).endVertex();
      buf.vertex(x3 - cam.x, y3 - cam.y, z3 - cam.z).color(r, g, b, a1).endVertex();
   }

   /** Flat annulus on the ground, optionally bent into a Reality shape. */
   static void flatRing(BufferBuilder buf, Vec3 cam, Vec3 c, double r0, double r1, int segs, int rgb, int alpha, int shape) {
      if (alpha <= 2 || r1 <= 0.0) {
         return;
      }

      for (int i = 0; i < segs; i++) {
         double a0 = Math.PI * 2.0 * i / segs;
         double a1 = Math.PI * 2.0 * (i + 1) / segs;
         double k0 = Reality.shapeScale(a0, shape);
         double k1 = Reality.shapeScale(a1, shape);
         quad(buf, cam, c.x + Math.cos(a0) * r0 * k0, c.y, c.z + Math.sin(a0) * r0 * k0, c.x + Math.cos(a1) * r0 * k1, c.y, c.z + Math.sin(a1) * r0 * k1,
            c.x + Math.cos(a1) * r1 * k1, c.y, c.z + Math.sin(a1) * r1 * k1, c.x + Math.cos(a0) * r1 * k0, c.y, c.z + Math.sin(a0) * r1 * k0, rgb, alpha, alpha);
      }
   }

   /** Vertical band around {@code c}: full alpha at the ground, fading upward. */
   static void wall(BufferBuilder buf, Vec3 cam, Vec3 c, double radius, double height, int segs, int rgb, int alpha, int shape) {
      if (alpha <= 2 || radius <= 0.05) {
         return;
      }

      for (int i = 0; i < segs; i++) {
         double a0 = Math.PI * 2.0 * i / segs;
         double a1 = Math.PI * 2.0 * (i + 1) / segs;
         double k0 = radius * Reality.shapeScale(a0, shape);
         double k1 = radius * Reality.shapeScale(a1, shape);
         double x0 = c.x + Math.cos(a0) * k0;
         double z0 = c.z + Math.sin(a0) * k0;
         double x1 = c.x + Math.cos(a1) * k1;
         double z1 = c.z + Math.sin(a1) * k1;
         quad(buf, cam, x0, c.y, z0, x1, c.y, z1, x1, c.y + height, z1, x0, c.y + height, z0, rgb, alpha, 0);
      }
   }

   /** Camera-facing strip between two points. */
   static void ribbon(BufferBuilder buf, Vec3 cam, Vec3 from, Vec3 to, double width, int rgb, int a0, int a1) {
      Vec3 d = to.subtract(from);
      Vec3 view = from.add(to).scale(0.5).subtract(cam);
      Vec3 side = d.cross(view);
      if (side.lengthSqr() < 1.0E-8) {
         return;
      }

      side = side.normalize().scale(width * 0.5);
      quad(buf, cam, from.x - side.x, from.y - side.y, from.z - side.z, from.x + side.x, from.y + side.y, from.z + side.z, to.x + side.x, to.y + side.y,
         to.z + side.z, to.x - side.x, to.y - side.y, to.z - side.z, rgb, a0, a1);
   }

   /** Ring of camera-facing strips in the plane normal to {@code normal}. */
   static void ring(BufferBuilder buf, Vec3 cam, Vec3 c, Vec3 normal, double radius, double width, int segs, int rgb, int alpha, double phase) {
      if (alpha <= 2 || radius <= 0.02) {
         return;
      }

      Vec3 prev = ringPoint(c, normal, radius, phase);
      for (int i = 1; i <= segs; i++) {
         Vec3 next = ringPoint(c, normal, radius, phase + Math.PI * 2.0 * i / segs);
         ribbon(buf, cam, prev, next, width, rgb, alpha, alpha);
         prev = next;
      }
   }

   /** Filled disc (fan of thin quads) in the plane normal to {@code normal}. */
   static void disc(BufferBuilder buf, Vec3 cam, Vec3 c, Vec3 normal, double radius, int segs, int rgb, int inner, int outer) {
      if (radius <= 0.02 || inner <= 2 && outer <= 2) {
         return;
      }

      for (int i = 0; i < segs; i++) {
         Vec3 p0 = ringPoint(c, normal, radius, Math.PI * 2.0 * i / segs);
         Vec3 p1 = ringPoint(c, normal, radius, Math.PI * 2.0 * (i + 1) / segs);
         int r = rgb >> 16 & 255;
         int g = rgb >> 8 & 255;
         int b = rgb & 255;
         buf.vertex(c.x - cam.x, c.y - cam.y, c.z - cam.z).color(r, g, b, inner).endVertex();
         buf.vertex(c.x - cam.x, c.y - cam.y, c.z - cam.z).color(r, g, b, inner).endVertex();
         buf.vertex(p0.x - cam.x, p0.y - cam.y, p0.z - cam.z).color(r, g, b, outer).endVertex();
         buf.vertex(p1.x - cam.x, p1.y - cam.y, p1.z - cam.z).color(r, g, b, outer).endVertex();
      }
   }

   static void pixel(BufferBuilder buf, Vec3 cam, Camera camera, Vec3 at, float size, int rgb, int alpha) {
      if (alpha > 2) {
         PixelVfx.billboardPixel(buf, cam, camera, at, size, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Math.min(255, alpha));
      }
   }

   /** Dashed ring of pixels on the ground (telegraphs). */
   static void dashes(BufferBuilder buf, Vec3 cam, Camera camera, Vec3 c, double radius, float size, int rgb, int alpha, double spin) {
      int n = Mth.clamp((int)(radius * 5.0), 12, 160);
      for (int i = 0; i < n; i++) {
         if ((i & 3) == 3) {
            continue;
         }

         double ang = spin + Math.PI * 2.0 * i / n;
         pixel(buf, cam, camera, new Vec3(c.x + Math.cos(ang) * radius, c.y + 0.08, c.z + Math.sin(ang) * radius), size, rgb, alpha);
      }
   }

   /** Flat strip along the ground between two points (fissures, clock hands). */
   static void groundStrip(BufferBuilder buf, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, double width, int rgb, int alpha) {
      double dx = x1 - x0;
      double dz = z1 - z0;
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 1.0E-4 || alpha <= 2) {
         return;
      }

      double sx = -dz / len * width * 0.5;
      double sz = dx / len * width * 0.5;
      quad(buf, cam, x0 - sx, y0, z0 - sz, x0 + sx, y0, z0 + sz, x1 + sx, y1, z1 + sz, x1 - sx, y1, z1 - sz, rgb, alpha, alpha);
   }

   /** 0..1 envelope of an effect: in over {@code in} ticks, out over the last {@code out}. */
   static float envelope(Fx f, float partial, float in, float out) {
      float t = f.age + partial;
      return Mth.clamp(Math.min(t / Math.max(1.0F, in), (f.life - t) / Math.max(1.0F, out)), 0.0F, 1.0F);
   }

   static float areaRadius(ThanosMove move) {
      return switch (move) {
         case POWER_WAVE -> (float)Power.WAVE_MAX;
         case MIND_PULSE -> (float)Mind.RING_MAX;
         case SOUL_NOVA, COMBO_ETERNITY -> (float)dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Soul.NOVA_RADIUS;
         case COMBO_TIME_CRUSH -> (float)Time.DOME_RADIUS;
         case COMBO_TRINITY -> 12.0F;
         default -> 0.0F;
      };
   }

   static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

   static void drawFx(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, Fx f, float partial, boolean glow) {
      float t = f.t(partial);
      float time = f.time(partial);
      int fade = (int)(220.0F * (1.0F - t));
      switch (f.kind) {
         case ThanosFxS2CPacket.MELEE_HIT -> {
            if (!glow) {
               return;
            }

            if (f.power > 0.0F) {
               ring(buf, cam, f.pos, f.dir, 0.4 + 1.8 * t, 0.14, 18, HOT, fade, 0.0);
               ring(buf, cam, f.pos, f.dir, 0.2 + 1.1 * t, 0.08, 14, PURPLE, fade, 0.4);
            } else {
               Vec3 d = new Vec3(f.dir.x, 0.0, f.dir.z);
               if (d.lengthSqr() > 1.0E-4) {
                  d = d.normalize();
                  Vec3 chest = f.pos.subtract(d.scale(2.4));
                  double base = Math.atan2(d.z, d.x);
                  Vec3 prev = null;
                  for (int i = 0; i <= 10; i++) {
                     double ang = base - 1.1 + 2.2 * i / 10.0;
                     Vec3 p = chest.add(Math.cos(ang) * 2.3, -0.2 + 0.4 * i / 10.0, Math.sin(ang) * 2.3);
                     if (prev != null && i / 10.0F <= t * 1.8F) {
                        ribbon(buf, cam, prev, p, 0.22, 0xE8DDFF, (int)(fade * 0.6F), (int)(fade * 0.6F));
                     }

                     prev = p;
                  }
               }
            }
         }
         case ThanosFxS2CPacket.SLAM -> {
            if (!glow) {
               return;
            }

            float r = f.power * ThanosPose.smooth(Math.min(1.0F, t * 1.6F));
            int rgb = f.extra == 1 ? PURPLE : HOT;
            Vec3 c = f.pos.add(0.0, 0.06, 0.0);
            flatRing(buf, cam, c, Math.max(0.0, r - 0.6), r, 48, rgb, (int)(fade * 0.9F), 0);
            wall(buf, cam, c, r, 1.0 * (1.0F - t), 48, rgb, (int)(fade * 0.5F), 0);
         }
         case ThanosFxS2CPacket.POWER_PUNCH -> {
            if (!glow) {
               return;
            }

            Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? UP : f.dir.normalize();
            Vec3 start = f.pos.subtract(d.scale(Math.min(f.power, 9.0F)));
            ribbon(buf, cam, start, f.pos, 0.9 * (1.0F - t), PURPLE, (int)(fade * 0.8F), fade);
            ribbon(buf, cam, start, f.pos, 0.3 * (1.0F - t), HOT, fade, fade);
            for (int i = 1; i <= 3; i++) {
               ring(buf, cam, start.lerp(f.pos, i / 3.5), d, 0.4 + 1.6 * t + i * 0.15, 0.1, 18, PURPLE, (int)(fade * 0.8F), i);
            }

            pixel(buf, cam, camera, f.pos, 0.3F + 1.4F * (1.0F - t), PURPLE, (int)(fade * 0.7F));
            pixel(buf, cam, camera, f.pos, 0.15F + 0.6F * (1.0F - t), HOT, fade);
         }
         case ThanosFxS2CPacket.POWER_WAVE -> {
            if (!glow) {
               return;
            }

            double r = Math.min(Power.WAVE_MAX, time * Power.WAVE_SPEED);
            if (r >= Power.WAVE_MAX - 0.05) {
               return;
            }

            int a = (int)(230.0 * (1.0 - r / Power.WAVE_MAX * 0.65));
            Vec3 c = f.pos.add(0.0, 0.06, 0.0);
            wall(buf, cam, c, r, Power.WAVE_HEIGHT, 80, PURPLE, (int)(a * 0.7F), f.extra);
            wall(buf, cam, c, r, 0.7, 80, HOT, (int)(a * 0.55F), f.extra);
            flatRing(buf, cam, c, Math.max(0.0, r - Power.WAVE_BAND * 0.5), r + Power.WAVE_BAND * 0.5, 80, PURPLE, (int)(a * 0.45F), f.extra);
         }
         case ThanosFxS2CPacket.FISSURES -> {
            if (f.lines == null) {
               return;
            }

            float env = f.age < 6 ? (f.age + partial) / 6.0F : 1.0F - ThanosPose.smooth((time - 60.0F) / 60.0F);
            for (float[] s : f.lines) {
               double w = 0.06 + 0.22 * s[6];
               if (glow) {
                  float flicker = 0.75F + 0.25F * (float)Math.sin(time * 0.6 + s[0] * 3.0 + s[2] * 2.0);
                  groundStrip(buf, cam, s[0], s[1] + 0.012, s[2], s[3], s[4] + 0.012, s[5], w * 0.55, PURPLE, (int)(220.0F * env * flicker));
                  groundStrip(buf, cam, s[0], s[1] + 0.014, s[2], s[3], s[4] + 0.014, s[5], w * 0.18, HOT, (int)(160.0F * env * flicker));
               } else {
                  groundStrip(buf, cam, s[0], s[1] + 0.008, s[2], s[3], s[4] + 0.008, s[5], w * 1.3, 0x0A0410, (int)(210.0F * env));
               }
            }
         }
         case ThanosFxS2CPacket.SPACE_PORTAL -> portal(buf, cam, camera, f, partial, glow);
         case ThanosFxS2CPacket.SPACE_STEP -> {
            if (!glow) {
               return;
            }

            ribbon(buf, cam, f.pos, f.pos.add(0.0, 4.4, 0.0), 1.6 * (1.0F - t), BLUE, fade, 0);
            ribbon(buf, cam, f.pos, f.pos.add(0.0, 4.0, 0.0), 0.5 * (1.0F - t), HOT, fade, 0);
            double r = 0.5 + 2.8 * t;
            flatRing(buf, cam, f.pos.add(0.0, 0.06, 0.0), Math.max(0.0, r - 0.3), r, 32, BLUE, fade, 0);
         }
         case ThanosFxS2CPacket.SPACE_PULL -> {
            if (!glow) {
               return;
            }

            Entity target = level.getEntity(f.b);
            ThanosRenderer.Anchors a = ThanosRenderer.fresh(f.a);
            if (target == null || a == null) {
               return;
            }

            Vec3 from = a.gauntlet();
            Vec3 to = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0);
            float env = envelope(f, partial, 3.0F, 4.0F);
            ribbon(buf, cam, from, to, 0.1, BLUE, (int)(200.0F * env), (int)(200.0F * env));
            ribbon(buf, cam, from, to, 0.035, HOT, (int)(200.0F * env), (int)(200.0F * env));
            for (int k = 0; k < 4; k++) {
               float frac = (float)((time * 0.09 + k / 4.0) % 1.0);
               ring(buf, cam, to.lerp(from, frac), from.subtract(to), 0.5 * (1.0F - frac) + 0.12, 0.05, 12, BLUE, (int)(180.0F * env), k);
            }
         }
         case ThanosFxS2CPacket.SPACE_WARD -> {
            if (!glow) {
               return;
            }

            Entity th = level.getEntity(f.a);
            Vec3 c = th != null ? th.getPosition(partial).add(0.0, 2.1, 0.0) : f.pos;
            float env = envelope(f, partial, 6.0F, 8.0F);
            int a = (int)(150.0F * env);
            PixelVfx.sphereShell(buf, cam, camera, c, f.power, time / 20.0F, BLUE >> 16 & 255, BLUE >> 8 & 255, BLUE & 255, a);
            ring(buf, cam, c, UP, f.power, 0.08, 72, BLUE, (int)(a * 0.8F), time * 0.02);
            ring(buf, cam, c, new Vec3(1.0, 0.0, 0.0), f.power, 0.05, 72, 0xBFD8FF, (int)(a * 0.4F), -time * 0.02);
         }
         case ThanosFxS2CPacket.SPACE_REDIRECT -> {
            if (glow) {
               Vec3 n = f.dir.lengthSqr() < 1.0E-6 ? UP : f.dir;
               ring(buf, cam, f.pos.add(0.0, 1.0, 0.0), n, 0.3 + 1.3 * t, 0.12, 18, BLUE, fade, 0.0);
            }
         }
         case ThanosFxS2CPacket.REALITY_ZONE -> zone(buf, cam, camera, f, partial, glow);
         case ThanosFxS2CPacket.REALITY_MIRAGE -> {
            if (glow) {
               float env = envelope(f, partial, 6.0F, 6.0F);
               flatRing(buf, cam, f.pos.add(0.0, 0.06, 0.0), 0.7, 1.0, 24, f.power > 0.5F ? ORANGE : RED, (int)(90.0F * env), 0);
            }
         }
         case ThanosFxS2CPacket.MIRAGE_POP -> {
            if (glow) {
               double r = (0.4 + 2.4 * t) * (f.power > 2.0F ? 1.6 : 1.0);
               Vec3 c = f.pos.add(0.0, 1.8, 0.0);
               ring(buf, cam, c, c.subtract(cam), r, 0.14, 20, RED, fade, 0.0);
               flatRing(buf, cam, f.pos.add(0.0, 0.06, 0.0), Math.max(0.0, r - 0.4), r, 24, RED, (int)(fade * 0.6F), 0);
            }
         }
         case ThanosFxS2CPacket.REALITY_SHARD -> {
            if (!glow) {
               return;
            }

            RandomSource r = RandomSource.create(f.seed);
            Vec3 prev = f.pos;
            for (int i = 1; i <= 6; i++) {
               Vec3 next = f.pos.add(f.dir.scale(i / 6.0));
               if (i < 6) {
                  next = next.add((r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5);
               }

               ribbon(buf, cam, prev, next, 0.16, RED, fade, fade);
               ribbon(buf, cam, prev, next, 0.05, HOT, fade, fade);
               prev = next;
            }
         }
         case ThanosFxS2CPacket.TIME_DOME -> dome(buf, cam, camera, f, partial, glow);
         case ThanosFxS2CPacket.TIME_REWIND -> {
            if (!glow) {
               return;
            }

            Vec3 from = f.pos.add(0.0, 1.9, 0.0);
            Vec3 to = from.add(f.dir);
            ring(buf, cam, from, UP, 0.3 + 1.6 * (1.0F - t), 0.1, 28, GREEN, fade, time * 0.1);
            ring(buf, cam, to, UP, 0.3 + 1.6 * t, 0.1, 28, GREEN, fade, -time * 0.1);
            PixelVfx.beamDots(buf, cam, camera, from, to, 0.45F, 0.05F, GREEN >> 16 & 255, GREEN >> 8 & 255, GREEN & 255, (int)(fade * 0.7F));
         }
         case ThanosFxS2CPacket.MIND_GAZE_CHARGE -> {
            if (!glow) {
               return;
            }

            ThanosRenderer.Anchors a = ThanosRenderer.fresh(f.a);
            Vec3 eyes = a != null ? a.head().add(0.0, 0.18, 0.0) : f.pos;
            float k = t;
            pixel(buf, cam, camera, eyes, 0.12F + 0.5F * k, YELLOW, (int)(70.0F + 110.0F * k));
            pixel(buf, cam, camera, eyes, 0.06F + 0.16F * k, HOT, (int)(140.0F + 100.0F * k));
            Entity th = level.getEntity(f.a);
            if (th != null && ((ticks >> 1) & 1) == 0) {
               Vec3 look = th.getViewVector(partial);
               for (int i = 0; i < 5; i++) {
                  double ang = i * Math.PI * 2.0 / 5.0 + time * 0.05;
                  Vec3 side = ringPoint(Vec3.ZERO, look, 0.7, ang);
                  Vec3 end = eyes.add(look.add(side).normalize().scale(f.power * k));
                  PixelVfx.beamDots(buf, cam, camera, eyes, end, 1.0F, 0.04F, YELLOW >> 16 & 255, YELLOW >> 8 & 255, YELLOW & 255, 60);
               }
            }
         }
         case ThanosFxS2CPacket.MIND_GAZE_HIT -> {
            if (!glow) {
               return;
            }

            Vec3 d = f.dir.lengthSqr() < 1.0E-6 ? UP : f.dir.normalize();
            double range = Mind.GAZE_RANGE;
            for (int i = 1; i <= 7; i++) {
               double dist = range * i / 7.0 * (0.35 + 0.65 * t);
               ring(buf, cam, f.pos.add(d.scale(dist)), d, dist * 0.62, 0.14, 28, YELLOW, (int)(fade * (1.0F - i / 8.0F)), i);
            }

            ribbon(buf, cam, f.pos, f.pos.add(d.scale(range * (0.4 + 0.6 * t))), 1.2 * (1.0F - t), YELLOW, fade, 0);
         }
         case ThanosFxS2CPacket.MIND_RESIST -> {
            if (glow && f.b != Minecraft.getInstance().player.getId()) {
               ring(buf, cam, f.pos, f.pos.subtract(cam), 0.5 + 0.9 * t, 0.08, 16, YELLOW, fade, 0.0);
            }
         }
         case ThanosFxS2CPacket.MIND_PULSE -> {
            if (!glow) {
               return;
            }

            Vec3 c = f.pos.add(0.0, 0.06, 0.0);
            for (int ring = 0; ring < Math.max(1, f.extra); ring++) {
               double r = (time - ring * Mind.RING_GAP) * Mind.RING_SPEED;
               if (r <= 0.5 || r >= Mind.RING_MAX) {
                  continue;
               }

               int a = (int)(210.0 * (1.0 - r / Mind.RING_MAX));
               wall(buf, cam, c, r, Mind.RING_HEIGHT, 72, YELLOW, (int)(a * 0.6F), 0);
               flatRing(buf, cam, c, Math.max(0.0, r - Mind.RING_BAND * 0.5), r + Mind.RING_BAND * 0.5, 72, YELLOW, (int)(a * 0.35F), 0);
            }
         }
         case ThanosFxS2CPacket.SOUL_TETHER -> {
            if (!glow) {
               return;
            }

            Vec3[] ends = tetherEnds(level, f, partial);
            if (ends == null) {
               return;
            }

            float env = envelope(f, partial, 4.0F, 6.0F);
            boolean real = f.power > 0.5F;
            float flicker = real ? 1.0F : 0.45F + 0.35F * (float)Math.abs(Math.sin(time * 0.9));
            int a = (int)(210.0F * env * flicker);
            if (real) {
               ribbon(buf, cam, ends[0], ends[1], 0.14, ORANGE, a, a);
               ribbon(buf, cam, ends[0], ends[1], 0.045, HOT, a, a);
            } else {
               for (int i = 0; i < 12; i += 2) {
                  ribbon(buf, cam, ends[0].lerp(ends[1], i / 12.0), ends[0].lerp(ends[1], (i + 1) / 12.0), 0.1, ORANGE, a, a);
               }
            }

            for (int k = 0; k < 6; k++) {
               float frac = (float)((time * 0.05 + k / 6.0) % 1.0);
               pixel(buf, cam, camera, ends[1].lerp(ends[0], frac), 0.07F, ORANGE, a);
            }
         }
         case ThanosFxS2CPacket.SOUL_TETHER_BREAK -> {
            if (glow) {
               Vec3 c = f.pos.add(0.0, 1.2, 0.0);
               ring(buf, cam, c, c.subtract(cam), 0.3 + 1.5 * t, 0.1, 18, ORANGE, fade, 0.0);
            }
         }
         case ThanosFxS2CPacket.SOUL_NOVA -> {
            if (!glow) {
               return;
            }

            float r = f.power * ThanosPose.smooth(Math.min(1.0F, t * 1.5F));
            Vec3 c = f.pos.add(0.0, 0.06, 0.0);
            PixelVfx.sphereShell(buf, cam, camera, f.pos.add(0.0, 1.2, 0.0), r, time / 20.0F, ORANGE >> 16 & 255, ORANGE >> 8 & 255, ORANGE & 255,
               (int)(fade * 0.7F));
            flatRing(buf, cam, c, Math.max(0.0, r - 0.7), r, 64, ORANGE, (int)(fade * 0.8F), 0);
            wall(buf, cam, c, r, 2.2, 64, ORANGE, (int)(fade * 0.45F), 0);
         }
         case ThanosFxS2CPacket.ARRIVAL -> arrival(buf, cam, camera, f, partial, glow);
         case ThanosFxS2CPacket.STUN -> {
            if (!glow) {
               return;
            }

            ThanosRenderer.Anchors a = ThanosRenderer.fresh(f.a);
            Vec3 head = a != null ? a.head().add(0.0, 0.55, 0.0) : f.pos.add(0.0, 1.5, 0.0);
            float env = envelope(f, partial, 3.0F, 8.0F);
            for (int i = 0; i < 5; i++) {
               double ang = time * 0.25 + i * Math.PI * 2.0 / 5.0;
               Vec3 p = head.add(Math.cos(ang) * 0.75, Math.sin(time * 0.3 + i) * 0.08, Math.sin(ang) * 0.75);
               PixelVfx.crossGlow(buf, cam, camera, p, 0.07F, 255, 236, 170, (int)(220.0F * env));
            }
         }
         default -> {
         }
      }
   }

   static void portal(BufferBuilder buf, Vec3 cam, Camera camera, Fx f, float partial, boolean glow) {
      float open = envelope(f, partial, 6.0F, 6.0F);
      double radius = f.power * 0.85 * open;
      Vec3 c = f.power >= 2.0F ? f.pos.add(0.0, f.power * 0.85 + 0.1, 0.0) : f.pos;
      Vec3 n = f.dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : f.dir;
      portalDisc(buf, cam, camera, c, n, radius, open, f.time(partial), f.seed, glow);
   }

   /** A Space stone gate: a black disc full of stars inside a turning blue rim. */
   static void portalDisc(BufferBuilder buf, Vec3 cam, Camera camera, Vec3 c, Vec3 n, double radius, float open, float time, long seed, boolean glow) {
      if (radius < 0.05) {
         return;
      }

      if (!glow) {
         disc(buf, cam, c, n, radius * 0.94, 32, 0x02040C, (int)(235.0F * open), (int)(200.0F * open));
         return;
      }

      ring(buf, cam, c, n, radius, 0.08 + radius * 0.09, 40, BLUE, (int)(230.0F * open), time * 0.05);
      ring(buf, cam, c, n, radius * 0.82, 0.05 + radius * 0.03, 32, 0xBFD8FF, (int)(130.0F * open), -time * 0.08);
      ring(buf, cam, c, n, radius * 1.1, 0.04, 40, BLUE, (int)(80.0F * open), time * 0.03);
      for (int i = 0; i < 20; i++) {
         double ang = time * 0.12 + i * 0.33;
         double rr = radius * (1.0 - (time * 0.025 + i / 20.0) % 1.0);
         pixel(buf, cam, camera, ringPoint(c, n, rr, ang), 0.05F + 0.02F * (float)radius, BLUE, (int)(200.0F * open));
      }

      RandomSource r = RandomSource.create(seed);
      for (int i = 0; i < 14; i++) {
         double rr = radius * 0.85 * Math.sqrt(r.nextDouble());
         double ang = r.nextDouble() * Math.PI * 2.0;
         float tw = 0.5F + 0.5F * (float)Math.sin(time * 0.3 + i * 1.7);
         pixel(buf, cam, camera, ringPoint(c, n, rr, ang).add(n.normalize().scale(-0.02)), 0.025F, 0xFFFFFF, (int)(200.0F * open * tw));
      }
   }

   static void zone(BufferBuilder buf, Vec3 cam, Camera camera, Fx f, float partial, boolean glow) {
      Vec3 c = f.pos.add(0.0, 0.05, 0.0);
      float time = f.time(partial);
      if (f.power <= 0.0F) {
         if (glow) {
            float t = f.t(partial);
            int a = (int)(110.0F + 90.0F * (float)Math.abs(Math.sin(time * 0.4)));
            dashes(buf, cam, camera, c, Reality.ZONE_RADIUS, 0.08F, RED, a, time * 0.03);
            flatRing(buf, cam, c, 0.0, Reality.ZONE_RADIUS * t, 40, RED, 50, 0);
         }

         return;
      }

      float scale = Reality.zoneScale(f.age);
      double r = f.power * scale;
      if (!glow) {
         disc(buf, cam, c, UP, r, 36, 0x26000A, (int)(170.0F * scale), (int)(110.0F * scale));
         return;
      }

      flatRing(buf, cam, c.add(0.0, 0.02, 0.0), Math.max(0.0, r - 0.25), r, 40, RED, (int)(200.0F * scale), 0);
      float pulse = (f.age % Reality.ZONE_PULSE) / (float)Reality.ZONE_PULSE;
      flatRing(buf, cam, c.add(0.0, 0.02, 0.0), Math.max(0.0, r * pulse - 0.15), r * pulse, 40, 0xFF6070, (int)(120.0F * scale * (1.0F - pulse)), 0);
      RandomSource rand = RandomSource.create(f.seed + ticks / 2);
      for (int i = 0; i < 12; i++) {
         double rr = r * Math.sqrt(rand.nextDouble());
         double ang = rand.nextDouble() * Math.PI * 2.0;
         double s = 0.15 + rand.nextDouble() * 0.35;
         double x = c.x + Math.cos(ang) * rr;
         double z = c.z + Math.sin(ang) * rr;
         quad(buf, cam, x - s, c.y + 0.03, z - s, x + s, c.y + 0.03, z - s, x + s, c.y + 0.03, z + s, x - s, c.y + 0.03, z + s, rand.nextBoolean() ? RED : 0xFF6070,
            (int)(150.0F * scale), (int)(150.0F * scale));
      }
   }

   static void dome(BufferBuilder buf, Vec3 cam, Camera camera, Fx f, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      Vec3 c = f.pos.add(0.0, 0.06, 0.0);
      float time = f.time(partial);
      if (f.power <= 0.0F) {
         // the charge: a clock face whose hand completes one turn when the dome falls
         double r = Time.DOME_RADIUS;
         int a = (int)(150.0F + 70.0F * f.t(partial));
         dashes(buf, cam, camera, c, r, 0.07F, GREEN, a, -time * 0.02);
         clockFace(buf, cam, c, r, a);
         double hand = -Math.PI / 2.0 + Math.PI * 2.0 * f.t(partial);
         groundStrip(buf, cam, c.x, c.y + 0.01, c.z, c.x + Math.cos(hand) * r * 0.85, c.y + 0.01, c.z + Math.sin(hand) * r * 0.85, 0.16, GREEN, a);
         return;
      }

      float env = envelope(f, partial, 6.0F, 10.0F);
      int a = (int)(160.0F * env);
      PixelVfx.domeShell(buf, cam, camera, f.pos, f.power, time / 20.0F, GREEN >> 16 & 255, GREEN >> 8 & 255, GREEN & 255, a);
      flatRing(buf, cam, c, Math.max(0.0, f.power - 0.3), f.power, 64, GREEN, (int)(a * 0.8F), 0);
      clockFace(buf, cam, c, f.power, (int)(a * 0.8F));
      double slow = -Math.PI / 2.0 + time * 0.012;
      double fast = -Math.PI / 2.0 + time * 0.06;
      groundStrip(buf, cam, c.x, c.y + 0.01, c.z, c.x + Math.cos(slow) * f.power * 0.5, c.y + 0.01, c.z + Math.sin(slow) * f.power * 0.5, 0.22, GREEN, a);
      groundStrip(buf, cam, c.x, c.y + 0.01, c.z, c.x + Math.cos(fast) * f.power * 0.85, c.y + 0.01, c.z + Math.sin(fast) * f.power * 0.85, 0.12, GREEN, a);
   }

   static void clockFace(BufferBuilder buf, Vec3 cam, Vec3 c, double r, int alpha) {
      for (int i = 0; i < 12; i++) {
         double ang = Math.PI * 2.0 * i / 12.0;
         double inner = r * (i % 3 == 0 ? 0.8 : 0.88);
         groundStrip(buf, cam, c.x + Math.cos(ang) * inner, c.y + 0.01, c.z + Math.sin(ang) * inner, c.x + Math.cos(ang) * r * 0.97, c.y + 0.01,
            c.z + Math.sin(ang) * r * 0.97, i % 3 == 0 ? 0.2 : 0.1, GREEN, alpha);
      }
   }

   static void arrival(BufferBuilder buf, Vec3 cam, Camera camera, Fx f, float partial, boolean glow) {
      float age = f.age + partial;
      float open = arrivalOpen(age);
      Vec3 c = arrivalCenter(f);
      Vec3 n = new Vec3(f.dir.x, 0.0, f.dir.z);
      n = n.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : n.normalize();
      portalDisc(buf, cam, camera, c, n, 2.8 * open, open, age, f.seed, glow);
      if (!glow) {
         return;
      }

      if (open > 0.3F && ((ticks >> 1) & 1) == 0) {
         RandomSource r = RandomSource.create(f.seed + ticks / 2);
         for (int k = 0; k < 3; k++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            Vec3 p = ringPoint(c, n, 2.8 * open, ang);
            Vec3 out = p.subtract(c).normalize();
            Vec3 prev = p;
            for (int i = 1; i <= 4; i++) {
               Vec3 next = p.add(out.scale(i * 0.45)).add((r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5);
               ribbon(buf, cam, prev, next, 0.06, 0xBFD8FF, (int)(200.0F * open), (int)(120.0F * open));
               prev = next;
            }
         }
      }

      if (age > 36.0F && age < 60.0F) {
         float k = (age - 36.0F) / 24.0F;
         double r = 1.0 + 9.0 * ThanosPose.smooth(k);
         Vec3 g = f.pos.add(0.0, 0.06, 0.0);
         flatRing(buf, cam, g, Math.max(0.0, r - 0.8), r, 56, PURPLE, (int)(200.0F * (1.0F - k)), 0);
         wall(buf, cam, g, r, 1.2 * (1.0F - k), 56, BLUE, (int)(120.0F * (1.0F - k)), 0);
      }
   }

}

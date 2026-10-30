package dev.baranhan.viltrumitecore.client.regulus;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusFlags;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusMove;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import dev.baranhan.viltrumitecore.hero.regulus.StoppedGrains;
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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Vector3f;

/**
 * Regulus' world effects, drawn as additive camera-facing quads (no vanilla particles). Every effect
 * is spawned from a server {@code RegulusFxS2CPacket} or derived from the synced snapshot, so all
 * viewers see the same beats and nothing appears that the server did not do: stilled motes that
 * halt mid-air, sand grains replayed from the shot packet and frozen where the server stopped
 * them, the air-cut glass sheet with its hairline scars, frozen block fragments, crack stars at
 * contacts, the trample's ground shockwave, the heart glyph on its host, the Lion's trail of stopped air
 * and the ultimate's dome.
 */
public final class RegulusVfx {
   static final int MAX_EFFECTS = 700;
   static final double CULL = 128.0;
   private static final List<Effect> EFFECTS = new ArrayList<>();
   private static final Map<Integer, SandShot> SHOTS = new HashMap<>();
   private static final Random RNG = new Random();

   private RegulusVfx() {
   }

   interface Effect {
      /** Draws at {@code age} ticks; false = finished (removed). */
      boolean draw(Ctx c, double age);

      double start();
   }

   /** One batch of additive quads relative to the camera. */
   record Ctx(BufferBuilder buffer, Vec3 cam, Camera camera, float partialTick, double now) {
      void dot(Vec3 at, float size, int rgb, int alpha) {
         if (alpha > 2) {
            double d = at.distanceTo(this.cam);
            if (d < 0.15) {
               return;
            }

            float s = d < 2.0 ? (float) (size * d / 2.0) : size;
            PixelVfx.billboardPixel(this.buffer, this.cam, this.camera, at, s, RegulusPalette.r(rgb), RegulusPalette.g(rgb), RegulusPalette.b(rgb),
               Math.min(255, alpha));
         }
      }

      /** Camera-facing ribbon from a to b. */
      void segment(Vec3 a, Vec3 b, float width, int rgb, int alpha) {
         if (alpha <= 2) {
            return;
         }

         Vec3 dir = b.subtract(a);
         Vec3 mid = a.add(b).scale(0.5);
         Vec3 side = dir.cross(mid.subtract(this.cam));
         double len = side.length();
         if (len < 1.0E-6) {
            return;
         }

         side = side.scale(width * 0.5 / len);
         this.quad(a.add(side), b.add(side), b.subtract(side), a.subtract(side), rgb, alpha);
      }

      void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int alpha) {
         int r = RegulusPalette.r(rgb);
         int g = RegulusPalette.g(rgb);
         int bl = RegulusPalette.b(rgb);
         int al = Math.min(255, alpha);
         for (Vec3 v : new Vec3[]{a, b, c, d}) {
            this.buffer.vertex((float) (v.x - this.cam.x), (float) (v.y - this.cam.y), (float) (v.z - this.cam.z)).color(r, g, bl, al).endVertex();
         }
      }

      /** A heart outline in the camera plane. */
      void heart(Vec3 center, float scale, int rgb, int alpha) {
         Vector3f l = this.camera.getLeftVector();
         Vector3f u = this.camera.getUpVector();
         Vec3 left = new Vec3(l.x, l.y, l.z);
         Vec3 up = new Vec3(u.x, u.y, u.z);
         for (int i = 0; i < 26; i++) {
            double t = Math.PI * 2.0 * i / 26.0;
            double x = 16.0 * Math.pow(Math.sin(t), 3.0) / 17.0;
            double y = (13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t)) / 17.0;
            this.dot(center.add(left.scale(-x * scale)).add(up.scale(y * scale)), 0.022F + scale * 0.03F, rgb, alpha);
         }
      }

      /** A crack star: jagged hairlines radiating from a point, deterministic per seed. */
      void crack(Vec3 center, double radius, int seed, int rgb, int alpha) {
         Random r = new Random(seed * 7919L);
         Vector3f l = this.camera.getLeftVector();
         Vector3f u = this.camera.getUpVector();
         Vec3 left = new Vec3(l.x, l.y, l.z);
         Vec3 up = new Vec3(u.x, u.y, u.z);
         int rays = 6 + r.nextInt(3);
         for (int i = 0; i < rays; i++) {
            double a = Math.PI * 2.0 * (i + r.nextDouble() * 0.6) / rays;
            Vec3 p = center;
            double len = radius * (0.55 + r.nextDouble() * 0.45);
            for (int k = 0; k < 3; k++) {
               a += (r.nextDouble() - 0.5) * 0.7;
               double step = len / 3.0;
               Vec3 q = p.add(left.scale(Math.cos(a) * step)).add(up.scale(Math.sin(a) * step));
               this.segment(p, q, (float) (0.035 * (1.0 - k * 0.28) * Math.max(0.6, radius)), rgb, alpha);
               p = q;
            }
         }

         this.dot(center, (float) (0.06 * radius + 0.04), RegulusPalette.GOLD, alpha);
      }
   }

   // ---------------------------------------------------------------- effects

   /** Motes that travel and then halt (stopped time); optionally fall after a release. */
   static final class Motes implements Effect {
      final double start;
      final Vec3[] from;
      final Vec3[] vel;
      final float[] stopAt;
      final int life;
      final int rgb;
      final float size;
      final double fallAt;

      Motes(double start, Vec3[] from, Vec3[] vel, float[] stopAt, int life, int rgb, float size, double fallAt) {
         this.start = start;
         this.from = from;
         this.vel = vel;
         this.stopAt = stopAt;
         this.life = life;
         this.rgb = rgb;
         this.size = size;
         this.fallAt = fallAt;
      }

      @Override
      public double start() {
         return this.start;
      }

      @Override
      public boolean draw(Ctx c, double age) {
         if (age > this.life) {
            return false;
         }

         float fade = (float) Mth.clamp((this.life - age) / (this.life * 0.35), 0.0, 1.0);
         for (int i = 0; i < this.from.length; i++) {
            double moving = Math.min(age, this.stopAt[i]);
            Vec3 p = this.from[i].add(this.vel[i].scale(moving));
            if (this.fallAt >= 0.0 && age > this.fallAt) {
               double f = age - this.fallAt;
               p = p.add(0.0, -0.02 * f * f, 0.0);
            }

            boolean stopped = age >= this.stopAt[i];
            double sinceStop = age - this.stopAt[i];
            int alpha = (int) (200 * fade);
            c.dot(p, this.size, stopped ? this.rgb : RegulusPalette.mix(this.rgb, RegulusPalette.GLASS, 0.4F), alpha);
            if (stopped && sinceStop < 3.0) {
               // the glint of the instant it stops
               c.dot(p, this.size * 3.2F, RegulusPalette.GOLD, (int) (110 * (1.0 - sinceStop / 3.0) * fade));
            } else if (stopped) {
               c.dot(p, this.size * 2.2F, this.rgb, (int) (26 * fade));
            }
         }

         return true;
      }
   }

   /** A crack star that flashes and fades. */
   record Crack(double start, Vec3 at, double radius, int seed, int life, int rgb) implements Effect {
      @Override
      public boolean draw(Ctx c, double age) {
         if (age > this.life) {
            return false;
         }

         float k = (float) (1.0 - age / this.life);
         double grow = Math.min(1.0, age / 2.0 + 0.4);
         c.crack(this.at, this.radius * grow, this.seed, this.rgb, (int) (230 * k));
         if (age < 2.0) {
            c.dot(this.at, (float) (this.radius * 0.55), RegulusPalette.IVORY, (int) (120 * (1.0 - age / 2.0)));
         }

         return true;
      }
   }

   /** A ribbon between two points (reply strike, flick, the sever cut across a body). */
   record Streak(double start, Vec3 a, Vec3 b, float width, int life, int rgb) implements Effect {
      @Override
      public boolean draw(Ctx c, double age) {
         if (age > this.life) {
            return false;
         }

         float k = (float) (1.0 - age / this.life);
         c.segment(this.a, this.b, this.width * (0.4F + 0.6F * k), this.rgb, (int) (220 * k));
         c.segment(this.a, this.b, this.width * 2.5F * k, RegulusPalette.GLASS, (int) (50 * k));
         return true;
      }
   }

   /** A pane of stopped air (Right of Reply), facing along {@code normal}. */
   record Pane(double start, Vec3 at, Vec3 normal, double half, int life) implements Effect {
      @Override
      public boolean draw(Ctx c, double age) {
         if (age > this.life) {
            return false;
         }

         float k = (float) (1.0 - age / this.life);
         Vec3 n = this.normal.normalize();
         Vec3 s = n.cross(new Vec3(0.0, 1.0, 0.0));
         s = s.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : s.normalize();
         Vec3 u = s.cross(n).normalize();
         double h = this.half * (0.8 + 0.2 * Math.min(1.0, age / 2.0));
         Vec3 a = this.at.add(s.scale(-h)).add(u.scale(-h));
         Vec3 b = this.at.add(s.scale(h)).add(u.scale(-h));
         Vec3 cc = this.at.add(s.scale(h)).add(u.scale(h));
         Vec3 d = this.at.add(s.scale(-h)).add(u.scale(h));
         c.quad(a, b, cc, d, RegulusPalette.GLASS, (int) (60 * k));
         c.segment(a, b, 0.03F, RegulusPalette.IVORY, (int) (200 * k));
         c.segment(b, cc, 0.03F, RegulusPalette.IVORY, (int) (200 * k));
         c.segment(cc, d, 0.03F, RegulusPalette.IVORY, (int) (200 * k));
         c.segment(d, a, 0.03F, RegulusPalette.IVORY, (int) (200 * k));
         return true;
      }
   }

   /** Severance of the air: the widening glass sheet and the hairline scars it leaves. */
   static final class Blade implements Effect {
      final double start;
      final Vec3 origin;
      final Vec3 forward;
      final Vec3 longAxis;
      final Vec3 thinAxis;

      Blade(double start, Vec3 origin, Vec3 look, boolean vertical) {
         this.start = start;
         this.origin = origin;
         this.forward = look.normalize();
         Vec3 s = this.forward.cross(new Vec3(0.0, 1.0, 0.0));
         Vec3 side = s.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : s.normalize();
         Vec3 up = side.cross(this.forward).normalize();
         this.longAxis = vertical ? up : side;
         this.thinAxis = vertical ? side : up;
      }

      @Override
      public double start() {
         return this.start;
      }

      @Override
      public boolean draw(Ctx c, double age) {
         double flight = RegulusRules.SEVER_RANGE / RegulusRules.SEVER_SPEED;
         if (age > flight + RegulusRules.SEVER_SCAR_TICKS) {
            return false;
         }

         double front = Math.min(RegulusRules.SEVER_RANGE, age * RegulusRules.SEVER_SPEED);
         if (age <= flight) {
            double half = RegulusRules.severWidth(front) * 0.5;
            double depth = 0.9;
            Vec3 f = this.origin.add(this.forward.scale(front));
            Vec3 b = this.origin.add(this.forward.scale(Math.max(0.0, front - depth)));
            Vec3 l = this.longAxis.scale(half);
            c.quad(b.subtract(l), b.add(l), f.add(l), f.subtract(l), RegulusPalette.GLASS, 55);
            c.segment(f.subtract(l), f.add(l), 0.05F, RegulusPalette.IVORY, 230);
            c.segment(f.subtract(l), f.add(l), 0.16F, RegulusPalette.PALE_GOLD, 50);
         }

         // scars: hairlines hanging where the sheet passed, fading from the oldest
         for (double d = 1.5; d < front; d += 1.5) {
            double passed = age - d / RegulusRules.SEVER_SPEED;
            double left = RegulusRules.SEVER_SCAR_TICKS - passed;
            if (left <= 0.0) {
               continue;
            }

            float k = (float) Math.min(1.0, left / 10.0);
            double half = RegulusRules.severWidth(d) * 0.5;
            Vec3 m = this.origin.add(this.forward.scale(d)).add(this.thinAxis.scale(Math.sin(d * 3.1) * 0.04));
            c.segment(m.subtract(this.longAxis.scale(half)), m.add(this.longAxis.scale(half)), 0.018F, RegulusPalette.IVORY, (int) (140 * k));
         }

         return true;
      }
   }

   /** Stopped sand: grains replayed from the shot packet, frozen where the server stopped them. */
   static final class SandShot implements Effect {
      final double start;
      final Vec3 from;
      final Vec3[] dirs;
      final Vec3[] stopPos;
      final double[] stopAt;
      final int rgb;

      SandShot(double start, Vec3 from, Vec3 look, int count, int seed, int rgb) {
         this.start = start;
         this.from = from;
         this.dirs = new Vec3[count];
         this.stopPos = new Vec3[count];
         this.stopAt = new double[count];
         for (int i = 0; i < count; i++) {
            this.dirs[i] = StoppedGrains.direction(look, i, count, RegulusRules.SAND_SPREAD_DEGREES, seed);
            this.stopAt[i] = -1.0;
         }

         this.rgb = rgb;
      }

      void stop(int index, Vec3 at, double now) {
         if (index >= 0 && index < this.dirs.length && this.stopAt[index] < 0.0) {
            this.stopPos[index] = at;
            this.stopAt[index] = now - this.start;
         }
      }

      @Override
      public double start() {
         return this.start;
      }

      @Override
      public boolean draw(Ctx c, double age) {
         double flight = RegulusRules.SAND_RANGE / RegulusRules.SAND_SPEED;
         boolean any = false;
         for (int i = 0; i < this.dirs.length; i++) {
            double stop = this.stopAt[i] >= 0.0 ? this.stopAt[i] : flight;
            if (age < stop) {
               any = true;
               Vec3 p = this.from.add(this.dirs[i].scale(RegulusRules.SAND_SPEED * age));
               Vec3 back = this.dirs[i].scale(-0.35);
               c.dot(p, 0.045F, RegulusPalette.IVORY, 230);
               c.dot(p.add(back), 0.035F, this.rgb, 170);
               c.dot(p.add(back.scale(2.0)), 0.025F, this.rgb, 90);
               continue;
            }

            double held = age - stop;
            if (held < 26.0) {
               any = true;
               Vec3 p = this.stopPos[i] != null ? this.stopPos[i] : this.from.add(this.dirs[i].scale(RegulusRules.SAND_RANGE));
               float k = (float) Math.min(1.0, (26.0 - held) / 8.0);
               c.dot(p, 0.05F, this.rgb, (int) (220 * k));
               c.dot(p, 0.13F, held < 3.0 ? RegulusPalette.GOLD : RegulusPalette.IVORY, (int) ((held < 3.0 ? 120 : 30) * k));
            }
         }

         return any;
      }
   }

   /** Frozen fragments of a broken block: they hang, then fall. */
   static final class Fragments implements Effect {
      final double start;
      final Vec3[] at;
      final int rgb;

      Fragments(double start, Vec3 center, int rgb, int count, long seed) {
         this.start = start;
         Random r = new Random(seed);
         this.at = new Vec3[count];
         for (int i = 0; i < count; i++) {
            this.at[i] = center.add((r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.9);
         }

         this.rgb = rgb;
      }

      @Override
      public double start() {
         return this.start;
      }

      @Override
      public boolean draw(Ctx c, double age) {
         if (age > 34.0) {
            return false;
         }

         double fall = Math.max(0.0, age - 12.0);
         float k = (float) Math.min(1.0, (34.0 - age) / 10.0);
         for (int i = 0; i < this.at.length; i++) {
            Vec3 p = this.at[i].add(0.0, -0.025 * fall * fall, 0.0);
            c.dot(p, 0.075F + (i % 3) * 0.02F, this.rgb, (int) (210 * k));
            if (age < 12.0) {
               c.dot(p, 0.17F, RegulusPalette.IVORY, (int) (22 * k));
            }
         }

         return true;
      }
   }

   /**
    * «Попрание»: the shock running out over the ground (or the stopped air) from his foot. A flat
    * ivory ring with a gold leading edge, hairline cracks lying on the ground behind it, and the
    * dust of the struck ground lifted by the front and stopped in mid-rise before it settles.
    * Pure timing in {@link #front}; deterministic per seed so every viewer sees the same cracks.
    */
   static final class Shockwave implements Effect {
      static final int SEGMENTS = 56;
      final double start;
      final Vec3 center;
      final double radius;
      final double speed;
      final boolean air;
      final int dust;
      final double[] crackAngle;
      final double[] crackLen;
      final long seed;
      final double[][] motes;

      Shockwave(double start, Vec3 center, double radius, double speed, boolean air, int dust, long seed) {
         this.start = start;
         this.center = center.add(0.0, air ? 0.0 : 0.04, 0.0);
         this.radius = radius;
         this.speed = speed;
         this.air = air;
         this.dust = dust;
         this.seed = seed;
         Random r = new Random(seed);
         int rays = 9 + r.nextInt(4);
         this.crackAngle = new double[rays];
         this.crackLen = new double[rays];
         for (int i = 0; i < rays; i++) {
            this.crackAngle[i] = Math.PI * 2.0 * (i + r.nextDouble() * 0.7) / rays;
            this.crackLen[i] = radius * (air ? 0.25 : 0.35 + r.nextDouble() * 0.35);
         }

         this.motes = new double[40][];
         for (int i = 0; i < this.motes.length; i++) {
            // angle, distance from the centre, rise height
            this.motes[i] = new double[]{r.nextDouble() * Math.PI * 2.0, 0.6 + r.nextDouble() * (radius - 0.6), 0.25 + r.nextDouble() * 0.9};
         }
      }

      /** Pure: ring radius at {@code age} ticks (starts at half a block, stops at the full radius). */
      static double front(double age, double speed, double radius) {
         return Math.min(radius, 0.5 + Math.max(0.0, age) * speed);
      }

      double life() {
         return (this.radius - 0.5) / this.speed + 30.0;
      }

      @Override
      public double start() {
         return this.start;
      }

      @Override
      public boolean draw(Ctx c, double age) {
         double life = this.life();
         if (age > life) {
            return false;
         }

         double r = front(age, this.speed, this.radius);
         double travel = (this.radius - 0.5) / this.speed;
         float out = (float) Mth.clamp((life - age) / 14.0, 0.0, 1.0);
         // the ring: bright while it runs, it fades once it reaches the edge
         float ringFade = (float) Mth.clamp(1.0 - (age - travel) / 6.0, 0.0, 1.0);
         if (ringFade > 0.0F) {
            double w = 0.22 + 0.25 * (1.0 - r / this.radius);
            int alpha = (int) (170 * ringFade);
            for (int i = 0; i < SEGMENTS; i++) {
               double a0 = Math.PI * 2.0 * i / SEGMENTS;
               double a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
               Vec3 i0 = this.flat(a0, Math.max(0.0, r - w));
               Vec3 i1 = this.flat(a1, Math.max(0.0, r - w));
               Vec3 o0 = this.flat(a0, r);
               Vec3 o1 = this.flat(a1, r);
               c.quad(i0, o0, o1, i1, this.air ? RegulusPalette.GLASS : RegulusPalette.IVORY, alpha);
               if (i % 2 == 0) {
                  c.dot(o0.add(0.0, 0.05, 0.0), 0.05F, RegulusPalette.GOLD, (int) (150 * ringFade));
               }
            }

            if (this.air) {
               // a faint pane of stopped air under the ring
               for (int i = 0; i < SEGMENTS; i += 4) {
                  double a = Math.PI * 2.0 * i / SEGMENTS;
                  c.segment(this.center, this.flat(a, r), 0.015F, RegulusPalette.GLASS, (int) (40 * ringFade));
               }
            }
         }

         // hairline cracks lying on the ground, growing with the ring and staying after it
         int crack = (int) (210 * out);
         for (int i = 0; i < this.crackAngle.length; i++) {
            Random jr = new Random(this.seed + i * 31L);
            double len = Math.min(this.crackLen[i], r);
            Vec3 p = this.center;
            double a = this.crackAngle[i];
            for (int k = 0; k < 4; k++) {
               a += (jr.nextDouble() - 0.5) * 0.6;
               double d = len * (k + 1) / 4.0;
               Vec3 q = this.flat(a, d);
               c.quad(p.add(sideOf(p, q, 0.03)), q.add(sideOf(p, q, 0.015)), q.subtract(sideOf(p, q, 0.015)), p.subtract(sideOf(p, q, 0.03)),
                  RegulusPalette.IVORY, crack);
               p = q;
            }
         }

         // dust lifted by the front, stopped in mid-rise, then released
         int dustRgb = this.air ? RegulusPalette.GLASS : blockColor(this.dust, RegulusPalette.GREY);
         double release = travel + 14.0;
         for (double[] m : this.motes) {
            double passed = age - (m[1] - 0.5) / this.speed;
            if (passed < 0.0) {
               continue;
            }

            double rise = m[2] * Math.min(1.0, passed / 4.0);
            Vec3 p = this.flat(m[0], m[1]).add(0.0, this.air ? rise * 0.3 : rise, 0.0);
            if (age > release) {
               double f = age - release;
               p = p.add(0.0, -0.03 * f * f, 0.0);
            }

            c.dot(p, 0.06F, dustRgb, (int) (200 * out));
            if (passed >= 4.0 && passed < 7.0) {
               c.dot(p, 0.15F, RegulusPalette.GOLD, (int) (90 * (1.0 - (passed - 4.0) / 3.0) * out));
            }
         }

         return true;
      }

      Vec3 flat(double angle, double distance) {
         return this.center.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
      }

      static Vec3 sideOf(Vec3 a, Vec3 b, double half) {
         Vec3 d = b.subtract(a);
         Vec3 s = new Vec3(-d.z, 0.0, d.x);
         double len = s.length();
         return len < 1.0E-6 ? Vec3.ZERO : s.scale(half / len);
      }
   }

   /** A heart glyph travelling from a to the entity {@code to} (bind, hop) or breaking (release). */
   record HeartTravel(double start, Vec3 from, int to, int life, boolean breaking) implements Effect {
      @Override
      public boolean draw(Ctx c, double age) {
         if (age > this.life) {
            return false;
         }

         Minecraft mc = Minecraft.getInstance();
         Entity target = mc.level == null ? null : mc.level.getEntity(this.to);
         Vec3 end = target == null ? this.from : target.getPosition(c.partialTick()).add(0.0, target.getBbHeight() * 0.68, 0.0);
         float u = (float) (age / this.life);
         if (this.breaking) {
            Random r = new Random(this.to * 31L);
            for (int i = 0; i < 16; i++) {
               Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() + 0.4, r.nextGaussian()).normalize();
               Vec3 p = this.from.add(d.scale(0.6 * Math.min(1.0, age / 5.0)));
               c.dot(p, 0.04F, RegulusPalette.HEART, (int) (220 * (1.0F - u)));
            }

            return true;
         }

         float e = u * u * (3.0F - 2.0F * u);
         Vec3 p = this.from.lerp(end, e).add(0.0, Math.sin(Math.PI * u) * 0.6, 0.0);
         c.heart(p, 0.16F + 0.06F * (float) Math.sin(Math.PI * u), RegulusPalette.HEART, 230);
         return true;
      }
   }

   /** Kingdom of One's dome of stopped air around the target. */
   static final class Dome implements Effect {
      final double start;
      final Vec3 center;
      final double radius;
      final int seed;
      double endAt = -1.0;

      Dome(double start, Vec3 center, double radius, int seed) {
         this.start = start;
         this.center = center;
         this.radius = radius;
         this.seed = seed;
      }

      @Override
      public double start() {
         return this.start;
      }

      @Override
      public boolean draw(Ctx c, double age) {
         double life = RegulusRules.ULT_LENGTH - RegulusRules.ULT_SCOOP + 10;
         if (this.endAt >= 0.0 && age > this.endAt + 20.0 || age > life + 20.0) {
            return false;
         }

         double broken = this.endAt >= 0.0 ? age - this.endAt : age > life ? age - life : -1.0;
         float grow = (float) Math.min(1.0, age / 12.0);
         float fade = broken < 0.0 ? 1.0F : (float) Math.max(0.0, 1.0 - broken / 20.0);
         int n = 150;
         double golden = Math.PI * (3.0 - Math.sqrt(5.0));
         for (int i = 0; i < n; i++) {
            double y = 1.0 - (i / (double) (n - 1));
            if (y < -0.1) {
               continue;
            }

            double rr = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double th = golden * i + this.seed;
            Vec3 d = new Vec3(Math.cos(th) * rr, y, Math.sin(th) * rr);
            Vec3 p = this.center.add(d.scale(this.radius * grow));
            if (broken >= 0.0) {
               p = p.add(0.0, -0.02 * broken * broken, 0.0);
            }

            c.dot(p, 0.04F, i % 9 == 0 ? RegulusPalette.GOLD : RegulusPalette.IVORY, (int) (150 * fade));
         }

         c.dot(this.center.add(0.0, 1.0, 0.0), (float) this.radius * 0.6F * grow, RegulusPalette.GLASS, (int) (18 * fade));
         return true;
      }
   }

   // ---------------------------------------------------------------- spawning (from RegulusFx)

   static double now() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0.0 : mc.level.getGameTime();
   }

   static void add(Effect effect) {
      if (EFFECTS.size() >= MAX_EFFECTS) {
         EFFECTS.remove(0);
      }

      EFFECTS.add(effect);
   }

   /** A burst of motes from a point that halt after {@code stopTicks}. */
   static void burst(Vec3 at, int count, double speed, float stopTicks, int life, int rgb, float size, double fallAt, Vec3 bias) {
      Vec3[] from = new Vec3[count];
      Vec3[] vel = new Vec3[count];
      float[] stop = new float[count];
      for (int i = 0; i < count; i++) {
         Vec3 d = new Vec3(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian()).normalize().add(bias).normalize();
         from[i] = at;
         vel[i] = d.scale(speed * (0.5 + RNG.nextDouble() * 0.7));
         stop[i] = stopTicks * (0.6F + RNG.nextFloat() * 0.6F);
      }

      add(new Motes(now(), from, vel, stop, life, rgb, size, fallAt));
   }

   /** Motes placed still around a point (stopped air): no travel. */
   static void still(Vec3 at, double radius, int count, int life, int rgb, float size) {
      Vec3[] from = new Vec3[count];
      Vec3[] vel = new Vec3[count];
      float[] stop = new float[count];
      for (int i = 0; i < count; i++) {
         from[i] = at.add(RNG.nextGaussian() * radius, RNG.nextGaussian() * radius * 0.8, RNG.nextGaussian() * radius);
         vel[i] = Vec3.ZERO;
         stop[i] = 0.0F;
      }

      add(new Motes(now(), from, vel, stop, life, rgb, size, -1.0));
   }

   /** A ring of motes expanding from {@code at} on the horizontal plane and halting at {@code radius}. */
   static void ring(Vec3 at, double radius, int count, float ticks, int life, int rgb) {
      Vec3[] from = new Vec3[count];
      Vec3[] vel = new Vec3[count];
      float[] stop = new float[count];
      double phase = RNG.nextDouble() * Math.PI;
      for (int i = 0; i < count; i++) {
         double a = phase + Math.PI * 2.0 * i / count;
         from[i] = at;
         vel[i] = new Vec3(Math.cos(a), 0.0, Math.sin(a)).scale(radius / ticks);
         stop[i] = ticks;
      }

      add(new Motes(now(), from, vel, stop, life, rgb, 0.04F, -1.0));
   }

   static void crack(Vec3 at, double radius, int life) {
      add(new Crack(now(), at, radius, RNG.nextInt(), life, RegulusPalette.IVORY));
   }

   static void streak(Vec3 a, Vec3 b, float width, int life) {
      add(new Streak(now(), a, b, width, life, RegulusPalette.IVORY));
   }

   static void pane(Vec3 at, Vec3 normal, double half, int life) {
      add(new Pane(now(), at, normal, half, life));
   }

   static void blade(Vec3 origin, Vec3 look, boolean vertical) {
      add(new Blade(now(), origin, look, vertical));
   }

   static void sand(int source, Vec3 from, Vec3 look, int count, int seed, int material) {
      int rgb = blockColor(material, RegulusPalette.PALE_GOLD);
      SandShot shot = new SandShot(now(), from, look, Math.max(1, Math.min(32, count)), seed, rgb);
      SHOTS.put(source, shot);
      add(shot);
   }

   static void grainStop(int source, int index, Vec3 at) {
      SandShot shot = SHOTS.get(source);
      if (shot != null) {
         shot.stop(index, at, now());
      }
   }

   static void shockwave(Vec3 center, double radius, double speed, boolean air, int dustState) {
      // seeded by the impact point: every viewer draws the same cracks
      long seed = Double.doubleToLongBits(Math.floor(center.x * 16.0)) * 31L + Double.doubleToLongBits(Math.floor(center.z * 16.0));
      add(new Shockwave(now(), center, radius, speed, air, dustState, seed));
   }

   static void fragments(Vec3 center, int stateId) {
      add(new Fragments(now(), center, blockColor(stateId, RegulusPalette.GREY), 7, RNG.nextLong()));
   }

   static void heart(Vec3 from, int to, int life, boolean breaking) {
      add(new HeartTravel(now(), from, to, life, breaking));
   }

   private static Dome dome;

   static void dome(Vec3 center, double radius, int seed) {
      dome = new Dome(now(), center, radius, seed);
      add(dome);
   }

   static void endDome() {
      if (dome != null) {
         dome.endAt = now() - dome.start;
         dome = null;
      }
   }

   /** Map colour of a block state id (sand, stone, water...), or the fallback. */
   static int blockColor(int stateId, int fallback) {
      Minecraft mc = Minecraft.getInstance();
      BlockState state = Block.stateById(stateId);
      if (state == null || state.isAir() || mc.level == null) {
         return fallback;
      }

      try {
         int col = state.getMapColor(mc.level, BlockPos.ZERO).col;
         return col == 0 ? fallback : RegulusPalette.mix(col, RegulusPalette.IVORY, 0.25F);
      } catch (RuntimeException e) {
         return fallback;
      }
   }

   // ---------------------------------------------------------------- continuous (snapshot-driven)

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.isPaused()) {
         return;
      }

      RegulusUltScene.tick(mc);
      long time = level.getGameTime();
      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      for (Player player : level.players()) {
         HeroPublicSnapshot s = RegulusView.of(player);
         if (s == null || player.position().distanceToSqr(cam) > CULL * CULL || player.isInvisible()) {
            continue;
         }

         RegulusMove move = RegulusView.move(s);
         boolean lion = RegulusView.lion(s);
         // the Lion: the air he passes through stays stopped for a moment behind him
         if (lion && time % 2 == 0) {
            Vec3 c = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
            still(c, 0.4, 2, 14, RegulusPalette.IVORY, 0.03F);
         }

         if (RegulusView.flag(s, RegulusFlags.SEIZED) && time % 3 == 0) {
            burst(RegulusAnchors.heart(player, 0.0F), 3, 0.05, 6.0F, 18, RegulusPalette.INK, 0.035F, 8.0, Vec3.ZERO);
         }
      }

      double now = time;
      SHOTS.values().removeIf(shot -> now - shot.start > 80.0);
   }

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         RegulusUltScene.renderModels(event);
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

      List<Player> regulus = new ArrayList<>();
      for (Player player : level.players()) {
         if (RegulusView.of(player) != null) {
            regulus.add(player);
         }
      }

      if (EFFECTS.isEmpty() && regulus.isEmpty()) {
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
         float partial = event.getPartialTick();
         double now = level.getGameTime() + partial;
         Ctx ctx = new Ctx(buffer, camera.getPosition(), camera, partial, now);
         Iterator<Effect> it = EFFECTS.iterator();
         while (it.hasNext()) {
            Effect e = it.next();
            double age = now - e.start();
            if (age < 0.0) {
               continue;
            }

            if (!e.draw(ctx, age)) {
               it.remove();
            }
         }

         for (Player player : regulus) {
            hostHeart(ctx, level, player);
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

   /** The heart beating in its host's chest, visible within the reveal range. */
   private static void hostHeart(Ctx c, ClientLevel level, Player regulus) {
      HeroPublicSnapshot s = RegulusView.of(regulus);
      if (s == null || !RegulusView.flag(s, RegulusFlags.HOST_ALIVE)) {
         return;
      }

      Entity host = level.getEntity(RegulusView.hostId(s));
      if (host == null || host.isInvisible() || host.position().distanceToSqr(c.cam()) > RegulusRules.KING_REVEAL_RANGE * RegulusRules.KING_REVEAL_RANGE) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (host == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
         return;
      }

      double beat = (c.now() % 20.0) / 20.0;
      float pulse = (float) (beat < 0.12 ? beat / 0.12 : Math.max(0.0, 1.0 - (beat - 0.12) / 0.4));
      Vec3 at = host.getPosition(c.partialTick()).add(0.0, host.getBbHeight() * 0.7, 0.0);
      boolean strained = RegulusView.flag(s, RegulusFlags.STRAIN_WARN);
      c.heart(at, 0.12F + 0.03F * pulse, strained ? RegulusPalette.mix(RegulusPalette.HEART, RegulusPalette.INK, 0.4F) : RegulusPalette.HEART,
         (int) (120 + 110 * pulse));
   }

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         clear();
      }
   }

   public static void clear() {
      EFFECTS.clear();
      SHOTS.clear();
      dome = null;
      RegulusAnchors.clear();
      RegulusPoser.clear();
      RegulusUltScene.clear();
   }
}

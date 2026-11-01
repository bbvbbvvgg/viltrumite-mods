package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.ultron.UltronBolt;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMissile;
import dev.baranhan.viltrumitecore.entity.ultron.UltronNodeEntity;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronCapture;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The live effect types of {@link UltronVfx} (glows, rings, beams, ground marks, swirls, beam ends, broken parts). Client only. */
final class UltronVfxParts {
   private UltronVfxParts() {
   }

   /** A soft additive glow: flash, fire puff, spark, energy mote. */
   static final class Glow {
      static final int RED = 0, HOT = 1, FIRE = 2, FLASH = 3, SPARK = 4, COLD = 5;
      double x, y, z, px, py, pz, vx, vy, vz;
      final float size0, size1, alpha, gravity, lift;
      final int life, kind;
      int age;

      Glow(Vec3 at, Vec3 v, float size0, float size1, int life, int kind, float alpha, float gravity, float lift) {
         this.x = this.px = at.x;
         this.y = this.py = at.y;
         this.z = this.pz = at.z;
         this.vx = v.x;
         this.vy = v.y;
         this.vz = v.z;
         this.size0 = size0;
         this.size1 = size1;
         this.life = Math.max(1, life);
         this.kind = kind;
         this.alpha = alpha;
         this.gravity = gravity;
         this.lift = lift;
      }

      int[] colour(float t) {
         return switch (this.kind) {
            case RED -> UltronVfx.RED;
            case HOT -> UltronVfx.HOT;
            case FLASH -> new int[]{255, 236, 226};
            case SPARK -> t < 0.3F ? mix(255, 240, 210, 255, 150, 60, t / 0.3F) : mix(255, 150, 60, 200, 40, 20, (t - 0.3F) / 0.7F);
            case COLD -> UltronVfx.COLD;
            default -> t < 0.25F ? mix(255, 240, 206, 255, 150, 60, t / 0.25F) : mix(255, 150, 60, 140, 30, 18, (t - 0.25F) / 0.75F);
         };
      }

      static int[] mix(int r0, int g0, int b0, int r1, int g1, int b1, float f) {
         return new int[]{(int)Mth.lerp(f, r0, r1), (int)Mth.lerp(f, g0, g1), (int)Mth.lerp(f, b0, b1)};
      }
   }

   /** A ring growing in a plane (circle or hexagon). */
   static final class Pulse {
      final Vec3 centre;
      final Vector3f a = new Vector3f();
      final Vector3f b = new Vector3f();
      final float from, to, strength;
      final int life, segments, delay;
      final int[] col;
      int age;

      Pulse(Vec3 centre, Vec3 normal, float from, float to, int life, float strength, int[] col, int segments, int delay) {
         this.centre = centre;
         Vector3f n = new Vector3f((float)normal.x, (float)normal.y, (float)normal.z);
         if (n.lengthSquared() < 1.0E-6F) {
            n.set(0.0F, 1.0F, 0.0F);
         }
         n.normalize();
         UltronUnitRenderer.basis(n, this.a, this.b);
         this.from = from;
         this.to = to;
         this.life = Math.max(1, life);
         this.strength = strength;
         this.col = col;
         this.segments = segments;
         this.delay = delay;
      }
   }

   /** A line of light between two points: straight, jagged (arcs, re-jittered every tick) or carrying data pulses. */
   static final class Beam {
      final Vec3 from, to;
      final int life;
      final float width, jag;
      final int[] col;
      final long seed;
      @Nullable
      UltronUnit follow;
      int anchor = -1;
      boolean pulses;
      int delay;
      int age;

      Beam(Vec3 from, Vec3 to, int life, float width, int[] col, float jag, long seed) {
         this.from = from;
         this.to = to;
         this.life = Math.max(1, life);
         this.width = width;
         this.col = col;
         this.jag = jag;
         this.seed = seed;
      }

      void draw(BufferBuilder buf, Matrix4f m, Vec3 eye, float partial) {
         float age = this.age + partial - this.delay;
         if (age < 0.0F) {
            return;
         }
         float t = Mth.clamp(age / this.life, 0.0F, 1.0F);
         float fade = (1.0F - t) * Math.min(1.0F, age / 1.5F);
         Vec3 a = this.from;
         if (this.follow != null && this.anchor >= 0 && this.follow.clientAnchors[this.anchor] != null && this.follow.tickCount - this.follow.clientAnchorTick < 3) {
            a = this.follow.clientAnchors[this.anchor];
         }
         Vec3 d = this.to.subtract(a);
         double len = d.length();
         if (len < 1.0E-4) {
            return;
         }
         int segs = this.jag > 0.0F ? Math.max(4, (int)(len * 2.5)) : 1;
         Random r = new Random(this.seed + (long)(this.age + this.delay) * 7919L);
         Vector3f p0 = new Vector3f((float)(a.x - eye.x), (float)(a.y - eye.y), (float)(a.z - eye.z));
         Vector3f p1 = new Vector3f();
         Vector3f toCam = new Vector3f();
         for (int i = 1; i <= segs; i++) {
            double f = i / (double)segs;
            Vec3 q = a.add(d.scale(f));
            if (this.jag > 0.0F && i < segs) {
               q = q.add(r.nextGaussian() * this.jag, r.nextGaussian() * this.jag, r.nextGaussian() * this.jag);
            }
            p1.set((float)(q.x - eye.x), (float)(q.y - eye.y), (float)(q.z - eye.z));
            toCam.set(p0).negate();
            int alpha = (int)(200 * fade);
            GlowDraw.ribbon(buf, m, p0, p1, toCam, this.width * 2.6F, this.width * 2.6F, this.col[0], this.col[1], this.col[2], alpha / 3, alpha / 3);
            GlowDraw.ribbon(buf, m, p0, p1, toCam, this.width, this.width, 255, Math.min(255, this.col[1] + 120), Math.min(255, this.col[2] + 110), alpha, alpha);
            p0.set(p1);
         }
         if (this.pulses) {
            for (int k = 0; k < 3; k++) {
               float f = ((age * 0.09F + k / 3.0F) % 1.0F);
               Vec3 q = a.add(d.scale(f));
               Vector3f c = new Vector3f((float)(q.x - eye.x), (float)(q.y - eye.y), (float)(q.z - eye.z));
               Vector3f n = new Vector3f(c).negate().normalize();
               Vector3f l = new Vector3f();
               Vector3f u = new Vector3f();
               UltronUnitRenderer.basis(n, l, u);
               GlowDraw.billboard(buf, m, c, l, u, this.width * 5.0F, 0.3F, this.col[0], this.col[1], this.col[2], (int)(200 * fade));
            }
         }
      }
   }

   /** A target reticle (designator mark on an entity) or a ground strike mark. */
   static final class Mark {
      final Vec3 at;
      final float radius;
      final int life;
      @Nullable
      final Entity follow;
      int age;

      Mark(Vec3 at, float radius, int life, @Nullable Entity follow) {
         this.at = at;
         this.radius = radius;
         this.life = life;
         this.follow = follow;
      }

      void draw(BufferBuilder buf, Matrix4f m, Vec3 eye, float partial) {
         float age = this.age + partial;
         float fade = Math.min(1.0F, age / 4.0F) * Math.min(1.0F, (this.life - age) / 8.0F);
         if (fade <= 0.0F) {
            return;
         }
         Vec3 c = this.at;
         float r = this.radius;
         if (this.follow != null) {
            c = new Vec3(Mth.lerp(partial, this.follow.xo, this.follow.getX()), Mth.lerp(partial, this.follow.yo, this.follow.getY()) + 0.05,
               Mth.lerp(partial, this.follow.zo, this.follow.getZ()));
            r = Math.max(0.6F, this.follow.getBbWidth() * 0.9F);
         }
         Vector3f p = new Vector3f((float)(c.x - eye.x), (float)(c.y - eye.y) + 0.05F, (float)(c.z - eye.z));
         float spin = age * 0.12F;
         Vector3f ax = new Vector3f(Mth.cos(spin), 0.0F, Mth.sin(spin));
         Vector3f bx = new Vector3f(-Mth.sin(spin), 0.0F, Mth.cos(spin));
         int a = (int)(170 * fade * (0.8F + 0.2F * Mth.sin(age * 0.8F)));
         float shrink = this.follow == null ? 1.0F : 1.0F + 0.6F * Math.max(0.0F, 1.0F - age / 10.0F);
         GlowDraw.ring(buf, m, p, ax, bx, r * 0.9F * shrink, r * shrink, 0.6F, 64, 255, 40, 30, a);
         GlowDraw.ring(buf, m, p, ax, bx, r * 0.45F, r * 0.52F, 0.6F, 4, 255, 60, 40, a);
         for (int i = 0; i < 4; i++) {
            Vector3f dir = new Vector3f(ax).rotateY(i * Mth.HALF_PI);
            Vector3f q0 = new Vector3f(dir).mul(r * 0.55F).add(p);
            Vector3f q1 = new Vector3f(dir).mul(r * 1.15F).add(p);
            GlowDraw.ribbon(buf, m, q0, q1, new Vector3f(0.0F, 1.0F, 0.0F), 0.05F, 0.02F, 255, 50, 35, a, a / 3);
         }
      }
   }

   /** The assembly vortex: motes spiral in toward the centre for the length of the assembly. */
   static final class Swirl {
      final Vec3 centre;
      final float radius;
      final int life;
      final int id;
      int age;

      Swirl(Vec3 centre, float radius, int life, int id) {
         this.centre = centre;
         this.radius = radius;
         this.life = life;
         this.id = id;
      }

      void tick() {
         this.age++;
         float k = 1.0F - this.age / (float)this.life;
         int n = 3 + (int)(4 * k);
         for (int i = 0; i < n; i++) {
            double a = UltronVfx.RANDOM.nextDouble() * Math.PI * 2.0;
            double r = this.radius * (0.6 + 0.4 * UltronVfx.RANDOM.nextDouble());
            Vec3 p = this.centre.add(Math.cos(a) * r, UltronVfx.RANDOM.nextDouble() * 3.0 - 0.5, Math.sin(a) * r);
            Vec3 in = this.centre.add(0.0, 1.4, 0.0).subtract(p);
            Vec3 tangent = new Vec3(-Math.sin(a), 0.0, Math.cos(a)).scale(0.12);
            int life = 18 + UltronVfx.RANDOM.nextInt(8);
            UltronVfx.glow(p, in.scale(1.0 / life * 1.6).add(tangent), 0.12F, 0.22F, life, Glow.RED, 0.85F, 0.0F, 0.0F);
         }
         if (this.age % 20 == 0) {
            UltronVfx.pulse(this.centre.add(0.0, 0.2, 0.0), new Vec3(0, 1, 0), this.radius, 0.6F, 20, 0.6F, UltronVfx.RED, 64, 0);
         }
      }
   }

   /** The finale's eye-beam target, between two server aim updates. */
   record BeamEnd(Vec3 prev, Vec3 end, int tick) {
   }

   /** One part of a broken machine: tumbles, bounces, smokes, cools and fades. */
   static final class Piece {
      final UltronAssets.Model assets;
      final String bone;
      final Set<String> exclude;
      final Matrix4f frame;
      final Vector3f centre;
      double x, y, z, px, py, pz, vx, vy, vz;
      final Quaternionf rot = new Quaternionf();
      final Quaternionf prev = new Quaternionf();
      final Vector3f axis;
      float spin;
      final int life;
      int age;
      boolean resting;

      Piece(UltronAssets.Model assets, String bone, Set<String> exclude, Matrix4f frame, Vec3 origin, Vector3f centre, Vec3 v, Vector3f axis, float spin,
         int life) {
         this.assets = assets;
         this.bone = bone;
         this.exclude = exclude;
         this.frame = frame;
         this.centre = centre;
         this.x = this.px = origin.x + centre.x;
         this.y = this.py = origin.y + centre.y;
         this.z = this.pz = origin.z + centre.z;
         this.vx = v.x;
         this.vy = v.y;
         this.vz = v.z;
         this.axis = axis;
         this.spin = spin;
         this.life = life;
      }

      void tick(ClientLevel level) {
         this.age++;
         this.px = this.x;
         this.py = this.y;
         this.pz = this.z;
         this.prev.set(this.rot);
         if (!this.resting) {
            this.vy -= 0.045;
            this.vx *= 0.985;
            this.vz *= 0.985;
            double ny = this.y + this.vy;
            BlockPos below = BlockPos.containing(this.x + this.vx, ny - 0.12, this.z + this.vz);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && this.vy < 0.0) {
               this.y = Math.max(this.y, below.getY() + 1.12);
               this.vy = -this.vy * 0.3;
               this.vx *= 0.55;
               this.vz *= 0.55;
               this.spin *= 0.5F;
               if (Math.abs(this.vy) < 0.06 && this.vx * this.vx + this.vz * this.vz < 0.004) {
                  this.resting = true;
               }
            } else {
               this.y = ny;
            }
            BlockPos side = BlockPos.containing(this.x + this.vx, this.y, this.z + this.vz);
            if (!level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
               this.vx *= -0.3;
               this.vz *= -0.3;
            } else {
               this.x += this.vx;
               this.z += this.vz;
            }
            this.rot.rotateAxis(this.spin, this.axis.x, this.axis.y, this.axis.z);
         } else if (this.age > this.life - 30) {
            this.y -= 0.01;
         }
         if (this.age < 50 && (this.age < 24 || this.age % 3 == 0)) {
            level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, this.x, this.y + 0.1, this.z, 0.0, 0.03, 0.0);
         }
         if (this.age < 20 && !this.resting && UltronVfx.RANDOM.nextInt(3) == 0) {
            UltronVfx.glow(new Vec3(this.x, this.y, this.z), UltronVfx.gauss(0.05), 0.06F, 0.02F, 8, Glow.SPARK, 1.0F, 0.03F, 0.0F);
         }
         if (this.resting && this.age < this.life - 40 && this.age % 11 == 0) {
            level.addParticle(ParticleTypes.SMOKE, this.x, this.y + 0.2, this.z, 0.0, 0.02, 0.0);
         }
      }
   }
}

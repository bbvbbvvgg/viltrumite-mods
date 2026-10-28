package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Layered electric shapes of {@link HeroImpactFx} (same packet, same tick
 * and render pass; not a separate pipeline): lightning channels drawn as
 * camera-facing ribbons (white-blue core, inner glow, wide outer glow, with
 * branches, leader, re-strikes and afterglow), arcs, ground crawlers, pixel
 * sparks, light pools, lit cloud sheets, burn decals and storm clouds.
 * Ages by client ticks, interpolates with the partial tick, caps every list.
 * Render thread only.
 */
public final class ElectricVfx {
   private static final int MAX_CHANNELS = 160;
   private static final int MAX_SPARKS = 900;
   private static final int MAX_POOLS = 24;
   private static final int MAX_DECALS = 12;
   private static final List<Channel> CHANNELS = new ArrayList<>();
   private static final List<Spark> SPARKS = new ArrayList<>();
   private static final List<Pool> POOLS = new ArrayList<>();
   private static final ScorchBuffer<Decal> DECALS = new ScorchBuffer<>(MAX_DECALS, 20L * 60L);
   private static final Map<Object, Cloud> CLOUDS = new HashMap<>();
   private static final Random RANDOM = new Random();

   private ElectricVfx() {
   }

   enum Shape {
      BOLT,
      ARC,
      CRAWL
   }

   // --- spawning ---

   /** A sky bolt from {@code top} onto {@code ground}; {@code halfWidth} is the core half-width in blocks. */
   public static void bolt(Vec3 top, Vec3 ground, float halfWidth, int branches, int life, long seed) {
      List<BoltShape.Strand> strands = BoltShape.channel(seed, top, ground, 0.42, branches);
      add(new Channel(Shape.BOLT, strands, halfWidth, life, seed, 0));
   }

   /** A jagged discharge; it takes a new path {@code rejitters} times over its life. */
   public static void arc(Vec3 from, Vec3 to, float halfWidth, int life, int rejitters, long seed) {
      int branches = from.distanceTo(to) > 3.0 ? 2 : 0;
      List<BoltShape.Strand> strands = BoltShape.channel(seed, from, to, 0.28, branches);
      add(new Channel(Shape.ARC, strands, halfWidth, life, seed, rejitters <= 0 ? 0 : Math.max(1, life / (rejitters + 1))));
   }

   /** Discharges along a ground circle. */
   public static void ring(ClientLevel level, Vec3 center, double radius, float halfWidth, int arcs, int life, long seed) {
      Random random = new Random(seed);
      int count = Math.max(6, Math.min(32, arcs));
      for (int i = 0; i < count; i++) {
         double a0 = Math.PI * 2.0 * i / count;
         double a1 = a0 + Math.PI * 2.0 / count * (0.8 + random.nextDouble() * 0.5);
         Vec3 from = onGround(level, center.add(Math.cos(a0) * radius, 0.0, Math.sin(a0) * radius), center.y);
         Vec3 to = onGround(level, center.add(Math.cos(a1) * radius, 0.0, Math.sin(a1) * radius), center.y);
         add(new Channel(Shape.ARC, BoltShape.channel(seed + i, from.add(0, 0.15, 0), to.add(0, 0.15, 0), 0.3, 0), halfWidth,
            life - random.nextInt(Math.max(1, life / 3)), seed + i, 2));
      }
   }

   /** Micro arcs jumping inside a ball around {@code center}. */
   public static void crackle(Vec3 center, float radius, int arcs, int life, long seed) {
      Random random = new Random(seed);
      int count = Math.max(1, Math.min(16, arcs));
      for (int i = 0; i < count; i++) {
         Vec3 from = center.add(gauss(random, radius), gauss(random, radius), gauss(random, radius));
         Vec3 to = from.add(gauss(random, radius * 1.4F), gauss(random, radius * 1.4F), gauss(random, radius * 1.4F));
         int delay = random.nextInt(Math.max(1, life / 2));
         Channel channel = new Channel(Shape.ARC, BoltShape.channel(seed * 31 + i, from, to, 0.35, 0), 0.035F + random.nextFloat() * 0.02F,
            Math.max(3, life - delay), seed * 31 + i, 2);
         channel.age = -delay;
         add(channel);
      }
   }

   /** Discharges crawling outward over the ground from {@code center} (they follow the terrain). */
   public static void crawl(ClientLevel level, Vec3 center, float length, int arcs, int life, float halfWidth, long seed) {
      Random random = new Random(seed);
      int count = Math.max(1, Math.min(24, arcs));
      for (int i = 0; i < count; i++) {
         double angle = Math.PI * 2.0 * (i + random.nextDouble() * 0.6) / count;
         double reach = length * (0.6 + random.nextDouble() * 0.6);
         Vec3 end = center.add(Math.cos(angle) * reach, 0.0, Math.sin(angle) * reach);
         List<BoltShape.Strand> strands = BoltShape.channel(seed * 17 + i, center, end, 0.22, 1 + random.nextInt(2));
         add(new Channel(Shape.CRAWL, hug(level, strands, center.y), halfWidth * (0.7F + random.nextFloat() * 0.6F),
            life - random.nextInt(Math.max(1, life / 4)), seed * 17 + i, 2));
      }
   }

   public static void sparks(Vec3 at, int count, float speed, int life, long seed) {
      Random random = new Random(seed);
      int n = Math.max(0, Math.min(200, count));
      for (int i = 0; i < n && SPARKS.size() < MAX_SPARKS; i++) {
         Vec3 v = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.8 + 0.3, random.nextGaussian()).normalize()
            .scale(speed * (0.4 + random.nextDouble()));
         SPARKS.add(new Spark(at, v, Math.max(4, (int)(life * (0.5 + random.nextDouble() * 0.5))), 0.03F + random.nextFloat() * 0.035F));
      }
   }

   /** Light thrown on the ground around a strike; it flickers with the bolt of the same seed. */
   public static void lightPool(Vec3 center, float radius, float power, int life, long seed) {
      if (POOLS.size() >= MAX_POOLS) {
         POOLS.remove(0);
      }

      POOLS.add(new Pool(center, radius, power, life, seed, false));
   }

   /** The underside of the clouds lit by the bolt. */
   public static void skyFlash(Vec3 center, float radius, int life, long seed) {
      if (POOLS.size() >= MAX_POOLS) {
         POOLS.remove(0);
      }

      POOLS.add(new Pool(center, radius, 1.0F, life, seed, true));
   }

   /** A burn decal: dark core and seeded rays, hot for the first second, fading at the end of its life. */
   public static void scorch(ClientLevel level, Vec3 center, float radius, int rays, int life, long seed) {
      Decal decal = Decal.make(level, center, Math.min(8.0F, radius), Math.max(3, Math.min(24, rays)), life, seed);
      if (decal.count > 0) {
         DECALS.add(decal, level.getGameTime());
      }
   }

   /**
    * Storm cloud owned by {@code key}: call every tick while it lives
    * (vortex of dark pixel puffs above {@code center}); it fades out by
    * itself once the owner stops updating it.
    */
   public static void cloud(Object key, Vec3 center, float radius, float density, float flash) {
      Cloud cloud = CLOUDS.computeIfAbsent(key, k -> new Cloud(RANDOM.nextLong()));
      cloud.center = center;
      cloud.radius = radius;
      cloud.target = Math.max(0.0F, Math.min(1.0F, density));
      cloud.flash = Math.max(cloud.flash, Math.max(0.0F, Math.min(1.0F, flash)));
      cloud.touched = 2;
   }

   private static void add(Channel channel) {
      if (CHANNELS.size() >= MAX_CHANNELS) {
         CHANNELS.remove(0);
      }

      CHANNELS.add(channel);
   }

   public static boolean active() {
      return !CHANNELS.isEmpty() || !SPARKS.isEmpty() || !POOLS.isEmpty() || DECALS.size() > 0 || !CLOUDS.isEmpty();
   }

   public static void clear() {
      CHANNELS.clear();
      SPARKS.clear();
      POOLS.clear();
      DECALS.clear();
      CLOUDS.clear();
   }

   // --- tick ---

   public static void tick(ClientLevel level) {
      for (Iterator<Channel> it = CHANNELS.iterator(); it.hasNext();) {
         Channel channel = it.next();
         channel.age++;
         if (channel.age >= channel.life) {
            it.remove();
         } else if (channel.age > 0) {
            channel.restrike();
         }
      }

      for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext();) {
         Spark spark = it.next();
         spark.prev = spark.pos;
         spark.pos = spark.pos.add(spark.vel);
         spark.vel = spark.vel.scale(0.9).add(0.0, -0.035, 0.0);
         if (++spark.age >= spark.life) {
            it.remove();
         }
      }

      POOLS.removeIf(pool -> ++pool.age >= pool.life);
      DECALS.expire(level.getGameTime());
      for (Iterator<Cloud> it = CLOUDS.values().iterator(); it.hasNext();) {
         Cloud cloud = it.next();
         cloud.age++;
         if (--cloud.touched < 0) {
            cloud.target = 0.0F;
         }

         cloud.density += (cloud.target - cloud.density) * 0.08F;
         cloud.flash *= 0.55F;
         if (cloud.touched < 0 && cloud.density < 0.01F) {
            it.remove();
         }
      }
   }

   // --- render ---

   /** Normal-alpha pass (inside HeroImpactFx's first batch): decals and storm clouds. */
   public static void renderSolid(BufferBuilder buffer, Vec3 cameraPos, Camera camera, long gameTime, float partialTick) {
      for (Map.Entry<Decal, Long> entry : DECALS.entries()) {
         Decal decal = entry.getKey();
         float age = gameTime - entry.getValue() + partialTick;
         float fade = 1.0F - clamp((age / decal.life - 0.7F) / 0.3F);
         if (fade > 0.0F) {
            decal.draw(buffer, cameraPos, 22, 18, 16, 215.0F * fade, false);
         }
      }

      for (Cloud cloud : CLOUDS.values()) {
         cloud.draw(buffer, cameraPos, camera, partialTick, false);
      }
   }

   /** Additive pass (inside HeroImpactFx's glow batch): channels, sparks, light, hot decals, cloud flashes. */
   public static void renderGlow(BufferBuilder buffer, Vec3 cameraPos, Camera camera, long gameTime, float partialTick) {
      for (Pool pool : POOLS) {
         pool.draw(buffer, cameraPos, partialTick);
      }

      for (Channel channel : CHANNELS) {
         channel.draw(buffer, cameraPos, partialTick);
      }

      for (Spark spark : SPARKS) {
         Vec3 at = spark.prev.lerp(spark.pos, partialTick);
         float fade = 1.0F - (spark.age + partialTick) / spark.life;
         if (fade > 0.0F) {
            int alpha = (int)(255 * fade);
            PixelVfx.billboardPixel(buffer, cameraPos, camera, at, spark.size, 235, 245, 255, alpha);
            PixelVfx.billboardPixel(buffer, cameraPos, camera, at.subtract(spark.vel.scale(0.6)), spark.size * 0.7F, 120, 170, 255, alpha / 2);
         }
      }

      for (Map.Entry<Decal, Long> entry : DECALS.entries()) {
         Decal decal = entry.getKey();
         float age = gameTime - entry.getValue() + partialTick;
         float hot = 1.0F - clamp(age / 40.0F);
         if (hot > 0.0F) {
            decal.draw(buffer, cameraPos, 255, 120, 40, 190.0F * hot * hot, true);
         }
      }

      for (Cloud cloud : CLOUDS.values()) {
         cloud.draw(buffer, cameraPos, camera, partialTick, true);
      }
   }

   /** One camera-facing ribbon strip along {@code points}, tapered by {@code taper} toward the last point. */
   static void ribbon(BufferBuilder buffer, Vec3 cameraPos, Vec3[] points, int count, float halfWidth, boolean taper, int r, int g, int b, int alpha) {
      if (count < 2 || alpha <= 0 || halfWidth <= 0.0F) {
         return;
      }

      Vec3 prevA = null;
      Vec3 prevB = null;
      for (int i = 0; i < count; i++) {
         Vec3 p = points[i];
         Vec3 tangent = points[Math.min(count - 1, i + 1)].subtract(points[Math.max(0, i - 1)]);
         Vec3 view = p.subtract(cameraPos);
         Vec3 side = tangent.cross(view);
         double len = side.length();
         side = len < 1.0E-6 ? BoltShape.perpendicular(tangent.lengthSqr() < 1.0E-9 ? new Vec3(0, 1, 0) : tangent.normalize()) : side.scale(1.0 / len);
         float w = halfWidth * (taper ? Math.max(0.15F, 1.0F - (float)i / (count - 1)) : 1.0F);
         Vec3 a = view.add(side.scale(w));
         Vec3 c = view.subtract(side.scale(w));
         if (prevA != null) {
            vertex(buffer, prevA, r, g, b, alpha);
            vertex(buffer, prevB, r, g, b, alpha);
            vertex(buffer, c, r, g, b, alpha);
            vertex(buffer, a, r, g, b, alpha);
         }

         prevA = a;
         prevB = c;
      }
   }

   private static void vertex(BufferBuilder buffer, Vec3 p, int r, int g, int b, int a) {
      buffer.vertex(p.x, p.y, p.z).color(r, g, b, Math.min(255, a)).endVertex();
   }

   // --- terrain ---

   /** The top of the ground under (x, z) near {@code aroundY}, or NaN when there is none within a few blocks. */
   static double groundY(ClientLevel level, double x, double z, double aroundY) {
      int top = (int)Math.floor(aroundY + 1.5);
      for (int y = top; y >= top - 5; y--) {
         BlockPos pos = BlockPos.containing(x, y, z);
         BlockState state = level.getBlockState(pos);
         VoxelShape shape = state.getCollisionShape(level, pos);
         if (!shape.isEmpty()) {
            return y + shape.max(Direction.Axis.Y);
         }
      }

      return Double.NaN;
   }

   static Vec3 onGround(ClientLevel level, Vec3 at, double aroundY) {
      double y = groundY(level, at.x, at.z, aroundY);
      return new Vec3(at.x, Double.isNaN(y) ? at.y : y, at.z);
   }

   private static List<BoltShape.Strand> hug(ClientLevel level, List<BoltShape.Strand> strands, double aroundY) {
      List<BoltShape.Strand> out = new ArrayList<>(strands.size());
      for (BoltShape.Strand strand : strands) {
         Vec3[] points = strand.points().clone();
         for (int i = 0; i < points.length; i++) {
            Vec3 p = points[i];
            double y = groundY(level, p.x, p.z, aroundY);
            points[i] = new Vec3(p.x, (Double.isNaN(y) ? aroundY : y) + 0.06 + Math.abs(p.y - aroundY) * 0.25, p.z);
         }

         out.add(new BoltShape.Strand(points, strand.width(), strand.brightness(), strand.level()));
      }

      return out;
   }

   private static float gauss(Random random, float radius) {
      return (float)random.nextGaussian() * radius * 0.6F;
   }

   static float clamp(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   // --- live shapes ---

   private static final class Channel {
      final Shape shape;
      final List<BoltShape.Strand> base;
      List<BoltShape.Strand> strands;
      final float halfWidth;
      final int life;
      final long seed;
      final int rejitter;
      int age;

      Channel(Shape shape, List<BoltShape.Strand> strands, float halfWidth, int life, long seed, int rejitter) {
         this.shape = shape;
         this.base = strands;
         this.strands = strands;
         this.halfWidth = halfWidth;
         this.life = Math.max(2, life);
         this.seed = seed;
         this.rejitter = rejitter;
      }

      void restrike() {
         if (this.shape == Shape.BOLT) {
            // Each re-strike after the return stroke takes a slightly new path.
            if (this.age > BoltShape.LEADER_TICKS + BoltShape.STROKE_TICKS && this.age % 3 == 0) {
               this.strands = BoltShape.perturb(this.base, this.seed + this.age, 0.35 + this.halfWidth);
            }
         } else if (this.rejitter > 0 && this.age % this.rejitter == 0) {
            this.strands = BoltShape.perturb(this.base, this.seed + this.age, this.shape == Shape.CRAWL ? 0.25 : 0.3);
         }
      }

      float brightness(float age) {
         return switch (this.shape) {
            case BOLT -> BoltShape.boltBrightness(age, this.life, this.seed);
            case ARC -> BoltShape.arcBrightness(age, this.life, this.seed);
            case CRAWL -> {
               float grow = clamp(age / 5.0F);
               yield BoltShape.arcBrightness(age, this.life, this.seed) * (0.6F + 0.4F * grow);
            }
         };
      }

      void draw(BufferBuilder buffer, Vec3 cameraPos, float partialTick) {
         float age = this.age + partialTick;
         if (age < 0.0F) {
            return;
         }

         float light = this.brightness(age);
         if (light <= 0.004F) {
            return;
         }

         boolean afterglow = this.shape == Shape.BOLT && age > this.life * 0.55F;
         float reach = this.shape == Shape.BOLT ? BoltShape.leaderReach(age) : this.shape == Shape.CRAWL ? clamp(age / 5.0F) : 1.0F;
         for (BoltShape.Strand strand : this.strands) {
            if (this.shape == Shape.BOLT && strand.level() > 0 && age < BoltShape.LEADER_TICKS) {
               continue;
            }

            Vec3[] points = strand.points();
            int count = strand.level() == 0 || this.shape == Shape.CRAWL ? Math.max(2, (int)Math.ceil(points.length * reach)) : points.length;
            count = Math.min(points.length, count);
            float b = light * strand.brightness();
            float w = this.halfWidth * strand.width();
            boolean taper = strand.level() > 0 || this.shape != Shape.BOLT;
            // Outer glow, inner glow, then the white core (additive: overlaps add up to white).
            ribbon(buffer, cameraPos, points, count, w * 8.0F, taper, 60, 100, 255, (int)(48 * b));
            ribbon(buffer, cameraPos, points, count, w * 3.0F, taper, 150, 195, 255, (int)(130 * b));
            if (afterglow) {
               ribbon(buffer, cameraPos, points, count, w * 0.8F, taper, 205, 175, 255, (int)(255 * b));
            } else {
               ribbon(buffer, cameraPos, points, count, w, taper, 255, 255, 255, (int)(255 * b));
            }
         }
      }
   }

   private static final class Spark {
      final Vec3 start;
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      final int life;
      final float size;
      int age;

      Spark(Vec3 at, Vec3 vel, int life, float size) {
         this.start = at;
         this.pos = at;
         this.prev = at;
         this.vel = vel;
         this.life = life;
         this.size = size;
      }
   }

   private static final class Pool {
      final Vec3 center;
      final float radius;
      final float power;
      final int life;
      final long seed;
      final boolean sky;
      int age;

      Pool(Vec3 center, float radius, float power, int life, long seed, boolean sky) {
         this.center = center;
         this.radius = radius;
         this.power = power;
         this.life = Math.max(2, life);
         this.seed = seed;
         this.sky = sky;
      }

      void draw(BufferBuilder buffer, Vec3 cameraPos, float partialTick) {
         float age = this.age + partialTick;
         // Same flicker as the bolt it belongs to, stretched over this light's life.
         float light = BoltShape.boltBrightness(age * 40.0F / this.life, 40, this.seed) * this.power;
         if (light <= 0.01F) {
            return;
         }

         int rings = 8;
         int segments = 28;
         double y = this.center.y + (this.sky ? 0.0 : 0.08);
         for (int ring = 0; ring < rings; ring++) {
            double r0 = this.radius * ring / rings;
            double r1 = this.radius * (ring + 1) / rings;
            float falloff = 1.0F - (float)ring / rings;
            int r = this.sky ? 150 : 110;
            int g = this.sky ? 170 : 150;
            int b = 255;
            int alpha = (int)((this.sky ? 70 : 85) * light * falloff * falloff);
            if (alpha <= 0) {
               continue;
            }

            for (int s = 0; s < segments; s++) {
               double a0 = Math.PI * 2.0 * s / segments;
               double a1 = Math.PI * 2.0 * (s + 1) / segments;
               vertex(buffer, new Vec3(this.center.x + Math.cos(a0) * r0, y, this.center.z + Math.sin(a0) * r0).subtract(cameraPos), r, g, b, alpha);
               vertex(buffer, new Vec3(this.center.x + Math.cos(a1) * r0, y, this.center.z + Math.sin(a1) * r0).subtract(cameraPos), r, g, b, alpha);
               vertex(buffer, new Vec3(this.center.x + Math.cos(a1) * r1, y, this.center.z + Math.sin(a1) * r1).subtract(cameraPos), r, g, b, alpha / 3);
               vertex(buffer, new Vec3(this.center.x + Math.cos(a0) * r1, y, this.center.z + Math.sin(a0) * r1).subtract(cameraPos), r, g, b, alpha / 3);
            }
         }
      }
   }

   /** Burn decal cells (1/8 block) precomputed once on the terrain. */
   private static final class Decal {
      static final float CELL = 0.125F;
      final float[] xs;
      final float[] ys;
      final float[] zs;
      final float[] heat;
      final int count;
      final float life;

      private Decal(float[] xs, float[] ys, float[] zs, float[] heat, int count, float life) {
         this.xs = xs;
         this.ys = ys;
         this.zs = zs;
         this.heat = heat;
         this.count = count;
         this.life = Math.max(20.0F, life);
      }

      static Decal make(ClientLevel level, Vec3 center, float radius, int rays, int life, long seed) {
         Random random = new Random(seed);
         double[] rayAngle = new double[rays];
         double[] rayLength = new double[rays];
         for (int i = 0; i < rays; i++) {
            rayAngle[i] = Math.PI * 2.0 * (i + random.nextDouble() * 0.7) / rays;
            rayLength[i] = radius * (0.45 + random.nextDouble() * 0.55);
         }

         int side = (int)Math.ceil(radius / CELL);
         int max = (side * 2 + 1) * (side * 2 + 1);
         float[] xs = new float[max];
         float[] ys = new float[max];
         float[] zs = new float[max];
         float[] heat = new float[max];
         Map<Long, Double> columns = new HashMap<>();
         int n = 0;
         for (int ix = -side; ix <= side; ix++) {
            for (int iz = -side; iz <= side; iz++) {
               double dx = ix * CELL;
               double dz = iz * CELL;
               double d = Math.sqrt(dx * dx + dz * dz);
               if (d > radius) {
                  continue;
               }

               double angle = Math.atan2(dz, dx);
               double core = Math.max(0.0, 1.0 - d / (radius * 0.32));
               double ray = 0.0;
               for (int i = 0; i < rays; i++) {
                  if (d > rayLength[i]) {
                     continue;
                  }

                  double diff = Math.abs(Math.atan2(Math.sin(angle - rayAngle[i] - Math.sin(d * 2.3 + i) * 0.25), Math.cos(angle - rayAngle[i])));
                  double across = diff * d;
                  double width = 0.09 + 0.12 * (1.0 - d / rayLength[i]);
                  ray = Math.max(ray, Math.exp(-(across * across) / (width * width)) * (1.0 - d / rayLength[i] * 0.7));
               }

               float noise = BoltShape.hash01(seed, ix * 977 + iz);
               double value = Math.max(core * (0.75 + 0.25 * noise), ray * (0.65 + 0.35 * noise));
               if (value < 0.12) {
                  continue;
               }

               double x = center.x + dx;
               double z = center.z + dz;
               long key = ((long)Math.floor(x) << 32) ^ ((long)Math.floor(z) & 0xFFFFFFFFL);
               Double y = columns.computeIfAbsent(key, k -> groundY(level, x, z, center.y));
               if (y.isNaN() || Math.abs(y - center.y) > 2.5) {
                  continue;
               }

               xs[n] = (float)x;
               ys[n] = (float)(y + 0.012 + noise * 0.002);
               zs[n] = (float)z;
               heat[n] = (float)Math.min(1.0, value);
               n++;
            }
         }

         return new Decal(xs, ys, zs, heat, n, life);
      }

      void draw(BufferBuilder buffer, Vec3 cameraPos, int r, int g, int b, float alpha, boolean hot) {
         float h = CELL * 0.5F;
         for (int i = 0; i < this.count; i++) {
            float value = hot ? this.heat[i] * this.heat[i] : this.heat[i];
            int a = (int)(alpha * value);
            if (a <= 2) {
               continue;
            }

            double x = this.xs[i] - cameraPos.x;
            double y = this.ys[i] - cameraPos.y + (hot ? 0.002 : 0.0);
            double z = this.zs[i] - cameraPos.z;
            buffer.vertex(x - h, y, z - h).color(r, g, b, a).endVertex();
            buffer.vertex(x - h, y, z + h).color(r, g, b, a).endVertex();
            buffer.vertex(x + h, y, z + h).color(r, g, b, a).endVertex();
            buffer.vertex(x + h, y, z - h).color(r, g, b, a).endVertex();
         }
      }
   }

   /** Storm vortex: pixel puffs on a slowly turning, contracting spiral disc. */
   private static final class Cloud {
      static final int PUFFS = 260;
      final long seed;
      Vec3 center = Vec3.ZERO;
      float radius;
      float density;
      float target;
      float flash;
      int touched;
      int age;

      Cloud(long seed) {
         this.seed = seed;
      }

      void draw(BufferBuilder buffer, Vec3 cameraPos, Camera camera, float partialTick, boolean glow) {
         if (this.density <= 0.01F) {
            return;
         }

         float t = this.age + partialTick;
         for (int i = 0; i < PUFFS; i++) {
            float u = BoltShape.hash01(this.seed, i);
            float v = BoltShape.hash01(this.seed, i + 7919);
            float w = BoltShape.hash01(this.seed, i + 15887);
            double ring = Math.sqrt(u);
            // Inner puffs turn faster: a vortex, not a rotating plate.
            double angle = v * Math.PI * 2.0 + t * (0.012 + 0.05 * (1.0 - ring)) + ring * 2.2;
            double r = this.radius * (0.12 + 0.88 * ring);
            double y = (w - 0.5) * 2.4 + (1.0 - ring) * -1.6;
            Vec3 p = this.center.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
            float size = 0.7F + 1.1F * w;
            if (glow) {
               float lit = this.flash * (BoltShape.hash01(this.seed + this.age / 2, i) > 0.6F ? 1.0F : 0.25F);
               int alpha = (int)(120 * lit * this.density);
               if (alpha > 2) {
                  PixelVfx.billboardPixel(buffer, cameraPos, camera, p.add(0, -0.2, 0), size * 0.9F, 140, 170, 255, alpha);
               }
            } else {
               int shade = 30 + (int)(22 * w);
               PixelVfx.billboardPixel(buffer, cameraPos, camera, p, size, shade, shade + 2, shade + 10, (int)(175 * this.density * (0.6F + 0.4F * (float)ring)));
            }
         }
      }
   }
}

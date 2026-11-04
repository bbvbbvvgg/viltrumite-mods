package dev.baranhan.viltrumitecore.client.particle;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Meteor Rain 2.0 smoke: irregular volumetric puffs (8 procedural sprites, random roll) that start hot and
 * lit from inside (orange, full block light) and cool into dark grey, grow, slow down by drag, rise by
 * buoyancy and wander on their own turbulence, fade in and out softly and thin out near the camera so a
 * puff never covers the view. One class, four styles: the flight trail, the impact billow, the impact
 * dust ring and the smoke of fires and smouldering craters.
 *
 * <p>City disaster aftermath adds four more: {@link Style#RUIN} smoke seeping from broken windows and
 * breaches, the tall {@link Style#PLUME} of a burning ruin, low {@link Style#HAZE} lying over a wrecked
 * quarter and {@link Style#SETTLE} dust sinking back onto fresh rubble. They drift with the ruin wind
 * ({@link #setWind}, 0 for the meteor styles) and vary their shade so no two columns look alike.
 */
public class MeteorSmokeParticle extends TextureSheetParticle {
   public enum Style {
      // alphas are for the smooth 64 px puffs; QA showed fire columns of 0.7-alpha black puffs hiding a whole burning block
      //          size  sizeVar growth  life lifeVar hotR  hotG  hotB  hotTicks cool  coolVar alpha fadeIn drag   buoy    turb    roll
      TRAIL(      1.3F, 1.0F,   3.6F,   80,  50,     1.0F, 0.55F, 0.22F, 9,     0.20F, 0.08F,  0.62F, 3,     0.94F, 0.004F, 0.012F, 0.04F),
      BILLOW(     3.0F, 3.0F,   2.6F,   100, 80,     1.0F, 0.60F, 0.30F, 12,    0.22F, 0.09F,  0.72F, 2,     0.90F, 0.006F, 0.010F, 0.02F),
      DUST(       2.4F, 2.6F,   2.3F,   60,  60,     0.0F, 0.0F,  0.0F,  0,     0.40F, 0.08F,  0.58F, 2,     0.86F, 0.0015F, 0.008F, 0.02F, 0.0F, true),
      FIRE(       1.8F, 2.2F,   3.0F,   110, 80,     0.95F, 0.48F, 0.22F, 7,    0.17F, 0.08F,  0.46F, 4,     0.96F, 0.011F, 0.010F, 0.03F),
      // structural collapse (client/collapse): cold concrete dust, a ground-hugging wall and a slow rising plume
      COLLAPSE_DUST(3.0F, 2.6F,   2.7F,   140, 80,     0.0F, 0.0F,  0.0F,  0,     0.52F, 0.10F,  0.60F, 3,     0.93F, 0.0012F, 0.006F, 0.015F),
      COLLAPSE_PLUME(3.4F, 3.0F,  3.1F,   170, 90,     0.0F, 0.0F,  0.0F,  0,     0.48F, 0.10F,  0.56F, 4,     0.90F, 0.006F, 0.008F, 0.015F),
      // aftermath: slow, long-lived, wind-driven; alphas low because many overlap in a column
      RUIN(       1.5F, 1.6F,   3.4F,   170, 110,    0.0F, 0.0F,  0.0F,  0,     0.15F, 0.13F,  0.44F, 8,     0.95F, 0.0065F, 0.008F, 0.02F, 1.0F, false),
      PLUME(      2.6F, 2.4F,   3.0F,   220, 120,    0.95F, 0.5F, 0.22F, 10,    0.11F, 0.08F,  0.50F, 6,     0.95F, 0.0100F, 0.007F, 0.015F, 0.8F, false),
      HAZE(       5.0F, 4.0F,   1.8F,   240, 140,    0.0F, 0.0F,  0.0F,  0,     0.46F, 0.08F,  0.12F, 30,    0.90F, 0.0004F, 0.004F, 0.008F, 0.6F, true),
      SETTLE(     2.2F, 2.0F,   2.6F,   110, 80,     0.0F, 0.0F,  0.0F,  0,     0.48F, 0.10F,  0.40F, 3,     0.88F, -0.0008F, 0.006F, 0.015F, 0.5F, true);

      final float size, sizeVar, growth;
      final int life, lifeVar;
      final float hotR, hotG, hotB;
      final int hotTicks;
      final float cool, coolVar, alpha;
      final int fadeIn;
      final float drag, buoyancy, turbulence, roll;
      /** Share of the ruin wind this style follows (0 = still air, the meteor styles). */
      final float wind;
      /** Warm earthy dust instead of neutral smoke. */
      final boolean earthy;

      Style(float size, float sizeVar, float growth, int life, int lifeVar, float hotR, float hotG, float hotB, int hotTicks, float cool, float coolVar,
         float alpha, int fadeIn, float drag, float buoyancy, float turbulence, float roll) {
         this(size, sizeVar, growth, life, lifeVar, hotR, hotG, hotB, hotTicks, cool, coolVar, alpha, fadeIn, drag, buoyancy, turbulence, roll, 0.0F, false);
      }

      Style(float size, float sizeVar, float growth, int life, int lifeVar, float hotR, float hotG, float hotB, int hotTicks, float cool, float coolVar,
         float alpha, int fadeIn, float drag, float buoyancy, float turbulence, float roll, float wind, boolean earthy) {
         this.wind = wind;
         this.earthy = earthy;
         this.size = size;
         this.sizeVar = sizeVar;
         this.growth = growth;
         this.life = life;
         this.lifeVar = lifeVar;
         this.hotR = hotR;
         this.hotG = hotG;
         this.hotB = hotB;
         this.hotTicks = hotTicks;
         this.cool = cool;
         this.coolVar = coolVar;
         this.alpha = alpha;
         this.fadeIn = fadeIn;
         this.drag = drag;
         this.buoyancy = buoyancy;
         this.turbulence = turbulence;
         this.roll = roll;
      }
   }

   /**
    * Draw only the visible rectangle of the sprite ({@link SpriteTrim}). On by default; {@code -Dviltrumitecore.smoke.trim=false}
    * restores the full quads for A/B frame-time and screenshot comparisons; the QA harness can flip it at runtime
    * ({@code smoketrim on|off}) so both variants are measured on the very same particles.
    */
   private static volatile boolean trimEnabled = !"false".equalsIgnoreCase(System.getProperty("viltrumitecore.smoke.trim"));

   public static void setTrimEnabled(boolean on) {
      trimEnabled = on;
   }

   public static boolean trimEnabled() {
      return trimEnabled;
   }

   // QA counters (render thread only): how many puffs took the trimmed path vs the full vanilla quad
   private static long trimmedQuads;
   private static long fullQuads;

   public static void resetQuadStats() {
      trimmedQuads = 0L;
      fullQuads = 0L;
   }

   /** "trimmed/total" puff quads drawn since the last reset (FrameProbe perfstate). */
   public static String quadStats() {
      return trimmedQuads + "/" + (trimmedQuads + fullQuads);
   }

   /** Ruin wind in blocks per tick per tick (set by the aftermath client each tick; 0 when no ruin is near). */
   private static float windX;
   private static float windZ;

   private final Style style;
   private final float maxAlpha;
   private final float coolR, coolG, coolB;
   private final int hotTicks;
   private final float rollSpeed;
   private final float seedA, seedB, seedC;
   /** Visible part of the sprite in quad fractions {s0, s1, t0, t1} ({@link SpriteTrim}). */
   private final float[] trim;

   protected MeteorSmokeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, Style style) {
      super(level, x, y, z);
      this.style = style;
      this.xd = dx;
      this.yd = dy;
      this.zd = dz;
      this.quadSize = style.size + this.random.nextFloat() * style.sizeVar;
      this.lifetime = style.life + this.random.nextInt(style.lifeVar + 1);
      this.hasPhysics = false;
      this.gravity = 0.0F;
      this.friction = style.drag;
      float grey = style.cool + this.random.nextFloat() * style.coolVar;
      if (style.earthy) {
         // warm earthy dust
         this.coolR = grey * 1.1F;
         this.coolG = grey * 0.95F;
         this.coolB = grey * 0.8F;
      } else if (style.wind > 0.0F) {
         // ruin smoke: burning plastics and tar are near black, plaster and wood smoke browner or bluer
         float shade = this.random.nextFloat();
         float warm = shade < 0.3F ? 1.12F : shade > 0.8F ? 0.94F : 1.03F;
         this.coolR = grey * warm;
         this.coolG = grey;
         this.coolB = grey * (2.0F - warm) * 0.98F;
      } else {
         this.coolR = grey * 1.04F;
         this.coolG = grey;
         this.coolB = grey * 0.97F;
      }
      // not every puff of a billow starts glowing
      this.hotTicks = style.hotTicks == 0 || style == Style.BILLOW && this.random.nextInt(3) == 0 ? 0 : style.hotTicks + this.random.nextInt(style.hotTicks + 1);
      this.maxAlpha = style.alpha * (0.8F + this.random.nextFloat() * 0.2F);
      this.alpha = 0.0F;
      this.roll = this.random.nextFloat() * Mth.TWO_PI;
      this.oRoll = this.roll;
      this.rollSpeed = (this.random.nextFloat() - 0.5F) * 2.0F * style.roll;
      this.seedA = this.random.nextFloat() * 100.0F;
      this.seedB = this.random.nextFloat() * 100.0F;
      this.seedC = this.random.nextFloat() * 100.0F;
      this.pickSprite(sprites);
      this.trim = SpriteTrim.of(this.sprite, UV_INSET);
      this.updateColor();
   }

   @Override
   public void tick() {
      this.xo = this.x;
      this.yo = this.y;
      this.zo = this.z;
      this.oRoll = this.roll;
      if (this.age++ >= this.lifetime) {
         this.remove();
         return;
      }

      float t = this.age * 0.15F;
      float turb = this.style.turbulence;
      this.xd += Mth.sin(t + this.seedA) * turb + (this.random.nextFloat() - 0.5F) * turb * 0.6F;
      this.zd += Mth.cos(t * 0.9F + this.seedB) * turb + (this.random.nextFloat() - 0.5F) * turb * 0.6F;
      this.yd += this.style.buoyancy + Mth.sin(t * 1.3F + this.seedC) * turb * 0.4F;
      if (this.style.wind > 0.0F) {
         this.xd += windX * this.style.wind;
         this.zd += windZ * this.style.wind;
      }

      this.move(this.xd, this.yd, this.zd);
      this.xd *= this.friction;
      this.yd *= this.friction;
      this.zd *= this.friction;
      this.roll += this.rollSpeed * (1.0F - 0.6F * this.age / (float)this.lifetime);
      this.updateColor();
   }

   private void updateColor() {
      float hot = this.hotTicks == 0 ? 0.0F : 1.0F - Mth.clamp(this.age / (float)this.hotTicks, 0.0F, 1.0F);
      hot = hot * hot;
      float life = this.age / (float)this.lifetime;
      float dim = 1.0F - 0.2F * life;
      this.rCol = Mth.lerp(hot, this.coolR * dim, this.style.hotR);
      this.gCol = Mth.lerp(hot, this.coolG * dim, this.style.hotG);
      this.bCol = Mth.lerp(hot, this.coolB * dim, this.style.hotB);
      float in = this.style.fadeIn <= 0 ? 1.0F : Mth.clamp(this.age / (float)this.style.fadeIn, 0.0F, 1.0F);
      float out = 1.0F - smooth(Mth.clamp((life - 0.45F) / 0.55F, 0.0F, 1.0F));
      this.alpha = this.maxAlpha * in * out;
   }

   /** The ruin wind the aftermath styles drift with (client tick; values are clamped to a breeze). */
   public static void setWind(float x, float z) {
      windX = Mth.clamp(x, -0.01F, 0.01F);
      windZ = Mth.clamp(z, -0.01F, 0.01F);
   }

   private static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   @Override
   public float getQuadSize(float partialTick) {
      float life = Mth.clamp((this.age + partialTick) / (float)this.lifetime, 0.0F, 1.0F);
      float ease = 1.0F - (1.0F - life) * (1.0F - life);
      return this.quadSize * (1.0F + (this.style.growth - 1.0F) * ease);
   }

   @Override
   protected int getLightColor(float partialTick) {
      int world = super.getLightColor(partialTick);
      if (this.hotTicks == 0) {
         return world;
      }

      float hot = 1.0F - Mth.clamp((this.age + partialTick) / (float)this.hotTicks, 0.0F, 1.0F);
      int block = Math.max(LightTexture.block(world), Math.round(15.0F * hot));
      return LightTexture.pack(block, LightTexture.sky(world));
   }

   @Override
   public void render(VertexConsumer consumer, Camera camera, float partialTick) {
      // thin out a puff that reaches the camera instead of filling the screen with grey
      double px = Mth.lerp(partialTick, this.xo, this.x) - camera.getPosition().x;
      double py = Mth.lerp(partialTick, this.yo, this.y) - camera.getPosition().y;
      double pz = Mth.lerp(partialTick, this.zo, this.z) - camera.getPosition().z;
      float size = this.getQuadSize(partialTick);
      float d = (float)Math.sqrt(px * px + py * py + pz * pz);
      float near = Mth.clamp((d - size * 0.35F) / (size * 0.9F + 1.0F), 0.0F, 1.0F);
      if (near <= 0.01F) {
         return;
      }

      float keep = this.alpha;
      this.alpha = keep * near;
      if (trimEnabled && this.trim != SpriteTrim.FULL && MeteorParticleRenderTypes.softSheetActive()) {
         if (this.renderTrimmed(consumer, camera, partialTick)) {
            trimmedQuads++;
         } else {
            fullQuads++;
            super.render(consumer, camera, partialTick);
         }
      } else {
         fullQuads++;
         super.render(consumer, camera, partialTick);
      }
      this.alpha = keep;
   }

   /**
    * The vanilla {@code SingleQuadParticle.render} quad cut down to the sprite's visible rectangle. Same corners
    * order, the same UV per point of the plane, so every pixel that can be non-transparent is drawn exactly as
    * before; the transparent margin (≈42 % of the quad's area) is simply not rasterized. Only valid while the soft
    * program samples the atlas linearly without mipmaps ({@link MeteorParticleRenderTypes#softSheetActive}).
    *
    * <p>Fog is evaluated per vertex and interpolated, so moved corners would carry different fog distances. When
    * fog cannot differ (the whole puff is nearer than the fog start or beyond the fog end, {@link #fogNeutral}) the
    * rectangle is drawn as one quad. Otherwise it is cut along the original triangle diagonal and every vertex gets
    * the fog distance the original triangle interpolates there ({@link TrimGeometry}), sent to the soft shader in
    * the lightmap's high bits. Returns {@code false} (caller draws the full quad) only beyond the encodable range.
    */
   private boolean renderTrimmed(VertexConsumer consumer, Camera camera, float partialTick) {
      Vec3 cam = camera.getPosition();
      float px = (float)(Mth.lerp(partialTick, this.xo, this.x) - cam.x());
      float py = (float)(Mth.lerp(partialTick, this.yo, this.y) - cam.y());
      float pz = (float)(Mth.lerp(partialTick, this.zo, this.z) - cam.z());
      float size = this.getQuadSize(partialTick);
      boolean cylinder = RenderSystem.getShaderFogShape() == FogShape.CYLINDER;
      boolean neutral = fogNeutral(px, py, pz, size, RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), cylinder);
      Quaternionf rotation;
      if (this.roll == 0.0F) {
         rotation = camera.rotation();
      } else {
         rotation = new Quaternionf(camera.rotation());
         rotation.rotateZ(Mth.lerp(partialTick, this.oRoll, this.roll));
      }

      // vanilla corner (x, y) in -1..1 shows U1 at x = -1 and U0 at x = +1, V1 at y = -1 and V0 at y = +1
      float s0 = this.trim[0], s1 = this.trim[1], t0 = this.trim[2], t1 = this.trim[3];
      float xNear = 1.0F - 2.0F * s1, xFar = 1.0F - 2.0F * s0;
      float yNear = 1.0F - 2.0F * t1, yFar = 1.0F - 2.0F * t0;
      float uA = this.getU0(), uB = this.getU1(), vA = this.getV0(), vB = this.getV1();
      float uS0 = Mth.lerp(s0, uA, uB), uS1 = Mth.lerp(s1, uA, uB);
      float vT0 = Mth.lerp(t0, vA, vB), vT1 = Mth.lerp(t1, vA, vB);
      int light = this.getLightColor(partialTick);
      if (neutral) {
         this.corner(consumer, rotation, xNear, yNear, size, px, py, pz, uS1, vT1, light);
         this.corner(consumer, rotation, xNear, yFar, size, px, py, pz, uS1, vT0, light);
         this.corner(consumer, rotation, xFar, yFar, size, px, py, pz, uS0, vT0, light);
         this.corner(consumer, rotation, xFar, yNear, size, px, py, pz, uS0, vT1, light);
         return true;
      }

      // inside the fog ramp: the original corners' fog distances, exactly as soft_particle.vsh computes them
      Matrix4f modelView = RenderSystem.getModelViewMatrix();
      float d0 = fogDistance(modelView, rotation, -1.0F, -1.0F, size, px, py, pz, cylinder);
      float d1 = fogDistance(modelView, rotation, -1.0F, 1.0F, size, px, py, pz, cylinder);
      float d2 = fogDistance(modelView, rotation, 1.0F, 1.0F, size, px, py, pz, cylinder);
      float d3 = fogDistance(modelView, rotation, 1.0F, -1.0F, size, px, py, pz, cylinder);
      if (Math.max(Math.max(d0, d1), Math.max(d2, d3)) >= TrimGeometry.MAX_FOG_DISTANCE) {
         return false;
      }
      float[] poly = POLY.get();
      for (int half = 0; half < 2; half++) {
         int n = TrimGeometry.clip(xNear, yNear, xFar, yFar, half == 0, poly);
         // fan as QUADS: (0,1,2,3) then (0,3,4,4); a 3-point half repeats its last point (zero-area triangle)
         for (int q = 0; q + 2 < n; q += 2) {
            int a = 0, b = q + 1, c = q + 2, d = Math.min(q + 3, n - 1);
            this.fogCorner(consumer, rotation, poly, a, size, px, py, pz, uA, uB, vA, vB, light, d0, d1, d2, d3);
            this.fogCorner(consumer, rotation, poly, b, size, px, py, pz, uA, uB, vA, vB, light, d0, d1, d2, d3);
            this.fogCorner(consumer, rotation, poly, c, size, px, py, pz, uA, uB, vA, vB, light, d0, d1, d2, d3);
            this.fogCorner(consumer, rotation, poly, d, size, px, py, pz, uA, uB, vA, vB, light, d0, d1, d2, d3);
         }
      }
      return true;
   }

   private static final ThreadLocal<float[]> POLY = ThreadLocal.withInitial(() -> new float[10]);

   /** Plane point i of {@code poly} with the vanilla UV mapping (U1 at x = -1, V1 at y = -1) and its exact fog distance. */
   private void fogCorner(VertexConsumer consumer, Quaternionf rotation, float[] poly, int i, float size, float px, float py, float pz,
      float uA, float uB, float vA, float vB, int light, float d0, float d1, float d2, float d3) {
      float x = poly[2 * i], y = poly[2 * i + 1];
      float u = Mth.lerp((1.0F - x) * 0.5F, uA, uB), v = Mth.lerp((1.0F - y) * 0.5F, vA, vB);
      int packed = TrimGeometry.packLight(light, TrimGeometry.fogAt(x, y, d0, d1, d2, d3));
      this.corner(consumer, rotation, x, y, size, px, py, pz, u, v, packed);
   }

   /** fog.glsl {@code fog_distance(ModelViewMat, Position, FogShape)} for corner (cx, cy) of this quad. */
   private static float fogDistance(Matrix4f mv, Quaternionf rotation, float cx, float cy, float size, float px, float py, float pz,
      boolean cylinder) {
      Vector3f p = new Vector3f(cx, cy, 0.0F).rotate(rotation).mul(size).add(px, py, pz);
      if (!cylinder) {
         return mv.transformPosition(p, new Vector3f()).length();
      }
      float xz = mv.transformPosition(new Vector3f(p.x(), 0.0F, p.z())).length();
      float y = mv.transformPosition(new Vector3f(0.0F, p.y(), 0.0F)).length();
      return Math.max(xz, y);
   }

   /**
    * True when vanilla per-vertex fog gives the same result for every point of a camera-facing quad of half-size
    * {@code size} centred at camera-relative (px, py, pz), whatever sub-rectangle is drawn. Any point of the quad
    * lies within {@code size * sqrt 2} of the centre; spherical fog distance is the length, cylindrical fog
    * distance lies between length / sqrt 2 and length. A one-block margin absorbs float error and small model-view offsets (camera shake).
    */
   static boolean fogNeutral(float px, float py, float pz, float size, float fogStart, float fogEnd, boolean cylinder) {
      float centre = Mth.sqrt(px * px + py * py + pz * pz);
      float reach = size * 1.4143F + 1.0F;
      if (centre + reach <= fogStart) {
         return true;
      }
      float nearest = centre - reach;
      if (cylinder) {
         nearest /= 1.4143F;
      }
      return nearest >= fogEnd;
   }

   private void corner(VertexConsumer consumer, Quaternionf rotation, float cx, float cy, float size, float px, float py, float pz, float u, float v,
      int light) {
      Vector3f p = new Vector3f(cx, cy, 0.0F).rotate(rotation).mul(size).add(px, py, pz);
      consumer.vertex(p.x(), p.y(), p.z()).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
   }

   @Override
   public ParticleRenderType getRenderType() {
      return MeteorParticleRenderTypes.SOFT_SMOKE;
   }

   // the soft sheet samples the atlas linearly: keep a 1 % margin so a puff never picks up its neighbour sprite
   static final float UV_INSET = 0.01F;

   @Override
   protected float getU0() {
      return Mth.lerp(UV_INSET, this.sprite.getU0(), this.sprite.getU1());
   }

   @Override
   protected float getU1() {
      return Mth.lerp(1.0F - UV_INSET, this.sprite.getU0(), this.sprite.getU1());
   }

   @Override
   protected float getV0() {
      return Mth.lerp(UV_INSET, this.sprite.getV0(), this.sprite.getV1());
   }

   @Override
   protected float getV1() {
      return Mth.lerp(1.0F - UV_INSET, this.sprite.getV0(), this.sprite.getV1());
   }

   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet sprites;
      private final Style style;

      public Provider(SpriteSet sprites, Style style) {
         this.sprites = sprites;
         this.style = style;
      }

      @Override
      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
         return new MeteorSmokeParticle(level, x, y, z, dx, dy, dz, this.sprites, this.style);
      }
   }
}

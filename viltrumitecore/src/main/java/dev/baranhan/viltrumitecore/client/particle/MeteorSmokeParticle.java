package dev.baranhan.viltrumitecore.client.particle;

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

/**
 * Meteor Rain 2.0 smoke: irregular volumetric puffs (8 procedural sprites, random roll) that start hot and
 * lit from inside (orange, full block light) and cool into dark grey, grow, slow down by drag, rise by
 * buoyancy and wander on their own turbulence, fade in and out softly and thin out near the camera so a
 * puff never covers the view. One class, four styles: the flight trail, the impact billow, the impact
 * dust ring and the smoke of fires and smouldering craters.
 */
public class MeteorSmokeParticle extends TextureSheetParticle {
   public enum Style {
      // alphas are for the smooth 64 px puffs; QA showed fire columns of 0.7-alpha black puffs hiding a whole burning block
      //          size  sizeVar growth  life lifeVar hotR  hotG  hotB  hotTicks cool  coolVar alpha fadeIn drag   buoy    turb    roll
      TRAIL(      1.3F, 1.0F,   3.6F,   80,  50,     1.0F, 0.55F, 0.22F, 9,     0.20F, 0.08F,  0.62F, 3,     0.94F, 0.004F, 0.012F, 0.04F),
      BILLOW(     3.0F, 3.0F,   2.6F,   100, 80,     1.0F, 0.60F, 0.30F, 12,    0.22F, 0.09F,  0.72F, 2,     0.90F, 0.006F, 0.010F, 0.02F),
      DUST(       2.4F, 2.6F,   2.3F,   60,  60,     0.0F, 0.0F,  0.0F,  0,     0.40F, 0.08F,  0.58F, 2,     0.86F, 0.0015F, 0.008F, 0.02F),
      FIRE(       1.8F, 2.2F,   3.0F,   110, 80,     0.95F, 0.48F, 0.22F, 7,    0.17F, 0.08F,  0.46F, 4,     0.96F, 0.011F, 0.010F, 0.03F),
      // structural collapse (client/collapse): cold concrete dust, a ground-hugging wall and a slow rising plume
      COLLAPSE_DUST(3.0F, 2.6F,   2.7F,   140, 80,     0.0F, 0.0F,  0.0F,  0,     0.52F, 0.10F,  0.60F, 3,     0.93F, 0.0012F, 0.006F, 0.015F),
      COLLAPSE_PLUME(3.4F, 3.0F,  3.1F,   170, 90,     0.0F, 0.0F,  0.0F,  0,     0.48F, 0.10F,  0.56F, 4,     0.90F, 0.006F, 0.008F, 0.015F);

      final float size, sizeVar, growth;
      final int life, lifeVar;
      final float hotR, hotG, hotB;
      final int hotTicks;
      final float cool, coolVar, alpha;
      final int fadeIn;
      final float drag, buoyancy, turbulence, roll;

      Style(float size, float sizeVar, float growth, int life, int lifeVar, float hotR, float hotG, float hotB, int hotTicks, float cool, float coolVar,
         float alpha, int fadeIn, float drag, float buoyancy, float turbulence, float roll) {
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

   private final Style style;
   private final float maxAlpha;
   private final float coolR, coolG, coolB;
   private final int hotTicks;
   private final float rollSpeed;
   private final float seedA, seedB, seedC;

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
      if (style == Style.DUST) {
         // warm earthy dust
         this.coolR = grey * 1.1F;
         this.coolG = grey * 0.95F;
         this.coolB = grey * 0.8F;
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
      super.render(consumer, camera, partialTick);
      this.alpha = keep;
   }

   @Override
   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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

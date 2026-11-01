package dev.baranhan.viltrumitecore.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Additive flame lick (Meteor Rain 2.0): white-yellow when born, orange, deep red, gone. Keeps the speed it
 * was born with (inertia, light drag) so a trail of them streams behind the meteor instead of hanging in
 * the air. {@code flare} = the bigger, slower, rising tongues of fires and brand landings.
 */
public class MeteorFlameParticle extends TextureSheetParticle {
   private final boolean flare;
   private final float rollSpeed;

   protected MeteorFlameParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean flare) {
      super(level, x, y, z);
      this.flare = flare;
      this.xd = dx;
      this.yd = dy;
      this.zd = dz;
      this.hasPhysics = false;
      this.gravity = 0.0F;
      this.friction = flare ? 0.9F : 0.93F;
      this.quadSize = flare ? 1.2F + this.random.nextFloat() * 1.6F : 0.9F + this.random.nextFloat() * 1.1F;
      this.lifetime = flare ? 14 + this.random.nextInt(14) : 7 + this.random.nextInt(9);
      this.roll = this.random.nextFloat() * Mth.TWO_PI;
      this.oRoll = this.roll;
      this.rollSpeed = (this.random.nextFloat() - 0.5F) * 0.3F;
      this.pickSprite(sprites);
      this.updateColor(0.0F);
   }

   /** Pure: the colour ramp of a flame by life fraction, {r, g, b}. */
   public static float[] ramp(float t) {
      float[][] keys = {{1.0F, 0.97F, 0.85F}, {1.0F, 0.78F, 0.32F}, {1.0F, 0.42F, 0.1F}, {0.62F, 0.12F, 0.03F}};
      float p = Mth.clamp(t, 0.0F, 1.0F) * (keys.length - 1);
      int i = Math.min(keys.length - 2, (int)p);
      float f = p - i;
      return new float[]{Mth.lerp(f, keys[i][0], keys[i + 1][0]), Mth.lerp(f, keys[i][1], keys[i + 1][1]), Mth.lerp(f, keys[i][2], keys[i + 1][2])};
   }

   private void updateColor(float t) {
      float[] c = ramp(t);
      this.rCol = c[0];
      this.gCol = c[1];
      this.bCol = c[2];
      this.alpha = (1.0F - t * t) * (this.flare ? 0.85F : 0.95F);
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

      this.yd += this.flare ? 0.018 : 0.008;
      this.move(this.xd, this.yd, this.zd);
      this.xd *= this.friction;
      this.yd *= this.friction;
      this.zd *= this.friction;
      this.roll += this.rollSpeed;
      this.updateColor(this.age / (float)this.lifetime);
   }

   @Override
   public float getQuadSize(float partialTick) {
      float t = Mth.clamp((this.age + partialTick) / (float)this.lifetime, 0.0F, 1.0F);
      // swell quickly, then lick away
      return this.quadSize * (0.6F + 0.8F * Mth.sin(Mth.PI * Math.min(1.0F, t * 1.4F)) * (1.0F - 0.5F * t));
   }

   @Override
   public void render(com.mojang.blaze3d.vertex.VertexConsumer consumer, net.minecraft.client.Camera camera, float partialTick) {
      // a fireball flame right at the camera fades instead of filling the screen with additive white (QA)
      double px = Mth.lerp(partialTick, this.xo, this.x) - camera.getPosition().x;
      double py = Mth.lerp(partialTick, this.yo, this.y) - camera.getPosition().y;
      double pz = Mth.lerp(partialTick, this.zo, this.z) - camera.getPosition().z;
      float size = this.getQuadSize(partialTick);
      float near = Mth.clamp(((float)Math.sqrt(px * px + py * py + pz * pz) - size * 0.4F) / (size * 0.8F + 1.0F), 0.0F, 1.0F);
      if (near <= 0.01F) {
         return;
      }

      float keep = this.alpha;
      this.alpha = keep * near;
      super.render(consumer, camera, partialTick);
      this.alpha = keep;
   }

   @Override
   protected int getLightColor(float partialTick) {
      return 15728880;
   }

   @Override
   public ParticleRenderType getRenderType() {
      return MeteorParticleRenderTypes.ADDITIVE;
   }

   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet sprites;
      private final boolean flare;

      public Provider(SpriteSet sprites, boolean flare) {
         this.sprites = sprites;
         this.flare = flare;
      }

      @Override
      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
         return new MeteorFlameParticle(level, x, y, z, dx, dy, dz, this.sprites, this.flare);
      }
   }
}

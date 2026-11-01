package dev.baranhan.viltrumitecore.client.particle;

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
 * Hot sparks and ash (Meteor Rain 2.0). A spark is additive, flies ballistically with the speed it was
 * thrown with, flickers and cools from white-yellow to dull red; it stops on the ground. Ash is a dark
 * flake that drifts and falls slowly; a quarter of it still glows at first (smouldering cinders).
 */
public class MeteorEmberParticle extends TextureSheetParticle {
   private final boolean ash;
   private final boolean glowing;
   private final float sway;

   protected MeteorEmberParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean ash) {
      super(level, x, y, z);
      this.ash = ash;
      this.xd = dx;
      this.yd = dy;
      this.zd = dz;
      this.sway = this.random.nextFloat() * 100.0F;
      if (ash) {
         this.glowing = this.random.nextInt(4) == 0;
         this.quadSize = 0.07F + this.random.nextFloat() * 0.08F;
         this.lifetime = 80 + this.random.nextInt(90);
         this.gravity = 0.006F;
         this.friction = 0.95F;
         this.hasPhysics = true;
      } else {
         this.glowing = true;
         this.quadSize = 0.06F + this.random.nextFloat() * 0.12F;
         this.lifetime = 18 + this.random.nextInt(34);
         this.gravity = 0.55F;
         this.friction = 0.985F;
         this.hasPhysics = true;
      }

      this.pickSprite(sprites);
      this.updateColor(0.0F);
   }

   private void updateColor(float t) {
      if (this.ash) {
         float glow = this.glowing ? Math.max(0.0F, 1.0F - t * 2.5F) : 0.0F;
         float grey = 0.16F + this.random.nextFloat() * 0.03F;
         this.rCol = Mth.lerp(glow, grey, 1.0F);
         this.gCol = Mth.lerp(glow, grey, 0.45F);
         this.bCol = Mth.lerp(glow, grey, 0.12F);
         this.alpha = 0.9F * (1.0F - Mth.clamp((t - 0.75F) / 0.25F, 0.0F, 1.0F));
      } else {
         float[] c = MeteorFlameParticle.ramp(t * 1.1F);
         this.rCol = c[0];
         this.gCol = c[1];
         this.bCol = c[2];
         float flicker = 0.75F + 0.25F * Mth.sin(this.age * 1.7F + this.sway);
         this.alpha = (1.0F - t * t) * flicker;
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.ash && !this.onGround) {
         this.xd += Mth.sin(this.age * 0.2F + this.sway) * 0.004F;
         this.zd += Mth.cos(this.age * 0.17F + this.sway) * 0.004F;
      }

      if (this.onGround && !this.ash) {
         // a spark that landed dies quickly instead of sliding along the ground
         this.age = Math.max(this.age, this.lifetime - 6);
      }

      this.updateColor(this.age / (float)this.lifetime);
   }

   @Override
   protected int getLightColor(float partialTick) {
      if (!this.glowing) {
         return super.getLightColor(partialTick);
      }

      if (!this.ash) {
         return 15728880;
      }

      int world = super.getLightColor(partialTick);
      float glow = Math.max(0.0F, 1.0F - (this.age + partialTick) / this.lifetime * 2.5F);
      return LightTexture.pack(Math.max(LightTexture.block(world), Math.round(15.0F * glow)), LightTexture.sky(world));
   }

   @Override
   public ParticleRenderType getRenderType() {
      return this.ash ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : MeteorParticleRenderTypes.ADDITIVE;
   }

   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet sprites;
      private final boolean ash;

      public Provider(SpriteSet sprites, boolean ash) {
         this.sprites = sprites;
         this.ash = ash;
      }

      @Override
      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
         return new MeteorEmberParticle(level, x, y, z, dx, dy, dz, this.sprites, this.ash);
      }
   }
}

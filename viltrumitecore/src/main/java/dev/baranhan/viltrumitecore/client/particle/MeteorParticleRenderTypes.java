package dev.baranhan.viltrumitecore.client.particle;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/** Particle sheet with additive blending: flames and sparks glow over the sky and over smoke instead of greying it. */
public final class MeteorParticleRenderTypes {
   public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
      @Override
      public void begin(BufferBuilder builder, TextureManager textures) {
         RenderSystem.depthMask(false);
         RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         builder.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
      }

      @Override
      public void end(Tesselator tesselator) {
         tesselator.end();
         RenderSystem.defaultBlendFunc();
         RenderSystem.depthMask(true);
      }

      @Override
      public String toString() {
         return "VILTRUMITE_METEOR_ADDITIVE";
      }
   };

   private MeteorParticleRenderTypes() {
   }
}

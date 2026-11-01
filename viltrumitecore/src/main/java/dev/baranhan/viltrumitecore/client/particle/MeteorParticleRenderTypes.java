package dev.baranhan.viltrumitecore.client.particle;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.thanos.ThanosDissolve;
import java.io.IOException;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Particle sheets of the meteor and aftermath effects. {@link #ADDITIVE}: flames and sparks glow over the sky and
 * over smoke instead of greying it. {@link #SOFT_SMOKE}: faint, large ruin smoke drawn with linear filtering and a
 * program that keeps the sprite's soft falloff (vanilla cuts everything under 10 % alpha, which left 12-45 % alpha
 * puffs as stair-stepped pixel blobs).
 */
public final class MeteorParticleRenderTypes {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/aftermath");
   private static final ResourceLocation SOFT_SHADER = new ResourceLocation("viltrumitecore", "soft_particle");
   private static ShaderInstance softProgram;

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

   /**
    * Translucent sheet for the aftermath smoke styles. Falls back to the vanilla particle program when the soft
    * program failed to load or a shader pack owns the particle pass; the atlas filter is restored after the batch.
    */
   public static final ParticleRenderType SOFT_SMOKE = new ParticleRenderType() {
      private AbstractTexture atlas;

      @Override
      public void begin(BufferBuilder builder, TextureManager textures) {
         RenderSystem.depthMask(false);
         RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         ShaderInstance program = softProgram;
         if (program != null && !ThanosDissolve.shaderPackInUse()) {
            RenderSystem.setShader(() -> program);
            this.atlas = textures.getTexture(TextureAtlas.LOCATION_PARTICLES);
            this.atlas.setBlurMipmap(true, false);
         }

         builder.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
      }

      @Override
      public void end(Tesselator tesselator) {
         tesselator.end();
         if (this.atlas != null) {
            this.atlas.restoreLastBlurMipmap();
            this.atlas = null;
         }

         RenderSystem.depthMask(true);
      }

      @Override
      public String toString() {
         return "VILTRUMITE_AFTERMATH_SOFT_SMOKE";
      }
   };

   private MeteorParticleRenderTypes() {
   }

   /** Mod bus: the soft smoke program. */
   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Registrar {
      private Registrar() {
      }

      @SubscribeEvent
      public static void onRegisterShaders(RegisterShadersEvent event) {
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SOFT_SHADER, DefaultVertexFormat.PARTICLE), loaded -> softProgram = loaded);
         } catch (IOException e) {
            softProgram = null;
            LOGGER.error("Aftermath soft smoke shader failed to load; using the vanilla particle program", e);
         }
      }
   }
}

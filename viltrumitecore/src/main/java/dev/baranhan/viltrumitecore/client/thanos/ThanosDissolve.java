package dev.baranhan.viltrumitecore.client.thanos;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Driver of the snap's universe dissolve ({@code shaders/core/thanos_dissolve}): at
 * {@code AFTER_LEVEL} the finished level frame (colour and depth) is copied, and one full-screen
 * pass puts every pixel back into the world from that depth copy and redraws it - still standing,
 * coming apart in glowing cells behind the front that runs out of the gauntlet, or gone into the
 * void (depth 1 is written there, so the figures the scene draws afterwards hang in it). The world
 * itself is never touched. Nothing is allocated while no scene runs; a shader pack (Iris/Oculus)
 * owns the frame, so then the pass is skipped and the scene falls back to its screen grade.
 */
public final class ThanosDissolve {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/thanos");
   public static final ResourceLocation SHADER = new ResourceLocation("viltrumitecore", "thanos_dissolve");
   private static ShaderInstance program;
   private static TextureTarget copy;
   private static boolean packChecked;
   private static Method packInUse;
   private static Object packApi;
   private static boolean packUnknown;
   private static boolean warned;

   /** One frame of the dissolve (world positions absolute). */
   public record Params(Vec3 origin, Vec3 restoreOrigin, float front, float restore, float reach, float light, float freeze, float shockRadius,
      float shockStrength, float gold, float time) {
   }

   private ThanosDissolve() {
   }

   /** Can the pass run this frame (program loaded, no shader pack)? */
   public static boolean available() {
      return program != null && !shaderPackInUse();
   }

   /** Draws the pass over the main target; false when it could not (the caller falls back). */
   public static boolean render(RenderLevelStageEvent event, Params p) {
      ShaderInstance shader = program;
      if (shader == null || ShaderCompat.isShadowPass() || shaderPackInUse()) {
         if (shader != null && !warned && shaderPackInUse()) {
            warned = true;
            LOGGER.info("Thanos snap dissolve is not drawn while a shader pack is active");
         }

         return false;
      }

      Minecraft mc = Minecraft.getInstance();
      RenderTarget main = mc.getMainRenderTarget();
      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      copyFrame(main);
      // AFTER_LEVEL carries the projection (+bob) matrix; the view rotation is rebuilt as GameRenderer builds it
      Matrix4f viewProj = new Matrix4f(event.getProjectionMatrix()).mul(ScreenProjector.viewRotation(ScreenProjector.roll(), camera.getXRot(),
         camera.getYRot()));
      Matrix4f inverse = viewProj.invert(new Matrix4f());
      // grid anchor: an integer block near the origin keeps the cell grid exact in float precision
      double ax = Math.floor(p.origin().x);
      double ay = Math.floor(p.origin().y);
      double az = Math.floor(p.origin().z);
      shader.safeGetUniform("InvViewProj").set(inverse);
      shader.safeGetUniform("CamToGrid").set((float)(cam.x - ax), (float)(cam.y - ay), (float)(cam.z - az));
      shader.safeGetUniform("Origin").set((float)(p.origin().x - ax), (float)(p.origin().y - ay), (float)(p.origin().z - az));
      shader.safeGetUniform("RestoreOrigin").set((float)(p.restoreOrigin().x - ax), (float)(p.restoreOrigin().y - ay), (float)(p.restoreOrigin().z - az));
      shader.safeGetUniform("Front").set(p.front());
      shader.safeGetUniform("Restore").set(p.restore());
      shader.safeGetUniform("Reach").set(Math.max(8.0F, p.reach()));
      shader.safeGetUniform("Light").set(p.light());
      shader.safeGetUniform("Freeze").set(p.freeze());
      shader.safeGetUniform("Shock").set(p.shockRadius(), p.shockStrength());
      shader.safeGetUniform("Gold").set(p.gold());
      shader.safeGetUniform("Time").set(p.time());
      shader.safeGetUniform("HotColor").set(1.0F, 0.62F, 0.28F);
      shader.safeGetUniform("GoldColor").set(1.0F, 0.82F, 0.42F);
      shader.safeGetUniform("VoidColor").set(0.018F, 0.012F, 0.035F);
      main.bindWrite(false);
      RenderSystem.enableDepthTest();
      RenderSystem.depthFunc(GL11.GL_ALWAYS);
      RenderSystem.depthMask(true);
      RenderSystem.disableCull();
      RenderSystem.setShader(() -> shader);
      RenderSystem.setShaderTexture(0, copy.getColorTextureId());
      RenderSystem.setShaderTexture(1, copy.getDepthTextureId());
      try {
         BufferBuilder b = Tesselator.getInstance().getBuilder();
         b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
         b.vertex(-1.0, -1.0, 0.0).endVertex();
         b.vertex(1.0, -1.0, 0.0).endVertex();
         b.vertex(1.0, 1.0, 0.0).endVertex();
         b.vertex(-1.0, 1.0, 0.0).endVertex();
         BufferUploader.drawWithShader(b.end());
      } finally {
         RenderSystem.setShaderTexture(0, 0);
         RenderSystem.setShaderTexture(1, 0);
         RenderSystem.depthFunc(GL11.GL_LEQUAL);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
      }

      return true;
   }

   /** Colour and depth of the finished level, read by the pass (sampling the target being drawn is undefined). */
   private static void copyFrame(RenderTarget main) {
      if (copy == null) {
         copy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
      }

      if (main.isStencilEnabled() && !copy.isStencilEnabled()) {
         copy.enableStencil();
      }

      if (copy.width != main.width || copy.height != main.height) {
         copy.resize(main.width, main.height, Minecraft.ON_OSX);
      }

      GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
      GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
      GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
         GL11.GL_NEAREST);
      main.bindWrite(false);
   }

   /** Frees the frame copy (scene end, level unload, resource reload). Render thread only. */
   public static void release() {
      if (copy != null) {
         copy.destroyBuffers();
         copy = null;
      }
   }

   static boolean hasCopy() {
      return copy != null;
   }

   /** Shader-pack detection through the public Iris/Oculus API, by reflection (no compile dependency). */
   /** True while an Iris/Oculus shader pack replaces the vanilla programs (or when that cannot be told). */
   public static boolean shaderPackInUse() {
      if (!packChecked) {
         packChecked = true;
         ModList mods = ModList.get();
         if (mods != null && (mods.isLoaded("oculus") || mods.isLoaded("iris"))) {
            try {
               Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
               packApi = type.getMethod("getInstance").invoke(null);
               packInUse = type.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
               packUnknown = true;
            }
         }
      }

      if (packUnknown) {
         return true;
      }

      if (packInUse == null) {
         return false;
      }

      try {
         return (Boolean)packInUse.invoke(packApi);
      } catch (ReflectiveOperationException | RuntimeException e) {
         return true;
      }
   }

   /** Mod bus: the core shader and the reload cleanup. */
   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Registrar {
      private Registrar() {
      }

      @SubscribeEvent
      public static void onRegisterShaders(RegisterShadersEvent event) {
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION), loaded -> program = loaded);
         } catch (IOException e) {
            program = null;
            LOGGER.error("Thanos dissolve shader failed to load", e);
         }
      }

      @SubscribeEvent
      public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener((ResourceManagerReloadListener)manager -> release());
      }
   }
}

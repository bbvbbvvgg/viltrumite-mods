package dev.baranhan.viltrumitecore.client.render.erasure;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Draws The Hand erasure strokes with the original thehand_erasure core shader.
 *
 * Port of the draw path of Roundabout's TheHandErasureRenderer (Hydraheads, Roundabout License v1.2, see
 * CREDITS.md): one copy of the finished frame (colour with mipmaps for the refraction blur, depth to refuse the
 * foreground), then three passes - every shell (the space around the tube), every glass tube with depth writes,
 * every aura - and the world depth is put back. Roundabout's mixin callback at LevelRenderer.renderLevel TAIL is
 * replaced with Forge's {@code AFTER_LEVEL} stage, which Forge fires right after that method returns; the core
 * shader is registered with {@link RegisterShadersEvent} instead of a GameRenderer mixin.
 *
 * Strokes come from {@link Provider}s every frame, so an effect has no lifetime of its own here: when its owner
 * forgets it, it is gone. Nothing is copied or allocated on the GPU while no stroke is visible. */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ErasureRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/erasure");
   public static final ResourceLocation SHADER = new ResourceLocation("viltrumitecore", "thehand_erasure");
   private static final double MAX_DISTANCE = 320;
   private static final int MAX_STROKES = 96;
   private static final float MODE_SPACE = 0F;
   private static final float MODE_AURA = 1F;
   private static final float MODE_GLASS = 2F;
   private static final double[] RING_COS = new double[ErasureStroke.AROUND + 1];
   private static final double[] RING_SIN = new double[ErasureStroke.AROUND + 1];

   static {
      for (int k = 0; k <= ErasureStroke.AROUND; k++) {
         double angle = Mth.TWO_PI * k / ErasureStroke.AROUND;
         RING_COS[k] = Math.cos(angle);
         RING_SIN[k] = Math.sin(angle);
      }
   }

   /** Supplies the strokes to draw this frame, already updated to this frame's time. */
   public interface Provider {
      void collect(List<ErasureStroke> out, float partialTick, long gameTime);
   }

   private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
   private static final List<ErasureStroke> COLLECTED = new ArrayList<>();
   private static final List<ErasureStroke> VISIBLE = new ArrayList<>();
   private static ShaderInstance program;
   private static TextureTarget sceneCopy;
   private static boolean warnedShaderPack;

   private ErasureRenderer() {
   }

   public static void register(Provider provider) {
      if (!PROVIDERS.contains(provider)) PROVIDERS.add(provider);
   }

   /** Frees the frame copy. Render thread only. */
   public static void releaseTargets() {
      if (sceneCopy != null) {
         sceneCopy.destroyBuffers();
         sceneCopy = null;
      }
   }

   static boolean hasSceneCopy() {
      return sceneCopy != null;
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || PROVIDERS.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      COLLECTED.clear();
      float partialTick = event.getPartialTick();
      long gameTime = mc.level.getGameTime();
      for (Provider provider : PROVIDERS) provider.collect(COLLECTED, partialTick, gameTime);
      if (COLLECTED.isEmpty()) return;
      ShaderInstance shader = program;
      if (shader == null || dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass()) {
         COLLECTED.clear();
         return;
      }
      if (ErasureCompat.shaderPackInUse()) {
         // A shader pack draws the world into its own targets: the main colour and depth copied here would not be
         // the frame the player sees, and the glass would draw through walls. Draw nothing rather than garbage.
         if (!warnedShaderPack) {
            warnedShaderPack = true;
            LOGGER.info("The Hand erasure is not drawn while a shader pack is active");
         }
         COLLECTED.clear();
         return;
      }
      Vec3 camera = event.getCamera().getPosition();
      Frustum frustum = event.getFrustum();
      VISIBLE.clear();
      for (ErasureStroke stroke : COLLECTED) {
         if (VISIBLE.size() >= MAX_STROKES) break;
         if (!(stroke.tubeVisible() || stroke.auraVisible())) continue;
         AABB box = stroke.bounds();
         if (distance(box, camera) > MAX_DISTANCE || (frustum != null && !frustum.isVisible(box))) continue;
         VISIBLE.add(stroke);
      }
      COLLECTED.clear();
      if (VISIBLE.isEmpty()) return;
      RenderTarget main = mc.getMainRenderTarget();
      try {
         copyScene(main);
         draw(main, shader, camera);
      } finally {
         VISIBLE.clear();
      }
   }

   static double distance(AABB box, Vec3 p) {
      double dx = Math.max(Math.max(box.minX - p.x, 0), p.x - box.maxX);
      double dy = Math.max(Math.max(box.minY - p.y, 0), p.y - box.maxY);
      double dz = Math.max(Math.max(box.minZ - p.z, 0), p.z - box.maxZ);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** The glass shows the world behind it, so it samples a copy of the finished frame: reading the framebuffer that
    * is being drawn into is undefined in OpenGL. Mipmaps of the colour copy give the blurred world seen through the
    * glass. */
   private static void copyScene(RenderTarget main) {
      if (sceneCopy == null) {
         sceneCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
      }
      // A depth blit needs equal depth formats: follow the main target when a mod enabled its stencil.
      if (main.isStencilEnabled() && !sceneCopy.isStencilEnabled()) {
         sceneCopy.enableStencil();
      }
      if (sceneCopy.width != main.width || sceneCopy.height != main.height) {
         sceneCopy.resize(main.width, main.height, Minecraft.ON_OSX);
      }
      blit(main, sceneCopy, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      main.bindWrite(false);
      // Filters are set every frame: RenderTarget.resize() resets them to nearest.
      GlStateManager._bindTexture(sceneCopy.getColorTextureId());
      GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
      GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
      GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GlStateManager._bindTexture(0);
   }

   private static void blit(RenderTarget from, RenderTarget to, int buffers) {
      GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
      GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
      GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, buffers, GL11.GL_NEAREST);
   }

   private static void draw(RenderTarget main, ShaderInstance shader, Vec3 camera) {
      // After the level the model-view stack no longer holds the camera rotation: rebuild it from the inverse that
      // GameRenderer stored for this frame, as the original does.
      Matrix3f inverseView = RenderSystem.getInverseViewRotationMatrix();
      Vector3f cameraRight = inverseView.transform(new Vector3f(1, 0, 0));
      Vector3f cameraUp = inverseView.transform(new Vector3f(0, 1, 0));
      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      try {
         modelView.setIdentity();
         modelView.mulPoseMatrix(new Matrix4f(new Matrix3f(inverseView).invert()));
         RenderSystem.applyModelViewMatrix();
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.enableDepthTest();
         RenderSystem.depthFunc(GL11.GL_LEQUAL);
         RenderSystem.depthMask(false);
         // Both sides: the shell does not depend on the side, and the glass drops faces turned away itself.
         RenderSystem.disableCull();
         RenderSystem.setShader(() -> shader);
         RenderSystem.setShaderTexture(0, sceneCopy.getColorTextureId());
         RenderSystem.setShaderTexture(1, sceneCopy.getDepthTextureId());
         shader.safeGetUniform("Debug").set(0F);
         for (ErasureStroke stroke : VISIBLE) {
            if (!stroke.tubeVisible()) continue;
            setUniforms(shader, stroke, MODE_SPACE);
            drawTube(stroke.rings(true), camera, true);
         }
         RenderSystem.depthMask(true);
         for (ErasureStroke stroke : VISIBLE) {
            if (!stroke.tubeVisible()) continue;
            setUniforms(shader, stroke, MODE_GLASS);
            drawTube(stroke.rings(false), camera, false);
         }
         RenderSystem.depthMask(false);
         for (ErasureStroke stroke : VISIBLE) {
            if (!stroke.auraVisible()) continue;
            setUniforms(shader, stroke, MODE_AURA);
            drawAura(shader, stroke.auraCentre(), stroke.auraRadius(), stroke.auraDrift(), camera, cameraRight, cameraUp);
         }
         // Put the world depth back, so nothing after this sees the tubes.
         blit(sceneCopy, main, GL11.GL_DEPTH_BUFFER_BIT);
         main.bindWrite(false);
      } finally {
         RenderSystem.setShaderTexture(0, 0);
         RenderSystem.setShaderTexture(1, 0);
         RenderSystem.enableCull();
         RenderSystem.depthMask(true);
         RenderSystem.disableBlend();
         RenderSystem.defaultBlendFunc();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static void setUniforms(ShaderInstance shader, ErasureStroke stroke, float mode) {
      shader.safeGetUniform("Mode").set(mode);
      shader.safeGetUniform("Time").set(stroke.time);
      shader.safeGetUniform("Seed").set(stroke.seed);
      shader.safeGetUniform("Amount").set(stroke.aura);
      shader.safeGetUniform("Length").set(stroke.length);
      shader.safeGetUniform("HeadS").set(stroke.headS);
      shader.safeGetUniform("CapR").set(stroke.capR);
      shader.safeGetUniform("Decay").set(stroke.decay);
      shader.safeGetUniform("Flash").set(stroke.flash);
      shader.safeGetUniform("HaloStrength").set(stroke.halo);
      shader.safeGetUniform("Scale").set(stroke.scale);
      ErasureStyle style = stroke.style;
      for (int i = 0; i < 5; i++) {
         setColor(shader, "HeadRamp" + i, style.head().get(i));
         setColor(shader, "TailRamp" + i, style.tail().get(i));
      }
      setColor(shader, "EdgeColor", style.edge());
      setColor(shader, "DecayColor0", style.decayStart());
      setColor(shader, "DecayColor1", style.decayEnd());
   }

   private static void setColor(ShaderInstance shader, String name, int rgb) {
      shader.safeGetUniform(name).set(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
   }

   /** Quads between neighbouring rings.
    * Glass: UV = (path length, angle around / 2 pi), colour r = tube radius / (RADIUS_SCALE * scale), gba = path
    * tangent, normal = surface normal.
    * Shell: UV = (path length of the axis point, tube radius there), colour r = shell radius / (SHELL_SCALE * scale),
    * gba = path tangent (0 on the round ends), normal = direction from the axis point, unnormalised. */
   private static void drawTube(List<ErasureStroke.Ring> rings, Vec3 camera, boolean shell) {
      BufferBuilder builder = Tesselator.getInstance().getBuilder();
      builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
      float[] next = ringVertices(rings.get(0), camera, shell);
      for (int i = 0; i + 1 < rings.size(); i++) {
         float[] current = next;
         next = ringVertices(rings.get(i + 1), camera, shell);
         for (int k = 0; k < ErasureStroke.AROUND; k++) {
            ringVertex(builder, rings.get(i), current, k, shell);
            ringVertex(builder, rings.get(i), current, k + 1, shell);
            ringVertex(builder, rings.get(i + 1), next, k + 1, shell);
            ringVertex(builder, rings.get(i + 1), next, k, shell);
         }
      }
      BufferUploader.drawWithShader(builder.end());
   }

   private static float[] ringVertices(ErasureStroke.Ring ring, Vec3 camera, boolean shell) {
      float[] vertices = new float[(ErasureStroke.AROUND + 1) * 6];
      for (int k = 0; k <= ErasureStroke.AROUND; k++) {
         double rx = ring.n().x * RING_COS[k] + ring.b().x * RING_SIN[k];
         double ry = ring.n().y * RING_COS[k] + ring.b().y * RING_SIN[k];
         double rz = ring.n().z * RING_COS[k] + ring.b().z * RING_SIN[k];
         double nx = rx * ring.normalRadial() + ring.tangent().x * ring.normalAxial();
         double ny = ry * ring.normalRadial() + ring.tangent().y * ring.normalAxial();
         double nz = rz * ring.normalRadial() + ring.tangent().z * ring.normalAxial();
         if (!shell) {
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1.0E-4) {
               nx = 0;
               ny = 0;
               nz = 0;
            } else {
               nx /= length;
               ny /= length;
               nz /= length;
            }
         }
         int o = k * 6;
         vertices[o] = (float) (ring.centre().x + rx * ring.r() - camera.x);
         vertices[o + 1] = (float) (ring.centre().y + ry * ring.r() - camera.y);
         vertices[o + 2] = (float) (ring.centre().z + rz * ring.r() - camera.z);
         vertices[o + 3] = (float) nx;
         vertices[o + 4] = (float) ny;
         vertices[o + 5] = (float) nz;
      }
      return vertices;
   }

   private static void ringVertex(BufferBuilder builder, ErasureStroke.Ring ring, float[] vertices, int k, boolean shell) {
      int o = k * 6;
      Vec3 t = ring.passTangent() ? ring.tangent() : Vec3.ZERO;
      builder.vertex(vertices[o], vertices[o + 1], vertices[o + 2])
         .uv(ring.s(), shell ? ring.tubeRadius() : k / (float) ErasureStroke.AROUND)
         .color(Mth.clamp(ring.colourRadius(), 0F, 1F), (float) (0.5 + 0.5 * t.x), (float) (0.5 + 0.5 * t.y), (float) (0.5 + 0.5 * t.z))
         .normal(vertices[o + 3], vertices[o + 4], vertices[o + 5])
         .endVertex();
   }

   /** Palm aura: a boiling flame ball traced in 3D by the shader inside a camera-facing square at the front of its
    * bounding sphere. */
   private static void drawAura(ShaderInstance shader, Vec3 centre, float radius, Vec3 drift, Vec3 camera,
                                Vector3f right, Vector3f up) {
      Vec3 c = centre.subtract(camera);
      double distance = c.length();
      float bound = radius * ErasureStroke.AURA_BOUND;
      if (distance < bound + 0.1) return;
      shader.safeGetUniform("AuraCentre").set((float) c.x, (float) c.y, (float) c.z);
      shader.safeGetUniform("AuraRadius").set(radius);
      shader.safeGetUniform("AuraDrift").set((float) drift.x, (float) drift.y, (float) drift.z);
      Vec3 front = c.scale(1 - bound / distance);
      float half = bound * 1.15F;
      BufferBuilder builder = Tesselator.getInstance().getBuilder();
      builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
      billboardVertex(builder, front, right, up, half, -1F, -1F);
      billboardVertex(builder, front, right, up, half, 1F, -1F);
      billboardVertex(builder, front, right, up, half, 1F, 1F);
      billboardVertex(builder, front, right, up, half, -1F, 1F);
      BufferUploader.drawWithShader(builder.end());
   }

   private static void billboardVertex(BufferBuilder builder, Vec3 c, Vector3f right, Vector3f up, float radius, float x, float y) {
      builder.vertex(c.x + (right.x() * x + up.x() * y) * radius,
            c.y + (right.y() * x + up.y() * y) * radius,
            c.z + (right.z() * x + up.z() * y) * radius)
         .uv(x, y).color(1F, 1F, 1F, 1F).normal(0F, 1F, 0F).endVertex();
   }

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) releaseTargets();
   }

   /** Mod bus: core shader registration and reload cleanup. */
   @EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = Dist.CLIENT)
   public static final class Registrar {
      private Registrar() {
      }

      @SubscribeEvent
      public static void onRegisterShaders(RegisterShadersEvent event) {
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
               loaded -> program = loaded);
         } catch (IOException e) {
            // Forge closes the old program on reload; without a new one the effect is simply not drawn.
            program = null;
            LOGGER.error("The Hand erasure shader failed to load", e);
         }
      }

      @SubscribeEvent
      public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener((ResourceManagerReloadListener) manager -> releaseTargets());
      }
   }
}

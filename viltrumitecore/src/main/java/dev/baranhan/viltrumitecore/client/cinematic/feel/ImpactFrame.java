package dev.baranhan.viltrumitecore.client.cinematic.feel;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.viltrumitecore.client.cinematic.CinematicSettings;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import dev.baranhan.viltrumitecore.combat.feel.FeelMath;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;

/**
 * Manga impact frame (wave1-22): one inverted frame (about 50 ms), then tapered speed lines toward
 * the point of impact that fade out by 175 ms. Real-time based, so hit-stop and lag never stretch
 * it. Rate-limited to 2.5 per second; reduced motion or a weak flash setting skip the inversion,
 * disabled impact frames fall back to a soft white flash. Drawn under the HUD.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class ImpactFrame {
   private static final int LINES = 40;
   private static long startMs = -1L;
   private static long lastMs = -1L;
   private static float strength;
   private static boolean invert;
   @Nullable
   private static Vec3 focus;
   private static int seed;

   private ImpactFrame() {
   }

   /** Starts an impact frame toward {@code at} (null = screen centre). False if suppressed. */
   public static boolean trigger(@Nullable Vec3 at, float power) {
      CinematicSettings settings = CinematicSettings.get();
      float flash = settings.flash();
      float s = Math.max(0.3F, FeelMath.clamp01(power));
      if (flash <= 0.0F) {
         return false;
      }
      long now = Util.getMillis();
      if (!FeelMath.impactAllowed(now, lastMs)) {
         return false;
      }
      lastMs = now;
      if (!settings.impactFrames) {
         FlashOverlay.flash(0.22F * flash * s, 4);
         return true;
      }
      startMs = now;
      strength = s;
      focus = at;
      invert = !settings.reduceCameraMotion && flash >= 0.5F && s >= 0.5F;
      seed = (seed + 1) & 1023;
      return true;
   }

   public static boolean active() {
      return startMs >= 0L;
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      startMs = -1L;
      focus = null;
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Pre event) {
      if (startMs < 0L) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         startMs = -1L;
         return;
      }
      long elapsed = Util.getMillis() - startMs;
      int phase = FeelMath.impactPhase(elapsed, invert);
      if (phase == FeelMath.PHASE_NONE) {
         startMs = -1L;
         focus = null;
         return;
      }
      GuiGraphics graphics = event.getGuiGraphics();
      graphics.flush();
      int w = graphics.guiWidth();
      int h = graphics.guiHeight();
      float cx = w * 0.5F;
      float cy = h * 0.5F;
      if (focus != null) {
         ScreenProjector.Point p = ScreenProjector.project(focus, w, h);
         if (p != null && p.onScreen(w, h)) {
            cx = p.x();
            cy = p.y();
         }
      }
      float flash = CinematicSettings.get().flash() * strength;
      Matrix4f pose = graphics.pose().last().pose();
      RenderSystem.enableBlend();
      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      if (phase == FeelMath.PHASE_INVERT) {
         // white x (1 - dst): the frame becomes its own negative
         RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
         quad(pose, w, h, 1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.defaultBlendFunc();
         lines(pose, cx, cy, w, h, 1.0F, 1.0F, 1.0F, 0.6F * flash);
      } else {
         RenderSystem.defaultBlendFunc();
         float a = FeelMath.impactAlpha(elapsed) * flash;
         quad(pose, w, h, 1.0F, 1.0F, 1.0F, 0.08F * a);
         lines(pose, cx, cy, w, h, 0.02F, 0.02F, 0.03F, 0.8F * a);
      }
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   private static void quad(Matrix4f pose, int w, int h, float r, float g, float b, float a) {
      if (a <= 0.003F) {
         return;
      }
      BufferBuilder buf = Tesselator.getInstance().getBuilder();
      buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      buf.vertex(pose, 0.0F, 0.0F, 0.0F).color(r, g, b, a).endVertex();
      buf.vertex(pose, 0.0F, (float)h, 0.0F).color(r, g, b, a).endVertex();
      buf.vertex(pose, (float)w, (float)h, 0.0F).color(r, g, b, a).endVertex();
      buf.vertex(pose, (float)w, 0.0F, 0.0F).color(r, g, b, a).endVertex();
      BufferUploader.drawWithShader(buf.end());
   }

   /** Tapered wedges from an inner ring to beyond the screen corners; transparent tips. */
   private static void lines(Matrix4f pose, float cx, float cy, int w, int h, float r, float g, float b, float a) {
      if (a <= 0.003F) {
         return;
      }
      float reach = (float)Math.sqrt((double)w * w + (double)h * h) * 1.2F;
      float inner = Math.min(w, h) * 0.2F;
      BufferBuilder buf = Tesselator.getInstance().getBuilder();
      buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      for (int i = 0; i < LINES; i++) {
         float angle = (i + hash(i, 1) * 0.8F) * Mth.TWO_PI / LINES;
         float half = 0.004F + hash(i, 2) * 0.014F;
         float start = inner * (0.75F + hash(i, 3) * 0.9F);
         buf.vertex(pose, cx + Mth.cos(angle) * start, cy + Mth.sin(angle) * start, 0.0F).color(r, g, b, 0.0F).endVertex();
         buf.vertex(pose, cx + Mth.cos(angle - half) * reach, cy + Mth.sin(angle - half) * reach, 0.0F).color(r, g, b, a).endVertex();
         buf.vertex(pose, cx + Mth.cos(angle + half) * reach, cy + Mth.sin(angle + half) * reach, 0.0F).color(r, g, b, a).endVertex();
      }
      BufferUploader.drawWithShader(buf.end());
   }

   private static float hash(int i, int salt) {
      float v = Mth.sin(i * 12.9898F + salt * 78.233F + seed * 0.37F) * 43758.547F;
      return v - Mth.floor(v);
   }
}

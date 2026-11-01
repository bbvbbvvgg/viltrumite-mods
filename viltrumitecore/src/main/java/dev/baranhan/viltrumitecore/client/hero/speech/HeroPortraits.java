package dev.baranhan.viltrumitecore.client.hero.speech;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Subtitle portraits per hero: a hero whose body is its own model registers a
 * {@link Portrait} (client init); every other hero gets the 3D bust of the
 * speaker's real skin ({@link PlayerBustPortrait}). Also holds the shared bust
 * camera (three-quarter view from slightly above) and lighting.
 */
public final class HeroPortraits {
   /** Face turned toward the text panel (viewer's left), degrees. */
   public static final float YAW = -32.0F;
   /** Camera slightly above: the top of the head leans toward the viewer, degrees. */
   public static final float PITCH = 15.0F;
   /** GUI depth of the bust centre: above the panel (z 0), well inside the GUI depth range. */
   public static final float DEPTH = 150.0F;
   /** Key light from above-front-left (GUI space: y down, +z toward the viewer), fill from the front. */
   private static final Vector3f KEY_LIGHT = new Vector3f(-0.3F, -1.0F, 0.6F).normalize();
   private static final Vector3f FILL_LIGHT = new Vector3f(-0.2F, -0.2F, 1.0F).normalize();
   private static final Map<HeroId, Portrait> PORTRAITS = new EnumMap<>(HeroId.class);
   private static final Map<String, Portrait> VOICES = new java.util.HashMap<>();
   /** GUI box (x0, y0, x1, y1) the caller has scissored to (GuiGraphics#enableScissor), or null. */
   @Nullable
   private static int[] outerClip;

   private HeroPortraits() {
   }

   /** What a portrait draws into: the inner box (inside the frame), opacity and the speaker. */
   public record Frame(float x, float y, float width, float height, float alpha, double seconds, UUID speaker, @Nullable AbstractClientPlayer player) {
   }

   @FunctionalInterface
   public interface Portrait {
      void render(GuiGraphics graphics, Frame frame);
   }

   /** Draws the model with buffers from {@code buffers}, every vertex at opacity {@code alpha}. */
   @FunctionalInterface
   public interface ModelPass {
      void draw(MultiBufferSource buffers, float alpha);
   }

   /**
    * Portrait of a non-hero voice (a world event speaker such as the Chitauri hive), keyed by the line's voice key.
    * Unknown voices fall back to the default bust.
    */
   public static synchronized void registerVoice(String voice, Portrait portrait) {
      VOICES.put(Objects.requireNonNull(voice, "voice"), Objects.requireNonNull(portrait, "portrait"));
   }

   public static synchronized Portrait resolveVoice(@Nullable String voice) {
      Portrait portrait = voice == null ? null : VOICES.get(voice);
      return portrait == null ? PlayerBustPortrait.INSTANCE : portrait;
   }

   public static synchronized void register(HeroId hero, Portrait portrait) {
      PORTRAITS.put(Objects.requireNonNull(hero, "hero"), Objects.requireNonNull(portrait, "portrait"));
   }

   /** The portrait a hero registered, or null when it uses the default bust. */
   @Nullable
   public static synchronized Portrait registered(@Nullable HeroId hero) {
      return hero == null ? null : PORTRAITS.get(hero);
   }

   /**
    * Runs {@code draw} with every portrait also clipped to the GUI box (x0, y0)-(x1, y1), which the caller
    * has already scissored to with GuiGraphics#enableScissor (a scrolled list): the portrait's own scissor is
    * intersected with it and the box is scissored again afterwards instead of switching the scissor off.
    */
   public static void withOuterClip(int x0, int y0, int x1, int y1, Runnable draw) {
      int[] previous = outerClip;
      outerClip = new int[]{x0, y0, x1, y1};
      try {
         draw.run();
      } finally {
         outerClip = previous;
      }
   }

   public static synchronized Portrait resolve(@Nullable HeroId hero) {
      Portrait portrait = hero == null ? null : PORTRAITS.get(hero);
      return portrait == null ? PlayerBustPortrait.INSTANCE : portrait;
   }

   /**
    * Sets the bust camera on {@code pose}: origin at ({@code cx}, {@code cy}),
    * {@code pixel} GUI px per model px, a right-handed y-up view frame turned to
    * a three-quarter view from slightly above. The caller then maps its model
    * so that it faces +z with y up.
    */
   public static void camera(PoseStack pose, float cx, float cy, float pixel) {
      float k = pixel * 16.0F;
      pose.translate(cx, cy, DEPTH);
      // GUI y points down: one reflection gives a right-handed view frame (like the inventory entity view).
      pose.scale(k, -k, k);
      pose.mulPose(Axis.XP.rotationDegrees(PITCH));
      pose.mulPose(Axis.YP.rotationDegrees(YAW));
   }

   /**
    * Renders a model clipped to the frame box with portrait lighting. While
    * fading, a depth-only pass first keeps hidden faces and the inner skin
    * layer from showing through the translucent outer layer.
    */
   public static void renderClipped(GuiGraphics graphics, Frame frame, ModelPass pass) {
      Minecraft mc = Minecraft.getInstance();
      double scale = mc.getWindow().getGuiScale();
      int x0 = (int)Math.round(frame.x() * scale);
      int y0 = (int)Math.round(frame.y() * scale);
      int x1 = x0 + (int)Math.round(frame.width() * scale);
      int y1 = (int)Math.round((frame.y() + frame.height()) * scale);
      int[] outer = outerClip;
      if (outer != null) {
         x0 = Math.max(x0, (int)(outer[0] * scale));
         y0 = Math.max(y0, (int)(outer[1] * scale));
         x1 = Math.min(x1, (int)(outer[2] * scale));
         y1 = Math.min(y1, (int)(outer[3] * scale));
      }
      int w = x1 - x0;
      int h = y1 - y0;
      if (w <= 0 || h <= 0 || frame.alpha() <= 0.004F) {
         return;
      }

      graphics.flush();
      RenderSystem.enableScissor(x0, mc.getWindow().getHeight() - y1, w, h);
      RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
      RenderSystem.enableDepthTest();
      RenderSystem.setShaderLights(KEY_LIGHT, FILL_LIGHT);
      MultiBufferSource.BufferSource buffers = graphics.bufferSource();
      try {
         if (frame.alpha() < 0.995F) {
            RenderSystem.colorMask(false, false, false, false);
            pass.draw(buffers, 1.0F);
            graphics.flush();
            RenderSystem.colorMask(true, true, true, true);
            pass.draw(buffers, frame.alpha());
         } else {
            pass.draw(buffers, 1.0F);
         }

         graphics.flush();
      } finally {
         RenderSystem.colorMask(true, true, true, true);
         // The bust must not hide later HUD layers (chat) behind its depth.
         RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
         if (outer != null) {
            // same box GuiGraphics#enableScissor set for the caller
            int ox = (int)(outer[0] * scale);
            int oy = (int)(outer[1] * scale);
            int ow = Math.max(0, (int)(outer[2] * scale) - ox);
            int oh = Math.max(0, (int)(outer[3] * scale) - oy);
            RenderSystem.enableScissor(ox, mc.getWindow().getHeight() - oy - oh, ow, oh);
         } else {
            RenderSystem.disableScissor();
         }
         Lighting.setupFor3DItems();
      }
   }

   /** Gentle idle motion: a slow head turn and nod, radians. */
   public static float swayYaw(double seconds) {
      return (float)Math.toRadians(2.5 * Math.sin(seconds * 2.0 * Math.PI / 7.0));
   }

   public static float swayPitch(double seconds) {
      return (float)Math.toRadians(1.2 * Math.sin(seconds * 2.0 * Math.PI / 5.3 + 0.8));
   }

   /** Tests only. */
   static synchronized void clear() {
      PORTRAITS.clear();
   }
}

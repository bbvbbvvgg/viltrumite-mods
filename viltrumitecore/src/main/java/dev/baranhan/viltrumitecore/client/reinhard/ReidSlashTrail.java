package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeArc;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * REINHARD OVERHAUL: a short additive light trail behind every ordinary strike. It is drawn
 * along the same view-relative {@link MeleeArc} path the server hit test sweeps, on the same
 * predictive action clock as the body clip, so the visible arc is the hit area. Reid leaves a
 * warm blade-edge crescent (the finisher a longer, brighter one); fists only a faint air smear.
 * Depth-tested (never through walls), no state: it exists only while the synced action runs.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReidSlashTrail {
   /**
    * @param inner      trail start, blocks from the eye (hilt side, fades to zero)
    * @param outer      trail end, blocks from the eye (the blade tip / fist)
    * @param edge       thickness of the bright edge line at the tip, blocks (0 = none)
    * @param trailTicks how much of the last motion stays visible, ticks
    */
   public record Style(double inner, double outer, double edge, double trailTicks, int r, int g, int b, int alpha) {
   }

   /** Reid's drawn blade reaches about two blocks from the eye: hand + 1.39-block blade. */
   public static final Style REID = new Style(0.9, 2.05, 0.10, 2.5, 255, 226, 150, 120);
   public static final Style REID_HEAVY = new Style(0.9, 2.35, 0.14, 3.2, 255, 236, 170, 165);
   public static final Style FIST = new Style(0.35, 0.95, 0.0, 1.6, 235, 240, 255, 42);
   /** Ticks after the contact window in which the remaining trail dies out. */
   public static final double FADE_TICKS = 4.0;
   static final int SEGMENTS = 14;

   public record Trail(MeleeArc.Swing swing, Style style) {
   }

   private ReidSlashTrail() {
   }

   /** Pure: the trail of a synced action (ordinal, phase = move index), or null for any other action. */
   @Nullable
   public static Trail trail(int actionId, int phase) {
      if (actionId == ReinhardAbility.REID_STRIKE.ordinal()) {
         MeleeMoves.Move move = MeleeMoves.reid(phase);
         return new Trail(move.swing(), move.heavy() ? REID_HEAVY : REID);
      }
      if (actionId == ReinhardAbility.LIGHT_PUNCH.ordinal()) {
         return new Trail(MeleeMoves.fist(phase).swing(), FIST);
      }
      if (actionId == ReinhardAbility.FIST_COMBO.ordinal()) {
         return new Trail(MeleeMoves.KNIGHT[Math.floorMod(phase, MeleeMoves.KNIGHT.length)], FIST);
      }
      return null;
   }

   /**
    * Pure: {head, tail, alpha} of the visible part of the swing at action time {@code ticks}
    * (sweep progress 0..1 and a 0..1 fade), or null when nothing shows. The head follows the
    * server sweep; the tail lags {@code trailTicks}; after the contact window the rest fades.
    */
   @Nullable
   public static double[] window(MeleeArc.Swing swing, double ticks, double trailTicks) {
      double head = swing.progress(ticks);
      double tail = swing.progress(ticks - trailTicks);
      double after = ticks - (swing.contactEnd() + 1);
      double alpha = after <= 0 ? 1 : Math.max(0, 1 - after / FADE_TICKS);
      if (head <= 0 || head - tail < 1.0E-3 || alpha <= 0) {
         return null;
      }
      return new double[]{head, tail, alpha};
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES) {
         return;
      }
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }
      float partial = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      BufferBuilder buffer = null;
      Tesselator tesselator = Tesselator.getInstance();
      PoseStack modelView = RenderSystem.getModelViewStack();
      try {
         for (Player player : level.players()) {
            HeroPublicSnapshot snapshot = ReinhardView.of(player);
            if (snapshot == null || player.isInvisible() || !(player instanceof AbstractClientPlayer clientPlayer)) {
               continue;
            }
            Trail trail = trail(snapshot.actionId(), snapshot.variant());
            if (trail == null) {
               continue;
            }
            double[] window = window(trail.swing(), ReinhardPlayback.actionTicks(clientPlayer, snapshot, partial), trail.style().trailTicks());
            if (window == null) {
               continue;
            }
            if (buffer == null) {
               modelView.pushPose();
               modelView.setIdentity();
               PixelVfx.rotateCamera(modelView, camera.getXRot(), camera.getYRot());
               RenderSystem.applyModelViewMatrix();
               RenderSystem.enableBlend();
               RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
               RenderSystem.disableCull();
               RenderSystem.enableDepthTest();
               RenderSystem.depthMask(false);
               RenderSystem.setShader(GameRenderer::getPositionColorShader);
               buffer = tesselator.getBuilder();
               buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            Vec3 eye = player.getEyePosition(partial).subtract(cameraPos);
            draw(buffer, eye, MeleeArc.basis(player.getViewYRot(partial), player.getViewXRot(partial)), trail, window);
         }
         if (buffer != null) {
            tesselator.end();
         }
      } finally {
         if (buffer != null) {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
         }
      }
   }

   /** A crescent between {@code inner} and {@code outer}: hilt side transparent, newest motion brightest, a thin hot edge at the tip. */
   private static void draw(BufferBuilder buffer, Vec3 eye, double[][] basis, Trail trail, double[] window) {
      Style s = trail.style();
      double head = window[0], tail = window[1];
      double[] prev = null;
      int prevAlpha = 0;
      for (int i = 0; i <= SEGMENTS; i++) {
         double k = i / (double)SEGMENTS;
         double[] angles = trail.swing().at(tail + (head - tail) * k);
         double[] d = MeleeArc.direction(basis, angles[0], angles[1]);
         int alpha = (int)Math.round(s.alpha() * window[2] * Math.pow(k, 1.6));
         if (prev != null) {
            // Glow body: transparent toward the hilt, full at the tip.
            quad(buffer, eye, prev, d, s.inner() + (s.outer() - s.inner()) * 0.35 * (1 - k), s.outer(), 0, prevAlpha, alpha, s.r(), s.g(), s.b());
            if (s.edge() > 0) {
               quad(buffer, eye, prev, d, s.outer() - s.edge(), s.outer() + s.edge() * 0.25, 255, Math.min(255, prevAlpha * 2), Math.min(255, alpha * 2),
                  255, 250, 235);
            }
         }
         prev = d;
         prevAlpha = alpha;
      }
   }

   /** One ribbon cell from direction a to b between two radii; inner edge alpha {@code innerAlpha}, outer edge fading a→b. */
   private static void quad(BufferBuilder buffer, Vec3 eye, double[] a, double[] b, double inner, double outer, int innerAlpha, int alphaA, int alphaB,
      int r, int g, int bl) {
      int innerA = Math.min(innerAlpha, alphaA), innerB = Math.min(innerAlpha, alphaB);
      vertex(buffer, eye, a, inner, r, g, bl, innerA);
      vertex(buffer, eye, a, outer, r, g, bl, alphaA);
      vertex(buffer, eye, b, outer, r, g, bl, alphaB);
      vertex(buffer, eye, b, inner, r, g, bl, innerB);
   }

   private static void vertex(BufferBuilder buffer, Vec3 eye, double[] d, double radius, int r, int g, int b, int alpha) {
      buffer.vertex(eye.x + d[0] * radius, eye.y + d[1] * radius, eye.z + d[2] * radius).color(r, g, b, alpha).endVertex();
   }
}

package dev.baranhan.viltrumitecore.client.thanos.war;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Ebony Maw's and Cull Obsidian's boss bars share one row (half width each, Maw left, Cull right), so three
 * bars cost two rows: the snap timer and its warning strip below them stay clear of the hero subtitle panel.
 * Pairing follows what the previous frame showed (Cull's bar right after Maw's); a general alone keeps the
 * vanilla full-width bar. Only the drawing changes, the server's bars stay plain boss events.
 */
public final class BlackOrderBars {
   private static final ResourceLocation BARS = new ResourceLocation("textures/gui/bars.png");
   static final String MAW = "entity.viltrumitecore.ebony_maw";
   static final String CULL = "entity.viltrumitecore.cull_obsidian";
   /** Width of one half bar (vanilla 182 = 2 x 89 + a 4 px gap). */
   static final int HALF = 89;
   private static boolean pairedLastFrame;
   private static boolean pairedThisFrame;
   private static boolean mawCompact;
   /** Kind of the bar handled just before (0 Maw, 1 Cull, -1 another or none) in this frame. */
   private static int previous = -1;

   private BlackOrderBars() {
   }

   /** 0 for Ebony Maw's bar, 1 for Cull Obsidian's, -1 for any other. */
   static int side(Component name) {
      if (name.getContents() instanceof TranslatableContents t) {
         return MAW.equals(t.getKey()) ? 0 : CULL.equals(t.getKey()) ? 1 : -1;
      }

      return -1;
   }

   /** Boss-bar hook (before the vanilla drawing): draws the paired bars itself. */
   public static void onBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
      LerpingBossEvent bar = event.getBossEvent();
      int side = side(bar.getName());
      int before = previous;
      previous = side;
      if (side == 0) {
         mawCompact = pairedLastFrame;
         if (mawCompact) {
            event.setCanceled(true);
            draw(event.getGuiGraphics(), event.getX(), event.getY(), bar);
            // Cull's bar follows on the same row
            event.setIncrement(0);
         }
      } else if (side == 1) {
         if (before == 0) {
            pairedThisFrame = true;
            if (mawCompact) {
               event.setCanceled(true);
               draw(event.getGuiGraphics(), event.getX() + 182 - HALF, event.getY(), bar);
            }
         }
      }
   }

   /** End of the HUD frame (after the boss bars): the pairing seen becomes the next frame's layout. */
   public static void endFrame() {
      pairedLastFrame = pairedThisFrame;
      pairedThisFrame = false;
      mawCompact = false;
      previous = -1;
   }

   /** No bars at all (no boss overlay this frame, logout): back to vanilla rows. */
   public static void reset() {
      pairedLastFrame = false;
      endFrame();
   }

   private static void draw(GuiGraphics g, int x, int y, LerpingBossEvent bar) {
      int v = bar.getColor().ordinal() * 10;
      piece(g, x, y, v, HALF);
      int fill = Math.min(HALF, Math.round(bar.getProgress() * (HALF + 1)));
      if (fill > 0) {
         piece(g, x, y, v + 5, fill);
      }

      if (bar.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
         int o = 80 + (bar.getOverlay().ordinal() - 1) * 10;
         com.mojang.blaze3d.systems.RenderSystem.enableBlend();
         piece(g, x, y, o, HALF);
         if (fill > 0) {
            piece(g, x, y, o + 5, fill);
         }

         com.mojang.blaze3d.systems.RenderSystem.disableBlend();
      }

      Font font = Minecraft.getInstance().font;
      Component name = bar.getName();
      int w = font.width(name);
      float s = w > HALF + 6 ? Math.max(0.6F, (HALF + 6) / (float)w) : 1.0F;
      g.pose().pushPose();
      g.pose().translate(x + HALF / 2.0F, y - 9.0F + (1.0F - s) * 8.0F, 0.0F);
      g.pose().scale(s, s, 1.0F);
      g.drawString(font, name, -w / 2, 0, 0xFFFFFF);
      g.pose().popPose();
   }

   /** A bar sprite cut to {@code w} px; a full half keeps the sprite's 2 px end cap. */
   private static void piece(GuiGraphics g, int x, int y, int v, int w) {
      if (w >= HALF) {
         g.blit(BARS, x, y, 0, v, HALF - 2, 5);
         g.blit(BARS, x + HALF - 2, y, 180, v, 2, 5);
      } else {
         g.blit(BARS, x, y, 0, v, w, 5);
      }
   }
}

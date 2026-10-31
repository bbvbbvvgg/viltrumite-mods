package dev.baranhan.viltrumitecore.client.budget;

import dev.baranhan.viltrumitecore.hero.budget.BudgetScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Client side of the expansion ultimate scenes: a camera sway, a FOV pulse,
 * a screen flash and one line of text. Only the render camera is touched,
 * never the player's rotation; the offset is sin(pi * t), zero at both ends,
 * and everything is dropped on log out, so the camera always comes back.
 */
public final class BudgetSceneClient {
   /** Extra ticks after the wind-up for the flash to fade. */
   static final int TAIL = 10;
   private static BudgetScene scene = BudgetScene.NONE;
   private static int total;
   private static int elapsed;

   private BudgetSceneClient() {
   }

   public static void receive(int entityId, int sceneId, int ticks) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null || minecraft.player.getId() != entityId) {
         return;
      }

      BudgetScene next = BudgetScene.byId(sceneId);
      if (next == BudgetScene.NONE || ticks <= 0) {
         reset();
         return;
      }

      scene = next;
      total = ticks + TAIL;
      elapsed = 0;
      minecraft.player.displayClientMessage(Component.translatable(next.lineKey()), true);
   }

   public static void reset() {
      scene = BudgetScene.NONE;
      total = 0;
      elapsed = 0;
   }

   public static void tick() {
      if (scene == BudgetScene.NONE) {
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null || ++elapsed >= total) {
         reset();
      }
   }

   public static boolean active() {
      return scene != BudgetScene.NONE && total > 0;
   }

   /** 0..1..0 envelope over the whole scene. */
   static float envelope(float partialTick) {
      if (!active()) {
         return 0.0F;
      }

      float t = Mth.clamp((elapsed + partialTick) / total, 0.0F, 1.0F);
      return Mth.sin((float)Math.PI * t);
   }

   public static float yawOffset(float partialTick) {
      return active() ? scene.cameraYaw * envelope(partialTick) : 0.0F;
   }

   public static float rollOffset(float partialTick) {
      return active() ? scene.cameraYaw * 0.15F * envelope(partialTick) : 0.0F;
   }

   public static double fovFactor(float partialTick) {
      return 1.0 - 0.08 * envelope(partialTick);
   }

   /** Flash peaks when the ultimate fires (end of the wind-up) and fades over the tail. */
   public static void renderFlash(GuiGraphics graphics, float partialTick) {
      if (!active()) {
         return;
      }

      float sinceFire = elapsed + partialTick - (total - TAIL);
      float alpha = sinceFire < -4.0F ? 0.0F : sinceFire < 0.0F ? (sinceFire + 4.0F) / 4.0F : 1.0F - sinceFire / TAIL;
      alpha = Mth.clamp(alpha, 0.0F, 1.0F) * 0.45F;
      if (alpha <= 0.01F) {
         return;
      }

      int color = ((int)(alpha * 255.0F) << 24) | (scene.flashColor & 0xFFFFFF);
      graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
   }
}

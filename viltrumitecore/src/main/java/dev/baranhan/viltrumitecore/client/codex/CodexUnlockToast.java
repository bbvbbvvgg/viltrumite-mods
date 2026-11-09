package dev.baranhan.viltrumitecore.client.codex;

import dev.baranhan.viltrumitecore.codex.data.CodexEntryDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * "New in the Codex" toast: accent bar in the entry's colour, the category glyph, a gold header and the
 * entry title, a thin timer line. A page-turn plus a soft amethyst chime when it appears.
 */
public class CodexUnlockToast implements Toast {
   static final int WIDTH = 176;
   static final int HEIGHT = 32;
   static final long SHOW_MS = 5000L;
   private final CodexEntryDef entry;
   private boolean played;

   public CodexUnlockToast(CodexEntryDef entry) {
      this.entry = entry;
   }

   @Override
   public int width() {
      return WIDTH;
   }

   @Override
   public int height() {
      return HEIGHT;
   }

   @Override
   public Toast.Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
      Minecraft mc = toasts.getMinecraft();
      if (!this.played && time > 0L) {
         this.played = true;
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F));
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.25F, 0.9F));
      }
      int accent = 0xFF000000 | (this.entry.accent() & 0xFFFFFF);
      g.fill(0, 0, WIDTH, HEIGHT, 0xEC0C0E13);
      g.fill(0, 0, WIDTH, 1, 0x40FFFFFF);
      g.fill(0, HEIGHT - 1, WIDTH, HEIGHT, 0x40FFFFFF);
      g.fill(WIDTH - 1, 0, WIDTH, HEIGHT, 0x40FFFFFF);
      g.fill(0, 0, 3, HEIGHT, accent);
      Font font = mc.font;
      g.pose().pushPose();
      g.pose().translate(10.0F, 9.0F, 0.0F);
      g.pose().scale(1.5F, 1.5F, 1.0F);
      g.drawString(font, this.entry.category().glyph, 0, 0, accent, true);
      g.pose().popPose();
      g.drawString(font, Component.translatable("codex2.viltrumitecore.toast.title"), 30, 7, 0xFFFFD34D, false);
      String title = Component.translatable(this.entry.titleKey()).getString();
      g.drawString(font, font.plainSubstrByWidth(title, WIDTH - 36), 30, 19, 0xFFFFFFFF, false);
      double limit = SHOW_MS * toasts.getNotificationDisplayTimeMultiplier();
      double left = Math.max(0.0, 1.0 - time / Math.max(1.0, limit));
      g.fill(3, HEIGHT - 2, 3 + (int)((WIDTH - 4) * left), HEIGHT - 1, (accent & 0xFFFFFF) | 0x90000000);
      return time >= limit ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
   }
}

package dev.baranhan.viltrumitecore.client.ironman.veronica;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.ironman.hulkbuster.HulkbusterPreview;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkTextures;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.VeronicaView;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.VeronicaChooseC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelLayers;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Veronica suit menu (spec §12.3): a holographic grid of the seven marks and
 * the Hulkbuster card (Stage 5). Each card has a turning 3D preview in the mark
 * skin, its durability, cooldown and location. Only choosable cards send the
 * choice. ESC closes.
 */
public final class VeronicaScreen extends Screen {
   private static final int CARD_W = 84;
   private static final int CARD_H = 96;
   private static final int GAP = 6;
   private static final int COLS = 4;
   private static final int ROWS = 2;
   private static final int GLASS = 0xB00A1A2C;
   private static final int EDGE = 0x9060D8FF;
   private static final int CELL = 0x7018405A;
   private static final int LOCKED = 0x8A10161E;
   private static final int ACCENT = 0xFF9FE8FF;
   private static final int DIM = 0xFF5A7A8A;
   private static final int AMBER = 0xFFFFB020;
   private static final int RED = 0xFFFF5040;
   private static final MarkId[] MARKS = MarkId.values();
   private final VeronicaView view;
   private PlayerModel<AbstractClientPlayer> preview;
   private int left;
   private int top;

   public VeronicaScreen(VeronicaView view) {
      super(Component.translatable("gui.viltrumitecore.veronica.title"));
      this.view = view;
   }

   @Override
   protected void init() {
      super.init();
      this.preview = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
      this.left = (this.width - panelWidth()) / 2;
      this.top = (this.height - panelHeight()) / 2;
   }

   private static int panelWidth() {
      return COLS * CARD_W + (COLS + 1) * GAP;
   }

   private static int panelHeight() {
      return 30 + ROWS * CARD_H + (ROWS - 1) * GAP + 26;
   }

   private int cardX(int index) {
      return this.left + GAP + index % COLS * (CARD_W + GAP);
   }

   private int cardY(int index) {
      return this.top + 30 + index / COLS * (CARD_H + GAP);
   }

   /** Card 0..6 are the marks, 7 is the Hulkbuster. */
   private int cardCount() {
      return MARKS.length + 1;
   }

   private boolean choosable(int index) {
      return index < MARKS.length && this.view.choosable(MARKS[index]);
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics);
      int width = panelWidth();
      int height = panelHeight();
      graphics.fill(this.left, this.top, this.left + width, this.top + height, GLASS);
      graphics.fill(this.left, this.top, this.left + width, this.top + 1, EDGE);
      graphics.fill(this.left, this.top + height - 1, this.left + width, this.top + height, EDGE);
      graphics.fill(this.left, this.top, this.left + 1, this.top + height, EDGE);
      graphics.fill(this.left + width - 1, this.top, this.left + width, this.top + height, EDGE);
      int scan = this.top + 2 + (int)(System.currentTimeMillis() / 40L % Math.max(1, height - 4));
      graphics.fill(this.left + 1, scan, this.left + width - 1, scan + 1, 0x2890E8FF);
      graphics.drawCenteredString(this.font, this.title, this.left + width / 2, this.top + 8, ACCENT);
      if (this.view.podTicksLeft() >= 0) {
         long seconds = (this.view.podTicksLeft() + 19) / 20;
         graphics.drawString(this.font, Component.translatable("gui.viltrumitecore.veronica.pod_time", seconds), this.left + 8, this.top + height - 16, DIM, false);
      }

      float time = (System.currentTimeMillis() % 100000L) / 1000.0F;
      for (int i = 0; i < cardCount(); i++) {
         renderCard(graphics, i, mouseX, mouseY, time);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void renderCard(GuiGraphics graphics, int index, int mouseX, int mouseY, float time) {
      int x = cardX(index);
      int y = cardY(index);
      boolean hulk = index == MARKS.length;
      boolean ok = hulk ? this.view.hulkbuster() == 0 : choosable(index);
      boolean hover = ok && mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
      graphics.fill(x, y, x + CARD_W, y + CARD_H, ok ? (hover ? 0x9028608A : CELL) : LOCKED);
      graphics.fill(x, y, x + CARD_W, y + 1, ok ? EDGE : 0x40406070);
      if (hulk) {
         renderHulkCard(graphics, x, y, time);
         return;
      }

      MarkId mark = MARKS[index];
      // Model spans -0.5..1.5 units around the origin: feet end just above the name.
      drawPreview(graphics, mark, x + CARD_W / 2, y + 16, time * 40.0F + index * 25.0F, ok);
      drawFitted(graphics, Component.translatable("mark.viltrumitecore." + mark.key() + ".name"), x + CARD_W / 2, y + 54, ok ? ACCENT : DIM);
      drawFitted(graphics, Component.translatable("mark.viltrumitecore." + mark.key() + ".role"), x + CARD_W / 2, y + 63, DIM);
      int bar = Mth.clamp(Math.round((CARD_W - 12) * this.view.durabilityFraction(mark)), 0, CARD_W - 12);
      graphics.fill(x + 6, y + 73, x + CARD_W - 6, y + 76, 0x80103040);
      graphics.fill(x + 6, y + 73, x + 6 + bar, y + 76, this.view.durabilityFraction(mark) < 0.25F ? AMBER : 0xFF50D8FF);
      Component status = statusOf(mark);
      graphics.drawCenteredString(this.font, status, x + CARD_W / 2, y + 81, statusColor(mark));
      if (this.view.cooldown(mark) > 0) {
         graphics.fill(x, y, x + CARD_W, y + CARD_H, 0xA0000810);
         long seconds = (this.view.cooldown(mark) + 19) / 20;
         graphics.drawCenteredString(this.font, Component.translatable("gui.viltrumitecore.veronica.cooldown", seconds), x + CARD_W / 2, y + CARD_H / 2 - 4, RED);
      }
   }

   private Component statusOf(MarkId mark) {
      MarkLocation location = this.view.location(mark);
      if (this.view.cooldown(mark) > 0) {
         return Component.translatable("gui.viltrumitecore.veronica.unavailable");
      }

      return switch (location) {
         case WORN -> Component.translatable("gui.viltrumitecore.veronica.worn");
         case EMPTY -> Component.translatable("gui.viltrumitecore.veronica.empty");
         case IN_DELIVERY -> Component.translatable("gui.viltrumitecore.veronica.delivery");
         case LEGION -> Component.translatable("gui.viltrumitecore.veronica.legion");
         case STORED -> this.view.durability(mark) <= 0.0F ? Component.translatable("gui.viltrumitecore.veronica.unavailable")
            : Component.translatable("gui.viltrumitecore.veronica.durability", Math.round(this.view.durabilityFraction(mark) * 100.0F));
      };
   }

   private int statusColor(MarkId mark) {
      if (this.view.cooldown(mark) > 0) {
         return RED;
      }

      return this.view.location(mark) == MarkLocation.WORN ? AMBER : ACCENT;
   }

   /** Centered text shrunk to the card width (long mark names, other languages). */
   private void drawFitted(GuiGraphics graphics, Component text, int cx, int y, int color) {
      int width = this.font.width(text);
      int max = CARD_W - 6;
      if (width <= max) {
         graphics.drawCenteredString(this.font, text, cx, y, color);
         return;
      }

      float scale = max / (float)width;
      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(cx, y + 4.0F * (1.0F - scale), 0.0F);
      pose.scale(scale, scale, 1.0F);
      graphics.drawCenteredString(this.font, text, 0, 0, color);
      pose.popPose();
   }

   /** Hulkbuster card (spec §12.3, §14.1): turning Mark 48, name, description fitted to the card, synced state. */
   private void renderHulkCard(GuiGraphics graphics, int x, int y, float time) {
      int state = this.view.hulkbuster();
      boolean on = state == VeronicaView.HULKBUSTER_ON;
      boolean ready = state == 0;
      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(x + CARD_W / 2.0F, y + 40.0F, 100.0F);
      MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      HulkbusterPreview.draw(pose, buffers, time * 40.0F, ready || on ? 1.0F : 0.45F);
      buffers.endBatch();
      pose.popPose();
      drawFitted(graphics, Component.translatable("gui.viltrumitecore.veronica.hulkbuster"), x + CARD_W / 2, y + 44, ready || on ? ACCENT : DIM);
      drawWrapped(graphics, Component.translatable("gui.viltrumitecore.veronica.hulkbuster_desc"), x + 4, y + 53, 26, DIM);
      if (state > 0) {
         graphics.fill(x, y, x + CARD_W, y + CARD_H, 0xA0000810);
         long seconds = (state + 19) / 20;
         graphics.drawCenteredString(this.font, Component.translatable("gui.viltrumitecore.veronica.cooldown", seconds), x + CARD_W / 2, y + CARD_H / 2 - 4, RED);
         return;
      }

      Component status = on ? Component.translatable("gui.viltrumitecore.veronica.hulkbuster_on")
         : ready ? Component.translatable("gui.viltrumitecore.veronica.choose") : Component.translatable("gui.viltrumitecore.veronica.unavailable");
      graphics.drawCenteredString(this.font, status, x + CARD_W / 2, y + 81, on ? AMBER : ready ? ACCENT : DIM);
   }

   /** Wrapped text shrunk until its lines fit the box (the description length depends on the language). */
   private void drawWrapped(GuiGraphics graphics, Component text, int left, int top, int height, int color) {
      int width = CARD_W - 8;
      float scale = 0.8F;
      List<FormattedCharSequence> lines = this.font.split(text, Math.round(width / scale));
      while (scale > 0.5F && lines.size() * this.font.lineHeight * scale > height) {
         scale -= 0.05F;
         lines = this.font.split(text, Math.round(width / scale));
      }

      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(left, top, 0.0F);
      pose.scale(scale, scale, 1.0F);
      for (int i = 0; i < lines.size(); i++) {
         graphics.drawString(this.font, lines.get(i), 0, i * this.font.lineHeight, color, false);
      }

      pose.popPose();
   }

   /** Turning preview of the player model in the mark skin (rest pose, arms slightly out). */
   private void drawPreview(GuiGraphics graphics, MarkId mark, int cx, int cy, float yaw, boolean active) {
      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(cx, cy, 100.0F);
      pose.scale(21.0F, 21.0F, 21.0F);
      pose.mulPose(Axis.YP.rotationDegrees(yaw));
      this.preview.rightArm.zRot = 0.18F;
      this.preview.leftArm.zRot = -0.18F;
      MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(MarkTextures.skin(mark)));
      float shade = active ? 1.0F : 0.45F;
      this.preview.renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, shade, shade, shade, 1.0F);
      buffers.endBatch();
      pose.popPose();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         for (int i = 0; i < MARKS.length; i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H && this.view.choosable(MARKS[i])) {
               CoreMessages.sendToServer(new VeronicaChooseC2SPacket(MARKS[i].ordinal()));
               this.onClose();
               return true;
            }
         }

         int hx = cardX(MARKS.length);
         int hy = cardY(MARKS.length);
         if (mouseX >= hx && mouseX < hx + CARD_W && mouseY >= hy && mouseY < hy + CARD_H && this.view.hulkbuster() == 0) {
            CoreMessages.sendToServer(new VeronicaChooseC2SPacket(VeronicaView.HULKBUSTER));
            this.onClose();
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}

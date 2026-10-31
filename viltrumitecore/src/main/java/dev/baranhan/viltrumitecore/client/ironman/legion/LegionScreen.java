package dev.baranhan.viltrumitecore.client.ironman.legion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkTextures;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionOrder;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPriority;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionProfile;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionStatus;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.VeronicaView;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.LegionCallC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.LegionOrderC2SPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Iron Legion menu (design §3–§4) in the Veronica glass style. Without a
 * Legion in the world it is the call screen: the seven marks as cards with a
 * turning preview, durability and Legion role; up to three are picked (click
 * or keys 1–7, the server's suggestion is preselected) and "Call" (Enter)
 * sends them. With the Legion out it is the command screen: five orders
 * (keys 1–5, sent at once, the menu closes so ATTACK/HOLD aim with the
 * crosshair) and the target priority (P, cycles in place), next to the
 * suits' live status. The server re-checks everything.
 */
public final class LegionScreen extends net.minecraft.client.gui.screens.Screen {
   private static final int CARD_W = 84;
   /** Tall enough for the status line (y + 81, 9 px font); the call grid must fit a 240-px-high GUI. */
   private static final int CARD_H = 92;
   private static final int GAP = 6;
   private static final int COLS = 4;
   private static final int ROWS = 2;
   /** Title strip above the cards and the hint strip below them (call mode). */
   private static final int CALL_HEADER = 24;
   private static final int CALL_FOOTER = 20;
   /**
    * The smallest common GUI height: 1280×720 at auto scale 3 and the default
    * 854×480 window at scale 2 are both 240 scaled pixels high.
    */
   static final int MIN_GUI_HEIGHT = 240;
   private static final int ORDER_W = 176;
   private static final int ORDER_H = 22;
   private static final int SUIT_W = 150;
   private static final int GLASS = 0xB00A1A2C;
   private static final int EDGE = 0x9060D8FF;
   private static final int CELL = 0x7018405A;
   private static final int HOVER = 0x9028608A;
   private static final int LOCKED = 0x8A10161E;
   private static final int ACCENT = 0xFF9FE8FF;
   private static final int DIM = 0xFF5A7A8A;
   private static final int AMBER = 0xFFFFB020;
   private static final int RED = 0xFFFF5040;
   private static final MarkId[] MARKS = MarkId.values();
   private static final LegionOrder[] ORDERS = LegionOrder.values();
   private final VeronicaView view;
   private final boolean command;
   private final List<MarkId> picked = new ArrayList<>();
   private int order;
   private int priority;
   private PlayerModel<AbstractClientPlayer> preview;
   private int left;
   private int top;

   public LegionScreen(LegionMenuS2CPacket packet) {
      super(Component.translatable(packet.active() ? "gui.viltrumitecore.legion.command_title" : "gui.viltrumitecore.legion.title"));
      this.view = packet.view();
      this.command = packet.active();
      this.order = packet.order();
      this.priority = packet.priority();
      for (int id : packet.suggested()) {
         MarkId mark = MarkId.byId(id);
         if (mark != null && this.view.legionCallable(mark) && !this.picked.contains(mark) && this.picked.size() < LegionRules.MAX_SUITS) {
            this.picked.add(mark);
         }
      }
   }

   /** A newer status while the command menu is open (order and priority follow the server). */
   void onStatus(LegionStatus status) {
      if (this.command) {
         if (!status.active()) {
            this.onClose();
            return;
         }

         this.order = status.order();
         this.priority = status.priority();
      }
   }

   @Override
   protected void init() {
      super.init();
      this.preview = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
      this.left = (this.width - panelWidth()) / 2;
      this.top = Math.max(0, (this.height - panelHeight()) / 2);
   }

   private int panelWidth() {
      return this.command ? GAP * 3 + ORDER_W + SUIT_W : COLS * CARD_W + (COLS + 1) * GAP;
   }

   private int panelHeight() {
      return this.command ? commandPanelHeight() : callPanelHeight();
   }

   static int callPanelHeight() {
      return CALL_HEADER + ROWS * CARD_H + (ROWS - 1) * GAP + CALL_FOOTER;
   }

   static int commandPanelHeight() {
      return 30 + ORDERS.length * (ORDER_H + 4) + ORDER_H + 18;
   }

   private int cardX(int index) {
      return this.left + GAP + index % COLS * (CARD_W + GAP);
   }

   private int cardY(int index) {
      return this.top + CALL_HEADER + index / COLS * (CARD_H + GAP);
   }

   private int orderY(int index) {
      return this.top + 30 + index * (ORDER_H + 4);
   }

   private int priorityY() {
      return orderY(ORDERS.length) + 4;
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
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
      if (this.command) {
         renderCommand(graphics, mouseX, mouseY);
      } else {
         float time = (System.currentTimeMillis() % 100000L) / 1000.0F;
         for (int i = 0; i < MARKS.length; i++) {
            renderCard(graphics, i, mouseX, mouseY, time);
         }

         renderCallCard(graphics, mouseX, mouseY);
         graphics.drawString(this.font, Component.translatable("gui.viltrumitecore.legion.hint_call"), this.left + 8, this.top + height - 16, DIM, false);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   // --- call mode ---

   private void renderCard(GuiGraphics graphics, int index, int mouseX, int mouseY, float time) {
      MarkId mark = MARKS[index];
      int x = cardX(index);
      int y = cardY(index);
      boolean ok = this.view.legionCallable(mark);
      int pick = this.picked.indexOf(mark);
      boolean hover = ok && inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
      graphics.fill(x, y, x + CARD_W, y + CARD_H, ok ? (hover || pick >= 0 ? HOVER : CELL) : LOCKED);
      int edge = pick >= 0 ? ACCENT : ok ? EDGE : 0x40406070;
      graphics.fill(x, y, x + CARD_W, y + 1, edge);
      if (pick >= 0) {
         graphics.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, edge);
         graphics.fill(x, y, x + 1, y + CARD_H, edge);
         graphics.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, edge);
         graphics.drawString(this.font, "\u25C6" + (pick + 1), x + CARD_W - 16, y + 4, ACCENT, false);
      }

      graphics.drawString(this.font, String.valueOf(index + 1), x + 4, y + 4, ok ? DIM : 0xFF3A4A52, false);
      drawPreview(graphics, mark, x + CARD_W / 2, y + 16, time * 40.0F + index * 25.0F, ok);
      drawFitted(graphics, Component.translatable("mark.viltrumitecore." + mark.key() + ".name"), x + CARD_W / 2, y + 54, ok ? ACCENT : DIM);
      LegionProfile profile = LegionProfile.of(mark);
      if (profile != null) {
         drawFitted(graphics, Component.translatable("gui.viltrumitecore.legion.role." + profile.role().name().toLowerCase(java.util.Locale.ROOT)),
            x + CARD_W / 2, y + 63, DIM);
      }

      float fraction = this.view.durabilityFraction(mark);
      int bar = Mth.clamp(Math.round((CARD_W - 12) * fraction), 0, CARD_W - 12);
      graphics.fill(x + 6, y + 73, x + CARD_W - 6, y + 76, 0x80103040);
      graphics.fill(x + 6, y + 73, x + 6 + bar, y + 76, fraction < 0.25F ? AMBER : 0xFF50D8FF);
      drawFitted(graphics, statusOf(mark), x + CARD_W / 2, y + 81, ok ? (pick >= 0 ? ACCENT : 0xFF7FC8E0) : RED);
   }

   private Component statusOf(MarkId mark) {
      if (this.view.cooldown(mark) > 0) {
         return Component.translatable("gui.viltrumitecore.veronica.cooldown", (this.view.cooldown(mark) + 19) / 20);
      }

      MarkLocation location = this.view.location(mark);
      return switch (location) {
         case WORN -> Component.translatable("gui.viltrumitecore.veronica.worn");
         case EMPTY -> Component.translatable("gui.viltrumitecore.veronica.empty");
         case IN_DELIVERY -> Component.translatable("gui.viltrumitecore.veronica.delivery");
         case LEGION -> Component.translatable("gui.viltrumitecore.veronica.legion");
         case STORED -> this.view.durability(mark) <= 0.0F ? Component.translatable("gui.viltrumitecore.legion.broken")
            : Component.translatable("gui.viltrumitecore.veronica.durability", Math.round(this.view.durabilityFraction(mark) * 100.0F));
      };
   }

   /** The eighth cell: how many are picked and the call button. */
   private void renderCallCard(GuiGraphics graphics, int mouseX, int mouseY) {
      int x = cardX(MARKS.length);
      int y = cardY(MARKS.length);
      boolean ok = !this.picked.isEmpty();
      boolean hover = ok && inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
      graphics.fill(x, y, x + CARD_W, y + CARD_H, ok ? (hover ? 0xA0307090 : CELL) : LOCKED);
      graphics.fill(x, y, x + CARD_W, y + 1, ok ? ACCENT : 0x40406070);
      drawFitted(graphics, Component.translatable("gui.viltrumitecore.legion.selected", this.picked.size(), LegionRules.MAX_SUITS), x + CARD_W / 2, y + 14,
         ok ? ACCENT : DIM);
      for (int i = 0; i < LegionRules.MAX_SUITS; i++) {
         int sx = x + 12 + i * 22;
         boolean on = i < this.picked.size();
         graphics.fill(sx, y + 30, sx + 16, y + 46, on ? 0xC050D8FF : 0x60203040);
         if (on) {
            graphics.drawCenteredString(this.font, String.valueOf(this.picked.get(i).ordinal() + 1), sx + 8, y + 34, 0xFF06141C);
         }
      }

      graphics.fill(x + 8, y + 58, x + CARD_W - 8, y + 78, ok ? (hover ? 0xE060D8FF : 0xB040A8D0) : 0x60203040);
      drawFitted(graphics, Component.translatable("gui.viltrumitecore.legion.call"), x + CARD_W / 2, y + 64, ok ? 0xFF06141C : DIM);
      drawFitted(graphics, Component.translatable("gui.viltrumitecore.legion.hint_enter"), x + CARD_W / 2, y + 82, DIM);
   }

   private void toggle(MarkId mark) {
      if (this.picked.remove(mark)) {
         return;
      }

      if (this.view.legionCallable(mark) && this.picked.size() < LegionRules.MAX_SUITS) {
         this.picked.add(mark);
      }
   }

   private void call() {
      if (this.picked.isEmpty()) {
         return;
      }

      int[] ids = new int[this.picked.size()];
      for (int i = 0; i < ids.length; i++) {
         ids[i] = this.picked.get(i).ordinal();
      }

      CoreMessages.sendToServer(new LegionCallC2SPacket(ids));
      this.onClose();
   }

   // --- command mode ---

   private void renderCommand(GuiGraphics graphics, int mouseX, int mouseY) {
      int x = this.left + GAP;
      for (int i = 0; i < ORDERS.length; i++) {
         LegionOrder value = ORDERS[i];
         int y = orderY(i);
         boolean current = i == this.order;
         boolean hover = inside(mouseX, mouseY, x, y, ORDER_W, ORDER_H);
         graphics.fill(x, y, x + ORDER_W, y + ORDER_H, current ? 0xA0307090 : hover ? HOVER : CELL);
         graphics.fill(x, y, x + 2, y + ORDER_H, current ? ACCENT : EDGE);
         graphics.drawString(this.font, (i + 1) + "  " + Component.translatable("gui.viltrumitecore.legion.order." + value.key()).getString(), x + 6, y + 3,
            current ? ACCENT : 0xFFCFEFFF, false);
         drawSmall(graphics, Component.translatable("gui.viltrumitecore.legion.order." + value.key() + ".hint"), x + 15, y + 13, ORDER_W - 18, DIM);
      }

      int py = priorityY();
      boolean hover = inside(mouseX, mouseY, x, py, ORDER_W, ORDER_H);
      graphics.fill(x, py, x + ORDER_W, py + ORDER_H, hover ? HOVER : CELL);
      graphics.fill(x, py, x + 2, py + ORDER_H, AMBER);
      LegionPriority current = LegionPriority.byId(this.priority) == null ? LegionPriority.NEAREST : LegionPriority.byId(this.priority);
      graphics.drawString(this.font, Component.translatable("gui.viltrumitecore.legion.priority",
         Component.translatable("gui.viltrumitecore.legion.priority." + current.key())), x + 6, py + 3, AMBER, false);
      drawSmall(graphics, Component.translatable("gui.viltrumitecore.legion.priority.hint"), x + 6, py + 13, ORDER_W - 10, DIM);
      renderSuits(graphics, this.left + GAP * 2 + ORDER_W, this.top + 30);
   }

   private void renderSuits(GuiGraphics graphics, int x, int y) {
      LegionStatus status = ClientLegion.status();
      graphics.drawString(this.font, Component.translatable("gui.viltrumitecore.legion.suits"), x, y, DIM, false);
      int row = y + 12;
      for (LegionStatus.Suit suit : status.suits()) {
         MarkId mark = suit.markId();
         if (mark == null) {
            continue;
         }

         graphics.fill(x, row, x + SUIT_W, row + 34, CELL);
         drawSmall(graphics, Component.translatable("mark.viltrumitecore." + mark.key() + ".name"), x + 4, row + 3, SUIT_W - 8, ACCENT);
         int bar = Math.round((SUIT_W - 8) * suit.fraction());
         graphics.fill(x + 4, row + 14, x + SUIT_W - 4, row + 17, 0x80103040);
         graphics.fill(x + 4, row + 14, x + 4 + bar, row + 17, LegionHud.barColor(suit.fraction(), false));
         drawSmall(graphics, LegionHud.state(suit), x + 4, row + 21, SUIT_W - 8, LegionHud.stateColor(suit));
         row += 38;
      }

      if (status.suits().isEmpty()) {
         drawSmall(graphics, Component.translatable("gui.viltrumitecore.legion.none"), x, row, SUIT_W, DIM);
      }
   }

   private void sendOrder(int index) {
      if (index < 0 || index >= ORDERS.length) {
         return;
      }

      CoreMessages.sendToServer(new LegionOrderC2SPacket(index, -1));
      this.onClose();
   }

   private void cyclePriority() {
      LegionPriority current = LegionPriority.byId(this.priority);
      LegionPriority next = current == null ? LegionPriority.NEAREST : current.next();
      this.priority = next.ordinal();
      CoreMessages.sendToServer(new LegionOrderC2SPacket(-1, next.ordinal()));
   }

   // --- input ---

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         if (this.command) {
            int x = this.left + GAP;
            for (int i = 0; i < ORDERS.length; i++) {
               if (inside(mouseX, mouseY, x, orderY(i), ORDER_W, ORDER_H)) {
                  sendOrder(i);
                  return true;
               }
            }

            if (inside(mouseX, mouseY, x, priorityY(), ORDER_W, ORDER_H)) {
               cyclePriority();
               return true;
            }
         } else {
            for (int i = 0; i < MARKS.length; i++) {
               if (inside(mouseX, mouseY, cardX(i), cardY(i), CARD_W, CARD_H)) {
                  toggle(MARKS[i]);
                  return true;
               }
            }

            if (inside(mouseX, mouseY, cardX(MARKS.length), cardY(MARKS.length), CARD_W, CARD_H)) {
               call();
               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      int digit = keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9 ? keyCode - GLFW.GLFW_KEY_1
         : keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9 ? keyCode - GLFW.GLFW_KEY_KP_1 : -1;
      if (this.command) {
         if (digit >= 0 && digit < ORDERS.length) {
            sendOrder(digit);
            return true;
         }

         if (keyCode == GLFW.GLFW_KEY_P) {
            cyclePriority();
            return true;
         }
      } else {
         if (digit >= 0 && digit < MARKS.length) {
            toggle(MARKS[digit]);
            return true;
         }

         if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            call();
            return true;
         }
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   // --- drawing helpers (Veronica style) ---

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

   /** Left-aligned text at 0.75 scale, shrunk further to {@code max} pixels. */
   private void drawSmall(GuiGraphics graphics, Component text, int x, int y, int max, int color) {
      float scale = Math.min(0.75F, max / (float)Math.max(1, this.font.width(text)));
      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(x, y, 0.0F);
      pose.scale(scale, scale, 1.0F);
      graphics.drawString(this.font, text, 0, 0, color, false);
      pose.popPose();
   }

   private void drawPreview(GuiGraphics graphics, MarkId mark, int cx, int cy, float yaw, boolean active) {
      PoseStack pose = graphics.pose();
      pose.pushPose();
      pose.translate(cx, cy, 100.0F);
      pose.scale(21.0F, 21.0F, 21.0F);
      pose.mulPose(Axis.YP.rotationDegrees(yaw));
      // Legion stance: arms a little out and forward, ready to fly.
      this.preview.rightArm.xRot = -0.25F;
      this.preview.leftArm.xRot = -0.25F;
      this.preview.rightArm.zRot = 0.3F;
      this.preview.leftArm.zRot = -0.3F;
      MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(MarkTextures.skin(mark)));
      float shade = active ? 1.0F : 0.45F;
      this.preview.renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, shade, shade, shade, 1.0F);
      if (active) {
         VertexConsumer glow = buffers.getBuffer(RenderType.eyes(MarkTextures.glow(mark)));
         this.preview.renderToBuffer(pose, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
      }

      buffers.endBatch();
      pose.popPose();
   }
}

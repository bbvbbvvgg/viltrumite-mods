package dev.baranhan.viltrumitecore.client.gui;

import dev.baranhan.viltrumitecore.client.budget.BudgetSkins;
import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroChoiceC2SPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Ultimate Hero Expansion picker: paged grid of plain buttons with a small
 * face icon each, plus the inactive "HULK - IN DEVELOPMENT" entry on the
 * last page. Hulk is interface-only (no HeroId), so saves never see it.
 */
public class ExpansionHeroScreen extends Screen {
   static final int BUTTON_W = 110;
   static final int BUTTON_H = 20;
   static final int ICON = 16;
   static final int CELL_W = BUTTON_W + ICON + 6;
   static final int CELL_H = BUTTON_H + 4;
   private static final int HULK_INDEX = -1;
   private final Screen parent;
   private int page;
   private int pages = 1;
   private int columns = 1;
   private int rows = 1;
   private int gridX;
   private int gridY;
   private int hulkMessageTicks;
   private int[] pageEntries = new int[0];

   public ExpansionHeroScreen(Screen parent) {
      super(Component.translatable("gui.viltrumitecore.expansion.title"));
      this.parent = parent;
   }

   /** Roster entries followed by the Hulk stub. */
   static int entryCount() {
      return BudgetHeroes.count() + 1;
   }

   protected void init() {
      this.clearWidgets();
      this.columns = Math.max(1, Math.min(3, (this.width - 20) / CELL_W));
      this.rows = Math.max(1, Math.min(6, (this.height - 90) / CELL_H));
      int perPage = this.columns * this.rows;
      this.pages = Math.max(1, (entryCount() + perPage - 1) / perPage);
      this.page = Math.max(0, Math.min(this.page, this.pages - 1));
      this.gridX = this.width / 2 - this.columns * CELL_W / 2;
      this.gridY = 40;
      List<BudgetHeroSpec> roster = BudgetHeroes.roster();
      int first = this.page * perPage;
      int last = Math.min(entryCount(), first + perPage);
      this.pageEntries = new int[last - first];
      for (int index = first; index < last; index++) {
         int slot = index - first;
         int x = this.gridX + (slot % this.columns) * CELL_W + ICON + 4;
         int y = this.gridY + (slot / this.columns) * CELL_H;
         if (index < roster.size()) {
            BudgetHeroSpec spec = roster.get(index);
            this.pageEntries[slot] = index;
            this.addRenderableWidget(Button.builder(Component.translatable(spec.nameKey()), button -> {
               CoreMessages.sendToServer(new HeroChoiceC2SPacket(spec.key()));
               this.onClose();
            }).bounds(x, y, BUTTON_W, BUTTON_H).build());
         } else {
            this.pageEntries[slot] = HULK_INDEX;
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7c").append(Component.translatable("gui.viltrumitecore.expansion.hulk")),
               button -> this.refuseHulk()).bounds(x, y, BUTTON_W, BUTTON_H).build());
         }
      }

      int bottom = this.height - 28;
      Button prev = this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.expansion.prev"), button -> {
         this.page--;
         this.init();
      }).bounds(this.width / 2 - 155, bottom, 70, 20).build());
      prev.active = this.page > 0;
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.expansion.back"),
         button -> this.minecraft.setScreen(this.parent)).bounds(this.width / 2 - 40, bottom, 80, 20).build());
      Button next = this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.expansion.next"), button -> {
         this.page++;
         this.init();
      }).bounds(this.width / 2 + 85, bottom, 70, 20).build());
      next.active = this.page < this.pages - 1;
   }

   private void refuseHulk() {
      this.hulkMessageTicks = 80;
      if (this.minecraft != null && this.minecraft.player != null) {
         this.minecraft.player.displayClientMessage(Component.translatable("gui.viltrumitecore.expansion.hulk_locked"), false);
      }
   }

   public void tick() {
      if (this.hulkMessageTicks > 0) {
         this.hulkMessageTicks--;
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics);
      super.render(graphics, mouseX, mouseY, partialTick);
      graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFF55);
      graphics.drawCenteredString(this.font, Component.translatable("gui.viltrumitecore.expansion.count", BudgetHeroes.count()), this.width / 2, 24, 0xAAAAAA);
      List<BudgetHeroSpec> roster = BudgetHeroes.roster();
      for (int slot = 0; slot < this.pageEntries.length; slot++) {
         int x = this.gridX + (slot % this.columns) * CELL_W;
         int y = this.gridY + (slot / this.columns) * CELL_H + 2;
         int index = this.pageEntries[slot];
         if (index == HULK_INDEX) {
            graphics.fill(x, y, x + ICON, y + ICON, 0xFF2E7D32);
            graphics.drawCenteredString(this.font, "?", x + ICON / 2, y + 4, 0xFFFFFF);
         } else {
            BudgetHeroSpec spec = roster.get(index);
            HeroHeadPreviews.draw(spec.id(), graphics, x, y, ICON, partialTick, BudgetSkins.skin(spec));
         }
      }

      graphics.drawCenteredString(this.font, Component.translatable("gui.viltrumitecore.expansion.page", this.page + 1, this.pages),
         this.width / 2, this.height - 40, 0xFFFFFF);
      if (this.hulkMessageTicks > 0) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.viltrumitecore.expansion.hulk_locked"), this.width / 2,
            this.height - 52, 0xFF5555);
      }
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public boolean isPauseScreen() {
      return false;
   }
}

package dev.baranhan.viltrumitecore.client.gui;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroChoiceC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.RaceChoiceC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class RaceSelectionScreen extends Screen {
   private static final ResourceLocation HOMELANDER_SKIN = new ResourceLocation("viltrumitecore", "textures/entity/hero/homelander.png");
   private static final ResourceLocation REGULUS_SKIN = new ResourceLocation("viltrumitecore", "textures/entity/hero/regulus.png");
   private static final ResourceLocation IRON_MAN_SKIN = new ResourceLocation("viltrumitecore", "textures/entity/hero/ironman.png");

   public RaceSelectionScreen() {
      super(Component.translatable("gui.viltrumitecore.race_selection.title"));
   }

   protected void init() {
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      // Four hero buttons in one row (100 px, 5 px gap), centred.
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.race_selection.become_homelander"), button -> {
         this.sendHeroChoice(HeroId.HOMELANDER);
         this.onClose();
      }).bounds(centerX + heroX(0), centerY - 20, 100, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.race_selection.become_regulus"), button -> {
         this.sendHeroChoice(HeroId.REGULUS);
         this.onClose();
      }).bounds(centerX + heroX(1), centerY - 20, 100, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.race_selection.become_ironman"), button -> {
         this.sendHeroChoice(HeroId.IRON_MAN);
         this.onClose();
      }).bounds(centerX + heroX(2), centerY - 20, 100, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.race_selection.become_reinhard"), button -> {
         this.sendHeroChoice(HeroId.REINHARD);
         this.onClose();
      }).bounds(centerX + heroX(3), centerY - 20, 100, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.race_selection.remain_human"), button -> {
         this.sendHeroChoice(HeroId.HUMAN);
         this.onClose();
      }).bounds(centerX - 50, centerY + 6, 100, 20).build());
   }

   private void sendLegacyChoice(boolean choseViltrumite) {
      CoreMessages.sendToServer(new RaceChoiceC2SPacket(choseViltrumite));
   }

   private void sendHeroChoice(HeroId id) {
      CoreMessages.sendToServer(new HeroChoiceC2SPacket(id.key()));
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      guiGraphics.drawCenteredString(this.font, Component.translatable("gui.viltrumitecore.race_selection.question"), centerX, centerY - 60, 16777215);

      // Head previews above each hero button: base face + hat layer, drawn skin-flat like a player head.
      drawHead(guiGraphics, HOMELANDER_SKIN, centerX + heroX(0) + 50 - 12, centerY - 48);
      drawHead(guiGraphics, REGULUS_SKIN, centerX + heroX(1) + 50 - 12, centerY - 48);
      drawHead(guiGraphics, IRON_MAN_SKIN, centerX + heroX(2) + 50 - 12, centerY - 48);
      // Reinhard: vanilla skin until his model head preview arrives (Stage 1b).
      drawHead(guiGraphics, net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(), centerX + heroX(3) + 50 - 12, centerY - 48);
   }

   /** Left edge of hero button {@code index} relative to the screen centre (4 buttons, 100 px, 5 px gap). */
   private static int heroX(int index) {
      return -207 + index * 105;
   }

   private static void drawHead(GuiGraphics guiGraphics, ResourceLocation skin, int x, int y) {
      guiGraphics.blit(skin, x, y, 24, 24, 8.0F, 8.0F, 8, 8, 64, 64);
      guiGraphics.blit(skin, x, y, 24, 24, 40.0F, 8.0F, 8, 8, 64, 64);
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public boolean isPauseScreen() {
      return false;
   }
}

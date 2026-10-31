package dev.baranhan.viltrumitecore.client.gui;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
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
   private static final HeroId[] HEROES = {HeroId.HOMELANDER, HeroId.REGULUS, HeroId.IRON_MAN, HeroId.REINHARD, HeroId.THOR};
   private static final String[] LABELS = {"become_homelander", "become_regulus", "become_ironman", "become_reinhard", "become_thor"};
   private HeroChoiceLayout.Layout layout = HeroChoiceLayout.of(0, 0, HEROES.length);

   public RaceSelectionScreen() {
      super(Component.translatable("gui.viltrumitecore.race_selection.title"));
   }

   protected void init() {
      // One centred row when it fits, wrapped rows on narrow GUI scales (HeroChoiceLayout).
      this.layout = HeroChoiceLayout.of(this.width, this.height, HEROES.length);
      for (int index = 0; index < HEROES.length; index++) {
         HeroId hero = HEROES[index];
         HeroChoiceLayout.Cell cell = this.layout.heroes().get(index);
         this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.race_selection." + LABELS[index]), button -> {
            this.sendHeroChoice(hero);
            this.onClose();
         }).bounds(cell.x(), cell.y(), HeroChoiceLayout.BUTTON_W, HeroChoiceLayout.BUTTON_H).build());
      }
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.race_selection.remain_human"), button -> {
         this.sendHeroChoice(HeroId.HUMAN);
         this.onClose();
      }).bounds(this.layout.humanX(), this.layout.humanY(), HeroChoiceLayout.BUTTON_W, HeroChoiceLayout.BUTTON_H).build());
      // Ultimate Hero Expansion: the 30 new heroes live on their own paged screen.
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.expansion.open"),
         button -> this.minecraft.setScreen(new ExpansionHeroScreen(this)))
         .bounds(this.width / 2 - 80, Math.min(this.height - 24, this.layout.humanY() + HeroChoiceLayout.BUTTON_H + 6), 160, HeroChoiceLayout.BUTTON_H).build());
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
      guiGraphics.drawCenteredString(this.font, Component.translatable("gui.viltrumitecore.race_selection.question"), this.width / 2, this.layout.questionY(), 16777215);

      // Head previews above each hero button (HeroHeadPreviews: registered 3D heads, else the flat skin face).
      for (int index = 0; index < HEROES.length; index++) {
         HeroChoiceLayout.Cell cell = this.layout.heroes().get(index);
         HeroHeadPreviews.draw(HEROES[index], guiGraphics, cell.headX(), cell.headY(), HeroChoiceLayout.HEAD, partialTick, skin(HEROES[index]));
      }
   }

   private static ResourceLocation skin(HeroId hero) {
      return switch (hero) {
         case HOMELANDER -> HOMELANDER_SKIN;
         case REGULUS -> REGULUS_SKIN;
         case IRON_MAN -> IRON_MAN_SKIN;
         default -> net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
      };
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public boolean isPauseScreen() {
      return false;
   }
}

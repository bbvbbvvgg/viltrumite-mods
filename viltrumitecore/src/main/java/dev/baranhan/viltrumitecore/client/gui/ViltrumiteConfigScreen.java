package dev.baranhan.viltrumitecore.client.gui;

import dev.baranhan.viltrumitecore.client.gui.config.CameraConfigScreen;
import dev.baranhan.viltrumitecore.client.gui.config.CinematicConfigScreen;
import dev.baranhan.viltrumitecore.client.gui.config.ClientConfigScreen;
import dev.baranhan.viltrumitecore.client.gui.config.CoreConfigScreen;
import dev.baranhan.viltrumitecore.client.gui.config.FlightCameraConfigScreen;
import dev.baranhan.viltrumitecore.client.gui.config.FlightClientConfigScreen;
import dev.baranhan.viltrumitecore.client.gui.config.FlightConfigScreen;
import dev.baranhan.viltrumitecore.client.gui.config.PostProcessConfigScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ViltrumiteConfigScreen extends Screen {
   private final Screen parent;

   public ViltrumiteConfigScreen(Screen parent) {
      super(Component.translatable("gui.viltrumitecore.config.title"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      int buttonWidth = 150;
      int buttonHeight = 20;
      int startX = this.width / 2 - 155;
      int startY = this.height / 6 + 24;
      int rowSpacing = 30;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.camera"), button -> this.minecraft.setScreen(new CameraConfigScreen(this)))
            .bounds(startX, startY, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.post_process"), button -> this.minecraft.setScreen(new PostProcessConfigScreen(this)))
            .bounds(startX + 160, startY, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.client"), button -> this.minecraft.setScreen(new ClientConfigScreen(this)))
            .bounds(startX, startY + rowSpacing, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.core_server"), button -> this.minecraft.setScreen(new CoreConfigScreen(this)))
            .bounds(startX + 160, startY + rowSpacing, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.flight_server"), button -> this.minecraft.setScreen(new FlightConfigScreen(this)))
            .bounds(startX, startY + rowSpacing * 2, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.flight_client"), button -> this.minecraft.setScreen(new FlightClientConfigScreen(this)))
            .bounds(startX + 160, startY + rowSpacing * 2, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.flight_camera"), button -> this.minecraft.setScreen(new FlightCameraConfigScreen(this)))
            .bounds(startX, startY + rowSpacing * 3, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.cinematic"), button -> this.minecraft.setScreen(new CinematicConfigScreen(this)))
            .bounds(startX + 160, startY + rowSpacing * 3, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.viltrumitecore.config.done"), button -> this.minecraft.setScreen(this.parent))
            .bounds(this.width / 2 - 100, startY + rowSpacing * 4 + 10, 200, 20)
            .build()
      );
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      guiGraphics.fill(0, 0, this.width, this.height, -1073741824);
      guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   public boolean isPauseScreen() {
      return false;
   }
}

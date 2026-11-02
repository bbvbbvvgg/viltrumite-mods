package dev.baranhan.viltrumitecore.client.gui.config;

import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PostProcessConfigScreen extends Screen {
   private final Screen parent;
   private PostProcessConfigScreen.ConfigListWidget listWidget;
   private EditBox punchShakeField;
   private EditBox punchEffectField;
   private EditBox lockVignetteField;
   private EditBox mangaField;

   public PostProcessConfigScreen(Screen parent) {
      super(Component.translatable("gui.viltrumitecore.config.post_process.title"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      this.listWidget = new PostProcessConfigScreen.ConfigListWidget(this.minecraft, this.width, this.height, 40, this.height - 40, 44);
      this.addRenderableWidget(this.listWidget);
      this.punchShakeField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.punchShakeField.setValue(String.valueOf(ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier));
      this.punchShakeField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.listWidget
         .addEntry(new PostProcessConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.post_process.punch_shake"), this.punchShakeField));
      this.punchEffectField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.punchEffectField.setValue(String.valueOf(ViltrumitePostProcessingConfig.INSTANCE.punchEffectMultiplier));
      this.punchEffectField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.listWidget
         .addEntry(
            new PostProcessConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.post_process.punch_effect"), this.punchEffectField)
         );
      this.lockVignetteField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.lockVignetteField.setValue(String.valueOf(ViltrumitePostProcessingConfig.INSTANCE.lockVignetteMultiplier));
      this.lockVignetteField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.listWidget
         .addEntry(
            new PostProcessConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.post_process.lock_vignette"), this.lockVignetteField)
         );
      this.mangaField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.mangaField.setValue(String.valueOf(ViltrumitePostProcessingConfig.INSTANCE.mangaEffectsMultiplier));
      this.mangaField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.listWidget
         .addEntry(new PostProcessConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.post_process.manga_effects"), this.mangaField));
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.config.generic.save_back"), button -> {
         try {
            ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier = Float.parseFloat(this.punchShakeField.getValue());
            ViltrumitePostProcessingConfig.INSTANCE.punchEffectMultiplier = Float.parseFloat(this.punchEffectField.getValue());
            ViltrumitePostProcessingConfig.INSTANCE.lockVignetteMultiplier = Float.parseFloat(this.lockVignetteField.getValue());
            ViltrumitePostProcessingConfig.INSTANCE.mangaEffectsMultiplier = Math.max(0.0F, Float.parseFloat(this.mangaField.getValue()));
            ViltrumitePostProcessingConfig.save();
         } catch (NumberFormatException var3) {
         }

         this.minecraft.setScreen(this.parent);
      }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
   }

   private abstract class ConfigListEntry extends Entry<PostProcessConfigScreen.ConfigListEntry> {
   }

   private class ConfigListWidget extends ContainerObjectSelectionList<PostProcessConfigScreen.ConfigListEntry> {
      public ConfigListWidget(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
         super(client, width, height, top, bottom, itemHeight);
      }

      public int getRowWidth() {
         return 200;
      }

      protected int getScrollbarPosition() {
         return this.width / 2 + 115;
      }

      public int addEntry(PostProcessConfigScreen.ConfigListEntry entry) {
         return super.addEntry(entry);
      }
   }

   private class TextFieldEntry extends PostProcessConfigScreen.ConfigListEntry {
      private final Component label;
      private final EditBox textField;

      public TextFieldEntry(Component label, EditBox textField) {
         this.label = label;
         this.textField = textField;
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         guiGraphics.drawString(PostProcessConfigScreen.this.font, this.label, left, top + 4, 10526880, true);
         this.textField.setX(left);
         this.textField.setY(top + 18);
         this.textField.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      public List<? extends GuiEventListener> children() {
         return Collections.singletonList(this.textField);
      }

      public List<? extends NarratableEntry> narratables() {
         return Collections.singletonList(this.textField);
      }
   }
}

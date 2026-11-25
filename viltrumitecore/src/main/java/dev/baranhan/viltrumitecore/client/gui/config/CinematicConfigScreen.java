package dev.baranhan.viltrumitecore.client.gui.config;

import dev.baranhan.viltrumitecore.client.cinematic.CinematicSettings;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Comfort settings of the cinematic combat (wave1-22): the 5 fields of config/viltrumitecore_cinematic.json. */
public class CinematicConfigScreen extends Screen {
   private final Screen parent;
   private CinematicConfigScreen.ConfigListWidget listWidget;
   private Button reducedMotionButton;
   private Button disableCameraButton;
   private Button watchOthersButton;
   private Button impactFramesButton;
   private float flashValue;

   public CinematicConfigScreen(Screen parent) {
      super(Component.translatable("gui.viltrumitecore.config.cinematic.title"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      CinematicSettings settings = CinematicSettings.get();
      this.flashValue = settings.flash();
      this.listWidget = new CinematicConfigScreen.ConfigListWidget(this.minecraft, this.width, this.height, 40, this.height - 40, 28);
      this.addRenderableWidget(this.listWidget);
      this.reducedMotionButton = this.toggle(
         "gui.viltrumitecore.config.cinematic.reduce_motion", settings.reduceCameraMotion, CinematicConfigScreen.Kind.REDUCE_MOTION
      );
      this.listWidget.addEntry(new CinematicConfigScreen.SingleButtonEntry(this.reducedMotionButton));
      this.disableCameraButton = this.toggle(
         "gui.viltrumitecore.config.cinematic.disable_camera", settings.disableCinematicCamera, CinematicConfigScreen.Kind.DISABLE_CAMERA
      );
      this.listWidget.addEntry(new CinematicConfigScreen.SingleButtonEntry(this.disableCameraButton));
      this.watchOthersButton = this.toggle(
         "gui.viltrumitecore.config.cinematic.watch_others", settings.watchOthersCinematics, CinematicConfigScreen.Kind.WATCH_OTHERS
      );
      this.listWidget.addEntry(new CinematicConfigScreen.SingleButtonEntry(this.watchOthersButton));
      this.impactFramesButton = this.toggle(
         "gui.viltrumitecore.config.cinematic.impact_frames", settings.impactFrames, CinematicConfigScreen.Kind.IMPACT_FRAMES
      );
      this.listWidget.addEntry(new CinematicConfigScreen.SingleButtonEntry(this.impactFramesButton));
      this.listWidget.addEntry(new CinematicConfigScreen.SingleButtonEntry(new CinematicConfigScreen.FlashSlider()));
      this.addRenderableWidget(Button.builder(Component.translatable("gui.viltrumitecore.config.generic.save_back"), button -> {
         CinematicSettings current = CinematicSettings.get();
         current.reduceCameraMotion = this.kindOn(CinematicConfigScreen.Kind.REDUCE_MOTION);
         current.disableCinematicCamera = this.kindOn(CinematicConfigScreen.Kind.DISABLE_CAMERA);
         current.watchOthersCinematics = this.kindOn(CinematicConfigScreen.Kind.WATCH_OTHERS);
         current.impactFrames = this.kindOn(CinematicConfigScreen.Kind.IMPACT_FRAMES);
         current.flashIntensity = this.flashValue;
         CinematicSettings.save();
         this.minecraft.setScreen(this.parent);
      }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
   }

   private boolean kindOn(CinematicConfigScreen.Kind kind) {
      return Boolean.TRUE.equals(this.states.get(kind));
   }

   private final java.util.EnumMap<CinematicConfigScreen.Kind, Boolean> states = new java.util.EnumMap<>(CinematicConfigScreen.Kind.class);

   private Button toggle(String labelKey, boolean initial, CinematicConfigScreen.Kind kind) {
      this.states.put(kind, initial);
      return Button.builder(this.toggleLabel(labelKey, initial), button -> {
         boolean next = !Boolean.TRUE.equals(this.states.get(kind));
         this.states.put(kind, next);
         button.setMessage(this.toggleLabel(labelKey, next));
      }).bounds(0, 0, 200, 20).build();
   }

   private Component toggleLabel(String labelKey, boolean on) {
      return Component.translatable(
         labelKey, Component.translatable(on ? "gui.viltrumitecore.config.generic.on" : "gui.viltrumitecore.config.generic.off")
      );
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
   }

   private enum Kind {
      REDUCE_MOTION,
      DISABLE_CAMERA,
      WATCH_OTHERS,
      IMPACT_FRAMES
   }

   private abstract class ConfigListEntry extends Entry<CinematicConfigScreen.ConfigListEntry> {
   }

   private class ConfigListWidget extends ContainerObjectSelectionList<CinematicConfigScreen.ConfigListEntry> {
      public ConfigListWidget(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
         super(client, width, height, top, bottom, itemHeight);
      }

      public int getRowWidth() {
         return 200;
      }

      protected int getScrollbarPosition() {
         return this.width / 2 + 115;
      }

      protected int getMaxPosition() {
         return super.getMaxPosition() + 10;
      }

      public int addEntry(CinematicConfigScreen.ConfigListEntry entry) {
         return super.addEntry(entry);
      }
   }

   private class SingleButtonEntry extends CinematicConfigScreen.ConfigListEntry {
      private final AbstractWidget button;

      public SingleButtonEntry(AbstractWidget button) {
         this.button = button;
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         this.button.setX(left);
         this.button.setY(top + 4);
         this.button.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      public List<? extends GuiEventListener> children() {
         return Collections.singletonList(this.button);
      }

      public List<? extends NarratableEntry> narratables() {
         return Collections.singletonList(this.button);
      }
   }

   private class FlashSlider extends AbstractSliderButton {
      public FlashSlider() {
         super(0, 0, 200, 20, Component.empty(), CinematicConfigScreen.this.flashValue);
         this.refresh();
      }

      private void refresh() {
         this.setMessage(
            Component.translatable(
               "gui.viltrumitecore.config.cinematic.flash",
               Component.literal(String.format(java.util.Locale.ROOT, "%.2f", this.value))
            )
         );
      }

      protected void updateMessage() {
         this.refresh();
      }

      protected void applyValue() {
         CinematicConfigScreen.this.flashValue = (float)this.value;
      }
   }
}

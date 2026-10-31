package dev.baranhan.viltrumitecore.client.gui.config;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.CoreConfigSyncC2SPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteStatHolder;
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

public class CoreConfigScreen extends Screen {
   private final Screen parent;
   private CoreConfigScreen.ConfigListWidget listWidget;
   private EditBox baseDamageField;
   private EditBox damageReductionField;
   private EditBox damageIgnoreField;
   private EditBox healFactorField;
   private EditBox punchDropField;
   private EditBox dashDropField;
   private EditBox spaceLimitField;
   private EditBox meteorSpawnChanceField;
   private EditBox meteorDistanceField;
   private EditBox worldEventChanceField;
   private EditBox worldEventCooldownField;

   public CoreConfigScreen(Screen parent) {
      super(Component.translatable("gui.viltrumitecore.config.core.title"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      boolean isOp = this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
      ViltrumiteStatHolder playerStats = (ViltrumiteStatHolder)this.minecraft.player;
      this.listWidget = new CoreConfigScreen.ConfigListWidget(this.minecraft, this.width, this.height, 40, this.height - 40, 48);
      this.addRenderableWidget(this.listWidget);
      this.baseDamageField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.baseDamageField.setValue(playerStats != null ? String.valueOf(playerStats.getBaseDamage()) : "19.0");
      this.baseDamageField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.baseDamageField.setEditable(isOp);
      this.listWidget.addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.base_damage"), this.baseDamageField));
      this.damageReductionField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.damageReductionField.setValue(playerStats != null ? String.valueOf(playerStats.getDamageReduction()) : "0.0");
      this.damageReductionField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.damageReductionField.setEditable(isOp);
      this.listWidget
         .addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.damage_reduction"), this.damageReductionField));
      this.damageIgnoreField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.damageIgnoreField.setValue(playerStats != null ? String.valueOf(playerStats.getDamageIgnoreThreshold()) : "0.0");
      this.damageIgnoreField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.damageIgnoreField.setEditable(isOp);
      this.listWidget
         .addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.damage_ignore"), this.damageIgnoreField));
      this.healFactorField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.healFactorField.setValue(playerStats != null ? String.valueOf(playerStats.getHealFactor()) : "1.0");
      this.healFactorField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.healFactorField.setEditable(isOp);
      this.listWidget.addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.heal_factor"), this.healFactorField));
      this.punchDropField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.punchDropField.setValue(String.valueOf(ViltrumiteCoreConfig.INSTANCE.punchBlockDropChance));
      this.punchDropField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.punchDropField.setEditable(isOp);
      this.listWidget.addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.punch_drop"), this.punchDropField));
      this.dashDropField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.dashDropField.setValue(String.valueOf(ViltrumiteCoreConfig.INSTANCE.dashBlockDropChance));
      this.dashDropField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.dashDropField.setEditable(isOp);
      this.listWidget.addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.dash_drop"), this.dashDropField));
      this.spaceLimitField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.spaceLimitField.setValue(String.valueOf(ViltrumiteCoreConfig.INSTANCE.spaceLimitY));
      this.spaceLimitField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.spaceLimitField.setEditable(isOp);
      this.listWidget.addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.space_limit"), this.spaceLimitField));
      this.meteorSpawnChanceField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.meteorSpawnChanceField.setValue(String.valueOf(ViltrumiteCoreConfig.INSTANCE.meteorSpawnChancePercent));
      this.meteorSpawnChanceField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.meteorSpawnChanceField.setEditable(isOp);
      this.listWidget
         .addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.meteor_chance"), this.meteorSpawnChanceField));
      this.meteorDistanceField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.meteorDistanceField.setValue(String.valueOf(ViltrumiteCoreConfig.INSTANCE.meteorMinDistance));
      this.meteorDistanceField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.meteorDistanceField.setEditable(isOp);
      this.listWidget
         .addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.meteor_dist"), this.meteorDistanceField));
      this.worldEventChanceField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.worldEventChanceField.setValue(String.valueOf(ViltrumiteCoreConfig.INSTANCE.worldEventChancePercent));
      this.worldEventChanceField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
      this.worldEventChanceField.setEditable(isOp);
      this.listWidget
         .addEntry(new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.world_event_chance"), this.worldEventChanceField));
      this.worldEventCooldownField = new EditBox(this.font, 0, 0, 200, 20, Component.literal(""));
      this.worldEventCooldownField.setValue(String.valueOf(ViltrumiteCoreConfig.INSTANCE.worldEventCooldownMinutes));
      this.worldEventCooldownField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
      this.worldEventCooldownField.setEditable(isOp);
      this.listWidget
         .addEntry(
            new CoreConfigScreen.TextFieldEntry(Component.translatable("gui.viltrumitecore.config.core.world_event_cooldown"), this.worldEventCooldownField)
         );
      // "Ask race on join" / "Viltrumite by default" toggles are gone: the hero choice is never forced on join.
      Button bloodEnabledBtn = Button.builder(
            Component.translatable(
               "gui.viltrumitecore.config.core.blood_enabled",
               new Object[]{
                  Component.translatable(
                     ViltrumiteCoreConfig.INSTANCE.bloodEnabled ? "gui.viltrumitecore.config.generic.on" : "gui.viltrumitecore.config.generic.off"
                  )
               }
            ),
            button -> {
               ViltrumiteCoreConfig.INSTANCE.bloodEnabled = !ViltrumiteCoreConfig.INSTANCE.bloodEnabled;
               button.setMessage(
                  Component.translatable(
                     "gui.viltrumitecore.config.core.blood_enabled",
                     new Object[]{
                        Component.translatable(
                           ViltrumiteCoreConfig.INSTANCE.bloodEnabled ? "gui.viltrumitecore.config.generic.on" : "gui.viltrumitecore.config.generic.off"
                        )
                     }
                  )
               );
            }
         )
         .bounds(0, 0, 200, 20)
         .build();
      bloodEnabledBtn.active = isOp;
      Button humansBleedBtn = Button.builder(
            Component.translatable(
               "gui.viltrumitecore.config.core.humans_bleed",
               new Object[]{
                  Component.translatable(
                     ViltrumiteCoreConfig.INSTANCE.shouldHumansBleed ? "gui.viltrumitecore.config.generic.on" : "gui.viltrumitecore.config.generic.off"
                  )
               }
            ),
            button -> {
               ViltrumiteCoreConfig.INSTANCE.shouldHumansBleed = !ViltrumiteCoreConfig.INSTANCE.shouldHumansBleed;
               button.setMessage(
                  Component.translatable(
                     "gui.viltrumitecore.config.core.humans_bleed",
                     new Object[]{
                        Component.translatable(
                           ViltrumiteCoreConfig.INSTANCE.shouldHumansBleed ? "gui.viltrumitecore.config.generic.on" : "gui.viltrumitecore.config.generic.off"
                        )
                     }
                  )
               );
            }
         )
         .bounds(0, 0, 200, 20)
         .build();
      humansBleedBtn.active = isOp;
      this.listWidget.addEntry(new CoreConfigScreen.DoubleButtonEntry(bloodEnabledBtn, humansBleedBtn));
      this.addRenderableWidget(
         Button.builder(
               Component.translatable("gui.viltrumitecore.config.generic.save_back"),
               button -> {
                  if (isOp) {
                     try {
                        float baseDmg = Float.parseFloat(this.baseDamageField.getValue());
                        float dmgRed = Float.parseFloat(this.damageReductionField.getValue());
                        float dmgIgn = Float.parseFloat(this.damageIgnoreField.getValue());
                        float healFact = Float.parseFloat(this.healFactorField.getValue());
                        float punchDrop = Float.parseFloat(this.punchDropField.getValue());
                        float dashDrop = Float.parseFloat(this.dashDropField.getValue());
                        double spaceLimit = Double.parseDouble(this.spaceLimitField.getValue());
                        float meteorChance = Float.parseFloat(this.meteorSpawnChanceField.getValue());
                        double meteorDist = Double.parseDouble(this.meteorDistanceField.getValue());
                        float eventChance = Float.parseFloat(this.worldEventChanceField.getValue());
                        int eventCooldown = Integer.parseInt(this.worldEventCooldownField.getValue());
                        boolean askRace = ViltrumiteCoreConfig.INSTANCE.shouldAskRace;
                        boolean isDefault = ViltrumiteCoreConfig.INSTANCE.isViltrumiteByDefault;
                        boolean bEnabled = ViltrumiteCoreConfig.INSTANCE.bloodEnabled;
                        boolean hBleed = ViltrumiteCoreConfig.INSTANCE.shouldHumansBleed;
                        CoreMessages.sendToServer(
                           new CoreConfigSyncC2SPacket(
                              baseDmg,
                              dmgRed,
                              dmgIgn,
                              healFact,
                              punchDrop,
                              dashDrop,
                              askRace,
                              isDefault,
                              spaceLimit,
                              meteorChance,
                              meteorDist,
                              eventChance,
                              eventCooldown,
                              bEnabled,
                              hBleed
                           )
                        );
                     } catch (NumberFormatException var20) {
                     }
                  }

                  this.minecraft.setScreen(this.parent);
               }
            )
            .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
            .build()
      );
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
   }

   private abstract class ConfigListEntry extends Entry<CoreConfigScreen.ConfigListEntry> {
   }

   private class ConfigListWidget extends ContainerObjectSelectionList<CoreConfigScreen.ConfigListEntry> {
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

      public int addEntry(CoreConfigScreen.ConfigListEntry entry) {
         return super.addEntry(entry);
      }
   }

   private class DoubleButtonEntry extends CoreConfigScreen.ConfigListEntry {
      private final Button topButton;
      private final Button bottomButton;

      public DoubleButtonEntry(Button topButton, Button bottomButton) {
         this.topButton = topButton;
         this.bottomButton = bottomButton;
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         this.topButton.setX(left);
         this.topButton.setY(top + 4);
         this.topButton.render(guiGraphics, mouseX, mouseY, partialTick);
         this.bottomButton.setX(left);
         this.bottomButton.setY(top + 28);
         this.bottomButton.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      public List<? extends GuiEventListener> children() {
         return List.of(this.topButton, this.bottomButton);
      }

      public List<? extends NarratableEntry> narratables() {
         return List.of(this.topButton, this.bottomButton);
      }
   }

   private class SingleButtonEntry extends CoreConfigScreen.ConfigListEntry {
      private final Button button;

      public SingleButtonEntry(Button button) {
         this.button = button;
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         this.button.setX(left);
         this.button.setY(top + 8);
         this.button.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      public List<? extends GuiEventListener> children() {
         return Collections.singletonList(this.button);
      }

      public List<? extends NarratableEntry> narratables() {
         return Collections.singletonList(this.button);
      }
   }

   private class TextFieldEntry extends CoreConfigScreen.ConfigListEntry {
      private final Component label;
      private final EditBox textField;

      public TextFieldEntry(Component label, EditBox textField) {
         this.label = label;
         this.textField = textField;
      }

      public void render(
         GuiGraphics guiGraphics, int index, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick
      ) {
         guiGraphics.drawString(CoreConfigScreen.this.font, this.label, left, top + 4, 10526880, true);
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

package dev.baranhan.viltrumitecore.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.baranhan.viltrumitecore.ability.ViltrumiteAbilities;
import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.EquipAbilityC2SPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

public class ViltrumiteAbilityScreen extends Screen {
   private static final ResourceLocation TEXTURE = new ResourceLocation("viltrumitecore", "textures/gui/ability_menu.png");
   private static final ResourceLocation LOCK_ICON = new ResourceLocation("viltrumitecore", "textures/gui/ability/lock.png");
   private static final ResourceLocation PLUS_ICON = new ResourceLocation("viltrumitecore", "textures/gui/ability/plus.png");
   private final int backgroundWidth = 174;
   private final int backgroundHeight = 212;
   private int x;
   private int y;
   private ViltrumiteAbility draggedAbility = null;
   private int viewedPage = 0;

   public ViltrumiteAbilityScreen() {
      super(Component.translatable("gui.viltrumitecore.abilities.title"));
   }

   protected void init() {
      super.init();
      this.x = (this.width - 174) / 2;
      this.y = (this.height - 212) / 2;
      if (this.minecraft.player instanceof ViltrumiteAbilityUser abilityUser) {
         this.viewedPage = abilityUser.getActivePage();
      }
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      guiGraphics.fill(0, 0, this.width, this.height, -1073741824);
      guiGraphics.blit(TEXTURE, this.x, this.y, 0, 0, 174, 212);
      ViltrumiteAbility hoveredAbility = null;
      ViltrumiteAbilityUser abilityUser = (ViltrumiteAbilityUser)this.minecraft.player;

      for (int p = 0; p < 3; p++) {
         int btnX = this.x + 9 + 168 + 2;
         int btnY = this.y + 31 + p * 18;
         boolean isBtnHovered = this.isPointWithinBounds(btnX, btnY, 14, 14, (double)mouseX, (double)mouseY);
         boolean isSelected = p == this.viewedPage;
         int bgColor = isSelected ? -1862281472 : (isBtnHovered ? -1867863382 : 1879048192);
         int textColor = isSelected ? 16771584 : 16777215;
         guiGraphics.fill(btnX, btnY, btnX + 14, btnY + 14, bgColor);
         guiGraphics.drawString(this.font, String.valueOf(p + 1), btnX + 4, btnY + 3, textColor, true);
      }

      for (int i = 0; i < 9; i++) {
         int slotX = 0;
         int slotY = 0;
         String equippedId = "";
         if (i < 6) {
            slotX = this.x + 9 + i * 28;
            slotY = this.y + 31;
            equippedId = abilityUser.getAbilityInSlot(this.viewedPage * 6 + i);
            String keyName = "";
            switch (i) {
               case 0:
                  keyName = dev.baranhan.viltrumitecore.client.gui.KeyLabels.shortName(AbilityInputManager.abilityKey1);
                  break;
               case 1:
                  keyName = dev.baranhan.viltrumitecore.client.gui.KeyLabels.shortName(AbilityInputManager.abilityKey2);
                  break;
               case 2:
                  keyName = dev.baranhan.viltrumitecore.client.gui.KeyLabels.shortName(AbilityInputManager.abilityKey3);
                  break;
               case 3:
                  keyName = dev.baranhan.viltrumitecore.client.gui.KeyLabels.shortName(AbilityInputManager.abilityKey4);
                  break;
               case 4:
                  keyName = dev.baranhan.viltrumitecore.client.gui.KeyLabels.shortName(AbilityInputManager.abilityKey5);
                  break;
               case 5:
                  keyName = dev.baranhan.viltrumitecore.client.gui.KeyLabels.shortName(AbilityInputManager.abilityKey6);
            }

            int textWidth = this.font.width(keyName);
            float scale = 1.0F;
            float scaledWidth = (float)textWidth * scale;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((float)(slotX + 8) - scaledWidth / 2.0F, (float)(slotY - 20), 0.0F);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.drawString(this.font, keyName, 0, 0, 16777215, true);
            guiGraphics.pose().popPose();
         } else if (i == 6) {
            slotX = this.x + 48;
            slotY = this.y + 185;
            equippedId = "viltrumite:speed_lock";
         } else if (i == 7) {
            slotX = this.x + 79;
            slotY = this.y + 185;
            equippedId = "viltrumite:fast_takeoff";
         } else if (i == 8) {
            slotX = this.x + 110;
            slotY = this.y + 185;
            equippedId = "viltrumite:supersonic_flight";
         }

         if (!equippedId.isEmpty() && !dev.baranhan.viltrumitecore.hero.HeroRegistry.get(this.minecraft.player).ownsAbility(equippedId)) {
            equippedId = "";
         }

         boolean isHovered = this.isPointWithinBounds(slotX - 1, slotY - 1, 18, 18, (double)mouseX, (double)mouseY);
         if (isHovered) {
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, -2130706433);
         }

         if (!equippedId.isEmpty()) {
            ViltrumiteAbility ability = ViltrumiteAbilities.get(equippedId);
            if (ability != null) {
               if (isHovered && this.draggedAbility == null) {
                  hoveredAbility = ability;
                  guiGraphics.pose().pushPose();
                  guiGraphics.pose().translate((float)(slotX + 8), (float)(slotY + 8), 0.0F);
                  guiGraphics.pose().scale(1.15F, 1.15F, 1.0F);
                  guiGraphics.pose().translate((float)(-(slotX + 8)), (float)(-(slotY + 8)), 0.0F);
                  guiGraphics.blit(ability.getIcon(this.minecraft.player), slotX, slotY, 0.0F, 0.0F, 16, 16, 16, 16);
                  guiGraphics.pose().popPose();
               } else {
                  guiGraphics.blit(ability.getIcon(this.minecraft.player), slotX, slotY, 0.0F, 0.0F, 16, 16, 16, 16);
               }
            }
         } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 0.2F);
            guiGraphics.blit(PLUS_ICON, slotX, slotY, 0.0F, 0.0F, 16, 16, 16, 16);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
         }
      }

      List<ViltrumiteAbility> allAbilities = new ArrayList<>();
      for (ViltrumiteAbility ability : ViltrumiteAbilities.REGISTRY.values()) {
         if (dev.baranhan.viltrumitecore.hero.HeroRegistry.get(this.minecraft.player).ownsAbility(ability.getId())) {
            allAbilities.add(ability);
         }
      }

      for (int i = 0; i < 35; i++) {
         int col = i % 7;
         int row = i / 7;
         int poolX = this.x + 25 + col * 18;
         int poolY = this.y + 72 + row * 18;
         boolean isHoveredx = this.isPointWithinBounds(poolX - 1, poolY - 1, 18, 18, (double)mouseX, (double)mouseY);
         if (i < allAbilities.size()) {
            ViltrumiteAbility ability = allAbilities.get(i);
            if (isHoveredx && this.draggedAbility == null) {
               guiGraphics.fill(poolX, poolY, poolX + 16, poolY + 16, -2130706433);
               hoveredAbility = ability;
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate((float)(poolX + 8), (float)(poolY + 8), 0.0F);
               guiGraphics.pose().scale(1.15F, 1.15F, 1.0F);
               guiGraphics.pose().translate((float)(-(poolX + 8)), (float)(-(poolY + 8)), 0.0F);
               guiGraphics.blit(ability.getIcon(this.minecraft.player), poolX, poolY, 0.0F, 0.0F, 16, 16, 16, 16);
               guiGraphics.pose().popPose();
            } else {
               guiGraphics.blit(ability.getIcon(this.minecraft.player), poolX, poolY, 0.0F, 0.0F, 16, 16, 16, 16);
            }
         } else {
            guiGraphics.fill(poolX, poolY, poolX + 16, poolY + 16, 1073741824);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 0.2F);
            guiGraphics.blit(LOCK_ICON, poolX, poolY, 0.0F, 0.0F, 16, 16, 16, 16);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            if (isHoveredx && this.draggedAbility == null) {
               guiGraphics.fill(poolX, poolY, poolX + 16, poolY + 16, 1358954495);
            }
         }
      }

      if (this.draggedAbility != null) {
         guiGraphics.fill(mouseX - 6, mouseY - 6, mouseX + 10, mouseY + 10, 1610612736);
         guiGraphics.blit(this.draggedAbility.getIcon(this.minecraft.player), mouseX - 8, mouseY - 8, 0.0F, 0.0F, 16, 16, 16, 16);
      }

      if (hoveredAbility != null) {
         guiGraphics.renderComponentTooltip(this.font, hoveredAbility.getTooltip(), mouseX, mouseY);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         for (int p = 0; p < 3; p++) {
            int btnX = this.x + 9 + 168 + 2;
            int btnY = this.y + 31 + p * 18;
            if (this.isPointWithinBounds(btnX, btnY, 14, 14, mouseX, mouseY)) {
               this.viewedPage = p;
               this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.5F));
               return true;
            }
         }

         List<ViltrumiteAbility> allAbilities = new ArrayList<>();
         for (ViltrumiteAbility ability : ViltrumiteAbilities.REGISTRY.values()) {
            if (dev.baranhan.viltrumitecore.hero.HeroRegistry.get(this.minecraft.player).ownsAbility(ability.getId())) {
               allAbilities.add(ability);
            }
         }

         for (int i = 0; i < allAbilities.size(); i++) {
            int col = i % 7;
            int row = i / 7;
            int poolX = this.x + 25 + col * 18;
            int poolY = this.y + 72 + row * 18;
            if (this.isPointWithinBounds(poolX - 1, poolY - 1, 18, 18, mouseX, mouseY)) {
               ViltrumiteAbility target = allAbilities.get(i);
               if (!target.getId().equals("viltrumite:fast_takeoff")
                  && !target.getId().equals("viltrumite:supersonic_flight")
                  && !target.getId().equals("viltrumite:speed_lock")) {
                  this.draggedAbility = target;
                  this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                  return true;
               }

               return true;
            }
         }
      } else if (button == 1) {
         for (int ix = 0; ix < 6; ix++) {
            int slotX = this.x + 9 + ix * 28;
            int slotY = this.y + 31;
            if (this.isPointWithinBounds(slotX - 2, slotY - 2, 20, 20, mouseX, mouseY)) {
               int absoluteSlot = this.viewedPage * 6 + ix;
               if (!((ViltrumiteAbilityUser)this.minecraft.player).getAbilityInSlot(absoluteSlot).isEmpty()) {
                  this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.2F));
                  CoreMessages.sendToServer(new EquipAbilityC2SPacket(absoluteSlot, ""));
               }

               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0 && this.draggedAbility != null) {
         boolean equipped = false;

         for (int i = 0; i < 6; i++) {
            int slotX = this.x + 9 + i * 28;
            int slotY = this.y + 31;
            if (this.isPointWithinBounds(slotX - 2, slotY - 2, 20, 20, mouseX, mouseY)) {
               this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F));
               CoreMessages.sendToServer(new EquipAbilityC2SPacket(this.viewedPage * 6 + i, this.draggedAbility.getId()));
               equipped = true;
               break;
            }
         }

         if (!equipped) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8F));
         }

         this.draggedAbility = null;
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private boolean isPointWithinBounds(int boxX, int boxY, int boxWidth, int boxHeight, double mouseX, double mouseY) {
      return mouseX >= (double)boxX && mouseX < (double)(boxX + boxWidth) && mouseY >= (double)boxY && mouseY < (double)(boxY + boxHeight);
   }

}

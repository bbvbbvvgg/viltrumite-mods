package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.baranhan.viltrumitecore.ability.ViltrumiteAbilities;
import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ForgeGui.class})
public class ViltrumiteInGameHudMixin {
   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void renderViltrumiteHotbar(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      Player player = client.player;
      if (player != null && !client.options.hideGui) {
         if (player instanceof ViltrumiteAbilityUser abilityUser) {
            if (!dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).hasAbilityPanel(player)) {
               return;
            }

            int scaledWidth = client.getWindow().getGuiScaledWidth();
            int scaledHeight = client.getWindow().getGuiScaledHeight();
            int rightWidth = 62;
            int barHeight = 22;
            int vertWidth = 22;
            int vertHeight = 120;
            int padding = 5;
            int rightBarX = scaledWidth - rightWidth - padding;
            int barY = scaledHeight - barHeight - padding;
            int vertBarX = rightBarX + rightWidth - vertWidth;
            int vertBarY = barY - vertHeight;
            int activePage = abilityUser.getActivePage();
            int offset = activePage * 6;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            PanelStyle style = PanelStyles.of(player);
            PanelStyle.Bounds abilityBar = new PanelStyle.Bounds(vertBarX, vertBarY, vertWidth, vertHeight);
            style.drawFrames(guiGraphics, new PanelStyle.Bounds(rightBarX, barY, rightWidth, barHeight), abilityBar);

            for (int i = 0; i < 6; i++) {
               int slotX = vertBarX + 3;
               int slotY = vertBarY + 3 + i * 20;
               String abilityId = abilityUser.getAbilityInSlot(offset + i);
               String keyName = "";
               switch (i) {
                  case 0:
                     keyName = this.getShortKeyName(AbilityInputManager.abilityKey1.getTranslatedKeyMessage().getString());
                     break;
                  case 1:
                     keyName = this.getShortKeyName(AbilityInputManager.abilityKey2.getTranslatedKeyMessage().getString());
                     break;
                  case 2:
                     keyName = this.getShortKeyName(AbilityInputManager.abilityKey3.getTranslatedKeyMessage().getString());
                     break;
                  case 3:
                     keyName = this.getShortKeyName(AbilityInputManager.abilityKey4.getTranslatedKeyMessage().getString());
                     break;
                  case 4:
                     keyName = this.getShortKeyName(AbilityInputManager.abilityKey5.getTranslatedKeyMessage().getString());
                     break;
                  case 5:
                     keyName = this.getShortKeyName(AbilityInputManager.abilityKey6.getTranslatedKeyMessage().getString());
               }

               if (!keyName.isEmpty()) {
                  int textWidth = client.font.width(keyName);
                  int highlightX = slotX - textWidth - 6;
                  guiGraphics.fill(highlightX, slotY + 2, slotX - 2, slotY + 14, 1879048192);
                  guiGraphics.drawString(client.font, keyName, highlightX + 2, slotY + 4, 16777215, true);
               }

               ViltrumiteAbility ability = abilityId != null && !abilityId.isEmpty() ? ViltrumiteAbilities.get(abilityId) : null;
               style.drawSlot(guiGraphics, slotX, slotY, ability == null);
               if (ability != null) {
                  guiGraphics.blit(ability.getIcon(player), slotX, slotY, 0.0F, 0.0F, 16, 16, 16, 16);
                  if (ability.isGrey(player)) {
                     style.drawCooldown(guiGraphics, slotX, slotY);
                  }
               }
            }

            int indicatorX = vertBarX + 5;
            int indicatorY = vertBarY - 14;
            guiGraphics.fill(indicatorX, indicatorY, indicatorX + 13, indicatorY + 12, 1610612736);
            guiGraphics.drawString(client.font, String.valueOf(activePage + 1), indicatorX + 4, indicatorY + 2, style.accentColor(), true);
            String[] rightIds = new String[]{"viltrumite:speed_lock", "viltrumite:fast_takeoff", "viltrumite:supersonic_flight"};

            for (int i = 0; i < 3; i++) {
               int slotX = rightBarX + 3 + i * 20;
               int slotY = barY + 3;
               ViltrumiteAbility ability = ViltrumiteAbilities.get(rightIds[i]);
               boolean owned = ability != null && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).ownsAbility(rightIds[i]);
               style.drawSlot(guiGraphics, slotX, slotY, !owned);
               if (owned) {
                  guiGraphics.blit(ability.getIcon(player), slotX, slotY, 0.0F, 0.0F, 16, 16, 16, 16);
                  if (ability.isGrey(player)) {
                     style.drawCooldown(guiGraphics, slotX, slotY);
                  }
               }
            }

            dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot = player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer
               ? heroPlayer.getHeroSnapshot() : null;
            style.drawAttachments(guiGraphics, abilityBar, player,
               snapshot != null ? snapshot : dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot.EMPTY);
            RenderSystem.disableBlend();
         }
      }
   }

   @Unique
   private String getShortKeyName(String key) {
      if (key == null) {
         return "";
      } else {
         key = key.toUpperCase();
         if (key.contains("MOUSE") || key.contains("BUTTON")) {
            if (key.contains("1") || key.contains("LEFT")) {
               return "M1";
            }

            if (key.contains("2") || key.contains("RIGHT")) {
               return "M2";
            }

            if (key.contains("3") || key.contains("MIDDLE")) {
               return "M3";
            }

            if (key.contains("4")) {
               return "M4";
            }

            if (key.contains("5")) {
               return "M5";
            }
         }

         if (key.contains("SPACE")) {
            return "SPC";
         } else if (key.contains("SHIFT")) {
            return "SHFT";
         } else if (key.contains("CONTROL") || key.contains("CTRL")) {
            return "CTRL";
         } else if (key.contains("ALT")) {
            return "ALT";
         } else {
            return !key.contains("ENTER") && !key.contains("RETURN") ? key.replace("KEYBOARD", "").replace("KEY", "").replace(".", "").trim() : "ENTR";
         }
      }
   }
}

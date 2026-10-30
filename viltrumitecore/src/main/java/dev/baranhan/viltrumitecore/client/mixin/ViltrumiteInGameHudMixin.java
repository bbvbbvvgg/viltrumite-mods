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
      if (dev.baranhan.viltrumitecore.client.hero.BodyIsolationClient.active(player)) return;
      if (dev.baranhan.viltrumitecore.client.dio.RollerCinema.active()) return; // DIO's Road Roller cinematic owns the screen
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
            // Flight row: only the flight abilities this hero owns (Homelander has no speed lock, the
            // empty frame was noise). A hero owning none keeps the old three-slot row.
            String[] rightIds = viltrumitecore$flightRow(player);
            int flightWidth = rightIds.length * 20 + 2;
            int flightX = rightBarX + rightWidth - flightWidth;
            style.drawFrames(guiGraphics, new PanelStyle.Bounds(flightX, barY, flightWidth, barHeight), abilityBar);

            for (int i = 0; i < 6; i++) {
               int slotX = vertBarX + 3;
               int slotY = vertBarY + 3 + i * 20;
               String abilityId = abilityUser.getAbilityInSlot(offset + i);
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
            for (int i = 0; i < rightIds.length; i++) {
               int slotX = flightX + 3 + i * 20;
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
   private static final String[] VILTRUMITECORE$FLIGHT_IDS = {"viltrumite:speed_lock", "viltrumite:fast_takeoff", "viltrumite:supersonic_flight"};

   @Unique
   private static String[] viltrumitecore$flightRow(Player player) {
      var hero = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player);
      return dev.baranhan.viltrumitecore.client.gui.FlightRow.visible(VILTRUMITECORE$FLIGHT_IDS, hero::ownsAbility);
   }

}

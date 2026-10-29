package dev.baranhan.viltrumitecore.client;

import dev.baranhan.viltrumitecore.client.gui.ViltrumiteDashboardScreen;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.SwapAbilityBarC2SPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

public class AbilityInputManager {
   public static KeyMapping abilityKey1;
   public static KeyMapping abilityKey2;
   public static KeyMapping abilityKey3;
   public static KeyMapping abilityKey4;
   public static KeyMapping abilityKey5;
   public static KeyMapping abilityKey6;
   public static KeyMapping abilityMenuKey;
   public static KeyMapping dashboardKey;
   public static KeyMapping abilitySwapKey;
   /** Regulus: mark/unmark a heart carrier. Default MMB — wins over vanilla pick-block. */
   public static KeyMapping assignHeartKey;
   public static KeyMapping superJumpKey;

   public static void registerKeys(RegisterKeyMappingsEvent event) {
      abilityKey1 = new KeyMapping("key.viltrumitecore.ability_1", 82, "category.viltrumitecore.keys");
      abilityKey2 = new KeyMapping("key.viltrumitecore.ability_2", 89, "category.viltrumitecore.keys");
      abilityKey3 = new KeyMapping("key.viltrumitecore.ability_3", 90, "category.viltrumitecore.keys");
      abilityKey4 = new KeyMapping("key.viltrumitecore.ability_4", 86, "category.viltrumitecore.keys");
      abilityKey5 = new KeyMapping("key.viltrumitecore.ability_5", 66, "category.viltrumitecore.keys");
      abilityKey6 = new KeyMapping("key.viltrumitecore.ability_6", 78, "category.viltrumitecore.keys");
      abilityMenuKey = new KeyMapping("key.viltrumitecore.ability_menu", 75, "category.viltrumitecore.keys");
      dashboardKey = new KeyMapping("key.viltrumitecore.dashboard", 80, "category.viltrumitecore.keys");
      abilitySwapKey = new KeyMapping("key.viltrumitecore.ability_swap", 342, "category.viltrumitecore.keys");
      event.register(abilityKey1);
      event.register(abilityKey2);
      event.register(abilityKey3);
      event.register(abilityKey4);
      event.register(abilityKey5);
      event.register(abilityKey6);
      event.register(abilityMenuKey);
      event.register(dashboardKey);
      event.register(abilitySwapKey);
      assignHeartKey = new KeyMapping("key.viltrumitecore.assign_heart", net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,
         com.mojang.blaze3d.platform.InputConstants.Type.MOUSE, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "category.viltrumitecore.keys");
      event.register(assignHeartKey);
      // Id kept from v1.13.0 (renaming would drop players' bindings); the key is shared by every hero.
      superJumpKey = new KeyMapping("key.viltrumitecore.regulus_super_jump", net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,
         com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_G, "category.viltrumitecore.keys");
      event.register(superJumpKey);
   }

   /**
    * True while the local hero claims the heart key (MIDDLE) and it shares the
    * given mapping's key — the hero action then beats that vanilla mapping.
    */
   public static boolean heroKeyOwns(Minecraft client, KeyMapping other) {
      return assignHeartKey != null
         && client.player != null
         && !assignHeartKey.isUnbound()
         && assignHeartKey.getKey().equals(other.getKey())
         && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(client.player)
            .mouseAction(dev.baranhan.viltrumitecore.hero.MouseButton.MIDDLE, client.player) != null;
   }

   public static void tick(Minecraft client) {
      // Claimed mouse buttons (Regulus heart key, Iron Man LMB in flight) are
      // edge-tracked in HeroMouseInput, which also cancels the vanilla mapping.
      dev.baranhan.viltrumitecore.client.hero.HeroMouseInput.tick(client);
      while (abilitySwapKey.consumeClick()) {
         Player player = client.player;
         if (player == null || !dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).allowsAbilityPages(player)) {
            // A hero without pages ignores Alt; the rest of this tick's input (dashboard key) still runs.
            continue;
         }

         CoreMessages.sendToServer(new SwapAbilityBarC2SPacket());
         client.getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.get(), 1.1F, 0.3F));
      }

      while (dashboardKey.consumeClick()) {
         if (client.screen == null) {
            client.setScreen(new ViltrumiteDashboardScreen());
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }
      }
   }

   public static boolean consumeAbilityKeyPress(Player player, String targetAbilityId) {
      if (player instanceof ViltrumiteAbilityUser abilityUser) {
         int offset = abilityUser.getActivePage() * 6;
         if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset)) && abilityKey1.consumeClick()) {
            return true;
         } else if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 1)) && abilityKey2.consumeClick()) {
            return true;
         } else if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 2)) && abilityKey3.consumeClick()) {
            return true;
         } else if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 3)) && abilityKey4.consumeClick()) {
            return true;
         } else {
            return targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 4)) && abilityKey5.consumeClick()
               ? true
               : targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 5)) && abilityKey6.consumeClick();
         }
      } else {
         return false;
      }
   }

   public static boolean isAbilityKeyDown(Player player, String targetAbilityId) {
      if (player instanceof ViltrumiteAbilityUser abilityUser) {
         int offset = abilityUser.getActivePage() * 6;
         if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset)) && abilityKey1.isDown()) {
            return true;
         } else if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 1)) && abilityKey2.isDown()) {
            return true;
         } else if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 2)) && abilityKey3.isDown()) {
            return true;
         } else if (targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 3)) && abilityKey4.isDown()) {
            return true;
         } else {
            return targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 4)) && abilityKey5.isDown()
               ? true
               : targetAbilityId.equals(abilityUser.getAbilityInSlot(offset + 5)) && abilityKey6.isDown();
         }
      } else {
         return false;
      }
   }
}

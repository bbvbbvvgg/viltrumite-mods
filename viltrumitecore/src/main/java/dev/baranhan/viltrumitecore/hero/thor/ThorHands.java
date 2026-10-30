package dev.baranhan.viltrumitecore.hero.thor;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Mjölnir always sits in the right hand (its model is built for the right
 * arm, as in the source pack). Both sides: held items are synced.
 */
public final class ThorHands {
   private ThorHands() {
   }

   /** The item in the right hand: main hand for right-handed players, off hand otherwise. */
   public static ItemStack hammerHandItem(Player player) {
      return player.getMainArm() == HumanoidArm.RIGHT ? player.getMainHandItem() : player.getOffhandItem();
   }

   /**
    * Pure, the one rule for "Mjölnir really in the hand" on wire values
    * (both sides; the server twin over the world store is
    * ThorBridge.hammerHeld): attached (IN_HAND) and the right hand free. An
    * item in the right hand keeps it drawn on the belt (A-M2 / X5).
    */
   public static boolean holds(HammerState attached, boolean handFree) {
      return attached == HammerState.IN_HAND && handFree;
   }

   /**
    * Audit v3 (item 7): make the right hand free for Mjölnir without losing
    * the item. Right-handed: select an empty hotbar slot (the item stays where
    * it is); else move the item to an empty inventory slot. Left-handed (the
    * right hand is the off hand): move the off-hand item into the inventory.
    * False when there is no room (nothing changes then).
    */
   public static boolean clearForMjolnir(net.minecraft.server.level.ServerPlayer player) {
      net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
      if (player.getMainArm() == HumanoidArm.RIGHT) {
         int slot = emptyHotbarSlot(inventory, inventory.selected);
         if (slot >= 0) {
            inventory.selected = slot;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));
            return true;
         }

         return moveToInventory(inventory, inventory.selected);
      }

      return moveToInventory(inventory, net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND);
   }

   /** Pure: the nearest empty hotbar slot after {@code selected} (wrapping), or -1. */
   static int emptyHotbarSlot(net.minecraft.world.entity.player.Inventory inventory, int selected) {
      for (int step = 1; step <= 9; step++) {
         int slot = (selected + step) % 9;
         if (inventory.items.get(slot).isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private static boolean moveToInventory(net.minecraft.world.entity.player.Inventory inventory, int from) {
      ItemStack stack = inventory.getItem(from);
      for (int slot = 9; slot < inventory.items.size(); slot++) {
         if (inventory.items.get(slot).isEmpty()) {
            inventory.items.set(slot, stack);
            inventory.setItem(from, ItemStack.EMPTY);
            inventory.setChanged();
            return true;
         }
      }

      return false;
   }

   /** May Mjölnir be in the right hand now? */
   public static boolean free(Player player) {
      return hammerHandItem(player).isEmpty();
   }
}

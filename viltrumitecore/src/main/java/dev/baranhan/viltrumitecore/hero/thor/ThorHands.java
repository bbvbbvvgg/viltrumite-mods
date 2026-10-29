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

   /** May Mjölnir be in the right hand now? */
   public static boolean free(Player player) {
      return hammerHandItem(player).isEmpty();
   }
}

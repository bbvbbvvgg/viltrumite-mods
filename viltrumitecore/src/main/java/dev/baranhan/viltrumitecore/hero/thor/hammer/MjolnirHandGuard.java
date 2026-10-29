package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.bridge.HammerView;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Playtest fix: a picked-up item must not take Mjölnir's hand. Vanilla puts a
 * picked-up stack into the first empty slot, which is often the selected
 * (empty) slot where Mjölnir is held. The item then pushes the hammer to the
 * belt (ThorHands.free), and LMB/RMB become fists. Typical case: the blocks
 * that Thor's own craters drop.
 *
 * <p>Server only. Before the pickup we remember that the hammer was really in
 * the hand (ThorBridge.hammerHeld). After the pickup, when the item landed in
 * the selected slot, we move it to another inventory slot. When no other slot
 * has space, the item stays (the player chose a full inventory).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class MjolnirHandGuard {
   private static final Map<UUID, Boolean> HELD_BEFORE_PICKUP = new ConcurrentHashMap<>();

   private MjolnirHandGuard() {
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void beforePickup(EntityItemPickupEvent event) {
      if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }

      boolean guard = player.getMainArm() == HumanoidArm.RIGHT && ThorBridge.hammerHeld(player);
      if (guard) {
         HELD_BEFORE_PICKUP.put(player.getUUID(), Boolean.TRUE);
      } else {
         HELD_BEFORE_PICKUP.remove(player.getUUID());
      }
   }

   @SubscribeEvent
   public static void afterPickup(PlayerEvent.ItemPickupEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player) || HELD_BEFORE_PICKUP.remove(player.getUUID()) == null) {
         return;
      }

      HammerView view = ThorBridge.hammer(player);
      if (view == null || !view.inHand() || ThorHands.free(player)) {
         return;
      }

      Inventory inventory = player.getInventory();
      if (Inventory.isHotbarSlot(inventory.selected)) {
         moveOutOfSlot(inventory.items, inventory.selected, inventory.getMaxStackSize());
      }
   }

   /**
    * Pure: moves the stack in {@code slot} into the other slots (merge into
    * equal stacks first, then empty slots, in index order). What does not fit
    * stays in {@code slot}. Returns true when {@code slot} is empty after it.
    */
   public static boolean moveOutOfSlot(List<ItemStack> items, int slot, int inventoryMax) {
      ItemStack moving = items.get(slot);
      if (moving.isEmpty()) {
         return true;
      }

      for (int i = 0; i < items.size() && !moving.isEmpty(); i++) {
         ItemStack other = items.get(i);
         if (i != slot && !other.isEmpty() && ItemStack.isSameItemSameTags(other, moving)) {
            int room = Math.min(other.getMaxStackSize(), inventoryMax) - other.getCount();
            if (room > 0) {
               int moved = Math.min(room, moving.getCount());
               other.grow(moved);
               moving.shrink(moved);
            }
         }
      }

      for (int i = 0; i < items.size() && !moving.isEmpty(); i++) {
         if (i != slot && items.get(i).isEmpty()) {
            items.set(i, moving.copy());
            moving.setCount(0);
         }
      }

      if (moving.isEmpty()) {
         items.set(slot, ItemStack.EMPTY);
         return true;
      }

      return false;
   }
}

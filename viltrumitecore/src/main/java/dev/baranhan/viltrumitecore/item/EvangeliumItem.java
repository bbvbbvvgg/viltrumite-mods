package dev.baranhan.viltrumitecore.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Evangelium of the first Regulus kit. Regulus Reborn has no ritual (his ult is Kingdom of
 * One): the item stays registered so old saves keep their stacks, but it is an inert keepsake — it
 * does nothing on use and may be dropped like any book.
 */
public class EvangeliumItem extends Item {
   public EvangeliumItem(Item.Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      return InteractionResultHolder.pass(player.getItemInHand(hand));
   }
}

package dev.baranhan.viltrumitecore.item;

import dev.baranhan.viltrumitecore.hero.wish.WishSceneView;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Od Lagna's Wish: the one item of every transformation. RMB opens «чего ты желаешь?» on the client;
 * the item is never used up and never copied. The server decides the wish (WishRitual).
 */
public class OdLagnaWishItem extends Item {
   public OdLagnaWishItem(Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide() && !WishSceneView.active(player.getId())) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.wish.WishScreens.open(false));
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }

   @Override
   public Rarity getRarity(ItemStack stack) {
      return Rarity.EPIC;
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.viltrumitecore.od_lagna_wish.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      tooltip.add(Component.translatable("item.viltrumitecore.od_lagna_wish.use").withStyle(ChatFormatting.GOLD));
   }
}

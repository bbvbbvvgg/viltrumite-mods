package dev.baranhan.viltrumitecore.item;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.regulus.Evangelium;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusAbilities;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The bound Evangelium book: holding use channels the 60t ritual. It cannot be
 * thrown (onDroppedByPlayer) and death drops are stripped in HeroEvents. The
 * server is authoritative: beginRitual gates the cast; the client mirrors the
 * same check from its synced snapshot for instant feedback.
 */
public class EvangeliumItem extends Item {
   public EvangeliumItem(Item.Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (player instanceof ServerPlayer serverPlayer) {
         if (!Evangelium.beginRitual(serverPlayer)) {
            Evangelium.explainRefusal(serverPlayer);
            return InteractionResultHolder.fail(stack);
         }

         player.startUsingItem(hand);
         if (!player.isUsingItem()) {
            // Start event cancelled after the gate: no channel, so no cancel fee either.
            Evangelium.abortBegin(serverPlayer);
            return InteractionResultHolder.fail(stack);
         }

         return InteractionResultHolder.consume(stack);
      } else if (!clientUsable(player)) {
         return InteractionResultHolder.fail(stack);
      }

      player.startUsingItem(hand);
      return InteractionResultHolder.consume(stack);
   }

   private static boolean clientUsable(Player player) {
      if (!(player instanceof HeroPlayer heroPlayer) || heroPlayer.getHeroId() != HeroId.REGULUS) {
         return false;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      int cooldownSlot = RegulusAbilities.cooldownIndex(RegulusAbilities.EVANGELIUM);
      int cooldown = cooldownSlot >= 0 && cooldownSlot < snapshot.cooldowns().length ? snapshot.cooldowns()[cooldownSlot] : 0;
      return !snapshot.madness() && snapshot.ritualTicks() < 0 && cooldown <= 0;
   }

   @Override
   public int getUseDuration(ItemStack stack) {
      return RegulusRules.RITUAL_TICKS;
   }

   @Override
   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.NONE;
   }

   @Override
   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
      if (entity instanceof ServerPlayer serverPlayer) {
         Evangelium.finishBookUse(serverPlayer);
      }

      return stack;
   }

   @Override
   public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
      if (entity instanceof ServerPlayer serverPlayer) {
         Evangelium.interrupt(serverPlayer);
      }
   }

   /** The bound book cannot be thrown out of the inventory. */
   @Override
   public boolean onDroppedByPlayer(ItemStack item, Player player) {
      return false;
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }
}

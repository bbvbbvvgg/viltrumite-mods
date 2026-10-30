package dev.baranhan.viltrumitecore.hero.wish;

import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Server lifecycle of the wish ritual and the first-login gift. */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class WishEvents {
   /** PlayerPersisted flag: the one free Wish was given (kept across death and dimension copies). */
   public static final String GIFT_KEY = "ViltrumiteOdLagnaWishGiven";

   private WishEvents() {
   }

   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
         WishRitual.tick(player);
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
         WishRitual.abort(player, WishRitual.INTERRUPTED);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         WishRitual.forget(player);
      }
   }

   @SubscribeEvent
   public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         WishRitual.abort(player, WishRitual.INTERRUPTED);
      }
   }

   @SubscribeEvent
   public static void onStartTracking(PlayerEvent.StartTracking event) {
      if (event.getEntity() instanceof ServerPlayer tracker && event.getTarget() instanceof Player target) {
         WishRitual.startTracking(tracker, target);
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      WishRitual.clearAll();
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         giftOnce(player);
      }
   }

   /** Every player receives one Wish on the first login with this version; never again (no duplicates). */
   static void giftOnce(ServerPlayer player) {
      CompoundTag root = player.getPersistentData();
      CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
      if (persisted.getBoolean(GIFT_KEY)) {
         return;
      }
      persisted.putBoolean(GIFT_KEY, true);
      root.put(Player.PERSISTED_NBT_TAG, persisted);
      ItemStack stack = new ItemStack(ViltrumiteItems.OD_LAGNA_WISH.get());
      if (!player.getInventory().add(stack)) {
         player.drop(stack, false);
      }
   }
}

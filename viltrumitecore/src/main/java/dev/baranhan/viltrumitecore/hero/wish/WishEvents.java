package dev.baranhan.viltrumitecore.hero.wish;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Server lifecycle of the wish ritual. The item is not given out on login: creative tab, commands or loot only. */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class WishEvents {
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
}

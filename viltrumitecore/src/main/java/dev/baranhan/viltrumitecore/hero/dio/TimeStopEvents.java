package dev.baranhan.viltrumitecore.hero.dio;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Victim side of stopped time: whoever dies inside a field (player or mob) leaves
 * it at once, so the death plays and a respawned player (same UUID, same entity
 * id) is never held by the stale record; a player changing dimension is released.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class TimeStopEvents {
   private TimeStopEvents() {
   }

   @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
   public static void onDeath(LivingDeathEvent event) {
      if (!event.getEntity().level().isClientSide()) {
         DioTimeStop.releaseVictim(event.getEntity(), true);
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         DioTimeStop.releaseVictim(player, true);
      }
   }

   @SubscribeEvent
   public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         DioTimeStop.releaseVictim(player, false);
      }
   }
}

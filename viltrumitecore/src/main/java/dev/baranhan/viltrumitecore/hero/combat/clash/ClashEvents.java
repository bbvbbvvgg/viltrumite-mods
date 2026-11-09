package dev.baranhan.viltrumitecore.hero.combat.clash;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Drives the clash runtime: one pass per server tick at END (after the network tick ran every player, so every
 * beam reported this tick is in), cleanup on logout and on server stop (no static state survives a world).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ClashEvents {
   private static final int PRUNE_EVERY = 100;

   private ClashEvents() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      MinecraftServer server = event.getServer();
      if (server == null) {
         return;
      }

      for (ServerLevel level : server.getAllLevels()) {
         BeamStruggles.tick(level);
      }

      if (server.getTickCount() % PRUNE_EVERY == 0) {
         BeamStruggles.prune(server.overworld().getGameTime());
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         BeamStruggles.forget(player);
      }
   }

   @SubscribeEvent
   public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         ClashBeams.forget(player.getUUID());
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      BeamStruggles.clear();
   }
}

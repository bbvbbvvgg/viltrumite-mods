package dev.baranhan.viltrumitecore.hero.thor.hammer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Server hooks of the world hammer: the world tick, logins, lift interruptions, transient reset. */
@EventBusSubscriber(modid = "viltrumitecore")
public final class MjolnirEvents {
   private MjolnirEvents() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         MjolnirWorld.tick(server);
         MjolnirLift.tick(server);
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         MjolnirWorld.onLogin(player);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      MjolnirLift.stop(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void onHurt(LivingHurtEvent event) {
      MjolnirLift.interrupt(event.getEntity());
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      MjolnirLift.clear();
      MjolnirPull.clear();
      ThorTransform.clear();
   }
}

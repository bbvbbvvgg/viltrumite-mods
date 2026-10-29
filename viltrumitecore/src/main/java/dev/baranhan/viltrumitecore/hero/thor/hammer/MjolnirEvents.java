package dev.baranhan.viltrumitecore.hero.thor.hammer;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Server hooks of the world hammer: the world tick, logins, lift interruptions, transient reset, the operator recovery. */
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

   /**
    * {@code /mjolnir recover} (operators, level 2, also in production): the
    * one logical hammer out of any limbo, settled and called back to its
    * active Thor ({@link MjolnirWorld#forceRecover}). Never a second hammer.
    */
   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("mjolnir").requires(source -> source.hasPermission(RECOVER_PERMISSION))
         .then(Commands.literal("recover").executes(context -> {
            MjolnirWorld.Recovery result = MjolnirWorld.forceRecover(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("Mjolnir: " + result.name().toLowerCase(java.util.Locale.ROOT)), true);
            return result == MjolnirWorld.Recovery.NO_HAMMER ? 0 : 1;
         })));
   }

   static final int RECOVER_PERMISSION = 2;

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
      MjolnirWorld.clearTransient();
   }
}

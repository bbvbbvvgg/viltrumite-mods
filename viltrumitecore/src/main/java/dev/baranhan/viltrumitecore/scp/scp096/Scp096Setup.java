package dev.baranhan.viltrumitecore.scp.scp096;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/** SCP-096 module wiring (docs/design/2026-11-05-scp096-design.md): sounds, entity, spawner, commands. */
public final class Scp096Setup {
   private Scp096Setup() {
   }

   public static void init(IEventBus modBus) {
      Scp096Sounds.init();
      Scp096Entities.register(modBus);
      MinecraftForge.EVENT_BUS.addListener(Scp096Setup::onCommands);
      MinecraftForge.EVENT_BUS.addListener(Scp096Setup::onServerTick);
      MinecraftForge.EVENT_BUS.addListener(Scp096Setup::onJoin);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, Scp096Setup::onDeath);
      MinecraftForge.EVENT_BUS.addListener(Scp096Setup::onLogin);
   }

   private static void onCommands(RegisterCommandsEvent event) {
      Scp096Commands.register(event.getDispatcher());
   }

   private static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
         Scp096Spawner.tick(event.getServer());
      }
   }

   private static void onJoin(EntityJoinLevelEvent event) {
      if (!event.getLevel().isClientSide() && event.getEntity() instanceof Scp096Entity e) {
         if (!Scp096Spawner.admit(e)) {
            event.setCanceled(true);
         }
      }
   }

   private static void onDeath(LivingDeathEvent event) {
      if (!event.isCanceled()) {
         Scp096Entity.onDeath(event.getEntity());
      }
   }

   private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         Scp096Entity.onLogin(sp);
      }
   }
}

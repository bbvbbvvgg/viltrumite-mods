package dev.baranhan.viltrumitecore.t1000;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** T-1000 module wiring (docs/design/2026-11-06-t1000-design.md): sounds, entity, scheduler, commands. */
public final class T1000Setup {
   private T1000Setup() {
   }

   public static void init(IEventBus modBus) {
      T1000Sounds.init();
      T1000Entities.register(modBus);
      MinecraftForge.EVENT_BUS.addListener(T1000Setup::onCommands);
      MinecraftForge.EVENT_BUS.addListener(T1000Setup::onServerTick);
   }

   private static void onCommands(RegisterCommandsEvent event) {
      T1000Commands.register(event.getDispatcher());
   }

   private static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
         T1000Spawner.tick(event.getServer());
      }
   }
}

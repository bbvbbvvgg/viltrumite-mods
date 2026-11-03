package dev.baranhan.viltrumitecore.client.t1000;

import dev.baranhan.viltrumitecore.t1000.T1000ClientHooks;
import dev.baranhan.viltrumitecore.t1000.T1000Entities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Mod-bus client registration of the T-1000: its renderer and the droplet effects behind its entity events. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class T1000ClientSetup {
   private T1000ClientSetup() {
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      T1000ClientHooks.install(T1000Droplets::onEvent);
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(T1000Entities.T1000.get(), T1000Renderer::new);
   }
}

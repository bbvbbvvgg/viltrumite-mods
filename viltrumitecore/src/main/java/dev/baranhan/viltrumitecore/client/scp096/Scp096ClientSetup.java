package dev.baranhan.viltrumitecore.client.scp096;

import dev.baranhan.viltrumitecore.scp.scp096.Scp096Entities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Mod-bus client registration of SCP-096: the renderer and the optional pack of the player's own SCP:CB sounds. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class Scp096ClientSetup {
   private Scp096ClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(Scp096Entities.SCP096.get(), Scp096Renderer::new);
   }

   @SubscribeEvent
   public static void onAddPackFinders(AddPackFindersEvent event) {
      Scp096OriginalSounds.onAddPackFinders(event);
   }
}

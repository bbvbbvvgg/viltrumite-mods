package dev.baranhan.viltrumitecore.client.godzilla;

import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Mod-bus client registration of the kaiju renderer (the hit zones are parts of it and draw nothing). */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class GodzillaClientSetup {
   private GodzillaClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(GodzillaEntities.GODZILLA.get(), GodzillaRenderer::new);
   }
}

package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleRenderer;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Mod-bus client registration of the Hell Cycle renderer. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class GhostRiderClientSetup {
   private GhostRiderClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(ViltrumiteEntities.HELL_CYCLE.get(), HellCycleRenderer::new);
   }
}

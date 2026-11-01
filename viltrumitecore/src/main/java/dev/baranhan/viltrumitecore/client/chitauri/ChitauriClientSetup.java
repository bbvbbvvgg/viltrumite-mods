package dev.baranhan.viltrumitecore.client.chitauri;

import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Mod-bus client registration of the Chitauri renderers. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class ChitauriClientSetup {
   private ChitauriClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(ChitauriEntities.SOLDIER.get(), ChitauriSoldierRenderer::new);
      event.registerEntityRenderer(ChitauriEntities.CHARIOT.get(), ChitauriChariotRenderer::new);
      event.registerEntityRenderer(ChitauriEntities.LEVIATHAN.get(), ChitauriLeviathanRenderer::new);
      event.registerEntityRenderer(ChitauriEntities.BOLT.get(), ChitauriBoltRenderer::new);
      event.registerEntityRenderer(ChitauriEntities.CAPTOR.get(), c -> new ChitauriEliteRenderer<>(c, ChitauriEliteRenderer.CAPTOR));
      event.registerEntityRenderer(ChitauriEntities.HEAVY.get(), c -> new ChitauriEliteRenderer<>(c, ChitauriEliteRenderer.HEAVY));
      event.registerEntityRenderer(ChitauriEntities.COMMANDER.get(), c -> new ChitauriEliteRenderer<>(c, ChitauriEliteRenderer.COMMANDER));
      event.registerEntityRenderer(ChitauriEntities.INTERCEPTOR.get(), ChitauriInterceptorRenderer::new);
      event.registerEntityRenderer(ChitauriEntities.SIEGE.get(), ChitauriSiegeRenderer::new);
      event.registerEntityRenderer(ChitauriEntities.BEACON.get(), ChitauriBeaconRenderer::new);
   }
}

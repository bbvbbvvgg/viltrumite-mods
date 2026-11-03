package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.hero.venom.boss.VenomEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Mod-bus client registration of Venom: the boss's renderer. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class VenomClientSetup {
   private VenomClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(VenomEntities.VENOM_BOSS.get(), VenomBossRenderer::new);
   }
}

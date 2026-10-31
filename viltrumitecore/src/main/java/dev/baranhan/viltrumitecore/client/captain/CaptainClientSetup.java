package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Mod-bus client registration of the shield, the Avengers, their projectiles and the Quinjet. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class CaptainClientSetup {
   private CaptainClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(ViltrumiteEntities.THROWN_SHIELD.get(), ThrownShieldRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.AVENGER.get(), AvengerRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.AVENGER_PROJECTILE.get(), AvengerProjectileRenderer::new);
      event.registerEntityRenderer(ViltrumiteEntities.QUINJET.get(), QuinjetRenderer::new);
   }
}

package dev.baranhan.viltrumitecore.client.ultron;

import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.entity.ultron.UltronEntities;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronVoice;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Mod-bus client registration of the uprising: the machine, node and projectile renderers and Ultron's subtitle portrait. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class UltronClientSetup {
   private UltronClientSetup() {
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(UltronEntities.DRONE.get(), UltronDroneRenderer::new);
      event.registerEntityRenderer(UltronEntities.SENTRY.get(), UltronSentryRenderer::new);
      event.registerEntityRenderer(UltronEntities.BRUTE.get(), UltronBruteRenderer::new);
      event.registerEntityRenderer(UltronEntities.INTERCEPTOR.get(), UltronInterceptorRenderer::new);
      event.registerEntityRenderer(UltronEntities.ENGINEER.get(), UltronEngineerRenderer::new);
      event.registerEntityRenderer(UltronEntities.PRIME.get(), UltronPrimeRenderer::new);
      event.registerEntityRenderer(UltronEntities.NODE.get(), UltronNodeRenderer::new);
      event.registerEntityRenderer(UltronEntities.BOLT.get(), UltronBoltRenderer::new);
      event.registerEntityRenderer(UltronEntities.MISSILE.get(), UltronMissileRenderer::new);
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> HeroPortraits.registerVoice(UltronVoice.VOICE, UltronPortrait.INSTANCE));
   }
}

package dev.baranhan.viltrumitecore.client.thanos.war;

import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWar;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Mod-bus client registration of the war: the Black Order and debris renderers, the speakers' portraits. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class ThanosWarClientSetup {
   private ThanosWarClientSetup() {
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
         HeroPortraits.registerVoice(ThanosWar.VOICE_THANOS, ThanosWarPortrait.THANOS);
         HeroPortraits.registerVoice(ThanosWar.VOICE_MAW, ThanosWarPortrait.MAW);
         HeroPortraits.registerVoice(ThanosWar.VOICE_CULL, ThanosWarPortrait.CULL);
      });
   }

   @SubscribeEvent
   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(ViltrumiteEntities.EBONY_MAW.get(), c -> new BlackOrderRenderer<>(c, BlackOrderRenderer.MAW));
      event.registerEntityRenderer(ViltrumiteEntities.CULL_OBSIDIAN.get(), c -> new BlackOrderRenderer<>(c, BlackOrderRenderer.CULL));
      event.registerEntityRenderer(ViltrumiteEntities.WAR_DEBRIS.get(), WarDebrisRenderer::new);
   }
}

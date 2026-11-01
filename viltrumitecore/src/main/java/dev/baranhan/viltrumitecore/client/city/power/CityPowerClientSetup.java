package dev.baranhan.viltrumitecore.client.city.power;

import dev.baranhan.viltrumitecore.block.ViltrumiteBlocks;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Mod-bus client registration of Living City 3.0: the dark fixture tint and the civil defence subtitle portrait. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.MOD, value = {Dist.CLIENT})
public final class CityPowerClientSetup {
   /** Multiplier of the dark fixtures: the lamp texture at about a quarter of its brightness, slightly cold. */
   public static final int DARK_TINT = 0x45484E;

   private CityPowerClientSetup() {
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      HeroPortraits.registerVoice("city", CityDefensePortrait.INSTANCE);
   }

   @SubscribeEvent
   public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
      event.register((state, level, pos, tint) -> tint == 0 ? DARK_TINT : 0xFFFFFF, ViltrumiteBlocks.DARK_FIXTURE.get(), ViltrumiteBlocks.DARK_LANTERN.get(),
         ViltrumiteBlocks.DARK_END_ROD.get());
   }
}

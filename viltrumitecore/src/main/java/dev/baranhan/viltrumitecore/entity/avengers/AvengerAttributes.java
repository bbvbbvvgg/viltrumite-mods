package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Mod bus: attributes of the summoned Avengers (per-kind values are set in {@link AvengerEntity#setup}). */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD)
public final class AvengerAttributes {
   private AvengerAttributes() {
   }

   @SubscribeEvent
   public static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(ViltrumiteEntities.AVENGER.get(), AvengerEntity.createAttributes().build());
   }
}

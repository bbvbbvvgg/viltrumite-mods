package dev.baranhan.viltrumitecore.entity.thanos;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Mod bus: Thanos's attributes (the event scales his max health on spawn). */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD)
public final class ThanosAttributes {
   private ThanosAttributes() {
   }

   @SubscribeEvent
   public static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(ViltrumiteEntities.THANOS.get(), ThanosEntity.createAttributes().build());
   }
}

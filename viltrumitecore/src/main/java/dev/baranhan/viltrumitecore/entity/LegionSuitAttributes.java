package dev.baranhan.viltrumitecore.entity;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Mod bus: attributes of the Iron Legion suit (its durability is the roster's, not the health). */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD)
public final class LegionSuitAttributes {
   private LegionSuitAttributes() {
   }

   @SubscribeEvent
   public static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(ViltrumiteEntities.LEGION_SUIT.get(), LegionSuitEntity.createAttributes().build());
   }
}

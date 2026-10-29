package dev.baranhan.viltrumitecore.entity.jinwoo;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Mod bus: attributes of the shadow soldier (per-kind values are set on spawn). */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD)
public final class ShadowAttributes {
   private ShadowAttributes() {
   }

   @SubscribeEvent
   public static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(ViltrumiteEntities.SHADOW_SOLDIER.get(), ShadowSoldierEntity.createAttributes().build());
   }
}

package dev.baranhan.viltrumitecore.scp.scp106;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity register of SCP-106 (its own DeferredRegister, like SCP-096). */
public final class Scp106Entities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<Scp106Entity>> SCP106 = ENTITIES.register("scp106",
      () -> EntityType.Builder.<Scp106Entity>of(Scp106Entity::new, MobCategory.MISC).sized(Scp106Rules.WIDTH, Scp106Rules.HEIGHT)
         .clientTrackingRange(10).updateInterval(1).fireImmune().build("scp106"));

   private Scp106Entities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(Scp106Entities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(SCP106.get(), Scp106Entity.createAttributes().build());
   }
}

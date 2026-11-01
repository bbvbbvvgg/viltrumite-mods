package dev.baranhan.viltrumitecore.scp.scp096;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity register of SCP-096 (its own DeferredRegister, like the Chitauri and Ultron modules). */
public final class Scp096Entities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<Scp096Entity>> SCP096 = ENTITIES.register("scp096",
      () -> EntityType.Builder.<Scp096Entity>of(Scp096Entity::new, MobCategory.MISC).sized(Scp096Rules.WIDTH, Scp096Rules.HEIGHT)
         .clientTrackingRange(16).updateInterval(1).fireImmune().build("scp096"));

   private Scp096Entities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(Scp096Entities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(SCP096.get(), Scp096Entity.createAttributes().build());
   }
}

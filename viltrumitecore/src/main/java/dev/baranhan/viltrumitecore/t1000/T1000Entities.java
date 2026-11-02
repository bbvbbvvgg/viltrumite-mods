package dev.baranhan.viltrumitecore.t1000;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity register of the T-1000. Same size and tracking as a citizen (nothing on the wire tells them apart);
 * {@code noSave}: the body is never written into a chunk, {@link T1000SavedData} remembers it instead.
 */
public final class T1000Entities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<T1000Entity>> T1000 = ENTITIES.register("t1000",
      () -> EntityType.Builder.<T1000Entity>of(T1000Entity::new, MobCategory.MISC).sized(T1000Rules.WIDTH, T1000Rules.HEIGHT)
         .clientTrackingRange(8).updateInterval(2).noSave().build("t1000"));

   private T1000Entities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(T1000Entities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(T1000.get(), T1000Entity.createT1000Attributes().build());
   }
}

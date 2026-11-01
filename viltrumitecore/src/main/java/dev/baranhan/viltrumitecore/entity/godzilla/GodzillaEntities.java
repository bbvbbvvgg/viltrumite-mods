package dev.baranhan.viltrumitecore.entity.godzilla;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Godzilla city catastrophe entity register (docs/plans/2026-11-05-godzilla-city-catastrophe.md). The kaiju is
 * never saved (the event keeps its state in memory and ends on a restart) and has no spawn egg, no natural
 * spawn and no spawn placement: only {@code /godzilla start} creates it. MISC keeps it out of the mob caps.
 * The type box is small (the feet); the body is the sixteen hit zones ({@link GodzillaPart}).
 */
public final class GodzillaEntities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<GodzillaEntity>> GODZILLA = ENTITIES.register("godzilla",
      () -> EntityType.Builder.<GodzillaEntity>of(GodzillaEntity::new, MobCategory.MISC).sized(6.0F, 6.0F).clientTrackingRange(32).updateInterval(1)
         .fireImmune().noSave().noSummon().build("godzilla"));

   private GodzillaEntities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(GodzillaEntities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(GODZILLA.get(), GodzillaEntity.createAttributes().build());
   }
}

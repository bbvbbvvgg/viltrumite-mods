package dev.baranhan.viltrumitecore.entity.ultron;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Ultron uprising entity register (docs/plans/2026-11-01-ultron-uprising.md). None is saved as an entity:
 * the event keeps its own state ({@code UltronSavedData}) and rebuilds the bodies in ticking chunks.
 * MISC category keeps them out of the natural monster cap.
 */
public final class UltronEntities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<UltronDrone>> DRONE = ENTITIES.register("ultron_drone",
      () -> EntityType.Builder.<UltronDrone>of(UltronDrone::new, MobCategory.MISC).sized(0.8F, 0.7F).clientTrackingRange(10).updateInterval(2)
         .fireImmune().noSave().build("ultron_drone"));
   public static final RegistryObject<EntityType<UltronSentry>> SENTRY = ENTITIES.register("ultron_sentry",
      () -> EntityType.Builder.<UltronSentry>of(UltronSentry::new, MobCategory.MISC).sized(0.8F, 2.2F).clientTrackingRange(10).updateInterval(2)
         .noSave().build("ultron_sentry"));
   public static final RegistryObject<EntityType<UltronBrute>> BRUTE = ENTITIES.register("ultron_brute",
      () -> EntityType.Builder.<UltronBrute>of(UltronBrute::new, MobCategory.MISC).sized(1.7F, 3.0F).clientTrackingRange(12).updateInterval(2)
         .fireImmune().noSave().build("ultron_brute"));
   public static final RegistryObject<EntityType<UltronInterceptor>> INTERCEPTOR = ENTITIES.register("ultron_interceptor",
      () -> EntityType.Builder.<UltronInterceptor>of(UltronInterceptor::new, MobCategory.MISC).sized(1.1F, 1.2F).clientTrackingRange(12).updateInterval(1)
         .fireImmune().noSave().build("ultron_interceptor"));
   public static final RegistryObject<EntityType<UltronEngineer>> ENGINEER = ENTITIES.register("ultron_engineer",
      () -> EntityType.Builder.<UltronEngineer>of(UltronEngineer::new, MobCategory.MISC).sized(1.2F, 1.1F).clientTrackingRange(10).updateInterval(2)
         .noSave().build("ultron_engineer"));
   public static final RegistryObject<EntityType<UltronPrime>> PRIME = ENTITIES.register("ultron_prime",
      () -> EntityType.Builder.<UltronPrime>of(UltronPrime::new, MobCategory.MISC).sized(0.9F, 2.9F).clientTrackingRange(16).updateInterval(1)
         .fireImmune().noSave().build("ultron_prime"));
   public static final RegistryObject<EntityType<UltronNodeEntity>> NODE = ENTITIES.register("ultron_node",
      () -> EntityType.Builder.<UltronNodeEntity>of(UltronNodeEntity::new, MobCategory.MISC).sized(2.4F, 4.4F).clientTrackingRange(16).updateInterval(5)
         .fireImmune().noSave().build("ultron_node"));
   public static final RegistryObject<EntityType<UltronBolt>> BOLT = ENTITIES.register("ultron_bolt",
      () -> EntityType.Builder.<UltronBolt>of(UltronBolt::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(10)
         .fireImmune().noSave().build("ultron_bolt"));
   public static final RegistryObject<EntityType<UltronMissile>> MISSILE = ENTITIES.register("ultron_missile",
      () -> EntityType.Builder.<UltronMissile>of(UltronMissile::new, MobCategory.MISC).sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(2)
         .fireImmune().noSave().build("ultron_missile"));

   private UltronEntities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(UltronEntities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(DRONE.get(), UltronDrone.createAttributes().build());
      event.put(SENTRY.get(), UltronSentry.createAttributes().build());
      event.put(BRUTE.get(), UltronBrute.createAttributes().build());
      event.put(INTERCEPTOR.get(), UltronInterceptor.createAttributes().build());
      event.put(ENGINEER.get(), UltronEngineer.createAttributes().build());
      event.put(PRIME.get(), UltronPrime.createAttributes().build());
      event.put(NODE.get(), UltronNodeEntity.createAttributes().build());
   }
}

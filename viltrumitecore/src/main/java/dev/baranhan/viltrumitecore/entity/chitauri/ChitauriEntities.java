package dev.baranhan.viltrumitecore.entity.chitauri;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Chitauri entity register (docs/plans/2026-10-31-chitauri-invasion.md). None is saved: the
 * invasion is memory-only, a restart leaves only the broken blocks. MISC category keeps them
 * out of the natural monster cap.
 */
public final class ChitauriEntities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<ChitauriSoldier>> SOLDIER = ENTITIES.register("chitauri_soldier",
      () -> EntityType.Builder.<ChitauriSoldier>of(ChitauriSoldier::new, MobCategory.MISC).sized(0.7F, 1.95F).clientTrackingRange(10).updateInterval(2)
         .noSave().build("chitauri_soldier"));
   public static final RegistryObject<EntityType<ChitauriChariot>> CHARIOT = ENTITIES.register("chitauri_chariot",
      () -> EntityType.Builder.<ChitauriChariot>of(ChitauriChariot::new, MobCategory.MISC).sized(2.6F, 1.3F).clientTrackingRange(14).updateInterval(1)
         .fireImmune().noSave().build("chitauri_chariot"));
   public static final RegistryObject<EntityType<ChitauriLeviathan>> LEVIATHAN = ENTITIES.register("chitauri_leviathan",
      () -> EntityType.Builder.<ChitauriLeviathan>of(ChitauriLeviathan::new, MobCategory.MISC).sized(5.0F, 4.0F).clientTrackingRange(16).updateInterval(1)
         .fireImmune().noSave().build("chitauri_leviathan"));
   public static final RegistryObject<EntityType<ChitauriBolt>> BOLT = ENTITIES.register("chitauri_bolt",
      () -> EntityType.Builder.<ChitauriBolt>of(ChitauriBolt::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(10)
         .fireImmune().noSave().build("chitauri_bolt"));

   // ---- city war units (docs/plans/2026-11-04-chitauri-evolution.md) ----
   public static final RegistryObject<EntityType<ChitauriCaptor>> CAPTOR = ENTITIES.register("chitauri_captor",
      () -> EntityType.Builder.<ChitauriCaptor>of(ChitauriCaptor::new, MobCategory.MISC).sized(0.7F, 1.95F).clientTrackingRange(10).updateInterval(2)
         .noSave().build("chitauri_captor"));
   public static final RegistryObject<EntityType<ChitauriHeavy>> HEAVY = ENTITIES.register("chitauri_heavy",
      () -> EntityType.Builder.<ChitauriHeavy>of(ChitauriHeavy::new, MobCategory.MISC).sized(1.0F, 2.5F).clientTrackingRange(10).updateInterval(2)
         .noSave().build("chitauri_heavy"));
   public static final RegistryObject<EntityType<ChitauriCommander>> COMMANDER = ENTITIES.register("chitauri_commander",
      () -> EntityType.Builder.<ChitauriCommander>of(ChitauriCommander::new, MobCategory.MISC).sized(0.75F, 2.2F).clientTrackingRange(10).updateInterval(2)
         .noSave().build("chitauri_commander"));
   public static final RegistryObject<EntityType<ChitauriInterceptor>> INTERCEPTOR = ENTITIES.register("chitauri_interceptor",
      () -> EntityType.Builder.<ChitauriInterceptor>of(ChitauriInterceptor::new, MobCategory.MISC).sized(1.2F, 1.0F).clientTrackingRange(14).updateInterval(1)
         .fireImmune().noSave().build("chitauri_interceptor"));
   public static final RegistryObject<EntityType<ChitauriSiege>> SIEGE = ENTITIES.register("chitauri_siege",
      () -> EntityType.Builder.<ChitauriSiege>of(ChitauriSiege::new, MobCategory.MISC).sized(2.6F, 1.8F).clientTrackingRange(14).updateInterval(1)
         .fireImmune().noSave().build("chitauri_siege"));
   public static final RegistryObject<EntityType<ChitauriBeacon>> BEACON = ENTITIES.register("chitauri_beacon",
      () -> EntityType.Builder.<ChitauriBeacon>of(ChitauriBeacon::new, MobCategory.MISC).sized(1.0F, 2.6F).clientTrackingRange(12).updateInterval(5)
         .fireImmune().noSave().build("chitauri_beacon"));

   private ChitauriEntities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(ChitauriEntities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(SOLDIER.get(), ChitauriSoldier.createAttributes().build());
      event.put(CHARIOT.get(), ChitauriChariot.createAttributes().build());
      event.put(LEVIATHAN.get(), ChitauriLeviathan.createAttributes().build());
      event.put(CAPTOR.get(), ChitauriCaptor.createAttributes().build());
      event.put(HEAVY.get(), ChitauriHeavy.createAttributes().build());
      event.put(COMMANDER.get(), ChitauriCommander.createAttributes().build());
      event.put(INTERCEPTOR.get(), ChitauriInterceptor.createAttributes().build());
      event.put(SIEGE.get(), ChitauriSiege.createAttributes().build());
      event.put(BEACON.get(), ChitauriBeacon.createAttributes().build());
   }
}

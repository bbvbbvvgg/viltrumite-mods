package dev.baranhan.viltrumitecore.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ViltrumiteEntities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<MeteorEntity>> METEOR = ENTITIES.register(
      "meteor", () -> Builder.of(MeteorEntity::new, MobCategory.MISC).sized(3.0F, 3.0F).clientTrackingRange(10).updateInterval(1).build("meteor")
   );
   public static final RegistryObject<EntityType<BloodDropEntity>> BLOOD_DROP = ENTITIES.register(
      "blood_drop",
      () -> Builder.of(BloodDropEntity::new, MobCategory.MISC).sized(0.12F, 0.12F).clientTrackingRange(4).updateInterval(1).fireImmune().build("blood_drop")
   );

   public static final RegistryObject<EntityType<RepulsorBlastEntity>> REPULSOR_BLAST = ENTITIES.register(
      "repulsor_blast",
      () -> Builder.<RepulsorBlastEntity>of(RepulsorBlastEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("repulsor_blast")
   );
   public static final RegistryObject<EntityType<MicroMissileEntity>> MICRO_MISSILE = ENTITIES.register(
      "micro_missile",
      () -> Builder.<MicroMissileEntity>of(MicroMissileEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().build("micro_missile")
   );

   public static final RegistryObject<EntityType<FlareEntity>> FLARE = ENTITIES.register(
      "flare",
      () -> Builder.<FlareEntity>of(FlareEntity::new, MobCategory.MISC).sized(0.2F, 0.2F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("flare")
   );

   public static final RegistryObject<EntityType<VeronicaPodEntity>> VERONICA_POD = ENTITIES.register(
      "veronica_pod", () -> EntityType.Builder.<VeronicaPodEntity>of(VeronicaPodEntity::new, MobCategory.MISC).sized(1.6F, 3.0F).clientTrackingRange(16).updateInterval(1).build("veronica_pod")
   );
   public static final RegistryObject<EntityType<EmptySuitEntity>> EMPTY_SUIT = ENTITIES.register(
      "empty_suit", () -> EntityType.Builder.<EmptySuitEntity>of(EmptySuitEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(2).build("empty_suit")
   );
   public static final RegistryObject<EntityType<SuitDebrisEntity>> SUIT_DEBRIS = ENTITIES.register(
      "suit_debris", () -> EntityType.Builder.<SuitDebrisEntity>of(SuitDebrisEntity::new, MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(2).build("suit_debris")
   );
   public static final RegistryObject<EntityType<RocketFistEntity>> ROCKET_FIST = ENTITIES.register(
      "rocket_fist", () -> EntityType.Builder.<RocketFistEntity>of(RocketFistEntity::new, MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).build("rocket_fist")
   );

   /** Session A: the one world Mjölnir's body (never saved: MjolnirWorld respawns it from the SavedData record). */
   public static final RegistryObject<EntityType<dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity>> MJOLNIR = ENTITIES.register(
      "mjolnir",
      () -> EntityType.Builder.<dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity>of(dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity::new,
            MobCategory.MISC)
         .sized(0.7F, 0.7F).clientTrackingRange(16).updateInterval(1).fireImmune().noSave().noSummon().build("mjolnir")
   );

   /** DIO's thrown knife (hero/dio, spec docs/design/2026-10-28-dio-design.md). */
   public static final RegistryObject<EntityType<dev.baranhan.viltrumitecore.hero.dio.DioKnifeEntity>> DIO_KNIFE = ENTITIES.register(
      "dio_knife",
      () -> Builder.<dev.baranhan.viltrumitecore.hero.dio.DioKnifeEntity>of(dev.baranhan.viltrumitecore.hero.dio.DioKnifeEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("dio_knife")
   );

   /** Jinwoo's army (spec docs/design/2026-10-28-jinwoo-design.md §6): never saved to the world. */
   public static final RegistryObject<EntityType<dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity>> SHADOW_SOLDIER = ENTITIES.register(
      "shadow_soldier", () -> EntityType.Builder.<dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity>of(dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity::new, MobCategory.MISC)
         .sized(0.62F, 1.9F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().build("shadow_soldier")
   );

   /** Living city pedestrian (city/citizen): never saved, the city population manager refills the streets. */
   public static final RegistryObject<EntityType<dev.baranhan.viltrumitecore.city.citizen.CitizenEntity>> CITIZEN = ENTITIES.register(
      "citizen", () -> EntityType.Builder.<dev.baranhan.viltrumitecore.city.citizen.CitizenEntity>of(dev.baranhan.viltrumitecore.city.citizen.CitizenEntity::new, MobCategory.MISC)
         .sized(0.6F, 1.8F).clientTrackingRange(8).updateInterval(3).noSave().build("citizen")
   );

   /** The Thanos city event's boss (worldevent/thanos): one at a time, never saved, no army. */
   public static final RegistryObject<EntityType<dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity>> THANOS = ENTITIES.register(
      "thanos", () -> EntityType.Builder.<dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity>of(dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity::new, MobCategory.MISC)
         .sized(1.6F, 3.8F).clientTrackingRange(16).updateInterval(2).fireImmune().noSave().build("thanos")
   );

   public static void register(IEventBus eventBus) {
      ENTITIES.register(eventBus);
   }
}

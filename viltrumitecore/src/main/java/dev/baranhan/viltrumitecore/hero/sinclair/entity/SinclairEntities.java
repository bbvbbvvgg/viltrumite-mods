package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sinclair's own entity register (plan docs/plans/2026-10-28-sinclair.md step 4). None of them is
 * saved (spec §8): every one checks its owner each tick and disappears without him.
 */
public final class SinclairEntities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<SparkEntity>> SPARK = ENTITIES.register("sinclair_spark",
      () -> EntityType.Builder.<SparkEntity>of(SparkEntity::new, MobCategory.MISC).sized(0.2F, 0.2F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("sinclair_spark"));
   public static final RegistryObject<EntityType<VexingBoltEntity>> VEXING_BOLT = ENTITIES.register("sinclair_vexing_bolt",
      () -> EntityType.Builder.<VexingBoltEntity>of(VexingBoltEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("sinclair_vexing_bolt"));
   public static final RegistryObject<EntityType<AssistantEntity>> ASSISTANT = ENTITIES.register("sinclair_assistant",
      () -> EntityType.Builder.<AssistantEntity>of(AssistantEntity::new, MobCategory.MISC).sized(0.6F, 1.75F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().build("sinclair_assistant"));
   public static final RegistryObject<EntityType<DecoyEntity>> DECOY = ENTITIES.register("sinclair_decoy",
      () -> EntityType.Builder.<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().build("sinclair_decoy"));
   public static final RegistryObject<EntityType<HexHatEntity>> HEX_HAT = ENTITIES.register("sinclair_hex_hat",
      () -> EntityType.Builder.<HexHatEntity>of(HexHatEntity::new, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(10).updateInterval(2).fireImmune().noSave().build("sinclair_hex_hat"));
   public static final RegistryObject<EntityType<HexMarkEntity>> HEX_MARK = ENTITIES.register("sinclair_hex_mark",
      () -> EntityType.Builder.<HexMarkEntity>of(HexMarkEntity::new, MobCategory.MISC).sized(0.1F, 0.1F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().build("sinclair_hex_mark"));
   public static final RegistryObject<EntityType<ActPropEntity>> ACT_PROP = ENTITIES.register("sinclair_act_prop",
      () -> EntityType.Builder.<ActPropEntity>of(ActPropEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).clientTrackingRange(12).updateInterval(1).fireImmune().noSave().build("sinclair_act_prop"));
   public static final RegistryObject<EntityType<ActCardEntity>> ACT_CARD = ENTITIES.register("sinclair_act_card",
      () -> EntityType.Builder.<ActCardEntity>of(ActCardEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).fireImmune().noSave().build("sinclair_act_card"));

   private SinclairEntities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(SinclairEntities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(ASSISTANT.get(), AssistantEntity.createAttributes().build());
      event.put(DECOY.get(), DecoyEntity.createAttributes().build());
   }
}

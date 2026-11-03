package dev.baranhan.viltrumitecore.hero.venom.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity register of the Venom boss (its own DeferredRegister, like SCP-096). */
public final class VenomEntities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "viltrumitecore");
   public static final RegistryObject<EntityType<VenomBossEntity>> VENOM_BOSS = ENTITIES.register("venom_boss",
      () -> EntityType.Builder.<VenomBossEntity>of(VenomBossEntity::new, MobCategory.MONSTER).sized(1.05F, 2.6F)
         .clientTrackingRange(10).updateInterval(1).build("venom_boss"));

   private VenomEntities() {
   }

   public static void register(IEventBus modBus) {
      ENTITIES.register(modBus);
      modBus.addListener(VenomEntities::onAttributes);
   }

   private static void onAttributes(EntityAttributeCreationEvent event) {
      event.put(VENOM_BOSS.get(), VenomBossEntity.createAttributes().build());
   }
}

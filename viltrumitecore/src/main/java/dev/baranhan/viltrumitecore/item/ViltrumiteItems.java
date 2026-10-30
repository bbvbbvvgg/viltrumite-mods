package dev.baranhan.viltrumitecore.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ViltrumiteItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "viltrumitecore");
   public static final RegistryObject<Item> INFINITY_GUN = ITEMS.register("infinity_gun", () -> new InfinityGunItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> INFINITY_GUN_BASE = ITEMS.register("infinity_gun_base", () -> new EmptyGeoItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> INFINITY_GUN_HANDLE = ITEMS.register("infinity_gun_handle", () -> new EmptyGeoItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> INFINITY_GUN_BARREL = ITEMS.register("infinity_gun_barrel", () -> new EmptyGeoItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> VILTRUMITE_BLOOD_SAMPLE = ITEMS.register("viltrumite_blood_sample", () -> new Item(new Properties().stacksTo(16)));
   public static final RegistryObject<Item> HUMAN_BLOOD_SAMPLE = ITEMS.register("human_blood_sample", () -> new Item(new Properties().stacksTo(16)));
   public static final RegistryObject<Item> SCOURGE_CULTURE = ITEMS.register("scourge_culture", () -> new Item(new Properties().stacksTo(16)));
   public static final RegistryObject<Item> EVANGELIUM = ITEMS.register("evangelium", () -> new EvangeliumItem(new Properties().stacksTo(1)));
   /** The universal transformation item (docs/design/2026-10-30-od-lagna-wish-design.md). */
   public static final RegistryObject<Item> OD_LAGNA_WISH = ITEMS.register("od_lagna_wish", () -> new OdLagnaWishItem(new Properties().stacksTo(1).fireResistant()));

   public static void register(IEventBus eventBus) {
      ITEMS.register(eventBus);
   }
}

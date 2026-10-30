package dev.baranhan.viltrumitecore.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ViltrumiteCreativeTabs {
   public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "viltrumitecore");
   public static final RegistryObject<CreativeModeTab> VILTRUMITE_TAB = CREATIVE_MODE_TABS.register(
      "main_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)ViltrumiteItems.INFINITY_GUN.get()))
            .title(Component.translatable("itemgroup.viltrumitecore.main_tab"))
            .displayItems((parameters, output) -> {
               output.accept((ItemLike)ViltrumiteItems.INFINITY_GUN.get());
               output.accept((ItemLike)ViltrumiteItems.INFINITY_GUN_HANDLE.get());
               output.accept((ItemLike)ViltrumiteItems.INFINITY_GUN_BASE.get());
               output.accept((ItemLike)ViltrumiteItems.INFINITY_GUN_BARREL.get());
               output.accept((ItemLike)ViltrumiteItems.VILTRUMITE_BLOOD_SAMPLE.get());
               output.accept((ItemLike)ViltrumiteItems.HUMAN_BLOOD_SAMPLE.get());
               output.accept((ItemLike)ViltrumiteItems.SCOURGE_CULTURE.get());
               output.accept((ItemLike)ViltrumiteItems.EVANGELIUM.get());
            })
            .build()
   );

   /** «Желания Од Лагуны»: one tab, one universal item. */
   public static final RegistryObject<CreativeModeTab> OD_LAGNA_TAB = CREATIVE_MODE_TABS.register(
      "od_lagna_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)ViltrumiteItems.OD_LAGNA_WISH.get()))
            .title(Component.translatable("itemgroup.viltrumitecore.od_lagna_tab"))
            .withTabsBefore(VILTRUMITE_TAB.getKey())
            .displayItems((parameters, output) -> output.accept((ItemLike)ViltrumiteItems.OD_LAGNA_WISH.get()))
            .build()
   );

   public static void register(IEventBus eventBus) {
      CREATIVE_MODE_TABS.register(eventBus);
   }
}

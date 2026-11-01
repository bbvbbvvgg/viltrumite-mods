package dev.baranhan.viltrumitecore.client.redhulk;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point for the Red Hulk (called from ViltrumiteCoreClient's setup). The renderer subscribes itself. */
public final class RedHulkClient {
   private RedHulkClient() {
   }

   public static void init() {
      HeroSkins.register(HeroId.RED_HULK, new HeroSkins.Provider() {
         @Override
         public ResourceLocation defaultSkin() {
            return RedHulkView.VANILLA_SKIN;
         }

         @Override
         public String defaultModelName() {
            return "default";
         }
      });
      PanelStyles.register(HeroId.RED_HULK, RedHulkPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.RED_HULK, RedHulkHeadPreview.INSTANCE);
      HeroPortraits.register(HeroId.RED_HULK, RedHulkPortrait.INSTANCE);
      RedHulkFxRecipes.init();
      RedHulkVfx.init();
      MinecraftForge.EVENT_BUS.addListener(RedHulkVfx::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(RedHulkVfx::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(RedHulkVfx::onLevelUnload);
   }
}

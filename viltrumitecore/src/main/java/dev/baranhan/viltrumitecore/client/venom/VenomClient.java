package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point of Venom (called from ViltrumiteCoreClient's setup). The renderers subscribe themselves. */
public final class VenomClient {
   private VenomClient() {
   }

   public static void init() {
      HeroSkins.register(HeroId.VENOM, new HeroSkins.Provider() {
         @Override
         public ResourceLocation defaultSkin() {
            return VenomView.VANILLA_SKIN;
         }

         @Override
         public String defaultModelName() {
            return "default";
         }
      });
      PanelStyles.register(HeroId.VENOM, VenomPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.VENOM, VenomHeadPreview.INSTANCE);
      HeroPortraits.register(HeroId.VENOM, VenomPortrait.INSTANCE);
      VenomFxRecipes.init();
      VenomVfx.init();
      MinecraftForge.EVENT_BUS.addListener(VenomVfx::onLevelUnload);
   }
}

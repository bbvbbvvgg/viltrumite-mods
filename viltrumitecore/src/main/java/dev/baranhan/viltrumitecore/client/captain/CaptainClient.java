package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point for Captain America (called from ViltrumiteCoreClient's setup). */
public final class CaptainClient {
   private CaptainClient() {
   }

   public static void init() {
      HeroSkins.register(HeroId.CAPTAIN, new HeroSkins.Provider() {
         @Override
         public ResourceLocation defaultSkin() {
            return CaptainView.SKIN;
         }

         @Override
         public String defaultModelName() {
            return "default";
         }
      });
      PanelStyles.register(HeroId.CAPTAIN, CaptainPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.CAPTAIN, CaptainHeadPreview.INSTANCE);
      HeroPortraits.register(HeroId.CAPTAIN, CaptainPortrait.INSTANCE);
      MinecraftForge.EVENT_BUS.addListener(CaptainVfx::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(CaptainVfx::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(CaptainVfx::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(CaptainUltScene::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(CaptainUltScene::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, CaptainUltScene::onRenderHand);
      MinecraftForge.EVENT_BUS.addListener(CaptainUltScene::onOverlay);
      MinecraftForge.EVENT_BUS.addListener(CaptainUltScene::onRenderGui);
      MinecraftForge.EVENT_BUS.addListener(CaptainUltScene::onFov);
      MinecraftForge.EVENT_BUS.addListener(CaptainUltScene::onNameTag);
      MinecraftForge.EVENT_BUS.addListener(CaptainUltScene::onLevelUnload);
   }
}

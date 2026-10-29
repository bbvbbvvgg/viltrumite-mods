package dev.baranhan.viltrumitecore.client.jinwoo;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point for Jinwoo (called from ViltrumiteCoreClient's setup). */
public final class JinwooClient {
   private JinwooClient() {
   }

   public static void init() {
      HeroSkins.register(HeroId.JINWOO, new HeroSkins.Provider() {
         @Override
         public ResourceLocation defaultSkin() {
            return JinwooView.SKIN;
         }

         @Override
         public String defaultModelName() {
            return "default";
         }
      });
      PanelStyles.register(HeroId.JINWOO, JinwooPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.JINWOO, JinwooHeadPreview.INSTANCE);
      HeroPortraits.register(HeroId.JINWOO, JinwooPortrait.INSTANCE);
      MinecraftForge.EVENT_BUS.addListener(JinwooVfx::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(JinwooVfx::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(JinwooVfx::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(JinwooUltScene::onRenderGui);
      MinecraftForge.EVENT_BUS.addListener(JinwooUltScene::onFov);
      MinecraftForge.EVENT_BUS.addListener(JinwooUltScene::onCameraAngles);
   }
}

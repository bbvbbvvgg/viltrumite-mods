package dev.baranhan.viltrumitecore.client.regulus;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Client entry point of Regulus Corneas (called from ViltrumiteCoreClient's setup): panel style,
 * race-screen head, subtitle portrait, the own body and first-person renderers, the world VFX and
 * Kingdom of One's cameras. No mixins: everything hangs on Forge events.
 */
public final class RegulusClient {
   private RegulusClient() {
   }

   public static void init() {
      PanelStyles.register(HeroId.REGULUS, RegulusPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.REGULUS, RegulusHeadPreview.INSTANCE);
      HeroPortraits.register(HeroId.REGULUS, RegulusPortrait.INSTANCE);
      MinecraftForge.EVENT_BUS.addListener(RegulusRenderer::onRenderPlayerPre);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RegulusUltScene::onRenderHand);
      MinecraftForge.EVENT_BUS.addListener(RegulusFirstPerson::onRenderHand);
      MinecraftForge.EVENT_BUS.addListener(RegulusVfx::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(RegulusVfx::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(RegulusVfx::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(RegulusUltScene::onRenderGui);
      MinecraftForge.EVENT_BUS.addListener(RegulusUltScene::onFov);
      MinecraftForge.EVENT_BUS.addListener(RegulusUltScene::onCameraAngles);
   }
}

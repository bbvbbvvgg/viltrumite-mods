package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraftforge.common.MinecraftForge;

/**
 * Client entry point of The Magnificent Sinclair (plan step 7): panel style, race-screen head,
 * subtitle portrait, body/first-person renderers, world VFX and the stage. Entity renderers are
 * registered from ClientModEvents via {@link SinclairEntityRenderers#register}; the hex rabbit is
 * {@link HexClient} (its own event subscriber).
 */
public final class SinclairClient {
   private SinclairClient() {
   }

   public static void init() {
      PanelStyles.register(HeroId.SINCLAIR, SinclairPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.SINCLAIR, SinclairHeadPreview.INSTANCE);
      HeroPortraits.register(HeroId.SINCLAIR, SinclairPortrait.INSTANCE);
      MinecraftForge.EVENT_BUS.addListener(SinclairRenderer::onRenderPlayerPre);
      MinecraftForge.EVENT_BUS.addListener(SinclairFirstPerson::onRenderHand);
      MinecraftForge.EVENT_BUS.addListener(SinclairVfx::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(StageLight::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(StageLight::onRenderGuiPre);
      // Opt-in scripted playtest; inert unless -Dviltrumitecore.sinclair.playtest=true.
      dev.baranhan.viltrumitecore.client.sinclair.qa.SinclairPlaytest.init();
   }
}

package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.client.sinclair.stage.CopyCinematicClient;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

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
      MinecraftForge.EVENT_BUS.addListener(SinclairFirstPerson::onRenderArm);
      MinecraftForge.EVENT_BUS.addListener(SinclairVfx::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(StageLight::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(StageLight::onRenderGuiPre);
      MinecraftForge.EVENT_BUS.addListener(ActCardPopup::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(ActCardPopup::onRenderGui);
      // Copy cinematic and the act cameras (design 2026-11-02 §2, §3.2).
      MinecraftForge.EVENT_BUS.addListener(CopyCinematicClient::onMovement);
      MinecraftForge.EVENT_BUS.addListener(CopyCinematicClient::onInteraction);
      MinecraftForge.EVENT_BUS.addListener(CopyCinematicClient::onOverlayPre);
      // LOWEST: the card is drawn after other HUD layers, so a volunteer's own hero HUD never covers it.
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CopyCinematicClient::onRenderGui);
      MinecraftForge.EVENT_BUS.addListener(CopyCinematicClient::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(CopyCinematicClient::onLogout);
      // Opt-in scripted playtest; inert unless -Dviltrumitecore.sinclair.playtest=true.
      dev.baranhan.viltrumitecore.client.sinclair.qa.SinclairPlaytest.init();
   }
}

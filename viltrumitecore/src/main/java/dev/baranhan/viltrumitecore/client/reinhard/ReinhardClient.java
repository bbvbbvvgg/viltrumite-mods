package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.render.vfx.OutlineTargets;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point for Reinhard: client systems register here (panel style, HUD, race screen head, Wind Reading outline, Step Dash prediction). */
public final class ReinhardClient {
   private ReinhardClient() {
   }

   public static void init() {
      PanelStyles.register(HeroId.REINHARD, ReinhardPanelStyle.INSTANCE);
      HeroHeadPreviews.register(HeroId.REINHARD, ReinhardHeadPreview.INSTANCE);
      OutlineTargets.register(WindReadingClient::colorOf);
      MinecraftForge.EVENT_BUS.addListener(StepDashClient::onClientTick);
   }
}

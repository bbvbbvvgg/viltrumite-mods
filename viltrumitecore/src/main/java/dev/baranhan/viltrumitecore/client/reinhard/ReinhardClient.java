package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.hero.HeroId;

/** Client entry point for Reinhard: client systems register here (panel style, HUD). */
public final class ReinhardClient {
   private ReinhardClient() {
   }

   public static void init() {
      PanelStyles.register(HeroId.REINHARD, ReinhardPanelStyle.INSTANCE);
   }
}

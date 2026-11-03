package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point of Venom (called from ViltrumiteCoreClient's setup). The renderers subscribe themselves. */
public final class VenomClient {
   private VenomClient() {
   }

   /** The body is drawn as the human (before the grow stage, after the revert, the possession husk): no symbiote skin. */
   static boolean humanBody(@Nullable HeroPublicSnapshot s) {
      if (s == null) {
         return true;
      }
      return humanBody(VenomView.flag(s, VenomFlags.POSSESSING), VenomView.flag(s, VenomFlags.FORM), VenomView.move(s), s.actionElapsed());
   }

   /** Pure: the same decision the renderer makes before drawing the grown body. */
   static boolean humanBody(boolean possessing, boolean form, @Nullable VenomMove move, double ticks) {
      return possessing || !VenomView.look(form, move, ticks).geo();
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

         @Override
         public boolean suppressOverride(AbstractClientPlayer player) {
            // the human keeps his own skin (the coat layer spreads over it); the husk too
            return humanBody(VenomView.of(player));
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

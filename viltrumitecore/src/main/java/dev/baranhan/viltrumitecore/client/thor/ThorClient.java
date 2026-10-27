package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Client entry point for Thor: skin, race-screen head, 3D parts. Poses run from ThorModelMixin. */
public final class ThorClient {
   /** Composite of the source pack's Thor skin and its armour layer (tools/assets/convert_thor.py). */
   public static final ResourceLocation SKIN = new ResourceLocation("viltrumitecore", "textures/entity/hero/thor.png");

   private ThorClient() {
   }

   public static void init() {
      HeroSkins.register(HeroId.THOR, new HeroSkins.Provider() {
         @Override
         public ResourceLocation defaultSkin() {
            return SKIN;
         }

         @Override
         public String defaultModelName() {
            return "default";
         }

         @Override
         public Optional<ResourceLocation> skinVariant(AbstractClientPlayer player) {
            return Optional.empty();
         }

         @Override
         public boolean suppressCosmetics(AbstractClientPlayer player) {
            return true;
         }

         @Override
         public boolean suppressCape(AbstractClientPlayer player) {
            return true;
         }
      });
      HeroHeadPreviews.register(HeroId.THOR, HeroHeadPreviews.skinFace(SKIN));
      PlayerGeoLayer.register(new ThorParts());
      ThorCombatClient.init();
   }
}

package dev.baranhan.viltrumitecore.client.ironman;

import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Client entry point for Iron Man: Tony skin, the suit skin reveal frames
 * (player skin = baked frame, so the first-person arm uses it too) and the
 * 3D parts provider. Layers are added in ViltrumiteCoreClient.onAddLayers.
 */
public final class IronManClient {
   /** Tony Stark without armor (classic 4 px arms). */
   public static final ResourceLocation SKIN = new ResourceLocation("viltrumitecore", "textures/entity/hero/ironman.png");

   private IronManClient() {
   }

   public static void registerSkins() {
      HeroSkins.register(HeroId.IRON_MAN, new HeroSkins.Provider() {
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
            HeroPublicSnapshot snapshot = IronManView.of(player);
            if (snapshot == null) {
               return Optional.empty();
            }

            Optional<ResourceLocation> mark = dev.baranhan.viltrumitecore.client.ironman.mark.MarkVisuals.skin(player, snapshot);
            if (mark.isPresent()) {
               return mark;
            }

            int frame = IronManView.frame(snapshot, Minecraft.getInstance().getFrameTime());
            return frame <= 0 ? Optional.empty() : Optional.of(IronManSuitTextures.INSTANCE.skin(frame));
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
      PlayerGeoLayer.register(new IronManPartsProvider());
      dev.baranhan.viltrumitecore.client.ironman.mark.MarkVisuals.init();
      dev.baranhan.viltrumitecore.client.ironman.mark.sig.SignatureVisuals.init();
      dev.baranhan.viltrumitecore.client.render.vfx.OutlineTargets.register(ScanHighlight::colorOf);
      PanelStyles.register(HeroId.IRON_MAN, new IronManPanelStyle());
   }
}

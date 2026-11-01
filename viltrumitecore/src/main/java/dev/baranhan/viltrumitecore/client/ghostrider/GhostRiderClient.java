package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleClient;
import dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleTrails;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

/** Client entry point for the Ghost Rider (called from ViltrumiteCoreClient's setup). */
public final class GhostRiderClient {
   private GhostRiderClient() {
   }

   public static void init() {
      HeroSkins.register(HeroId.GHOST_RIDER, new HeroSkins.Provider() {
         @Override
         public ResourceLocation defaultSkin() {
            return GhostRiderView.SKINS[0];
         }

         @Override
         public String defaultModelName() {
            return "default";
         }

         @Override
         public Optional<ResourceLocation> skinVariant(AbstractClientPlayer player) {
            HeroPublicSnapshot s = GhostRiderView.of(player);
            return s == null ? Optional.empty() : Optional.of(GhostRiderView.SKINS[GhostRiderView.burnFrame(s)]);
         }

         @Override
         public boolean suppressCosmetics(AbstractClientPlayer player) {
            HeroPublicSnapshot s = GhostRiderView.of(player);
            return s != null && GhostRiderView.burnFrame(s) > 0;
         }

         @Override
         public boolean suppressCape(AbstractClientPlayer player) {
            return true;
         }
      });
      PlayerGeoLayer.register(GhostRiderGear.INSTANCE);
      GhostRiderFxRecipes.init();
      GhostRiderVfx.init();
      HellCycleEntity.installClient(HellCycleClient.INSTANCE);
      MinecraftForge.EVENT_BUS.addListener(GhostRiderVfx::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(GhostRiderVfx::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(GhostRiderVfx::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(HellCycleTrails::onRenderLevel);
      MinecraftForge.EVENT_BUS.addListener(HellCycleClient::onLevelUnload);
      MinecraftForge.EVENT_BUS.addListener(GhostRiderCamera::onFov);
      MinecraftForge.EVENT_BUS.addListener(GhostRiderCamera::onAngles);
   }
}

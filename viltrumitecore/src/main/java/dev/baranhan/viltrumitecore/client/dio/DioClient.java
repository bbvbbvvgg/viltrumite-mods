package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Client entry point for DIO: skin, 3D outfit parts. Renderers subscribe themselves. */
public final class DioClient {
   public static final ResourceLocation SKIN = new ResourceLocation("viltrumitecore", "textures/entity/hero/dio.png");
   public static final ResourceLocation OUTFIT_GEO = new ResourceLocation("viltrumitecore", "geo/dio/dio_outfit.geo.json");
   public static final ResourceLocation OUTFIT_TEXTURE = new ResourceLocation("viltrumitecore", "textures/entity/dio/dio_outfit.png");

   private DioClient() {
   }

   public static void init() {
      HeroSkins.register(HeroId.DIO, new HeroSkins.Provider() {
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
      PlayerGeoLayer.register((player, partialTick, firstPerson, out) -> {
         if (DioView.of(player) != null) {
            out.add(new PlayerGeoLayer.Part(OUTFIT_GEO, List.of(PlayerGeoLayer.Pass.cutout(OUTFIT_TEXTURE))));
         }
      });
   }
}

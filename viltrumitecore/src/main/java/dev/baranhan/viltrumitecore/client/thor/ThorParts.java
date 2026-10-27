package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Thor's 3D parts on the player model (PlayerGeoLayer): beard and belly
 * (third person; the belly hides under a chestplate), and Mjölnir in the
 * right hand (both views) or on the right hip. Models converted from the
 * source pack by tools/assets/convert_thor.py.
 */
public final class ThorParts implements PlayerGeoLayer.Provider {
   public static final ResourceLocation BEARD = geo("beard");
   public static final ResourceLocation BELLY = geo("belly");
   public static final ResourceLocation MJOLNIR_HAND = geo("mjolnir_hand");
   public static final ResourceLocation MJOLNIR_BELT = geo("mjolnir_belt");
   private static final List<PlayerGeoLayer.Pass> BEARD_PASSES = List.of(PlayerGeoLayer.Pass.cutout(texture("beard")));
   private static final List<PlayerGeoLayer.Pass> BELLY_PASSES = List.of(PlayerGeoLayer.Pass.cutout(texture("belly")));
   /** The rune lights glow always, as the source pack's lights texture does. */
   private static final List<PlayerGeoLayer.Pass> MJOLNIR_PASSES = List.of(
      PlayerGeoLayer.Pass.cutout(texture("mjolnir")), PlayerGeoLayer.Pass.glow(texture("mjolnir_glow")));

   @Override
   public void collect(AbstractClientPlayer player, float partialTick, boolean firstPerson, List<PlayerGeoLayer.Part> out) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         return;
      }

      MjolnirPlace place = ThorView.place(snapshot, player);
      if (place == MjolnirPlace.HAND) {
         out.add(new PlayerGeoLayer.Part(MJOLNIR_HAND, MJOLNIR_PASSES));
      }

      if (firstPerson) {
         return;
      }

      out.add(new PlayerGeoLayer.Part(BEARD, BEARD_PASSES));
      if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
         out.add(new PlayerGeoLayer.Part(BELLY, BELLY_PASSES));
      }

      if (place == MjolnirPlace.BELT) {
         out.add(new PlayerGeoLayer.Part(MJOLNIR_BELT, MJOLNIR_PASSES));
      }
   }

   private static ResourceLocation geo(String name) {
      return new ResourceLocation("viltrumitecore", "geo/thor/" + name + ".geo.json");
   }

   private static ResourceLocation texture(String name) {
      return new ResourceLocation("viltrumitecore", "textures/entity/thor/" + name + ".png");
   }
}

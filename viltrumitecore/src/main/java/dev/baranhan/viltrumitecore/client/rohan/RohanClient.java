package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.hero.HeroSkins;
import dev.baranhan.viltrumitecore.client.hero.PanelStyles;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.rohan.RohanFlags;
import dev.baranhan.viltrumitecore.hero.rohan.RohanMove;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Client entry point for Rohan Kishibe: skin, 3D outfit (hair, headband, pen-nib earrings, jacket), the pen and the
 * manuscript page in his hands, the panel. Renderers subscribe themselves. */
public final class RohanClient {
   public static final ResourceLocation SKIN = HeavensDoorRenderer.rl("textures/entity/hero/rohan.png");
   public static final ResourceLocation OUTFIT_GEO = HeavensDoorRenderer.rl("geo/rohan/rohan_outfit.geo.json");
   public static final ResourceLocation OUTFIT_TEXTURE = HeavensDoorRenderer.rl("textures/entity/rohan/rohan_outfit.png");

   private RohanClient() {
   }

   /** The pen shows while he draws, writes, throws nibs or edits himself; the page only with the manuscript (pure). */
   public static boolean penVisible(RohanMove move, boolean reading) {
      return reading || move == RohanMove.SKETCH || move == RohanMove.WRITE || move == RohanMove.NIBS || move == RohanMove.SELF_EDIT
         || move == RohanMove.DOOR;
   }

   public static void init() {
      HeroSkins.register(HeroId.ROHAN, new HeroSkins.Provider() {
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
         HeroPublicSnapshot s = RohanView.of(player);
         if (s == null) {
            return;
         }
         RohanMove move = RohanView.move(s);
         boolean pen = penVisible(move, RohanView.flag(s, RohanFlags.READING));
         boolean page = move == RohanMove.MANUSCRIPT || move == RohanMove.COUNTER;
         out.add(new PlayerGeoLayer.Part(OUTFIT_GEO, List.of(PlayerGeoLayer.Pass.cutout(OUTFIT_TEXTURE)), model -> {
            GeoBone penBone = model.getBone("pen");
            if (penBone != null) {
               penBone.hidden = !pen;
            }
            GeoBone pageBone = model.getBone("page");
            if (pageBone != null) {
               pageBone.hidden = !page;
            }
         }));
      });
      PanelStyles.register(HeroId.ROHAN, RohanPanelStyle.INSTANCE);
   }
}

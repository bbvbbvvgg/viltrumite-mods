package dev.baranhan.viltrumitecore.client.clash;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.combat.clash.ClashFx;
import java.util.List;

/**
 * Neutral layers of the clash presets (ids in {@link ClashFx}): shakes, flashes, shockwaves, debris and dust. The
 * beam colours at the point are ClashClient's spray. Pure data (ClashFxRecipesTest).
 */
public final class ClashFxRecipes {
   private ClashFxRecipes() {
   }

   public static void init() {
      all().forEach(FxRecipe::register);
   }

   public static List<FxRecipe> all() {
      return List.of(
         FxRecipe.builder(ClashFx.BEAM_LOCK).shake(0, 0.7F, 22.0F).screenFlash(0, 3, 0.07F, 14.0F).shockwave(0, 8, 1.8F, 0.6F).build(),
         FxRecipe.builder(ClashFx.BEAM_BLAST).shake(0, 1.2F, 30.0F).screenFlash(0, 5, 0.12F, 18.0F).shockwave(0, 14, 3.4F, 0.9F)
            .debris(0, 10, 2.2F, 0.6F).dust(1, 2.6F, 28).build(),
         FxRecipe.builder(ClashFx.BEAM_FIZZLE).shake(0, 0.2F, 10.0F).dust(0, 0.6F, 6).build());
   }
}

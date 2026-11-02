package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.venom.VenomFx;
import java.util.List;

/**
 * Neutral layers of Venom's presets (ids in {@link VenomFx}): shakes, dust, rings and debris. The symbiote's goo
 * (black strands, wet splatter) is VenomVfx's preset listener. No electric layers, no white glare: the symbiote is
 * black and wet. Pure data (VenomFxRecipesTest).
 */
public final class VenomFxRecipes {
   private VenomFxRecipes() {
   }

   public static void init() {
      all().forEach(FxRecipe::register);
   }

   public static List<FxRecipe> all() {
      return List.of(
         FxRecipe.builder(VenomFx.TRANSFORM).shake(0, 0.7F, 18.0F).screenFlash(0, 4, 0.06F, 12.0F).build(),
         FxRecipe.builder(VenomFx.CLAW).shake(0, 0.25F, 10.0F).build(),
         FxRecipe.builder(VenomFx.STICK).shake(0, 0.15F, 8.0F).dust(0, 0.4F, 4).build(),
         FxRecipe.builder(VenomFx.SLAM).shake(0, 1.0F, 22.0F).shockwave(0, 14, 3.2F, 0.9F).debris(0, 8, 2.0F, 0.6F).dust(1, 2.6F, 30).build(),
         FxRecipe.builder(VenomFx.SWEEP).shake(0, 0.5F, 14.0F).dust(0, 2.0F, 16).build(),
         FxRecipe.builder(VenomFx.RECOIL).shake(0, 0.35F, 10.0F).build(),
         FxRecipe.builder(VenomFx.WEB).dust(0, 0.3F, 3).build(),
         FxRecipe.builder(VenomFx.CLASH).shake(0, 0.9F, 20.0F).shockwave(0, 10, 2.2F, 0.5F).dust(0, 1.4F, 14).build(),
         FxRecipe.builder(VenomFx.ENGULF).shake(0, 0.6F, 14.0F).screenFlash(0, 4, 0.05F, 8.0F).build(),
         FxRecipe.builder(VenomFx.LEAP).shake(0, 0.8F, 18.0F).shockwave(0, 12, 2.6F, 0.7F).debris(0, 6, 1.6F, 0.5F).dust(0, 2.2F, 24).build());
   }
}

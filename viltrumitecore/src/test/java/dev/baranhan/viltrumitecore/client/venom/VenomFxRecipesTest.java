package dev.baranhan.viltrumitecore.client.venom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.venom.VenomFx;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Venom's preset recipes: one per preset, black and wet (no lightning, no white glare), registered by net id. */
class VenomFxRecipesTest {
   @Test
   void onePresetOneRecipeNoElectricityNoGlare() {
      List<FxRecipe> recipes = VenomFxRecipes.all();
      assertEquals(VenomFx.PRESETS.stream().map(FxPreset::id).collect(Collectors.toSet()),
         recipes.stream().map(r -> r.preset().id()).collect(Collectors.toSet()));
      assertEquals(VenomFx.PRESETS.size(), recipes.size());
      Set<Integer> nets = new HashSet<>();
      for (FxPreset p : VenomFx.PRESETS) {
         assertTrue(nets.add(p.netId()), "unique net id " + p.id());
         assertTrue(p.netId() >= 740 && p.netId() < 760, "Venom's id block 740+: " + p.id());
      }
      for (FxRecipe r : recipes) {
         assertFalse(r.layers().isEmpty(), r.preset().id());
         for (FxRecipe.Kind k : new FxRecipe.Kind[]{FxRecipe.Kind.BOLT, FxRecipe.Kind.ARC, FxRecipe.Kind.CRACKLE, FxRecipe.Kind.GROUND_CRAWL,
            FxRecipe.Kind.SPARKS, FxRecipe.Kind.SKY_FLASH, FxRecipe.Kind.FLASH, FxRecipe.Kind.SCORCH}) {
            assertTrue(r.of(k).isEmpty(), r.preset().id() + ": the symbiote is black and wet, no " + k);
         }
      }
      VenomFxRecipes.init();
      for (FxPreset p : VenomFx.PRESETS) {
         assertNotNull(FxRecipe.byNetId(p.netId()), p.id());
      }
   }
}

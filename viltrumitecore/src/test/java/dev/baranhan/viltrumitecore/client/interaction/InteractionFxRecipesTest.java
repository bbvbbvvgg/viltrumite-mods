package dev.baranhan.viltrumitecore.client.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Every interaction FX preset has exactly one client recipe; ids are namespaced and net ids stay in 600-649. */
class InteractionFxRecipesTest {
   @Test
   void presetsAreNamespacedAndInTheirRange() {
      Set<Integer> nets = new HashSet<>();
      for (FxPreset preset : InteractionFx.PRESETS) {
         assertTrue(preset.id().startsWith("viltrumitecore:interaction_"), preset.id());
         assertTrue(preset.netId() >= 600 && preset.netId() < 650, preset.id());
         assertTrue(nets.add(preset.netId()));
         assertSame(preset, FxPreset.byNetId(preset.netId()));
      }
   }

   @Test
   void everyPresetHasExactlyOneRecipe() {
      assertEquals(InteractionFx.PRESETS.stream().map(FxPreset::id).collect(Collectors.toSet()),
         InteractionFxRecipes.all().stream().map(r -> r.preset().id()).collect(Collectors.toSet()));
      assertEquals(InteractionFx.PRESETS.size(), InteractionFxRecipes.all().size());
      InteractionFxRecipes.init();
      for (FxPreset preset : InteractionFx.PRESETS) {
         FxRecipe recipe = FxRecipe.byNetId(preset.netId());
         assertSame(preset, recipe.preset());
      }
   }
}

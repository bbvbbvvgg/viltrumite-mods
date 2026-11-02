package dev.baranhan.viltrumitecore.client.ghostrider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderFx;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Pure client rules of the Ghost Rider: the bike dashboard and the hellfire FX recipes. */
class GhostRiderClientRulesTest {

   @Test
   void dashboardSpeedIsKilometresPerHour() {
      assertEquals(0, GhostRiderPanelStyle.kmh(0.0));
      assertEquals(72, GhostRiderPanelStyle.kmh(1.0), "one block a tick is 72 km/h");
      assertEquals(61, GhostRiderPanelStyle.kmh(HellCycleRules.MAX_SPEED));
      assertEquals(94, GhostRiderPanelStyle.kmh(HellCycleRules.BOOST_SPEED));
      assertEquals(7, GhostRiderPanelStyle.kmh(-HellCycleRules.REVERSE_SPEED), "reverse reads as a positive speed");
   }

   @Test
   void revBarHeatsUpToWhiteAtTheRedline() {
      int lastR = 0;
      int lastG = 0;
      for (float t = 0.0F; t <= 1.0F; t += 0.05F) {
         int c = GhostRiderPanelStyle.rpmColor(t);
         assertEquals(0xFF, c >>> 24, "opaque");
         int r = c >> 16 & 0xFF;
         int g = c >> 8 & 0xFF;
         int b = c & 0xFF;
         assertTrue(r >= lastR && g >= lastG, "hotter as it revs");
         if (t < GhostRiderPanelStyle.REDLINE) {
            assertTrue(b < 20, "no white before the redline");
         }
         lastR = r;
         lastG = g;
      }
      assertTrue((GhostRiderPanelStyle.rpmColor(1.0F) & 0xFF) > 150, "white-hot past the redline");
      assertEquals(GhostRiderPanelStyle.rpmColor(0.0F), GhostRiderPanelStyle.rpmColor(-3.0F));
      assertEquals(GhostRiderPanelStyle.rpmColor(1.0F), GhostRiderPanelStyle.rpmColor(7.0F));
   }

   @Test
   void timerBarsAreClamped() {
      assertEquals(0, GhostRiderPanelStyle.timerWidth(0, HellCycleRules.BOOST_TICKS, 48));
      assertEquals(0, GhostRiderPanelStyle.timerWidth(-4, HellCycleRules.BOOST_TICKS, 48));
      assertEquals(0, GhostRiderPanelStyle.timerWidth(10, 0, 48));
      assertEquals(48, GhostRiderPanelStyle.timerWidth(HellCycleRules.BOOST_TICKS, HellCycleRules.BOOST_TICKS, 48));
      assertEquals(48, GhostRiderPanelStyle.timerWidth(HellCycleRules.BOOST_TICKS * 2, HellCycleRules.BOOST_TICKS, 48));
      assertEquals(24, GhostRiderPanelStyle.timerWidth(HellCycleRules.BOOST_TICKS / 2, HellCycleRules.BOOST_TICKS, 48));
   }

   @Test
   void everyPresetHasOneHellfireRecipe() {
      List<FxRecipe> recipes = GhostRiderFxRecipes.all();
      assertEquals(GhostRiderFx.PRESETS.stream().map(FxPreset::id).collect(Collectors.toSet()),
         recipes.stream().map(r -> r.preset().id()).collect(Collectors.toSet()));
      assertEquals(GhostRiderFx.PRESETS.size(), recipes.size(), "one recipe per preset");
      for (FxRecipe r : recipes) {
         assertFalse(r.layers().isEmpty(), r.preset().id() + " has layers");
         for (FxRecipe.Kind lightning : new FxRecipe.Kind[]{FxRecipe.Kind.BOLT, FxRecipe.Kind.ARC, FxRecipe.Kind.CRACKLE}) {
            assertTrue(r.of(lightning).isEmpty(), r.preset().id() + ": hellfire, not lightning (" + lightning + ")");
         }
      }
      // playtest #2: the camera starts inside these, so no flash, ring or dust cloud around it
      for (FxRecipe r : List.of(GhostRiderFxRecipes.dash(), GhostRiderFxRecipes.bikeForm(), GhostRiderFxRecipes.transform())) {
         assertTrue(r.of(FxRecipe.Kind.FLASH).isEmpty(), r.preset().id() + ": no flash");
      }
      assertTrue(GhostRiderFxRecipes.dash().of(FxRecipe.Kind.DUST).isEmpty(), "no dust in the first-person dash");
      assertTrue(GhostRiderFxRecipes.dash().of(FxRecipe.Kind.SHOCKWAVE).isEmpty(), "no ring in the first-person dash");
      GhostRiderFxRecipes.init();
      for (FxPreset p : GhostRiderFx.PRESETS) {
         assertNotNull(FxRecipe.byNetId(p.netId()), p.id());
      }
   }
}

package dev.baranhan.viltrumitecore.client.redhulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFx;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pure client rules of the Red Hulk: emissive glow, faces, the heat gauge, the hum, FX presets and recipes. */
class RedHulkClientRulesTest {
   private static final float EPS = 1.0E-4F;

   @Test
   void heatVeinsBrightenWithHeatAndStayInRange() {
      for (double t = 0; t < 4.0; t += 0.05) {
         for (int h = 0; h <= RedHulkRules.HEAT_MAX; h += 100) {
            for (int f = 0; f < 4; f++) {
               float g = RedHulkView.glow(h, (f & 1) != 0, (f & 2) != 0, t);
               assertTrue(g >= 0.0F && g <= 1.0F, "glow in range");
            }
         }
         assertTrue(RedHulkView.glow(0, false, false, t) <= 0.09F, "cold veins are only faintly lit");
         assertTrue(RedHulkView.glow(0, false, false, t) > 0.0F, "never fully dark");
         assertTrue(RedHulkView.glow(RedHulkRules.HEAT_MAX, false, false, t) > RedHulkView.glow(0, false, false, t));
         assertTrue(RedHulkView.glow(0, true, false, t) >= 0.85F, "fury burns white-hot");
         assertTrue(RedHulkView.glow(0, false, true, t) >= 0.55F, "overheat flickers bright");
      }
   }

   @Test
   void eyesAlwaysGlow() {
      assertEquals(0.7F, RedHulkView.eyeGlow(0, false, false), EPS);
      assertEquals(1.0F, RedHulkView.eyeGlow(RedHulkRules.HEAT_MAX, false, false), EPS);
      assertEquals(1.0F, RedHulkView.eyeGlow(0, true, false), EPS);
      assertEquals(1.0F, RedHulkView.eyeGlow(0, false, true), EPS);
   }

   @Test
   void faceFollowsHeatAndState() {
      assertEquals(RedHulkPose.CALM, RedHulkPose.faceFor(0, false, false, false, false));
      assertEquals(RedHulkPose.SNARL, RedHulkPose.faceFor(0, false, false, true, false), "combat stance snarls");
      assertEquals(RedHulkPose.SNARL, RedHulkPose.faceFor(1, false, false, false, false));
      assertEquals(RedHulkPose.SNARL, RedHulkPose.faceFor(2, false, false, false, false));
      assertEquals(RedHulkPose.FURY, RedHulkPose.faceFor(3, false, false, false, false), "blazing heat bares the teeth");
      assertEquals(RedHulkPose.FURY, RedHulkPose.faceFor(0, true, false, false, false));
      assertEquals(RedHulkPose.PANT, RedHulkPose.faceFor(3, true, true, true, false), "overheat pants");
      assertEquals(RedHulkPose.ROAR, RedHulkPose.faceFor(3, true, true, true, true), "a hit roars over everything");
      assertEquals(RedHulkPose.FACES.length, 5);
   }

   @Test
   void gaugeColourRunsFromDarkRedToYellowWhite() {
      int cold = RedHulkPanelStyle.heatColor(0);
      int hot = RedHulkPanelStyle.heatColor(RedHulkRules.HEAT_MAX);
      assertEquals(0xFF, cold >>> 24);
      assertTrue(((cold >> 16) & 0xFF) > ((cold >> 8) & 0xFF) * 3, "cold is dark red");
      assertTrue(((hot >> 8) & 0xFF) > 200 && ((hot >> 16) & 0xFF) == 255, "full heat is yellow-white");
      int lastG = -1;
      for (int h = 0; h <= RedHulkRules.HEAT_MAX; h += 50) {
         int g = (RedHulkPanelStyle.heatColor(h) >> 8) & 0xFF;
         assertTrue(g >= lastG, "the gauge warms monotonically");
         lastG = g;
      }
      assertEquals(RedHulkPanelStyle.heatColor(RedHulkRules.HEAT_MAX), RedHulkPanelStyle.heatColor(50000), "clamped");
   }

   @Test
   void tierLabelKeys() {
      assertEquals("hud.viltrumitecore.red_hulk.tier.0", RedHulkPanelStyle.tierKey(0, false));
      assertEquals("hud.viltrumitecore.red_hulk.tier.1", RedHulkPanelStyle.tierKey(RedHulkRules.WARM, false));
      assertEquals("hud.viltrumitecore.red_hulk.tier.2", RedHulkPanelStyle.tierKey(RedHulkRules.HOT, false));
      assertEquals("hud.viltrumitecore.red_hulk.tier.3", RedHulkPanelStyle.tierKey(RedHulkRules.BLAZING, false));
      assertEquals("hud.viltrumitecore.red_hulk.overheat", RedHulkPanelStyle.tierKey(0, true));
   }

   @Test
   void portraitJawGrowlsWithinLimits() {
      for (double t = 0; t < 5.0; t += 0.01) {
         float jaw = RedHulkPortrait.growlJaw(t);
         assertTrue(jaw >= 6.0F && jaw <= 19.0F, "jaw opening " + jaw);
      }
   }

   @Test
   void heatHumStartsNearHotAndFuryKeepsIt() {
      assertEquals(0.0F, RedHulkVfx.humVolume(0, false), EPS);
      assertEquals(0.0F, RedHulkVfx.humVolume(RedHulkRules.HOT - 100, false), EPS);
      assertTrue(RedHulkVfx.humVolume(RedHulkRules.HOT, false) > 0.0F);
      assertEquals(0.8F, RedHulkVfx.humVolume(RedHulkRules.HEAT_MAX, false), EPS);
      assertEquals(0.4F, RedHulkVfx.humVolume(0, true), EPS);
      assertEquals(0.8F, RedHulkVfx.humVolume(RedHulkRules.HEAT_MAX, true), EPS);
   }

   @Test
   void presetRadiusRoundTrips() {
      Vec3 at = new Vec3(10.0, 64.0, -3.0);
      assertEquals(6.0, RedHulkVfx.radiusOf(at, RedHulkFx.radius(at, 6.0)), 1.0E-9);
      assertEquals(0.0, RedHulkVfx.radiusOf(at, at.add(0.0, 0.0, 4.0)), 1.0E-9, "a direction is not a radius");
      assertEquals(0.0, RedHulkVfx.radiusOf(at, at), 1.0E-9);
   }

   @Test
   void presetsAreUniqueAndEachHasOneHeatRecipe() {
      Set<Integer> nets = new HashSet<>();
      for (FxPreset p : RedHulkFx.PRESETS) {
         assertTrue(p.id().startsWith("red_hulk:"), p.id());
         assertTrue(p.netId() >= 700 && p.netId() <= 712, p.id() + " net id " + p.netId());
         assertTrue(nets.add(p.netId()), "duplicate " + p.netId());
      }
      assertEquals(13, RedHulkFx.PRESETS.size());
      List<FxRecipe> recipes = RedHulkFxRecipes.all();
      assertEquals(RedHulkFx.PRESETS.size(), recipes.size());
      assertEquals(RedHulkFx.PRESETS.stream().map(FxPreset::id).collect(Collectors.toSet()),
         recipes.stream().map(r -> r.preset().id()).collect(Collectors.toSet()));
      for (FxRecipe r : recipes) {
         assertFalse(r.layers().isEmpty(), r.preset().id() + " has layers");
         for (FxRecipe.Kind electric : new FxRecipe.Kind[]{FxRecipe.Kind.BOLT, FxRecipe.Kind.ARC, FxRecipe.Kind.RING, FxRecipe.Kind.CRACKLE}) {
            assertTrue(r.of(electric).isEmpty(), r.preset().id() + ": heat, not lightning (" + electric + ")");
         }
      }
      RedHulkFxRecipes.init();
      for (FxPreset p : RedHulkFx.PRESETS) {
         assertNotNull(FxRecipe.byNetId(p.netId()), p.id());
      }
      assertTrue(RedHulkFxRecipes.ultRelease().length() > RedHulkFxRecipes.punch().length(), "the ultimate lingers longest");
      assertNotEquals(RedHulkFx.ULT_RELEASE.netId(), RedHulkFx.ULT_CHARGE.netId());
   }
}

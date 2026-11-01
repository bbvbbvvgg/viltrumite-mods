package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollapseRulesTest {
   @Test
   void fallTimeMatchesGravity() {
      // 0.5 * 0.04 * t^2 = 20 blocks -> t = 31.6
      assertEquals(32, CollapseRules.fallTicks(20));
      assertEquals(6, CollapseRules.fallTicks(0));
      assertEquals(40, CollapseRules.fallTicks(500));
   }

   @Test
   void toppleTakesLongerThanAPancake() {
      int topple = CollapseRules.impactTicks(CollapseVariant.TOPPLE, 2, 24);
      int pancake = CollapseRules.impactTicks(CollapseVariant.PANCAKE, 2, 24);
      assertTrue(topple > pancake, topple + " vs " + pancake);
      assertTrue(topple <= 52);
      for (CollapseVariant v : CollapseVariant.values()) {
         assertTrue(CollapseRules.impactTicks(v, 0, 1) >= 6);
      }
   }

   @Test
   void creakTimeIsSeededAndBounded() {
      for (long seed = 0; seed < 200; seed++) {
         int t = CollapseRules.creakTicks(CollapseVariant.TOPPLE, CollapseSeverity.MAJOR, seed * 0x9E3779B97F4A7C15L);
         assertTrue(t >= 30 && t <= 42, "creak " + t);
      }
   }

   @Test
   void shedCountScales() {
      assertTrue(CollapseRules.shedCount(1600, 30) <= 64);
      assertTrue(CollapseRules.shedCount(40, 30) <= 10);
      assertTrue(CollapseRules.shedCount(500, 30) >= 15);
   }

   @Test
   void impactDamageFallsOffFromTheCentre() {
      float centre = CollapseRules.impactDamage(CollapseSeverity.CATASTROPHIC, 0.0);
      float edge = CollapseRules.impactDamage(CollapseSeverity.CATASTROPHIC, 1.0);
      assertTrue(centre > edge);
      assertTrue(CollapseRules.impactDamage(CollapseSeverity.MINOR, 0.0) < centre);
   }

   @Test
   void rubbleIsCapped() {
      assertEquals(CollapseRules.RUBBLE_MAX, CollapseRules.rubbleCount(100000));
      assertEquals(8, CollapseRules.rubbleCount(100));
   }

   @Test
   void causesById() {
      assertEquals(CollapseCause.LEVIATHAN, CollapseCause.byId("leviathan"));
      CollapseCause custom = CollapseCause.byId("ultron_strike");
      assertEquals("ultron_strike", custom.id());
      assertTrue(custom.feeds());
      assertTrue(!CollapseCause.SECONDARY.feeds());
   }

   @Test
   void blockKeyRoundTrip() {
      int[][] pts = {{0, 0, 0}, {-1, -64, -1}, {29999999, 319, -29999999}, {123, 70, -456}};
      for (int[] p : pts) {
         long k = BlockKey.of(p[0], p[1], p[2]);
         assertEquals(p[0], BlockKey.x(k));
         assertEquals(p[1], BlockKey.y(k));
         assertEquals(p[2], BlockKey.z(k));
         assertEquals(net.minecraft.core.BlockPos.asLong(p[0], p[1], p[2]), k);
      }
   }
}

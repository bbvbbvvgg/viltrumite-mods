package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UltronScenarioTest {
   @Test
   void sameSeedSamePlan() {
      for (long seed : new long[]{0L, 1L, 42L, -7L, 123456789L, Long.MAX_VALUE}) {
         UltronScenario a = UltronScenario.of(seed);
         UltronScenario b = UltronScenario.of(seed);
         assertEquals(a.variant(), b.variant());
         assertEquals(a.layout(), b.layout());
         assertEquals(a.sites(), b.sites());
         assertArrayEquals(a.composition(), b.composition());
         assertArrayEquals(a.doctrineWeights(), b.doctrineWeights());
         assertArrayEquals(a.routes(), b.routes());
         assertEquals(a.style(), b.style());
         assertEquals(a.boss(), b.boss());
         assertEquals(a.finale(), b.finale());
         assertEquals(a.uploadTicks(), b.uploadTicks());
         assertEquals(a.timeScale(), b.timeScale());
         assertEquals(a.maxProtocols(), b.maxProtocols());
      }
   }

   @Test
   void seedsCoverEveryVariantLayoutStyleAndBoss() {
      Set<UltronScenario.Variant> variants = EnumSet.noneOf(UltronScenario.Variant.class);
      Set<UltronScenario.Layout> layouts = EnumSet.noneOf(UltronScenario.Layout.class);
      Set<UltronScenario.AttackStyle> styles = EnumSet.noneOf(UltronScenario.AttackStyle.class);
      Set<UltronScenario.BossStyle> bosses = EnumSet.noneOf(UltronScenario.BossStyle.class);
      Set<UltronScenario.FinaleRule> finales = EnumSet.noneOf(UltronScenario.FinaleRule.class);
      Set<String> combos = new HashSet<>();
      Set<String> kindOrders = new HashSet<>();
      for (long seed = 0; seed < 200; seed++) {
         UltronScenario s = UltronScenario.of(seed);
         variants.add(s.variant());
         layouts.add(s.layout());
         styles.add(s.style());
         bosses.add(s.boss());
         finales.add(s.finale());
         combos.add(s.variant() + "/" + s.layout() + "/" + s.style());
         StringBuilder order = new StringBuilder();
         for (UltronScenario.Site site : s.sites()) {
            order.append(site.kind().ordinal());
         }
         kindOrders.add(order.toString());
      }
      assertEquals(EnumSet.allOf(UltronScenario.Variant.class), variants);
      assertEquals(EnumSet.allOf(UltronScenario.Layout.class), layouts);
      assertEquals(EnumSet.allOf(UltronScenario.AttackStyle.class), styles);
      assertEquals(EnumSet.allOf(UltronScenario.BossStyle.class), bosses);
      assertEquals(EnumSet.allOf(UltronScenario.FinaleRule.class), finales);
      assertTrue(combos.size() >= 20, "variant/layout/style combinations: " + combos.size());
      assertTrue(kindOrders.size() >= 100, "node orders: " + kindOrders.size());
   }

   @Test
   void sitesAreWellFormedAndSpacedApart() {
      double worst = Double.MAX_VALUE;
      for (long seed = 0; seed < 1000; seed++) {
         UltronScenario s = UltronScenario.of(seed);
         List<UltronScenario.Site> sites = s.sites();
         assertTrue(sites.size() <= UltronScenario.MAX_SITES, "seed " + seed);
         UltronScenario.Site core = sites.get(0);
         assertEquals(UltronNodeKind.CORE, core.kind());
         assertEquals(0, core.wave());
         int reserve = 0;
         int cores = 0;
         for (int i = 0; i < sites.size(); i++) {
            UltronScenario.Site site = sites.get(i);
            assertEquals(i, site.index());
            assertTrue(site.radius() >= 0.0 && site.radius() <= 0.95, "seed " + seed + " radius " + site.radius());
            assertTrue(site.angle() >= 0.0 && site.angle() < Math.PI * 2.0, "seed " + seed + " angle " + site.angle());
            if (site.wave() == 2) {
               reserve++;
            }
            if (site.kind() == UltronNodeKind.CORE) {
               cores++;
            }
            for (int j = 0; j < i; j++) {
               UltronScenario.Site o = sites.get(j);
               double d = UltronScenario.distance(new double[]{site.angle(), site.radius()}, new double[]{o.angle(), o.radius()});
               worst = Math.min(worst, d);
            }
         }
         assertEquals(1, cores, "seed " + seed);
         assertEquals(2, reserve, "seed " + seed);
         assertEquals(UltronNodeKind.FOUNDRY, sites.get(sites.size() - 2).kind(), "the first reserve site relocates a foundry");
         assertTrue(s.spreadTarget() >= 6 && s.spreadTarget() <= 7, "seed " + seed);
         assertTrue(s.timeScale() >= 0.9F && s.timeScale() <= 1.1F);
         assertTrue(s.uploadTicks() >= 190 * UltronRules.TPS && s.uploadTicks() <= 320 * UltronRules.TPS);
         assertTrue(s.routes().length >= 2 && s.routes().length <= 4);
         for (float w : s.composition()) {
            assertTrue(w > 0.0F);
         }
      }
      // at the default radius (92 blocks) no two nodes stand closer than ~11 blocks
      assertTrue(worst >= 0.12, "closest pair of sites: " + worst);
   }

   @Test
   void everyVariantHasEveryNodeKindItNeeds() {
      for (UltronScenario.Variant v : UltronScenario.Variant.values()) {
         List<UltronNodeKind> plan = UltronScenario.plan(v);
         assertTrue(plan.contains(UltronNodeKind.FOUNDRY), v + " builds units");
         assertTrue(plan.contains(UltronNodeKind.SENSOR), v + " can learn (adaptation needs sensors)");
         assertTrue(plan.contains(UltronNodeKind.UPLINK), v + " can capture machines");
         assertTrue(plan.contains(UltronNodeKind.BULWARK) && plan.contains(UltronNodeKind.REPAIR), v.toString());
      }
   }
}

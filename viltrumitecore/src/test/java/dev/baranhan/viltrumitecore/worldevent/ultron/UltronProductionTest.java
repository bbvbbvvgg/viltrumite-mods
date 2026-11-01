package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UltronProductionTest {
   @Test
   void capsBudgetsAndEventCap() {
      assertTrue(UltronProduction.mayBuild(UltronPhase.SPREAD, 0, 1, 0, 0));
      assertFalse(UltronProduction.mayBuild(UltronPhase.SPREAD, UltronRules.aliveCap(UltronPhase.SPREAD, 1), 1, 0, 0));
      assertFalse(UltronProduction.mayBuild(UltronPhase.SPREAD, 0, 1, UltronRules.phaseBudget(UltronPhase.SPREAD), 0));
      assertFalse(UltronProduction.mayBuild(UltronPhase.MACHINE_WAR, 0, 1, 0, UltronRules.EVENT_PRODUCTION_CAP));
      assertFalse(UltronProduction.mayBuild(UltronPhase.ENDED, 0, 1, 0, 0));
      assertEquals(UltronRules.aliveCap(UltronPhase.MACHINE_WAR, 1) + 2 * UltronRules.CAP_PER_EXTRA_PLAYER,
         UltronRules.aliveCap(UltronPhase.MACHINE_WAR, 3));
      assertEquals(UltronRules.aliveCap(UltronPhase.MACHINE_WAR, 4), UltronRules.aliveCap(UltronPhase.MACHINE_WAR, 40), "extra players are capped");
   }

   @Test
   void nextRespectsKindCaps() {
      UltronScenario s = UltronScenario.of(3L);
      int[] full = new int[UltronUnitKind.values().length];
      for (UltronUnitKind k : UltronUnitKind.values()) {
         full[k.ordinal()] = UltronRules.kindCap(k);
      }
      assertNull(UltronProduction.next(s, full, UltronDoctrine.SIEGE, false, false, 0));
      full[UltronUnitKind.BRUTE.ordinal()]--;
      assertEquals(UltronUnitKind.BRUTE, UltronProduction.next(s, full, UltronDoctrine.SIEGE, false, false, 0));
   }

   @Test
   void missingKindsComeFirstAndAntiAirBuildsInterceptors() {
      UltronScenario s = UltronScenario.of(11L);
      int[] alive = {3, 5, 2, 0, 2};
      assertEquals(UltronUnitKind.INTERCEPTOR, UltronProduction.next(s, alive, UltronDoctrine.SIEGE, true, false, 0));
      int[] someAir = {2, 3, 1, 2, 2};
      int antiAir = 0;
      int plain = 0;
      for (long seed = 0; seed < 100; seed++) {
         UltronScenario sc = UltronScenario.of(seed);
         if (UltronProduction.next(sc, someAir, UltronDoctrine.SIEGE, true, false, 0) == UltronUnitKind.INTERCEPTOR) {
            antiAir++;
         }
         if (UltronProduction.next(sc, someAir, UltronDoctrine.SIEGE, false, false, 0) == UltronUnitKind.INTERCEPTOR) {
            plain++;
         }
      }
      assertTrue(antiAir > plain, "anti-air " + antiAir + " vs plain " + plain);
   }

   /** A whole event worth of production with nothing ever killed: the army stops at the caps, never grows forever. */
   @Test
   void noEndlessArmy() {
      for (long seed = 0; seed < 20; seed++) {
         UltronScenario s = UltronScenario.of(seed);
         int[] alive = new int[UltronUnitKind.values().length];
         int total = 0;
         for (UltronPhase phase : new UltronPhase[]{UltronPhase.AWAKENING, UltronPhase.SPREAD, UltronPhase.MACHINE_WAR, UltronPhase.ADAPTATION, UltronPhase.FINALE}) {
            int built = 0;
            for (int step = 0; step < 1000; step++) {
               int sum = 0;
               for (int a : alive) {
                  sum += a;
               }
               if (!UltronProduction.mayBuild(phase, sum, 4, built, total)) {
                  break;
               }
               UltronUnitKind k = UltronProduction.next(s, alive, UltronDoctrine.SIEGE, step % 2 == 0, false, 1);
               if (k == null) {
                  break;
               }
               alive[k.ordinal()]++;
               built++;
               total++;
               // the units killed meanwhile: a third of them each step in the war phases
               if (phase.atLeast(UltronPhase.MACHINE_WAR) && step % 3 == 0) {
                  for (int i = 0; i < alive.length; i++) {
                     alive[i] = alive[i] * 2 / 3;
                  }
               }
            }
            assertTrue(built <= UltronRules.phaseBudget(phase));
            int sum = 0;
            for (UltronUnitKind kind : UltronUnitKind.values()) {
               assertTrue(alive[kind.ordinal()] <= UltronRules.kindCap(kind));
               sum += alive[kind.ordinal()];
            }
            assertTrue(sum <= UltronRules.aliveCap(phase, 4) || phase == UltronPhase.FINALE && sum <= UltronRules.aliveCap(UltronPhase.ADAPTATION, 4));
         }
         assertTrue(total <= UltronRules.EVENT_PRODUCTION_CAP, "seed " + seed + " built " + total);
      }
   }
}

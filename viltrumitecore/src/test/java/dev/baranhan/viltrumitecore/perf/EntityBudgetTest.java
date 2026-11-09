package dev.baranhan.viltrumitecore.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityBudgetTest {
   @Test
   void nearDebrisOutlivesFarDebrisUnderTheLiveCap() {
      TickBudget budget = new TickBudget("debris", 100);
      budget.reset(100);
      budget.occupy(59);
      assertTrue(budget.tryTake(EntityBudget.priority(false)), "far debris fits below 60% of the cap");
      assertFalse(budget.tryTake(EntityBudget.priority(false)), "far debris stops at 60%");
      for (int i = 61; i <= 85; i++) {
         assertTrue(budget.tryTake(EntityBudget.priority(true)), "near debris up to 85%, at " + i);
      }

      assertFalse(budget.tryTake(EntityBudget.priority(true)), "near debris stops at 85%");
      assertTrue(budget.tryTake(PerfPriority.HIGH), "the rest stays for higher priorities");
      budget.reset(100);
      assertEquals(2, budget.lastDenied());
   }

   @Test
   void occupiedUnitsAreNotCountedAsGranted() {
      TickBudget budget = new TickBudget("debris", 100);
      budget.reset(100);
      budget.occupy(40);
      budget.tryTake(PerfPriority.NORMAL);
      budget.reset(100);
      assertEquals(41, budget.lastUsed());
      assertEquals(1, budget.grantedTotal());
   }

   @Test
   void theCapShrinksWithServerLoad() {
      assertEquals(EntityBudget.BASE, EntityBudget.capacityFor(0.0));
      assertEquals(EntityBudget.FLOOR, EntityBudget.capacityFor(1.0));
      assertTrue(EntityBudget.capacityFor(0.5) < EntityBudget.BASE && EntityBudget.capacityFor(0.5) > EntityBudget.FLOOR);
   }
}

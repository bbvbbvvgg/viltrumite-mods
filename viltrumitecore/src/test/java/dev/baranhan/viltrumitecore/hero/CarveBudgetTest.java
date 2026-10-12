package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reinhard stage 4 Task 8: the carve budget falls with the server's tick time. */
class CarveBudgetTest {
   @Test
   void fullUnder40() {
      assertEquals(2000, CarveBudget.perTick(2000, 0.0));
      assertEquals(2000, CarveBudget.perTick(2000, 25.0));
      assertEquals(2000, CarveBudget.perTick(2000, 40.0));
   }

   @Test
   void scalesDown() {
      assertEquals(250, CarveBudget.perTick(2000, 59.999), 1);
      int mid = CarveBudget.perTick(2000, 50.0);
      assertEquals(1125, mid, "halfway between 2000 and 250");
      int prev = Integer.MAX_VALUE;
      for (double mspt = 40.0; mspt < 60.0; mspt += 0.5) {
         int b = CarveBudget.perTick(2000, mspt);
         assertTrue(b <= prev, "monotone at " + mspt);
         prev = b;
      }
   }

   @Test
   void floorAt50() {
      assertEquals(100, CarveBudget.perTick(2000, 60.0), "base / 20");
      assertEquals(100, CarveBudget.perTick(2000, 500.0));
      assertEquals(50, CarveBudget.perTick(400, 80.0), "never below 50");
      assertEquals(CarveJobs.BASE_BUDGET, dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.CUT_BASE_BUDGET);
   }
}

package dev.baranhan.viltrumitecore.client.aftermath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.ParticleStatus;
import org.junit.jupiter.api.Test;

/** The aftermath particle budget: per-tick rate by particle setting, held back near the live ceiling. */
class AftermathFxBudgetTest {
   @Test
   void rateAndCeilingFollowTheParticleSetting() {
      assertEquals(AftermathFx.BUDGET, AftermathFx.budgetFor(ParticleStatus.ALL));
      assertTrue(AftermathFx.budgetFor(ParticleStatus.DECREASED) < AftermathFx.budgetFor(ParticleStatus.ALL));
      assertTrue(AftermathFx.budgetFor(ParticleStatus.MINIMAL) < AftermathFx.budgetFor(ParticleStatus.DECREASED));
      assertTrue(AftermathFx.liveCapFor(ParticleStatus.MINIMAL) < AftermathFx.liveCapFor(ParticleStatus.DECREASED));
      assertTrue(AftermathFx.liveCapFor(ParticleStatus.DECREASED) < AftermathFx.liveCapFor(ParticleStatus.ALL));
   }

   @Test
   void budgetShrinksToZeroAtTheCeilingAndNeverGoesNegative() {
      for (ParticleStatus status : ParticleStatus.values()) {
         int cap = AftermathFx.liveCapFor(status);
         assertEquals(AftermathFx.budgetFor(status), AftermathFx.budgetFor(status, 0), status.name());
         assertEquals(3, AftermathFx.budgetFor(status, cap - 3), status.name());
         assertEquals(0, AftermathFx.budgetFor(status, cap), status.name());
         assertEquals(0, AftermathFx.budgetFor(status, cap + 500), status.name());
         int last = Integer.MAX_VALUE;
         for (int alive = 0; alive <= cap + 10; alive += 7) {
            int b = AftermathFx.budgetFor(status, alive);
            assertTrue(b >= 0 && b <= last, status + " at " + alive);
            last = b;
         }
      }
   }

   @Test
   void windowCoversARuinPuffLife() {
      // RUIN puffs live 170-280 ticks, PLUME 220-340: the estimate window sits inside that span
      assertTrue(AftermathFx.WINDOW >= 170 && AftermathFx.WINDOW <= 340);
   }
}

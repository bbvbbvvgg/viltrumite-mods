package dev.baranhan.viltrumitecore.hero.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.CarveBudget;
import dev.baranhan.viltrumitecore.hero.CarveJobs;
import org.junit.jupiter.api.Test;

/** Pure queue workload. These are NOT TPS, lighting, fluids or in-game measurements. */
class WorldCutBudgetProtocolTest {
   @Test void twoFullCutsShare2000() {
      var run=WorldCutBudgetProtocol.run(2,160000,t -> 40);
      assertEquals(160,run.ticks()); assertEquals(320000,run.changed());
      assertEquals(2000,run.maxVisited()); assertTrue(run.everyTickWithinBudget());
   }
   @Test void adaptiveLoadLengthensWorkWithoutTuning() {
      var healthy=WorldCutBudgetProtocol.run(1,160000,t -> 40);
      var loaded=WorldCutBudgetProtocol.run(1,160000,t -> 55);
      assertEquals(80,healthy.ticks()); assertTrue(loaded.ticks()>healthy.ticks());
      assertTrue(loaded.everyTickWithinBudget()); assertEquals(160000,loaded.changed());
   }
   @Test void lagSpikeRecoveryStillSharesBudget() {
      var run=WorldCutBudgetProtocol.run(2,160000,t -> t<30 ? 40 : t<90 ? 80 : 40);
      assertTrue(run.everyTickWithinBudget()); assertEquals(320000,run.changed());
      assertTrue(run.ticks()>160); assertTrue(run.ticks()<300);
   }
   @Test void productionConstantsRemainPinned() {
      assertEquals(2000,ReinhardRules.CUT_BASE_BUDGET); assertEquals(2000,CarveJobs.BASE_BUDGET);
      assertEquals(40,CarveBudget.FULL_MSPT); assertEquals(60,CarveBudget.SLOW_MSPT); assertEquals(50,CarveBudget.FLOOR);
      for(int ms=0;ms<=200;ms++) assertTrue(CarveBudget.perTick(2000,ms)<=2000);
   }
}

package dev.baranhan.viltrumitecore.perf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Every budget registers here so that {@code /vperf status} and the overlay can show them. */
public final class PerfRegistry {
   private static final List<TickBudget> BUDGETS = new CopyOnWriteArrayList<>();

   private PerfRegistry() {
   }

   public static TickBudget register(TickBudget budget) {
      BUDGETS.add(budget);
      return budget;
   }

   public static List<TickBudget> all() {
      return BUDGETS;
   }
}

package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

/** Reinhard stage 4 Task 8: queued block work under a per-tick budget. */
class CarveJobsTest {
   /** A job of {@code units} blocks that logs which job worked each call. */
   private static final class Fake implements CarveJobs.Job {
      final String name;
      final int priority;
      int left;
      final List<String> log;

      Fake(String name, int priority, int units, List<String> log) {
         this.name = name;
         this.priority = priority;
         this.left = units;
         this.log = log;
      }

      @Override
      public boolean step(ServerLevel level, int budget) {
         int done = Math.min(budget, this.left);
         this.left -= done;
         for (int i = 0; i < done; i++) {
            this.log.add(this.name);
         }
         return this.left == 0;
      }

      @Override
      public int priority() {
         return this.priority;
      }

      @Override
      public int remainingWork() {
         return this.left;
      }
   }

   @Test
   void respectsBudget() {
      List<String> log = new ArrayList<>();
      CarveJobs.Queue queue = new CarveJobs.Queue();
      queue.submit(new Fake("a", 0, 250, log));
      queue.submit(new Fake("b", 0, 250, log));
      queue.tick(null, 100);
      assertEquals(100, log.size());
      queue.tick(null, 100);
      queue.tick(null, 100);
      assertEquals(300, log.size());
      assertEquals(1, queue.size(), "a finished at 250, b has 200 left");
      queue.tick(null, 1000);
      assertEquals(500, log.size());
      assertEquals(0, queue.size());
      queue.tick(null, 1000);
      assertEquals(500, log.size(), "empty queue does nothing");
   }

   @Test
   void fifoWithinPriority() {
      List<String> log = new ArrayList<>();
      CarveJobs.Queue queue = new CarveJobs.Queue();
      queue.submit(new Fake("first", 0, 30, log));
      queue.submit(new Fake("second", 0, 30, log));
      queue.submit(new Fake("urgent", 5, 30, log));
      queue.tick(null, 40);
      assertEquals(List.of("urgent"), log.subList(0, 30).stream().distinct().toList(), "higher priority first");
      assertEquals(List.of("first"), log.subList(30, 40).stream().distinct().toList(), "then FIFO");
      queue.tick(null, 40);
      assertEquals("first", log.get(59));
      assertEquals("second", log.get(60));
      assertTrue(queue.size() == 1);
   }

   @Test
   void sharedBudget() {
      List<String> log = new ArrayList<>();
      CarveJobs.Queue queue = new CarveJobs.Queue();
      queue.submit(new Fake("cut1", 10, 1500, log));
      queue.submit(new Fake("cut2", 10, 1600, log));
      queue.submit(new Fake("rain", 0, 25, log));
      queue.tick(null, CarveBudget.perTick(CarveJobs.BASE_BUDGET, 40));
      assertEquals(2000, log.size(), "one level budget shared by two cuts");
      assertEquals(1500, log.stream().filter("cut1"::equals).count());
      assertEquals(500, log.stream().filter("cut2"::equals).count());
      assertFalse(log.contains("rain"), "large high-priority cuts come first");
      int before = log.size();
      queue.tick(null, CarveBudget.perTick(CarveJobs.BASE_BUDGET, 55));
      assertTrue(log.size() - before <= CarveBudget.perTick(2000, 55));
      before = log.size();
      queue.tick(null, CarveBudget.perTick(CarveJobs.BASE_BUDGET, 40));
      assertTrue(log.size() - before <= 2000);
      assertEquals(3125, log.size());
      assertEquals(0, queue.size());
   }
}

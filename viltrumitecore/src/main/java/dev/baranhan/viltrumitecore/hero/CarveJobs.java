package dev.baranhan.viltrumitecore.hero;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Hero-neutral queue of block work (grooves, cuts; Reinhard stage 4+), spread over
 * ticks: per level one queue, each END level tick the queue gets
 * {@link CarveBudget#perTick}({@link #BASE_BUDGET}, mean tick ms) blocks. Higher
 * priority first, FIFO inside one priority; a job takes what it still needs
 * ({@link Job#remainingWork}) and the rest goes on to the next job. A level that
 * unloads drops its queue (the work is simply not done).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class CarveJobs {
   /** Blocks per tick on a healthy server (shared with Reinhard's cuts, stage 5 reuses it). */
   public static final int BASE_BUDGET = 2000;
   private static final Map<ResourceKey<Level>, Queue> QUEUES = new HashMap<>();

   private CarveJobs() {
   }

   /** One piece of block work. */
   public interface Job {
      /** Works through at most {@code budget} blocks; true when done. */
      boolean step(@Nullable ServerLevel level, int budget);

      int priority();

      /** Blocks still to do (the budget a step may use up); unknown = all of it. */
      default int remainingWork() {
         return Integer.MAX_VALUE;
      }
   }

   public static void submit(ServerLevel level, Job job) {
      QUEUES.computeIfAbsent(level.dimension(), key -> new Queue()).submit(job);
   }

   /** Actual queue ownership, including jobs discarded after a failed step. */
   public static boolean pending(ServerLevel level, Job job) {
      Queue queue=QUEUES.get(level.dimension());
      return queue!=null && queue.contains(job);
   }

   public static void tick(ServerLevel level) {
      Queue queue = QUEUES.get(level.dimension());
      if (queue == null || queue.size() == 0) {
         return;
      }

      queue.tick(level, CarveBudget.perTick(BASE_BUDGET, level.getServer().getAverageTickTime()));
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         QUEUES.remove(level.dimension());
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      QUEUES.clear();
   }

   /** One level's jobs. Pure apart from what the jobs themselves do. */
   public static final class Queue {
      /** Highest priority first. */
      private final TreeMap<Integer, ArrayDeque<Job>> byPriority = new TreeMap<>((a, b) -> Integer.compare(b, a));

      public void submit(Job job) {
         this.byPriority.computeIfAbsent(job.priority(), p -> new ArrayDeque<>()).add(job);
      }

      public boolean contains(Job job) {
         return byPriority.values().stream().anyMatch(jobs -> jobs.stream().anyMatch(candidate -> candidate==job));
      }

      public int size() {
         int n = 0;
         for (ArrayDeque<Job> jobs : this.byPriority.values()) {
            n += jobs.size();
         }

         return n;
      }

      public void tick(@Nullable ServerLevel level, int budget) {
         int left = budget;
         Iterator<ArrayDeque<Job>> classes = this.byPriority.values().iterator();
         while (left > 0 && classes.hasNext()) {
            ArrayDeque<Job> jobs = classes.next();
            while (left > 0 && !jobs.isEmpty()) {
               Job job = jobs.peekFirst();
               int give = Math.min(left, Math.max(1, job.remainingWork()));
               boolean done;
               try {
                  done = job.step(level, give);
               } catch (RuntimeException e) {
                  done = true;
               }

               left -= give;
               if (done) {
                  jobs.pollFirst();
               } else {
                  break;
               }
            }

            if (jobs.isEmpty()) {
               classes.remove();
            }
         }
      }
   }
}

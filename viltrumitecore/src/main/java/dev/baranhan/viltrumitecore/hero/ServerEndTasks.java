package dev.baranhan.viltrumitecore.hero;

import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Main-thread transfers run after entity iteration, never inline like server.execute can. */
public final class ServerEndTasks {
   private static final Queue<MinecraftServer> TASKS = new Queue<>();
   private ServerEndTasks() {}
   public static void enqueue(MinecraftServer server, Runnable task) {TASKS.enqueue(server, task);}
   public static void flush(MinecraftServer server) {TASKS.flush(server, error -> LogUtils.getLogger().error("Server END task failed", error));}
   public static void clear(MinecraftServer server) {TASKS.clear(server);}

   /** Snapshot-before-run: a transfer's follow-up waits for the next END. */
   public static final class Queue<K> {
      private final Map<K,List<Runnable>> pending = new HashMap<>();
      public void enqueue(K server, Runnable task) {pending.computeIfAbsent(server, key -> new ArrayList<>()).add(Objects.requireNonNull(task));}
      public void flush(K server, Consumer<Throwable> report) {
         List<Runnable> batch = pending.remove(server);
         if(batch == null) return;
         for(Runnable task : batch) try {task.run();} catch(RuntimeException error) {report.accept(error);}
      }
      public void clear(K server) {pending.remove(server);}
   }
}

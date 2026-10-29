package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A-m3: the hammer store announces its changes only after they are written, in order, and a failing listener stops nothing. */
class MjolnirEventQueueTest {
   @Test
   void eventsWaitForTheFlushAndKeepTheirOrder() {
      MjolnirEventQueue<String> queue = new MjolnirEventQueue<>();
      List<String> seen = new ArrayList<>();
      queue.add("state");
      queue.add("catch");
      assertTrue(seen.isEmpty(), "nothing is delivered while the change is written");
      queue.flush(seen::add);
      assertEquals(List.of("state", "catch"), seen);
      assertEquals(0, queue.size());
   }

   @Test
   void eventsCausedByAListenerArriveInTheSameFlushAfterTheOthers() {
      MjolnirEventQueue<String> queue = new MjolnirEventQueue<>();
      List<String> seen = new ArrayList<>();
      queue.add("a");
      queue.add("b");
      queue.flush(event -> {
         seen.add(event);
         if (event.equals("a")) {
            queue.add("caused");
            queue.flush(seen::add);
         }
      });
      assertEquals(List.of("a", "b", "caused"), seen, "a nested flush is a no-op, the outer one delivers");
   }

   @Test
   void aThrowingListenerDoesNotStopTheRest() {
      MjolnirEventQueue<String> queue = new MjolnirEventQueue<>();
      List<String> seen = new ArrayList<>();
      queue.add("boom");
      queue.add("next");
      queue.flush(event -> {
         if (event.equals("boom")) {
            throw new IllegalStateException("listener");
         }

         seen.add(event);
      });
      assertEquals(List.of("next"), seen);
      queue.add("later");
      queue.flush(seen::add);
      assertEquals(List.of("next", "later"), seen, "the queue is usable after a failure");
   }

   /** Source contract: the store only queues; the bus is reached from flush() alone (no post in the middle of a transition). */
   @Test
   void theStoreReachesTheBusOnlyThroughTheFlush() throws IOException {
      Path root = Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/hammer");
      String world = Files.readString(root.resolve("MjolnirWorld.java"));
      assertEquals(0, count(world, "ThorEvents.post("), "MjolnirWorld never posts in the middle of a change");
      assertEquals(1, count(world, "ThorEvents::post"), "the bus is reached once, in flush()");
      assertTrue(world.contains("EVENTS.flush(ThorEvents::post)"));
      try (var files = Files.list(root)) {
         for (Path file : (Iterable<Path>)files::iterator) {
            if (!file.getFileName().toString().equals("MjolnirWorld.java")) {
               String text = Files.readString(file);
               assertFalse(text.contains("ThorEvents.post") || text.contains("ThorEvents::post"), file.getFileName() + " posts around the store");
            }
         }
      }
   }

   private static int count(String text, String needle) {
      int n = 0;
      for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
         n++;
      }

      return n;
   }
}

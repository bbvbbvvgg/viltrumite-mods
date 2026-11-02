package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.hero.book.BookPages;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "memory" page of a book: recent deeds of bodies seen by the server this session (kills, harm to players, who
 * hurt them), kept in memory with a bounded LRU. Nothing private: only what happened in the open world.
 */
public final class RohanChronicle {
   static final int MAX_BODIES = 4096;
   static final int MAX_LINES = 5;
   private static final Map<UUID, Deque<BookPages.Line>> DEEDS = new LinkedHashMap<>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<UUID, Deque<BookPages.Line>> eldest) {
         return this.size() > MAX_BODIES;
      }
   };

   private RohanChronicle() {
   }

   public static synchronized void record(UUID who, String key, String... args) {
      Deque<BookPages.Line> deque = DEEDS.computeIfAbsent(who, k -> new ArrayDeque<>());
      deque.addFirst(new BookPages.Line(key, List.of(args)));
      while (deque.size() > MAX_LINES) {
         deque.removeLast();
      }
   }

   public static synchronized List<BookPages.Line> of(UUID who) {
      Deque<BookPages.Line> deque = DEEDS.get(who);
      return deque == null ? List.of() : new ArrayList<>(deque);
   }

   public static synchronized void clear() {
      DEEDS.clear();
   }
}

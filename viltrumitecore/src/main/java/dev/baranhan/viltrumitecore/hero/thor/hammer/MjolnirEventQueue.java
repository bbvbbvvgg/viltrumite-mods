package dev.baranhan.viltrumitecore.hero.thor.hammer;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * A-m3: the hammer store announces a change only after the whole change is
 * written. {@link MjolnirWorld} queues every bridge event while it mutates
 * the record and flushes the queue at the end of each public entry point
 * (and of the world / body tick), so a listener always reads the finished
 * state, never a half-written transition. Events a listener causes while the
 * queue drains are delivered in the same flush, in order. A listener that
 * throws cannot stop the rest (the bus logs it; this queue also guards).
 * Server main thread only.
 */
final class MjolnirEventQueue<E> {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Deque<E> pending = new ArrayDeque<>();
   private boolean draining;

   void add(E event) {
      this.pending.addLast(event);
   }

   int size() {
      return this.pending.size();
   }

   /** Deliver everything queued (also what the delivery itself queues); a nested flush is a no-op. */
   void flush(Consumer<E> sink) {
      if (this.draining) {
         return;
      }

      this.draining = true;
      try {
         E event;
         while ((event = this.pending.pollFirst()) != null) {
            try {
               sink.accept(event);
            } catch (RuntimeException e) {
               LOGGER.error("Mjolnir: a hammer event listener failed", e);
            }
         }
      } finally {
         this.draining = false;
      }
   }

   void clear() {
      this.pending.clear();
   }
}

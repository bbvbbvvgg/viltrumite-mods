package dev.baranhan.viltrumitecore.hero.thor.bridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Typed event bus between the Thor sessions (the contract's onMeleeHit /
 * onLethalHit / onComboEnd / onThrow / onRecall / onCatch /
 * onHammerStateChanged are {@code subscribe(owner, type, listener)}). No
 * listener is registered by the foundation: posting is a no-op until a
 * session subscribes. Listeners of
 * different owners are independent; one owner subscribes at most once per
 * type. Server main thread.
 */
public final class ThorEvents {
   @FunctionalInterface
   public interface Listener {
      void on(ThorEvent event);
   }

   private record Subscription(String owner, Listener listener) {
   }

   /** How many recent event ids are remembered for dedupe. */
   public static final int DEDUPE_WINDOW = 512;

   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/thor");
   private static final Map<ThorEventType, List<Subscription>> LISTENERS = new EnumMap<>(ThorEventType.class);
   private static final Set<Long> SEEN = new HashSet<>();
   private static final ArrayDeque<Long> ORDER = new ArrayDeque<>();
   private static long nextId = 1;

   private ThorEvents() {
   }

   public static synchronized long nextId() {
      return nextId++;
   }

   public static synchronized void subscribe(String owner, ThorEventType type, Listener listener) {
      if (owner == null || owner.isBlank() || type == null || listener == null) {
         throw new IllegalArgumentException("owner, type and listener are required");
      }

      List<Subscription> list = LISTENERS.computeIfAbsent(type, t -> new ArrayList<>());
      for (Subscription subscription : list) {
         if (subscription.owner().equals(owner)) {
            throw new IllegalStateException(owner + " already listens to " + type);
         }
      }

      list.add(new Subscription(owner, listener));
   }

   /** Owners listening to a type, in subscription order. */
   public static synchronized List<String> owners(ThorEventType type) {
      List<String> owners = new ArrayList<>();
      for (Subscription subscription : LISTENERS.getOrDefault(type, Collections.emptyList())) {
         owners.add(subscription.owner());
      }

      return owners;
   }

   /**
    * Deliver to every listener of the type. False when the id was already
    * posted (nothing delivered again). A throwing listener does not stop the
    * others and never reaches the poster: posters sit on the damage path and
    * in the middle of hammer-store transitions, so the failure is logged
    * (owner, type, event id, stack) and delivery goes on.
    */
   public static boolean post(ThorEvent event) {
      List<Subscription> targets;
      synchronized (ThorEvents.class) {
         if (!SEEN.add(event.eventId())) {
            return false;
         }

         ORDER.addLast(event.eventId());
         if (ORDER.size() > DEDUPE_WINDOW) {
            SEEN.remove(ORDER.removeFirst());
         }

         targets = new ArrayList<>(LISTENERS.getOrDefault(event.type(), Collections.emptyList()));
      }

      for (Subscription subscription : targets) {
         try {
            subscription.listener().on(event);
         } catch (RuntimeException e) {
            LOGGER.error("Thor bridge listener of owner {} failed on {} (event {})", subscription.owner(), event.type(), event.eventId(), e);
         }
      }

      return true;
   }

   /** Tests only (also clears the lifecycle bus, ThorLifecycle). */
   public static synchronized void resetForTests() {
      ThorLifecycle.resetForTests();
      LISTENERS.clear();
      SEEN.clear();
      ORDER.clear();
      nextId = 1;
   }
}

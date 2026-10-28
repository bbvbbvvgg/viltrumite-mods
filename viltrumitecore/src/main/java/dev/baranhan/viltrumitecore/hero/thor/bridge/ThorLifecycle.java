package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle stop of one Thor body, published by the foundation
 * (ThorActions.stop) to the sessions that keep their own transient state
 * beside {@link ThorState} (scheduled effects, a running storm, a chain,
 * the scene lock or camera lease they own). The foundation never calls a
 * session directly: a session subscribes once (common setup) and clears what
 * it owns. Listeners run after the running move heard onStop and before the
 * bridge flags (lock, lease, windows) are dropped, so a lock / lease owner
 * still sees what it holds. Server main thread.
 */
public final class ThorLifecycle {
   /** Why the body stops. */
   public enum Cause {
      DIMENSION_CHANGE,
      DEATH,
      DISCONNECT,
      HERO_CHANGE,
      /** Death without a replacement entity (erasure / limbo). */
      ERASED,
      /** The body is replaced by a clone (respawn, End return): the old state is left behind. */
      CLONE,
      /**
       * Dev / QA reset (/thor foundation release_locks): the running move,
       * the sessions' transient state and the lock / lease owners are told,
       * exactly as on a lifecycle stop, instead of their flags vanishing.
       */
      DEV_RESET;

      public static Cause of(CleanupReason reason) {
         return switch (reason) {
            case DEATH -> DEATH;
            case DISCONNECT -> DISCONNECT;
            case HERO_CHANGE -> HERO_CHANGE;
            case ERASED -> ERASED;
         };
      }
   }

   @FunctionalInterface
   public interface Listener {
      /** {@code player} is null only in pure tests. */
      void onStop(@Nullable ServerPlayer player, ThorState state, Cause cause);
   }

   private record Subscription(String owner, Listener listener) {
   }

   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/thor");
   private static final List<Subscription> LISTENERS = new ArrayList<>();

   private ThorLifecycle() {
   }

   /** Once per owner (idempotent registration is the owner's job, as for ThorMoves / ThorEvents). */
   public static synchronized void subscribe(String owner, Listener listener) {
      if (owner == null || owner.isBlank() || listener == null) {
         throw new IllegalArgumentException("owner and listener are required");
      }

      for (Subscription subscription : LISTENERS) {
         if (subscription.owner().equals(owner)) {
            throw new IllegalStateException(owner + " already listens to the Thor lifecycle");
         }
      }

      LISTENERS.add(new Subscription(owner, listener));
   }

   public static synchronized List<String> owners() {
      List<String> owners = new ArrayList<>();
      for (Subscription subscription : LISTENERS) {
         owners.add(subscription.owner());
      }

      return owners;
   }

   /** Every listener hears the stop; a throwing one is logged and never stops the others or the poster. */
   public static void post(@Nullable ServerPlayer player, ThorState state, Cause cause) {
      List<Subscription> targets;
      synchronized (ThorLifecycle.class) {
         targets = new ArrayList<>(LISTENERS);
      }

      for (Subscription subscription : targets) {
         try {
            subscription.listener().onStop(player, state, cause);
         } catch (RuntimeException e) {
            LOGGER.error("Thor lifecycle listener of owner {} failed on {}", subscription.owner(), cause, e);
         }
      }
   }

   /** Tests only. */
   public static synchronized void resetForTests() {
      LISTENERS.clear();
   }
}

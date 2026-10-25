package dev.baranhan.viltrumitecore.client.hero;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client: when each entity last took an air step (the local prediction or an observed
 * server step), so body renderers can play a short step pulse. Render data only.
 */
public final class AirStepPulse {
   private static final Map<Object, Long> LAST = new WeakHashMap<>();

   private AirStepPulse() {
   }

   public static void mark(Object entity, long nanos) {
      LAST.put(entity, nanos);
   }

   /** Seconds since the last step of {@code entity}, or -1 when it never stepped. */
   public static double seconds(Object entity, long nanos) {
      Long at = LAST.get(entity);
      return at == null ? -1 : Math.max(0, (nanos - at) / 1.0E9);
   }

   public static void forget(Object entity) {
      LAST.remove(entity);
   }
}

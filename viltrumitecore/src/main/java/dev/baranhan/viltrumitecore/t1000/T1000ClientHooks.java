package dev.baranhan.viltrumitecore.t1000;

import java.util.function.BiConsumer;

/**
 * Seam from the common entity to its client effects (droplets on entity events). The client setup installs the
 * handler; a dedicated server keeps the no-op and never loads a client class.
 */
public final class T1000ClientHooks {
   private static volatile BiConsumer<T1000Entity, Byte> events = (e, id) -> {
   };

   private T1000ClientHooks() {
   }

   public static void install(BiConsumer<T1000Entity, Byte> handler) {
      events = handler;
   }

   static void event(T1000Entity e, byte id) {
      events.accept(e, id);
   }
}

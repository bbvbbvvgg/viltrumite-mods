package dev.baranhan.viltrumitecore.hero.fx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side "a fight is happening here" signal. Hero FX (impacts, slams, shards,
 * bolts, presets) emit it even when an ability deals no damage, so world systems
 * (the living city's danger map) can react without hero code depending on them.
 */
public final class CombatNoise {
   public interface Listener {
      void onNoise(ServerLevel level, Vec3 at, float radius);
   }

   private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

   private CombatNoise() {
   }

   public static void listen(Listener listener) {
      LISTENERS.add(listener);
   }

   public static void emit(ServerLevel level, Vec3 at, float radius) {
      if (level == null || at == null || LISTENERS.isEmpty()) {
         return;
      }

      for (Listener l : LISTENERS) {
         l.onNoise(level, at, radius);
      }
   }
}

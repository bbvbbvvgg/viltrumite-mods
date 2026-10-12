package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Ticks into Reid's current phase for one player, for the clips and the blade.
 * The draw uses the scene packet's start time (a relog mid-draw stays right);
 * without a scene (out of its range) and for the 1 s sheathe it counts from the
 * first frame the phase was seen. Render thread only.
 */
public final class ReidClock {
   private static final class Seen {
      ReinhardPoses.ReidPhase phase;
      long since;
   }

   private static final Map<Entity, Seen> SEEN = new WeakHashMap<>();

   private ReidClock() {
   }

   public static double ticks(Entity player, ReinhardPoses.ReidPhase phase, float partialTick) {
      long now = player.level().getGameTime();
      Seen seen = SEEN.computeIfAbsent(player, key -> new Seen());
      if (seen.phase != phase) {
         seen.phase = phase;
         seen.since = now;
      }

      if (phase == ReinhardPoses.ReidPhase.DRAWING) {
         ReidDrawScene.Active scene = ReidDrawScene.of(player.getId());
         if (scene != null) {
            return Math.max(0.0, now - scene.startGameTime() + partialTick);
         }
      }

      return Math.max(0.0, now - seen.since + partialTick);
   }
}

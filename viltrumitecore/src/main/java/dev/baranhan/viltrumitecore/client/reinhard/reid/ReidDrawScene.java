package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/**
 * Client side of the Reid draw scene (spec §7.1): the active scenes by caster
 * entity id, fed by {@link ReidSceneS2CPacket}. A START refresh keeps (or
 * re-creates, for a player who walked into range) the scene; END drops it.
 */
public final class ReidDrawScene {
   /** One running scene: where it started and the server game time of tick 0. */
   public record Active(int casterId, BlockPos origin, long startGameTime) {
   }

   private static final Map<Integer, Active> ACTIVE = new HashMap<>();

   private ReidDrawScene() {
   }

   public static void onPacket(int casterId, BlockPos origin, long startGameTime, int phase) {
      if (phase == ReidSceneS2CPacket.END) {
         ACTIVE.remove(casterId);
      } else {
         ACTIVE.put(casterId, new Active(casterId, origin, startGameTime));
      }
   }

   @Nullable
   public static Active of(int casterId) {
      return ACTIVE.get(casterId);
   }

   public static Iterable<Active> active() {
      return ACTIVE.values();
   }

   public static void clear() {
      ACTIVE.clear();
   }
}

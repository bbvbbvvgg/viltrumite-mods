package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Reid draw scene (spec §7.1): the active scenes by caster
 * entity id, fed by {@link ReidSceneS2CPacket}. A START refresh keeps (or
 * re-creates, for a player who walked into range) the scene; END drops it.
 * A scene whose END never arrives ends itself {@link #TIMEOUT_TICKS} after
 * the expected end. Visuals: {@link ReidDrawSceneVfx}. Render thread only.
 */
public final class ReidDrawScene {
   /** One running scene: where it started and the server game time of tick 0. */
   public record Active(int casterId, Vec3 center, long startGameTime) {
   }

   /** Ticks after the expected end before a scene without its END packet stops by itself. */
   public static final int TIMEOUT_TICKS = 40;
   public static final float CRACK_RADIUS_MIN = 2.0F;
   public static final float CRACK_RADIUS_MAX = 14.0F;
   private static final Map<Integer, Active> ACTIVE = new HashMap<>();

   private ReidDrawScene() {
   }

   public static void onPacket(int casterId, Vec3 center, long startGameTime, int phase) {
      if (phase == ReidSceneS2CPacket.END) {
         ACTIVE.remove(casterId);
      } else {
         ACTIVE.put(casterId, new Active(casterId, center, startGameTime));
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

   /** Drops the scenes past their expected end + {@link #TIMEOUT_TICKS}. */
   public static void prune(long gameTime) {
      ACTIVE.values().removeIf(scene -> gameTime - scene.startGameTime() > ReinhardRules.REID_DRAW_TICKS + TIMEOUT_TICKS);
   }

   /** Pure: draw progress 0..1 from the packet's start time (a relog mid-draw stays correct). */
   public static float progress(long startGameTime, long gameTime, float partialTick) {
      float t = (float)(gameTime - startGameTime) + partialTick;
      return Math.max(0.0F, Math.min(1.0F, t / ReinhardRules.REID_DRAW_TICKS));
   }

   /** Pure: crack ring radius at draw tick {@code t}: none before the cracks, 2 → 14 blocks over the crack window. */
   public static float crackRadius(float t) {
      if (t < ReinhardRules.REID_CRACK_START) {
         return 0.0F;
      }

      float k = Math.min(1.0F, (t - ReinhardRules.REID_CRACK_START) / (float)(ReinhardRules.REID_CRACK_END - ReinhardRules.REID_CRACK_START));
      return CRACK_RADIUS_MIN + (CRACK_RADIUS_MAX - CRACK_RADIUS_MIN) * k;
   }
}

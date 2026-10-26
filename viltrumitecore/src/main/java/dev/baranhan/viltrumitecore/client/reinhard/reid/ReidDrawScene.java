package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Reid draw scene (spec §7.1, REINHARD OVERHAUL item 15): the scenes by caster
 * entity id, fed by {@link ReidSceneS2CPacket}. One scene per draw, keyed by the server start time:
 * a START or RELEASE refresh with the same start keeps the running scene (no restart, no second
 * sound), a START for a draw that already ended is ignored, a newer start replaces an older scene,
 * and a player who walked into range mid-draw gets it at the right moment. END lets the scene decay
 * ({@link #TAIL_TICKS}); ABORT fades it out at once ({@link #ABORT_FADE_TICKS}). A scene whose END
 * never arrives stops by itself {@link #TIMEOUT_TICKS} after the expected end. Visuals:
 * {@link ReidDrawSceneVfx}, sound: {@link ReidDrawAudio}. Render thread only.
 */
public final class ReidDrawScene {
   /** No end yet. */
   public static final long LIVE = Long.MIN_VALUE;

   /** One scene: where it started, the server game time of draw tick 0, and when and how it ended. */
   public record Active(int casterId, Vec3 center, long startGameTime, long endedAt, boolean aborted) {
      public Active(int casterId, Vec3 center, long startGameTime) {
         this(casterId, center, startGameTime, LIVE, false);
      }

      public boolean ending() {
         return this.endedAt != LIVE;
      }

      Active end(long now, boolean abort) {
         return new Active(this.casterId, this.center, this.startGameTime, now, abort);
      }
   }

   /** Ticks after the expected end before a scene without its END packet stops by itself. */
   public static final int TIMEOUT_TICKS = 40;
   /** A finished draw keeps its scene this long after END: the light and the sound decay. */
   public static final int TAIL_TICKS = 24;
   /** An aborted draw fades out over this many ticks. */
   public static final int ABORT_FADE_TICKS = 8;
   public static final float CRACK_RADIUS_MIN = 2.0F;
   public static final float CRACK_RADIUS_MAX = 14.0F;
   private static final Map<Integer, Active> ACTIVE = new HashMap<>();
   /** Start time of the last ended draw per caster: a late refresh of it must not start it again. */
   private static final Map<Integer, Long> FINISHED = new HashMap<>();

   private ReidDrawScene() {
   }

   /** Packet entry (render thread): stamps the client game time. */
   public static void onPacket(int casterId, Vec3 center, long startGameTime, int phase) {
      Minecraft client = Minecraft.getInstance();
      onPacket(casterId, center, startGameTime, phase, client.level == null ? startGameTime : client.level.getGameTime());
   }

   public static void onPacket(int casterId, Vec3 center, long startGameTime, int phase, long now) {
      Active current = ACTIVE.get(casterId);
      if (phase == ReidSceneS2CPacket.END || phase == ReidSceneS2CPacket.ABORT) {
         Long finished = FINISHED.get(casterId);
         if (finished == null || finished < startGameTime) {
            FINISHED.put(casterId, startGameTime);
         }

         if (current != null && current.startGameTime() <= startGameTime && !current.ending()) {
            ACTIVE.put(casterId, current.end(now, phase == ReidSceneS2CPacket.ABORT));
         }

         return;
      }

      // START / RELEASE: the running scene of this draw stays as it is.
      if (current != null && current.startGameTime() >= startGameTime) {
         return;
      }

      Long finished = FINISHED.get(casterId);
      if (finished != null && finished >= startGameTime) {
         return;
      }

      ACTIVE.put(casterId, new Active(casterId, center, startGameTime));
   }

   /** The live (not ending) scene of a caster: the draw clock of his animation. */
   @Nullable
   public static Active of(int casterId) {
      Active scene = ACTIVE.get(casterId);
      return scene == null || scene.ending() ? null : scene;
   }

   /** Any scene of a caster, including one that is decaying or fading out. */
   @Nullable
   public static Active get(int casterId) {
      return ACTIVE.get(casterId);
   }

   /** Every scene to render or hear, including the decaying and fading ones. */
   public static List<Active> all() {
      return new ArrayList<>(ACTIVE.values());
   }

   public static void clear() {
      ACTIVE.clear();
      FINISHED.clear();
   }

   /** Drops the ended scenes after their tail or fade, and the scenes past their expected end + {@link #TIMEOUT_TICKS}. */
   public static void prune(long gameTime) {
      ACTIVE.values().removeIf(scene -> scene.ending()
         ? gameTime - scene.endedAt() > (scene.aborted() ? ABORT_FADE_TICKS : TAIL_TICKS)
         : gameTime - scene.startGameTime() > ReinhardRules.REID_DRAW_TICKS + TIMEOUT_TICKS);
   }

   /** Pure: 1 while the scene runs, then down to 0 over the abort fade; a natural end keeps 1 (its tail is timed by the draw). */
   public static float fade(Active scene, long gameTime, float partialTick) {
      if (!scene.ending() || !scene.aborted()) {
         return 1.0F;
      }

      float since = (float)(gameTime - scene.endedAt()) + partialTick;
      return Math.max(0.0F, Math.min(1.0F, 1.0F - since / ABORT_FADE_TICKS));
   }

   /** Pure: draw tick of the scene at a client game time, never negative. */
   public static float tick(Active scene, long gameTime, float partialTick) {
      return Math.max(0.0F, (float)(gameTime - scene.startGameTime()) + partialTick);
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

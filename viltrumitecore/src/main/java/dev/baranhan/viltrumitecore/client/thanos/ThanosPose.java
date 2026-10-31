package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import javax.annotation.Nullable;

/**
 * Pure pose and light curves of Thanos (no Minecraft classes): move clip timing with the phase's
 * telegraph, clip blend weights and the glow of each Infinity Stone in the fight and the scenes.
 */
public final class ThanosPose {
   /** Clip blend-in and blend-out of a move, ticks. */
   public static final float MOVE_IN = 3.0F;
   public static final float MOVE_OUT = 5.0F;
   /** Stone glow in the fight: resting level and the final minute's brighter level. */
   public static final float REST_GLOW = 0.42F;
   public static final float FINAL_GLOW = 0.74F;

   private ThanosPose() {
   }

   /**
    * Clip ticks for {@code elapsed} ticks into a move. The clips are authored with the base
    * telegraph; the server may shorten it (final minute), so the wind-up part is scaled to the real
    * telegraph and the strike keeps its authored speed.
    */
   public static float clipTicks(float elapsed, int baseTelegraph, int realTelegraph) {
      if (!(elapsed > 0.0F)) {
         return 0.0F;
      }

      int real = Math.max(1, realTelegraph);
      if (elapsed < real) {
         return elapsed / real * baseTelegraph;
      }

      return baseTelegraph + (elapsed - real);
   }

   /** Weight of a clip running {@code length} ticks: in over {@link #MOVE_IN}, out over {@link #MOVE_OUT}. */
   public static float clipWeight(float elapsed, float length) {
      if (!(elapsed >= 0.0F) || elapsed >= length) {
         return 0.0F;
      }

      float in = Math.min(1.0F, elapsed / MOVE_IN);
      float out = Math.min(1.0F, (length - elapsed) / MOVE_OUT);
      return smooth(Math.min(in, out));
   }

   /** Charge of a move's stones 0..1: rises over the telegraph, full while active, falls in the recovery. */
   public static float charge(ThanosMove move, float elapsed, int realTelegraph) {
      if (!(elapsed >= 0.0F)) {
         return 0.0F;
      }

      int tel = Math.max(1, realTelegraph);
      if (elapsed < tel) {
         return smooth(elapsed / tel);
      }

      if (elapsed < tel + move.active) {
         return 1.0F;
      }

      return 1.0F - smooth((elapsed - tel - move.active) / Math.max(1, move.recovery));
   }

   /** Fight glow of one stone: resting shimmer (brighter in the final minute) plus the move's charge. */
   public static float fightGlow(int stone, float age, boolean finalPhase, @Nullable ThanosMove move, float elapsed, int realTelegraph) {
      float base = finalPhase
         ? FINAL_GLOW + 0.16F * (0.5F + 0.5F * (float)Math.sin(age * 0.33F + stone * 1.1F))
         : REST_GLOW + 0.07F * (float)Math.sin(age * 0.12F + stone * 1.3F);
      if (move == null || !move.uses(ThanosStone.byIndex(stone))) {
         return base;
      }

      float k = charge(move, elapsed, realTelegraph) * (move.lead().ordinal() == stone ? 1.0F : 0.75F);
      float flicker = elapsed >= realTelegraph && elapsed < realTelegraph + move.active ? 0.12F * (float)Math.sin(age * 2.3F + stone) : 0.0F;
      return base + (1.35F - base) * k + flicker;
   }

   /** Intro: the stones wake as he clenches the gauntlet (60..80 of the 80-tick intro). */
   public static float introGlow(int stone, float t) {
      if (t < 56.0F) {
         return 0.18F;
      }

      float u = smooth((t - 56.0F - stone * 2.0F) / 10.0F);
      return 0.18F + 1.1F * u - 0.4F * smooth((t - 74.0F) / 6.0F) * u;
   }

   /** Snap scene: one stone after another lights (44 + 6 i), all blaze on the snap, then burn out. */
   public static float snapGlow(int stone, float t) {
      if (t < ThanosSceneTimeline.STONES_START) {
         return 0.3F;
      }

      if (t < ThanosSceneTimeline.SNAP) {
         float since = t - (ThanosSceneTimeline.STONES_START + stone * ThanosSceneTimeline.STONE_STEP);
         if (since < 0.0F) {
            return 0.22F;
         }

         // a flare when it lights, then a steady glow that swells while the fingers fold
         float flare = 0.55F * (float)Math.exp(-since / 3.5F);
         float fold = t >= ThanosSceneTimeline.FACE_END ? 0.3F * smooth((t - ThanosSceneTimeline.FACE_END) / 38.0F) : 0.0F;
         return 0.85F + flare + fold;
      }

      float u = t - ThanosSceneTimeline.SNAP;
      if (u < 12.0F) {
         return 2.0F - u / 12.0F * 0.6F;
      }

      return Math.max(0.1F, 1.4F - 1.3F * smooth((u - 12.0F) / 70.0F));
   }

   /** Victory (his defeat): the stones go out one after another after the kneel. */
   public static float victoryGlow(int stone, float t) {
      int lit = ThanosSceneTimeline.victoryStonesLit(t);
      if (stone >= lit) {
         return 0.0F;
      }

      float stagger = t < ThanosSceneTimeline.VICTORY_STAGGER_END ? 0.3F * (float)Math.abs(Math.sin(t * 1.7F + stone)) : 0.0F;
      return 0.55F + stagger;
   }

   static float clamp(float v) {
      return Float.isFinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 0.0F;
   }

   public static float smooth(float v) {
      float t = clamp(v);
      return t * t * (3.0F - 2.0F * t);
   }
}

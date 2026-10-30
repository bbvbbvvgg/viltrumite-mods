package dev.baranhan.viltrumitecore.hero.wish;

/**
 * The shared ritual clock (server and every client read the same numbers). 20 ticks = 1 s.
 * CALL: the plea, energy gathers. FULFIL: the answer, camera turns, the hero's own climax; the hero
 * changes at {@link #SWAP}. RESOLVE: «твоё желание услышано.», the camera comes home.
 */
public final class WishTimeline {
   public static final int CALL_END = 16;
   public static final int SWAP = 40;
   public static final int FULFIL_END = 48;
   public static final int LENGTH = 64;
   /** Ticks after the end before the same player may start another ritual (double clicks, replays). */
   public static final int REARM = 10;
   /** The owner's title stays this long after the ritual ended. */
   public static final int TITLE_TAIL = 36;

   public enum Phase {
      CALL,
      FULFIL,
      RESOLVE,
      DONE
   }

   private WishTimeline() {
   }

   public static Phase phase(float t) {
      if (t < 0 || t >= LENGTH) {
         return t < 0 ? Phase.CALL : Phase.DONE;
      }
      if (t < CALL_END) {
         return Phase.CALL;
      }
      return t < FULFIL_END ? Phase.FULFIL : Phase.RESOLVE;
   }

   /** 0..1 strength of the gathered Od Lagna light: rises through CALL/FULFIL, peaks at the swap, fades out. */
   public static float energy(float t) {
      if (t <= 0 || t >= LENGTH) {
         return 0;
      }
      if (t < SWAP) {
         float k = t / SWAP;
         return k * k * (3 - 2 * k);
      }
      float k = (t - SWAP) / (LENGTH - SWAP);
      return 1 - k * k * (3 - 2 * k);
   }

   /** 0..1 weight of the cinematic camera: eases in over the CALL, holds, eases back over the last 12 ticks. */
   public static float cameraWeight(float t) {
      if (t <= 0 || t >= LENGTH) {
         return 0;
      }
      float in = Math.min(1, t / CALL_END);
      float out = Math.min(1, (LENGTH - t) / 12.0F);
      float k = Math.min(in, out);
      return k * k * (3 - 2 * k);
   }

   /** 0..1 alpha of the final title, measured in ticks since the ritual start (shown past the end). */
   public static float titleAlpha(float t) {
      float start = FULFIL_END - 2;
      float end = LENGTH + TITLE_TAIL;
      if (t < start || t > end) {
         return 0;
      }
      float in = Math.min(1, (t - start) / 8.0F);
      float out = Math.min(1, (end - t) / 14.0F);
      return Math.max(0, Math.min(in, out));
   }
}

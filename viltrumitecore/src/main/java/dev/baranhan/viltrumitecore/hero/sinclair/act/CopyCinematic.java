package dev.baranhan.viltrumitecore.hero.sinclair.act;

/**
 * Pure timeline of the copy cinematic (design 2026-11-02 §2): after a successful capture both participants
 * are held for {@link #LENGTH} ticks while the copied number is shown on a magic card on both screens.
 * Server (freeze, sounds) and client (camera, letterbox, card) read the same numbers.
 *
 * <pre>
 *  0 ..  8  sting, both are stopped, the cameras blend to the staged shots, letterbox in
 *  8 .. 18  the card unfolds (edge-on to face-on, glow and motes)
 * 18 .. 44  hold: act name, source, short description
 * 44 .. 52  the card folds and flies off (into Sinclair's palm / out of the volunteer)
 * 52 .. 60  cameras return, letterbox out; control comes back on 60
 * </pre>
 */
public final class CopyCinematic {
   public static final int LENGTH = 60;
   public static final int CAMERA_IN = 8;
   public static final int UNFOLD_START = 8;
   public static final int UNFOLD_END = 18;
   public static final int TEXT_IN = 16;
   public static final int FOLD_START = 44;
   public static final int FOLD_END = 52;
   public static final int CAMERA_OUT = 52;
   /** Extra ticks the freeze record outlives the scene (a safety expiry if the release is ever missed). */
   public static final int FREEZE_SLACK = 20;

   private CopyCinematic() {
   }

   static double clamp01(double v) {
      return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
   }

   public static double smooth(double v) {
      double k = clamp01(v);
      return k * k * (3.0 - 2.0 * k);
   }

   /** Camera weight of the staged shot (0 = the player's own camera, 1 = the staged shot). */
   public static double cameraWeight(double t) {
      if (t < 0.0 || t >= LENGTH) {
         return 0.0;
      }

      return smooth(t / CAMERA_IN) * (1.0 - smooth((t - CAMERA_OUT) / (LENGTH - CAMERA_OUT)));
   }

   /** Letterbox bars: in with the camera, out with it. */
   public static double letterbox(double t) {
      return cameraWeight(t);
   }

   /**
    * Card turn in degrees around its vertical axis: 90 (edge-on, invisible) to 0 (facing the viewer) while it
    * unfolds, 0 while held, 0 to -90 while it folds away.
    */
   public static double cardTurn(double t) {
      if (t < UNFOLD_START) {
         return 90.0;
      }

      if (t < UNFOLD_END) {
         double k = (t - UNFOLD_START) / (UNFOLD_END - UNFOLD_START);
         // A small overshoot sells the flip (back-out easing).
         double e = 1.0 - Math.pow(1.0 - k, 3.0) + Math.sin(k * Math.PI) * 0.08;
         return 90.0 * (1.0 - e);
      }

      if (t < FOLD_START) {
         return 0.0;
      }

      if (t < FOLD_END) {
         double k = (t - FOLD_START) / (FOLD_END - FOLD_START);
         return -90.0 * k * k;
      }

      return -90.0;
   }

   /** Card scale: grows from a small card while it turns, shrinks into its flight while it folds. */
   public static double cardScale(double t) {
      if (t < UNFOLD_START || t >= FOLD_END) {
         return 0.0;
      }

      if (t < UNFOLD_END) {
         return 0.55 + 0.45 * smooth((t - UNFOLD_START) / (UNFOLD_END - UNFOLD_START));
      }

      if (t < FOLD_START) {
         // A slow breathing push-in while it is held.
         return 1.0 + 0.04 * smooth((t - UNFOLD_END) / (FOLD_START - UNFOLD_END));
      }

      return 1.04 - 0.64 * smooth((t - FOLD_START) / (FOLD_END - FOLD_START));
   }

   /** Card flight while it folds (0 = centre of the screen, 1 = gone to its destination). */
   public static double cardFlight(double t) {
      return t < FOLD_START ? 0.0 : smooth((t - FOLD_START) / (FOLD_END - FOLD_START));
   }

   /** Text opacity on the card: types in after the flip, gone before it folds. */
   public static double textAlpha(double t) {
      return smooth((t - TEXT_IN) / 4.0) * (1.0 - smooth((t - (FOLD_START - 2)) / 4.0));
   }

   /** Glow around the card (peaks right when it faces the viewer). */
   public static double glow(double t) {
      if (t < UNFOLD_START || t >= FOLD_END) {
         return 0.0;
      }

      double flare = Math.exp(-Math.pow((t - UNFOLD_END) / 3.0, 2.0));
      return 0.55 + 0.45 * flare;
   }

   /** Is the scene between its first and last tick. */
   public static boolean running(double t) {
      return t >= 0.0 && t < LENGTH;
   }

   /** Server sound beats: 0 sting, {@link #UNFOLD_START} unfold, {@link #FOLD_START} fold, {@link #CAMERA_OUT} return. */
   public static boolean beat(int t) {
      return t == 0 || t == UNFOLD_START || t == FOLD_START || t == CAMERA_OUT;
   }
}

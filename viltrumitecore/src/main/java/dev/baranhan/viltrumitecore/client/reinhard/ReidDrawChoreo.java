package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.animation.MonotoneCubic;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;

/**
 * REINHARD OVERHAUL item 15: the one timeline of the Reid draw, in draw ticks
 * {@code d = 0..120} (server ticks of the DRAWING phase). SHEATHING replays the same
 * choreography backwards through {@link #drawTick}. Pure and shared by the third-person
 * equipment, the first-person pose, the scene light and the draw audio, so the blade,
 * the light and the sound move together.
 *
 * <pre>
 *   0-8    anticipation: breath in, the head lowers to the hilt (left palm rests on it)
 *   8-16   the left hand slides down the hilt and closes on the scabbard throat
 *  16-30   the left hand lifts the sheathed sword level in front of the chest;
 *          the right hand reaches across (18-28) and takes the handle behind the guard
 *  33-35   koiguchi: the scabbard mouth is broken (0.8 px of blade shows)
 *  37-60   slow first extraction, light leaks from the mouth
 *  60-86   main extraction: the arms spread, the torso opens
 *  86-92   strain: the sword resists, Reid trembles
 *  92-96   the last hand-span snaps free
 *  96      RELEASE: the tip clears the mouth (flash, wave, accent)
 *  96-120  the freed blade rises in one unbroken arc into the Sword Drawn guard
 *  100-110 meanwhile the left hand returns the scabbard to the hip
 *  110-120 the left palm leaves the throat past the front of the hip and takes the pommel
 * </pre>
 *
 * Geometry (blade-native px): mouth of a sheathed blade at {@link #MOUTH}, tip at 49.58,
 * so the tip clears the mouth after {@link #FULL}. The right palm stays behind the guard
 * ({@link #RIGHT_GRIP}, the Sword Drawn main-hand point); the left palm holds the throat
 * fitting {@link #THROAT_GRIP} past the mouth. At the release the palms are 23.5 px apart
 * on a level line 4.8 px in front of the shoulders: inside both arms' reach with the
 * forearms at more than 60 degrees to the blade, so neither sleeve crosses the steel.
 */
public final class ReidDrawChoreo {
   public static final int LENGTH = ReinhardRules.REID_DRAW_TICKS;
   public static final int RELEASE = ReinhardRules.REID_RELEASE_TICK;
   public static final float MOUTH = 3.28F;
   public static final float FULL = 49.58F - MOUTH;
   /** Left palm on the throat fitting, measured past the mouth (native px). */
   public static final float THROAT_GRIP = 3.0F;
   public static final float RIGHT_GRIP = 0.0F;
   /** Native y of the pommel grip of the paired Sword Drawn hold. */
   public static final float POMMEL_GRIP = -8.5F;
   public static final int REST_END = 8, THROAT_CLOSED = 16, LIFT_END = 30, KOIGUCHI = 34, STRAIN = 86, SNAP = 92;
   public static final int DOCK_START = 100, DOCKED = 110;
   /** Body-space point (Bedrock px) in front of the left hip that the leaving left palm passes at 115. */
   public static final float[] SUPPORT_FRONT = {5.6F, 17.0F, -6.5F};

   private static final double[] S_T = {0, 32, 35, 37, 60, 86, 92, RELEASE, RELEASE + 4};
   private static final double[] S_V = {0, 0, .8, .9, 12, 38, 41, FULL, FULL + 7};
   /** SHEATHING: tick of the 1 s sheathe to draw tick. The left palm leaves the pommel and takes
    * the scabbard from the hip (0-7), the blade meets the raised mouth (8), slides home (8-11), the
    * mouth closes (12), the sword returns to the hip (12-16) and the left palm to the hilt (16-20).
    * Time goes where the arms travel most, so no section is more than ~3.5x the draw's speed. */
   private static final double[] SHEATHE_T = {0, 3.5, 7, 8, 11, 12, 16, 18.5, ReinhardRules.REID_SHEATHE_TICKS};
   private static final double[] SHEATHE_D = {LENGTH, DOCKED, DOCK_START, RELEASE, 37, LIFT_END, THROAT_CLOSED, REST_END, 0};

   private ReidDrawChoreo() {
   }

   /** Draw tick of a phase tick; SHEATHED 0, DRAWN the end of the draw. */
   public static double drawTick(ReinhardPoses.ReidPhase phase, double ticks) {
      double t = Double.isFinite(ticks) ? ticks : 0;
      return switch (phase) {
         case DRAWING -> clamp(t, 0, LENGTH);
         case SHEATHING -> monotone(SHEATHE_T, SHEATHE_D, clamp(t, 0, ReinhardRules.REID_SHEATHE_TICKS));
         case DRAWN -> LENGTH;
         default -> 0;
      };
   }

   /** Blade travel out of the scabbard (native px): 0 sheathed, {@link #FULL} at the release. */
   public static float extraction(double d) {
      return (float)Math.min(FULL, Math.max(0, monotone(S_T, S_V, clamp(d, 0, LENGTH))));
   }

   /** Extraction speed (native px per draw tick); the scrape cue of the draw audio is shaped on it
    * (tools/sfx/reinhard_draw.py, {@code ReidDrawAudio}). */
   public static float extractionRate(double d) {
      if (d < 0 || d >= RELEASE) return 0;
      double h = .25;
      return (float)Math.max(0, (monotone(S_T, S_V, Math.min(RELEASE, d + h)) - monotone(S_T, S_V, Math.max(0, d - h))) / (2 * h));
   }

   public static float bladeOut(double d) {
      return extraction(d) / FULL;
   }

   /** The scabbard rides the blade until the tip clears the mouth; then it is the left hand's. */
   public static boolean sheathFree(double d) {
      return d >= RELEASE;
   }

   /** Left palm: hilt rest to scabbard throat (8-16). */
   public static float throatWeight(double d) {
      return smooth((d - REST_END) / (THROAT_CLOSED - REST_END));
   }

   /** Right palm IK onto the handle: the clip brings it there, the constraint takes over 26-30. */
   public static float rightWeight(double d) {
      return smooth((d - 26) / 4);
   }

   /** The scabbard returns to the hip mount (100-110). */
   public static float dockWeight(double d) {
      return smooth((d - DOCK_START) / (DOCKED - DOCK_START));
   }

   /** The left palm leaves the docked throat (110-120): it swings out in front of the hip
    * ({@link #SUPPORT_FRONT}) instead of cutting through the belly to the pommel. */
   public static float leaveThroat(double d) {
      return smooth((d - DOCKED) / (LENGTH - DOCKED));
   }

   /** Paired Sword Drawn hold: the left palm takes the pommel 112-120. */
   public static float twoHandWeight(double d) {
      return smooth((d - 112) / (LENGTH - 112));
   }

   /** Weight of the generic elbow pole. The clip starts on the generic planes (0-8), owns the
    * elbow planes 9-110 and hands them back to the Sword Drawn solve by the end (110-120). */
   public static float poleWeight(double d) {
      return 1 - smooth((d - REST_END) / .5) + smooth((d - DOCKED) / (LENGTH - DOCKED));
   }

   /** Axial quarter turn of the free blade (owner-selected Sword Drawn variant); 0 while in the scabbard. */
   public static float roll(double d) {
      return smooth((d - (RELEASE + 2)) / 10) * (float)Math.PI / 2;
   }

   /** 0..1 light of the blade's exposed length: the mouth glows from the koiguchi, peaks at the release. */
   public static float glow(double d) {
      if (d < KOIGUCHI - 1) return 0;
      if (d < RELEASE) return .15F + .85F * bladeOut(d);
      return 1 - .65F * smooth((d - RELEASE) / 18);
   }

   static double monotone(double[] t, double[] v, double x) {
      int n = t.length;
      if (x <= t[0]) return v[0];
      if (x >= t[n - 1]) return v[n - 1];
      int i = 0;
      while (i < n - 2 && x > t[i + 1]) i++;
      double span = t[i + 1] - t[i];
      double slope = (v[i + 1] - v[i]) / span;
      double left = i == 0 ? 0 : MonotoneCubic.tangent(t[i] - t[i - 1], (v[i] - v[i - 1]) / (t[i] - t[i - 1]), span, slope);
      double right = i + 2 >= n ? slope : MonotoneCubic.tangent(span, slope, t[i + 2] - t[i + 1], (v[i + 2] - v[i + 1]) / (t[i + 2] - t[i + 1]));
      if (slope == 0) {
         left = 0;
         right = 0;
      }
      return MonotoneCubic.sample(v[i], v[i + 1], left, right, span, (x - t[i]) / span);
   }

   static float smooth(double value) {
      double t = Math.max(0, Math.min(1, value));
      return (float)(t * t * (3 - 2 * t));
   }

   private static double clamp(double v, double lo, double hi) {
      return Math.max(lo, Math.min(hi, v));
   }
}

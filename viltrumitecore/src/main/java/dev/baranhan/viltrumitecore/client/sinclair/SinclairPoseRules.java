package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import javax.annotation.Nullable;

/**
 * Pure visibility and weighting rules of Sinclair's props on the body (tested, no client classes):
 * where the top hat is (head, left hand, flying), when the card shows, how strongly a move clip
 * covers the locomotion.
 */
public final class SinclairPoseRules {
   /** Capture: the card leaves the volunteer on tick 14 (spec §5.6) and lands in the palm. */
   public static final int CAPTURE_CARD_TICK = 14;
   /** Ease in/out of a move clip over the locomotion, seconds. */
   static final double BLEND_SECONDS = 0.1;

   private SinclairPoseRules() {
   }

   @Nullable
   public static ActShape shape(@Nullable SinclairMove move) {
      if (move == null) {
         return null;
      }

      return switch (move) {
         case ACT_BEAM -> ActShape.BEAM;
         case ACT_NOVA -> ActShape.NOVA;
         case ACT_CUT -> ActShape.CUT;
         case ACT_BARRAGE -> ActShape.BARRAGE;
         case ACT_AEGIS -> ActShape.AEGIS;
         case ACT_FINALE -> ActShape.FINALE;
         default -> null;
      };
   }

   @Nullable
   public static ActScript script(@Nullable SinclairMove move) {
      ActShape shape = shape(move);
      return shape == null ? null : ActScript.of(shape);
   }

   /** The bow sweeps the hat off into the left hand (clip "bow": pinch from its start, back on the head at the end). */
   public static boolean bowHatInHand(@Nullable SinclairMove move, double tick) {
      ActScript script = script(move);
      return script != null && tick >= script.bowStart() + 2 && tick < script.length() - 3;
   }

   /** Top hat on the head. */
   public static boolean headHat(int flags, @Nullable SinclairMove move, double tick) {
      return !SinclairFlags.has(flags, SinclairFlags.HAT_OFF) && !bowHatInHand(move, tick);
   }

   /** Top hat in the left hand: Rabbit Hex cast (server HAT_OFF) and the bows. In NOVA's flight the hat is a world prop. */
   public static boolean heldHat(int flags, @Nullable SinclairMove move, double tick) {
      return SinclairFlags.has(flags, SinclairFlags.HAT_OFF) && move == SinclairMove.HEX_CAST || bowHatInHand(move, tick);
   }

   /** The volunteer's card in the left palm. */
   public static boolean card(int flags, @Nullable SinclairMove move, double tick) {
      if (SinclairFlags.has(flags, SinclairFlags.CARD_HELD)) {
         return true;
      }

      if (move == SinclairMove.CAPTURE) {
         return tick >= CAPTURE_CARD_TICK + 3;
      }

      if (move == SinclairMove.ACT_BEAM) {
         return tick < ActScript.BEAM_END;
      }

      return move == SinclairMove.ACT_BARRAGE && tick < ActScript.BARRAGE.pauseStart();
   }

   /**
    * Weight of a move clip over the locomotion at {@code seconds} of a {@code length}-second clip:
    * eased in and out over {@link #BLEND_SECONDS}. The capture and the acts own the body (full weight
    * from the first frame) so the stage beats never fade.
    */
   public static float moveWeight(@Nullable SinclairMove move, double seconds, double length) {
      if (move == null || !Double.isFinite(seconds) || length <= 0.0 || seconds < 0.0 || seconds > length) {
         return 0.0F;
      }

      double in = move.locksCasting() ? 1.0 : Math.min(1.0, seconds / BLEND_SECONDS);
      double out = Math.min(1.0, (length - seconds) / BLEND_SECONDS);
      return (float) (smooth(in) * smooth(Math.max(0.0, out)));
   }

   /** Prestige aim pose weight: held trigger or a shot within the last second. */
   public static float aimWeight(boolean triggerHeld, int sinceShot) {
      if (triggerHeld) {
         return 1.0F;
      }

      return sinceShot >= 20 ? 0.0F : (float) smooth(1.0 - Math.max(0, sinceShot - 10) / 10.0);
   }

   static double smooth(double t) {
      double k = Math.max(0.0, Math.min(1.0, t));
      return k * k * (3.0 - 2.0 * k);
   }
}

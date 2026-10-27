package dev.baranhan.viltrumitecore.client.thor;

import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.key;
import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.under;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorRules;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Third-person keys per Thor move: the animation extension point. A session
 * registers a move on the server (ThorMoves, phase ticks) and its clip
 * here; ThorPoser plays whatever is registered on the snapshot clock, so the
 * poser itself never changes. Rows are additive PoseKeys rows; key ticks must
 * end at the move's server length (tested). Clips may differ per combo step.
 */
public final class ThorClips {
   /** Right arm slightly forward while Mjölnir is held, like vanilla's item pose (deg). */
   public static final float HOLD_PITCH = -18.0F;
   /** Reach to the right hip for the grip (deg). */
   private static final float REACH_PITCH = 14.0F;
   private static final float REACH_ROLL = 12.0F;
   private static final float GRIP_PITCH = 12.0F;
   private static final float GRIP_ROLL = 10.0F;

   /** Rows held by the idle hold pose (one row = constant). */
   public static final float[][] HOLD = {key(0, HOLD_PITCH, 0, 0)};

   private static final Map<ThorMove, Clip[]> CLIPS = new EnumMap<>(ThorMove.class);

   /** Keys of one move step; null parts stay on the pose underneath. */
   public record Clip(float[][] rightArm, @Nullable float[][] leftArm, @Nullable float[][] body) {
      /** Tick of the last key over every part. */
      public float lastTick() {
         float last = this.rightArm[this.rightArm.length - 1][0];
         if (this.leftArm != null) {
            last = Math.max(last, this.leftArm[this.leftArm.length - 1][0]);
         }

         if (this.body != null) {
            last = Math.max(last, this.body[this.body.length - 1][0]);
         }

         return last;
      }
   }

   static {
      int drawGrip = ThorRules.DRAW_INTRO;
      int drawSettle = drawGrip + ThorRules.DRAW_MAIN;
      int drawEnd = drawSettle + ThorRules.DRAW_FINISH;
      register(ThorMove.DRAW, new Clip(new float[][]{
         under(0), key(drawGrip, REACH_PITCH, 0, REACH_ROLL), key(drawSettle, GRIP_PITCH, 0, GRIP_ROLL), key(drawEnd, HOLD_PITCH, 0, 0)
      }, null, null));
      int holsterReach = ThorRules.HOLSTER_INTRO;
      int holsterRelease = holsterReach + ThorRules.HOLSTER_MAIN;
      int holsterEnd = holsterRelease + ThorRules.HOLSTER_FINISH;
      register(ThorMove.HOLSTER, new Clip(new float[][]{
         key(0, HOLD_PITCH, 0, 0), key(holsterReach, REACH_PITCH, 0, REACH_ROLL), key(holsterRelease, GRIP_PITCH, 0, GRIP_ROLL), under(holsterEnd)
      }, null, null));
   }

   private ThorClips() {
   }

   /** Registers the clips of a move, one per combo step (the last one repeats). */
   public static void register(ThorMove move, Clip... steps) {
      if (steps.length == 0) {
         throw new IllegalArgumentException("no clip for " + move);
      }

      CLIPS.put(move, steps.clone());
   }

   /** Clip for a move step, or null when the move has none (it then plays on the pose underneath). */
   @Nullable
   public static Clip of(@Nullable ThorMove move, int step) {
      Clip[] steps = move == null ? null : CLIPS.get(move);
      return steps == null ? null : steps[Math.min(Math.max(0, step), steps.length - 1)];
   }
}

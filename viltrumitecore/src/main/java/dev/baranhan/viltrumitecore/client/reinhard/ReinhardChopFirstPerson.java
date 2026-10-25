package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * Reinhard's first-person Lethal Chop. The shared kit chop mixins keep their
 * piecewise timeline (holds and segment cuts) for the other heroes; Reinhard's
 * arm takes the same two authored poses (wind, strike) without holds, on the
 * third-person {@code chop_left}/{@code chop_right} hand timing: wind at 0.2,
 * contact at 0.35 with the strike overshoot, settle at 0.45, rest at 1.0 of the
 * 20-tick chop. Both views reach contact on the same tick. Client only;
 * {@link #sample} is pure.
 */
public final class ReinhardChopFirstPerson {
   static final float WIND = 0.2F;
   static final float CONTACT = 0.35F;
   static final float SETTLE = 0.45F;
   /** {x, y, z, rx, ry, rz}: [chop type][wind, strike], the kit mixins' constants. */
   private static final float[][][] POSES = {
      {{1.34F, -0.59F, 0.11F, 70.79F, 9.75F, -81.93F}, {1.2F, -0.35F, -0.98F, 47.28F, 48.34F, -131.75F}},
      {{-0.06F, 0.0F, 0.17F, -11.48F, 48.13F, 34.62F}, {-0.07F, 0.1F, -0.85F, -34.11F, -27.78F, 0.0F}}
   };
   private static final float[][][] KEYS = new float[2][][];

   static {
      for (int type = 0; type < 2; type++) {
         float[] rest = new float[6];
         KEYS[type] = new float[][]{row(0.0F, rest, 1.0F), row(WIND, POSES[type][0], 1.0F),
            row(CONTACT, POSES[type][1], PoseKeys.OVERSHOOT), row(SETTLE, POSES[type][1], 1.0F), row(1.0F, rest, 1.0F)};
      }
   }

   private ReinhardChopFirstPerson() {
   }

   private static float[] row(float time, float[] pose, float scale) {
      float[] row = new float[7];
      row[0] = time;
      for (int c = 0; c < 6; c++) {
         row[c + 1] = pose[c] * scale;
      }

      return row;
   }

   /** Pure: {x, y, z, rx, ry, rz} of the chopping arm at {@code time} (0..1 of the chop). */
   public static float[] sample(int type, float time) {
      float[][] keys = KEYS[type == 1 ? 1 : 0];
      float[] out = new float[6];
      for (int c = 0; c < 6; c++) {
         out[c] = PoseKeys.sampleContinuous(keys, time, c, 0.0F, false);
      }

      return out;
   }

   /** The kit mixins' chop time: (20 - (ticks - partial)) / 20, clamped to 0..1. */
   public static float time(int chopTicks, float partial) {
      return Mth.clamp((20.0F - (chopTicks - partial)) / 20.0F, 0.0F, 1.0F);
   }

   /** Applies a pose exactly like the kit mixins (mirrored for a left chop). */
   public static void apply(PoseStack stack, float[] pose, boolean left) {
      float m = left ? -1.0F : 1.0F;
      stack.translate(pose[0] * m, pose[1], pose[2]);
      stack.mulPose(new Quaternionf().rotateX((float)Math.toRadians(pose[3])));
      stack.mulPose(new Quaternionf().rotateY((float)Math.toRadians(pose[4] * m)));
      stack.mulPose(new Quaternionf().rotateZ((float)Math.toRadians(pose[5] * m)));
   }

   /** Kit chop mixins: true = Reinhard's chop arm was posed here (skip the legacy timeline). */
   public static boolean applyIfReinhard(Player player, int chopTicks, int type, boolean left, float partial, PoseStack stack) {
      if (!(player instanceof HeroPlayer hero) || hero.getHeroId() != HeroId.REINHARD) {
         return false;
      }

      apply(stack, sample(type, time(chopTicks, partial)), left);
      return true;
   }
}

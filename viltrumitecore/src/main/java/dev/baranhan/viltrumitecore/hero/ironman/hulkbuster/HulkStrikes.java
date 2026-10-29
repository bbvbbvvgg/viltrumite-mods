package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import java.util.List;
import javax.annotation.Nullable;

/**
 * One data row per Hulkbuster strike (rework spec §1.3.3, the Reinhard MeleeMoves rule): which move and
 * step, the authoritative contact tick (= the first MAIN tick of that step, tested), the hit volume, damage,
 * push, hit-stop, camera shake, the soft-block carve at the contact, the VFX and sound cue and the
 * first-person arm. The server hits from it, the client reads the same row for the arm, trail and shake.
 */
public final class HulkStrikes {
   public enum Arm {
      RIGHT,
      LEFT,
      BOTH
   }

   public enum Fx {
      PUNCH,
      HAMMER,
      PISTON,
      SHOVE
   }

   /**
    * @param reach   blocks from the eyes along the look
    * @param cone    cosine of the half angle of the hit cone
    * @param radius  extra radius around the contact point (the double hammer, the finisher)
    * @param carve   soft-block carve radius at the contact (0 = none)
    * @param fpPush  first-person arm thrust at the contact, model px
    */
   public record Strike(HulkMove move, int step, Arm arm, float damage, double reach, double cone, double radius, double push, double lift,
      int hitStop, float shake, double carve, Fx fx, HulkbusterSounds.Cue cue, float fpPush) {
      public int contactTick() {
         return this.move.contactTick(this.step);
      }
   }

   public static final List<Strike> ALL = List.of(
      new Strike(HulkMove.COMBO, 0, Arm.RIGHT, HulkbusterRules.COMBO_DAMAGE[0], HulkbusterRules.COMBO_REACH, 0.55, 0.0, 1.3, 0.35, 3, 1.2F, 1.0, Fx.PUNCH,
         HulkbusterSounds.Cue.PUNCH, 9.0F),
      new Strike(HulkMove.COMBO, 1, Arm.LEFT, HulkbusterRules.COMBO_DAMAGE[1], HulkbusterRules.COMBO_REACH, 0.6, 0.0, 1.5, 0.3, 4, 1.4F, 1.0, Fx.PUNCH,
         HulkbusterSounds.Cue.PUNCH, 11.0F),
      new Strike(HulkMove.COMBO, 2, Arm.BOTH, HulkbusterRules.COMBO_DAMAGE[2], HulkbusterRules.COMBO_REACH - 0.4, 0.3, 1.8, 0.4, -0.6, 5, 2.4F, 1.8, Fx.HAMMER,
         HulkbusterSounds.Cue.HAMMER, 6.0F),
      new Strike(HulkMove.CHARGED, 0, Arm.RIGHT, HulkbusterRules.CHARGE_DAMAGE[0], HulkbusterRules.COMBO_REACH + 0.6, 0.6, 0.0, 2.2, 0.45, 5, 2.0F, 1.6, Fx.PISTON,
         HulkbusterSounds.Cue.CHARGED_PUNCH, 14.0F),
      new Strike(HulkMove.GRAB_PUNCH, 0, Arm.RIGHT, HulkbusterRules.GRAB_PUNCH_DAMAGE[0], 3.6, 0.0, 1.2, 0.0, 0.0, 2, 0.9F, 0.0, Fx.PISTON,
         HulkbusterSounds.Cue.FACE_PUNCH, 8.0F),
      new Strike(HulkMove.GRAB_PUNCH, 1, Arm.RIGHT, HulkbusterRules.GRAB_PUNCH_DAMAGE[1], 3.6, 0.0, 1.2, 0.0, 0.0, 2, 1.1F, 0.0, Fx.PISTON,
         HulkbusterSounds.Cue.FACE_PUNCH, 8.5F),
      new Strike(HulkMove.GRAB_PUNCH, 2, Arm.RIGHT, HulkbusterRules.GRAB_PUNCH_DAMAGE[2], 3.6, 0.0, 1.2, 0.0, 0.0, 3, 1.3F, 0.0, Fx.PISTON,
         HulkbusterSounds.Cue.FACE_PUNCH, 9.0F),
      new Strike(HulkMove.GRAB_PUNCH, 3, Arm.RIGHT, HulkbusterRules.GRAB_PUNCH_DAMAGE[3], 3.6, 0.0, 1.2, 0.0, 0.0, 3, 1.6F, 0.0, Fx.PISTON,
         HulkbusterSounds.Cue.FACE_PUNCH, 9.5F),
      new Strike(HulkMove.GRAB_FINISH, 0, Arm.RIGHT, HulkbusterRules.GRAB_FINISH_DAMAGE, 3.8, 0.0, 1.4, HulkbusterRules.GRAB_FINISH_LAUNCH, 0.5, 6, 3.0F, 1.5,
         Fx.PISTON, HulkbusterSounds.Cue.FINISH_PUNCH, 15.0F),
      new Strike(HulkMove.COUNTER, 0, Arm.RIGHT, HulkbusterRules.COUNTER_DAMAGE, 3.6, 0.45, 0.0, 1.9, 0.35, 3, 1.4F, 0.0, Fx.SHOVE,
         HulkbusterSounds.Cue.PUNCH, 7.0F),
      new Strike(HulkMove.DASH_STRIKE, 0, Arm.RIGHT, 18.0F, HulkbusterRules.COMBO_REACH + 0.8, 0.5, 0.0, 2.4, 0.5, 4, 2.0F, 1.5, Fx.PUNCH,
         HulkbusterSounds.Cue.CHARGED_PUNCH, 12.0F));

   private HulkStrikes() {
   }

   @Nullable
   public static Strike of(HulkMove move, int step) {
      Strike fallback = null;
      for (Strike strike : ALL) {
         if (strike.move() == move) {
            if (strike.step() == step) {
               return strike;
            }

            if (fallback == null) {
               fallback = strike;
            }
         }
      }

      return move.maxSteps() == 1 ? fallback : null;
   }
}

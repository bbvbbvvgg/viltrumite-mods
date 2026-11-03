package dev.baranhan.viltrumitecore.t1000;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The lock goal of the hunt: while {@link T1000Brain} is not in DISGUISE it holds the move and look flags, so the
 * citizen goals (stroll, talk, panic, gaze) stand still and the brain alone steers the body.
 */
final class T1000Hunt extends Goal {
   private final T1000Entity e;

   T1000Hunt(T1000Entity e) {
      this.e = e;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   @Override
   public boolean canUse() {
      return this.e.brain().state().hunting() || this.e.form() != T1000Form.HUMAN;
   }

   @Override
   public boolean canContinueToUse() {
      return this.canUse();
   }

   @Override
   public boolean requiresUpdateEveryTick() {
      return true;
   }
}

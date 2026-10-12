package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;

/** First-person hooks of Reinhard for shared mixins (same pattern as IronManFirstPerson). */
public final class ReinhardFirstPerson {
   private ReinhardFirstPerson() {
   }

   /** AlwaysVisibleOffhandMixin: draw Reinhard's empty off hand when the stance shows that arm. */
   public static boolean wantsOffhand(AbstractClientPlayer player) {
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (snapshot == null) {
         return false;
      }

      HumanoidArm offArm = player.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
      return ReinhardPoses.wantsOffhand(ReinhardView.mode(snapshot), offArm, snapshot.heroFlags());
   }
}

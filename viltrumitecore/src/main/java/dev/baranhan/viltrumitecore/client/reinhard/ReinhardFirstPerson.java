package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.pose.FirstPersonArm;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;

/** First-person hooks of Reinhard for shared mixins (same pattern as IronManFirstPerson). */
public final class ReinhardFirstPerson {
   private ReinhardFirstPerson() {
   }

   private static final FirstPersonArm ARM = new FirstPersonArm();

   /** One view-space shoulder transform, before vanilla renders either geo arm or held item. */
   public static void apply(AbstractClientPlayer player,HumanoidArm side,PoseStack stack) {
      HeroPublicSnapshot snapshot=ReinhardView.of(player);
      if(snapshot==null) { ReinhardPlayback.reset(player); return; }
      var o=ReinhardRenderer.firstPersonOffset(player,snapshot,side);
      ARM.reset().blend(new float[]{o.pitch(),o.yaw(),o.roll(),o.x(),o.y(),o.z()},1)
         .apply(stack,side==HumanoidArm.LEFT?-1:1);
   }

   /** Authored right-arm attacks replace, never multiply, vanilla attack motion. */
   public static float swing(HeroPublicSnapshot snapshot,HumanoidArm arm,float vanilla) {
      var action=ReinhardPoses.action(snapshot.actionId());
      return arm==HumanoidArm.RIGHT && action!=null
         && action!=ReinhardAbility.REID ? 0 : vanilla;
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

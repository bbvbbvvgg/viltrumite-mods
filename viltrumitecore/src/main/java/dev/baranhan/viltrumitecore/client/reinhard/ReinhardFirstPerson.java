package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;

/** First-person hooks of Reinhard for shared mixins (same pattern as IronManFirstPerson). */
public final class ReinhardFirstPerson {
   /** Bone names of {@code reinhard_arms.geo.json} (Bedrock armor space). */
   static final String RIGHT_ARM_BONE = "armorRightArm";
   static final String LEFT_ARM_BONE = "armorLeftArm";
   /**
    * The archive arm is ~3.1–3.5 px wide against vanilla's 4 px: in the first-person view it
    * reads as a thin stick. Widen the first-person copy only (the body model keeps its arms),
    * slightly in depth too; the arm is drawn alone, so no torso intersection is possible.
    */
   public static final float ARM_WIDTH = 1.18F;
   public static final float ARM_DEPTH = 1.06F;

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

   /** {@code PlayerGeoLayer.Part.pose} of the first-person arm: wider, a bit deeper, rest pose kept. */
   public static void widenArm(BakedGeoModel arms) {
      scale(arms.getBone(RIGHT_ARM_BONE));
      scale(arms.getBone(LEFT_ARM_BONE));
   }

   private static void scale(GeoBone bone) {
      if (bone == null) {
         return;
      }

      bone.scaleX = ARM_WIDTH;
      bone.scaleZ = ARM_DEPTH;
   }
}

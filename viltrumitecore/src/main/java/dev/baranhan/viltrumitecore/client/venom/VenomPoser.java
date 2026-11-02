package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Venom's poses on the vanilla player model (VenomModelMixin, after vanilla's setupAnim):
 * <ul>
 * <li>the transformation's human stages: the arms jerk out as the coat climbs them, the head snaps back, the body
 * convulses; the reverse: hunched, the arms clutching the body as the coat recedes;</li>
 * <li>a ridden host: the symbiote's gait: hunched shoulders, the head low and cocked, the arms hanging forward and
 * swinging loose, wide knees, sudden twitches;</li>
 * <li>the husk: slumped on its knees, the head on the chest, the arms hanging.</li>
 * </ul>
 * Client only.
 */
public final class VenomPoser {
   private VenomPoser() {
   }

   public static void pose(PlayerModel<?> m, LivingEntity e, float limbSwing, float limbAmount, float age) {
      float partial = Minecraft.getInstance().getFrameTime();
      float gait = VenomPossessionClient.gaitOf(e, partial);
      if (gait > 0.01F) {
         hosted(m, gait, limbSwing, limbAmount, age);
         sync(m);
         return;
      }
      if (!(e instanceof AbstractClientPlayer player)) {
         return;
      }
      HeroPublicSnapshot s = VenomView.of(player);
      if (s == null) {
         return;
      }
      if (VenomView.flag(s, VenomFlags.POSSESSING)) {
         husk(m, age);
         sync(m);
         return;
      }
      VenomMove move = VenomView.move(s);
      if (move == VenomMove.TRANSFORM || move == VenomMove.REVERT) {
         double ticks = VenomPose.actionTicks(player, s, partial);
         if (move == VenomMove.TRANSFORM && ticks < VenomRules.STAGE_GROW) {
            convulse(m, (float)ticks, age);
            sync(m);
         } else if (move == VenomMove.REVERT && ticks >= VenomView.REVERT_GEO_END) {
            clutch(m, (float)((ticks - VenomView.REVERT_GEO_END) / (VenomRules.REVERT_TICKS - VenomView.REVERT_GEO_END)), age);
            sync(m);
         }
      }
   }

   /** The ridden host's gait, blended in by {@code w}. */
   static void hosted(PlayerModel<?> m, float w, float swing, float amount, float age) {
      float twitch = twitch(age);
      float sway = Mth.sin(swing * 0.6662F) * amount;
      m.body.xRot += 0.42F * w;
      m.body.y += 2.6F * w;
      m.head.y += 3.4F * w;
      m.head.z -= 2.2F * w;
      m.head.xRot += (0.15F + 0.25F * twitch) * w;
      m.head.zRot += (0.32F * Mth.sin(age * 0.09F) + 0.4F * twitch) * w;
      for (int i = 0; i < 2; i++) {
         var arm = i == 0 ? m.rightArm : m.leftArm;
         float side = i == 0 ? 1 : -1;
         arm.y += 3.4F * w;
         arm.z -= 1.4F * w;
         arm.xRot = Mth.lerp(w, arm.xRot, -0.55F + sway * side * 0.9F + 0.08F * Mth.sin(age * 0.2F + i));
         arm.zRot = Mth.lerp(w, arm.zRot, side * (0.2F + 0.12F * twitch));
         arm.yRot = Mth.lerp(w, arm.yRot, side * 0.15F);
      }
      for (int i = 0; i < 2; i++) {
         var leg = i == 0 ? m.rightLeg : m.leftLeg;
         float side = i == 0 ? 1 : -1;
         leg.z += 3.6F * w;
         leg.y -= 0.4F * w;
         leg.zRot += side * 0.1F * w;
         leg.xRot -= 0.18F * w;
      }
   }

   /** Pure-ish: sudden short jerks (0-1), a few per second at irregular moments. */
   static float twitch(float age) {
      float a = Mth.frac(age * 0.071F);
      float b = Mth.frac(age * 0.043F + 0.37F);
      return Math.max(a < 0.06F ? 1 - a / 0.06F : 0, b < 0.05F ? (1 - b / 0.05F) * 0.7F : 0);
   }

   /** The coat climbs the human: arms thrown out and shaking, the head snapping back as it reaches the torso. */
   static void convulse(PlayerModel<?> m, float ticks, float age) {
      float limbs = Mth.clamp(ticks / VenomRules.STAGE_TORSO, 0, 1);
      float torso = Mth.clamp((ticks - VenomRules.STAGE_TORSO) / (VenomRules.STAGE_GROW - VenomRules.STAGE_TORSO), 0, 1);
      float shake = Mth.sin(age * 2.3F) * 0.08F + Mth.sin(age * 3.7F) * 0.05F;
      m.rightArm.zRot = Mth.lerp(limbs, m.rightArm.zRot, 0.95F + shake);
      m.leftArm.zRot = Mth.lerp(limbs, m.leftArm.zRot, -0.95F - shake);
      m.rightArm.xRot = Mth.lerp(limbs, m.rightArm.xRot, -0.35F + shake);
      m.leftArm.xRot = Mth.lerp(limbs, m.leftArm.xRot, -0.35F - shake);
      m.head.xRot = Mth.lerp(torso, m.head.xRot, -0.7F + shake * 2);
      m.head.zRot += shake * 1.5F * torso;
      m.body.xRot += (-0.12F * torso + shake * 0.5F);
      m.rightLeg.zRot += 0.12F * limbs;
      m.leftLeg.zRot -= 0.12F * limbs;
   }

   /** The coat recedes: hunched, arms clutching the chest, straightening up as the human comes back. */
   static void clutch(PlayerModel<?> m, float f, float age) {
      float w = 1.0F - f;
      m.body.xRot += 0.5F * w;
      m.body.y += 3.0F * w;
      m.head.y += 3.6F * w;
      m.head.xRot += 0.4F * w;
      m.rightArm.y += 3.2F * w;
      m.leftArm.y += 3.2F * w;
      m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -1.1F);
      m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -1.1F);
      m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.6F);
      m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.6F);
      m.rightLeg.z += 4.0F * w;
      m.leftLeg.z += 4.0F * w;
   }

   /** The empty husk: knees on the ground (the renderer lowers it), the head on the chest, arms hanging. */
   static void husk(PlayerModel<?> m, float age) {
      float breath = Mth.sin(age * 0.05F) * 0.03F;
      m.body.xRot = 0.55F + breath;
      m.body.y = 3.0F;
      m.head.y = 4.4F;
      m.head.z = -2.6F;
      m.head.xRot = 0.95F;
      m.head.yRot = 0.0F;
      m.head.zRot = 0.18F;
      m.rightArm.y = 4.6F;
      m.leftArm.y = 4.6F;
      m.rightArm.z = -1.2F;
      m.leftArm.z = -1.2F;
      m.rightArm.xRot = -0.25F + breath;
      m.leftArm.xRot = -0.2F + breath;
      m.rightArm.zRot = 0.12F;
      m.leftArm.zRot = -0.12F;
      m.rightArm.yRot = 0;
      m.leftArm.yRot = 0;
      m.rightLeg.xRot = -1.5F;
      m.leftLeg.xRot = -1.5F;
      m.rightLeg.yRot = 0.25F;
      m.leftLeg.yRot = -0.25F;
      m.rightLeg.zRot = 0;
      m.leftLeg.zRot = 0;
      m.rightLeg.z = 1.0F;
      m.leftLeg.z = 1.0F;
      m.rightLeg.y = 12.0F;
      m.leftLeg.y = 12.0F;
   }

   /** The outer skin layer follows the parts (vanilla copies them before the TAIL). */
   static void sync(PlayerModel<?> m) {
      m.hat.copyFrom(m.head);
      m.jacket.copyFrom(m.body);
      m.rightSleeve.copyFrom(m.rightArm);
      m.leftSleeve.copyFrom(m.leftArm);
      m.rightPants.copyFrom(m.rightLeg);
      m.leftPants.copyFrom(m.leftLeg);
   }
}

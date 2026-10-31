package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.reinhard.finalpunch.FinalPunchClient;
import dev.baranhan.viltrumitecore.client.reinhard.finalpunch.FinalPunchCues;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchTargets;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * THE FINAL PUNCH on Reinhard's third-person body: the 24 s {@code final_punch} take
 * (tools/assets/make_reinhard_final_punch.py) over whatever the stance drew, on the scene's clock
 * (not the action snapshot), blended in at the start and out after the end or a cancel. A golem's
 * jaw sits ~0.66 block higher than a person's: from the silence to the follow-through the body
 * jumps into it and the arm rises a little more (constants printed by the generator).
 */
final class FinalPunchPose {
   static final ResourceLocation CLIP = new ResourceLocation("viltrumitecore", "animations/reinhard/final_punch.animation.json");
   /** Pelvis lift (native px) of the jump into a golem's jaw. */
   static final float GOLEM_LIFT_PX = 8.5F;
   /** Forward drift (native px) of the jump. */
   static final float GOLEM_FORWARD_PX = 2.08F;
   /** Extra raise of the right arm (degrees). */
   static final float GOLEM_ARM_DEG = 20.57F;
   static final float GOLEM_HEAD_DEG = 14.0F;

   private FinalPunchPose() {
   }

   @Nullable
   static FinalPunchClient.Scene scene(AbstractClientPlayer player) {
      return FinalPunchClient.of(player.getId());
   }

   /** The caster faces the scene's yaw for the whole take: the clip is authored in that frame. */
   static void face(AbstractClientPlayer player, FinalPunchClient.Scene scene) {
      if (scene.cancelled() || scene.time(0) > FinalPunchRules.LENGTH) return;
      player.yBodyRot = player.yBodyRotO = scene.yaw;
      player.yHeadRot = player.yHeadRotO = scene.yaw;
   }

   /** Poses {@code body}; false when no scene runs (nothing changed). */
   static boolean apply(BakedGeoModel body, FinalPunchClient.Scene scene, float partial) {
      Animation clip = AnimCache.animation(CLIP, ReinhardClips.FINAL_PUNCH);
      if (clip == null) return false;
      return apply(body, clip, scene.time(partial), scene.fade(partial), scene.kind);
   }

   /** Pure: the take at scene time {@code t} with weight {@code fade}; golems get the aim correction. */
   static boolean apply(BakedGeoModel body, Animation clip, double t, float fade, FinalPunchTargets.Kind kind) {
      float weight = FinalPunchCues.bodyWeight(t) * fade;
      if (weight <= 0.0F) return false;
      ReinhardLayers.overlay(clip, body, Math.min(t, FinalPunchRules.LENGTH) / 20.0, weight);
      if (kind == FinalPunchTargets.Kind.GOLEM) aim(body, FinalPunchCues.aim(t) * weight);
      return true;
   }

   /** The golem correction at strength {@code k} (0..1). */
   static void aim(BakedGeoModel body, float k) {
      if (k <= 0.0F) return;
      for (String name : new String[]{"body", ReinhardAssets.RIGHT_LEG, ReinhardAssets.LEFT_LEG}) {
         GeoBone bone = body.getBone(name);
         if (bone != null) {
            bone.posY += GOLEM_LIFT_PX * k;
            bone.posZ -= GOLEM_FORWARD_PX * k;
         }
      }
      GeoBone arm = body.getBone(ReinhardAssets.RIGHT_ARM);
      if (arm != null) arm.rotX += (float) Math.toRadians(GOLEM_ARM_DEG) * k;
      GeoBone head = body.getBone("head");
      if (head != null) head.rotX += (float) Math.toRadians(GOLEM_HEAD_DEG) * k;
   }
}

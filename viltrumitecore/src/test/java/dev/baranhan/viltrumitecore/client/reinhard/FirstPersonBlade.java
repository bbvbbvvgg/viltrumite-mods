package dev.baranhan.viltrumitecore.client.reinhard;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.FirstPersonArm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Test-only replay of the first-person Reid chain: hero shoulder offset, vanilla
 * renderPlayerArm (swing 0, equip 0), the right ModelPart at rest, PlayerGeoLayer's
 * geo conversion and ReinhardGrip.cameraHand. Projects the blade to NDC (FOV 70, 16:9). */
final class FirstPersonBlade {
   static final float TAN = (float)Math.tan(Math.toRadians(35));
   static final float ASPECT = 16F / 9F;
   static final float HILT = -8.5F / 16, GUARD = .7F / 16, TIP = 49.7F / 16;

   private FirstPersonBlade() {
   }

   static BakedGeoModel arms() throws Exception {
      return BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/geo/reinhard/reinhard_arms.geo.json"))).getAsJsonObject());
   }

   /** Same as ReinhardRenderer.poseFirstPersonElbows for one clip sample (null = no clip). */
   static void poseElbows(BakedGeoModel geo, Map<String, Animation> clips, String clip, double seconds) {
      geo.resetBones();
      for (String name : new String[]{"right_forearm", "left_forearm"}) {
         GeoBone elbow = geo.getBone(name);
         if (elbow != null) elbow.rotX = (float)Math.toRadians(7);
      }
      if (clip != null) {
         Animation a = clips.get(clip);
         java.util.Map<String, Animation.BoneAnimation> elbows = new java.util.HashMap<>();
         for (String name : new String[]{"right_forearm", "left_forearm"})
            if (a.boneAnimations().containsKey(name)) elbows.put(name, a.boneAnimations().get(name));
         ReinhardLayers.overlay(new Animation(a.name(), a.lengthSeconds(), a.loopType(), elbows, java.util.List.of()), geo,
            ReinhardPlayback.time(a, seconds), 1);
      }
      for (GeoBone bone : geo.topLevelBones()) bone.scaleX = 1.13F;
   }

   /** View-space blade frame for a first-person offset {pitch, yaw, roll, x, y, z}. */
   static Matrix4f frame(BakedGeoModel arms, ReinhardFirstPersonPose.Offset o) {
      PoseStack s = new PoseStack();
      new FirstPersonArm().reset().blend(new float[]{o.pitch(), o.yaw(), o.roll(), o.x(), o.y(), o.z()}, 1).apply(s, 1);
      s.translate(0.64000005F, -0.6F, -0.71999997F);
      s.mulPose(Axis.YP.rotationDegrees(45));
      s.translate(-1, 3.6F, 3.5F);
      s.mulPose(Axis.ZP.rotationDegrees(120));
      s.mulPose(Axis.XP.rotationDegrees(200));
      s.mulPose(Axis.YP.rotationDegrees(-135));
      s.translate(5.6F, 0, 0);
      // ModelPart rightArm at rest after renderHand's setupAnim: pivot (-5, 2, 0) px, bob zRot 0.1.
      s.translate(-5 / 16F, 2 / 16F, 0);
      s.mulPose(Axis.ZP.rotation(0.1F));
      s.translate(5 / 16F, 22 / 16F, 0);
      s.scale(-1, -1, 1);
      s.mulPoseMatrix(ReinhardGrip.cameraHand(arms));
      return new Matrix4f(s.last().pose());
   }

   /** View-space point of the blade at native height {@code y} (blocks). */
   static Vector3f point(Matrix4f frame, float y) {
      return frame.transformPosition(new Vector3f(0, y, 0));
   }

   /** {ndcX, ndcY, depth}; depth > 0 = in front of the camera. */
   static float[] ndc(Vector3f p) {
      float depth = -p.z;
      return new float[]{p.x / (depth * TAN * ASPECT), p.y / (depth * TAN), depth};
   }

   static String describe(Matrix4f frame) {
      StringBuilder out = new StringBuilder();
      for (float y : new float[]{HILT, GUARD, TIP * .5F, TIP}) {
         float[] n = ndc(point(frame, y));
         out.append(String.format(" y=%.2f:(%.2f,%.2f,d%.2f)", y, n[0], n[1], n[2]));
      }
      return out.toString();
   }
}

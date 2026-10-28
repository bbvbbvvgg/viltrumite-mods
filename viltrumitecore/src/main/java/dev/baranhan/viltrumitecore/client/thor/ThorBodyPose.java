package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import javax.annotation.Nullable;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Thor rework R1: the pose of Thor's own body, composed per frame.
 * <ol>
 * <li>Vanilla pose bridge: the shared PlayerModel is posed by vanilla setupAnim and every model
 * mixin (walk, swing, use poses, ThorPoser's current clips, flight arms, scenes); its part turns
 * are carried onto the geo bones. Head and arms are children of the body here, so they get
 * {@code body⁻¹·part}; the geo arms keep their rest abduction on top so the thick arms clear
 * the torso. Part positions are not carried (the crouch / sit clips own the body offsets).</li>
 * <li>Base clips of thor.animation.json, additive: crouch, sit.</li>
 * <li>Lift (ThorLift): lift_squat overlay, then both arms solved onto the handle (IK).</li>
 * </ol>
 * R2 replaces step 1 with authored clips; the bone names and the IK stay.
 */
public final class ThorBodyPose {
   /** BakedGeoModel's internal frame is the vanilla model frame turned 180° about Z (x and y flipped). */
   private static final Quaternionfc FLIP = new Quaternionf().rotationZ((float)Math.PI);

   /** Clip source (tests read thor.animation.json from disk instead of the resource cache). */
   static java.util.function.Function<String, Animation> clips = name -> AnimCache.animation(ThorAssets.ANIMATIONS, name);

   private ThorBodyPose() {
   }

   /** Vanilla ModelPart turn (ModelPart.translateAndRotate: Rz·Ry·Rx) in the geo frame. */
   static Quaternionf toGeo(float xRot, float yRot, float zRot) {
      Quaternionf vanilla = new Quaternionf().rotationZYX(zRot, yRot, xRot);
      return new Quaternionf(FLIP).mul(vanilla).mul(new Quaternionf(FLIP).conjugate());
   }

   static Quaternionf toGeo(ModelPart part) {
      return toGeo(part.xRot, part.yRot, part.zRot);
   }

   /** The parts' turns as one array (head, body, right arm, left arm, right leg, left leg; x, y, z each). */
   public static float[] capture(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
      ModelPart[] parts = {head, body, rightArm, leftArm, rightLeg, leftLeg};
      float[] out = new float[18];
      for (int i = 0; i < parts.length; i++) {
         out[i * 3] = parts[i].xRot;
         out[i * 3 + 1] = parts[i].yRot;
         out[i * 3 + 2] = parts[i].zRot;
      }

      return out;
   }

   private static Quaternionf part(float[] turns, int index) {
      return toGeo(turns[index * 3], turns[index * 3 + 1], turns[index * 3 + 2]);
   }

   /**
    * Step 1 over a reset model. {@code turns} from {@link #capture}; {@code head} false keeps the head
    * at rest (the lift looks at the handle instead of the camera).
    */
   public static void bridge(BakedGeoModel model, float[] turns, boolean head) {
      model.resetBones();
      Quaternionf body = part(turns, 1);
      Quaternionf inverse = new Quaternionf(body).conjugate();
      set(model.getBone(ThorAssets.BODY_BONE), body, null);
      if (head) {
         set(model.getBone(ThorAssets.HEAD), new Quaternionf(inverse).mul(part(turns, 0)), null);
      }

      set(model.getBone(ThorAssets.RIGHT_ARM), new Quaternionf(inverse).mul(part(turns, 2)), model.getBone(ThorAssets.RIGHT_ARM));
      set(model.getBone(ThorAssets.LEFT_ARM), new Quaternionf(inverse).mul(part(turns, 3)), model.getBone(ThorAssets.LEFT_ARM));
      set(model.getBone(ThorAssets.RIGHT_LEG), part(turns, 4), null);
      set(model.getBone(ThorAssets.LEFT_LEG), part(turns, 5), null);
   }

   /** Sets a bone's turn; {@code rest} keeps that bone's authored rest turn on top (the arm abduction). */
   private static void set(@Nullable GeoBone bone, Quaternionf turn, @Nullable GeoBone rest) {
      if (bone == null) {
         return;
      }

      Quaternionf q = rest == null ? turn : turn.mul(new Quaternionf().rotationZYX(rest.initRotZ, rest.initRotY, rest.initRotX));
      GeoPose.setLocalRotation(bone, q);
   }

   /** Step 2: crouch / sit base clips, additive, by weight. */
   public static void baseClips(BakedGeoModel model, float crouch, float sit) {
      additive(model, ThorAssets.CLIP_CROUCH, crouch);
      additive(model, ThorAssets.CLIP_SIT, sit);
   }

   private static void additive(BakedGeoModel model, String name, float weight) {
      if (weight <= 0.0F) {
         return;
      }

      Animation clip = clips.apply(name);
      if (clip != null) {
         AnimationController.additive(clip, model, 0.0, weight);
      }
   }

   /** The lift squat clip over the current pose at {@code seconds}, by weight. */
   public static void liftClip(BakedGeoModel model, double seconds, float weight) {
      Animation clip = clips.apply(ThorAssets.CLIP_LIFT);
      if (clip != null && weight > 0.0F) {
         double length = Math.max(1.0E-3, clip.lengthSeconds());
         AnimationController.overlay(clip, model, seconds % length, weight);
      }
   }
}

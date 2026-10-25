package dev.baranhan.viltrumitecore.client.anim.animation;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AnimationController {
   private static final int ROT_X = 0;
   private static final int ROT_Y = 1;
   private static final int ROT_Z = 2;
   private static final int POS_X = 3;
   private static final int POS_Y = 4;
   private static final int POS_Z = 5;
   private static final int SCL_X = 6;
   private static final int SCL_Y = 7;
   private static final int SCL_Z = 8;
   private final String name;
   private Animation current;
   private double startTime = Double.NaN;
   private float transitionTicks = 0.0F;
   private final Map<String, float[]> snapshot = new HashMap<>();
   private boolean snapshotPending = false;
   private Consumer<Animation.Event> eventListener;
   private double lastEventSeconds = -1.0;

   public AnimationController(String name) {
      this.name = name;
   }

   public String name() {
      return this.name;
   }

   public Animation currentAnimation() {
      return this.current;
   }

   public boolean isPlaying(String animationName) {
      return this.current != null && this.current.name().equals(animationName);
   }

   public AnimationController transitionLength(float ticks) {
      this.transitionTicks = Math.max(0.0F, ticks);
      return this;
   }

   public AnimationController onEvent(Consumer<Animation.Event> listener) {
      this.eventListener = listener;
      return this;
   }

   public void play(Animation animation) {
      if (animation == null) {
         this.stop();
      } else if (this.current == null || !this.current.name().equals(animation.name())) {
         this.beginNew(animation);
      }
   }

   public void restart(Animation animation) {
      if (animation == null) {
         this.stop();
      } else {
         this.beginNew(animation);
      }
   }

   public void stop() {
      this.current = null;
      this.startTime = Double.NaN;
      this.snapshotPending = false;
      this.lastEventSeconds = -1.0;
   }

   private void beginNew(Animation animation) {
      this.current = animation;
      this.startTime = Double.NaN;
      this.snapshotPending = this.transitionTicks > 0.0F;
      this.lastEventSeconds = -1.0;
   }

   public boolean isFinished(double seekTime) {
      if (this.current == null || this.current.loopType() != Animation.LoopType.PLAY_ONCE) {
         return false;
      } else {
         return Double.isNaN(this.startTime) ? false : (seekTime - this.startTime) / 20.0 >= this.current.lengthSeconds();
      }
   }

   public double elapsedSeconds(double seekTime) {
      return Double.isNaN(this.startTime) ? 0.0 : (seekTime - this.startTime) / 20.0;
   }

   public void apply(BakedGeoModel model, double seekTime) {
      if (this.snapshotPending) {
         this.captureSnapshot(model);
         this.snapshotPending = false;
      }

      model.resetBones();
      if (this.current != null) {
         if (Double.isNaN(this.startTime)) {
            this.startTime = seekTime;
         }

         double elapsedTicks = Math.max(0.0, seekTime - this.startTime);
         double elapsed = elapsedTicks / 20.0;
         double length = this.current.lengthSeconds();

         double t = switch (this.current.loopType()) {
            case LOOP -> length <= 0.0 ? 0.0 : elapsed % length;
            case HOLD_ON_LAST_FRAME, PLAY_ONCE -> Math.min(elapsed, length);
            default -> elapsed;
         };
         this.fireEvents(t, this.current.loopType() == Animation.LoopType.LOOP);
         float blend = 1.0F;
         if (this.transitionTicks > 0.0F && elapsedTicks < (double)this.transitionTicks) {
            blend = (float)(elapsedTicks / (double)this.transitionTicks);
         }

         poseBones(this.current, model, t, blend, this.snapshot);
      }
   }

   private void captureSnapshot(BakedGeoModel model) {
      this.snapshot.clear();

      for (GeoBone bone : model.allBones()) {
         this.snapshot.put(bone.name, new float[]{bone.rotX, bone.rotY, bone.rotZ, bone.posX, bone.posY, bone.posZ, bone.scaleX, bone.scaleY, bone.scaleZ});
      }
   }

   private void fireEvents(double t, boolean looping) {
      if (this.eventListener != null && !this.current.events().isEmpty()) {
         if (looping && t < this.lastEventSeconds) {
            this.lastEventSeconds = -1.0;
         }

         for (Animation.Event event : this.current.events()) {
            if (event.time() > this.lastEventSeconds && event.time() <= t) {
               this.eventListener.accept(event);
            }
         }

         this.lastEventSeconds = t;
      }
   }

   private static void poseBones(Animation animation, BakedGeoModel model, double t, float blend, Map<String, float[]> from) {
      for (Animation.BoneAnimation ba : animation.boneAnimations().values()) {
         GeoBone bone = model.getBone(ba.boneName());
         if (bone != null) {
            float rx = ba.rotX().sample(t);
            float ry = ba.rotY().sample(t);
            float rz = ba.rotZ().sample(t);
            float px = ba.posX().sample(t);
            float py = ba.posY().sample(t);
            float pz = ba.posZ().sample(t);
            float sx = ba.scaleX().sample(t);
            float sy = ba.scaleY().sample(t);
            float sz = ba.scaleZ().sample(t);
            float targetRotX = bone.initRotX + rx;
            float targetRotY = bone.initRotY + ry;
            float targetRotZ = bone.initRotZ + rz;
            if (blend < 1.0F) {
               float[] fromPose = from == null ? null : from.get(bone.name);
               if (fromPose != null) {
                  targetRotX = lerp(fromPose[0], targetRotX, blend);
                  targetRotY = lerp(fromPose[1], targetRotY, blend);
                  targetRotZ = lerp(fromPose[2], targetRotZ, blend);
                  px = lerp(fromPose[3], px, blend);
                  py = lerp(fromPose[4], py, blend);
                  pz = lerp(fromPose[5], pz, blend);
                  sx = lerp(fromPose[6], sx, blend);
                  sy = lerp(fromPose[7], sy, blend);
                  sz = lerp(fromPose[8], sz, blend);
               }
            }

            bone.rotX = targetRotX;
            bone.rotY = targetRotY;
            bone.rotZ = targetRotZ;
            bone.posX = px;
            bone.posY = py;
            bone.posZ = pz;
            bone.scaleX = sx;
            bone.scaleY = sy;
            bone.scaleZ = sz;
         }
      }
   }

   /** Poses the model at a clip time in seconds: synced timelines drive the time, no start clock. */
   public static void seek(Animation animation, BakedGeoModel model, double seconds) {
      model.resetBones();
      poseBones(animation, model, seconds, 1.0F, null);
   }

   /** Poses only the bones the clip animates, over the current pose (upper body over a walk). */
   public static void overlay(Animation animation, BakedGeoModel model, double seconds) {
      poseBones(animation, model, seconds, 1.0F, null);
   }

   /** Poses the clip's bones blended from their current pose by weight (0 = unchanged, 1 = the clip); other bones keep their pose. */
   public static void overlay(Animation animation, BakedGeoModel model, double seconds, float weight) {
      float w = Math.max(0.0F, Math.min(1.0F, weight));
      if (w <= 0.0F) {
         return;
      }

      Map<String, float[]> from = new HashMap<>();
      for (GeoBone bone : model.allBones()) {
         from.put(bone.name, pose(bone));
      }

      poseBones(animation, model, seconds, w, from);
   }

   /** Adds the clip's rotation and position (times weight) to the current pose of its bones; scale is untouched. */
   public static void additive(Animation animation, BakedGeoModel model, double seconds, float weight) {
      for (Animation.BoneAnimation ba : animation.boneAnimations().values()) {
         GeoBone bone = model.getBone(ba.boneName());
         if (bone != null) {
            bone.rotX += ba.rotX().sample(seconds) * weight;
            bone.rotY += ba.rotY().sample(seconds) * weight;
            bone.rotZ += ba.rotZ().sample(seconds) * weight;
            bone.posX += ba.posX().sample(seconds) * weight;
            bone.posY += ba.posY().sample(seconds) * weight;
            bone.posZ += ba.posZ().sample(seconds) * weight;
         }
      }
   }

   /** Two clips blended per bone: weight 0 = a at secondsA, weight 1 = b at secondsB. */
   public static void blend(BakedGeoModel model, Animation a, double secondsA, Animation b, double secondsB, float weight) {
      seek(a, model, secondsA);
      Map<String, float[]> from = new HashMap<>();
      for (GeoBone bone : model.allBones()) {
         from.put(bone.name, pose(bone));
      }

      seek(b, model, secondsB);
      float w = Math.max(0.0F, Math.min(1.0F, weight));
      for (GeoBone bone : model.allBones()) {
         float[] start = from.get(bone.name);
         if (start == null) {
            continue;
         }

         float[] end = pose(bone);
         float[] mixed = new float[end.length];
         for (int i = 0; i < mixed.length; i++) {
            mixed[i] = lerp(start[i], end[i], w);
         }

         setPose(bone, mixed);
      }
   }

   private static float[] pose(GeoBone bone) {
      return new float[]{bone.rotX, bone.rotY, bone.rotZ, bone.posX, bone.posY, bone.posZ, bone.scaleX, bone.scaleY, bone.scaleZ};
   }

   private static void setPose(GeoBone bone, float[] v) {
      bone.rotX = v[0];
      bone.rotY = v[1];
      bone.rotZ = v[2];
      bone.posX = v[3];
      bone.posY = v[4];
      bone.posZ = v[5];
      bone.scaleX = v[6];
      bone.scaleY = v[7];
      bone.scaleZ = v[8];
   }

   private static float lerp(float from, float to, float f) {
      return from + (to - from) * f;
   }
}

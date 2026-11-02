package dev.baranhan.viltrumitecore.client.scp096;

import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted pose blending in quaternions. The engine's own overlay blends Euler angles axis by axis, which is fine
 * between similar poses but not between two triples of the same orientation from different branches (an arm at
 * (131, -161, -145) degrees equals (-49, -19, 35)); the IK-solved hands-on-face poses produce both, and a crossfade
 * between them would swing the limb through the body. Here every layer's bone rotation becomes a quaternion, the
 * layers are summed with their weights (sign-aligned, normalised: nlerp) and the result goes back to the engine's
 * Z-Y-X Euler order ({@code AnimRenderer} applies Rz, then Ry, then Rx). Positions and scales blend linearly.
 * One instance per renderer; not thread-safe (render thread only).
 */
final class Scp096Blend {
   private final Map<GeoBone, Integer> index = new IdentityHashMap<>();
   private final List<GeoBone> bones = new ArrayList<>();
   private BakedGeoModel model;
   private float[] acc = new float[0];
   private final float[] q = new float[4];
   private final float[] e = new float[3];

   /** Clears the accumulators (and rebuilds the bone table when the model was reloaded). */
   void begin(BakedGeoModel m) {
      if (m != this.model) {
         this.model = m;
         this.index.clear();
         this.bones.clear();
         for (GeoBone b : m.allBones()) {
            this.index.put(b, this.bones.size());
            this.bones.add(b);
         }
         this.acc = new float[this.bones.size() * 11];
      }
      java.util.Arrays.fill(this.acc, 0.0F);
   }

   /** Adds a clip at t seconds with this weight to the blend (bones it does not key are unaffected). */
   void add(Animation clip, double t, float w) {
      if (w <= 0.0F) {
         return;
      }
      for (Animation.BoneAnimation ba : clip.boneAnimations().values()) {
         GeoBone bone = this.model.getBone(ba.boneName());
         Integer i = bone == null ? null : this.index.get(bone);
         if (i == null) {
            continue;
         }
         int o = i * 11;
         quat(bone.initRotX + ba.rotX().sample(t), bone.initRotY + ba.rotY().sample(t), bone.initRotZ + ba.rotZ().sample(t), this.q);
         float dot = this.acc[o] * this.q[0] + this.acc[o + 1] * this.q[1] + this.acc[o + 2] * this.q[2] + this.acc[o + 3] * this.q[3];
         float s = dot < 0.0F ? -w : w;
         this.acc[o] += this.q[0] * s;
         this.acc[o + 1] += this.q[1] * s;
         this.acc[o + 2] += this.q[2] * s;
         this.acc[o + 3] += this.q[3] * s;
         this.acc[o + 4] += ba.posX().sample(t) * w;
         this.acc[o + 5] += ba.posY().sample(t) * w;
         this.acc[o + 6] += ba.posZ().sample(t) * w;
         this.acc[o + 7] += ba.scaleX().sample(t) * w;
         this.acc[o + 8] += ba.scaleY().sample(t) * w;
         this.acc[o + 9] += ba.scaleZ().sample(t) * w;
         this.acc[o + 10] += w;
      }
   }

   /** Writes the blended pose into the bones that any added clip keyed. */
   void apply() {
      for (int i = 0; i < this.bones.size(); i++) {
         int o = i * 11;
         float w = this.acc[o + 10];
         if (w <= 1.0E-6F) {
            continue;
         }
         GeoBone b = this.bones.get(i);
         this.q[0] = this.acc[o];
         this.q[1] = this.acc[o + 1];
         this.q[2] = this.acc[o + 2];
         this.q[3] = this.acc[o + 3];
         euler(this.q, this.e);
         b.rotX = this.e[0];
         b.rotY = this.e[1];
         b.rotZ = this.e[2];
         b.posX = this.acc[o + 4] / w;
         b.posY = this.acc[o + 5] / w;
         b.posZ = this.acc[o + 6] / w;
         b.scaleX = this.acc[o + 7] / w;
         b.scaleY = this.acc[o + 8] / w;
         b.scaleZ = this.acc[o + 9] / w;
      }
   }

   /** Blends a clip over the current pose of the bones it keys by weight k (0 = unchanged, 1 = the clip). */
   void overlay(Animation clip, double t, float k) {
      if (k <= 0.0F) {
         return;
      }
      float[] cur = new float[4];
      for (Animation.BoneAnimation ba : clip.boneAnimations().values()) {
         GeoBone b = this.model.getBone(ba.boneName());
         if (b == null) {
            continue;
         }
         quat(b.rotX, b.rotY, b.rotZ, cur);
         quat(b.initRotX + ba.rotX().sample(t), b.initRotY + ba.rotY().sample(t), b.initRotZ + ba.rotZ().sample(t), this.q);
         nlerp(cur, this.q, k, this.q);
         euler(this.q, this.e);
         b.rotX = this.e[0];
         b.rotY = this.e[1];
         b.rotZ = this.e[2];
         b.posX += (ba.posX().sample(t) - b.posX) * k;
         b.posY += (ba.posY().sample(t) - b.posY) * k;
         b.posZ += (ba.posZ().sample(t) - b.posZ) * k;
      }
   }

   /** Pure: quaternion (x, y, z, w) of Rz(rz) * Ry(ry) * Rx(rx), angles in radians. */
   static void quat(float rx, float ry, float rz, float[] out) {
      double cx = Math.cos(rx * 0.5);
      double sx = Math.sin(rx * 0.5);
      double cy = Math.cos(ry * 0.5);
      double sy = Math.sin(ry * 0.5);
      double cz = Math.cos(rz * 0.5);
      double sz = Math.sin(rz * 0.5);
      // qz * qy * qx
      out[0] = (float)(cz * cy * sx - sz * sy * cx);
      out[1] = (float)(cz * sy * cx + sz * cy * sx);
      out[2] = (float)(sz * cy * cx - cz * sy * sx);
      out[3] = (float)(cz * cy * cx + sz * sy * sx);
   }

   /** Pure: Euler (rx, ry, rz) in radians with Rz * Ry * Rx equal to the (not necessarily unit) quaternion q. */
   static void euler(float[] q, float[] out) {
      double x = q[0];
      double y = q[1];
      double z = q[2];
      double w = q[3];
      double n = Math.sqrt(x * x + y * y + z * z + w * w);
      if (n < 1.0E-9) {
         out[0] = out[1] = out[2] = 0.0F;
         return;
      }
      x /= n;
      y /= n;
      z /= n;
      w /= n;
      double r20 = 2.0 * (x * z - w * y);
      if (Math.abs(r20) < 0.999999) {
         out[0] = (float)Math.atan2(2.0 * (y * z + w * x), 1.0 - 2.0 * (x * x + y * y));
         out[1] = (float)Math.asin(-r20);
         out[2] = (float)Math.atan2(2.0 * (x * y + w * z), 1.0 - 2.0 * (y * y + z * z));
      } else {
         // gimbal lock: put the whole turn on X
         out[0] = (float)Math.atan2(-2.0 * (y * z - w * x), 1.0 - 2.0 * (x * x + z * z));
         out[1] = (float)(r20 > 0.0 ? -Math.PI / 2.0 : Math.PI / 2.0);
         out[2] = 0.0F;
      }
   }

   /** Pure: normalised lerp from a to b (taking the short way) into out (may alias b). */
   static void nlerp(float[] a, float[] b, float t, float[] out) {
      float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
      float s = dot < 0.0F ? -1.0F : 1.0F;
      float x = a[0] + (b[0] * s - a[0]) * t;
      float y = a[1] + (b[1] * s - a[1]) * t;
      float z = a[2] + (b[2] * s - a[2]) * t;
      float w = a[3] + (b[3] * s - a[3]) * t;
      float n = (float)Math.sqrt(x * x + y * y + z * z + w * w);
      if (n < 1.0E-9F) {
         return;
      }
      out[0] = x / n;
      out[1] = y / n;
      out[2] = z / n;
      out[3] = w / n;
   }
}

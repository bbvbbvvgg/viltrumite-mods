package dev.baranhan.viltrumitecore.client.anim.geo;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Pure bone-space math for {@link BakedGeoModel} rigs (no Minecraft client calls, unit tested).
 * All frames are model space in blocks with the internal axes of BakedGeoModel (x mirrored from
 * Bedrock, y up, front -Z), built in exactly AnimRenderer.renderBone's order:
 * {@code translate(-pos) · translate(pivot) · Rz·Ry·Rx · scale · translate(-pivot)}.
 * Bone scales are assumed uniform 1 for the rotation helpers (IK, {@link #setModelRotation}).
 */
public final class GeoPose {
   private GeoPose() {
   }

   /** The bone's transform relative to its parent frame (AnimRenderer order). */
   public static Matrix4f local(GeoBone bone) {
      Matrix4f m = new Matrix4f();
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         m.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }

      m.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      m.rotateZ(bone.rotZ).rotateY(bone.rotY).rotateX(bone.rotX);
      m.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
      return m.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
   }

   /** Model-space frame of the bone's cubes (what the renderer multiplies its vertices by). */
   public static Matrix4f frame(GeoBone bone) {
      Matrix4f m = bone.parent == null ? new Matrix4f() : frame(bone.parent);
      return m.mul(local(bone));
   }

   /** The frame the bone's own transform starts from (its parent's frame, identity at top level). */
   public static Matrix4f parentFrame(GeoBone bone) {
      return bone.parent == null ? new Matrix4f() : frame(bone.parent);
   }

   /** Model-space frame with its origin on the bone's pivot (locators: a grip, a belt hook). */
   public static Matrix4f pivotFrame(GeoBone bone) {
      return frame(bone).translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
   }

   /** Model-space position of the bone's pivot (blocks). */
   public static Vector3f point(GeoBone bone) {
      return pivotFrame(bone).getTranslation(new Vector3f());
   }

   /** Rotation part of a frame (normalised, scale removed). */
   public static Quaternionf rotation(Matrix4f frame) {
      return frame.getNormalizedRotation(new Quaternionf());
   }

   /**
    * Exact inverse of the renderer's {@code Rz(z)·Ry(y)·Rx(x)}: the (x, y, z) radians of a rotation.
    * At the gimbal pole (|y| = 90°) x takes the whole remaining roll.
    */
   public static Vector3f euler(Quaternionfc q) {
      Matrix3f m = new Matrix3f().set(q);
      // Row-major element (row, column) of R = Rz·Ry·Rx: m20 = -sin y, m21 = cos y sin x, m22 = cos y cos x,
      // m10 = sin z cos y, m00 = cos z cos y. JOML stores column-major: mCR = column C, row R.
      float r20 = m.m02, r21 = m.m12, r22 = m.m22, r10 = m.m01, r00 = m.m00;
      float sy = Math.max(-1.0F, Math.min(1.0F, -r20));
      float y = (float)Math.asin(sy);
      float x, z;
      if (Math.abs(sy) < 0.99999F) {
         x = (float)Math.atan2(r21, r22);
         z = (float)Math.atan2(r10, r00);
      } else {
         // cos y = 0: only x - z (or x + z) is defined; keep z = 0.
         z = 0.0F;
         float r01 = m.m10, r11 = m.m11;
         x = (float)Math.atan2(sy > 0 ? r01 : -r01, r11);
      }

      return new Vector3f(x, y, z);
   }

   /** The renderer's local rotation of a bone as a quaternion. */
   public static Quaternionf localRotation(GeoBone bone) {
      return new Quaternionf().rotationZYX(bone.rotZ, bone.rotY, bone.rotX);
   }

   public static void setLocalRotation(GeoBone bone, Quaternionfc q) {
      Vector3f e = euler(q);
      bone.rotX = e.x;
      bone.rotY = e.y;
      bone.rotZ = e.z;
   }

   /** Sets the bone's local rotation so its model-space rotation becomes {@code model}. */
   public static void setModelRotation(GeoBone bone, Quaternionfc model) {
      Quaternionf parent = rotation(parentFrame(bone));
      setLocalRotation(bone, parent.conjugate().mul(model, new Quaternionf()));
   }

   /** Model-space rotation of the bone's frame. */
   public static Quaternionf modelRotation(GeoBone bone) {
      return rotation(frame(bone));
   }

   /** Result of {@link #twoBone}: where the chain ended and whether the target was in reach. */
   public record Solve(Vector3f end, Vector3f elbow, boolean reached) {
   }

   /**
    * Two-bone IK with a hinge elbow: turns {@code upper} (free) and {@code lower} (about its
    * local X only, the way elbows and knees flex forward) so the pivot of {@code end} lands on
    * {@code target} (model space, blocks). {@code pole} is a model-space point the elbow bends
    * toward. Lower's local Y/Z turns are cleared. Out of reach the chain points straight at the
    * target. Pure: only the three bones' rotations change.
    */
   public static Solve twoBone(GeoBone upper, GeoBone lower, GeoBone end, Vector3fc target, Vector3fc pole) {
      // Rest geometry in the upper's local frame (all three turns zeroed, the parent frame kept).
      float ux = upper.rotX, uy = upper.rotY, uz = upper.rotZ;
      float lx = lower.rotX, ly = lower.rotY, lz = lower.rotZ;
      upper.rotX = upper.rotY = upper.rotZ = 0.0F;
      lower.rotX = lower.rotY = lower.rotZ = 0.0F;
      Matrix4f parent = parentFrame(upper);
      Quaternionf parentRot = rotation(parent);
      Vector3f s = point(upper);
      Vector3f e0 = point(lower);
      Vector3f w0 = point(end);
      Quaternionf toLocal = new Quaternionf(parentRot).conjugate();
      Vector3f e = toLocal.transform(new Vector3f(e0).sub(s));
      Vector3f w = toLocal.transform(new Vector3f(w0).sub(e0));
      upper.rotX = ux;
      upper.rotY = uy;
      upper.rotZ = uz;
      lower.rotX = lx;
      lower.rotY = ly;
      lower.rotZ = lz;
      float l1 = e.length(), l2 = w.length();
      Vector3f toTarget = new Vector3f(target).sub(s);
      float distance = toTarget.length();
      if (l1 < 1.0E-6F || l2 < 1.0E-6F || distance < 1.0E-6F) {
         return new Solve(point(end), point(lower), false);
      }

      float min = Math.abs(l1 - l2) + 1.0E-4F, max = (l1 + l2) * 0.9999F;
      boolean reached = distance <= max && distance >= min;
      float d = Math.max(min, Math.min(max, distance));
      // Elbow flex about local X: |e + Rx(t)·w| = d.
      float k = (d * d - l1 * l1 - l2 * l2) * 0.5F - e.x * w.x;
      float a = e.y * w.y + e.z * w.z, b = e.z * w.y - e.y * w.z;
      float r = (float)Math.sqrt(a * a + b * b);
      float t;
      if (r < 1.0E-7F) {
         t = 0.0F;
      } else {
         float phi = (float)Math.atan2(b, a);
         float spread = (float)Math.acos(Math.max(-1.0F, Math.min(1.0F, k / r)));
         float t1 = phi + spread, t2 = phi - spread;
         // Forward flex: the forearm swings toward the front (-Z), sin t > 0 for a hanging arm.
         t = Math.sin(t1) >= Math.sin(t2) ? t1 : t2;
      }

      Quaternionf flex = new Quaternionf().rotationX(t);
      Vector3f chain = new Vector3f(e).add(flex.transform(new Vector3f(w)));
      // Bases: (chain, elbow side) in local -> (target direction, pole side) in model space.
      Vector3f c = new Vector3f(chain).normalize();
      Vector3f p = new Vector3f(e).sub(new Vector3f(c).mul(e.dot(c)));
      Vector3f u = new Vector3f(toTarget).normalize();
      Vector3f v = new Vector3f(pole).sub(s);
      v.sub(new Vector3f(u).mul(v.dot(u)));
      if (v.lengthSquared() < 1.0E-10F) {
         v.set(Math.abs(u.y) < 0.9F ? 0.0F : 1.0F, Math.abs(u.y) < 0.9F ? 1.0F : 0.0F, 0.0F);
         v.sub(new Vector3f(u).mul(v.dot(u)));
      }

      v.normalize();
      Quaternionf model;
      if (p.lengthSquared() < 1.0E-10F) {
         // Straight chain: no bend plane, the minimal turn onto the target line.
         model = new Quaternionf(parentRot).mul(new Quaternionf().rotationTo(c, toLocal.transform(new Vector3f(u))));
      } else {
         p.normalize();
         Matrix3f local = basis(c, p);
         Matrix3f world = basis(u, v);
         model = new Quaternionf().setFromNormalized(world.mul(local.transpose()));
      }

      setModelRotation(upper, model);
      lower.rotX = t;
      lower.rotY = 0.0F;
      lower.rotZ = 0.0F;
      return new Solve(point(end), point(lower), reached);
   }

   private static Matrix3f basis(Vector3f a, Vector3f b) {
      Vector3f c = new Vector3f(a).cross(b).normalize();
      Vector3f bb = new Vector3f(c).cross(a).normalize();
      return new Matrix3f(a.x, a.y, a.z, bb.x, bb.y, bb.z, c.x, c.y, c.z);
   }

   /**
    * IK of a limb with a fist: {@code end} gets the model rotation {@code endRotation}, and the
    * chain puts the point {@code tip} (a locator under {@code end}, e.g. the fist centre) on
    * {@code target}. Returns the solve of the wrist.
    */
   public static Solve reach(GeoBone upper, GeoBone lower, GeoBone end, GeoBone tip, Vector3fc target, Vector3fc pole, Quaternionfc endRotation) {
      // The tip's offset from the end pivot in the end's own frame does not depend on any turn.
      float x = end.rotX, y = end.rotY, z = end.rotZ;
      end.rotX = end.rotY = end.rotZ = 0.0F;
      Matrix4f endFrame = pivotFrame(end);
      Vector3f offset = rotation(endFrame).conjugate().transform(point(tip).sub(endFrame.getTranslation(new Vector3f())));
      end.rotX = x;
      end.rotY = y;
      end.rotZ = z;
      Vector3f wrist = new Vector3f(target).sub(endRotation.transform(new Vector3f(offset)));
      Solve solve = twoBone(upper, lower, end, wrist, pole);
      setModelRotation(end, endRotation);
      return solve;
   }
}

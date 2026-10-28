package dev.baranhan.viltrumitecore.client.thor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3f;

/**
 * "No hand or hammer through the body" (rework spec §3) as a calculation:
 * separating-axis overlap of posed cube boxes ({@link ThorRig.Box}).
 * <ul>
 *   <li>cubes of the same bone, and of the same attached model, never collide;</li>
 *   <li>between two body cubes, the end of each cube that lies at its bone's pivot (shoulder cap, hip,
 *   neck) is trimmed by {@link #JOINT_TRIM}: limbs may turn in their sockets, their far part may not enter
 *   the body. Attachments (the hammer) are never trimmed and meet the full body cubes;</li>
 *   <li>allowed pairs (an attachment and the bone holding it, both palms on the handle) are skipped;</li>
 *   <li>overlap that the model already has in its rest pose (a beard over the chest) is the baseline: only
 *   deeper overlap by more than {@link #TOLERANCE} counts.</li>
 * </ul>
 */
final class ThorIntersection {
   /** Overlap beyond the rest pose that still reads as touching (a quarter of a pixel). */
   static final float TOLERANCE = 0.25F * ThorRig.PX;
   /** Length cut from a limb cube at its joint. */
   static final float JOINT_TRIM = 3.0F * ThorRig.PX;

   private final Set<String> allowed = new HashSet<>();
   private final Map<String, Float> baseline = new HashMap<>();

   record Hit(ThorRig.Box a, ThorRig.Box b, float depth) {
      String describe() {
         return String.format(Locale.ROOT, "%s ↔ %s %.2f px", this.a.id(), this.b.id(), this.depth / ThorRig.PX);
      }
   }

   /** Never checks this pair of bones or models ("model:bone", or "model:*" for a whole attached model). */
   ThorIntersection allow(String a, String b) {
      this.allowed.add(a + "|" + b);
      this.allowed.add(b + "|" + a);
      return this;
   }

   /** Records the rest-pose overlap of every pair as the allowed baseline. */
   ThorIntersection rest(List<ThorRig.Box> boxes) {
      this.baseline.clear();
      forPairs(boxes, (a, b, ia, ib) -> {
         float depth = this.depth(a, b);
         if (depth > 0.0F) {
            this.baseline.put(ia + "|" + ib, depth);
         }
      });
      return this;
   }

   List<Hit> check(List<ThorRig.Box> boxes) {
      List<Hit> hits = new ArrayList<>();
      forPairs(boxes, (a, b, ia, ib) -> {
         float depth = this.depth(a, b) - this.baseline.getOrDefault(ia + "|" + ib, 0.0F);
         if (depth > TOLERANCE) {
            hits.add(new Hit(a, b, depth));
         }
      });
      hits.sort((x, y) -> Float.compare(y.depth, x.depth));
      return hits;
   }

   private interface PairVisitor {
      void visit(ThorRig.Box a, ThorRig.Box b, int ia, int ib);
   }

   private void forPairs(List<ThorRig.Box> boxes, PairVisitor visitor) {
      for (int i = 0; i < boxes.size(); i++) {
         ThorRig.Box a = boxes.get(i);
         for (int j = i + 1; j < boxes.size(); j++) {
            ThorRig.Box b = boxes.get(j);
            if (this.skip(a, b)) {
               continue;
            }

            visitor.visit(a, b, i, j);
         }
      }
   }

   private boolean skip(ThorRig.Box a, ThorRig.Box b) {
      if (a.model().equals(b.model()) && (a.bone().equals(b.bone()) || !a.model().equals(BODY))) {
         return true;
      }

      return this.allowed.contains(a.id() + "|" + b.id()) || this.allowed.contains(a.model() + ":*|" + b.id())
         || this.allowed.contains(a.id() + "|" + b.model() + ":*") || this.allowed.contains(a.model() + ":*|" + b.model() + ":*");
   }

   /** The model label of the body; every other label is a rigid attachment. */
   static final String BODY = "body";

   /** Overlap depth of two boxes (body pairs after joint trimming): > 0 overlap, ≤ 0 apart. */
   float depth(ThorRig.Box a, ThorRig.Box b) {
      boolean bodyPair = a.model().equals(BODY) && b.model().equals(BODY);
      return bodyPair ? sat(trim(a), trim(b)) : sat(a, b);
   }

   /**
    * Cuts {@link #JOINT_TRIM} from the face of the box that lies at its pivot (the face the pivot is
    * nearest to, relative to the box's size: the top of an upper arm, the bottom of a head). A pivot in
    * the middle of the box (a torso turning about its centre) cuts nothing.
    */
   static ThorRig.Box trim(ThorRig.Box box) {
      Vector3f offset = new Vector3f(box.pivot()).sub(box.center());
      int axis = -1;
      float best = 0.5F;
      for (int a = 0; a < 3; a++) {
         float half = box.half()[a];
         float ratio = half > 1.0E-6F ? Math.abs(offset.dot(box.axes()[a])) / half : 0.0F;
         if (ratio > best) {
            best = ratio;
            axis = a;
         }
      }

      if (axis < 0) {
         return box;
      }

      Vector3f u = box.axes()[axis];
      float half = box.half()[axis];
      float cut = Math.min(JOINT_TRIM, Math.max(0.0F, 2.0F * half - ThorRig.PX));
      float[] newHalf = box.half().clone();
      newHalf[axis] = half - cut * 0.5F;
      float side = Math.signum(offset.dot(u));
      Vector3f center = new Vector3f(box.center()).fma(-side * cut * 0.5F, u);
      return new ThorRig.Box(box.model(), box.bone(), center, box.axes(), newHalf, box.pivot());
   }

   /** Separating-axis test of two oriented boxes: the smallest overlap over the 15 axes (≤ 0 = separated). */
   static float sat(ThorRig.Box a, ThorRig.Box b) {
      Vector3f t = new Vector3f(b.center()).sub(a.center());
      float min = Float.POSITIVE_INFINITY;
      List<Vector3f> axes = new ArrayList<>(15);
      for (int i = 0; i < 3; i++) {
         axes.add(a.axes()[i]);
         axes.add(b.axes()[i]);
      }

      for (int i = 0; i < 3; i++) {
         for (int j = 0; j < 3; j++) {
            Vector3f c = new Vector3f(a.axes()[i]).cross(b.axes()[j]);
            if (c.lengthSquared() > 1.0E-8F) {
               axes.add(c.normalize());
            }
         }
      }

      for (Vector3f l : axes) {
         float ra = radius(a, l);
         float rb = radius(b, l);
         float overlap = ra + rb - Math.abs(t.dot(l));
         if (overlap < min) {
            min = overlap;
            if (min <= 0.0F) {
               return min;
            }
         }
      }

      return min;
   }

   private static float radius(ThorRig.Box box, Vector3f axis) {
      float r = 0.0F;
      for (int i = 0; i < 3; i++) {
         r += box.half()[i] * Math.abs(box.axes()[i].dot(axis));
      }

      return r;
   }
}

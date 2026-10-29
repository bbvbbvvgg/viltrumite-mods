package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * R2b's clip acceptance scan in Java, the same rules as {@code tools/assets/thor_mesh.py} (whose
 * docstring names this class): every cube of a moving part is sampled on a 0.35 px grid
 * ({@link ThorRig#points}) and the deepest sample inside a fixed part's cube is the penetration.
 * No baseline, no joint trim: what the player sees. Mjölnir is placed the way ThorRenderer draws it:
 * the hand / belt geo's {@code mjolnir} subtree with that bone's pivot on the body locator
 * ({@code mjolnir_grip} in the fist, {@code mjolnir_belt} on the belt).
 * <ul>
 *   <li>arms (upper arm, forearm, fist, both sides) into the torso (body, belly, head, beard, thighs,
 *   shins); the upper arm against the body is the shoulder joint itself and is skipped;</li>
 *   <li>Mjölnir in the right fist into the torso and the left arm; the left fist may close on the
 *   handle group, the right forearm may touch the pommel group;</li>
 *   <li>the belt hammer (when not in the fist and not away) into both arms and both legs; the right
 *   fist / forearm may hold its handle / pommel group while the grip is within {@link #GRAB} px of the
 *   belt grip (the draw / holster grab);</li>
 *   <li>the shins into the belly and the body (deep squats).</li>
 * </ul>
 */
final class ThorClipMesh {
   /** px: the sampling grid plus overlay shells touching (as R1's and R2b's mesh scans). */
   static final float TOLERANCE = 0.5F;
   /** px: the right grip this close to the belt hammer's grip point is taking it / putting it back. */
   static final float GRAB = 2.5F;
   /** px from the handle centre toward the pommel where the drawn hammer's grip sits (thor_clipcore._belt_grip). */
   static final float BELT_GRIP_OFFSET = 2.93F;
   static final String HAMMER_ROOT = "mjolnir";

   static final List<String> TORSO = List.of("body", "belly", "head", "beard", "right_leg", "left_leg", "right_shin", "left_shin");
   static final List<String> ARMS = List.of("right_arm", "right_forearm", "right_hand", "left_arm", "left_forearm", "left_hand");
   static final List<String> LEGS = List.of("right_leg", "right_shin", "left_leg", "left_shin");
   /** The handle group of the hand / belt geo: shaft, bands, leather wrap, pommel and strap. */
   static final Set<String> HANDLE = Stream.of(
      Stream.of("handle", "handle_plains", "bottom", "side1", "side2"),
      IntStream.rangeClosed(5, 7).mapToObj(i -> "cube_" + i),
      IntStream.rangeClosed(10, 31).mapToObj(i -> "cube_" + i)).flatMap(s -> s).collect(Collectors.toUnmodifiableSet());
   /** The handle group without the shaft and its upper bands. */
   static final Set<String> POMMEL = HANDLE.stream()
      .filter(b -> !b.equals("handle") && !b.equals("cube_5") && !b.equals("cube_6") && !b.equals("cube_7"))
      .collect(Collectors.toUnmodifiableSet());

   private ThorClipMesh() {
   }

   record Hit(String moving, String fixed, float depth) {
      String describe() {
         return String.format(Locale.ROOT, "%s → %s %.2f px", this.moving, this.fixed, this.depth);
      }
   }

   /** One posed moving part: its model-space samples and their bounds. */
   record Part(String id, String bone, List<Vector3f> points, Vector3f min, Vector3f max) {
      static Part of(String id, String bone, List<Vector3f> points) {
         Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
         for (Vector3f p : points) {
            min.min(p);
            max.max(p);
         }

         return new Part(id, bone, points, min, max);
      }
   }

   /**
    * The drawn {@code mjolnir} subtree of a hand / belt hammer geo with that bone's pivot on {@code at}
    * (ThorRenderer.drawMjolnir: {@code at · translate(-pivot)}, rendered from the mjolnir bone down).
    */
   static Map<String, Part> hammerParts(BakedGeoModel hammer, Matrix4f at, String label) {
      hammer.resetBones();
      GeoBone root = hammer.getBone(HAMMER_ROOT);
      Matrix4f base = new Matrix4f(at).translate(-root.pivotX / 16.0F, -root.pivotY / 16.0F, -root.pivotZ / 16.0F)
         .mul(GeoPose.parentFrame(root).invert());
      Map<String, Part> out = new LinkedHashMap<>();
      collect(root, base, label, out);
      return out;
   }

   private static void collect(GeoBone bone, Matrix4f base, String label, Map<String, Part> out) {
      if (!bone.cubes.isEmpty()) {
         out.put(bone.name, Part.of(label + ":" + bone.name, bone.name, ThorRig.points(bone, base)));
      }

      for (GeoBone child : bone.children) {
         collect(child, base, label, out);
      }
   }

   /** Model-space bounds of a body bone's cubes (current pose). */
   private static Vector3f[] bounds(GeoBone bone) {
      Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
      Matrix4f frame = GeoPose.frame(bone);
      for (GeoBone.Cube c : bone.cubes) {
         Matrix4f m = ThorRig.cubeFrame(frame, c);
         Vector3f[] box = ThorRig.bounds(c);
         for (int i = 0; i < 8; i++) {
            Vector3f p = new Vector3f((i & 1) == 0 ? box[0].x : box[1].x, (i & 2) == 0 ? box[0].y : box[1].y, (i & 4) == 0 ? box[0].z : box[1].z);
            m.transformPosition(p);
            min.min(p);
            max.max(p);
         }
      }

      return new Vector3f[]{min, max};
   }

   private static float depth(BakedGeoModel body, Part moving, String fixed, Map<String, Vector3f[]> fixedBounds) {
      GeoBone bone = body.getBone(fixed);
      if (bone == null || bone.cubes.isEmpty() || moving.points.isEmpty()) {
         return 0.0F;
      }

      Vector3f[] b = fixedBounds.computeIfAbsent(fixed, n -> bounds(bone));
      if (moving.min.x > b[1].x || moving.min.y > b[1].y || moving.min.z > b[1].z || b[0].x > moving.max.x || b[0].y > moving.max.y
         || b[0].z > moving.max.z) {
         return 0.0F;
      }

      return ThorRig.depth(body, moving.id, moving.points, fixed).depth();
   }

   /**
    * The belt hammer's grip point (blocks) in the {@code mjolnir_belt} locator frame: where the drawn
    * hand hammer's grip coincides with it ({@link #BELT_GRIP_OFFSET} px from the handle centre, away from the head).
    */
   static Vector3f beltGrip(BakedGeoModel body, BakedGeoModel belt) {
      body.resetBones();
      belt.resetBones();
      Matrix4f locator = GeoPose.pivotFrame(body.getBone(ThorAssets.MJOLNIR_BELT_BONE));
      GeoBone root = belt.getBone(HAMMER_ROOT);
      Matrix4f base = new Matrix4f(locator).translate(-root.pivotX / 16.0F, -root.pivotY / 16.0F, -root.pivotZ / 16.0F)
         .mul(GeoPose.parentFrame(root).invert());
      Vector3f[] handle = cubeAxis(belt.getBone("handle"), 0, base);
      Vector3f head = cubeAxis(belt.getBone("hammer"), 1, base)[0];
      Vector3f axis = handle[1];
      if (axis.dot(new Vector3f(head).sub(handle[0])) < 0.0F) {
         axis.negate();
      }

      Vector3f grip = new Vector3f(handle[0]).fma(-BELT_GRIP_OFFSET / 16.0F, axis);
      return new Matrix4f(locator).invert().transformPosition(grip);
   }

   /** Model-space centre and unit long axis of one cube of a bone drawn with {@code base}. */
   private static Vector3f[] cubeAxis(GeoBone bone, int cube, Matrix4f base) {
      GeoBone.Cube c = bone.cubes.get(cube);
      Matrix4f m = ThorRig.cubeFrame(new Matrix4f(base).mul(GeoPose.frame(bone)), c);
      Vector3f[] box = ThorRig.bounds(c);
      Vector3f size = new Vector3f(box[1]).sub(box[0]);
      int longest = size.x >= size.y && size.x >= size.z ? 0 : size.y >= size.z ? 1 : 2;
      Vector3f dir = new Vector3f(longest == 0 ? 1 : 0, longest == 1 ? 1 : 0, longest == 2 ? 1 : 0);
      Vector3f center = m.transformPosition(new Vector3f(box[0]).add(box[1]).mul(0.5F));
      return new Vector3f[]{center, m.transformDirection(dir).normalize()};
   }

   /**
    * All hits deeper than {@link #TOLERANCE} on the body's current pose, deepest first. {@code hand} = the
    * hand hammer geo when Mjölnir is in the fist (else null), {@code belt} = the belt geo when it hangs on
    * the belt (else null), {@code beltGrip} = {@link #beltGrip}.
    */
   static List<Hit> scan(BakedGeoModel body, BakedGeoModel hand, BakedGeoModel belt, Vector3f beltGrip) {
      Map<String, Part> parts = new LinkedHashMap<>();
      for (String name : Stream.concat(ARMS.stream(), Stream.of("right_shin", "left_shin")).toList()) {
         GeoBone bone = body.getBone(name);
         parts.put(name, Part.of("body:" + name, name, ThorRig.points(bone, new Matrix4f())));
      }

      Map<String, Vector3f[]> fixedBounds = new LinkedHashMap<>();
      List<Hit> hits = new ArrayList<>();
      for (String limb : ARMS) {
         for (String fixed : TORSO) {
            if (limb.endsWith("_arm") && fixed.equals("body")) {
               continue;
            }

            test(body, parts.get(limb), fixed, fixedBounds, hits);
         }
      }

      for (String shin : List.of("right_shin", "left_shin")) {
         for (String fixed : List.of("belly", "body")) {
            test(body, parts.get(shin), fixed, fixedBounds, hits);
         }
      }

      if (hand != null) {
         Matrix4f grip = GeoPose.pivotFrame(body.getBone(ThorAssets.MJOLNIR_GRIP));
         for (Part part : hammerParts(hand, grip, "mjolnir").values()) {
            for (String fixed : Stream.concat(TORSO.stream(), Stream.of("left_arm", "left_forearm", "left_hand", "right_arm", "right_forearm")).toList()) {
               if (fixed.equals("left_hand") && HANDLE.contains(part.bone) || fixed.equals("right_forearm") && POMMEL.contains(part.bone)) {
                  continue;
               }

               test(body, part, fixed, fixedBounds, hits);
            }
         }
      }

      if (belt != null) {
         Matrix4f frame = GeoPose.pivotFrame(body.getBone(ThorAssets.MJOLNIR_BELT_BONE));
         boolean grabbing = beltGrip != null
            && frame.transformPosition(new Vector3f(beltGrip)).distance(GeoPose.point(body.getBone(ThorAssets.MJOLNIR_GRIP))) * 16.0F <= GRAB;
         for (Part part : hammerParts(belt, frame, "belt").values()) {
            for (String fixed : Stream.concat(ARMS.stream(), LEGS.stream()).toList()) {
               if (grabbing && (fixed.equals("right_hand") && HANDLE.contains(part.bone) || fixed.equals("right_forearm") && POMMEL.contains(part.bone))) {
                  continue;
               }

               test(body, part, fixed, fixedBounds, hits);
            }
         }
      }

      hits.sort((a, b) -> Float.compare(b.depth, a.depth));
      return hits;
   }

   private static void test(BakedGeoModel body, Part moving, String fixed, Map<String, Vector3f[]> fixedBounds, List<Hit> hits) {
      float d = depth(body, moving, fixed, fixedBounds);
      if (d > TOLERANCE) {
         hits.add(new Hit(moving.id, "body:" + fixed, d));
      }
   }
}

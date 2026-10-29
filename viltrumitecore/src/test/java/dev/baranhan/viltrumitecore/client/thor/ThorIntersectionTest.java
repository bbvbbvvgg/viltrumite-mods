package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The penetration calculation itself (R2b tooling), on boxes and on the vanilla-proportion stand-in. */
class ThorIntersectionTest {
   private static ThorClipRig.Box box(String model, String bone, float x, float y, float z, float hx, float hy, float hz, float yaw) {
      Matrix4f m = new Matrix4f().rotateY(yaw);
      Vector3f[] axes = {m.transformDirection(new Vector3f(1, 0, 0)), m.transformDirection(new Vector3f(0, 1, 0)), m.transformDirection(new Vector3f(0, 0, 1))};
      return new ThorClipRig.Box(model, bone, new Vector3f(x, y, z), axes, new float[]{hx, hy, hz}, new Vector3f(x, y + hy + 10, z));
   }

   @Test
   void separatingAxesMeasureOverlap() {
      ThorClipRig.Box a = box("a", "a", 0, 0, 0, 1, 1, 1, 0);
      assertTrue(ThorIntersection.sat(a, box("b", "b", 3, 0, 0, 1, 1, 1, 0)) < 0, "apart");
      assertEquals(0.0F, ThorIntersection.sat(a, box("b", "b", 2, 0, 0, 1, 1, 1, 0)), 1.0E-6, "touching");
      assertEquals(0.5F, ThorIntersection.sat(a, box("b", "b", 1.5F, 0, 0, 1, 1, 1, 0)), 1.0E-6, "half a block deep");
      // A box turned 45° reaches √2 along x: at 2.3 it still cuts the corner, at 2.5 it is clear.
      assertTrue(ThorIntersection.sat(a, box("b", "b", 2.3F, 0, 0, 1, 1, 1, (float)Math.toRadians(45))) > 0, "rotated corner overlaps");
      assertTrue(ThorIntersection.sat(a, box("b", "b", 2.5F, 0, 0, 1, 1, 1, (float)Math.toRadians(45))) < 0, "rotated corner clear");
      // Edge-edge: two long bars crossed diagonally above each other.
      ThorClipRig.Box bar = box("a", "a", 0, 0, 0, 3, 0.2F, 0.2F, (float)Math.toRadians(45));
      assertTrue(ThorIntersection.sat(bar, box("b", "b", 0, 0.3F, 0, 3, 0.2F, 0.2F, (float)Math.toRadians(-45))) > 0);
      assertTrue(ThorIntersection.sat(bar, box("b", "b", 0, 0.5F, 0, 3, 0.2F, 0.2F, (float)Math.toRadians(-45))) < 0);
   }

   @Test
   void jointTrimCutsTheSocketEndOnly() {
      // An upper arm hanging from its shoulder pivot at the top: the top 3 px are cut.
      ThorClipRig.Box arm = new ThorClipRig.Box("body", "arm", new Vector3f(0, -6 * ThorClipRig.PX, 0),
         new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)},
         new float[]{2 * ThorClipRig.PX, 6 * ThorClipRig.PX, 2 * ThorClipRig.PX}, new Vector3f(0, 0, 0));
      ThorClipRig.Box trimmed = ThorIntersection.trim(arm);
      assertEquals(4.5F * ThorClipRig.PX, trimmed.half()[1], 1.0E-6);
      assertEquals(-7.5F * ThorClipRig.PX, trimmed.center().y, 1.0E-6, "cut at the shoulder end");
      // A torso turning about its own centre is not cut.
      ThorClipRig.Box torso = new ThorClipRig.Box("body", "torso", new Vector3f(), arm.axes(), new float[]{4 * ThorClipRig.PX, 6 * ThorClipRig.PX, 2 * ThorClipRig.PX},
         new Vector3f());
      assertEquals(torso, ThorIntersection.trim(torso));
   }

   @Test
   void allowedPairsAndAttachmentsAreSkipped() {
      ThorClipRig.Box hand = box("body", "right_hand", 0, 0, 0, 1, 1, 1, 0);
      ThorClipRig.Box offHand = box("body", "left_hand", 0.5F, 0, 0, 1, 1, 1, 0);
      ThorClipRig.Box handle = box("mjolnir", "handle", 0, 0.5F, 0, 0.2F, 2, 0.2F, 0);
      ThorClipRig.Box head = box("mjolnir", "hammer", 0, 0.5F, 0.5F, 1, 1, 1, 0);
      ThorIntersection rules = ThorClipChecks.rules("right_hand", "left_hand");
      List<ThorIntersection.Hit> hits = rules.check(List.of(hand, handle, head, offHand));
      // hand ↔ hammer: the grip; handle ↔ hammer head: one model; left hand ↔ handle: the second palm.
      // left hand ↔ hammer head and left hand ↔ right hand are real.
      assertEquals(2, hits.size(), () -> hits.stream().map(ThorIntersection.Hit::describe).toList().toString());
      assertTrue(hits.stream().anyMatch(h -> h.describe().contains("mjolnir:hammer")));
      assertTrue(hits.stream().anyMatch(h -> h.describe().contains("body:right_hand") && h.describe().contains("body:left_hand")));
   }

   @Test
   void restOverlapIsTheBaseline() {
      ThorClipRig.Box chest = box("body", "chest", 0, 0, 0, 1, 1, 1, 0);
      ThorClipRig.Box beard = box("body", "beard", 0, 0, 0.9F, 0.5F, 0.5F, 0.5F, 0);
      ThorIntersection rules = new ThorIntersection().rest(List.of(chest, beard));
      assertTrue(rules.check(List.of(chest, beard)).isEmpty(), "modelled overlap");
      assertFalse(rules.check(List.of(chest, box("body", "beard", 0, 0, 0.5F, 0.5F, 0.5F, 0.5F, 0))).isEmpty(), "deeper than modelled");
   }

   private static BakedGeoModel standIn() {
      return ThorClipRig.parse(ThorClipRig.standIn());
   }

   private static List<ThorIntersection.Hit> hits(BakedGeoModel body, BakedGeoModel hammer, ThorIntersection rules) {
      List<ThorClipRig.Box> boxes = new java.util.ArrayList<>(ThorClipRig.boxes(ThorIntersection.BODY, body, new Matrix4f()));
      if (hammer != null) {
         boxes.addAll(ThorClipRig.attached(ThorClipChecks.HAMMER_LABEL, hammer, body, "right_arm"));
      }

      return rules.check(boxes);
   }

   @Test
   void standInRestAndNaturalPosesAreClean() throws Exception {
      BakedGeoModel body = standIn();
      BakedGeoModel hammer = ThorClipRig.load(ThorClipChecks.HAMMER);
      ThorIntersection rules = ThorClipChecks.rules("right_arm", "left_arm");
      body.resetBones();
      rules.rest(ThorClipRig.boxes(ThorIntersection.BODY, body, new Matrix4f()));
      assertEquals(List.of(), hits(body, hammer, rules), "rest pose with Mjölnir in the hand");

      // A step (legs ±35° in their hip sockets), a raised sword-arm, a turned head.
      body.getBone("right_leg").rotX = (float)Math.toRadians(35);
      body.getBone("left_leg").rotX = (float)Math.toRadians(-35);
      body.getBone("right_arm").rotX = (float)Math.toRadians(-110);
      body.getBone("head").rotY = (float)Math.toRadians(60);
      List<ThorIntersection.Hit> hits = hits(body, hammer, rules);
      assertEquals(List.of(), hits.stream().map(ThorIntersection.Hit::describe).toList());
   }

   @Test
   void anArmSweptThroughTheChestIsCaught() {
      BakedGeoModel body = standIn();
      ThorIntersection rules = ThorClipChecks.rules("right_arm", "left_arm");
      body.resetBones();
      rules.rest(ThorClipRig.boxes(ThorIntersection.BODY, body, new Matrix4f()));
      GeoBone arm = body.getBone("right_arm");
      int caught = 0;
      for (float sign : new float[]{1, -1}) {
         body.resetBones();
         arm.rotZ = sign * (float)Math.toRadians(90);
         // The arm points across the body when its far end moves toward the centre line.
         Vector3f tip = ThorClipRig.boneMatrix(arm).transformPosition(new Vector3f(arm.pivotX * ThorClipRig.PX, 12 * ThorClipRig.PX, 0));
         boolean across = Math.signum(arm.pivotX) * tip.x < Math.abs(arm.pivotX) * ThorClipRig.PX;
         List<ThorIntersection.Hit> hits = hits(body, null, rules);
         if (across) {
            caught++;
            assertTrue(hits.stream().anyMatch(h -> h.describe().contains("body:right_arm") && h.describe().contains("body:body")), "through the chest");
         } else {
            assertEquals(List.of(), hits.stream().map(ThorIntersection.Hit::describe).toList(), "raised to the side");
         }
      }

      assertEquals(1, caught, "one of the two directions crosses the body");
   }

   @Test
   void theHammerSwungIntoTheBodyIsCaught() throws Exception {
      BakedGeoModel body = standIn();
      BakedGeoModel hammer = ThorClipRig.load(ThorClipChecks.HAMMER);
      ThorIntersection rules = ThorClipChecks.rules("right_arm", "left_arm");
      body.resetBones();
      rules.rest(ThorClipRig.boxes(ThorIntersection.BODY, body, new Matrix4f()));
      // Sweep the arm through every direction: some poses must drive Mjölnir into the stand-in, others must not.
      int bad = 0;
      int clean = 0;
      for (int pitch = -180; pitch < 180; pitch += 30) {
         for (int roll = -90; roll <= 90; roll += 30) {
            body.resetBones();
            body.getBone("right_arm").rotX = (float)Math.toRadians(pitch);
            body.getBone("right_arm").rotZ = (float)Math.toRadians(roll);
            boolean hammerHit = hits(body, hammer, rules).stream().anyMatch(h -> h.describe().contains("mjolnir:"));
            if (hammerHit) {
               bad++;
            } else {
               clean++;
            }
         }
      }

      assertTrue(bad > 0 && clean > 0, "bad " + bad + ", clean " + clean);
   }
}

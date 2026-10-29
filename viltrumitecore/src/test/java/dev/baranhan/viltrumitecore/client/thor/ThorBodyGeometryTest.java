package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import dev.baranhan.viltrumitecore.hero.thor.ThorHero;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Rework R1 acceptance in code: the skeleton contract R2 builds on, the 3.2-block proportions,
 * the mesh scan (no arm through the body in any R1 pose), the crouch under the crouch hitbox and
 * both fists on the handle in the lift. Also writes build/thor-preview/poses.json for
 * tools/preview_thor.py.
 */
class ThorBodyGeometryTest {
   /** Allowed penetration (px): the 0.35 px sampling grid plus the overlay shells touching. */
   private static final float TOLERANCE = 0.5F;
   /** Blend frames (lift_blend*: the ~0.3 s weight ramp, each passing in a few frames) may graze by up to 1 px. */
   private static final float BLEND_TOLERANCE = 1.0F;
   private static final String[] TORSO_ONLY = {ThorAssets.BODY_BONE, ThorAssets.BELLY};
   private static final String[] TORSO = {ThorAssets.BODY_BONE, ThorAssets.BELLY, ThorAssets.HEAD, ThorAssets.BEARD,
      ThorAssets.RIGHT_LEG, ThorAssets.LEFT_LEG, ThorAssets.RIGHT_SHIN, ThorAssets.LEFT_SHIN};
   private static final String[][] ARMS = {
      {ThorAssets.RIGHT_ARM, ThorAssets.RIGHT_FOREARM, ThorAssets.RIGHT_HAND},
      {ThorAssets.LEFT_ARM, ThorAssets.LEFT_FOREARM, ThorAssets.LEFT_HAND}};

   private static BakedGeoModel body() {
      return ThorRig.load(ThorAssets.BODY);
   }

   @Test
   void skeletonContract() {
      BakedGeoModel body = body();
      Map<String, String> parents = new LinkedHashMap<>();
      parents.put(ThorAssets.BODY_BONE, null);
      parents.put(ThorAssets.BELLY, ThorAssets.BODY_BONE);
      parents.put(ThorAssets.HEAD, ThorAssets.BODY_BONE);
      parents.put(ThorAssets.BEARD, ThorAssets.HEAD);
      parents.put(ThorAssets.MJOLNIR_BELT_BONE, ThorAssets.BODY_BONE);
      for (String side : new String[]{"right", "left"}) {
         parents.put(side + "_arm", ThorAssets.BODY_BONE);
         parents.put(side + "_forearm", side + "_arm");
         parents.put(side + "_hand", side + "_forearm");
         parents.put(side + "_leg", null);
         parents.put(side + "_shin", side + "_leg");
      }

      parents.put(ThorAssets.MJOLNIR_GRIP, ThorAssets.RIGHT_HAND);
      parents.put(ThorAssets.LEFT_GRIP, ThorAssets.LEFT_HAND);
      assertEquals(parents.keySet(), new java.util.HashSet<>(ThorAssets.BONES), "ThorAssets.BONES is the documented skeleton");
      for (Map.Entry<String, String> e : parents.entrySet()) {
         GeoBone bone = body.getBone(e.getKey());
         assertNotNull(bone, e.getKey());
         if (e.getValue() == null) {
            assertNull(bone.parent, e.getKey() + " is top level");
         } else {
            assertEquals(e.getValue(), bone.parent == null ? null : bone.parent.name, e.getKey() + " parent");
         }
      }

      for (String locator : new String[]{ThorAssets.MJOLNIR_GRIP, ThorAssets.LEFT_GRIP, ThorAssets.MJOLNIR_BELT_BONE}) {
         assertTrue(body.getBone(locator).cubes.isEmpty(), locator + " is a pure locator");
      }

      // Every clip of thor.animation.json keys only skeleton bones.
      for (String name : new String[]{ThorAssets.CLIP_CROUCH, ThorAssets.CLIP_SIT, ThorAssets.CLIP_LIFT}) {
         assertNotNull(ThorRig.clip(name), name);
      }

      BakedGeoModel arms = ThorRig.load(ThorAssets.ARMS);
      for (String bone : new String[]{ThorAssets.FP_RIGHT_ARM, ThorAssets.FP_LEFT_ARM, ThorAssets.RIGHT_FOREARM, ThorAssets.LEFT_FOREARM,
         ThorAssets.RIGHT_HAND, ThorAssets.LEFT_HAND, ThorAssets.MJOLNIR_GRIP, ThorAssets.LEFT_GRIP}) {
         assertNotNull(arms.getBone(bone), "FP arms: " + bone);
      }
   }

   @Test
   void godOfWarProportions() {
      BakedGeoModel body = body();
      body.resetBones();
      float top = ThorRig.top(body, ThorAssets.HEAD);
      assertTrue(top >= 3.0F && top <= 3.5F, "spec §1: 3.0–3.5 blocks, is " + top);
      assertEquals(ThorRenderer.BODY_HEIGHT, top, 0.05F, "renderer GUI scale assumes this height");
      // Hitbox follows the body: 3.15 tall vs 3.19 visual, eye at the eyes.
      assertEquals(top, 1.8F * ThorHero.BODY_HEIGHT_SCALE, 0.1F);
      float eye = 1.62F * ThorHero.BODY_HEIGHT_SCALE;
      GeoBone head = body.getBone(ThorAssets.HEAD);
      float headBottom = GeoPose.point(head).y;
      assertTrue(eye > headBottom && eye < top, "eye height inside the head: " + headBottom + " < " + eye + " < " + top);
      float[] x = extentX(body);
      float shoulders = (x[1] - x[0]) * 16.0F;
      // Vanilla shoulders: 16 px at 0.9375 = 15 px.
      assertTrue(shoulders >= 15.0F * 2.0F, "shoulders noticeably wider than vanilla, " + shoulders + " px");
   }

   private static float[] extentX(BakedGeoModel model) {
      float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
      for (GeoBone bone : model.allBones()) {
         Matrix4f frame = GeoPose.frame(bone);
         for (GeoBone.Cube c : bone.cubes) {
            for (GeoBone.Quad q : c.quads()) {
               for (GeoBone.Vertex v : q.vertices()) {
                  float px = ThorRig.cubeFrame(frame, c).transformPosition(new Vector3f(v.x(), v.y(), v.z())).x;
                  min = Math.min(min, px);
                  max = Math.max(max, px);
               }
            }
         }
      }

      return new float[]{min, max};
   }

   /** Every R1 pose the renderer can produce from vanilla part turns, plus lift samples. */
   private static Map<String, Consumer<BakedGeoModel>> poses() {
      Map<String, Consumer<BakedGeoModel>> poses = new LinkedHashMap<>();
      poses.put("rest", b -> ThorRig.pose(b, ThorRig.rest(), 0.0F, 0.0F));
      poses.put("walk_fwd", b -> ThorRig.pose(b, ThorRig.walk(1.0F), 0.0F, 0.0F));
      poses.put("walk_back", b -> ThorRig.pose(b, ThorRig.walk(-1.0F), 0.0F, 0.0F));
      poses.put("swing", b -> ThorRig.pose(b, ThorRig.swing(), 0.0F, 0.0F));
      poses.put("crouch", b -> ThorRig.pose(b, ThorRig.crouch(), 1.0F, 0.0F));
      poses.put("sit", b -> ThorRig.pose(b, ThorRig.sit(), 0.0F, 1.0F));
      for (double t : new double[]{0.0, 0.3, 0.6, 0.9}) {
         poses.put("lift_" + (int)Math.round(t * 10), b -> ThorRig.lift(b, t, 1.0F));
      }

      poses.put("lift_blend", b -> ThorRig.lift(b, 0.15, 0.5F));
      poses.put("lift_blend_25", b -> ThorRig.lift(b, 0.05, 0.25F));
      poses.put("lift_blend_75", b -> ThorRig.lift(b, 0.25, 0.75F));
      return poses;
   }

   @Test
   void meshScanNoArmThroughTheBody() {
      BakedGeoModel body = body();
      List<String> failures = new ArrayList<>();
      StringBuilder report = new StringBuilder();
      for (Map.Entry<String, Consumer<BakedGeoModel>> pose : poses().entrySet()) {
         pose.getValue().accept(body);
         float worst = 0.0F;
         float allowed = pose.getKey().startsWith("lift_blend") ? BLEND_TOLERANCE : TOLERANCE;
         ThorRig.Hit worstHit = null;
         for (String[] arm : ARMS) {
            for (String limb : arm) {
               for (String fixed : TORSO) {
                  // The upper arm hangs from the body at the shoulder joint: that contact is the joint itself.
                  if (limb.endsWith("_arm") && fixed.equals(ThorAssets.BODY_BONE)) {
                     continue;
                  }

                  ThorRig.Hit hit = ThorRig.depth(body, limb, fixed);
                  if (hit.depth() > worst) {
                     worst = hit.depth();
                     worstHit = hit;
                  }

                  if (hit.depth() > allowed) {
                     failures.add(pose.getKey() + ": " + hit);
                  }
               }
            }
         }

         report.append(pose.getKey()).append(' ').append(worstHit == null ? "clear" : worstHit.toString()).append('\n');
      }

      System.out.println("[thor mesh scan]\n" + report);
      assertTrue(failures.isEmpty(), "arm cubes inside the body: " + failures);
   }

   @Test
   void feetOnTheGround() {
      BakedGeoModel body = body();
      StringBuilder report = new StringBuilder();
      List<String> failures = new ArrayList<>();
      for (Map.Entry<String, Consumer<BakedGeoModel>> pose : poses().entrySet()) {
         pose.getValue().accept(body);
         boolean crouched = pose.getKey().equals("crouch");
         float ground = crouched ? (float)-ThorRenderer.CROUCH_OFFSET : 0.0F;
         float right = (ThorRig.bottom(body, ThorAssets.RIGHT_SHIN) - ground) * 16.0F;
         float left = (ThorRig.bottom(body, ThorAssets.LEFT_SHIN) - ground) * 16.0F;
         report.append(String.format(java.util.Locale.ROOT, "%s feet %.2f / %.2f px, crown %.2f px, hip %s%n", pose.getKey(), right, left,
            (ThorRig.top(body, ThorAssets.HEAD) - ground) * 16.0F, GeoPose.point(body.getBone(ThorAssets.BODY_BONE)).mul(16.0F)));
         boolean planted = pose.getKey().startsWith("rest") || pose.getKey().equals("crouch") || pose.getKey().startsWith("lift_");
         if (planted && (Math.abs(right) > 1.0F || Math.abs(left) > 1.0F)) {
            failures.add(pose.getKey() + " " + right + " / " + left);
         }
      }

      System.out.println("[thor feet]\n" + report);
      assertTrue(failures.isEmpty(), "feet off the ground (px): " + failures);
   }

   @Test
   void crouchStaysUnderTheCrouchHitbox() {
      BakedGeoModel body = body();
      ThorRig.pose(body, ThorRig.crouch(), 1.0F, 0.0F);
      float crown = ThorRig.top(body, ThorAssets.HEAD) + (float)ThorRenderer.CROUCH_OFFSET;
      float box = 1.5F * ThorHero.BODY_HEIGHT_SCALE;
      assertTrue(crown <= box + 0.02F, "crouched crown " + crown + " above the 2.625 crouch box");
      ThorRig.lift(body, 0.6, 1.0F);
      float liftCrown = ThorRig.top(body, ThorAssets.HEAD);
      assertTrue(liftCrown <= box + 0.02F, "lift crown " + liftCrown);
   }

   @Test
   void bothFistsOnTheHandle() {
      BakedGeoModel body = body();
      Vec3[] palms = ThorLift.thorPalms(ThorRig.grip());
      Vector3f top = ThorRig.liftModel(ThorRig.grip().handleTop());
      Vector3f foot = ThorRig.liftModel(ThorRig.HAMMER_REST.add(0.0, MjolnirPoseAccess.handleBottom(), 0.0));
      for (double t : new double[]{0.0, 0.25, 0.6, 1.1}) {
         ThorRig.lift(body, t, 1.0F);
         Vector3f right = GeoPose.point(body.getBone(ThorAssets.MJOLNIR_GRIP));
         Vector3f left = GeoPose.point(body.getBone(ThorAssets.LEFT_GRIP));
         assertEquals(0.0F, right.distance(ThorRig.liftModel(palms[0])) * 16.0F, 0.5F, "right fist on the handle at t=" + t);
         assertEquals(0.0F, left.distance(ThorRig.liftModel(palms[1])) * 16.0F, 0.5F, "left fist on the handle at t=" + t);
         for (Vector3f fist : new Vector3f[]{right, left}) {
            float axis = (float)Math.hypot(fist.x - top.x, fist.z - top.z) * 16.0F;
            assertTrue(axis < 0.5F, "fist centre on the handle axis, " + axis + " px off");
            assertTrue(fist.y < top.y && fist.y > foot.y, "fist between pommel and head");
         }

         // The handle runs through the fist: the fist's width axis (local x) is along the handle.
         Vector3f along = GeoPose.modelRotation(body.getBone(ThorAssets.RIGHT_HAND)).transform(new Vector3f(0.0F, 0.0F, 1.0F));
         assertTrue(Math.abs(along.y) > 0.9F, "right fist turned around the vertical handle: " + along);
      }
   }

   @Test
   void dumpPreviewPoses() throws IOException {
      BakedGeoModel body = body();
      BakedGeoModel hand = ThorRig.load(ThorAssets.MJOLNIR_HAND);
      BakedGeoModel belt = ThorRig.load(ThorAssets.MJOLNIR_BELT);
      BakedGeoModel world = ThorRig.load(new net.minecraft.resources.ResourceLocation("viltrumitecore", "geo/thor/mjolnir_world.geo.json"));
      Map<String, Object> out = new LinkedHashMap<>();
      Map<String, Object> third = new LinkedHashMap<>();
      for (Map.Entry<String, Consumer<BakedGeoModel>> pose : poses().entrySet()) {
         pose.getValue().accept(body);
         List<ThorRig.Quad> quads = new ArrayList<>(ThorRig.body(body));
         if (pose.getKey().startsWith("lift")) {
            quads.addAll(ThorRig.plantedHammer(world));
         } else if (pose.getKey().equals("rest") || pose.getKey().startsWith("walk")) {
            quads.addAll(ThorRig.hammerAt(hand, ThorRenderer.locator(body, ThorAssets.MJOLNIR_GRIP)));
         } else {
            quads.addAll(ThorRig.hammerAt(belt, ThorRenderer.locator(body, ThorAssets.MJOLNIR_BELT_BONE)));
         }

         third.put(pose.getKey(), quads);
      }

      out.put("third_person", third);
      // First person, hammer in hand: thor_arms.geo.json with the renderer's rest elbow bend (vanilla FP arm chain in the tool).
      BakedGeoModel arms = ThorRig.load(ThorAssets.ARMS);
      arms.resetBones();
      for (String elbow : new String[]{ThorAssets.RIGHT_FOREARM, ThorAssets.LEFT_FOREARM}) {
         arms.getBone(elbow).rotX = (float)Math.toRadians(ThorRenderer.FP_ELBOW);
      }

      Map<String, Object> fp = new LinkedHashMap<>();
      List<ThorRig.Quad> right = new ArrayList<>(), left = new ArrayList<>();
      ThorRig.emit(arms.getBone(ThorAssets.FP_RIGHT_ARM), new Matrix4f(), "body", right);
      right.addAll(ThorRig.hammerAt(hand, ThorRenderer.locator(arms, ThorAssets.MJOLNIR_GRIP)));
      ThorRig.emit(arms.getBone(ThorAssets.FP_LEFT_ARM), new Matrix4f(), "body", left);
      fp.put("right", right);
      fp.put("left", left);
      out.put("first_person_arms", fp);
      // First person lift: the body's two arm subtrees (as onRenderHand draws them) and the planted hammer, camera at the crouched eye.
      ThorRig.lift(body, 0.6, 1.0F);
      List<ThorRig.Quad> lift = new ArrayList<>();
      Matrix4f torso = GeoPose.frame(body.getBone(ThorAssets.BODY_BONE));
      ThorRig.emit(body.getBone(ThorAssets.RIGHT_ARM), torso, "body", lift);
      ThorRig.emit(body.getBone(ThorAssets.LEFT_ARM), torso, "body", lift);
      lift.addAll(ThorRig.plantedHammer(world));
      Map<String, Object> fpLift = new LinkedHashMap<>();
      fpLift.put("quads", lift);
      fpLift.put("eye_px", 1.27F * ThorHero.BODY_HEIGHT_SCALE * 16.0F);
      out.put("first_person_lift", fpLift);
      Path dir = Path.of("build/thor-preview");
      Files.createDirectories(dir);
      Gson gson = new GsonBuilder().create();
      Files.writeString(dir.resolve("poses.json"), gson.toJson(out));
      assertTrue(Files.size(dir.resolve("poses.json")) > 1000);
   }

   /** Handle bottom (where the handle enters the head) above the hammer rest, blocks. */
   static final class MjolnirPoseAccess {
      static double handleBottom() {
         return dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPose.MODEL_OFFSET;
      }
   }
}

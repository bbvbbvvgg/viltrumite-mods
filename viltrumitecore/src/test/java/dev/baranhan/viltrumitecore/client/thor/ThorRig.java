package dev.baranhan.viltrumitecore.client.thor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPose;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Test rig of Thor's R1 body: the real geo / clips from disk, the renderer's pose pipeline
 * (ThorBodyPose bridge + base clips, ThorLift.poseThor) for a few vanilla part poses, the mesh
 * penetration scan and a posed-quad dump for tools/preview_thor.py.
 */
final class ThorRig {
   static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");
   /** Feet of the test player (world origin), facing yaw 0 (+Z); a planted hammer in front at the Thor grip distance. */
   static Vec3 HAMMER_REST = new Vec3(0.0, 0.0, ThorLift.THOR_DISTANCE);
   static final float YAW = 0.0F;
   /** Model origin of the full lift: on the feet (ThorRenderer.renderRoot cancels the crouch offset at weight 1). */
   static final Vec3 LIFT_ROOT = Vec3.ZERO;
   private static final Map<String, Animation> CLIPS;

   static {
      try {
         CLIPS = AnimationParser.parse(JsonParser.parseString(Files.readString(RES.resolve(ThorAssets.ANIMATIONS.getPath()))).getAsJsonObject());
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }

      ThorBodyPose.clips = CLIPS::get;
   }

   private ThorRig() {
   }

   static Animation clip(String name) {
      return CLIPS.get(name);
   }

   static BakedGeoModel load(ResourceLocation location) {
      try {
         JsonObject root = JsonParser.parseString(Files.readString(RES.resolve(location.getPath()))).getAsJsonObject();
         return BakedGeoModel.parse(root);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   // ------------------------------------------------------------------ vanilla part poses (PlayerModel radians)

   private static float[] turns() {
      return new float[18];
   }

   private static void set(float[] t, int part, float x, float y, float z) {
      t[part * 3] = x;
      t[part * 3 + 1] = y;
      t[part * 3 + 2] = z;
   }

   static float[] rest() {
      float[] t = turns();
      // HumanoidModel idle sway at its widest (bobModelPart: zRot ±0.1 at age 0).
      set(t, 2, 0.0F, 0.0F, 0.1F);
      set(t, 3, 0.0F, 0.0F, -0.1F);
      return t;
   }

   /** Full-speed walk at the swing extreme: {@code s} = +1 right arm forward, -1 back. */
   static float[] walk(float s) {
      float[] t = turns();
      set(t, 2, -1.0F * s, 0.0F, 0.0F);
      set(t, 3, 1.0F * s, 0.0F, 0.0F);
      set(t, 4, 1.4F * s, 0.0F, 0.0F);
      set(t, 5, -1.4F * s, 0.0F, 0.0F);
      return t;
   }

   /** HumanoidModel crouch: body 0.5 forward, arms +0.4. */
   static float[] crouch() {
      float[] t = turns();
      set(t, 1, 0.5F, 0.0F, 0.0F);
      set(t, 2, 0.4F, 0.0F, 0.0F);
      set(t, 3, 0.4F, 0.0F, 0.0F);
      return t;
   }

   /** HumanoidModel riding. */
   static float[] sit() {
      float[] t = turns();
      set(t, 2, -0.62831855F, 0.0F, 0.0F);
      set(t, 3, -0.62831855F, 0.0F, 0.0F);
      set(t, 4, -1.4137167F, 0.31415927F, 0.07853982F);
      set(t, 5, -1.4137167F, -0.31415927F, -0.07853982F);
      return t;
   }

   /** A vanilla attack swing near its widest (HumanoidModel.setupAttackAnimation at attackTime 0.5, right arm). */
   static float[] swing() {
      float[] t = turns();
      float body = (float)Math.sin(Math.sqrt(0.5) * Math.PI * 2.0) * 0.2F;
      set(t, 1, 0.0F, body, 0.0F);
      set(t, 2, -1.7F, body + 0.25F, 0.0F);
      set(t, 3, 0.0F, body, 0.0F);
      return t;
   }

   static void pose(BakedGeoModel body, float[] turns, float crouch, float sit) {
      ThorBodyPose.bridge(body, turns, true);
      ThorBodyPose.baseClips(body, crouch, sit);
   }

   static MjolnirAnchors.Grip grip() {
      return new MjolnirAnchors.Grip(null, HAMMER_REST, MjolnirPose.handleTop(HAMMER_REST), MjolnirPose.upperPalm(HAMMER_REST),
         MjolnirPose.lowerPalm(HAMMER_REST), new Vec3(0.0, 1.0, 0.0), 0.0F);
   }

   /** Thor crouched at a planted hammer, as ThorRenderer.pose does it (head kept at rest above 0.5 weight). */
   static void lift(BakedGeoModel body, double seconds, float weight) {
      ThorBodyPose.bridge(body, crouch(), weight < 0.5F);
      ThorBodyPose.baseClips(body, 1.0F, 0.0F);
      ThorLift.poseThor(body, grip(), LIFT_ROOT, YAW, seconds, weight);
   }

   /** World point to Thor's model space for the crouched lift. */
   static Vector3f liftModel(Vec3 world) {
      return ThorLift.toModel(world, LIFT_ROOT, YAW);
   }

   // ------------------------------------------------------------------ geometry

   static Matrix4f cubeFrame(Matrix4f bone, GeoBone.Cube c) {
      Matrix4f m = new Matrix4f(bone);
      if (c.hasRotation()) {
         m.translate(c.pivot()).rotateZ(c.rotation().z).rotateY(c.rotation().y).rotateX(c.rotation().x).translate(new Vector3f(c.pivot()).negate());
      }

      return m;
   }

   static Vector3f[] bounds(GeoBone.Cube c) {
      Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
      for (GeoBone.Quad q : c.quads()) {
         if (q != null) {
            for (GeoBone.Vertex v : q.vertices()) {
               Vector3f p = new Vector3f(v.x(), v.y(), v.z());
               min.min(p);
               max.max(p);
            }
         }
      }

      return new Vector3f[]{min, max};
   }

   private static final Map<GeoBone.Cube, List<Vector3f>> GRID = new IdentityHashMap<>();
   private static final Map<GeoBone.Cube, List<Vector3f>> COARSE = new IdentityHashMap<>();
   /** Sampling step (px) of {@link #depth}: 0.35 for acceptance, coarser for searches. */
   static float samplePx = 0.35F;

   /** Cube volume sampled on a {@link #samplePx} grid, cube-local. */
   private static synchronized List<Vector3f> grid(GeoBone.Cube cube) {
      Map<GeoBone.Cube, List<Vector3f>> cache = samplePx == 0.35F ? GRID : COARSE;
      return cache.computeIfAbsent(cube, c -> {
         Vector3f[] box = bounds(c);
         Vector3f min = box[0], max = box[1];
         float step = samplePx / 16.0F;
         int nx = Math.max(1, (int)Math.ceil((max.x - min.x) / step)), ny = Math.max(1, (int)Math.ceil((max.y - min.y) / step)),
            nz = Math.max(1, (int)Math.ceil((max.z - min.z) / step));
         List<Vector3f> out = new ArrayList<>();
         for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) {
               for (int k = 0; k <= nz; k++) {
                  out.add(new Vector3f(min.x + (max.x - min.x) * i / nx, min.y + (max.y - min.y) * j / ny, min.z + (max.z - min.z) * k / nz));
               }
            }
         }

         return out;
      });
   }

   /** One deepest penetration: moving bone into fixed bone, depth in px. */
   record Hit(String moving, String fixed, float depth) {
      @Override
      public String toString() {
         return String.format(java.util.Locale.ROOT, "%s->%s %.2fpx", this.moving, this.fixed, this.depth);
      }
   }

   /** Deepest penetration (px) of the {@code moving} bone's own cubes into the {@code fixed} bone's own cubes, current pose. */
   static Hit depth(BakedGeoModel model, String moving, String fixed) {
      GeoBone a = model.getBone(moving);
      return a == null ? new Hit(moving, fixed, 0.0F) : depth(model, moving, points(a, new Matrix4f()), fixed);
   }

   /** Sample points (model space) of one bone's own cubes; {@code base} maps the bone's model frame further (identity for the body). */
   static List<Vector3f> points(GeoBone a, Matrix4f base) {
      List<Vector3f> points = new ArrayList<>();
      Matrix4f frameA = new Matrix4f(base).mul(GeoPose.frame(a));
      for (GeoBone.Cube c : a.cubes) {
         Matrix4f m = cubeFrame(frameA, c);
         for (Vector3f p : grid(c)) {
            points.add(m.transformPosition(new Vector3f(p)));
         }
      }

      return points;
   }

   /** Deepest penetration (px) of model-space {@code points} into the {@code fixed} bone's own cubes. */
   static Hit depth(BakedGeoModel model, String moving, List<Vector3f> points, String fixed) {
      GeoBone b = model.getBone(fixed);
      float best = 0.0F;
      if (b != null) {
         Matrix4f frameB = GeoPose.frame(b);
         for (GeoBone.Cube c : b.cubes) {
            Matrix4f inverse = cubeFrame(frameB, c).invert();
            Vector3f[] box = bounds(c);
            Vector3f min = box[0], max = box[1];
            for (Vector3f w : points) {
               Vector3f l = inverse.transformPosition(new Vector3f(w));
               float d = Math.min(Math.min(Math.min(l.x - min.x, max.x - l.x), Math.min(l.y - min.y, max.y - l.y)),
                  Math.min(l.z - min.z, max.z - l.z)) * 16.0F;
               best = Math.max(best, d);
            }
         }
      }

      return new Hit(moving, fixed, best);
   }

   /** Lowest model-space point (blocks) of the given bones' cubes in the current pose. */
   static float bottom(BakedGeoModel model, String... bones) {
      float bottom = Float.POSITIVE_INFINITY;
      for (String name : bones) {
         GeoBone bone = model.getBone(name);
         Matrix4f frame = GeoPose.frame(bone);
         for (GeoBone.Cube c : bone.cubes) {
            Matrix4f m = cubeFrame(frame, c);
            for (GeoBone.Quad q : c.quads()) {
               if (q != null) {
                  for (GeoBone.Vertex v : q.vertices()) {
                     bottom = Math.min(bottom, m.transformPosition(new Vector3f(v.x(), v.y(), v.z())).y);
                  }
               }
            }
         }
      }

      return bottom;
   }

   /** Highest model-space point (blocks) of the given bones' cubes in the current pose. */
   static float top(BakedGeoModel model, String... bones) {
      float top = Float.NEGATIVE_INFINITY;
      for (String name : bones) {
         GeoBone bone = model.getBone(name);
         Matrix4f frame = GeoPose.frame(bone);
         for (GeoBone.Cube c : bone.cubes) {
            Matrix4f m = cubeFrame(frame, c);
            for (GeoBone.Quad q : c.quads()) {
               if (q != null) {
                  for (GeoBone.Vertex v : q.vertices()) {
                     top = Math.max(top, m.transformPosition(new Vector3f(v.x(), v.y(), v.z())).y);
                  }
               }
            }
         }
      }

      return top;
   }

   // ------------------------------------------------------------------ preview dump (Bedrock px, as tools/preview_geo.py)

   /** One posed quad: corners TL, TR, BR, BL (Bedrock px) and its UV rect (0..1). */
   record Quad(String texture, float[][] corners, float[] uv) {
   }

   /** Emits {@code bone} and its subtree under {@code base} (the frame the bone's own transform starts from). */
   static void emit(GeoBone bone, Matrix4f base, String texture, List<Quad> out) {
      Matrix4f frame = new Matrix4f(base).mul(GeoPose.local(bone));
      if (!bone.hidden) {
         for (GeoBone.Cube c : bone.cubes) {
            Matrix4f m = cubeFrame(frame, c);
            for (GeoBone.Quad q : c.quads()) {
               if (q == null) {
                  continue;
               }

               float[][] corners = new float[4][];
               for (int i = 0; i < 4; i++) {
                  GeoBone.Vertex v = q.vertices()[i];
                  Vector3f p = m.transformPosition(new Vector3f(v.x(), v.y(), v.z()));
                  corners[i] = new float[]{-p.x * 16.0F, p.y * 16.0F, p.z * 16.0F};
               }

               GeoBone.Vertex a = q.vertices()[0], c2 = q.vertices()[2];
               out.add(new Quad(texture, corners, new float[]{a.u(), a.v(), c2.u(), c2.v()}));
            }
         }
      }

      for (GeoBone child : bone.children) {
         emit(child, frame, texture, out);
      }
   }

   static List<Quad> body(BakedGeoModel body) {
      List<Quad> out = new ArrayList<>();
      for (GeoBone bone : body.topLevelBones()) {
         emit(bone, new Matrix4f(), "body", out);
      }

      return out;
   }

   /** Mjölnir as ThorRenderer.drawMjolnir draws it: the hand / belt geo's hammer subtree with its pivot on {@code at}. */
   static List<Quad> hammerAt(BakedGeoModel hammerGeo, Matrix4f at) {
      hammerGeo.resetBones();
      GeoBone hammer = hammerGeo.getBone(ThorAssets.MJOLNIR_BONE);
      List<Quad> out = new ArrayList<>();
      emit(hammer, new Matrix4f(at).translate(-hammer.pivotX / 16.0F, -hammer.pivotY / 16.0F, -hammer.pivotZ / 16.0F), "mjolnir", out);
      return out;
   }

   /** Model-space frame of the planted hammer geo (MjolnirRenderer: rest + T(0, MODEL_OFFSET, 0) · Ry(-yaw) · Rx(180)). */
   static Matrix4f plantedFrame() {
      Vector3f offset = liftModel(HAMMER_REST.add(0.0, MjolnirPose.MODEL_OFFSET, 0.0));
      Matrix4f turn = new Matrix4f().rotate(new Quaternionf().rotationY((float)Math.toRadians(180.0F - YAW)).conjugate());
      return new Matrix4f().translate(offset).mul(turn).rotateY(0.0F).rotateX((float)Math.PI);
   }

   /** Sample points of the whole planted hammer in the lift's model space. */
   static List<Vector3f> plantedPoints(BakedGeoModel world) {
      world.resetBones();
      List<Vector3f> out = new ArrayList<>();
      for (GeoBone bone : world.allBones()) {
         out.addAll(points(bone, plantedFrame()));
      }

      return out;
   }

   /** The planted hammer (MjolnirRenderer) in the lift's model space. */
   static List<Quad> plantedHammer(BakedGeoModel world) {
      world.resetBones();
      Matrix4f base = plantedFrame();
      List<Quad> out = new ArrayList<>();
      for (GeoBone bone : world.topLevelBones()) {
         emit(bone, base, "mjolnir", out);
      }

      return out;
   }
}

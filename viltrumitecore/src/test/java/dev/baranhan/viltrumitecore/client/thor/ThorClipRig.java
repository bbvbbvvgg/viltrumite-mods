package dev.baranhan.viltrumitecore.client.thor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * CPU posed geometry for clip checks and previews (R2b tooling), with exactly
 * {@code AnimRenderer}'s bone and cube transforms. Skeleton-agnostic: works on
 * any geo model; attachments (Mjölnir) are drawn in a bone's frame the way a
 * held model is rendered inside its arm bone.
 */
final class ThorClipRig {
   static final Path GEO = Path.of("src/main/resources/assets/viltrumitecore/geo");
   static final float PX = 1.0F / 16.0F;

   private ThorClipRig() {
   }

   /** One cube as an oriented box in model space (blocks): centre, unit axes, half extents; {@code pivot} = its bone's posed pivot. */
   record Box(String model, String bone, Vector3f center, Vector3f[] axes, float[] half, Vector3f pivot) {
      Vector3f[] corners() {
         Vector3f[] out = new Vector3f[8];
         for (int i = 0; i < 8; i++) {
            Vector3f p = new Vector3f(this.center);
            for (int a = 0; a < 3; a++) {
               float s = (i >> a & 1) == 0 ? -this.half[a] : this.half[a];
               p.fma(s, this.axes[a]);
            }

            out[i] = p;
         }

         return out;
      }

      String id() {
         return this.model + ":" + this.bone;
      }

      /** Distance (blocks) from a point to this box, 0 inside. */
      float distance(Vector3f point) {
         Vector3f d = new Vector3f(point).sub(this.center);
         float sq = 0.0F;
         for (int a = 0; a < 3; a++) {
            float excess = Math.abs(d.dot(this.axes[a])) - this.half[a];
            if (excess > 0.0F) {
               sq += excess * excess;
            }
         }

         return (float)Math.sqrt(sq);
      }
   }

   /** The model-space matrix of a bone (AnimRenderer.renderBone order, parents first). */
   static Matrix4f boneMatrix(GeoBone bone) {
      Matrix4f m = bone.parent == null ? new Matrix4f() : boneMatrix(bone.parent);
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         m.translate(-bone.posX * PX, bone.posY * PX, bone.posZ * PX);
      }

      m.translate(bone.pivotX * PX, bone.pivotY * PX, bone.pivotZ * PX);
      m.rotateZ(bone.rotZ).rotateY(bone.rotY).rotateX(bone.rotX);
      m.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
      m.translate(-bone.pivotX * PX, -bone.pivotY * PX, -bone.pivotZ * PX);
      return m;
   }

   /** Boxes of every visible cube of a model drawn with {@code base} (identity = the body itself). */
   static List<Box> boxes(String label, BakedGeoModel model, Matrix4f base) {
      List<Box> out = new ArrayList<>();
      for (GeoBone bone : model.allBones()) {
         if (bone.hidden) {
            continue;
         }

         Matrix4f m = new Matrix4f(base).mul(boneMatrix(bone));
         Vector3f pivot = m.transformPosition(new Vector3f(bone.pivotX * PX, bone.pivotY * PX, bone.pivotZ * PX));
         for (GeoBone.Cube cube : bone.cubes) {
            Box box = box(label, bone.name, cube, m, pivot);
            if (box != null) {
               out.add(box);
            }
         }
      }

      return out;
   }

   /** Boxes of a model drawn in a body bone's frame (a held model rendered inside its arm bone). */
   static List<Box> attached(String label, BakedGeoModel part, BakedGeoModel body, String bone) {
      GeoBone parent = body.getBone(bone);
      if (parent == null) {
         throw new IllegalArgumentException("no bone " + bone);
      }

      return boxes(label, part, boneMatrix(parent));
   }

   private static Box box(String label, String bone, GeoBone.Cube cube, Matrix4f boneMatrix, Vector3f pivot) {
      Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
      Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
      for (GeoBone.Quad quad : cube.quads()) {
         if (quad == null) {
            continue;
         }

         for (GeoBone.Vertex v : quad.vertices()) {
            min.min(new Vector3f(v.x(), v.y(), v.z()));
            max.max(new Vector3f(v.x(), v.y(), v.z()));
         }
      }

      if (!Float.isFinite(min.x)) {
         return null;
      }

      Matrix4f m = new Matrix4f(boneMatrix);
      if (cube.hasRotation()) {
         m.translate(cube.pivot()).rotateZ(cube.rotation().z).rotateY(cube.rotation().y).rotateX(cube.rotation().x)
            .translate(new Vector3f(cube.pivot()).negate());
      }

      Vector3f localCenter = new Vector3f(min).add(max).mul(0.5F);
      Vector3f localHalf = new Vector3f(max).sub(min).mul(0.5F);
      Vector3f[] axes = new Vector3f[3];
      float[] half = new float[3];
      for (int a = 0; a < 3; a++) {
         Vector3f unit = new Vector3f(a == 0 ? 1 : 0, a == 1 ? 1 : 0, a == 2 ? 1 : 0);
         Vector3f dir = m.transformDirection(new Vector3f(unit));
         float scale = dir.length();
         axes[a] = scale > 1.0E-9F ? dir.div(scale) : unit;
         half[a] = localHalf.get(a) * scale;
      }

      return new Box(label, bone, m.transformPosition(localCenter), axes, half, pivot);
   }

   static BakedGeoModel load(Path path) throws IOException {
      return BakedGeoModel.parse(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
   }

   static BakedGeoModel parse(JsonObject geo) {
      return BakedGeoModel.parse(JsonParser.parseString(geo.toString()).getAsJsonObject());
   }

   /**
    * A tooling fixture with vanilla player proportions (Bedrock geometry.humanoid bone names and pivots).
    * NOT Thor's skeleton: R1 owns the body; this only lets the checks and previews test themselves.
    */
   static JsonObject standIn() {
      JsonArray bones = new JsonArray();
      bones.add(bone("body", null, 0, 24, 0, -4, 12, -2, 8, 12, 4));
      bones.add(bone("head", "body", 0, 24, 0, -4, 24, -4, 8, 8, 8));
      bones.add(bone("right_arm", "body", -5, 22, 0, -8, 12, -2, 4, 12, 4));
      bones.add(bone("left_arm", "body", 5, 22, 0, 4, 12, -2, 4, 12, 4));
      bones.add(bone("right_leg", null, -1.9F, 12, 0, -3.9F, 0, -2, 4, 12, 4));
      bones.add(bone("left_leg", null, 1.9F, 12, 0, -0.1F, 0, -2, 4, 12, 4));
      JsonObject description = new JsonObject();
      description.addProperty("identifier", "geometry.thor_tooling_stand_in");
      description.addProperty("texture_width", 64);
      description.addProperty("texture_height", 64);
      JsonObject geometry = new JsonObject();
      geometry.add("description", description);
      geometry.add("bones", bones);
      JsonArray geometries = new JsonArray();
      geometries.add(geometry);
      JsonObject root = new JsonObject();
      root.addProperty("format_version", "1.12.0");
      root.add("minecraft:geometry", geometries);
      return root;
   }

   static JsonObject bone(String name, String parent, float px, float py, float pz, float ox, float oy, float oz, float sx, float sy, float sz) {
      JsonObject bone = new JsonObject();
      bone.addProperty("name", name);
      if (parent != null) {
         bone.addProperty("parent", parent);
      }

      bone.add("pivot", vec(px, py, pz));
      JsonObject cube = new JsonObject();
      cube.add("origin", vec(ox, oy, oz));
      cube.add("size", vec(sx, sy, sz));
      cube.add("uv", vec2(0, 0));
      JsonArray cubes = new JsonArray();
      cubes.add(cube);
      bone.add("cubes", cubes);
      return bone;
   }

   private static JsonArray vec(float x, float y, float z) {
      JsonArray a = new JsonArray();
      a.add(x);
      a.add(y);
      a.add(z);
      return a;
   }

   private static JsonArray vec2(float u, float v) {
      JsonArray a = new JsonArray();
      a.add(u);
      a.add(v);
      return a;
   }
}

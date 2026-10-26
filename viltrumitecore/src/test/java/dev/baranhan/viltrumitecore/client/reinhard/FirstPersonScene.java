package dev.baranhan.viltrumitecore.client.reinhard;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Test-only export of a first-person frame: view-space quads of the camera arms and the
 * weapon meshes, for tools/preview_reinhard_fp.py (a CPU preview, not a Minecraft run). */
final class FirstPersonScene {
   private final JsonArray meshes = new JsonArray();

   static BakedGeoModel geo(String name) throws Exception {
      return BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore/geo/reinhard/" + name + ".geo.json"))).getAsJsonObject());
   }

   /** One top-level bone subtree of {@code model} drawn in {@code frame}. */
   FirstPersonScene bone(BakedGeoModel model, String top, Matrix4f frame, String texture) {
      JsonArray quads = new JsonArray();
      add(quads, model.getBone(top), frame);
      return add(quads, texture);
   }

   /** A whole model (weapon meshes) drawn in {@code frame}. */
   FirstPersonScene model(BakedGeoModel model, Matrix4f frame, String texture) {
      JsonArray quads = new JsonArray();
      for (GeoBone bone : model.topLevelBones()) add(quads, bone, frame);
      return add(quads, texture);
   }

   /** A marker point (palm, pommel) drawn as a small screen dot. */
   FirstPersonScene marker(Vector3f p, String color) {
      JsonObject m = new JsonObject();
      JsonArray at = new JsonArray();
      at.add(p.x); at.add(p.y); at.add(p.z);
      m.add("marker", at);
      m.addProperty("color", color);
      meshes.add(m);
      return this;
   }

   private FirstPersonScene add(JsonArray quads, String texture) {
      JsonObject m = new JsonObject();
      m.addProperty("texture", texture);
      m.add("quads", quads);
      meshes.add(m);
      return this;
   }

   private static void add(JsonArray quads, GeoBone bone, Matrix4f base) {
      if (bone == null || bone.hidden) return;
      PoseStack stack = new PoseStack();
      ReinhardRenderer.boneFrame(stack, bone);
      stack.translate(-bone.pivotX / 16, -bone.pivotY / 16, -bone.pivotZ / 16);
      for (GeoBone.Cube cube : bone.cubes) {
         Matrix4f transform = new Matrix4f(base).mul(stack.last().pose());
         if (cube.hasRotation()) transform.translate(cube.pivot()).rotateZ(cube.rotation().z).rotateY(cube.rotation().y)
            .rotateX(cube.rotation().x).translate(new Vector3f(cube.pivot()).negate());
         for (GeoBone.Quad quad : cube.quads()) {
            if (quad == null) continue;
            JsonArray vertices = new JsonArray();
            for (GeoBone.Vertex v : quad.vertices()) {
               Vector3f p = transform.transformPosition(new Vector3f(v.x(), v.y(), v.z()));
               JsonArray vertex = new JsonArray();
               for (float value : new float[]{p.x, p.y, p.z, v.u(), v.v()}) vertex.add(value);
               vertices.add(vertex);
            }
            quads.add(vertices);
         }
      }
      for (GeoBone child : bone.children) add(quads, child, base);
   }

   void write(String name) throws Exception {
      Path out = Path.of("build/diagnostics/reinhard-fp/" + name + ".json");
      Files.createDirectories(out.getParent());
      Files.writeString(out, meshes.toString());
   }
}

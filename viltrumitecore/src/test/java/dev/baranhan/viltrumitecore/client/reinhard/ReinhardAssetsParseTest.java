package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerBoneMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ReinhardAssetsParseTest {
   private static final Path ROOT = Path.of("src/main/resources/assets/viltrumitecore");

   private static JsonObject json(String relative) throws IOException {
      return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
   }

   private static BakedGeoModel load(String relative) throws IOException {
      return BakedGeoModel.parse(json(relative));
   }

   private static String path(net.minecraft.resources.ResourceLocation location) {
      return location.getPath();
   }

   @Test
   void bodyHasWaistRootAndTwoHipRoots() throws IOException {
      BakedGeoModel body = load(path(ReinhardAssets.BODY));
      assertEquals(List.of(ReinhardAssets.BODY_BONE,ReinhardAssets.RIGHT_LEG,ReinhardAssets.LEFT_LEG), body.topLevelBones().stream().map(b -> b.name).toList());
      int faces = 0;
      for (GeoBone bone : body.allBones()) {
         if(bone.name.equals("reid_grip")) {assertTrue(bone.cubes.isEmpty());continue;}
         assertTrue(!bone.cubes.isEmpty(), bone.name);
         if(bone.name.equals("reid_belt")) {assertEquals(6,bone.cubes.get(0).quads().length);continue;}
         for (GeoBone.Cube cube : bone.cubes) {
            faces += cube.quads().length;
         }
      }
      assertEquals(473, faces, "every OBJ face of reinhard.obj");
   }

   @Test
   void armsHaveArmorBones() throws IOException {
      BakedGeoModel arms = load(path(ReinhardAssets.ARMS));
      assertEquals(List.of("armorRightArm", "armorLeftArm"), arms.topLevelBones().stream().map(b -> b.name).toList());
      assertEquals(PlayerBoneMap.Part.RIGHT_ARM, PlayerBoneMap.of("armorRightArm"));
      assertEquals(PlayerBoneMap.Part.LEFT_ARM, PlayerBoneMap.of("armorLeftArm"));
   }

   @Test
   void pivotsAreVanilla() throws IOException {
      JsonArray bones = json(path(ReinhardAssets.BODY)).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
      for (var element : bones) {
         JsonObject bone = element.getAsJsonObject();
         if (bone.get("name").getAsString().equals(ReinhardAssets.RIGHT_ARM)) {
            JsonArray pivot = bone.getAsJsonArray("pivot");
            assertEquals(-5.0F, pivot.get(0).getAsFloat(), 1.0E-6F);
            assertEquals(22.0F, pivot.get(1).getAsFloat(), 1.0E-6F);
            assertEquals(0.0F, pivot.get(2).getAsFloat(), 1.0E-6F);
         }
      }

      // The parser negates x (Bedrock right = -x): the right arm turns about +5 like vanilla's flipped model.
      GeoBone right = load(path(ReinhardAssets.BODY)).getBone(ReinhardAssets.RIGHT_ARM);
      assertEquals(5.0F, right.pivotX, 1.0E-6F);
      assertEquals(22.0F, right.pivotY, 1.0E-6F);
      assertEquals(0.0F, right.pivotZ, 1.0E-6F);
      GeoBone leftLeg = load(path(ReinhardAssets.BODY)).getBone(ReinhardAssets.LEFT_LEG);
      assertEquals(-1.9F, leftLeg.pivotX, 1.0E-6F);
      assertEquals(12.0F, leftLeg.pivotY, 1.0E-6F);
   }

   @Test
   void heightIs32px() throws IOException {
      GeoBone head = load(path(ReinhardAssets.BODY)).getBone(ReinhardAssets.HEAD);
      float top = -Float.MAX_VALUE;
      for (GeoBone.Cube cube : head.cubes) {
         Matrix4f m = new Matrix4f();
         if (cube.hasRotation()) {
            Vector3f p = cube.pivot();
            m.translate(p).rotateZ(cube.rotation().z).rotateY(cube.rotation().y).rotateX(cube.rotation().x).translate(-p.x, -p.y, -p.z);
         }
         for (GeoBone.Quad quad : cube.quads()) {
            for (GeoBone.Vertex vertex : quad.vertices()) {
               top = Math.max(top, m.transformPosition(new Vector3f(vertex.x(), vertex.y(), vertex.z())).y);
            }
         }
      }
      assertEquals(32.0F / 16.0F, top, 1.0E-3F);
   }

   @Test
   void swordParses() throws IOException {
      BakedGeoModel sheath = load(path(ReinhardAssets.SHEATH));
      BakedGeoModel blade = load(path(ReinhardAssets.BLADE));
      assertEquals(List.of(ReinhardAssets.SWORD_BONE), sheath.topLevelBones().stream().map(b -> b.name).toList());
      assertEquals(List.of(ReinhardAssets.SWORD_BONE), blade.topLevelBones().stream().map(b -> b.name).toList());
      assertTrue(!sheath.getBone(ReinhardAssets.SWORD_BONE).cubes.isEmpty());
      assertTrue(!blade.getBone(ReinhardAssets.SWORD_BONE).cubes.isEmpty());
      GeoBone offset = blade.getBone(ReinhardAssets.SHEATHED_OFFSET_BONE);
      assertNotNull(offset);
      // Metadata bone: pivot = translation in Bedrock pixels; the parser stores x negated.
      assertEquals(ReinhardAssets.SHEATHED_OFFSET.x, -offset.pivotX, 1.0E-3F);
      assertEquals(ReinhardAssets.SHEATHED_OFFSET.y, offset.pivotY, 1.0E-3F);
      assertEquals(ReinhardAssets.SHEATHED_OFFSET.z, offset.pivotZ, 1.0E-3F);
      assertTrue(offset.cubes.isEmpty());
   }

   @Test
   void missingFaceIsNull() {
      JsonObject root = JsonParser.parseString("""
         {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test","texture_width":16,"texture_height":16},
          "bones":[{"name":"b","pivot":[0,0,0],"cubes":[{"origin":[0,0,0],"size":[2,2,0],"uv":{"north":{"uv":[0,0],"uv_size":[2,2]}}}]}]}]}
         """).getAsJsonObject();
      BakedGeoModel model = BakedGeoModel.parse(root);
      GeoBone.Cube cube = model.getBone("b").cubes.get(0);
      assertEquals(1, cube.quads().length, "only the north face; the five missing faces are not drawn");
      assertEquals(-1.0F, cube.quads()[0].normal().z, 1.0E-6F);
   }
}

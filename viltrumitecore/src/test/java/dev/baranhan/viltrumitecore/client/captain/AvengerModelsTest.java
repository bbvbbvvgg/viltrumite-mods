package dev.baranhan.viltrumitecore.client.captain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Regression for the Avengers renderer drawing the shared 64x64 rig for every kind while Thor, Widow and Hawkeye
 * had their own 128x256 shaded meshes: each kind must draw its dedicated body, every UV size must match its
 * texture, every animated / prop bone must exist, and the mesh height x render scale must match the hitbox.
 */
class AvengerModelsTest {
   private static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");

   private static JsonObject geometry(String path) throws IOException {
      Path p = RES.resolve(path);
      assertTrue(Files.isRegularFile(p), "missing model " + p);
      return JsonParser.parseString(Files.readString(p)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
   }

   /** Width and height of a texture shipped as base64 PNG (decoded into the JAR by decodeBinaryAssets). */
   private static int[] png(String path) throws IOException {
      Path p = BIN.resolve(path + ".b64");
      assertTrue(Files.isRegularFile(p), "missing texture " + p);
      byte[] b = Base64.getMimeDecoder().decode(Files.readString(p).trim());
      assertEquals(0x89, b[0] & 0xFF, path + " is not a PNG");
      ByteBuffer ihdr = ByteBuffer.wrap(b, 16, 8);
      return new int[]{ihdr.getInt(), ihdr.getInt()};
   }

   /**
    * UVs are normalised by the model's texture size, so a texture may be an exact integer multiple of it (Iron
    * Man's 128x128 armour skin over the 64x64 rig layout); dedicated meshes must match exactly.
    */
   private static void assertUv(String model, String texture, boolean exact) throws IOException {
      JsonObject d = geometry(model).getAsJsonObject("description");
      int w = d.get("texture_width").getAsInt();
      int h = d.get("texture_height").getAsInt();
      int[] size = png(texture);
      int k = size[0] / w;
      assertTrue(k >= 1 && size[0] == k * w && size[1] == k * h, model + " UV " + w + "x" + h + " vs " + texture + " " + size[0] + "x" + size[1]);
      if (exact) {
         assertEquals(1, k, model + " UV size must equal " + texture);
      }
   }

   private static Set<String> bones(String model) throws IOException {
      Set<String> out = new HashSet<>();
      if (model == null) {
         return out;
      }
      for (JsonElement b : geometry(model).getAsJsonArray("bones")) {
         out.add(b.getAsJsonObject().get("name").getAsString());
      }
      return out;
   }

   @Test
   void dedicatedBodiesAreDrawn() {
      for (AvengerKind kind : AvengerKind.values()) {
         String own = "geo/avengers/" + kind.key() + ".geo.json";
         if (Files.isRegularFile(RES.resolve(own))) {
            assertEquals(own, AvengerModels.body(kind), kind + " has its own body mesh but draws " + AvengerModels.body(kind));
         }
      }
      for (AvengerKind kind : List.of(AvengerKind.THOR, AvengerKind.BLACK_WIDOW, AvengerKind.HAWKEYE, AvengerKind.HULK)) {
         assertNotEquals(AvengerModels.SHARED_RIG, AvengerModels.body(kind), kind + " fell back to the shared rig");
      }
      assertEquals(AvengerModels.SHARED_RIG, AvengerModels.body(AvengerKind.IRON_MAN), "Iron Man's armour skin is painted for the rig");
   }

   @Test
   void uvSizesMatchTextures() throws IOException {
      for (AvengerKind kind : AvengerKind.values()) {
         boolean own = !AvengerModels.SHARED_RIG.equals(AvengerModels.body(kind));
         assertUv(AvengerModels.body(kind), AvengerModels.skin(kind), own);
         String gear = AvengerModels.gear(kind);
         if (gear != null) {
            assertNotNull(AvengerModels.gearTexture(kind));
            assertUv(gear, AvengerModels.gearTexture(kind), true);
         }
         assertUv(gear != null ? gear : AvengerModels.body(kind), AvengerModels.glow(kind), true);
      }
   }

   @Test
   void animatedAndPropBonesExist() throws IOException {
      Map<AvengerKind, List<String>> props = Map.of(
         AvengerKind.THOR, List.of("hammer", "cape", "cape_lower", "hair"),
         AvengerKind.BLACK_WIDOW, List.of("right_gun", "left_gun", "hair"),
         AvengerKind.HAWKEYE, List.of("arrow", "bow", "quiver"),
         AvengerKind.IRON_MAN, List.of("faceplate", "helmet"),
         AvengerKind.HULK, List.of("jaw"));
      for (AvengerKind kind : AvengerKind.values()) {
         Set<String> have = bones(AvengerModels.body(kind));
         have.addAll(bones(AvengerModels.gear(kind)));
         assertTrue(have.containsAll(List.of("root", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg")), kind + " rig bones");
         for (String bone : props.get(kind)) {
            assertTrue(have.contains(bone), kind + " lacks prop bone " + bone);
         }
         Path clips = RES.resolve(AvengerModels.clips(kind));
         JsonObject anims = JsonParser.parseString(Files.readString(clips)).getAsJsonObject().getAsJsonObject("animations");
         for (Map.Entry<String, JsonElement> clip : anims.entrySet()) {
            JsonObject b = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (b != null) {
               for (String bone : b.keySet()) {
                  assertTrue(have.contains(bone), kind + "/" + clip.getKey() + " animates missing bone " + bone);
               }
            }
         }
      }
   }

   @Test
   void meshHeightTimesScaleMatchesHitbox() throws IOException {
      for (AvengerKind kind : AvengerKind.values()) {
         double top = 0;
         for (JsonElement b : geometry(AvengerModels.body(kind)).getAsJsonArray("bones")) {
            JsonArray cubes = b.getAsJsonObject().getAsJsonArray("cubes");
            if (cubes == null) {
               continue;
            }
            for (JsonElement c : cubes) {
               JsonObject o = c.getAsJsonObject();
               top = Math.max(top, o.getAsJsonArray("origin").get(1).getAsDouble() + o.getAsJsonArray("size").get(1).getAsDouble());
            }
         }
         double shown = top / 16.0 * AvengerModels.scale(kind);
         assertTrue(Math.abs(shown - kind.height) <= kind.height * 0.08, kind + ": mesh " + shown + " blocks vs hitbox " + kind.height);
      }
   }
}

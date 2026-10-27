package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerBoneMap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Converted Thor assets parse, sit on the right body parts and have their textures. */
class ThorAssetsTest {
   private static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");

   private static BakedGeoModel load(ResourceLocation location) throws IOException {
      JsonObject root = JsonParser.parseString(Files.readString(RES.resolve(location.getPath()))).getAsJsonObject();
      return BakedGeoModel.parse(root);
   }

   private static BufferedImage png(String relative) throws IOException {
      Path path = BIN.resolve(relative + ".b64");
      assertTrue(Files.isRegularFile(path), relative);
      byte[] bytes = Base64.getMimeDecoder().decode(Files.readString(path, StandardCharsets.US_ASCII).trim());
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      assertNotNull(image, relative + " is a PNG");
      return image;
   }

   private static int cubes(BakedGeoModel model) {
      int count = 0;
      for (GeoBone bone : model.allBones()) {
         count += bone.cubes.size();
      }

      return count;
   }

   @Test
   void partsHangOnTheirPlayerBones() throws IOException {
      assertRoot(ThorParts.MJOLNIR_HAND, "armorRightArm", PlayerBoneMap.Part.RIGHT_ARM);
      assertRoot(ThorParts.MJOLNIR_BELT, "armorBody", PlayerBoneMap.Part.BODY);
      assertRoot(ThorParts.BEARD, "armorHead", PlayerBoneMap.Part.HEAD);
      assertRoot(ThorParts.BELLY, "armorBody", PlayerBoneMap.Part.BODY);
   }

   private static void assertRoot(ResourceLocation location, String root, PlayerBoneMap.Part part) throws IOException {
      BakedGeoModel model = load(location);
      assertEquals(List.of(root), model.topLevelBones().stream().map(bone -> bone.name).toList(), location.getPath());
      assertEquals(part, PlayerBoneMap.of(root));
      assertTrue(cubes(model) > 0, location + " has geometry");
   }

   @Test
   void handAndBeltCarryTheSameHammer() throws IOException {
      assertEquals(cubes(load(ThorParts.MJOLNIR_HAND)), cubes(load(ThorParts.MJOLNIR_BELT)));
   }

   @Test
   void texturesShipAsBinaryAssets() throws IOException {
      BufferedImage skin = png(ThorClient.SKIN.getPath());
      assertEquals(64, skin.getWidth());
      assertEquals(64, skin.getHeight());
      for (String name : new String[]{"mjolnir", "mjolnir_glow", "beard", "belly"}) {
         BufferedImage texture = png("textures/entity/thor/" + name + ".png");
         assertEquals(texture.getWidth(), texture.getHeight(), name + " is square like its UV space");
      }

      assertEquals(png("textures/entity/thor/mjolnir.png").getWidth(), png("textures/entity/thor/mjolnir_glow.png").getWidth(),
         "the glow pass shares the hammer UVs");
      for (String icon : new String[]{"thunder", "onslaught", "wrath", "teleport"}) {
         BufferedImage image = png("textures/gui/ability/thor/" + icon + ".png");
         // A 32-pixel grid upscaled 2x nearest, like the other heroes' panel icons.
         assertEquals(64, image.getWidth(), icon);
         assertEquals(64, image.getHeight(), icon);
      }
   }
}

package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Session A clips end at their server lengths; the world hammer model parses. */
class MjolnirClientTest {
   @Test
   void everyAClipEndsAtItsServerLength() {
      Map<ThorMove, ThorClips.Clip> clips = MjolnirClient.clips();
      assertEquals(MjolnirModule.DEFINITIONS.keySet(), clips.keySet());
      clips.forEach((move, clip) -> {
         assertEquals(MjolnirModule.DEFINITIONS.get(move).length(), clip.lastTick(), 1.0E-6, move.name());
         for (float[][] rows : new float[][][]{clip.rightArm(), clip.leftArm(), clip.body()}) {
            if (rows != null) {
               assertEquals(0.0F, rows[0][0], move.name());
               for (int i = 1; i < rows.length; i++) {
                  assertTrue(rows[i][0] > rows[i - 1][0], move + " key " + i);
               }
            }
         }
      });
      assertNull(ThorClips.of(ThorMove.GRAB, 0), "building the clips registers nothing");
   }

   @Test
   void theWorldHammerModelParses() throws IOException {
      Path path = Path.of("src/main/resources/assets/viltrumitecore/geo/thor/mjolnir_world.geo.json");
      BakedGeoModel model = BakedGeoModel.parse(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
      int cubes = 0;
      for (GeoBone bone : model.allBones()) {
         cubes += bone.cubes.size();
      }

      assertTrue(cubes > 0, "the world hammer has geometry");
   }
}

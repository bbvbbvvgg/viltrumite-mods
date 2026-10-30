package dev.baranhan.viltrumitecore.client.captain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerAction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Avenger clips: every action has a clip for every kind, and no clip ends a full turn away from rest (a final
 * 360 would unwind backwards in the release / hand-off fade — the "flip" the roll and the flips showed).
 */
class AvengerClipsTest {
   private static final Path DIR = Path.of("src/main/resources/assets/viltrumitecore/animations/avengers");
   private static final String[] KINDS = {"iron_man", "thor", "hulk", "black_widow", "hawkeye"};

   private static JsonObject clips(String kind) throws IOException {
      return JsonParser.parseString(Files.readString(DIR.resolve(kind + ".animation.json"))).getAsJsonObject().getAsJsonObject("animations");
   }

   @Test
   void everyActionHasAClip() throws IOException {
      for (String kind : KINDS) {
         JsonObject a = clips(kind);
         for (AvengerAction action : AvengerAction.values()) {
            if (action != AvengerAction.NONE) {
               assertTrue(a.has(action.clip()), kind + " lacks " + action.clip());
            }
         }
      }
   }

   @Test
   void clipsEndWithinHalfATurn() throws IOException {
      for (String kind : KINDS) {
         for (Map.Entry<String, JsonElement> clip : clips(kind).entrySet()) {
            JsonObject bones = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (bones == null) {
               continue;
            }
            for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
               JsonElement rot = bone.getValue().getAsJsonObject().get("rotation");
               if (rot == null) {
                  continue;
               }
               JsonElement last = rot;
               if (rot.isJsonObject()) {
                  double maxT = -1;
                  for (Map.Entry<String, JsonElement> k : rot.getAsJsonObject().entrySet()) {
                     double t = Double.parseDouble(k.getKey());
                     if (t > maxT) {
                        maxT = t;
                        last = k.getValue().isJsonObject() ? k.getValue().getAsJsonObject().get("post") : k.getValue();
                     }
                  }
               }
               for (JsonElement v : last.getAsJsonArray()) {
                  assertTrue(Math.abs(v.getAsDouble()) <= 180.0, kind + "/" + clip.getKey() + "/" + bone.getKey() + " ends at " + v);
               }
            }
         }
      }
   }
}

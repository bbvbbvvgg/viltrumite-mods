package dev.baranhan.viltrumitecore.combat.feel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.cinematic.CinematicScene;
import dev.baranhan.viltrumitecore.network.packet.CinematicS2CPacket;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The showcase wiring of wave1-22 T5: exactly one scene per ultimate, and every id really ships as a
 * JSON the client loader will read. The four call sites are not asserted from source text (project
 * rule, see {@link FeelCallersTest}); the table they all share is, and so is the shipped data.
 */
class CinematicUltimatesTest {

   @Test
   void everyShowcaseUltimateHasItsOwnScene() {
      assertEquals(4, CinematicUltimates.ALL.size(), "one scene per showcase ultimate");
      assertEquals(CinematicUltimates.ALL.size(), new HashSet<>(CinematicUltimates.ALL).size(),
         "no two ultimates share a scene");
      for (ResourceLocation id : CinematicUltimates.ALL) {
         assertNotNull(id);
         assertTrue(id.getPath().indexOf('.') < 0 && id.getPath().indexOf('/') < 0,
            id + " names one flat file, not a nested or dot path");
         assertTrue(id.getPath().length() <= CinematicS2CPacket.MAX_SCENE_ID_LENGTH,
            id + " must survive the packet's scene clamp");
         assertNotEquals(CinematicS2CPacket.DEFAULT_SCENE_ID, id, id + " must not be the built-in fallback shot");
      }
   }

   @Test
   void everyShippedSceneParses() throws IOException {
      for (ResourceLocation id : CinematicUltimates.ALL) {
         Path file = Path.of("src/main/resources").resolve(CinematicUltimates.resourcePath(id));
         assertTrue(Files.exists(file), id + " must ship at " + file);
         CinematicScene scene = CinematicScene.parse(Files.readString(file));
         assertNotNull(scene, id + " must parse into a scene");
         assertTrue(scene.path().size() >= CinematicScene.MIN_KEYS && scene.path().size() <= CinematicScene.MAX_KEYS,
            id + " needs a usable path, got " + scene.path().size() + " keys");
         assertTrue(scene.ticks() >= CinematicScene.MIN_TICKS && scene.ticks() <= CinematicScene.MAX_TICKS,
            id + " ticks out of range: " + scene.ticks());
         assertTrue(scene.slowmo() >= CinematicScene.MIN_SLOWMO && scene.slowmo() <= 1.0F,
            id + " slow-mo out of range: " + scene.slowmo());
         assertTrue(scene.duration(false) >= 20, id + " must last long enough to read as a shot");
      }
   }
}

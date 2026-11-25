package dev.baranhan.viltrumitecore.client.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Scene JSON of the cinematic camera: valid, broken, missing fields and every clamp (wave1-22, T4). */
class CinematicSceneTest {
   private static final String VALID = "{\"ticks\": 80, \"letterbox\": false, \"slowmo\": 0.5, \"keys\": ["
      + "{\"offset\": [1, 2, 3], \"look\": [0, 1, 0], \"fov\": 64}, {\"offset\": [4, 5, 6]}]}";

   @Test
   void aValidDocumentKeepsItsNumbers() {
      CinematicScene scene = CinematicScene.parse(VALID);
      assertNotNull(scene);
      assertEquals(80, scene.ticks());
      assertEquals(2, scene.path().size());
      assertFalse(scene.letterbox());
      assertEquals(0.5F, scene.slowmo(), 1.0E-6F);
      assertEquals(3.0, scene.path().key(0).offset().z, 1.0E-9);
      assertEquals(64.0F, scene.path().key(0).fov(), 1.0E-6F);
      assertEquals(CameraPath.DEFAULT_LOOK, scene.path().key(1).look());
      // The second key names no lens, so it carries the first key's (see CameraPath.bakeFov).
      assertEquals(64.0F, scene.path().key(1).fov(), 1.0E-6F);
   }

   @Test
   void missingFieldsTakeTheDefaults() {
      CinematicScene scene = CinematicScene.parse("{\"keys\": [{\"offset\": [1,1,1]}, {}]}");
      assertNotNull(scene);
      assertEquals(CinematicScene.DEFAULT_TICKS, scene.ticks());
      assertTrue(scene.letterbox());
      assertEquals(1.0F, scene.slowmo(), 1.0E-6F);
      assertEquals(CameraPath.DEFAULT_LOOK, scene.path().key(0).look());
      assertEquals(0.0, scene.path().key(1).offset().y, 1.0E-9);
   }

   @Test
   void aFisheyeLensIsClampedToTheUsefulRange() {
      // The contract of the format: a named fov never goes below 20 or above 150; a frame without one
      // carries the nearest named lens, and a path without any lens keeps the player's own (0).
      assertEquals(20.0F, CinematicScene.MIN_FOV, 1.0E-6F);
      assertEquals(150.0F, CinematicScene.MAX_FOV, 1.0E-6F);

      CinematicScene scene = CinematicScene.parse("{\"keys\": [{\"offset\": [0,0,0], \"fov\": 1},"
         + " {\"offset\": [1,1,1], \"fov\": 500}, {\"offset\": [2,2,2]}]}");
      assertNotNull(scene);
      assertEquals(20.0F, scene.path().key(0).fov(), 1.0E-6F);
      assertEquals(150.0F, scene.path().key(1).fov(), 1.0E-6F);
      // A frame without a lens of its own carries the last named one (see CameraPath).
      assertEquals(150.0F, scene.path().key(2).fov(), 1.0E-6F);

      CinematicScene lensless = CinematicScene.parse("{\"keys\": [{}, {}]}");
      assertNotNull(lensless);
      assertEquals(0.0F, lensless.path().key(0).fov(), 1.0E-6F);
   }

   @Test
   void everyNumberIsClamped() {
      CinematicScene scene = CinematicScene.parse("{\"ticks\": 100000, \"slowmo\": 9, \"keys\": ["
         + "{\"offset\": [1.0E9, -1.0E9, 0], \"fov\": 900}, {\"offset\": [0,0,0]}]}");
      assertNotNull(scene);
      assertEquals(CinematicScene.MAX_TICKS, scene.ticks());
      assertEquals(1.0F, scene.slowmo(), 1.0E-6F);
      assertEquals(CinematicScene.MAX_OFFSET, scene.path().key(0).offset().x, 1.0E-9);
      assertEquals(-CinematicScene.MAX_OFFSET, scene.path().key(0).offset().y, 1.0E-9);
      assertEquals(CinematicScene.MAX_FOV, scene.path().key(0).fov(), 1.0E-6F);

      CinematicScene tiny = CinematicScene.parse("{\"ticks\": 1, \"slowmo\": 0.001, \"keys\": [{}, {}]}");
      assertNotNull(tiny);
      assertEquals(CinematicScene.MIN_TICKS, tiny.ticks());
      assertEquals(CinematicScene.MIN_SLOWMO, tiny.slowmo(), 1.0E-6F);
   }

   @Test
   void wrongTypesFallBackInsteadOfThrowing() {
      CinematicScene scene = CinematicScene.parse("{\"ticks\": \"soon\", \"slowmo\": [], \"keys\": ["
         + "{\"offset\": \"far\", \"look\": 5}, {}]}");
      assertNotNull(scene);
      assertEquals(CinematicScene.DEFAULT_TICKS, scene.ticks());
      assertEquals(1.0F, scene.slowmo(), 1.0E-6F);
      assertEquals(0.0, scene.path().key(0).offset().x, 1.0E-9);
      assertEquals(CameraPath.DEFAULT_LOOK, scene.path().key(0).look());
   }

   @Test
   void aBrokenDocumentIsRejectedInsteadOfThrowing() {
      assertNull(CinematicScene.parse((String)null));
      assertNull(CinematicScene.parse("{ not json"));
      assertNull(CinematicScene.parse("[1,2,3]"));
      assertNull(CinematicScene.parse("{}"));
      assertNull(CinematicScene.parse("{\"keys\": \"nope\"}"));
      assertNull(CinematicScene.parse("{\"keys\": [{\"offset\": [1,2,3]}]}"));
      assertNull(CinematicScene.parse("{\"keys\": [1, 2, 3]}"));
      assertNull(CinematicScene.parse("{\"keys\": []}"));
   }

   @Test
   void nonFiniteNumbersAreTreatedAsWrongTypes() {
      // Gson parses the strings "NaN" and "Infinity" into those doubles, and no clamp catches NaN.
      CinematicScene scene = CinematicScene.parse("{\"ticks\": \"NaN\", \"slowmo\": \"NaN\", \"keys\": ["
         + "{\"offset\": [\"Infinity\", 1, 0], \"fov\": \"Infinity\"}, {\"offset\": [0, \"-Infinity\", 0]}]}");
      assertNotNull(scene);
      assertEquals(CinematicScene.DEFAULT_TICKS, scene.ticks());
      assertEquals(1.0F, scene.slowmo(), 1.0E-6F);
      assertEquals(0.0, scene.path().key(0).offset().x, 1.0E-9);
      assertEquals(1.0, scene.path().key(0).offset().y, 1.0E-9);
      assertEquals(0.0F, scene.path().key(0).fov(), 1.0E-6F);
      assertEquals(0.0, scene.path().key(1).offset().y, 1.0E-9);

      CinematicScene inf = CinematicScene.parse("{\"slowmo\": \"Infinity\", \"keys\": [{}, {}]}");
      assertNotNull(inf);
      assertEquals(1.0F, inf.slowmo(), 1.0E-6F);
      CinematicScene negative = CinematicScene.parse("{\"slowmo\": \"-Infinity\", \"keys\": [{}, {}]}");
      assertNotNull(negative);
      assertEquals(1.0F, negative.slowmo(), 1.0E-6F);
   }

   @Test
   void tooManyKeysAreCutAtTheLimit() {
      StringBuilder keys = new StringBuilder();
      for (int i = 0; i < 40; i++) {
         keys.append(i > 0 ? "," : "").append("{\"offset\": [1,1,1]}");
      }
      CinematicScene scene = CinematicScene.parse("{\"keys\": [" + keys + "]}");
      assertNotNull(scene);
      assertEquals(CinematicScene.MAX_KEYS, scene.path().size());
   }

   @Test
   void slowMotionAndReducedMotionStretchTheClockAndShrinkTheAmplitude() {
      CinematicScene scene = CinematicScene.parse(VALID);
      assertNotNull(scene);
      assertEquals(0.5F, scene.clockRate(false), 1.0E-6F);
      assertEquals(160, scene.duration(false));
      assertEquals(1.0F, scene.amplitude(false), 1.0E-6F);
      assertTrue(scene.duration(true) > scene.duration(false));
      assertEquals(CinematicScene.REDUCED_AMPLITUDE, scene.amplitude(true), 1.0E-6F);
   }

   @Test
   void theSceneIdComesFromTheFilePath() {
      assertEquals(new ResourceLocation("viltrumitecore", "orbit"),
         CinematicScenes.idOf(new ResourceLocation("viltrumitecore", "cinematics/orbit.json")));
      assertNull(CinematicScenes.idOf(new ResourceLocation("viltrumitecore", "geo/thing.json")));
      assertTrue(CinematicScenes.isSceneFile(new ResourceLocation("a", "cinematics/x.json")));
      assertFalse(CinematicScenes.isSceneFile(new ResourceLocation("a", "cinematics/x.txt")));
   }

   @Test
   void theShippedDemoSceneParses() throws Exception {
      String json = Files.readString(Path.of("src/main/resources/assets/viltrumitecore/cinematics/orbit.json"));
      CinematicScene scene = CinematicScene.parse(json);
      assertNotNull(scene, "the shipped demo scene must be loadable");
      assertTrue(scene.path().size() >= 2);
      assertTrue(scene.letterbox());
      assertTrue(scene.ticks() >= CinematicScene.MIN_TICKS && scene.ticks() <= CinematicScene.MAX_TICKS);
   }
}

package dev.baranhan.viltrumitecore.combat.finisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.cinematic.CinematicScene;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Scene pick and the shipped finisher scenes (wave1-22 T6). */
class FinisherScenesTest {
   @Test
   void heroSceneWinsAndSharedShotsSplitOnABoss() {
      assertEquals(FinisherScenes.GENERIC_A, FinisherScenes.pick(null, false));
      assertEquals(FinisherScenes.GENERIC_B, FinisherScenes.pick(null, true));
      assertEquals(FinisherScenes.THOR, FinisherScenes.pick(FinisherScenes.THOR, true));
      assertEquals("viltrumitecore", FinisherScenes.GENERIC_A.getNamespace());
   }

   /**
    * The T4 loader maps {@code <ns>:<file name>.json} to a scene id, so every id this mechanic can
    * send must have a file that parses into a short, slowed shot — and the four shots must not be the
    * same shot, or the mechanic would play one picture four times.
    */
   @Test
   void everyShippedFinisherSceneParsesAndTheyOpenDifferently() throws Exception {
      Set<Vec3> openingFrames = new HashSet<>();
      for (ResourceLocation id : List.of(FinisherScenes.GENERIC_A, FinisherScenes.GENERIC_B, FinisherScenes.HOMELANDER, FinisherScenes.THOR)) {
         Path file = Path.of("src/main/resources/assets", id.getNamespace(), "cinematics", id.getPath() + ".json");
         assertTrue(Files.exists(file), "missing scene file " + file);
         CinematicScene scene = CinematicScene.parse(Files.readString(file));
         assertNotNull(scene, id + " must parse into a scene");
         assertTrue(scene.path().size() >= CinematicScene.MIN_KEYS, id + " needs at least two keys");
         assertTrue(scene.ticks() <= 60, id + " must stay a short finisher shot, got " + scene.ticks());
         assertTrue(scene.slowmo() >= 0.3F && scene.slowmo() <= 0.5F, id + " must slow the clock into the finisher range");
         assertTrue(openingFrames.add(scene.path().key(0).offset()), id + " opens on the same frame as another shot");
      }
      assertEquals(4, openingFrames.size());
   }
}

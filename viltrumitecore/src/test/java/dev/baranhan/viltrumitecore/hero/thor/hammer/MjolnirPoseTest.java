package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Rework §6: the planted upright Mjölnir, its box, support probe and lift anchor match the world model. */
class MjolnirPoseTest {
   private static final Path GEO = Path.of("src/main/resources/assets/viltrumitecore/geo/thor/mjolnir_world.geo.json");

   @Test
   void theModelExtentsAreTheOnesThePoseUses() throws IOException {
      JsonObject geo = JsonParser.parseString(Files.readString(GEO)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
      double minY = Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      for (JsonElement bone : geo.getAsJsonArray("bones")) {
         JsonArray cubes = bone.getAsJsonObject().getAsJsonArray("cubes");
         if (cubes == null) {
            continue;
         }

         for (JsonElement cube : cubes) {
            JsonArray origin = cube.getAsJsonObject().getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonObject().getAsJsonArray("size");
            minY = Math.min(minY, origin.get(1).getAsDouble());
            maxY = Math.max(maxY, origin.get(1).getAsDouble() + size.get(1).getAsDouble());
         }
      }

      assertEquals(MjolnirPose.HEAD_END_PX, maxY, 1.0E-3, "head end (+Y)");
      assertEquals(MjolnirPose.HANDLE_END_PX, -minY, 1.0E-3, "handle end (-Y)");
   }

   @Test
   void theHeadIsBuriedAndTheHandleStandsUp() {
      Vec3 rest = new Vec3(10.5, 64.0, -3.5);
      assertTrue(MjolnirPose.BURY >= 0.2 && MjolnirPose.BURY <= 0.3, "spec: 0.2-0.3 block");
      assertEquals(64.0 - MjolnirPose.BURY, MjolnirPose.headBottom(rest).y, 1.0E-9);
      assertEquals(MjolnirPose.MODEL_OFFSET - MjolnirPose.HEAD_END_PX / 16.0, -MjolnirPose.BURY, 1.0E-9, "the upside-down model ends BURY below the ground");
      Vec3 top = MjolnirPose.handleTop(rest);
      assertEquals(rest.x, top.x, 1.0E-9, "on the vertical axis for any yaw");
      assertEquals(rest.z, top.z, 1.0E-9);
      assertEquals(64.0 + MjolnirPose.HANDLE_TOP, top.y, 1.0E-9);
      assertEquals(0.81, MjolnirPose.HANDLE_TOP, 0.01, "about a block of hammer, a fifth of it in the ground");
      assertTrue(MjolnirPose.upperPalm(rest).y < top.y && MjolnirPose.lowerPalm(rest).y < MjolnirPose.upperPalm(rest).y);
      assertTrue(MjolnirPose.lowerPalm(rest).y - 64.0 > MjolnirPose.MODEL_OFFSET - 3.25 / 16.0, "both palms above the head (model head block ends at 3.25 px)");
   }

   @Test
   void theBoxCoversTheVisibleUprightPartAndTheProbeTheBuriedHead() {
      Vec3 rest = new Vec3(0.5, 70.0, 0.5);
      AABB box = MjolnirPose.box(rest);
      assertEquals(70.0, box.minY, 1.0E-6, "from the ground");
      assertTrue(box.maxY >= MjolnirPose.handleTop(rest).y - 1.0E-6, "to the handle top");
      assertTrue(box.maxY - MjolnirPose.handleTop(rest).y < 0.02);
      assertEquals(MjolnirPose.UPRIGHT_WIDTH, box.getXsize(), 1.0E-6);
      assertTrue(MjolnirPose.UPRIGHT_WIDTH >= 2 * 5.46 / 16.0 - 0.01, "as wide as the head");
      AABB probe = MjolnirPose.supportProbe(rest);
      assertTrue(probe.minY < 70.0 && probe.maxY > 70.0, "reaches into the block under the head");
      assertTrue(probe.minY >= 70.0 - MjolnirPose.BURY, "never deeper than the head");
   }

   @Test
   void everyRestIsUprightExceptAThrowStuckInAWall() {
      assertTrue(MjolnirPose.planted(MjolnirPhase.RESTING, true, false), "landed");
      assertTrue(MjolnirPose.planted(MjolnirPhase.RESTING, true, true), "thrown into the ground: planted and ripped on recall");
      assertTrue(MjolnirPose.planted(MjolnirPhase.RESTING, false, false), "recovered / old save");
      assertFalse(MjolnirPose.planted(MjolnirPhase.RESTING, false, true), "stuck in a wall along the throw");
      for (MjolnirPhase phase : MjolnirPhase.values()) {
         if (phase != MjolnirPhase.RESTING) {
            assertFalse(MjolnirPose.planted(phase, true, false), phase.name());
         }
      }
   }

   @Test
   void liftReachAndSideFaceDeflection() {
      Vec3 rest = new Vec3(0.5, 64.0, 0.5);
      assertTrue(MjolnirPose.withinReach(new Vec3(3.0, 65.6, 0.5), rest, MjolnirTuning.LIFT_REACH));
      assertFalse(MjolnirPose.withinReach(new Vec3(6.0, 65.6, 0.5), rest, MjolnirTuning.LIFT_REACH));
      Vec3 out = MjolnirPose.besideFace(new Vec3(1.0, 66.0, 0.5), 1, 0, 0.7);
      assertEquals(1.4, out.x, 1.0E-9, "half the box plus a margin out of the face");
      assertEquals(66.0, out.y, 1.0E-9);
   }

   @Test
   void theServerUsesTheUprightPose() throws IOException {
      String entity = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/hammer/MjolnirEntity.java"));
      assertTrue(entity.contains("return this.planted() ? MjolnirPose.UPRIGHT : super.getDimensions(pose);"), "box follows the pose");
      assertTrue(entity.contains("this.refreshDimensions();"), "resized when the synced pose changes, both sides");
      assertTrue(entity.contains("MjolnirPose.supportProbe(this.position())"), "planted support = under the head");
      String lift = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/hammer/MjolnirLift.java"));
      assertTrue(lift.contains("hammer.getBoundingBox().distanceToSqr(player.getEyePosition())"), "lift reach to the real box");
      String renderer = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/thor/MjolnirRenderer.java"));
      assertFalse(renderer.contains("hammer.lying()"), "never laid on its side");
      assertTrue(renderer.contains("MjolnirPose.MODEL_OFFSET"));
   }
}

package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Item 13: the normal World Cut comes down from either shoulder, chosen automatically, and every layer agrees. */
class WorldCutSweepTest {
   @Test
   void theChoiceIsAutomaticBalancedAndTheSameEverywhere() {
      int left = 0, runs = 0, longest = 0, previous = -1;
      for (long tick = 1_000_000; tick < 1_010_000; tick++) {
         int sweep = WorldCutSweep.of(tick);
         assertEquals(sweep, WorldCutSweep.of(tick), "deterministic for one seed (server and clients)");
         if (sweep == WorldCutSweep.FROM_LEFT) left++;
         runs = sweep == previous ? runs + 1 : 1;
         longest = Math.max(longest, runs);
         previous = sweep;
      }
      assertTrue(left > 4700 && left < 5300, "about half of the casts from each shoulder: " + left);
      assertTrue(longest < 20, "no long streaks for consecutive cast ticks: " + longest);
      assertEquals(WorldCutSweep.FROM_RIGHT, WorldCutSweep.ofPhase(7));
      assertEquals(1, WorldCutSweep.side(WorldCutSweep.FROM_RIGHT));
      assertEquals(-1, WorldCutSweep.side(WorldCutSweep.FROM_LEFT));
   }

   @Test
   void theServerPutsTheSweepInTheSyncedActionPhase() {
      for (int sweep : new int[]{WorldCutSweep.FROM_RIGHT, WorldCutSweep.FROM_LEFT}) {
         ReinhardState state = new ReinhardState();
         WorldCut.start(state, sweep);
         assertEquals(ReinhardAbility.WORLD_CUT, state.action.ability());
         assertEquals(sweep, state.action.phase());
         assertEquals(ReinhardRules.WORLD_CUT_BLAST + 20, state.action.length(), "timeline unchanged");
         var clip = ReinhardClips.clip(ReinhardAbility.WORLD_CUT, state.action.phase(), 30);
         assertNotNull(clip);
         assertEquals(sweep == WorldCutSweep.FROM_LEFT ? ReinhardClips.WORLD_CUT_FROM_LEFT : ReinhardClips.WORLD_CUT, clip.animation());
      }
   }

   @Test
   void bothClipsAreExactMirrorsThatMeetSquareOnTheContact() throws Exception {
      JsonObject all = JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
      JsonObject right = all.getAsJsonObject(ReinhardClips.WORLD_CUT), left = all.getAsJsonObject(ReinhardClips.WORLD_CUT_FROM_LEFT);
      assertEquals(right.get("animation_length"), left.get("animation_length"));
      JsonObject rightGrip = right.getAsJsonObject("bones").getAsJsonObject("reid_grip").getAsJsonObject("rotation");
      JsonObject leftGrip = left.getAsJsonObject("bones").getAsJsonObject("reid_grip").getAsJsonObject("rotation");
      assertEquals(rightGrip.keySet(), leftGrip.keySet());
      for (String time : rightGrip.keySet()) {
         var r = rightGrip.getAsJsonObject(time).getAsJsonArray("vector");
         var l = leftGrip.getAsJsonObject(time).getAsJsonArray("vector");
         boolean guard = time.equals("0.0") || time.equals("5.0");
         assertEquals(r.get(0).getAsDouble(), l.get(0).getAsDouble(), 1E-9, "same chop at " + time);
         if (!guard) assertEquals(-r.get(2).getAsDouble(), l.get(2).getAsDouble(), 1E-9, "mirrored lean at " + time);
      }
      String contact = String.valueOf(ReinhardRules.WORLD_CUT_BLAST / 20.0);
      assertEquals(0, rightGrip.getAsJsonObject(contact).getAsJsonArray("vector").get(2).getAsDouble(), 1E-9, "square on the trench");
      assertEquals(0, rightGrip.getAsJsonObject("1.0").getAsJsonArray("vector").get(2).getAsDouble(), 1E-9, "raised upright first");
      assertEquals(-12, rightGrip.getAsJsonObject("1.5").getAsJsonArray("vector").get(2).getAsDouble(), 1E-9, "then settles over the right shoulder");
      assertEquals(-12, rightGrip.getAsJsonObject("3.3").getAsJsonArray("vector").get(2).getAsDouble(), 1E-9, "and holds there");
      // The chest turns and bends toward the raised shoulder (mirrored), the head stays level.
      JsonObject rightBody = right.getAsJsonObject("bones").getAsJsonObject("body").getAsJsonObject("rotation");
      JsonObject leftBody = left.getAsJsonObject("bones").getAsJsonObject("body").getAsJsonObject("rotation");
      var hold = rightBody.getAsJsonObject("3.3").getAsJsonArray("vector");
      var mirror = leftBody.getAsJsonObject("3.3").getAsJsonArray("vector");
      assertEquals(12, hold.get(1).getAsDouble(), 1E-9);
      assertEquals(-6, hold.get(2).getAsDouble(), 1E-9);
      assertEquals(-hold.get(1).getAsDouble(), mirror.get(1).getAsDouble(), 1E-9);
      assertEquals(-hold.get(2).getAsDouble(), mirror.get(2).getAsDouble(), 1E-9);
      JsonObject bones = left.getAsJsonObject("bones");
      assertTrue(bones.has("left_leg") && bones.has("right_leg") && bones.has("body") && bones.has("head"));
   }

   @Test
   void firstPersonFollowsTheSameShoulder() {
      double hold = ReinhardRules.WORLD_CUT_BLAST - 20, contact = ReinhardRules.WORLD_CUT_BLAST;
      var plain = ReinhardFirstPersonPose.worldCut(hold, false);
      var fromRight = ReinhardFirstPersonPose.worldCut(hold, false, WorldCutSweep.FROM_RIGHT);
      var fromLeft = ReinhardFirstPersonPose.worldCut(hold, false, WorldCutSweep.FROM_LEFT);
      assertTrue(fromRight.x() > plain.x() && fromLeft.x() < plain.x(), "the arm is raised toward the shoulder");
      assertEquals(plain.yaw() - fromRight.yaw(), fromLeft.yaw() - plain.yaw(), 1E-4, "mirror images");
      assertEquals(plain.roll() - fromRight.roll(), fromLeft.roll() - plain.roll(), 1E-4);
      assertTrue(Math.abs(fromRight.roll() - plain.roll()) > 15, "readable lean");
      for (int sweep : new int[]{WorldCutSweep.FROM_RIGHT, WorldCutSweep.FROM_LEFT}) {
         var hit = ReinhardFirstPersonPose.worldCut(contact, false, sweep);
         var square = ReinhardFirstPersonPose.worldCut(contact, false);
         assertEquals(square.yaw(), hit.yaw(), 1E-4, "square at the contact");
         assertEquals(square.roll(), hit.roll(), 1E-4);
         assertEquals(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.worldCut(0, false, sweep));
         assertEquals(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.worldCut(ReinhardRules.WORLD_CUT_BLAST + 20, false, sweep));
      }
      assertEquals(ReinhardFirstPersonPose.worldCut(hold, false, WorldCutSweep.FROM_LEFT),
         ReinhardFirstPersonPose.of(ReinhardAbility.WORLD_CUT, WorldCutSweep.FROM_LEFT, hold), "of() reads the phase");
   }
}

package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ReinhardChopFirstPersonTest {
   /** The kit chop mixins' authored wind and strike poses {x, y, z, rx, ry, rz}, per chop type. */
   private static final float[][] WIND = {{1.34F, -0.59F, 0.11F, 70.79F, 9.75F, -81.93F}, {-0.06F, 0.0F, 0.17F, -11.48F, 48.13F, 34.62F}};
   private static final float[][] STRIKE = {{1.2F, -0.35F, -0.98F, 47.28F, 48.34F, -131.75F}, {-0.07F, 0.1F, -0.85F, -34.11F, -27.78F, 0.0F}};

   private static void assertPose(float[] expected, float[] actual, String what) {
      for (int c = 0; c < 6; c++) {
         assertEquals(expected[c], actual[c], 1e-4, what + " channel " + c);
      }
   }

   @Test void authoredPosesLandOnTheirKeysWithTheStrikeOvershootAtContact() {
      for (int type = 0; type < 2; type++) {
         float[] over = new float[6];
         for (int c = 0; c < 6; c++) over[c] = STRIKE[type][c] * 1.15F;
         assertPose(new float[6], ReinhardChopFirstPerson.sample(type, 0), "rest");
         assertPose(WIND[type], ReinhardChopFirstPerson.sample(type, ReinhardChopFirstPerson.WIND), "wind");
         assertPose(over, ReinhardChopFirstPerson.sample(type, ReinhardChopFirstPerson.CONTACT), "contact");
         assertPose(STRIKE[type], ReinhardChopFirstPerson.sample(type, ReinhardChopFirstPerson.SETTLE), "settle");
         assertPose(new float[6], ReinhardChopFirstPerson.sample(type, 1), "recovered");
      }
   }

   @Test void noHoldsAndNoVelocityBreaksAt240Fps() {
      int frames = 20 * 12; // the 20-tick chop at 240 fps
      for (int type = 0; type < 2; type++) {
         float[] previous = ReinhardChopFirstPerson.sample(type, 0);
         double previousSpeed = 0;
         int still = 0;
         for (int f = 1; f <= frames; f++) {
            float[] pose = ReinhardChopFirstPerson.sample(type, f / (float)frames);
            double speed = 0;
            for (int c = 3; c < 6; c++) speed = Math.max(speed, Math.abs(pose[c] - previous[c]));
            still = speed < 1e-3 ? still + 1 : 0;
            // The legacy timeline held the wind for 3 ticks and the strike for 6 (36 and 72 frames).
            assertTrue(still < 12, "a tick-long hold before " + f + " (type " + type + ")");
            assertTrue(speed < 4.5, "pose jump of " + speed + "° at frame " + f + " (type " + type + ")");
            // Legacy segment cuts changed the per-frame rotation by ~3° at once; the monotone keys stay C1.
            assertTrue(Math.abs(speed - previousSpeed) < .6, "velocity break of " + Math.abs(speed - previousSpeed) + " at frame " + f);
            previous = pose;
            previousSpeed = speed;
         }
      }
   }

   @Test void bothViewsReachContactOnTheSameTick() {
      for (int ticks = 20; ticks >= 1; ticks--) {
         for (float partial : new float[]{0, .25F, .5F, .999F}) {
            double thirdPerson = ReinhardPoses.chopClip(ticks, ticks % 2 == 0, partial).seconds();
            assertEquals(thirdPerson, ReinhardChopFirstPerson.time(ticks, partial), 1e-6, ticks + "+" + partial);
         }
      }
      assertEquals(0, ReinhardChopFirstPerson.time(25, 0), "clamped like the kit mixins");
   }

   /** The decimal a float key is written as in the clip JSON. */
   private static double exact(float key) {
      return Double.parseDouble(Float.toString(key));
   }

   @Test void firstPersonKeysAreTheThirdPersonChopKeys() throws Exception {
      JsonObject clips;
      try (var in = ReinhardChopFirstPersonTest.class.getResourceAsStream("/assets/viltrumitecore/animations/reinhard/reinhard.animation.json")) {
         assertNotNull(in);
         clips = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
      }
      var fp = List.of(0.0, exact(ReinhardChopFirstPerson.WIND), exact(ReinhardChopFirstPerson.CONTACT), exact(ReinhardChopFirstPerson.SETTLE), 1.0);
      var choppingArmKeys = clips.getAsJsonObject("chop_right").getAsJsonObject("bones")
         .getAsJsonObject("right_arm").getAsJsonObject("rotation").keySet().stream().map(Double::parseDouble).sorted().toList();
      for (String side : new String[]{"left", "right"}) {
         var bones = clips.getAsJsonObject("chop_" + side).getAsJsonObject("bones");
         if (side.equals("right")) {
            var grip = new TreeSet<Double>();
            for (String channel : new String[]{"position", "rotation"})
               bones.getAsJsonObject("reid_grip").getAsJsonObject(channel).keySet().forEach(k -> grip.add(Double.parseDouble(k)));
            assertEquals(fp, List.copyOf(grip), side + " hand keys");
         } else {
            // The free left hand chops: Reid stays in the right hand, so no grip rows follow the chopping arm.
            assertFalse(bones.has("reid_grip"), "the left chop carries no sword grip");
         }
         var arm = new TreeSet<Double>();
         bones.getAsJsonObject(side + "_arm").getAsJsonObject("rotation").keySet().forEach(k -> arm.add(Double.parseDouble(k)));
         assertEquals(choppingArmKeys, List.copyOf(arm), side + " arm keys");
         assertTrue(arm.contains(0.35), side + " arm contact key");
      }
   }
}

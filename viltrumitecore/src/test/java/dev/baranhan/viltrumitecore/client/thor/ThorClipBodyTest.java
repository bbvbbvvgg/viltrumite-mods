package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Rework spec §3: no hand or hammer through the body, checked on every tick
 * of every authored third-person clip on R1's body. Aborted (skipped, not
 * passed) until both R1's model and {@code thor.animation.json} are here.
 */
class ThorClipBodyTest {
   private static final int MAX_LINES = 40;

   @Test
   void authoredClipsKeepHandsAndHammerOutOfTheBody() throws Exception {
      Assumptions.assumeTrue(Files.exists(ThorClipChecks.BODY), () -> ThorClipChecks.BODY + " (R1's body) is not on this branch yet");
      Assumptions.assumeTrue(Files.exists(ThorClipContract.FILE), () -> ThorClipContract.FILE + " is not authored yet (R2b)");
      BakedGeoModel body = ThorClipRig.load(ThorClipChecks.BODY);
      BakedGeoModel hammer = ThorClipRig.load(ThorClipChecks.HAMMER);
      assertNotNull(body.getBone(ThorClipChecks.GRIP_BONE), "set ThorClipChecks.GRIP_BONE to R1's grip bone");
      assertNotNull(body.getBone(ThorClipChecks.OFF_HAND_BONE), "set ThorClipChecks.OFF_HAND_BONE to R1's left hand bone");
      Map<String, Animation> clips = AnimationParser.parse(JsonParser.parseString(Files.readString(ThorClipContract.FILE)).getAsJsonObject());
      ThorIntersection rules = ThorClipChecks.rules(ThorClipChecks.GRIP_BONE, ThorClipChecks.OFF_HAND_BONE);
      body.resetBones();
      rules.rest(ThorClipRig.boxes(ThorIntersection.BODY, body, new Matrix4f()));
      List<String> failures = new ArrayList<>();
      int frames = 0;
      for (Map.Entry<String, Animation> entry : clips.entrySet()) {
         String name = entry.getKey();
         if (name.endsWith(ThorClipContract.FP_SUFFIX)) {
            continue;
         }

         Animation clip = entry.getValue();
         int ticks = (int)Math.round(clip.lengthSeconds() * 20.0);
         boolean withHammer = ThorClipChecks.hammerInHand(name);
         for (int tick = 0; tick <= ticks; tick++) {
            List<ThorClipRig.Box> boxes = ThorClipChecks.pose(body, hammer, ThorClipChecks.GRIP_BONE, clip, tick / 20.0, withHammer);
            List<ThorIntersection.Hit> hits = rules.check(boxes);
            frames++;
            if (!hits.isEmpty() && failures.size() < MAX_LINES) {
               failures.add(name + " @" + tick + ": " + hits.get(0).describe() + (hits.size() > 1 ? " (+" + (hits.size() - 1) + ")" : ""));
            }
         }
      }

      int checked = frames;
      assertTrue(failures.isEmpty(), () -> "penetration in " + failures.size() + "+ of " + checked + " frames:\n  " + String.join("\n  ", failures));
   }
}

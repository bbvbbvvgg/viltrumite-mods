package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Rework spec §3: no hand or hammer through the body, checked on every tick of every authored
 * third-person clip on R1's body with R2b's acceptance scan ({@link ThorClipMesh}, the Java twin of
 * {@code tools/assets/thor_mesh.py}). Mjölnir sits where ThorRenderer draws it on that tick
 * ({@link ThorClipChecks#place}: in the fist on {@code mjolnir_grip}, on the belt on
 * {@code mjolnir_belt}, or away). First-person clips are checked by ThorFirstPersonTest, R1's clips
 * ({@link #R1_CLIPS}) by R1's own tests.
 */
class ThorClipBodyTest {
   private static final int MAX_LINES = 60;
   /**
    * R1's clips, copied into the file as R1 wrote them and outside R2b's acceptance (make_thor_clips_run.write):
    * crouch / sit are overlays on top of the walk / idle arms, lift_squat is checked by ThorBodyGeometryTest.
    */
   static final java.util.Set<String> R1_CLIPS = java.util.Set.of("crouch", "sit", "lift_squat");
   /**
    * Known residual penetrations: empty since the 2026-10-30 rework (every clip reworked in make_thor_clips.py until
    * it scanned clean). The cap is {max ticks, max depth px}; a clip outside this list, a clip past its cap, or a listed
    * clip that became clean (delete its line) fails the test, so the list can only shrink. Keep it empty.
    */
   static final Map<String, double[]> KNOWN = Map.of();

   @Test
   void authoredClipsKeepHandsAndHammerOutOfTheBody() throws Exception {
      BakedGeoModel body = ThorClipRig.load(ThorClipChecks.BODY);
      BakedGeoModel hand = ThorClipRig.load(ThorClipChecks.HAMMER);
      BakedGeoModel belt = ThorClipRig.load(ThorClipRig.GEO.resolve("thor/mjolnir_belt.geo.json"));
      Vector3f beltGrip = ThorClipMesh.beltGrip(body, belt);
      Map<String, Animation> clips = AnimationParser.parse(JsonParser.parseString(Files.readString(ThorClipContract.FILE)).getAsJsonObject());
      Map<String, String> worst = new TreeMap<>();
      int frames = 0;
      int bad = 0;
      for (Map.Entry<String, Animation> entry : clips.entrySet()) {
         String name = entry.getKey();
         if (name.endsWith(ThorClipContract.FP_SUFFIX) || R1_CLIPS.contains(name)) {
            continue;
         }

         Animation clip = entry.getValue();
         int ticks = (int)Math.round(clip.lengthSeconds() * 20.0);
         int badTicks = 0;
         ThorClipMesh.Hit deepest = null;
         int deepestTick = -1;
         for (int tick = 0; tick <= ticks; tick++) {
            ThorClipChecks.Place place = ThorClipChecks.place(name, tick);
            AnimationController.seek(clip, body, tick / 20.0);
            List<ThorClipMesh.Hit> hits = ThorClipMesh.scan(body, place == ThorClipChecks.Place.HAND ? hand : null,
               place == ThorClipChecks.Place.BELT ? belt : null, beltGrip);
            frames++;
            if (!hits.isEmpty()) {
               badTicks++;
               bad++;
               if (deepest == null || hits.get(0).depth() > deepest.depth()) {
                  deepest = hits.get(0);
                  deepestTick = tick;
               }
            }
         }

         double[] cap = KNOWN.get(name);
         if (cap != null && deepest == null) {
            worst.put(name, "clean now: remove it from KNOWN");
         } else if (deepest != null && (cap == null || badTicks > cap[0] || deepest.depth() > cap[1])) {
            worst.put(name, badTicks + " ticks, worst @" + deepestTick + ": " + deepest.describe() + (cap != null ? " (over its KNOWN cap)" : ""));
         }
      }

      List<String> lines = new ArrayList<>();
      worst.forEach((name, line) -> {
         if (lines.size() < MAX_LINES) {
            lines.add(name + ": " + line);
         }
      });
      int checked = frames;
      int failed = bad;
      assertTrue(worst.isEmpty(), () -> "new / worse penetration (> " + ThorClipMesh.TOLERANCE + " px) in " + failed + " of " + checked + " frames, "
         + worst.size() + " clips:\n  " + String.join("\n  ", lines));
   }
}

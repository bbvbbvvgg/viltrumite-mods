package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * R2a → R2b: the authored Thor clips last exactly the server's rows and mark
 * the damage tick ({@link ThorClipContract}). Until {@code thor.animation.json}
 * exists the file check is reported as skipped (aborted, not passed); the
 * checker itself is always verified on a reference file built from the table.
 */
class ThorClipContractTest {
   @Test
   void authoredClipsMatchTheAttackTable() throws Exception {
      Assumptions.assumeTrue(Files.exists(ThorClipContract.FILE), () -> ThorClipContract.FILE + " is not authored yet (R2b): "
         + ThorAttacks.ALL.size() * 2 + " clips expected, the check runs as soon as the file appears");
      Map<String, Animation> clips = AnimationParser.parse(JsonParser.parseString(Files.readString(ThorClipContract.FILE)).getAsJsonObject());
      List<String> violations = ThorClipContract.violations(clips);
      assertTrue(violations.isEmpty(), () -> violations.size() + " clip contract violations:\n  " + String.join("\n  ", violations));
   }

   @Test
   void theReferenceFileSatisfiesTheContract() {
      assertEquals(List.of(), ThorClipContract.violations(parse(ThorClipContract.reference())));
   }

   @Test
   void theCheckerCatchesEveryBrokenRule() {
      ThorAttacks.Attack slam = ThorAttacks.byClip("hammer_ground_slam");
      ThorAttacks.Attack wrath = ThorAttacks.byClip("wrath");

      JsonObject missing = ThorClipContract.reference();
      missing.getAsJsonObject("animations").remove(slam.fpClip());
      assertViolation(missing, slam.fpClip() + ": missing");

      JsonObject wrongLength = ThorClipContract.reference();
      clip(wrongLength, wrath.clip()).addProperty("animation_length", 2.6);
      assertViolation(wrongLength, String.format(java.util.Locale.ROOT, "wrath: animation_length 2.6000 s, expected %.4f s (%d t)", wrath.length() / 20.0, wrath.length()));

      JsonObject looping = ThorClipContract.reference();
      clip(looping, slam.clip()).addProperty("loop", true);
      assertViolation(looping, slam.clip() + ": loops");

      JsonObject noContact = ThorClipContract.reference();
      clip(noContact, slam.clip()).remove("timeline");
      assertViolation(noContact, slam.clip() + ": 0 'contact' markers");

      JsonObject lateContact = ThorClipContract.reference();
      JsonObject timeline = new JsonObject();
      timeline.addProperty("0.75", "contact;");
      clip(lateContact, slam.clip()).add("timeline", timeline);
      int contact = slam.phases().contactTick();
      assertViolation(lateContact, slam.clip() + ": 'contact' at 0.7500 s, expected " + String.format(java.util.Locale.ROOT, "%.4f", contact / 20.0)
         + " s (tick " + contact);

      JsonObject stray = ThorClipContract.reference();
      JsonObject idle = new JsonObject();
      idle.addProperty("loop", true);
      idle.addProperty("animation_length", 2.0);
      JsonObject marker = new JsonObject();
      marker.addProperty("0.5", "contact");
      idle.add("timeline", marker);
      stray.getAsJsonObject("animations").add("idle_hammer", idle);
      assertViolation(stray, "idle_hammer: not in the attack table but carries a 'contact' marker");

      // Clips outside the table (loops, lift, flight) are free otherwise.
      JsonObject loop = ThorClipContract.reference();
      idle.remove("timeline");
      loop.getAsJsonObject("animations").add("idle_hammer", idle);
      assertEquals(List.of(), ThorClipContract.violations(parse(loop)));
   }

   @Test
   void oneActionClockForBothViews() {
      Map<String, Animation> clips = parse(ThorClipContract.reference());
      for (ThorAttacks.Attack row : ThorAttacks.ALL) {
         Animation tp = clips.get(row.clip());
         Animation fp = clips.get(row.fpClip());
         assertEquals(tp.lengthSeconds(), fp.lengthSeconds(), 1.0E-9, row.clip());
         assertEquals(ThorClipContract.contacts(tp), ThorClipContract.contacts(fp), row.clip());
      }
   }

   private static void assertViolation(JsonObject file, String expected) {
      List<String> violations = ThorClipContract.violations(parse(file));
      assertEquals(1, violations.size(), () -> String.join("\n", violations));
      assertTrue(violations.get(0).startsWith(expected), violations.get(0));
   }

   private static JsonObject clip(JsonObject file, String name) {
      return file.getAsJsonObject("animations").getAsJsonObject(name);
   }

   private static Map<String, Animation> parse(JsonObject file) {
      return AnimationParser.parse(JsonParser.parseString(file.toString()).getAsJsonObject());
   }
}

package dev.baranhan.viltrumitecore.client.dio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The World clips against the server: lengths, loops and contact keys. A strike
 * is "fastest at contact": the striking arm reaches its furthest key exactly on
 * the server hit tick, and the biggest per-key step of the swing lands on it.
 */
class TheWorldClipsTest {
   private static final Path ANIMS = Path.of("src/main/resources/assets/viltrumitecore/animations/dio");

   private static JsonObject clip(TheWorldClips.Clip clip) throws Exception {
      JsonObject root = JsonParser.parseString(Files.readString(ANIMS.resolve("the_world_" + clip.id + ".animation.json"))).getAsJsonObject();
      return root.getAsJsonObject("animations").getAsJsonObject(clip.id);
   }

   /** Arm pitch keys (|x rotation|, degrees) by tick. */
   private static TreeMap<Double, Double> armKeys(TheWorldClips.Clip clip, String bone) throws Exception {
      JsonObject rotation = clip(clip).getAsJsonObject("bones").getAsJsonObject(bone).getAsJsonObject("rotation");
      TreeMap<Double, Double> keys = new TreeMap<>();
      for (Map.Entry<String, com.google.gson.JsonElement> e : rotation.entrySet()) {
         var value = e.getValue();
         var vector = value.isJsonArray() ? value.getAsJsonArray() : value.getAsJsonObject().getAsJsonArray("post");
         keys.put(Math.round(Double.parseDouble(e.getKey()) * 20.0 * 1000.0) / 1000.0, Math.abs(vector.get(0).getAsDouble()));
      }

      return keys;
   }

   @Test
   void everyClipFileMatchesItsEnum() throws Exception {
      for (TheWorldClips.Clip clip : TheWorldClips.Clip.values()) {
         JsonObject json = clip(clip);
         assertEquals(clip.ticks, Math.round(json.get("animation_length").getAsDouble() * 20.0), clip.id + " length");
         // Bedrock format: true / "loop" loop; false / "hold_on_last_frame" do not.
         boolean loop = json.has("loop") && json.get("loop").isJsonPrimitive()
            && (json.get("loop").getAsJsonPrimitive().isBoolean() ? json.get("loop").getAsBoolean() : "loop".equals(json.get("loop").getAsString()));
         assertEquals(clip.loop, loop, clip.id + " loop");
      }
   }

   @Test
   void strikeClipsMatchServerTimings() {
      assertEquals(DioRules.COMBO_LENGTH[0], TheWorldClips.Clip.JAB.ticks);
      assertEquals(DioRules.COMBO_LENGTH[1], TheWorldClips.Clip.CROSS.ticks);
      assertEquals(DioRules.COMBO_LENGTH[2], TheWorldClips.Clip.HOOK.ticks);
      assertEquals(DioRules.FINISHER_LENGTH, TheWorldClips.Clip.FINISHER.ticks);
      assertEquals(DioRules.SUMMON_TICKS, TheWorldClips.Clip.SUMMON.ticks);
      assertEquals(DioRules.DISMISS_TICKS, TheWorldClips.Clip.DISMISS.ticks);
      assertEquals(DioRules.TIME_STOP_CAST, TheWorldClips.Clip.TIME_STOP.ticks);
      assertEquals(2 * DioRules.BARRAGE_HIT_INTERVAL, TheWorldClips.Clip.BARRAGE.ticks, "one left + one right per loop");
      assertEquals(DioRules.ROLLER_PULSE, TheWorldClips.Clip.ROLLER_BARRAGE.ticks);
   }

   private static void assertFastestAtContact(TheWorldClips.Clip clip, String arm, int contact) throws Exception {
      TreeMap<Double, Double> keys = armKeys(clip, arm);
      assertTrue(keys.containsKey((double)contact), clip.id + ": exact key on the contact tick " + contact);
      double peak = keys.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
      assertEquals(peak, keys.get((double)contact), 1.0E-9, clip.id + ": furthest reach on the contact tick");
      double best = 0.0, bestAt = -1.0, prevT = Double.NaN, prevV = 0.0;
      for (Map.Entry<Double, Double> e : keys.entrySet()) {
         if (!Double.isNaN(prevT)) {
            double speed = Math.abs(e.getValue() - prevV) / (e.getKey() - prevT);
            if (speed > best) {
               best = speed;
               bestAt = e.getKey();
            }
         }

         prevT = e.getKey();
         prevV = e.getValue();
      }

      assertEquals(contact, bestAt, 1.0E-9, clip.id + ": the fastest step ends on the contact");
   }

   @Test
   void strikesAreFastestAtContact() throws Exception {
      assertFastestAtContact(TheWorldClips.Clip.JAB, "arm_L", DioRules.COMBO_CONTACT[0]);
      assertFastestAtContact(TheWorldClips.Clip.CROSS, "arm_R", DioRules.COMBO_CONTACT[1]);
      assertFastestAtContact(TheWorldClips.Clip.HOOK, "arm_R", DioRules.COMBO_CONTACT[2]);
      assertFastestAtContact(TheWorldClips.Clip.FINISHER, "arm_R", DioRules.FINISHER_CONTACT);
      assertEquals(DioRules.COMBO_CONTACT[0], TheWorldClips.contactTick(TheWorldClips.Clip.JAB));
      assertEquals(DioRules.FINISHER_CONTACT, TheWorldClips.contactTick(TheWorldClips.Clip.FINISHER));
   }

   @Test
   void barrageLoopPunchesOnEveryServerHit() throws Exception {
      TreeMap<Double, Double> left = armKeys(TheWorldClips.Clip.BARRAGE, "arm_L");
      TreeMap<Double, Double> right = armKeys(TheWorldClips.Clip.BARRAGE, "arm_R");
      double maxL = left.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
      double maxR = right.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
      assertEquals(maxL, left.get(0.0), 1.0E-9, "left fist out at loop tick 0");
      assertEquals(maxR, right.get(2.0), 1.0E-9, "right fist out at loop tick 2");
      for (int t = DioRules.BARRAGE_WINDUP; t < DioRules.BARRAGE_MAX; t++) {
         TheWorldClips.Choice c = TheWorldClips.choose(HeroAction.MUDA_BARRAGE, t, DioRules.STAND_OUT, 0, 0, false, false, 0, true);
         assertEquals(TheWorldClips.Clip.BARRAGE, c.clip());
         boolean contact = c.ticks() == 0.0 || c.ticks() == 2.0;
         assertEquals(DioRules.barrageHitTick(t), contact, "barrage contact on hit tick " + t);
      }
   }

   @Test
   void rollerBarrageContactsOnServerPulses() {
      for (int t = DioRules.ROLLER_SLAM + 1; t <= DioRules.ROLLER_BARRAGE_END; t++) {
         TheWorldClips.Choice c = TheWorldClips.choose(HeroAction.ROAD_ROLLER, t, DioRules.STAND_NONE, 0, 0, false, false, 0, false);
         assertEquals(TheWorldClips.Clip.ROLLER_BARRAGE, c.clip());
         if (DioRules.rollerPulseTick(t)) {
            assertEquals(0.0, c.ticks(), "pulse " + t + " on the left contact");
         }
      }

      TheWorldClips.Choice last = TheWorldClips.choose(HeroAction.ROAD_ROLLER, DioRules.ROLLER_RESUME, DioRules.STAND_NONE, 0, 0, false, false, 0, false);
      assertEquals(TheWorldClips.Clip.FINISHER, last.clip());
      assertEquals(DioRules.FINISHER_CONTACT, last.ticks(), 1.0E-9, "the last blow lands on the resume tick");
   }

   @Test
   void choiceFollowsTheSnapshot() {
      assertEquals(TheWorldClips.Clip.SUMMON, TheWorldClips.choose(null, 0, DioRules.STAND_SUMMONING, 3, 0, false, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.DISMISS, TheWorldClips.choose(null, 0, DioRules.STAND_DISMISSING, 3, 0, false, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.JAB, TheWorldClips.choose(HeroAction.PRIMARY_ATTACK, 2, DioRules.STAND_OUT, 0, 0, false, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.CROSS, TheWorldClips.choose(HeroAction.PRIMARY_ATTACK, 2, DioRules.STAND_OUT, 0, 1, false, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.HOOK, TheWorldClips.choose(HeroAction.PRIMARY_ATTACK, 2, DioRules.STAND_OUT, 0, 2, false, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.FINISHER, TheWorldClips.choose(HeroAction.MUDA_BARRAGE, 2, DioRules.STAND_OUT, 0, 0, true, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.READY, TheWorldClips.choose(HeroAction.MUDA_BARRAGE, 2, DioRules.STAND_OUT, 0, 0, false, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.TIME_STOP, TheWorldClips.choose(HeroAction.TIME_STOP, 5, DioRules.STAND_OUT, 0, 0, false, false, 0, false).clip());
      assertEquals(TheWorldClips.Clip.GUARD, TheWorldClips.choose(null, 0, DioRules.STAND_OUT, 0, 0, false, true, 7, false).clip());
      assertEquals(TheWorldClips.Clip.IDLE, TheWorldClips.choose(null, 0, DioRules.STAND_OUT, 0, 0, false, false, 7, false).clip());
      assertEquals(TheWorldClips.Clip.READY, TheWorldClips.choose(null, 0, DioRules.STAND_OUT, 0, 0, false, false, 7, true).clip());
      assertEquals(2.0, TheWorldClips.Clip.IDLE.wrap(62.0), 1.0E-9);
      assertEquals(9.0, TheWorldClips.Clip.JAB.wrap(30.0), 1.0E-9);
      assertTrue(TheWorldClips.blendIn(TheWorldClips.Clip.JAB) < DioRules.COMBO_CONTACT[0], "blend done before the contact");
      assertTrue(TheWorldClips.blendIn(TheWorldClips.Clip.HOOK) < DioRules.COMBO_CONTACT[2]);
      assertTrue(TheWorldClips.blendIn(TheWorldClips.Clip.FINISHER) < DioRules.FINISHER_CONTACT);
   }
}

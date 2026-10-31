package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The client direction of THE FINAL PUNCH: layers, sound cues, camera, input, the jaw (pure). */
class FinalPunchCuesTest {
   private static final double END = FinalPunchRules.LENGTH + FinalPunchCues.TAIL;

   @Test
   void everyLayerStaysInRangeAndIsGoneAfterTheScene() {
      DoubleUnaryOperator[] layers = {FinalPunchCues::sparks, FinalPunchCues::fire, FinalPunchCues::rings, FinalPunchCues::arcs,
         FinalPunchCues::concentration, FinalPunchCues::sun, FinalPunchCues::compress, FinalPunchCues::trail, FinalPunchCues::impact,
         FinalPunchCues::afterglow, FinalPunchCues::smoke, FinalPunchCues::storm, FinalPunchCues::debris, FinalPunchCues::letterbox,
         t -> FinalPunchCues.grey(t), t -> FinalPunchCues.vignette(t), t -> FinalPunchCues.music(t), t -> FinalPunchCues.wind(t),
         t -> FinalPunchCues.hum(t), t -> FinalPunchCues.bodyWeight(t), t -> FinalPunchCues.aim(t)};
      for (int i = 0; i < layers.length; i++) {
         for (double t = 0; t <= END + 4; t += 0.25) {
            double v = layers[i].applyAsDouble(t);
            assertTrue(Double.isFinite(v) && v >= -1.0E-9 && v <= 1.0 + 1.0E-9, "layer " + i + " at " + t + " = " + v);
         }
         assertEquals(0.0, layers[i].applyAsDouble(END + 2), 1.0E-9, "layer " + i + " is gone after the tail");
      }
      assertEquals(0.0, FinalPunchCues.letterbox(0), 1.0E-9, "the bars slide in");
      assertEquals(1.0, FinalPunchCues.letterbox(200), 1.0E-9);
      assertTrue(FinalPunchCues.shake(FinalPunchRules.CONTACT + 1) > 0, "the hit shakes the camera");
      assertEquals(0.0, FinalPunchCues.shake(FinalPunchRules.SILENCE + 4), 1.0E-9, "the silence is still");
   }

   @Test
   void theFistGrowsSparksFireRingsArcsSun() {
      assertTrue(FinalPunchCues.sparks(FinalPunchRules.SPARKS + 5) > 0 && FinalPunchCues.fire(FinalPunchRules.SPARKS + 5) == 0);
      assertTrue(FinalPunchCues.fire(FinalPunchRules.FIRE + 20) > 0 && FinalPunchCues.rings(FinalPunchRules.FIRE + 20) == 0);
      assertTrue(FinalPunchCues.rings(FinalPunchRules.RINGS + 20) > 0 && FinalPunchCues.arcs(FinalPunchRules.RINGS + 20) == 0);
      assertTrue(FinalPunchCues.arcs(FinalPunchRules.ARCS + 20) > 0);
      assertTrue(FinalPunchCues.sun(FinalPunchRules.SUN + 20) > 0.5, "a small sun before the silence");
      assertTrue(FinalPunchCues.ringCount(FinalPunchRules.SUN) >= FinalPunchCues.ringCount(FinalPunchRules.RINGS + 1));
      assertEquals(0.0, FinalPunchCues.sun(FinalPunchRules.CONTACT + 1), 1.0E-9, "spent by the hit");
   }

   @Test
   void timeSlowsInTheSilenceAndStopsInTheHitStop() {
      assertEquals(1.0, FinalPunchCues.timeScale(100), 1.0E-9);
      assertTrue(FinalPunchCues.timeScale(FinalPunchRules.SILENCE + 4) < 0.2, "visual slow time");
      assertEquals(0.0, FinalPunchCues.timeScale(FinalPunchRules.CONTACT + 2), 1.0E-9, "hit-stop");
      assertEquals(1.0, FinalPunchCues.timeScale(FinalPunchRules.LAUNCH + 20), 1.0E-9);
      assertTrue(FinalPunchCues.music(FinalPunchRules.SILENCE - 30) > 0.3);
      assertEquals(0.0, FinalPunchCues.music(FinalPunchRules.SILENCE), 1.0E-9, "the music stops dead");
      assertEquals(0.0, FinalPunchCues.wind(FinalPunchRules.SILENCE + 2), 1.0E-9);
      assertTrue(FinalPunchCues.ducked(FinalPunchRules.SILENCE + 5), "the world goes quiet");
      assertFalse(FinalPunchCues.ducked(FinalPunchRules.CONTACT + 1), "the hit is heard at full volume");
      assertTrue(FinalPunchCues.grey(FinalPunchRules.SILENCE + 10) > FinalPunchCues.grey(FinalPunchRules.SILENCE - 40));
   }

   @Test
   void soundCuesAreSortedInsideTheSceneAndCoverEveryEvent() throws Exception {
      int last = -1;
      Set<String> used = new HashSet<>();
      for (FinalPunchCues.Cue cue : FinalPunchCues.cues()) {
         assertTrue(cue.tick() >= last, "sorted at " + cue.tick());
         assertTrue(cue.tick() >= 0 && cue.tick() < FinalPunchRules.LENGTH, "inside the scene: " + cue.tick());
         assertTrue(cue.volume() > 0 && cue.volume() <= 2.0F && cue.pitch() >= 0.5F && cue.pitch() <= 2.0F, cue.toString());
         assertFalse(cue.tick() > FinalPunchRules.SILENCE && cue.tick() < FinalPunchRules.CONTACT && !cue.sound().equals("whoosh"),
            "only the whoosh breaks the silence before the hit: " + cue);
         assertFalse(cue.sound().equals("impact") || cue.sound().equals("jaw") || cue.sound().equals("clang"),
            "the server plays the hit, the client must not double it: " + cue);
         last = cue.tick();
         used.add(cue.sound());
      }
      // the loops (FinalPunchAudio) and the server's contact sounds
      used.addAll(Set.of("music", "wind", "hum", "impact", "jaw", "clang"));
      Set<String> events = new TreeSet<>();
      Path overlay = Path.of("src/main/sound-overlays/reinhard_final_punch.json");
      for (String key : JsonParser.parseString(Files.readString(overlay)).getAsJsonObject().keySet()) {
         events.add(key.substring("reinhard.final_punch_".length()));
      }
      assertEquals(13, events.size(), events.toString());
      assertEquals(events, new TreeSet<>(used), "every one of the 13 sounds is used, nothing else");
      for (String e : events) {
         Path dir = Path.of("src/main/binassets/assets/viltrumitecore/sounds/reinhard/final_punch");
         assertTrue(Files.exists(dir.resolve(e + ".ogg.b64")) || Files.exists(dir.resolve(e + ".ogg.b64.part0")), "audio of " + e);
      }
   }

   private static FinalPunchShots.Frame frame(double t) {
      Vec3 feet = new Vec3(100.5, 64, -20.5);
      float yaw = 37.0F;
      Vec3 fist = FinalPunchFist.world(feet, yaw, t);
      Vec3 fw = Vec3.directionFromRotation(0, yaw);
      Vec3 right = new Vec3(-fw.z, 0, fw.x);
      Vec3 jaw = feet.add(fw.scale(0.95)).add(right.scale(0.13)).add(0, 1.406, 0);
      Vec3 target = jaw.add(fw.scale(Math.max(0, t - FinalPunchRules.LAUNCH) * 0.6)).subtract(0, 1.406, 0);
      return new FinalPunchShots.Frame(feet, yaw, fist, jaw, target);
   }

   @Test
   void theCameraOnlyJumpsOnItsTwoCuts() {
      FinalPunchShots.Shot before = FinalPunchShots.at(0, frame(0));
      for (double t = 0.25; t <= FinalPunchRules.LENGTH; t += 0.25) {
         FinalPunchShots.Shot shot = FinalPunchShots.at(t, frame(t));
         double jump = shot.pos().distanceTo(before.pos());
         boolean cut = false;
         for (int c : FinalPunchCues.CUTS) cut |= t > c - 0.25 && t <= c;
         if (!cut) assertTrue(jump < 0.45, "camera jump " + jump + " at " + t);
         else assertTrue(jump > 0.3, "a real cut at " + t);
         before = shot;
      }
   }

   @Test
   void theCameraNeverSitsInsideReinhardOrTheTarget() {
      for (double t = 0; t <= FinalPunchRules.LENGTH; t += 1) {
         FinalPunchShots.Frame f = frame(t);
         FinalPunchShots.Shot shot = FinalPunchShots.at(t, f);
         Vec3 p = shot.pos();
         double casterAxis = Math.hypot(p.x - f.feet().x, p.z - f.feet().z);
         boolean insideCaster = casterAxis < 0.4 && p.y > f.feet().y && p.y < f.feet().y + 2.0;
         Vec3 tf = f.target();
         double targetAxis = Math.hypot(p.x - tf.x, p.z - tf.z);
         boolean insideTarget = targetAxis < 0.4 && p.y > tf.y && p.y < tf.y + 2.0;
         assertFalse(insideCaster || insideTarget, "camera inside a body at " + t + ": " + p);
         assertTrue(shot.focus().distanceTo(p) > 0.2, "the camera looks at something at " + t);
      }
   }

   @Test
   void shiftAndTheSlotSixKeyMakeTheChord() {
      assertTrue(FinalPunchInput.chord(true, true));
      assertFalse(FinalPunchInput.chord(false, true), "N alone stays the normal slot-6 ability");
      assertFalse(FinalPunchInput.chord(true, false));
      assertFalse(FinalPunchInput.chord(false, false));
   }

   @Test
   void theJawFliesForwardUpAndTheSameWayOnEveryClient() {
      Vec3 dir = new Vec3(0.6, 0.1, 0.8);
      Vec3 a = FinalPunchJaw.launchVelocity(dir, 1234L);
      Vec3 b = FinalPunchJaw.launchVelocity(dir, 1234L);
      assertEquals(a, b, "deterministic per scene seed");
      assertNotEquals(a, FinalPunchJaw.launchVelocity(dir, 99L));
      assertTrue(a.y > 0.3, "upwards");
      assertTrue(a.x * dir.x + a.z * dir.z > 0.3, "along the punch");
      assertTrue(a.length() < 1.0, "bounded speed");
   }
}

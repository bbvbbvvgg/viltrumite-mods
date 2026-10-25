package dev.baranhan.viltrumitecore.client.reinhard.cut;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Review fixes for PR #42: one admitted beat per scene, render phase, bounded accents, hum headroom, pulse envelope. */
class WorldCutPulseSyncTest {
   private static WorldCutScene.Active scene() {
      WorldCutShape shape = new WorldCutShape(Vec3.ZERO, new Vec3(0, 0, 1), 1);
      return new WorldCutScene.Active(new WorldCutS2CPacket(UUID.randomUUID(), UUID.randomUUID(), shape,
         new int[200], 0, WorldCutS2CPacket.WORK));
   }

   /** Ticks at which the scene admits a beat, ticking every client tick from {@code first}. */
   private static List<Long> admitted(WorldCutScene.Active scene, long first, long last) {
      List<Long> beats = new ArrayList<>();
      for (long t = first; t <= last; t++) {
         if (scene.admitPulse(t)) {
            beats.add(t);
         }
      }
      return beats;
   }

   @Test void onTimeSceneAdmitsEveryBeatExactlyOnce() {
      WorldCutScene.Active scene = scene();
      assertEquals(List.of(96L, 146L, 196L), admitted(scene, 0, 230));
      WorldCutScene.Active other = scene();
      admitted(other, 0, 96);
      assertEquals(.5, other.pulsePhase(96.5), 1E-9);
      assertEquals(5.75, other.pulsePhase(101.75), 1E-9);
   }

   @Test void sceneCreatedMidPulseSkipsVoiceSwellShakeAndDustOfThatBeat() {
      // First WORK packet at elapsed 100: the beat began at 96, four ticks ago.
      WorldCutScene.Active scene = scene();
      for (long t = 100; t < 120; t++) {
         assertFalse(scene.admitPulse(t), "no late pulse voice at " + t);
         assertEquals(-1, scene.pulsePhase(t), 1E-9, "no swell/shake at " + t);
         assertEquals(-1, scene.pulsePhase(t + .5), 1E-9, "no dust ring at " + t + ".5");
         assertEquals(0F, WorldCutPulse.envelope(scene.pulsePhase(t)), 1E-9);
      }
      assertEquals(List.of(146L, 196L), admitted(scene, 120, 230), "the next beat plays complete");
   }

   @Test void slightlyLateStartCatchesUpAndClockJitterNeverReplaysTheVoice() {
      WorldCutScene.Active late = scene();
      assertTrue(late.admitPulse(96 + WorldCutPulse.CATCH_UP));
      assertEquals(WorldCutPulse.CATCH_UP, late.pulsePhase(96 + WorldCutPulse.CATCH_UP), 1E-9);
      WorldCutScene.Active tooLate = scene();
      assertFalse(tooLate.admitPulse(96 + WorldCutPulse.CATCH_UP + 1));
      WorldCutScene.Active jitter = scene();
      assertTrue(jitter.admitPulse(96));
      assertFalse(jitter.admitPulse(97));
      assertFalse(jitter.admitPulse(96), "a time sync that steps back does not start a second voice");
      assertFalse(jitter.admitPulse(97));
   }

   @Test void doneCapStillRejectsLaterBeatsForAdmission() {
      double done = 147.0;
      assertFalse(WorldCutPulse.admits(196, done, 146));
      assertFalse(WorldCutPulse.admits(146, 144.0, 96));
      assertEquals(-1, WorldCutPulse.admittedPhase(196.5, done, 146), 1E-9);
      assertEquals(2.5, WorldCutPulse.admittedPhase(148.5, done, 146), 1E-9);
   }

   @Test void renderPhaseKeepsPartialTicksWhileSoundUsesWholeTicks() {
      double inf = Double.POSITIVE_INFINITY;
      assertEquals(.25, WorldCutPulse.phase(96.25, inf), 1E-9);
      assertEquals(5.75, WorldCutPulse.phase(101.75, inf), 1E-9);
      assertEquals(5, WorldCutPulse.age(101.75, inf));
      double previous = -1;
      for (double t = 96; t < 96 + WorldCutPulse.LENGTH; t += .125) {
         double phase = WorldCutPulse.phase(t, inf);
         assertTrue(phase > previous, "render age advances every frame at " + t);
         previous = phase;
      }
      assertTrue(WorldCutPulse.envelope(5.5) < WorldCutPulse.envelope(5.0));
      assertTrue(WorldCutPulse.envelope(5.5) > WorldCutPulse.envelope(6.0));
      assertEquals(WorldCutPulse.strength(106, inf), WorldCutPulse.envelope(10), 1E-9);
      assertEquals(0F, WorldCutPulse.envelope(-1), 1E-9);
      assertEquals(0F, WorldCutPulse.envelope(WorldCutPulse.LENGTH), 1E-9);
   }

   @Test void oneShotAccentsDoNotReplayForLateViewers() {
      assertFalse(WorldCutScene.impactDue(79));
      for (int t = 80; t <= 80 + WorldCutPulse.CATCH_UP; t++) {
         assertTrue(WorldCutScene.impactDue(t));
      }
      assertFalse(WorldCutScene.impactDue(80 + WorldCutPulse.CATCH_UP + 1));
      assertFalse(WorldCutScene.impactDue(90), "first WORK packet after BLAST");
      assertFalse(WorldCutScene.impactDue(200));
      assertTrue(WorldCutScene.bladeDue(8));
      assertTrue(WorldCutScene.bladeDue(10), "the WORK packet at 10 still rings the blade");
      assertFalse(WorldCutScene.bladeDue(20));
      assertFalse(WorldCutScene.bladeDue(70));
   }

   @Test void humSwellKeepsItsFullRangeUnderTheEngineCeiling() {
      float rest = WorldCutAudio.humGain(1, 0), crest = WorldCutAudio.humGain(1, 1);
      assertEquals(WorldCutAudio.HUM_PEAK, crest, 1E-6, "the crest is the loudest accepted level");
      assertEquals(1 + WorldCutAudio.SWELL, crest / rest, 1E-5, "the configured +45 % swell survives");
      for (float fade = 0; fade <= 1.0001F; fade += .05F) {
         for (float pulse = 0; pulse <= 1.0001F; pulse += .05F) {
            assertTrue(WorldCutAudio.humGain(fade, pulse) <= WorldCutAudio.HUM_PEAK + 1E-6);
         }
      }
      assertEquals(WorldCutAudio.humGain(.5F, 1) / WorldCutAudio.humGain(.5F, 0), crest / rest, 1E-5,
         "a fading hum still swells by the same ratio");
      float volume = .001F, max = 0;
      for (int age = 1; age < 400; age++) {
         volume = WorldCutAudio.humStep(volume, crest, age);
         max = Math.max(max, volume);
      }
      assertTrue(max <= WorldCutAudio.HUM_PEAK + 1E-6, "wobble never asks for more than the ceiling");
   }

   @Test void pulseVoiceUsesOnlyTheBakedReleaseAndPlaysTheWholeAsset() throws Exception {
      for (int age = 0; age < WorldCutAudio.PULSE_TICKS; age++) {
         assertEquals(WorldCutAudio.pulseVolume(0), WorldCutAudio.pulseVolume(age), 1E-9, "no runtime decay at " + age);
      }
      assertEquals(0F, WorldCutAudio.pulseVolume(WorldCutAudio.PULSE_TICKS), 1E-9);
      double seconds = oggSeconds(Path.of("src/main/binassets/assets/viltrumitecore/sounds/reinhard/world_cut_pulse.ogg.b64"));
      assertEquals(1.35, seconds, .01);
      assertTrue(WorldCutAudio.PULSE_TICKS / 20.0 >= seconds, "the voice is not cut before the asset ends");
      assertTrue(WorldCutAudio.PULSE_TICKS / 20.0 < seconds + .05, "and stops within one tick after it");
   }

   /** Decoded length of a base64 Ogg Vorbis file: last page granule / identification-header rate. */
   private static double oggSeconds(Path b64) throws Exception {
      byte[] ogg = Base64.getMimeDecoder().decode(Files.readString(b64));
      ByteBuffer le = ByteBuffer.wrap(ogg).order(ByteOrder.LITTLE_ENDIAN);
      int id = indexOf(ogg, "\u0001vorbis".getBytes(StandardCharsets.ISO_8859_1), 0);
      assertTrue(id >= 0, "vorbis identification header");
      int rate = le.getInt(id + 7 + 4 + 1);
      int last = -1;
      for (int at = indexOf(ogg, "OggS".getBytes(StandardCharsets.ISO_8859_1), 0); at >= 0;
           at = indexOf(ogg, "OggS".getBytes(StandardCharsets.ISO_8859_1), at + 1)) {
         last = at;
      }
      assertTrue(last >= 0 && rate > 0);
      return le.getLong(last + 6) / (double)rate;
   }

   private static int indexOf(byte[] data, byte[] needle, int from) {
      outer:
      for (int i = from; i <= data.length - needle.length; i++) {
         for (int j = 0; j < needle.length; j++) {
            if (data[i + j] != needle[j]) {
               continue outer;
            }
         }
         return i;
      }
      return -1;
   }
}

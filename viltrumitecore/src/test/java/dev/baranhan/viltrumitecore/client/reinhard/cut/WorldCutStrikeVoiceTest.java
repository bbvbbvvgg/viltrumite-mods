package dev.baranhan.viltrumitecore.client.reinhard.cut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Item 12: the temporary Theresia strike plays once, exactly on the BLAST of the normal World Cut only.
 * Playtest item 8: the heavy explosion and the camera jolt start with it, from the same once-per-cast admission.
 */
class WorldCutStrikeVoiceTest {
   private static final Path ROOT = Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/cut");

   @Test
   void startsOnTheBlastTickAndNeverOnTheWindUp() {
      for (double t = 0; t < ReinhardRules.WORLD_CUT_BLAST; t += .5) {
         assertFalse(WorldCutScene.impactDue(t), "not during activation, blade or preparation: " + t);
      }
      assertTrue(WorldCutScene.impactDue(ReinhardRules.WORLD_CUT_BLAST), "the server BLAST tick");
      assertFalse(WorldCutScene.impactDue(ReinhardRules.WORLD_CUT_BLAST + WorldCutPulse.CATCH_UP + 1), "no late start");
   }

   @Test
   void replacesTheCompetingImpactInTheGroundCutOnly() throws Exception {
      String ground = Files.readString(ROOT.resolve("WorldCutScene.java"));
      String audio = Files.readString(ROOT.resolve("WorldCutAudio.java"));
      assertTrue(ground.contains("WorldCutAudio.strike("));
      assertFalse(ground.contains("WorldCutAudio.impact("), "no second impact voice on the BLAST");
      assertTrue(audio.contains("WORLD_CUT_STRIKE_VOICE.get()"));
      assertFalse(audio.contains("WORLD_CUT_IMPACT.get()"), "world_cut_impact is only the registered fallback");
      for (String diagonal : new String[]{"DiagonalCutScene.java", "CutFinishSound.java"}) {
         assertFalse(Files.readString(ROOT.resolve(diagonal)).contains("STRIKE_VOICE"), diagonal + " keeps its audio");
      }
      var id = new ResourceLocation("viltrumitecore", "reinhard.world_cut_strike_voice");
      assertTrue(WorldCutScene.owns(id), "never ducked by the silence hush");
      assertTrue(CutAtmosphere.owns(id), "never ducked by the atmosphere lease");
   }

   @Test
   void humAndPulseStepBackUnderTheLoudBodyAndRecover() {
      assertEquals(1F, WorldCutAudio.strikeDuck(-1), 0F);
      assertEquals(1F, WorldCutAudio.strikeDuck(Double.NaN), 0F);
      assertEquals(WorldCutAudio.STRIKE_DUCK, WorldCutAudio.strikeDuck(0), 0F);
      assertEquals(WorldCutAudio.STRIKE_DUCK, WorldCutAudio.strikeDuck(WorldCutAudio.STRIKE_BODY_TICKS), 0F);
      assertEquals(1F, WorldCutAudio.strikeDuck(WorldCutAudio.STRIKE_RECOVER_TICKS), 0F);
      float previous = 0;
      for (int t = 0; t <= 200; t++) {
         float duck = WorldCutAudio.strikeDuck(t);
         assertTrue(duck >= previous - 1E-6F, "recovers monotonically at " + t);
         assertTrue(duck >= WorldCutAudio.STRIKE_DUCK && duck <= 1F);
         previous = duck;
      }
      // The 8.76 s voice: loud for about 4.5 s, the hum is back before the voice has faded out.
      assertTrue(WorldCutAudio.STRIKE_RECOVER_TICKS / 20.0 < 8.76);
   }

   @Test
   void distanceSetsTheVolumeOfTheHeadRelativeStereoVoice() {
      assertEquals(1F, WorldCutAudio.strikeVolume(0), 0F);
      assertEquals(1F, WorldCutAudio.strikeVolume(WorldCutAudio.STRIKE_NEAR), 0F);
      assertEquals(WorldCutAudio.STRIKE_FAR_GAIN, WorldCutAudio.strikeVolume(ReinhardRules.WORLD_CUT_VIEW_RANGE), 1E-6F);
      assertEquals(WorldCutAudio.STRIKE_FAR_GAIN, WorldCutAudio.strikeVolume(5000), 1E-6F);
      float previous = 2;
      for (int d = 0; d <= 400; d += 8) {
         float v = WorldCutAudio.strikeVolume(d);
         assertTrue(v <= previous + 1E-6F, "quieter with distance at " + d);
         previous = v;
      }
   }

   private static WorldCutScene.Active scene() {
      WorldCutShape shape = new WorldCutShape(Vec3.ZERO, new Vec3(0, 0, 1), 1);
      return new WorldCutScene.Active(new WorldCutS2CPacket(UUID.randomUUID(), UUID.randomUUID(), shape,
         new int[200], 0, WorldCutS2CPacket.WORK));
   }

   private static List<Double> admitted(WorldCutScene.Active scene, double first, double last) {
      List<Double> at = new ArrayList<>();
      for (double t = first; t <= last; t += 1) {
         if (scene.admitImpact(t)) {
            at.add(t);
         }
      }
      return at;
   }

   @Test
   void impactIsAdmittedOncePerCastAndNeverReplayed() {
      assertEquals(List.of((double)ReinhardRules.WORLD_CUT_BLAST), admitted(scene(), 0, 400), "on time: the BLAST tick, once");
      WorldCutScene.Active late = scene();
      assertEquals(List.of(ReinhardRules.WORLD_CUT_BLAST + 2.0), admitted(late, ReinhardRules.WORLD_CUT_BLAST + 2, 400),
         "a scene first seen within CATCH_UP still hears it");
      assertEquals(List.of(), admitted(late, 0, 400), "and never a second time");
      assertEquals(List.of(), admitted(scene(), ReinhardRules.WORLD_CUT_BLAST + WorldCutPulse.CATCH_UP + 1, 400),
         "a viewer who joins later skips it");
   }

   @Test
   void repeatedPacketsOfOneCastShareOneSceneSoTheImpactCannotDouble() {
      WorldCutScene.clear();
      UUID cast = UUID.randomUUID(), caster = UUID.randomUUID();
      WorldCutShape shape = new WorldCutShape(Vec3.ZERO, new Vec3(0, 0, 1), 1);
      for (int phase : new int[]{WorldCutS2CPacket.START, WorldCutS2CPacket.BLAST, WorldCutS2CPacket.BLAST, WorldCutS2CPacket.WORK}) {
         WorldCutScene.onPacket(new WorldCutS2CPacket(cast, caster, shape, new int[200], 0, phase));
      }
      List<WorldCutScene.Active> scenes = new ArrayList<>();
      WorldCutScene.active().forEach(scenes::add);
      assertEquals(1, scenes.size(), "one scene per cast id");
      assertEquals(List.of((double)ReinhardRules.WORLD_CUT_BLAST), admitted(scenes.get(0), 0, 400));
      WorldCutScene.onPacket(new WorldCutS2CPacket(cast, caster, shape, new int[200], 0, WorldCutS2CPacket.BLAST));
      assertEquals(List.of(), admitted(scenes.get(0), 0, 400), "a late duplicate BLAST does not re-arm it");
      WorldCutScene.clear();
   }

   @Test
   void explosionStartsWithTheVoiceFromOneGuardAndIsNeverDucked() throws Exception {
      String ground = Files.readString(ROOT.resolve("WorldCutScene.java"));
      String audio = Files.readString(ROOT.resolve("WorldCutAudio.java"));
      assertTrue(ground.contains("scene.admitImpact(t) && WorldCutAudio.strike(scene.packet.castId(),nearest,distance,t,now)) WorldCutJolt.kick(nearest,now)"),
         "voice, explosion and jolt share one admission and one tick");
      String strike = audio.substring(audio.indexOf("static boolean strike("), audio.indexOf("private static void play("));
      assertTrue(strike.contains("WORLD_CUT_STRIKE_VOICE.get()") && strike.contains("WORLD_CUT_EXPLOSION.get()"));
      assertTrue(strike.contains("Attenuation.LINEAR, at.x, at.y, at.z, false"), "the mono explosion is positioned at the cut");
      assertTrue(strike.indexOf("return false") < strike.indexOf("sounds.play("), "the cooldown guards both voices");
      for (String diagonal : new String[]{"DiagonalCutScene.java", "CutFinishSound.java"}) {
         assertFalse(Files.readString(ROOT.resolve(diagonal)).contains("EXPLOSION"), diagonal + " keeps its audio");
      }
      var id = new ResourceLocation("viltrumitecore", "reinhard.world_cut_explosion");
      assertTrue(WorldCutScene.owns(id), "never ducked by the silence hush");
      assertTrue(CutAtmosphere.owns(id), "never ducked by the atmosphere lease");
      assertTrue(WorldCutAudio.EXPLOSION_GAIN > .5F && WorldCutAudio.EXPLOSION_GAIN < 1F, "a step under the voice");
   }

   @Test
   void joltIsOneFastDampedLurchOnTheBlast() {
      assertEquals(0F, WorldCutJolt.envelope(-1), 0F);
      assertEquals(0F, WorldCutJolt.envelope(Double.NaN), 0F);
      assertEquals(0F, WorldCutJolt.envelope(0), 1E-6F, "no snap on the first frame");
      assertEquals(0F, WorldCutJolt.envelope(WorldCutJolt.LENGTH), 0F);
      float peak = 0;
      double peakAt = -1;
      for (double age = 0; age < WorldCutJolt.LENGTH; age += .05) {
         float e = WorldCutJolt.envelope(age);
         if (e > peak) {
            peak = e;
            peakAt = age;
         }
         assertTrue(Math.abs(WorldCutJolt.pitch(age)) <= WorldCutJolt.PITCH + 1E-4F);
         assertTrue(Math.abs(WorldCutJolt.roll(age)) <= WorldCutJolt.ROLL + 1E-4F);
         assertTrue(Math.abs(WorldCutJolt.yaw(age)) <= WorldCutJolt.YAW + 1E-4F);
      }
      assertTrue(peakAt <= 2, "peaks within two ticks of the explosion: " + peakAt);
      assertTrue(peak > .7F);
      assertTrue(WorldCutJolt.envelope(10) < .25F * peak, "mostly gone after half a second");
      assertTrue(WorldCutJolt.envelope(WorldCutJolt.LENGTH - .05) < .01F, "fades into its hard end");
      int swings = 0;
      for (double age = .05; age < 12; age += .05) {
         if (Math.signum(WorldCutJolt.pitch(age)) != Math.signum(WorldCutJolt.pitch(age - .05))) {
            swings++;
         }
      }
      assertTrue(swings >= 3, "a swing, not a one-way tilt: " + swings);
   }

   @Test
   void joltFallsOffWithDistanceAndFollowsTheWorldCutShakeSetting() {
      assertEquals(1F, WorldCutJolt.strength(0, 1), 1E-6F);
      assertEquals(0F, WorldCutJolt.strength(ReinhardRules.WORLD_CUT_VIEW_RANGE, 1), 1E-6F);
      assertEquals(0F, WorldCutJolt.strength(40, 0), 0F, "0 disables it like the rumble");
      assertEquals(2F, WorldCutJolt.strength(0, 5), 1E-6F, "capped at twice");
      assertEquals(1F, WorldCutJolt.strength(0, Float.NaN), 1E-6F);
      float previous = 2;
      for (int d = 0; d <= 400; d += 10) {
         float s = WorldCutJolt.strength(d, 1);
         assertTrue(s <= previous + 1E-6F, "weaker with distance at " + d);
         previous = s;
      }
   }
}

package dev.baranhan.viltrumitecore.client.reinhard.cut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Item 12: the temporary Theresia strike plays once, exactly on the BLAST of the normal World Cut only. */
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
}

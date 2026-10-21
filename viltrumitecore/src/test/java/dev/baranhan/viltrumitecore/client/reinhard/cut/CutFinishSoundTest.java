package dev.baranhan.viltrumitecore.client.reinhard.cut;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CutFinishSoundTest {
   @Test void bothTypesShareOneFinishingTailAndClearOnWorldChange() {
      var gate=new CutFinishSound.Gate();Object world=new Object();
      assertTrue(gate.admit(world,100));assertFalse(gate.admit(world,100));assertFalse(gate.admit(world,111));
      assertTrue(gate.admit(world,112));assertTrue(gate.admit(new Object(),112));
      gate.clear();assertTrue(gate.admit(world,112));assertFalse(gate.admit(null,200));
   }
   @Test void bothDuckOwnersSpareTheNormalCutAccents() {
      var blade=new net.minecraft.resources.ResourceLocation("viltrumitecore","reinhard.world_cut_blade");
      var impact=new net.minecraft.resources.ResourceLocation("viltrumitecore","reinhard.world_cut_impact");
      var generic=new net.minecraft.resources.ResourceLocation("viltrumitecore","reinhard.sheath_strike");
      assertTrue(CutAtmosphere.owns(blade)); assertTrue(WorldCutScene.owns(blade));
      assertTrue(CutAtmosphere.owns(impact)); assertTrue(WorldCutScene.owns(impact));
      assertFalse(CutAtmosphere.owns(generic), "the generic SheathStrike stays duckable");
      assertFalse(WorldCutScene.owns(generic));
   }
   @Test void impactPulsesOnlyDuringOpenWorkingRupture() {
      for(int t=0;t<240;t++) {
         assertEquals(t>=96 && (t-96)%40==0,CutFinishSound.pulseDue(t,true));
         assertFalse(CutFinishSound.pulseDue(t,false),"No impact pulse after authoritative DONE");
      }
   }
   @Test void completionContainsOnlyTheChosenTailAndNoRepeatedRumble() throws Exception {
      var root=java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/cut");
      String sound=java.nio.file.Files.readString(root.resolve("CutFinishSound.java"));
      String finish=sound.substring(sound.indexOf("static void play(Vec3"));
      assertTrue(finish.contains("WORLD_CUT_TAIL.get()"));
      assertFalse(finish.contains("WORLD_CUT_BLAST.get()"));
      assertTrue(finish.contains("at,.9F,1,160"));
      String diagonal=java.nio.file.Files.readString(root.resolve("DiagonalCutScene.java"));
      String ground=java.nio.file.Files.readString(root.resolve("WorldCutScene.java"));
      assertFalse(diagonal.contains("CUT_RUMBLE.get()"));
      assertTrue(diagonal.contains("CutFinishSound.pulseDue("));
      assertTrue(diagonal.contains("WORLD_CUT_SILENCE.get()"), "approved diagonal cues stay untouched");
      assertFalse(ground.contains("CutFinishSound"), "ground cut uses its own hum, not the diagonal tail");
      assertFalse(ground.contains("WORLD_CUT_SILENCE"), "no reverse-fx cue inside the ground cut");
      assertTrue(ground.contains("WorldCutAudio.update("));
      assertTrue(ground.contains("WorldCutAudio.pulse("));
      String json=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/viltrumitecore/sounds.json"));
      var sounds=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
      assertEquals(320,sounds.getAsJsonObject("reinhard.world_cut_tail").getAsJsonArray("sounds").get(0).getAsJsonObject().get("attenuation_distance").getAsInt());
   }
}

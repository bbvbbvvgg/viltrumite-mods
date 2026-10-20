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
      for(String name:new String[]{"WorldCutScene.java","DiagonalCutScene.java"}) {
         String scene=java.nio.file.Files.readString(root.resolve(name));
         assertFalse(scene.contains("CUT_RUMBLE.get()"));
         assertTrue(scene.contains("CutFinishSound.pulseDue("));
      }
      String json=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/viltrumitecore/sounds.json"));
      var sounds=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
      assertEquals(320,sounds.getAsJsonObject("reinhard.world_cut_tail").getAsJsonArray("sounds").get(0).getAsJsonObject().get("attenuation_distance").getAsInt());
   }
}

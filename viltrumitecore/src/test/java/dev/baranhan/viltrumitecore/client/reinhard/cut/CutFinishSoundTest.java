package dev.baranhan.viltrumitecore.client.reinhard.cut;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CutFinishSoundTest {
   @Test void bothTypesShareOneFinishingPairAndClearOnWorldChange() {
      var gate=new CutFinishSound.Gate();Object world=new Object();
      assertTrue(gate.admit(world,100));assertFalse(gate.admit(world,100));assertFalse(gate.admit(world,111));
      assertTrue(gate.admit(world,112));assertTrue(gate.admit(new Object(),112));
      gate.clear();assertTrue(gate.admit(world,112));assertFalse(gate.admit(null,200));
   }
}

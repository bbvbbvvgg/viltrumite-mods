package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReinhardStrikeFluidityTest {
   private static final Path SOURCE=Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard");
   @Test void authoredArmDoesNotAddVanillaSwing() throws Exception {
      String renderer=Files.readString(SOURCE.resolve("ReinhardRenderer.java"));
      assertTrue(renderer.contains("ReinhardFirstPerson.swing("),"Custom arm must own swing, not stack vanilla attack motion");
      assertFalse(renderer.contains("arm.xRot += (float) Math.toRadians(action.pitch())"),"Do not rotate twice/in the wrong frame");
   }
   @Test void authoredSwingOwnsOnlyItsArmAndLeavesIdleUntouched() {
      var active=new dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot(
         dev.baranhan.viltrumitecore.hero.HeroId.REINHARD,
         dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.LIGHT_PUNCH.ordinal(),
         3,10,0,false,0,0,false,false,0,0,-1,new int[6],false);
      assertEquals(0,ReinhardFirstPerson.swing(active,net.minecraft.world.entity.HumanoidArm.RIGHT,.7F));
      assertEquals(.7F,ReinhardFirstPerson.swing(active,net.minecraft.world.entity.HumanoidArm.LEFT,.7F));
      assertEquals(.7F,ReinhardFirstPerson.swing(dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot.EMPTY,
         net.minecraft.world.entity.HumanoidArm.RIGHT,.7F));
   }
   @Test void firstPersonContactTargetsAndRecoveryRemainExact() {
      var ability=dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.LIGHT_PUNCH;
      var contact=ReinhardFirstPersonPose.of(ability,0,4,10);
      assertEquals(-.44F,contact.z(),1e-6);assertEquals(-12,contact.pitch(),1e-5);
      assertEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardFirstPersonPose.of(ability,0,10,10));
      // The old 2.6-tick anticipation peak has moved to 2: the delivery is 100ms.
      assertEquals(.1F,ReinhardFirstPersonPose.of(ability,0,2,10).z(),1e-6);
      assertTrue(ReinhardFirstPersonPose.of(ability,0,2.6,10).z()<.02);
   }
   @Test void combosContinueThroughContactRatherThanFreezeForOneTick() throws Exception {
      var clips=dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser.parse(JsonParser.parseString(
         Files.readString(Path.of("src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json"))).getAsJsonObject());
      for(String name:new String[]{"combo_1","combo_2","combo_3"}) {
         var arm=clips.get(name).boneAnimations().get("right_arm").rotX();
         double contact=name.equals("combo_3")?.3:.2,h=.0001;
         double a=arm.sample(contact),left=(a-arm.sample(contact-h))/h,right=(arm.sample(contact+h)-a)/h;
         assertTrue(Math.abs(left)>.01,name+" must carry motion through contact");
         assertEquals(left,right,.04,name+" contact velocity discontinuity");
         assertNotEquals(arm.sample(contact),arm.sample(contact+.05),name+" frozen contact hold");
      }
   }
   @Test void deliveryHasAtLeastTwoTicksBeforeExistingContact() throws Exception {
      var clips=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json")))
         .getAsJsonObject().getAsJsonObject("animations");
      for(String name:new String[]{"light_punch","combo_1","combo_2","combo_3"}) {
         var rotation=clips.getAsJsonObject(name).getAsJsonObject("bones").getAsJsonObject("right_arm").getAsJsonObject("rotation");
         double[] times=rotation.keySet().stream().mapToDouble(Double::parseDouble).sorted().toArray();
         assertTrue((times[2]-times[1])*20>=1.999,name+" delivery must not be a 60ms snap");
      }
   }
}

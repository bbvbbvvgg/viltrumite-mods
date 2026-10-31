package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.*;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.CloudStep;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ReinhardSecondPassCombatTest {
   @Test void repeatedAirStepsAreBoundedAndCanReverseHeading() {
      Vec3 v=Vec3.ZERO;
      for(int i=0;i<100;i++) {
         v=CloudStep.motion(v,new Vec3(0,.5,1),0,.62,false);
         assertTrue(v.horizontalDistance()<=1.35+1E-9);assertEquals(.62,v.y);assertTrue(v.z>0);
      }
      v=CloudStep.motion(v,new Vec3(0,0,-1),0,.62,false);assertTrue(v.z<0);
      Vec3 vertical=CloudStep.motion(Vec3.ZERO,new Vec3(0,1,0),90,.62,false);
      assertTrue(vertical.x< -1);assertEquals(0,vertical.z,1E-6);
   }
   @Test void launchHasFortyDegreeElevationInAttackDirection() {
      for(Vec3 heading:new Vec3[]{new Vec3(1,0,0),new Vec3(-1,1,1),new Vec3(0,-1,-1)}) {
         Vec3 v=SkyKick.launchVelocity(heading),flat=new Vec3(heading.x,0,heading.z).normalize();
         assertEquals(40,Math.toDegrees(Math.atan2(v.y,v.horizontalDistance())),1E-6);
         assertEquals(1,new Vec3(v.x,0,v.z).normalize().dot(flat),1E-6);
      }
   }
   @Test void retiredRSlotsAreEmptyAndLmbRoutesToInternalTimeline() {
      assertEquals(HeroAction.PRIMARY_ATTACK,ReinhardHero.INSTANCE.mouseAction(MouseButton.PRIMARY,null));
      assertFalse(ReinhardAbilities.active(ReinhardAbility.LIGHT_PUNCH));
      assertFalse(ReinhardAbilities.active(ReinhardAbility.FIST_COMBO));
      assertTrue(ReinhardAbilities.active(ReinhardAbility.REID));
      assertFalse(ReinhardHero.INSTANCE.allowsVanillaAttack(null));
   }
   @Test void comboOnlyQueuesOneNextHitAndResolvesOnAuthoredContacts() {
      var state=new ReinhardState();FistCombo.press(state);
      for(int i=0;i<20;i++)FistCombo.queue(state);
      assertTrue(state.comboQueued);assertEquals(0,state.comboStep);
      while(state.action.running())state.action.tick();
      FistCombo.tickChain(state);assertEquals(1,state.comboStep);assertFalse(state.comboQueued);
      assertEquals(4,ReinhardRules.LIGHT_PUNCH_HIT_TICK);assertEquals(10,ReinhardRules.LIGHT_PUNCH_TICKS);
      assertArrayEquals(new int[]{4,4,6},ReinhardRules.COMBO_HIT_TICKS);
   }
   @Test void oldLongCooldownsAreCappedOnLoadAndRetiredSlotsCleared() {
      var old=new ReinhardState();old.modeCooldown=999;
      java.util.Arrays.fill(old.cooldowns,1200);
      var nbt=new net.minecraft.nbt.CompoundTag();old.save(nbt);
      var loaded=new ReinhardState();loaded.load(nbt);
      assertEquals(ReinhardRules.MODE_COOLDOWN,loaded.modeCooldown);
      for(ReinhardAbility ability:ReinhardAbility.values())
         // a saved cooldown is capped at the ability's limit (THE FINAL PUNCH's limit is above 1200: kept)
         assertEquals(Math.min(1200,ReinhardRules.cooldownLimit(ability)),loaded.cooldowns[ability.ordinal()],ability.name());
      assertEquals(0,loaded.cooldowns[ReinhardAbility.LIGHT_PUNCH.ordinal()]);
      assertEquals(0,loaded.cooldowns[ReinhardAbility.FIST_COMBO.ordinal()]);
   }

}

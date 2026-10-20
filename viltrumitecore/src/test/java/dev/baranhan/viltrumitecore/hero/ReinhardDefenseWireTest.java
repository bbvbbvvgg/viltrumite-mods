package dev.baranhan.viltrumitecore.hero;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
class ReinhardDefenseWireTest {
 @Test void everyResponseAndHandSurviveAtomicSnapshotWithPalmArrowAndRunningAction() {
  for(var k:DefenseReaction.Kind.values())if(k!=DefenseReaction.Kind.NONE)for(boolean left:new boolean[]{false,true}) {
   var d=new DefenseReaction();d.contact(k,45,-30);d.aim(left);
   var s=new HeroPublicSnapshot(HeroId.REINHARD,17,8,20,0,false,0,0,false,false,0,0,-1,new int[6],true,-1,d.palm(),0,false,d.flags()|7,new int[18],19,1234);
   var q=HeroPublicSnapshot.decode(s.encode());assertEquals(s,q);assertEquals(k,DefenseReaction.kind(q.heroFlags()));assertEquals(left,DefenseReaction.leftHand(q.heroFlags()));assertEquals(7,q.heroFlags()&7);assertEquals(1234,q.resource2());assertEquals(d.palm(),q.actionTarget());assertEquals(8,q.actionElapsed());
  }
 }
 @Test void serverContactUsesTheSamePlayerFrameAndNeverMovesToTheBack() {
  for(boolean left:new boolean[]{false,true})for(int yaw=0;yaw<360;yaw+=15) {
   Vec3 origin=new Vec3(12,100,-20),local=DefenseContact.local(left,0,0),p=DefenseContact.world(origin,yaw,local);
   Vec3 forward=new Vec3(-Math.sin(Math.toRadians(yaw)),0,Math.cos(Math.toRadians(yaw)));
   assertTrue(p.subtract(origin).dot(forward)>.4);assertEquals(local.y*DefenseContact.SCALE,p.y-origin.y,1E-6);
  }
 }
}

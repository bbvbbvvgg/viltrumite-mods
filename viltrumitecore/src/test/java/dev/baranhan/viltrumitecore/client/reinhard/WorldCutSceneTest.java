package dev.baranhan.viltrumitecore.client.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.client.reinhard.cut.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WorldCutSceneTest {
   @Test void fovOnlyForOthers() {
      assertEquals(1,WorldCutFov.factor(0,true)); assertEquals(1.03,WorldCutFov.factor(0,false),1E-8);
   }
   @Test void fovFallsWithDistance() {
      assertEquals(1.03,WorldCutFov.factor(0),1E-8); assertEquals(1,WorldCutFov.factor(64),1E-8);
      assertEquals(1,WorldCutFov.factor(1000),1E-8);
      assertTrue(WorldCutFov.factor(16)>WorldCutFov.factor(32));
      assertTrue(WorldCutFov.factor(32)>WorldCutFov.factor(48));
   }
   @Test void frontReaches200At30() {
      assertEquals(0,WorldCutScene.front(80)); assertEquals(100,WorldCutScene.front(95));
      assertEquals(200,WorldCutScene.front(110)); assertEquals(200,WorldCutScene.front(200));
   }
   @Test void duckOnlyDuringSilence() {
      assertFalse(WorldCutScene.silence(19)); assertTrue(WorldCutScene.silence(20));
      assertTrue(WorldCutScene.silence(79.9)); assertFalse(WorldCutScene.silence(80));
   }
   @Test void elapsedUsesPacketClock() {
      assertEquals(90.5,WorldCutScene.elapsed(100,190,.5),1E-8);
   }
   @Test void clipAndBothHands() {
      assertEquals(List.of("world_cut","world_cut_from_left"),ReinhardClips.names(ReinhardAbility.WORLD_CUT)); // item 13: both shoulders
      assertNotEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardFirstPersonPose.worldCut(10,false));
      assertNotEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardFirstPersonPose.worldCut(10,true));
      assertNotEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardFirstPersonPose.worldCut(60,false));
      assertEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardFirstPersonPose.worldCut(100,false));
   }
   @Test void reorderedStartCannotUndoBlast() {
      UUID cast=UUID.randomUUID(), caster=UUID.randomUUID(); WorldCutShape shape=new WorldCutShape(Vec3.ZERO,new Vec3(0,0,1),1);
      WorldCutScene.clear();
      WorldCutScene.onPacket(new WorldCutS2CPacket(cast,caster,shape,new int[200],0,WorldCutS2CPacket.BLAST));
      WorldCutScene.onPacket(new WorldCutS2CPacket(cast,caster,shape,new int[200],0,WorldCutS2CPacket.START));
      assertTrue(WorldCutScene.active().iterator().next().blastReceived());
      WorldCutScene.clear(); assertFalse(WorldCutScene.active().iterator().hasNext());
   }
   @Test void timeoutDropsMissingBlast() {
      WorldCutScene.clear(); UUID cast=UUID.randomUUID();
      WorldCutScene.onPacket(new WorldCutS2CPacket(cast,UUID.randomUUID(),new WorldCutShape(Vec3.ZERO,new Vec3(0,0,1),1),new int[200],0,0));
      WorldCutScene.prune(500); assertFalse(WorldCutScene.active().iterator().hasNext());
   }
   @Test void casterSharesCinematicShake() {
      assertTrue(WorldCutScene.shakeCaster(true)); assertTrue(WorldCutScene.shakeCaster(false));
   }
   @Test void pulseClockRestsBetweenImpulses() {
      assertFalse(WorldCutPulse.due(95));
      assertTrue(WorldCutPulse.due(96));
      assertFalse(WorldCutPulse.due(97));
      assertFalse(WorldCutPulse.due(145));
      assertTrue(WorldCutPulse.due(146));
      assertEquals(-1,WorldCutPulse.age(95.9));
      assertEquals(0,WorldCutPulse.age(96.5));
      assertEquals(5,WorldCutPulse.age(101.5));
      assertEquals(1F,WorldCutPulse.strength(96),1E-6);
      assertTrue(WorldCutPulse.strength(106)<.4F);
      assertEquals(0F,WorldCutPulse.strength(120),1E-6);
      assertEquals(0F,WorldCutPulse.shake(124),1E-6);
      assertTrue(WorldCutPulse.shake(96)>2F);
   }
   @Test void bladeAndImpactAccentsStayOnTheirSideOfBlast() {
      assertFalse(WorldCutScene.bladeDue(7.9));
      assertTrue(WorldCutScene.bladeDue(8));
      assertTrue(WorldCutScene.bladeDue(11));
      // A late viewer must not hear the blade ring or BLAST long after the event.
      assertFalse(WorldCutScene.bladeDue(79.9));
      assertFalse(WorldCutScene.bladeDue(80));
      assertFalse(WorldCutScene.impactDue(79.9));
      assertTrue(WorldCutScene.impactDue(80));
      assertFalse(WorldCutScene.impactDue(200));
   }
   @Test void doneBetweenBeatsFinishesOnlyTheAcceptedImpulse() {
      double inf=Double.POSITIVE_INFINITY;
      assertEquals(0,WorldCutPulse.age(96.5,inf));
      assertEquals(4,WorldCutPulse.age(150.5,inf));
      // DONE at 148 accepts the 146 beat and lets it finish; the 196 beat never starts.
      double done=147.0;
      assertEquals(2,WorldCutPulse.age(148.5,done));
      assertEquals(10,WorldCutPulse.age(156.5,done));
      assertEquals(-1,WorldCutPulse.age(171.0,done));
      assertEquals(-1,WorldCutPulse.age(196.5,done));
      assertFalse(WorldCutPulse.due(196,done));
      assertEquals(0F,WorldCutPulse.strength(196.5,done),1E-6);
      assertEquals(0F,WorldCutPulse.shake(196.5,done),1E-6);
      assertTrue(WorldCutPulse.strength(148.5,done)>0F);
      // DONE at 145 rejects both the next beat and an already-finished 96 beat.
      assertEquals(-1,WorldCutPulse.age(145.5,144.0));
      assertEquals(-1,WorldCutPulse.age(146.5,144.0));
      assertEquals(-1,WorldCutPulse.age(196.5,144.0));
      assertFalse(WorldCutPulse.due(146,144.0));
   }
}

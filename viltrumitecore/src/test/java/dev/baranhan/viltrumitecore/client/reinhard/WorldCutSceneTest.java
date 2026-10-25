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
      assertEquals(List.of("world_cut"),ReinhardClips.names(ReinhardAbility.WORLD_CUT));
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
}

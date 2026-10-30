package dev.baranhan.viltrumitecore.hero.control;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WorldPauseMovementGuardTest {
   @Test void movingCarryAndGrindAreSuspendedForEitherPausedOrIsolatedBody(){for(int bits=0;bits<16;bits++){boolean blocked=WorldPause.blocksMovement((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,(bits&8)!=0);int grinds=0;Vec3 at=new Vec3(1,80,3);if(!blocked){at=new Vec3(9,60,30);grinds++;}assertEquals(bits==0?1:0,grinds);assertEquals(bits==0?new Vec3(9,60,30):new Vec3(1,80,3),at);}}
   @Test void foreignControlMotionIsSuspendedWithoutStealingOrRefreshingIt(){UUID pause=UUID.randomUUID(),foreign=UUID.randomUUID();for(ControlKind kind:List.of(ControlKind.PULL,ControlKind.FREEZE,ControlKind.STASIS)){var record=new ControlManager.ControlRecord(UUID.randomUUID(),UUID.randomUUID(),foreign,kind,new Vec3(1,80,3),false,true);record.expiresAt=1000;assertTrue(WorldPause.suspendForeignMovement(true,pause,record.effectId));assertFalse(WorldPause.suspendForeignMovement(true,pause,pause));assertFalse(WorldPause.suspendForeignMovement(false,pause,record.effectId));assertEquals(foreign,record.effectId);assertEquals(kind,record.kind);assertFalse(record.savedNoGravity);assertTrue(record.savedNoAi);assertEquals(1000,record.expiresAt);}}
   @Test void actualLegacyAndBothPullProducersGuardBeforeMovingOrGrinding() throws Exception{String prefix="src/main/java/dev/baranhan/viltrumitecore/";String legacy=Files.readString(Path.of(prefix+"mixin/PlayerEntityCoreMixin.java"));String grab=legacy.substring(legacy.indexOf("private void tickGrab("),legacy.indexOf("private void tickBlock("));assertTrue(grab.indexOf("WorldPause.blocksMovement")>=0);assertTrue(grab.indexOf("WorldPause.blocksMovement")<grab.indexOf("currentTarget.setPos"));assertTrue(grab.indexOf("WorldPause.blocksMovement")<grab.indexOf("serverWorld.destroyBlock"));for(String file:List.of("hero/reinhard/ability/GrabThrow.java","hero/ironman/hulkbuster/HulkbusterCombat.java","hero/regulus/KingdomOfOne.java")){var s=Files.readString(Path.of(prefix+file));assertTrue(s.contains("WorldPause.blocksMovement(player, target)"),file);}String cm=Files.readString(Path.of(prefix+"hero/control/ControlManager.java"));assertTrue(cm.contains("WorldPause.suspendForeignMovement(target, record.effectId)"));}
   @Test void pausedMobHeldByAForeignControlIsSuspendedAsAnOverlay(){
      UUID pause=UUID.randomUUID(),foreign=UUID.randomUUID(),mob=UUID.randomUUID();
      var own=new ControlManager.ControlRecord(mob,UUID.randomUUID(),pause,ControlKind.FREEZE,new Vec3(1,80,3),false,false);
      assertTrue(ControlManager.heldBy(own,pause));
      assertFalse(WorldPause.suspendsMob(true,ControlManager.heldBy(own,pause)),"the pause's own FREEZE already stops the mob");
      for(ControlKind kind:List.of(ControlKind.PULL,ControlKind.FREEZE,ControlKind.STASIS)){
         var record=new ControlManager.ControlRecord(mob,UUID.randomUUID(),foreign,kind,new Vec3(1,80,3),false,true);
         record.expiresAt=1000;
         assertTrue(WorldPause.suspendsMob(true,ControlManager.heldBy(record,pause)),kind+": a foreign control must not let the mob act");
         assertEquals(foreign,record.effectId);assertEquals(kind,record.kind);assertTrue(record.savedNoAi);assertEquals(1000,record.expiresAt);
      }
      var ownPull=new ControlManager.ControlRecord(mob,UUID.randomUUID(),pause,ControlKind.PULL,Vec3.ZERO,false,false);
      assertFalse(ControlManager.heldBy(ownPull,pause),"only a FREEZE holds the mob still");
      assertTrue(WorldPause.suspendsMob(true,ControlManager.heldBy(null,pause)),"no control at all (legacy grab refused the freeze)");
      assertFalse(WorldPause.suspendsMob(false,false),"mobs outside the pause tick normally");
   }
   @Test void overlayIsWiredIntoTheLivingTick() throws Exception{
      var source=Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/HeroEvents.java"));
      var tick=source.substring(source.indexOf("public static void onLivingTick("),source.indexOf("public static void onPlayerDeath("));
      assertTrue(tick.contains("WorldPause.suspendsMob(entity)"));
      assertTrue(tick.indexOf("setDeltaMovement")<tick.indexOf("setCanceled(true)"));
      assertTrue(tick.contains("isClientSide()"));
   }
}

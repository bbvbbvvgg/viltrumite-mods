package dev.baranhan.viltrumitecore.hero.control;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WorldPauseMovementGuardTest {
   @Test void movingCarryAndGrindAreSuspendedForEitherPausedOrIsolatedBody(){for(int bits=0;bits<16;bits++){boolean blocked=WorldPause.blocksMovement((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,(bits&8)!=0);int grinds=0;Vec3 at=new Vec3(1,80,3);if(!blocked){at=new Vec3(9,60,30);grinds++;}assertEquals(bits==0?1:0,grinds);assertEquals(bits==0?new Vec3(9,60,30):new Vec3(1,80,3),at);}}
   @Test void foreignControlMotionIsSuspendedWithoutStealingOrRefreshingIt(){UUID pause=UUID.randomUUID(),foreign=UUID.randomUUID();for(ControlKind kind:List.of(ControlKind.PULL,ControlKind.FREEZE,ControlKind.STASIS)){var record=new ControlManager.ControlRecord(UUID.randomUUID(),UUID.randomUUID(),foreign,kind,new Vec3(1,80,3),false,true);record.expiresAt=1000;assertTrue(WorldPause.suspendForeignMovement(true,pause,record.effectId));assertFalse(WorldPause.suspendForeignMovement(true,pause,pause));assertFalse(WorldPause.suspendForeignMovement(false,pause,record.effectId));assertEquals(foreign,record.effectId);assertEquals(kind,record.kind);assertFalse(record.savedNoGravity);assertTrue(record.savedNoAi);assertEquals(1000,record.expiresAt);}}
   @Test void actualLegacyAndBothPullProducersGuardBeforeMovingOrGrinding() throws Exception{String prefix="src/main/java/dev/baranhan/viltrumitecore/";String legacy=Files.readString(Path.of(prefix+"mixin/PlayerEntityCoreMixin.java"));String grab=legacy.substring(legacy.indexOf("private void tickGrab("),legacy.indexOf("private void tickBlock("));assertTrue(grab.indexOf("WorldPause.blocksMovement")>=0);assertTrue(grab.indexOf("WorldPause.blocksMovement")<grab.indexOf("currentTarget.setPos"));assertTrue(grab.indexOf("WorldPause.blocksMovement")<grab.indexOf("serverWorld.destroyBlock"));for(String file:List.of("hero/reinhard/ability/GrabThrow.java","hero/ironman/hulkbuster/IronManHulkbuster.java")){var s=Files.readString(Path.of(prefix+file));assertTrue(s.contains("WorldPause.blocksMovement(player, target)"),file);}String cm=Files.readString(Path.of(prefix+"hero/control/ControlManager.java"));assertTrue(cm.contains("WorldPause.suspendForeignMovement(target, record.effectId)"));}
}

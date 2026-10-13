package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.BodyIsolation;
import dev.baranhan.viltrumitecore.hero.reinhard.limbo.Limbo;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LimboTest {
   @Test void stripKeepsEnderChest(){var p=new Body();Limbo.send(p);assertTrue(p.main.isEmpty());assertTrue(p.armour.isEmpty());assertTrue(p.offhand.isEmpty());assertEquals(List.of("diamond"),p.ender);assertEquals(List.of("close","inventory","xp","void","start"),p.order);}
   @Test void xpZeroed(){var p=new Body();Limbo.send(p);assertEquals(0,p.level);assertEquals(0,p.points);assertEquals(0,p.total);}
   @Test void returnsAfter600OnlineTicks(){var p=new Body();Limbo.send(p);for(int i=0;i<599;i++)Limbo.tick(p);assertTrue(p.inVoid);assertEquals(1,Limbo.remaining(p.data));Limbo.tick(p);assertFalse(p.inVoid);assertEquals(1,p.returns);assertEquals(20,p.health);assertEquals(20,p.food);assertFalse(p.gravityOff);assertFalse(BodyIsolation.active(p.data));assertEquals(0,p.deaths);assertEquals(0,p.drops);assertEquals(0,p.deathStats);}
   @Test void offlineTimeDoesNotCount(){var p=new Body();Limbo.send(p);for(int i=0;i<200;i++)Limbo.tick(p);p.online=false;for(int i=0;i<5000;i++)Limbo.tick(p);assertEquals(400,Limbo.remaining(p.data));p.online=true;for(int i=0;i<400;i++)Limbo.tick(p);assertEquals(1,p.returns);}
   @Test void returnToSpawn(){var p=new Body();Limbo.send(p);Limbo.returnToSpawn(p);assertEquals(p.sharedSpawn,p.position);assertNotEquals(p.bed,p.position);}
   @Test void persistsAcrossLogout(){var p=new Body();Limbo.send(p);for(int i=0;i<275;i++)Limbo.tick(p);CompoundTag saved=p.data.copy();var reconnect=new Body();reconnect.data=saved;reconnect.main.clear();reconnect.armour.clear();reconnect.offhand.clear();Limbo.resume(reconnect);assertTrue(reconnect.inVoid);assertEquals(325,Limbo.remaining(saved));assertEquals(325,reconnect.startLeft);assertEquals(0,reconnect.strips);for(int i=0;i<325;i++)Limbo.tick(reconnect);assertEquals(1,reconnect.returns);}
   @Test void pendingStripCompletesExactlyOnce(){var p=new Body();Limbo.prepareTag(p.data);assertTrue(BodyIsolation.active(p.data));Limbo.resume(p);Limbo.resume(p);assertEquals(1,p.strips);assertTrue(p.main.isEmpty());}
   @Test void expiredLoginReturnsImmediately(){var p=new Body();Limbo.prepareTag(p.data);p.data.getCompound(Limbo.TAG).putInt("ticksLeft",0);Limbo.resume(p);assertFalse(p.inVoid);assertEquals(1,p.returns);}
   @Test void noPortalOut(){assertTrue(Limbo.blocksTravel(true,false));assertFalse(Limbo.blocksTravel(true,true));assertFalse(Limbo.blocksTravel(false,false));}
   @Test void immuneInLimbo(){var p=new Body();Limbo.send(p);assertTrue(BodyIsolation.active(p.data));p.food=1;Limbo.tick(p);assertEquals(20,p.food);assertTrue(p.gravityOff);}
   @Test void freshBodyCalledOnReturn(){var p=new Body();Limbo.send(p);Limbo.returnToSpawn(p);assertEquals(1,p.fresh);assertEquals(List.of("spawn","vitals","end","fresh"),p.order.subList(p.order.size()-4,p.order.size()));}
   @Test void countdownOnlyInsideVoid(){var p=new Body();Limbo.send(p);p.inVoid=false;Limbo.tick(p);assertEquals(600,Limbo.remaining(p.data));}
   @Test void safeSearchStaysInsideVanillaSpawnRadius(){Set<BlockPos> attempts=new HashSet<>();var found=Limbo.safeSpawn(10,20,3,123,(x,z)->{var pos=new BlockPos(x,64,z);assertTrue(Math.abs(x-10)<=3);assertTrue(Math.abs(z-20)<=3);assertTrue(attempts.add(pos));return null;});assertNull(found);assertEquals(49,attempts.size());assertEquals(new BlockPos(10,64,20),Limbo.safeSpawn(10,20,0,0,(x,z)->new BlockPos(x,64,z)));}
   @Test void liveTransferIsQueuedAndUsesSharedSpawn() throws Exception {String s=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/reinhard/limbo/Limbo.java"));assertTrue(s.contains("ServerEndTasks.enqueue(server,"));assertTrue(s.contains("getSharedSpawnPos()"));assertTrue(s.contains("PlayerRespawnLogicInvoker"));assertFalse(s.contains("getRespawnPosition()"));assertFalse(s.contains("getEnderChestInventory()"));assertFalse(s.contains(".die("));assertFalse(s.contains(".setBlock"));assertTrue(s.contains("HeroEvents.freshBody(player)"));}
   @Test void constants(){assertEquals(600,ReinhardRules.LIMBO_TICKS);assertEquals(new net.minecraft.world.phys.Vec3(.5,128,.5),ReinhardRules.LIMBO_POS);}
   static final class Body implements Limbo.Body {
      CompoundTag data=new CompoundTag();boolean online=true,inVoid,gravityOff;int level=45,points=50,total=3200,health=3,food=4,returns,fresh,strips,startLeft,deaths,drops,deathStats;
      final List<String> main=new ArrayList<>(List.of("sword")),armour=new ArrayList<>(List.of("helmet")),offhand=new ArrayList<>(List.of("shield")),ender=new ArrayList<>(List.of("diamond")),order=new ArrayList<>();
      final BlockPos sharedSpawn=new BlockPos(10,64,20),bed=new BlockPos(9000,70,9000);BlockPos position;
      public CompoundTag data(){return data;}public boolean online(){return online;}public boolean inVoid(){return inVoid;}
      public void closeContainer(){order.add("close");}
      public void clearInventory(){strips++;main.clear();armour.clear();offhand.clear();order.add("inventory");}
      public void clearExperience(){level=points=total=0;order.add("xp");}
      public void teleportToVoid(){inVoid=true;gravityOff=true;order.add("void");}
      public void holdVoid(){gravityOff=true;food=20;}
      public void startPacket(int left){startLeft=left;order.add("start");}
      public void requestReturn(){Limbo.returnToSpawn(this);}
      public void teleportToSharedSpawn(){inVoid=false;gravityOff=false;returns++;position=sharedSpawn;order.add("spawn");}
      public void restoreVitals(){health=food=20;order.add("vitals");}
      public void endPacket(){order.add("end");}
      public void freshBody(){fresh++;order.add("fresh");}
   }
}

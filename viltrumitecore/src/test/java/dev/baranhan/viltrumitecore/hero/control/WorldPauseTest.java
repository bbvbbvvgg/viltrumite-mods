package dev.baranhan.viltrumitecore.hero.control;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorldPauseTest {
   final UUID owner=UUID.randomUUID(), other=UUID.randomUUID(), joiner=UUID.randomUUID(), monster=UUID.randomUUID();
   final WorldPause.Target self=new WorldPause.Target(owner,"minecraft:overworld"), victim=new WorldPause.Target(other,"minecraft:the_nether");
   final Fake env=new Fake();
   WorldPause.Session start(boolean protect) { env.players.addAll(List.of(self,victim)); return new WorldPause.Session(owner,UUID.randomUUID(),150,protect,32); }
   @Test void enemiesOnlyNearPausedPlayersCasterIsNotACentre() {env.players.add(self);var mob=new WorldPause.Target(monster,self.level());env.mobs.add(mob);env.mobCentre=self;var s=new WorldPause.Session(owner,UUID.randomUUID(),150,true,32);s.sync(env);assertNull(env.records.get(mob));assertFalse(s.isPaused(owner));}
   @Test void hostileMobClassificationIncludesDragonGhastAndHoglin() {assertTrue(WorldPause.hostileType(net.minecraft.world.entity.boss.enderdragon.EnderDragon.class));assertTrue(WorldPause.hostileType(net.minecraft.world.entity.monster.Ghast.class));assertTrue(WorldPause.hostileType(net.minecraft.world.entity.monster.hoglin.Hoglin.class));assertFalse(WorldPause.hostileType(net.minecraft.world.entity.animal.Cow.class));}
   @Test void ownerNeverFrozen() { var s=start(true); s.sync(env); assertFalse(env.records.containsKey(self)); assertFalse(s.isPaused(owner)); assertTrue(s.isPaused(other)); }
   @Test void secondBeginRefused() { var c=new WorldPause.Coordinator<String>(); assertNotNull(c.begin("server",owner,150,true,32)); assertNull(c.begin("server",other,150,true,32)); c.end("server",env,WorldPause.EndReason.DONE); assertNotNull(c.begin("server",other,150,true,32)); }
   @Test void joinerFrozen() { var s=start(true); s.sync(env); var j=new WorldPause.Target(joiner,"minecraft:the_end"); env.players.add(j); s.sync(env); assertEquals(s.id,env.records.get(j)); assertTrue(s.isPaused(joiner)); }
   @Test void dimensionChangeReacquired() { var s=start(true); s.sync(env); env.players.remove(victim); var moved=new WorldPause.Target(other,"minecraft:the_end"); env.players.add(moved); s.sync(env); assertFalse(env.records.containsKey(victim)); assertEquals(s.id,env.records.get(moved)); }
   @Test void foreignControlNotOverridden() { var s=start(true); UUID foreign=UUID.randomUUID(); env.records.put(victim,foreign); s.sync(env); assertEquals(foreign,env.records.get(victim)); assertTrue(s.isPaused(other)); s.end(env,WorldPause.EndReason.DONE); assertEquals(foreign,env.records.get(victim)); }
   @ParameterizedTest @EnumSource(WorldPause.EndReason.class) void endsOnEveryPath(WorldPause.EndReason reason) { var s=start(true); s.sync(env); env.players.remove(victim); var moved=new WorldPause.Target(other,"minecraft:the_end"); env.players.add(moved); s.sync(env); s.end(env,reason); assertTrue(env.records.isEmpty()); assertTrue(s.ended); assertFalse(s.isPaused(other)); assertEquals(reason==WorldPause.EndReason.DONE?ReleaseReason.EXPIRED:ReleaseReason.INTERRUPTED,env.reason); }
   @Test void controlsSelfExpire() { var s=start(true); env.now=100; s.sync(env); assertEquals(105,env.expiry.get(victim)); env.now=105; env.expire(); assertTrue(env.records.isEmpty()); }
   @Test void mobsOnlyWhenProtected() { var mob=new WorldPause.Target(monster,"minecraft:the_nether"); env.mobs.add(mob); var s=start(false); s.sync(env); assertFalse(env.records.containsKey(mob)); s.end(env,WorldPause.EndReason.DONE); s=start(true); s.sync(env); assertEquals(s.id,env.records.get(mob)); }
   @Test void watchdogHasAHardLimit() { var s=start(true); for(int i=0;i<189;i++) assertFalse(s.tick(env)); assertTrue(s.tick(env)); assertTrue(s.ended); assertTrue(env.records.isEmpty()); }
   @Test void expiredClientWatchdogDoesNotHideForeignFreeze() { UUID pause=UUID.randomUUID(); assertTrue(WorldPause.hidesAnchor(true,pause,pause)); assertFalse(WorldPause.hidesAnchor(false,pause,pause)); assertFalse(WorldPause.hidesAnchor(true,pause,UUID.randomUUID())); }
   static final class Fake implements WorldPause.Backend {
      final List<WorldPause.Target> players=new ArrayList<>(),mobs=new ArrayList<>();
      final Map<WorldPause.Target,UUID> records=new HashMap<>(); final Map<WorldPause.Target,Long> expiry=new HashMap<>();
      long now; ReleaseReason reason; WorldPause.Target mobCentre;
      public List<WorldPause.Target> players() {return List.copyOf(players);}
      public List<WorldPause.Target> hostiles(List<WorldPause.Target> players,int radius) {assertEquals(32,radius); return mobCentre==null || players.contains(mobCentre) ? List.copyOf(mobs):List.of();}
      public long now(WorldPause.Target t) {return now;}
      public void freeze(WorldPause.Target t,UUID owner,UUID id,long until) {if(!records.containsKey(t)||records.get(t).equals(id)){records.put(t,id); expiry.put(t,until);}}
      public void release(WorldPause.Target t,UUID id,ReleaseReason why) {reason=why; if(id.equals(records.get(t))){records.remove(t);expiry.remove(t);}}
      void expire() {new HashSet<>(records.keySet()).stream().filter(t->expiry.containsKey(t)&&expiry.get(t)<=now).forEach(t->{records.remove(t);expiry.remove(t);});}
   }
}

package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutManager;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid;
import java.util.*;
import net.minecraftforge.event.TickEvent;
import org.junit.jupiter.api.Test;

class WorldCutTest {
   static final class World implements WorldCut.Environment {
      int blasts, strikes, front; boolean casterPresent = true;
      final UUID target = UUID.randomUUID();
      public void blast() { blasts++; }
      public int carveFront() { return front; }
      public Collection<UUID> targets(int through) { return through > 0 ? List.of(target) : List.of(); }
      public void strike(UUID id) { strikes++; }
   }
   @Test void timelineTicks() {
      World w = new World(); WorldCut cut = WorldCut.timed(100, w);
      cut.advance(119); assertEquals(0, w.blasts);
      cut.advance(179); assertEquals(0, w.blasts);
      assertFalse(cut.advance(180)); assertEquals(1, w.blasts);
      cut.advance(180); assertEquals(1, w.blasts);
      assertTrue(cut.advance(210)); assertEquals(1, w.blasts);
   }
   @Test void onlyWhenDrawn() {
      for (Reid.Phase p : Reid.Phase.values())
         assertEquals(p == Reid.Phase.DRAWN, Reid.allows(ReinhardAbility.WORLD_CUT, p));
      assertInstanceOf(WorldCut.class, ReinhardActions.handler(ReinhardAbility.WORLD_CUT));
   }
   @Test void cooldown900() {
      ReinhardState state = new ReinhardState(); WorldCut.start(state);
      assertEquals(900, state.cooldowns[ReinhardAbility.WORLD_CUT.ordinal()]);
      assertEquals(80, state.action.length()); assertTrue(state.busy());
      for (int i=0;i<80;i++) state.action.tick();
      assertFalse(state.busy());
   }
   @Test void hitsFollowFront() {
      assertEquals(0, WorldCut.visualFront(79)); assertEquals(0, WorldCut.visualFront(80));
      assertEquals(100, WorldCut.visualFront(95)); assertEquals(200, WorldCut.visualFront(110));
      assertEquals(200, WorldCut.visualFront(1000));
      World w = new World(); w.front=20;
      WorldCut.timed(0,w).advance(80); assertEquals(1,w.strikes);
   }
   @Test void everyEntityHitOnce() {
      World w = new World(); WorldCut cut = WorldCut.timed(0,w);
      for(int t=0;t<=110;t++) cut.advance(t);
      assertEquals(1,w.strikes);
   }
   @Test void strikeVerdicts() {
      assertEquals(Annihilation.Outcome.KILLED, Annihilation.decide(DivineTargets.Verdict.MORTAL,false,true));
      assertEquals(Annihilation.Outcome.IMMUNE_DIVINE, Annihilation.decide(DivineTargets.Verdict.DIVINE,false,true));
      assertEquals(Annihilation.Outcome.BLOCKED_ABSOLUTE, Annihilation.decide(DivineTargets.Verdict.ABSOLUTE,false,true));
   }
   @Test void resolvesWithoutCaster() {
      World w = new World(); WorldCutManager manager = new WorldCutManager();
      manager.submit(WorldCut.timed(0,w)); w.casterPresent=false;
      for(int t=1;t<=110;t++) manager.advance(t);
      assertEquals(1,w.blasts); assertEquals(1,w.strikes); assertEquals(0,manager.size());
   }
   @Test void managerTickedOnEndPhaseOnly() {
      assertFalse(LevelTickPhases.systemsFor(TickEvent.Phase.START).contains(LevelTickPhases.System.WORLD_CUT));
      assertTrue(LevelTickPhases.systemsFor(TickEvent.Phase.END).contains(LevelTickPhases.System.WORLD_CUT));
   }
   @Test void twoCutsIndependent() {
      World a=new World(), b=new World(); WorldCutManager manager=new WorldCutManager();
      manager.submit(WorldCut.timed(0,a)); manager.submit(WorldCut.timed(30,b));
      manager.advance(110); assertEquals(1,manager.size()); assertEquals(1,a.strikes); assertEquals(0,b.strikes);
      manager.advance(140); assertEquals(0,manager.size()); assertEquals(1,b.strikes);
   }
   @Test void catchesUpWithoutRepeatingBlast() {
      World w=new World(); WorldCut cut=WorldCut.timed(0,w);
      assertTrue(cut.advance(500)); assertEquals(1,w.blasts); assertEquals(1,w.strikes);
      cut.advance(501); assertEquals(1,w.blasts); assertEquals(1,w.strikes);
   }
   @Test void interruptedPoseKeepsBusyUntilBlast() {
      ReinhardState state=new ReinhardState(); World w=new World();
      WorldCut.start(state); state.worldCut=WorldCut.timed(0,w); state.action.cancel();
      assertTrue(state.busy()); state.worldCut.advance(79); assertTrue(state.busy());
      state.worldCut.advance(80); assertFalse(state.busy());
   }
   @Test void consumedSliceCountIsNotIncrementedAgain() {
      assertEquals(0,WorldCut.carveFront(null));
      var carve=WorldCutCarveTest.job(100,new WorldCutCarveTest.Probe());
      assertEquals(0,WorldCut.carveFront(carve)); carve.step(null,1);
      assertEquals(1,WorldCut.carveFront(carve));
   }
}

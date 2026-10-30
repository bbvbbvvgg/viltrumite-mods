package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.DivineTargets.Verdict;
import dev.baranhan.viltrumitecore.hero.QuietDragonCompletion;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldErasure;
import java.util.*;
import java.util.stream.Stream;
import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.ERASE_REMOVE_TICK;
import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.ERASE_LENGTH;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class WorldErasureTest {
   static Stream<WorldErasure.Gate> refusals() {return Stream.of(
      new WorldErasure.Gate(false,false,false,false,false,true), new WorldErasure.Gate(true,true,false,false,false,true),
      new WorldErasure.Gate(true,false,true,false,false,true),new WorldErasure.Gate(true,false,false,true,false,true),
      new WorldErasure.Gate(true,false,false,false,true,true),new WorldErasure.Gate(true,false,false,false,false,false),
      new WorldErasure.Gate(true,false,false,false,false,true,true));}
   static WorldErasure.Gate good(){return new WorldErasure.Gate(true,false,false,false,false,true);}
   @ParameterizedTest @MethodSource("refusals") void refusalsSpendNoCooldown(WorldErasure.Gate gate){var state=new ReinhardState(); int[] begin={0}; assertFalse(WorldErasure.tryStart(state,gate,()->{begin[0]++;return true;})); assertEquals(0,begin[0]); assertEquals(0,state.cooldowns[ReinhardAbility.WORLD_ERASURE.ordinal()]);assertFalse(state.action.running());}
   @Test void failedBeginSpendsNothing(){var s=new ReinhardState(); assertFalse(WorldErasure.tryStart(s,good(),()->false));assertEquals(0,s.cooldowns[ReinhardAbility.WORLD_ERASURE.ordinal()]);}
   @Test void secondCastRefusedWhileActive(){var a=new ReinhardState();assertTrue(WorldErasure.tryStart(a,good(),()->true));var b=new ReinhardState();assertFalse(WorldErasure.tryStart(b,new WorldErasure.Gate(true,true,false,false,false,true),()->fail()));assertEquals(4,a.cooldowns[13]);assertEquals(0,b.cooldowns[13]);}
   @Test void removeOnTheRemoveTick(){var e=new Fake();var cast=WorldErasure.timed(Verdict.MORTAL,e);cast.advance(ERASE_REMOVE_TICK-1);assertEquals(0,e.discards);cast.advance(ERASE_REMOVE_TICK);assertEquals(1,e.discards);cast.advance(ERASE_REMOVE_TICK+1);cast.advance(ERASE_REMOVE_TICK);assertEquals(1,e.discards);assertFalse(cast.advance(ERASE_LENGTH-1));assertTrue(cast.advance(ERASE_LENGTH));assertEquals(WorldPause.EndReason.DONE,e.end);}
   @Test void mobDiscardedNoLoot(){var e=new Fake();WorldErasure.timed(Verdict.MORTAL,e).advance(ERASE_REMOVE_TICK);assertEquals("DISCARDED",e.removal);assertEquals(0,e.die);assertEquals(0,e.loot);assertEquals(0,e.xp);}
   @Test void dragonMarksFightKilled(){var e=new Fake();e.kind=WorldErasure.TargetKind.DRAGON;WorldErasure.timed(Verdict.MORTAL,e).advance(ERASE_REMOVE_TICK);assertEquals(List.of("fight","discard"),e.order);assertTrue(e.dragonKilled);assertEquals(0,e.blocks);assertEquals(0,e.xp);}
   @Test void partEntityResolvesParent(){Map<String,String> parts=Map.of("wing","dragon","tip","wing");assertEquals("dragon",WorldErasure.root("tip",parts::get));assertEquals("dragon",WorldErasure.root("dragon",parts::get));}
   @ParameterizedTest @EnumSource(Verdict.class) void verdicts(Verdict verdict){var state=new ReinhardState();assertTrue(WorldErasure.tryStart(state,good(),()->true));var e=new Fake();var c=WorldErasure.timed(verdict,e);c.advance(ERASE_REMOVE_TICK);assertEquals(verdict==Verdict.MORTAL,c.erase());assertEquals(verdict==Verdict.MORTAL?1:0,e.discards);assertEquals(4,state.cooldowns[13]);}
   @Test void verdictFrozenAtCast(){var stays=new Fake();stays.absolute=true;var c=WorldErasure.timed(Verdict.of(false,stays.absolute),stays);stays.absolute=false;c.advance(ERASE_REMOVE_TICK);assertEquals(0,stays.discards);var erased=new Fake();var m=WorldErasure.timed(Verdict.of(false,false),erased);erased.absolute=true;m.advance(ERASE_REMOVE_TICK);assertEquals(1,erased.discards);}
   @ParameterizedTest @EnumSource(value=WorldPause.EndReason.class,names={"OWNER_LEFT","SERVER_STOP","RELOAD","WATCHDOG"}) void casterLeavesMidway(WorldPause.EndReason reason){var e=new Fake();var c=WorldErasure.timed(Verdict.MORTAL,e);c.advance(60);c.interrupt(reason);c.advance(ERASE_REMOVE_TICK);assertEquals(0,e.discards);assertTrue(c.ended());assertEquals(reason,e.end);}
   @ParameterizedTest @ValueSource(ints={ERASE_REMOVE_TICK-1,ERASE_REMOVE_TICK,ERASE_LENGTH-1,ERASE_LENGTH})
   void pureInterruptionBoundariesDoNotUndoOrRepeatRemoval(int age) {
      for (WorldPause.EndReason reason : List.of(WorldPause.EndReason.OWNER_LEFT, WorldPause.EndReason.SERVER_STOP, WorldPause.EndReason.RELOAD, WorldPause.EndReason.WATCHDOG)) {
         for (WorldErasure.TargetKind kind : List.of(WorldErasure.TargetKind.MOB, WorldErasure.TargetKind.PLAYER)) {
            var e = new Fake(); e.kind = kind;
            var c = WorldErasure.timed(Verdict.MORTAL, e);
            c.advance(age); c.interrupt(reason); c.advance(ERASE_REMOVE_TICK); c.advance(ERASE_LENGTH);
            assertTrue(c.ended());
            assertEquals(age >= ERASE_LENGTH ? WorldPause.EndReason.DONE : reason, e.end);
            assertEquals(kind == WorldErasure.TargetKind.MOB && age >= ERASE_REMOVE_TICK ? 1 : 0, e.discards);
            List<String> order = age < ERASE_REMOVE_TICK ? List.of()
               : kind == WorldErasure.TargetKind.PLAYER ? List.of("cleanup", "limbo") : List.of("discard");
            assertEquals(order, e.order);
         }
      }
   }
   @Test void targetEdgeCases(){var e=new Fake();var c=WorldErasure.timed(Verdict.MORTAL,e);e.present=false;c.advance(ERASE_REMOVE_TICK);e.present=true;c.advance(ERASE_REMOVE_TICK+10);assertEquals(0,e.discards);assertTrue(c.advance(ERASE_LENGTH));}
   @Test void playerCleanupBeforeTransfer(){var e=new Fake();e.kind=WorldErasure.TargetKind.PLAYER;WorldErasure.timed(Verdict.MORTAL,e).advance(ERASE_REMOVE_TICK);assertEquals(List.of("cleanup","limbo"),e.order);assertEquals(0,e.discards);}
   @Test void ownCastStaysBusyUntilPauseEnd(){var s=new ReinhardState();var e=new Fake();WorldErasure.tryStart(s,good(),()->true);s.worldErasure=WorldErasure.timed(Verdict.MORTAL,e);s.action.cancel();assertTrue(s.busy());s.worldErasure.advance(ERASE_LENGTH-1);assertTrue(s.busy());s.worldErasure.advance(ERASE_LENGTH);assertFalse(s.busy());}
   @Test void quietDragonCompletionHasNoRewardOrWorldMutation(){var f=new QuietDragonCompletion.State(){public void hideBossbar(){hidden[0]=true;}public void markDefeated(){defeated[0]=true;}};QuietDragonCompletion.finish(f);assertTrue(hidden[0]);assertTrue(defeated[0]);}
   final boolean[] hidden={false},defeated={false};
   @Test void constants(){assertEquals(64,ReinhardRules.ERASE_RANGE);assertEquals(205,ReinhardRules.ERASE_LENGTH);assertEquals(150,ReinhardRules.ERASE_REMOVE_TICK);assertTrue(ReinhardRules.ERASE_DISSOLVE_START<ReinhardRules.ERASE_REMOVE_TICK && ReinhardRules.ERASE_REMOVE_TICK<ReinhardRules.ERASE_REBUILD_END);assertEquals(4,ReinhardRules.ERASE_COOLDOWN);assertTrue(ReinhardRules.ERASE_PROTECT_FROZEN);assertEquals(32,ReinhardRules.ERASE_MOB_FREEZE_RADIUS);}
   @Test void liveRemovalHasNoDieOrBlockCalls() throws Exception {String s=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/reinhard/ability/WorldErasure.java"));assertFalse(s.contains(".setBlock"));assertFalse(s.contains(".die("));assertFalse(s.contains(".hurt("));assertTrue(s.contains("target.discard()"));assertTrue(s.contains("QuietDragonCompletion.markKilled"));assertTrue(s.contains("ProjectileUtil.getEntityHitResult"));}
   static final class Fake implements WorldErasure.Environment {
      WorldErasure.TargetKind kind=WorldErasure.TargetKind.MOB;boolean present=true,absolute,dragonKilled;
      int discards,die,loot,xp,blocks;String removal;WorldPause.EndReason end;final List<String> order=new ArrayList<>();
      public boolean targetPresent(){return present;}public WorldErasure.TargetKind targetKind(){return kind;}
      public void markDragonKilled(){dragonKilled=true;order.add("fight");}
      public void discard(){discards++;removal="DISCARDED";order.add("discard");}
      public void erasePlayer(){order.add("cleanup");order.add("limbo");}
      public void end(WorldPause.EndReason reason){end=reason;}
   }
   /** Polish: the scene starts on the press (world stopped at once); the line is spoken inside it, cracks start after it. */
   @Test void cinematicStartsOnThePress(){assertEquals(0,WorldErasure.cinematicStart());assertEquals(0,WorldErasure.cinematicAge(0));assertEquals(ERASE_LENGTH,WorldErasure.cinematicAge(ERASE_LENGTH));assertEquals(ReinhardRules.ERASE_SPEECH_TICKS,ReinhardRules.ERASE_CRACK_START);}
   @Test void heldPlayersAreNeverKickedForFloating() throws Exception {
      assertTrue(WorldPause.holdsAirborne(true, false), "frozen mid-air during the pause");
      assertTrue(WorldPause.holdsAirborne(false, true), "an erased player hangs in the Limbo void");
      assertFalse(WorldPause.holdsAirborne(false, false));
      String mixin = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/mixin/AntiCheatBypassMixin.java"));
      assertTrue(mixin.contains("WorldPause.holdsAirborne") && mixin.contains("Limbo.dimension()"), "the floating counter reset uses the hold predicate");
   }
}

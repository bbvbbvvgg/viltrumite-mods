package dev.baranhan.viltrumitecore.hero.control;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Actual Session against adversarial movement-producing refusals, not an always-frozen fake. */
class WorldPauseOverlayTest {
   enum Refusal { FOREIGN_PULL, FOREIGN_FREEZE, FOREIGN_STASIS, POLICY, LEGACY_GRAB }
   final UUID owner=UUID.randomUUID(),victimId=UUID.randomUUID(),foreign=UUID.randomUUID();
   final WorldPause.Target victim=new WorldPause.Target(victimId,"minecraft:overworld");
   final Vec3 original=new Vec3(1,80,3);
   Fixture fixture(Refusal refusal) {return new Fixture(refusal);}
   WorldPause.Session start(Fixture f) {var s=new WorldPause.Session(owner,UUID.randomUUID(),150,true,32);s.sync(f);return s;}
   @ParameterizedTest @EnumSource(Refusal.class) void refusedPlayersHaveAnIndependentFixedPosition(Refusal refusal) {
      var f=fixture(refusal);var s=start(f);var record=f.record;
      for(int tick=0;tick<8;tick++) {f.position=new Vec3(20+tick,60-tick,30);f.velocity=new Vec3(2,-1,3);s.tick(f);assertEquals(original,f.position,"foreign carry/pull or gravity must not move a paused player");assertEquals(Vec3.ZERO,f.velocity);}
      assertSame(record,f.record);assertFalse(f.gravity);assertTrue(f.noAi);assertEquals(1000,f.foreignExpiry);assertTrue(s.isPaused(victimId));
   }
   @Test void anchorIsNotRecapturedFromDriftingPosition() {var f=fixture(Refusal.POLICY);var s=start(f);f.position=new Vec3(100,4,5);s.sync(f);assertEquals(original,f.position);f.position=new Vec3(200,3,7);s.sync(f);assertEquals(original,f.position);}
   @Test void newDimensionGetsOneNewAnchor() {var f=fixture(Refusal.LEGACY_GRAB);var s=start(f);f.key=new WorldPause.Target(victimId,"minecraft:the_nether");Vec3 destination=new Vec3(9,100,9);f.position=destination;s.sync(f);f.position=new Vec3(88,90,33);s.sync(f);assertEquals(destination,f.position);}
   @Test void isolatedBodyDropsTheOldAnchorAndFreshBodyGetsANewOne() {var f=fixture(Refusal.POLICY);var s=start(f);f.isolated=true;Vec3 limbo=new Vec3(.5,128,.5);f.position=limbo;s.sync(f);assertEquals(limbo,f.position);f.isolated=false;Vec3 spawn=new Vec3(10.5,64,20.5);f.position=spawn;s.sync(f);f.position=new Vec3(50,40,50);s.sync(f);assertEquals(spawn,f.position);}
   @ParameterizedTest @EnumSource(WorldPause.EndReason.class) void everyEndLeavesForeignOwnershipAndFlagsUntouched(WorldPause.EndReason reason) {var f=fixture(Refusal.FOREIGN_PULL);var s=start(f);var record=f.record;s.end(f,reason);assertSame(record,f.record);assertEquals(foreign,f.record.effectId);assertFalse(f.gravity);assertTrue(f.noAi);assertEquals(1000,f.foreignExpiry);assertFalse(s.isPaused(victimId));}
   final class Fixture implements WorldPause.Backend {
      WorldPause.Target key=victim;Vec3 position=original,velocity=Vec3.ZERO;boolean gravity=false,noAi=true,isolated;long foreignExpiry=1000;final Refusal refusal;final ControlManager.ControlRecord record;
      Fixture(Refusal refusal) {this.refusal=refusal;ControlKind kind=switch(refusal){case FOREIGN_PULL -> ControlKind.PULL;case FOREIGN_STASIS -> ControlKind.STASIS;default -> ControlKind.FREEZE;};record=refusal==Refusal.POLICY||refusal==Refusal.LEGACY_GRAB?null:new ControlManager.ControlRecord(victimId,UUID.randomUUID(),foreign,kind,original,false,true);}
      public List<WorldPause.Target> players(){return List.of(key);}public List<WorldPause.Target> hostiles(List<WorldPause.Target> players,int radius){return List.of();}public long now(WorldPause.Target target){return 0;}
      public void freeze(WorldPause.Target target,UUID caster,UUID effect,long until){/* Real refusal categories: foreign ownership, policy or legacy tag. No positional work. */}
      public void release(WorldPause.Target target,UUID effect,ReleaseReason reason){assertNotEquals(foreign,effect);}
      // Additional methods compile against the old interface; RED is a real position assertion.
      public Vec3 position(WorldPause.Target target){return !isolated&&target.equals(key)?position:null;}
      public void hold(WorldPause.Target target,Vec3 anchor){assertEquals(key,target);position=anchor;velocity=Vec3.ZERO;}
   }
}

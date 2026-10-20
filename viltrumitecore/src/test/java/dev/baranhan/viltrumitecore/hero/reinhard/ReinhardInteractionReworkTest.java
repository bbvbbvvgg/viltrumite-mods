package dev.baranhan.viltrumitecore.hero.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.DiagonalCutGeometry;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ReinhardInteractionReworkTest {
   @Test void hiddenVariantsAreNotBindableButExistingIdsRemainStable() {
      for(var ability:List.of(ReinhardAbility.CATCH,ReinhardAbility.REFLECT,ReinhardAbility.GRAB_THROW,
         ReinhardAbility.STOMP,ReinhardAbility.DIAGONAL_WORLD_CUT,ReinhardAbility.DASH_BEHIND,ReinhardAbility.REID_STRIKE)) {
         assertFalse(ReinhardAbilities.active(ability)); assertNull(ReinhardAbilities.actionFor(ability.id()));
      }
      assertEquals(14,ReinhardAbility.DIAGONAL_WORLD_CUT.ordinal());
      for(var mode:ReinhardMode.values())assertEquals(mode,ReinhardActions.contextualMode(ReinhardAbility.STEP_DASH,mode));
      assertNotEquals(ReinhardMode.HOLY_SWORD,ReinhardActions.contextualMode(ReinhardAbility.GRAB_THROW,ReinhardMode.HOLY_SWORD));
   }
   @Test void continuousBeamDoesNotRestartOrStealMainAction() {
      var state=new ReinhardState();state.action.start(ReinhardAbility.FIST_COMBO,14,9);
      for(int t=0;t<80;t++) {
         state.defense.contact(DefenseReaction.Kind.BEAM,30,-10);
         assertEquals(t,state.defense.age());
         state.defense.tick();
      }
      assertTrue(state.action.is(ReinhardAbility.FIST_COMBO));assertEquals(0,state.action.elapsed());
      int flags=state.defense.flags();assertEquals(DefenseReaction.Kind.BEAM,DefenseReaction.kind(flags));
      assertEquals(30,DefenseReaction.yaw(flags),3);assertEquals(-10,DefenseReaction.pitch(flags),5);
      for(int i=0;i<8;i++)state.defense.tick();assertEquals(DefenseReaction.Kind.NONE,state.defense.kind());
   }
   @Test void simultaneousDifferentThreatsCannotRestartEveryTick() {
      var d=new DefenseReaction();d.contact(DefenseReaction.Kind.ARROW,0,0);
      for(int i=0;i<7;i++) {d.tick();assertFalse(d.contact(DefenseReaction.Kind.PROJECTILE,60,0));assertEquals(DefenseReaction.Kind.ARROW,d.kind());}
      d.tick();assertTrue(d.contact(DefenseReaction.Kind.PROJECTILE,60,0));assertEquals(0,d.age());
   }
   @Test void rasterMatchesExhaustiveOriginalMaskAcrossAnglesAndFractionalOrigins() {
      for(int yaw:new int[]{0,45,103,225})for(int pitch:new int[]{-35,0,35}) {
         var g=new DiagonalCutGeometry(new Vec3(.17,63.37,-.61),Vec3.directionFromRotation(pitch,yaw),931);
         var b=g.bounds(); Set<Long> actual=new HashSet<>(),expected=new HashSet<>();var wave=g.wave(58,72);
         int total=wave.remainingWork(),visited=0;
         while(wave.hasNext()) {var c=wave.next();visited++;if(wave.carve(c))assertTrue(actual.add(BlockPos.asLong(c.x(),c.y(),c.z())));}
         assertEquals(total,visited);assertEquals(0,wave.remainingWork());
         for(int x=(int)Math.floor(b.minX);x<Math.ceil(b.maxX);x++)for(int y=58;y<72;y++)for(int z=(int)Math.floor(b.minZ);z<Math.ceil(b.maxZ);z++)
            if(g.contains(new Vec3(x+.5,y+.5,z+.5)))expected.add(BlockPos.asLong(x,y,z));
         assertEquals(expected,actual,"yaw="+yaw+" pitch="+pitch);
      }
   }
   @Test void soundsUseAuthoritativeCompletionAndCustomProjectilesUseSharedHook() throws Exception {
      Path root=Path.of("src/main/java/dev/baranhan/viltrumitecore");
      String ground=Files.readString(root.resolve("client/reinhard/cut/WorldCutScene.java"));
      String diagonal=Files.readString(root.resolve("client/reinhard/cut/DiagonalCutScene.java"));
      assertTrue(ground.contains("!scene.working() && !scene.blasted"));
      assertTrue(diagonal.contains("s.done>=0&&!s.blast"));
      assertFalse(ground.contains("CUT_RUMBLE.get(),t<80?1.4F:2.8F"));
      for(String name:List.of("RepulsorBlastEntity","MicroMissileEntity"))assertTrue(Files.readString(root.resolve("entity/"+name+".java")).contains("HeroProjectiles.intercept(this, hit)"));
   }
}

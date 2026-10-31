package dev.baranhan.viltrumitecore.hero.reinhard.finalpunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Who THE FINAL PUNCH may hit and which one the look picks (pure). */
class FinalPunchTargetsTest {
   private static final Vec3 LOOK = new Vec3(0, 0, 1);

   @Test
   void peopleAndIronGolemsOnly() {
      for (String id : new String[]{"minecraft:player", "minecraft:villager", "minecraft:wandering_trader", "viltrumitecore:citizen", "viltrumitecore:avenger"}) {
         FinalPunchTargets.Entry e = FinalPunchTargets.of(id);
         assertNotNull(e, id);
         assertEquals(FinalPunchTargets.Kind.HUMAN, e.kind(), id);
      }
      FinalPunchTargets.Entry golem = FinalPunchTargets.of("minecraft:iron_golem");
      assertNotNull(golem);
      assertEquals(FinalPunchTargets.Kind.GOLEM, golem.kind());
      assertEquals(FinalPunchTargets.Jaw.GOLEM, golem.jaw());
      for (String id : new String[]{"minecraft:zombie", "minecraft:ender_dragon", "minecraft:pig", "minecraft:armor_stand", "minecraft:item"}) {
         assertNull(FinalPunchTargets.of(id), id);
      }
      assertNull(FinalPunchTargets.of((String) null));
      for (FinalPunchTargets.Entry e : FinalPunchTargets.all()) {
         assertTrue(e.headHeight() > 1.0F && e.headHeight() < 2.5F, e.typeId());
         assertTrue(e.faceDepth() > 0.1F && e.faceDepth() < 0.6F, e.typeId());
      }
   }

   @Test
   void picksTheCandidateClosestToTheLook() {
      List<FinalPunchTargets.Candidate> c = List.of(
         new FinalPunchTargets.Candidate(1, new Vec3(2.0, 0, 6), true, true),
         new FinalPunchTargets.Candidate(2, new Vec3(0.2, 0, 9), true, true),
         new FinalPunchTargets.Candidate(3, new Vec3(0, 0, 3), false, true),
         new FinalPunchTargets.Candidate(4, new Vec3(0, 0, 4), true, false));
      assertEquals(2, FinalPunchTargets.pick(LOOK, c, FinalPunchRules.TARGET_RADIUS, FinalPunchRules.TARGET_CONE_DEGREES),
         "invalid and hidden candidates are skipped; the angle wins");
   }

   @Test
   void nothingBehindOutsideTheConeOrTooFar() {
      assertEquals(-1, FinalPunchTargets.pick(LOOK, List.of(new FinalPunchTargets.Candidate(1, new Vec3(0, 0, -4), true, true)), 16, 28));
      assertEquals(-1, FinalPunchTargets.pick(LOOK, List.of(new FinalPunchTargets.Candidate(1, new Vec3(4, 0, 4), true, true)), 16, 28));
      assertEquals(-1, FinalPunchTargets.pick(LOOK, List.of(new FinalPunchTargets.Candidate(1, new Vec3(0, 0, 16.5), true, true)), 16, 28));
      assertEquals(-1, FinalPunchTargets.pick(LOOK, List.of(), 16, 28));
      assertEquals(-1, FinalPunchTargets.pick(Vec3.ZERO, List.of(new FinalPunchTargets.Candidate(1, new Vec3(0, 0, 4), true, true)), 16, 28));
   }

   @Test
   void serverGraceToleratesALittleLag() {
      assertTrue(FinalPunchTargets.withinGrace(LOOK, new Vec3(0, 0, 17.5), 16, 28));
      assertTrue(FinalPunchTargets.withinGrace(LOOK, new Vec3(Math.tan(Math.toRadians(35)) * 5, 0, 5), 16, 28));
      assertFalse(FinalPunchTargets.withinGrace(LOOK, new Vec3(0, 0, 18.5), 16, 28));
      assertFalse(FinalPunchTargets.withinGrace(LOOK, new Vec3(0, 0, -3), 16, 28));
   }

   @Test
   void theTargetStandsOnTheFistLine() {
      FinalPunchTargets.Entry human = FinalPunchTargets.of("minecraft:villager");
      for (float yaw : new float[]{0, 37, 90, 180, -135}) {
         Vec3 feet = new Vec3(10, 64, -3);
         Vec3 at = FinalPunch.anchor(feet, yaw, human.faceDepth());
         Vec3 jaw = FinalPunch.jawPoint(at, yaw, human);
         Vec3 flat = Vec3.directionFromRotation(0, yaw);
         Vec3 right = new Vec3(-flat.z, 0, flat.x);
         Vec3 rel = jaw.subtract(feet);
         assertEquals(FinalPunch.FIST_REACH - 0.05, rel.dot(flat), 1e-6, "jaw distance at yaw " + yaw);
         assertEquals(FinalPunch.FIST_RIGHT, rel.dot(right), 1e-6, "jaw on the right fist line at yaw " + yaw);
         assertEquals(human.headHeight(), rel.y, 1e-6);
      }
   }
}

package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** №55/№98: aim cone, the 128-block limit, each enemy hit once per throw. */
class MjolnirTargetingTest {
   private static final Vec3 ORIGIN = Vec3.ZERO;
   private static final Vec3 LOOK = new Vec3(0.0, 0.0, 1.0);

   @Test
   void theAimPicksTheEnemyClosestToTheLine() {
      MjolnirTargeting.Candidate onLine = new MjolnirTargeting.Candidate(1, new Vec3(0.0, 0.0, 40.0));
      MjolnirTargeting.Candidate offLine = new MjolnirTargeting.Candidate(2, new Vec3(4.0, 0.0, 20.0));
      MjolnirTargeting.Candidate behind = new MjolnirTargeting.Candidate(3, new Vec3(0.0, 0.0, -5.0));
      assertEquals(1, MjolnirTargeting.aim(ORIGIN, LOOK, List.of(offLine, onLine, behind)).id());
      assertNull(MjolnirTargeting.aim(ORIGIN, LOOK, List.of(behind)), "nothing in the cone: ballistic throw");
      assertNull(MjolnirTargeting.aim(ORIGIN, Vec3.ZERO, List.of(onLine)));
   }

   @Test
   void nothingBeyondTheRange() {
      Vec3 far = new Vec3(0.0, 0.0, MjolnirTuning.THROW_RANGE + 1.0);
      assertFalse(MjolnirTargeting.inRange(ORIGIN, far));
      assertTrue(MjolnirTargeting.inRange(ORIGIN, new Vec3(0.0, 0.0, MjolnirTuning.THROW_RANGE)));
      assertNull(MjolnirTargeting.aim(ORIGIN, LOOK, List.of(new MjolnirTargeting.Candidate(1, far))));
      assertNull(MjolnirTargeting.next(new Vec3(0.0, 0.0, MjolnirTuning.THROW_RANGE), ORIGIN, Set.of(), List.of(new MjolnirTargeting.Candidate(1, far))));
   }

   @Test
   void thePierceGoesOnToTheNearestNotYetHit() {
      Vec3 hammer = new Vec3(0.0, 0.0, 30.0);
      MjolnirTargeting.Candidate hitAlready = new MjolnirTargeting.Candidate(1, new Vec3(0.0, 0.0, 31.0));
      MjolnirTargeting.Candidate near = new MjolnirTargeting.Candidate(2, new Vec3(3.0, 0.0, 30.0));
      MjolnirTargeting.Candidate farther = new MjolnirTargeting.Candidate(3, new Vec3(10.0, 0.0, 30.0));
      MjolnirTargeting.Candidate tooFar = new MjolnirTargeting.Candidate(4, new Vec3(0.0, 0.0, 30.0 + MjolnirTuning.NEXT_TARGET_RADIUS + 1.0));
      assertEquals(2, MjolnirTargeting.next(hammer, ORIGIN, Set.of(1), List.of(hitAlready, farther, near)).id());
      assertNull(MjolnirTargeting.next(hammer, ORIGIN, Set.of(1, 2, 3), List.of(hitAlready, near, farther)), "never the same enemy twice");
      assertNull(MjolnirTargeting.next(hammer, ORIGIN, Set.of(), List.of(tooFar)));
   }
}

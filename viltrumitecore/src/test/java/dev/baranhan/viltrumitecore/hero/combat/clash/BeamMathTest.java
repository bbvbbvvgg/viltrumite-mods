package dev.baranhan.viltrumitecore.hero.combat.clash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Where two beams meet: head-on, near misses, ranges, beams pointing away. */
class BeamMathTest {
   private static final Vec3 A = Vec3.ZERO;
   private static final Vec3 B = new Vec3(20.0, 0.0, 0.0);
   private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
   private static final Vec3 WEST = new Vec3(-1.0, 0.0, 0.0);

   @Test
   void collinearBeamsMeetHalfway() {
      BeamMath.Meeting meeting = BeamMath.meet(A, EAST, 64.0, B, WEST, 64.0);
      assertNotNull(meeting);
      assertEquals(10.0, meeting.alongA(), 1.0E-6);
      assertEquals(10.0, meeting.alongB(), 1.0E-6);
      assertEquals(0.0, meeting.gap(), 1.0E-6);
      assertEquals(10.0, meeting.point().x, 1.0E-6);
   }

   @Test
   void shortBeamMeetsWhereBothReach() {
      BeamMath.Meeting meeting = BeamMath.meet(A, EAST, 64.0, B, WEST, 5.0);
      assertNotNull(meeting);
      assertEquals(16.375, meeting.alongA(), 0.3, "contact runs from B's tip plus the contact radius (13.75) to 19");
      assertEquals(3.625, meeting.alongB(), 0.3);
   }

   @Test
   void slightlyAngledBeamsStillCollide() {
      Vec3 dirB = new Vec3(-1.0, 0.04, 0.0);
      BeamMath.Meeting meeting = BeamMath.meet(A, EAST, 64.0, B, dirB, 64.0);
      assertNotNull(meeting);
      assertTrue(meeting.gap() <= ClashRules.CONTACT_RADIUS);
      assertEquals(10.0, meeting.alongA(), 0.3, "nearly anti-parallel beams overlap, they do not meet at a muzzle");
   }

   @Test
   void parallelBeamsTooFarApartMiss() {
      assertNull(BeamMath.meet(A, EAST, 64.0, new Vec3(20.0, 3.0, 0.0), WEST, 64.0));
   }

   @Test
   void crossingBeamsAreNotHeadOn() {
      assertNull(BeamMath.meet(A, EAST, 64.0, new Vec3(10.0, -10.0, 0.0), new Vec3(0.0, 1.0, 0.0), 64.0));
   }

   @Test
   void beamsOutOfRangeMiss() {
      assertNull(BeamMath.meet(A, EAST, 5.0, B, WEST, 5.0));
   }

   @Test
   void beamsPointingAwayMiss() {
      assertNull(BeamMath.meet(A, WEST, 64.0, B, EAST, 64.0));
   }

   @Test
   void meetingAtTheMuzzleDoesNotCount() {
      assertNull(BeamMath.meet(A, EAST, 64.0, new Vec3(0.5, 0.0, 0.0), WEST, 64.0));
   }

   @Test
   void zeroDirectionIsIgnored() {
      assertNull(BeamMath.meet(A, Vec3.ZERO, 64.0, B, WEST, 64.0));
   }

   @Test
   void startFractionStaysInsideThePlayableBand() {
      assertEquals(0.5, BeamMath.startFraction(A, B, new Vec3(10.0, 0.0, 0.0)), 1.0E-6);
      assertEquals(ClashRules.WIN_MARGIN + 0.08, BeamMath.startFraction(A, B, new Vec3(0.2, 0.0, 0.0)), 1.0E-6);
      assertEquals(1.0 - ClashRules.WIN_MARGIN - 0.08, BeamMath.startFraction(A, B, new Vec3(19.9, 0.0, 0.0)), 1.0E-6);
      assertEquals(0.5, BeamMath.startFraction(A, A, A), 1.0E-6);
      assertEquals(5.0, BeamMath.at(A, B, 0.25).x, 1.0E-6);
   }
}

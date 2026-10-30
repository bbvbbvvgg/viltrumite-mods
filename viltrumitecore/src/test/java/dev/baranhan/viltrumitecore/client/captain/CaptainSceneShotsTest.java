package dev.baranhan.viltrumitecore.client.captain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.captain.CaptainMove;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CaptainSceneShotsTest {
   private static final Vec3 STEVE = new Vec3(0.0, 64.0, 0.0);
   private static final Vec3 F = new Vec3(0.0, 0.0, 1.0);
   private static final Vec3 CENTRE = STEVE.add(F.scale(CaptainRules.CIRCLE_AHEAD));

   @Test
   void callLooksAtStevesFaceFromTheFront() {
      Vec3[] s = CaptainSceneShots.shot(CaptainMove.ASSEMBLE_CALL, 10, STEVE, CENTRE, F, 0);
      assertTrue(s[0].z > 1.5, "lens ahead of him");
      assertTrue(s[1].z < s[0].z, "looking back at him");
      assertEquals(65.45, s[1].y, 1.0E-6);
   }

   @Test
   void arrivalIsWideAndHigh() {
      Vec3[] s = CaptainSceneShots.shot(CaptainMove.ASSEMBLE_WAIT, 20, STEVE, CENTRE, F, 0);
      Vec3 flat = new Vec3(s[0].x - CENTRE.x, 0.0, s[0].z - CENTRE.z);
      assertEquals(CaptainSceneShots.WIDE_RADIUS, flat.length(), 1.0E-6);
      assertTrue(s[0].y > STEVE.y + 3.0);
      assertTrue(s[0].z < STEVE.z, "behind Steve over the landing site");
   }

   @Test
   void circleOrbitsAFullTurnOutsideTheRing() {
      double start = CaptainSceneShots.wideAngle(60);
      Vec3 a = CaptainSceneShots.shot(CaptainMove.ASSEMBLE_RAISE, 0, STEVE, CENTRE, F, start)[0];
      Vec3 half = CaptainSceneShots.shot(CaptainMove.ASSEMBLE_RAISE, CaptainSceneShots.ORBIT_TICKS / 2.0, STEVE, CENTRE, F, start)[0];
      Vec3 end = CaptainSceneShots.shot(CaptainMove.ASSEMBLE_RAISE, CaptainSceneShots.ORBIT_TICKS, STEVE, CENTRE, F, start)[0];
      assertEquals(0.0, new Vec3(a.x - end.x, 0, a.z - end.z).length(), 1.0E-6, "a full 360");
      assertEquals(2.0 * CaptainSceneShots.ORBIT_RADIUS, new Vec3(a.x - half.x, 0, a.z - half.z).length(), 1.0E-6, "opposite side at mid-turn");
      assertTrue(CaptainSceneShots.ORBIT_RADIUS > CaptainRules.CIRCLE_RADIUS + 2.0, "outside the heroes");
      assertTrue(half.y - STEVE.y < 1.6, "a low hero angle");
   }

   @Test
   void circleEndsInStevesEyes() {
      assertEquals(0.0, CaptainSceneShots.returnWeight(CaptainSceneShots.ORBIT_TICKS), 1.0E-9);
      assertEquals(1.0, CaptainSceneShots.returnWeight(CaptainRules.CIRCLE_TICKS), 1.0E-9);
      assertTrue(CaptainSceneShots.RETURN >= 10);
   }
}

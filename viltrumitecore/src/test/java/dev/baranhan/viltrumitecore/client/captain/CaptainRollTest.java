package dev.baranhan.viltrumitecore.client.captain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import org.junit.jupiter.api.Test;

/** Tactical Roll body spin: a full forward turn inside the move, no unwinding, no sinking under the floor. */
class CaptainRollTest {
   @Test
   void spinIsMonotoneAndComplete() {
      assertEquals(0.0F, CaptainRoll.angle(0.0), 1.0E-4);
      assertEquals(360.0F, CaptainRoll.angle(CaptainRules.ROLL_TICKS - 2.0), 1.0E-4);
      assertEquals(360.0F, CaptainRoll.angle(CaptainRules.ROLL_TICKS), 1.0E-4);
      float prev = -1;
      for (double t = 0; t <= CaptainRules.ROLL_TICKS; t += 0.25) {
         float a = CaptainRoll.angle(t);
         assertTrue(a >= prev, "spin never turns back at t=" + t);
         prev = a;
      }
   }

   @Test
   void liftKeepsTheBallOnTheFloor() {
      assertEquals(0.0F, CaptainRoll.lift(0.0F), 1.0E-4);
      assertEquals(0.0F, CaptainRoll.lift(360.0F), 1.0E-4);
      for (float a = 0; a <= 360; a += 5) {
         float centre = CaptainRoll.PIVOT_Y + CaptainRoll.lift(a);
         assertTrue(centre > 6.0F && centre < 14.0F, "centroid stays a ball radius above the floor at " + a);
      }
   }

   @Test
   void travelYawMatchesMinecraftConvention() {
      assertEquals(0.0F, CaptainRoll.travelYaw(0, 1), 1.0E-3);
      assertEquals(90.0F, Math.floorMod(Math.round(CaptainRoll.travelYaw(-1, 0)), 360), 1.0E-3);
      assertTrue(Float.isNaN(CaptainRoll.travelYaw(0, 0)));
   }
}

package dev.baranhan.viltrumitecore.hero.captain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Ricochet chain numbers and the soft next-target assist (rework §16). */
class CaptainRulesTest {
   @Test
   void longerChainSpreadsDamageOverMoreEnemies() {
      assertEquals(5, CaptainRules.maxBounces(false));
      assertEquals(8, CaptainRules.maxBounces(true));
      float normal = 0;
      for (int i = 0; i < CaptainRules.maxBounces(false); i++) {
         normal += CaptainRules.bounceDamage(i);
      }
      float empowered = 0;
      for (int i = 0; i < CaptainRules.maxBounces(true); i++) {
         empowered += CaptainRules.bounceDamage(i);
      }
      assertEquals(33.4, normal, 0.5);
      assertEquals(43.6, empowered, 0.5);
      assertTrue(CaptainRules.bounceDamage(7) > 2.5F, "the last empowered bounce still matters");
   }

   @Test
   void chainCannotRefillTheChargeItSpends() {
      int normal = 0;
      for (int i = 0; i < CaptainRules.maxBounces(false); i++) {
         normal += CaptainRules.bounceGain(i, false);
      }
      assertEquals(28, normal, "3 × 8 as before, the extra bounces only +2");
      int empowered = CaptainRules.WALL_BOUNCES * CaptainRules.wallGain(true);
      for (int i = 0; i < CaptainRules.maxBounces(true); i++) {
         empowered += CaptainRules.bounceGain(i, true);
      }
      assertTrue(empowered < CaptainRules.EMPOWER_COST, "empowered chain returns " + empowered + " of " + CaptainRules.EMPOWER_COST);
      assertTrue(CaptainRules.WALL_BOUNCES * CaptainRules.wallGain(false) < CaptainRules.GAIN_RICOCHET * 2, "walls are no free charge");
   }

   @Test
   void assistPrefersTheNearestEnemyAndRespectsTheLimits() {
      assertTrue(CaptainRules.bounceScore(3, 90, 0) < CaptainRules.bounceScore(6, 0, 0), "a near enemy to the side beats a far one ahead");
      assertTrue(CaptainRules.bounceScore(5, 10, 0) < CaptainRules.bounceScore(5, 60, 0), "same distance: the one ahead");
      assertEquals(Double.POSITIVE_INFINITY, CaptainRules.bounceScore(CaptainRules.BOUNCE_RADIUS + 0.1, 0, 0));
      assertEquals(Double.POSITIVE_INFINITY, CaptainRules.bounceScore(4, CaptainRules.BOUNCE_ASSIST_DEGREES + 1, 0), "no reversal");
      assertEquals(Double.POSITIVE_INFINITY, CaptainRules.bounceScore(4, 0, CaptainRules.BOUNCE_MAX_DY + 0.5), "not up a cliff");
      assertEquals(Double.POSITIVE_INFINITY, CaptainRules.bounceScore(4, 0, -CaptainRules.BOUNCE_MAX_DY - 0.5), "not down a pit");
   }

   @Test
   void softHomingTurnsAtMostTheLimitPerTick() {
      Vec3 dir = new Vec3(1, 0, 0);
      Vec3 small = new Vec3(1, 0, 0.2);
      assertEquals(0.0, ShieldFlight.degrees(ShieldFlight.steer(dir, small, 20), small), 1.0E-6, "a small correction is taken whole");
      Vec3 side = new Vec3(0, 0, 1);
      Vec3 turned = ShieldFlight.steer(dir, side, CaptainRules.SHIELD_TURN_DEGREES);
      assertEquals(CaptainRules.SHIELD_TURN_DEGREES, ShieldFlight.degrees(dir, turned), 1.0E-6);
      assertEquals(90 - CaptainRules.SHIELD_TURN_DEGREES, ShieldFlight.degrees(turned, side), 1.0E-6);
      assertEquals(1.0, turned.length(), 1.0E-9);
      Vec3 behind = ShieldFlight.steer(dir, new Vec3(-1, 0, 0), CaptainRules.SHIELD_TURN_DEGREES);
      assertEquals(CaptainRules.SHIELD_TURN_DEGREES, ShieldFlight.degrees(dir, behind), 1.0E-6, "no instant U-turn");
      // chasing a target at the side: within 90 / 20 ticks the shield points at it
      Vec3 d = dir;
      for (int i = 0; i < 5; i++) {
         d = ShieldFlight.steer(d, side, CaptainRules.SHIELD_TURN_DEGREES);
      }
      assertEquals(0.0, ShieldFlight.degrees(d, side), 1.0E-6);
   }

   @Test
   void fightFollowSpotsStayOutOfStevesViewAndTheCameraLine() {
      for (int i = 0; i < 5; i++) {
         double b = CaptainRules.followBearing(i);
         assertTrue(Math.abs(b) >= 35.0 && Math.abs(360.0 - b) >= 35.0, "avenger " + i + " not in his line of sight: " + b);
         assertTrue(Math.abs(b - 180.0) >= 35.0, "avenger " + i + " not between him and the third-person camera: " + b);
         for (int j = 0; j < i; j++) {
            assertTrue(Math.abs(b - CaptainRules.followBearing(j)) >= 35.0, "spots " + j + " and " + i + " apart");
         }
      }
   }
}

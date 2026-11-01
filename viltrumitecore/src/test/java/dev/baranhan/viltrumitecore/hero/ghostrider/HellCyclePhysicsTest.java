package dev.baranhan.viltrumitecore.hero.ghostrider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics.Input;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics.State;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import org.junit.jupiter.api.Test;

/** The Hell Cycle riding model on flat ground (the entity's collision layer is out of scope here). */
class HellCyclePhysicsTest {
   private static final Input GAS = new Input(1.0, 0.0, false, false, false);
   private static final Input BRAKE = new Input(-1.0, 0.0, false, false, false);

   /** One tick on flat ground: the floor stops the fall like the entity's move() does. */
   private static void ground(State s, Input in) {
      s.onGround = true;
      HellCyclePhysics.step(s, in);
      if (s.velY < 0.0) {
         s.velY = 0.0;
      }
   }

   private static State cruising(double speed) {
      State s = new State();
      s.velZ = speed;
      return s;
   }

   @Test
   void throttleReachesTopSpeedWithoutOvershoot() {
      State s = new State();
      int to60 = -1;
      double max = 0.0;
      for (int t = 0; t < 400; t++) {
         ground(s, GAS);
         max = Math.max(max, s.speed());
         if (to60 < 0 && s.speed() >= 0.6) {
            to60 = t;
         }
      }
      assertTrue(max <= HellCycleRules.MAX_SPEED + 1.0E-9, "never above vmax: " + max);
      assertTrue(s.speed() > 0.80, "close to vmax after 20 s: " + s.speed());
      assertTrue(to60 >= 20 && to60 <= 70, "0 -> 43 km/h in 1-3.5 s, was " + to60 + " ticks");
   }

   @Test
   void brakingFromTopSpeedStopsInAboutTenBlocks() {
      State s = cruising(HellCycleRules.MAX_SPEED);
      double distance = 0.0;
      int ticks = 0;
      boolean stoppie = false;
      while (s.speed() > 0.0 && ticks < 100) {
         ground(s, BRAKE);
         distance += s.speed();
         stoppie |= s.stoppie > 0.5;
         ticks++;
         if (s.forward() <= 0.0) {
            break;
         }
      }
      // playtest tuning: the first brake (about 0.8 s) felt like hitting a wall, now about 1.3 s
      assertTrue(ticks >= 18 && ticks <= 30, "stops in 0.9-1.5 s: " + ticks);
      assertTrue(distance > 7.0 && distance < 13.0, "braking distance " + distance);
      assertTrue(stoppie, "a hard stop from top speed lifts the rear wheel");
   }

   @Test
   void gentleBrakeHasNoStoppie() {
      State s = cruising(0.4);
      double lift = 0.0;
      for (int t = 0; t < 20; t++) {
         ground(s, BRAKE);
         lift = Math.max(lift, s.stoppie);
      }
      assertEquals(0.0, lift, 1.0E-9);
   }

   @Test
   void coastingStopsAndStays() {
      State s = cruising(HellCycleRules.MAX_SPEED);
      int ticks = 0;
      while (s.speed() > 0.0 && ticks < 400) {
         ground(s, Input.NONE);
         ticks++;
      }
      assertTrue(ticks > 40 && ticks < 160, "coasts for 2-8 s: " + ticks);
      for (int t = 0; t < 40; t++) {
         ground(s, Input.NONE);
      }
      assertEquals(0.0, s.speed(), 1.0E-9);
   }

   @Test
   void turnRadiusGrowsWithSpeed() {
      assertEquals(HellCycleRules.MIN_RADIUS, HellCycleRules.turnRadius(0.2), 1.0E-9);
      double r = HellCycleRules.turnRadius(HellCycleRules.MAX_SPEED);
      assertTrue(r > 10.0 && r < 13.0, "about 12 blocks at top speed: " + r);
      // measured: hold full lock at top speed and compare speed / yaw rate
      State s = cruising(HellCycleRules.MAX_SPEED);
      Input left = new Input(1.0, 1.0, false, false, false);
      for (int t = 0; t < 60; t++) {
         ground(s, left);
      }
      double measured = s.speed() / Math.toRadians(Math.abs(s.yawDelta));
      assertTrue(measured > 9.0 && measured < 14.0, "measured radius " + measured);
      assertTrue(s.yawDelta < 0.0, "A turns left (yaw decreases)");
      assertTrue(s.speed() > 0.8, "a gripping turn keeps momentum: " + s.speed());
   }

   @Test
   void leanBalancesTheTurnAndIsCapped() {
      State s = cruising(HellCycleRules.MAX_SPEED);
      Input right = new Input(1.0, -1.0, false, false, false);
      double maxLean = 0.0;
      for (int t = 0; t < 60; t++) {
         ground(s, right);
         maxLean = Math.max(maxLean, Math.abs(s.lean));
      }
      assertTrue(s.lean < 0.0, "leans right into a right turn");
      assertTrue(maxLean <= HellCycleRules.LEAN_MAX + 1.0E-6, "lean cap: " + maxLean);
      assertTrue(maxLean > 28.0, "visible lean at speed: " + maxLean);
      assertTrue(s.yawDelta > 0.0, "D turns right (yaw increases)");
   }

   @Test
   void steeringIsSmoothedNotInstant() {
      State s = cruising(0.5);
      Input left = new Input(1.0, 1.0, false, false, false);
      ground(s, left);
      assertTrue(s.steer < 0.2, "one tick moves the bars a little: " + s.steer);
      int ticks = 1;
      while (s.steer < 1.0 && ticks < 20) {
         ground(s, left);
         ticks++;
      }
      assertTrue(ticks >= 6 && ticks <= 8, "full lock in ~0.3 s: " + ticks);
   }

   @Test
   void driftSlidesWithinTheSlipCapAndPaysABoostOnExit() {
      State s = cruising(0.75);
      Input drift = new Input(1.0, 1.0, false, true, false);
      double maxSlip = 0.0;
      boolean started = false;
      for (int t = 0; t < 30; t++) {
         ground(s, drift);
         started |= (s.events & HellCyclePhysics.EV_DRIFT_START) != 0;
         maxSlip = Math.max(maxSlip, Math.abs(s.slip()));
      }
      assertTrue(started && s.drifting, "drift engaged");
      assertTrue(maxSlip > 20.0, "the tail swings out: " + maxSlip);
      assertTrue(maxSlip <= HellCycleRules.SLIP_MAX + 1.0E-6, "slip cap: " + maxSlip);
      double before = s.forward();
      ground(s, GAS);
      assertFalse(s.drifting);
      assertTrue((s.events & HellCyclePhysics.EV_DRIFT_BOOST) != 0, "a long drift pays the exit boost");
      assertTrue(s.forward() > before, "exit boost adds speed");
   }

   @Test
   void slowDriftKeyIsAHandbrakeNotADrift() {
      State s = cruising(0.2);
      Input drift = new Input(0.0, 1.0, false, true, false);
      for (int t = 0; t < 5; t++) {
         ground(s, drift);
      }
      assertFalse(s.drifting);
      assertTrue(s.speed() < 0.12, "rear wheel locked: " + s.speed());
   }

   @Test
   void dashReachesBoostSpeedThenBleedsBack() {
      State s = cruising(HellCycleRules.MAX_SPEED);
      Input dash = new Input(1.0, 0.0, false, false, true);
      double max = 0.0;
      for (int t = 0; t < HellCycleRules.BOOST_TICKS; t++) {
         ground(s, dash);
         max = Math.max(max, s.speed());
      }
      assertTrue(max > 1.2 && max <= HellCycleRules.BOOST_SPEED + 1.0E-9, "dash speed " + max);
      assertTrue(s.wheelie > 0.0 || s.wheelieTicks == 0, "the dash pops the front");
      for (int t = 0; t < 60; t++) {
         ground(s, GAS);
      }
      assertEquals(HellCycleRules.MAX_SPEED, s.speed(), 0.01);
   }

   @Test
   void reverseIsSlow() {
      State s = new State();
      for (int t = 0; t < 100; t++) {
         ground(s, BRAKE);
      }
      assertEquals(-HellCycleRules.REVERSE_SPEED, s.forward(), 1.0E-6);
   }

   @Test
   void slopesSlowTheClimbAndAParkedBikeHolds() {
      State s = new State();
      s.pitch = 26.57; // a staircase
      for (int t = 0; t < 300; t++) {
         ground(s, GAS);
      }
      assertTrue(s.speed() > 0.25 && s.speed() < 0.65, "climbing speed " + s.speed());
      State parked = new State();
      parked.pitch = 26.57;
      for (int t = 0; t < 60; t++) {
         ground(parked, Input.NONE);
      }
      assertEquals(0.0, parked.speed(), 1.0E-9, "no roll-back while parked");
   }

   @Test
   void jumpIsAControlledHop() {
      State s = cruising(0.6);
      ground(s, new Input(1.0, 0.0, true, false, false));
      assertTrue((s.events & HellCyclePhysics.EV_JUMP) != 0);
      double y = 0.0;
      double apex = 0.0;
      int air = 0;
      s.onGround = false;
      do {
         y += s.velY;
         apex = Math.max(apex, y);
         HellCyclePhysics.step(s, new Input(1.0, 0.0, true, false, false));
         air++;
      } while (y > 0.0 && air < 60);
      assertTrue(apex > 1.1 && apex < 1.6, "clears a one-block curb: " + apex);
      assertTrue(air > 8 && air < 20, "air time " + air);
      assertTrue(s.speed() > 0.55, "speed carries through the air");
   }

   @Test
   void headOnCrashBouncesAndLocksBriefly() {
      State s = cruising(0.8);
      assertTrue(HellCyclePhysics.hitWall(s, 0.8, 0.0, 0.0));
      assertTrue((s.events & HellCyclePhysics.EV_CRASH) != 0);
      assertTrue(s.forward() < 0.0, "bounced back");
      assertEquals(HellCycleRules.CRASH_LOCK_TICKS, s.crashTicks);
      ground(s, GAS);
      assertTrue(s.forward() <= 0.0, "no throttle during the lock");
      State glance = cruising(0.8);
      assertFalse(HellCyclePhysics.hitWall(glance, 0.2, 0.3, 0.6));
      assertEquals(0.3, glance.velX, 1.0E-9);
   }

   @Test
   void enginesShiftAndBackfire() {
      State s = new State();
      boolean shifted = false;
      for (int t = 0; t < 120; t++) {
         ground(s, GAS);
         shifted |= (s.events & HellCyclePhysics.EV_SHIFT) != 0;
         assertTrue(s.rpm >= 0.0 && s.rpm <= 1.05, "rpm " + s.rpm);
      }
      assertTrue(shifted, "gears change while accelerating");
      assertTrue(s.gear >= 4);
      ground(s, Input.NONE);
      assertTrue((s.events & HellCyclePhysics.EV_BACKFIRE) != 0, "lifting off at high rpm pops the exhaust");
   }

   @Test
   void airSteeringIsWeak() {
      State g = cruising(0.6);
      State a = cruising(0.6);
      Input left = new Input(0.0, 1.0, false, false, false);
      for (int t = 0; t < 10; t++) {
         ground(g, left);
         a.onGround = false;
         HellCyclePhysics.step(a, left);
      }
      assertTrue(Math.abs(a.yaw) < Math.abs(g.yaw) * 0.45, "air " + a.yaw + " vs ground " + g.yaw);
   }

   @Test
   void speedometerAndGears() {
      assertEquals(61.2, HellCycleRules.kmh(HellCycleRules.MAX_SPEED), 1.0E-6);
      assertEquals(93.6, HellCycleRules.kmh(HellCycleRules.BOOST_SPEED), 1.0E-6);
      assertEquals(1, HellCycleRules.gear(0.0));
      assertEquals(4, HellCycleRules.gear(0.7));
      assertEquals(5, HellCycleRules.gear(1.2));
   }
}

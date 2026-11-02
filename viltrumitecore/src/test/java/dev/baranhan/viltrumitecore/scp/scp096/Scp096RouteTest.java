package dev.baranhan.viltrumitecore.scp.scp096;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The vertical pursuit of SCP-096 (issue #137): tactic choice, progress and stall detection, the moves' gates. */
class Scp096RouteTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore/scp/scp096");

   private static String src(String file) throws IOException {
      return Files.readString(SRC.resolve(file));
   }

   @Test
   void aReachablePathIsAlwaysRunFirst() {
      assertEquals(Scp096Route.Tactic.GROUND, Scp096Route.choose(36.0, 18.0, true, true, false, true, true, 0));
      assertEquals(Scp096Route.Tactic.GROUND, Scp096Route.choose(-20.0, 4.0, true, false, true, true, false, 0));
   }

   @Test
   void aTargetHighAboveIsClimbedToOrBoredTo() {
      // a roof 36 blocks up across the street: the facade first
      assertEquals(Scp096Route.Tactic.FACADE, Scp096Route.choose(36.0, 18.0, false, false, false, true, true, 0));
      // right above behind the slab: punch through the ceiling first
      assertEquals(Scp096Route.Tactic.BORE, Scp096Route.choose(4.0, 1.0, false, true, false, true, true, 0));
      // without mobGriefing: never bore or tunnel
      for (int stalls = 0; stalls < 8; stalls++) {
         Scp096Route.Tactic t = Scp096Route.choose(4.0, 1.0, false, true, false, false, true, stalls);
         assertTrue(t == Scp096Route.Tactic.FACADE || t == Scp096Route.Tactic.GROUND, t + " at stall " + stalls);
         Scp096Route.Tactic d = Scp096Route.choose(-6.0, 1.0, false, false, true, false, false, stalls);
         assertEquals(Scp096Route.Tactic.GROUND, d, "no digging down without mobGriefing");
      }
      // far away under a ceiling with no facade: the bore/tunnel rotation
      assertEquals(Scp096Route.Tactic.BORE, Scp096Route.choose(20.0, 30.0, false, true, false, true, false, 0));
   }

   @Test
   void everyStallSwitchesTheTactic() {
      Set<Scp096Route.Tactic> seen = EnumSet.noneOf(Scp096Route.Tactic.class);
      Scp096Route.Tactic prev = null;
      for (int stalls = 0; stalls < 4; stalls++) {
         Scp096Route.Tactic t = Scp096Route.choose(36.0, 18.0, false, true, false, true, true, stalls);
         if (prev != null && stalls < 2) {
            assertNotEquals(prev, t, "a stalled tactic is not chosen again right away");
         }
         prev = t;
         seen.add(t);
      }
      assertTrue(seen.contains(Scp096Route.Tactic.FACADE) && seen.contains(Scp096Route.Tactic.BORE) && seen.contains(Scp096Route.Tactic.TUNNEL), seen.toString());
      // even a reachable path is given up once it stalled (the path finder lies about vertical routes)
      assertNotEquals(Scp096Route.Tactic.GROUND, Scp096Route.choose(36.0, 18.0, true, false, false, true, true, 1));
      // same level, closed room: run, then tunnel through
      assertEquals(Scp096Route.Tactic.GROUND, Scp096Route.choose(0.5, 9.0, false, false, false, true, false, 0));
      assertEquals(Scp096Route.Tactic.TUNNEL, Scp096Route.choose(0.5, 9.0, false, false, false, true, false, 1));
   }

   @Test
   void progressStallsOnlyWithoutGain() {
      Scp096Route.Progress p = new Scp096Route.Progress();
      // steady gain never stalls
      for (int i = 0; i < 400; i++) {
         assertFalse(p.tick(500.0 - i));
      }
      p.reset();
      assertEquals(Double.MAX_VALUE, p.best());
      assertFalse(p.tick(40.0));
      int stalledAt = -1;
      for (int i = 1; i <= Scp096Route.STALL_TICKS + 5; i++) {
         // jitter under the threshold is no progress
         if (p.tick(40.0 - (i % 2) * Scp096Route.PROGRESS_EPS * 0.5)) {
            stalledAt = i;
            break;
         }
      }
      assertEquals(Scp096Route.STALL_TICKS, stalledAt);
      assertEquals(0, p.since(), "the counter starts again after a stall");
   }

   @Test
   void leapingWithoutProgressIsBanned() {
      Scp096Route.Progress p = new Scp096Route.Progress();
      p.tick(30.0);
      for (int i = 1; i < Scp096Route.LEAPS_WITHOUT_PROGRESS; i++) {
         assertFalse(p.leapLanded());
      }
      assertTrue(p.leapLanded(), "the third fruitless leap bans leaping");
      p.tick(10.0);
      assertFalse(p.leapLanded(), "progress forgives the earlier leaps");
      assertTrue(Scp096Route.LEAP_BAN_TICKS >= 100 && Scp096Route.PAUSE_TICKS > 0 && Scp096Route.STALLS_BEFORE_PAUSE >= 3);
      // every fruitless run of tactics pauses longer (no endless attempts at a sealed target), up to a cap
      assertEquals(Scp096Route.PAUSE_TICKS, Scp096Route.pauseTicks(1));
      assertEquals(Scp096Route.PAUSE_TICKS * 2, Scp096Route.pauseTicks(2));
      assertTrue(Scp096Route.pauseTicks(3) > Scp096Route.pauseTicks(2));
      assertEquals(Scp096Route.MAX_PAUSE_TICKS, Scp096Route.pauseTicks(50));
      assertEquals(Scp096Route.PAUSE_TICKS, Scp096Route.pauseTicks(0));
   }

   @Test
   void measuresFavourHeightWon() {
      assertTrue(Scp096Route.measure(Scp096Route.Tactic.FACADE, 18.0, 36.0, 4.0) > Scp096Route.measure(Scp096Route.Tactic.FACADE, 18.0, 20.0, 4.0));
      assertTrue(Scp096Route.measure(Scp096Route.Tactic.FACADE, 18.0, 36.0, 10.0) > Scp096Route.measure(Scp096Route.Tactic.FACADE, 18.0, 36.0, 0.0));
      assertTrue(Scp096Route.measure(Scp096Route.Tactic.BORE, 1.0, 8.0, 0.0) > Scp096Route.measure(Scp096Route.Tactic.BORE, 1.0, 4.0, 0.0));
      assertEquals(Scp096Route.measure(Scp096Route.Tactic.GROUND, 5.0, 2.0, 0.0), Scp096Route.measure(Scp096Route.Tactic.TUNNEL, 5.0, 2.0, 0.0));
   }

   @Test
   void facadeScorePrefersCloseCleanUntriedColumns() {
      double near = Scp096Route.facadeScore(5.0, 10.0, 0, true, false);
      assertTrue(near < Scp096Route.facadeScore(15.0, 10.0, 0, true, false));
      assertTrue(near < Scp096Route.facadeScore(5.0, 10.0, 3, true, false));
      assertTrue(Scp096Route.facadeScore(5.0, 10.0, 3, true, false) < Scp096Route.facadeScore(5.0, 10.0, 3, false, false),
         "ledges cost more when they cannot be punched away");
      assertTrue(Scp096Route.facadeScore(5.0, 10.0, 0, true, true) > Scp096Route.facadeScore(40.0, 40.0, 5, false, false), "failed columns last");
   }

   @Test
   void theClimbRampsUpAndCaps() {
      assertEquals(Scp096Route.CLIMB_START, Scp096Route.climbSpeed(0), 1e-9);
      assertTrue(Scp096Route.climbSpeed(10) > Scp096Route.climbSpeed(0));
      assertEquals(Scp096Route.CLIMB_TOP, Scp096Route.climbSpeed(10_000), 1e-9);
      assertEquals(Scp096Route.CLIMB_START, Scp096Route.climbSpeed(-5), 1e-9);
      // 36 blocks (Atheria B1) in well under ten seconds, but not instantly
      double y = 0.0;
      int t = 0;
      while (y < 36.0) {
         y += Scp096Route.climbSpeed(t++);
      }
      assertTrue(t > 40 && t < 200, "ticks for 36 blocks: " + t);
   }

   @Test
   void theDashNeedsSightGroundAndRange() {
      assertTrue(Scp096Route.mayDash(true, 0.0, 10.0, true, 0));
      assertFalse(Scp096Route.mayDash(false, 0.0, 10.0, true, 0), "no lunge at a target it cannot see");
      assertFalse(Scp096Route.mayDash(true, 5.0, 10.0, true, 0), "not at a target on another floor");
      assertFalse(Scp096Route.mayDash(true, 0.0, Scp096Route.DASH_MIN - 1.0, true, 0), "in reach it strikes instead");
      assertFalse(Scp096Route.mayDash(true, 0.0, Scp096Route.DASH_MAX + 1.0, true, 0));
      assertFalse(Scp096Route.mayDash(true, 0.0, 10.0, false, 0));
      assertFalse(Scp096Route.mayDash(true, 0.0, 10.0, true, 1), "cooldown");
   }

   @Test
   void theMantleIgnoresWindowsBelowTheTargetsFloor() {
      assertTrue(Scp096Route.mayMantle(-15.0, -15.0, true, false));
      assertFalse(Scp096Route.mayMantle(-30.0, -15.0, true, false), "an open window of a lower floor is passed by");
      assertTrue(Scp096Route.mayMantle(-30.0, -15.0, true, true), "the wall ends under open sky");
      assertFalse(Scp096Route.mayMantle(-15.0, -15.0, false, true), "nothing to pull onto");
   }

   @Test
   void theLeapReachesAnyApexItCan() {
      for (double need : new double[]{0.5, 1.5, 3.0, 5.0}) {
         double vy = Scp096Leap.vyForApex(need);
         assertTrue(vy > 0.0 && vy <= Scp096Leap.MAX_VY);
         if (vy < Scp096Leap.MAX_VY) {
            assertTrue(Scp096Leap.apex(vy) >= need - 1e-6 && Scp096Leap.apex(vy) < need + 0.1, need + " -> " + vy);
         }
      }
      assertEquals(Scp096Leap.MAX_VY, Scp096Leap.vyForApex(1000.0));
      assertEquals(Scp096Leap.MAX_VY, Scp096Leap.vyForApex(Double.NaN));
   }

   @Test
   void theChaseKeepsItsSafetyRails() throws IOException {
      String c = src("Scp096Chase.java");
      // destruction only with mobGriefing and through the shared breakable check (bedrock, barriers, unbreakable)
      assertTrue(c.contains("if (blockedUp && griefing && this.climbPunches < Scp096Route.MAX_OVERHANG_PUNCHES)"));
      assertTrue(c.contains("if (!s.breakable()) {"));
      String d = src("Scp096Destruction.java");
      assertTrue(d.contains("static List<BlockPos> bore("));
      int bore = d.indexOf("static List<BlockPos> bore(");
      assertTrue(d.indexOf("breakable(", bore) > bore, "bore goes through the breakable check");
      // no endless jumping: fruitless leaps ban leaping, repeated stalls pause
      assertTrue(c.contains("this.progress.leapLanded()"));
      assertTrue(c.contains("Scp096Route.LEAP_BAN_TICKS"));
      assertTrue(c.contains("Scp096Route.STALLS_BEFORE_PAUSE"));
      assertTrue(c.contains("Scp096Route.pauseTicks(this.fruitless / Scp096Route.STALLS_BEFORE_PAUSE)"));
      // the mantle backs off a wall it climbed from the inside instead of falling over the parapet
      assertTrue(c.contains("if (behind || !this.landable(level, this.wallX, this.wallZ)) {"));
   }
}

package dev.baranhan.viltrumitecore.city.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * WindowRules on degenerate inputs and on the thresholds of the code (T7): a zero drop is not a
 * fall, a low window is never used, a quiet city sends nobody to the windows, the causes and the
 * cooldown numbers are exactly the ones of the code. The geometry of the opening itself (free at
 * feet and head, sky outside, the drop search) needs a real level and lives in WindowPeril; here
 * the pure rules pin the numbers those checks decide with.
 */
class WindowRulesTest {
   @Test
   void zeroDropAndLowWindowsAreNeverAFall() {
      // y diff = 0 (and anything below the stumble limit) is not a fall
      assertFalse(PerilRules.isFall(0.0));
      assertFalse(PerilRules.isFall(PerilRules.FALL_MIN_DROP - 0.1));
      assertTrue(PerilRules.isFall(PerilRules.FALL_MIN_DROP));
      // the numbers of the code: a walking person needs a drop of MIN_DROP = 9 (a hop of 8 blocks
      // is not an opening), a jumper needs JUMP_MIN_DROP = 7, and both are real falls
      assertEquals(9, WindowRules.MIN_DROP);
      assertEquals(7, WindowRules.JUMP_MIN_DROP);
      assertEquals(4.0, PerilRules.FALL_MIN_DROP, 0.0);
      assertTrue(WindowRules.MIN_DROP - 1 > PerilRules.FALL_MIN_DROP, "even the smallest rejected drop was no stumble");
      assertTrue(WindowRules.MIN_DROP > WindowRules.JUMP_MIN_DROP, "walking to an opening needs a higher drop than jumping out");
      // the drop search goes down to MAX_DROP and no further
      assertEquals(96, WindowRules.MAX_DROP);
   }

   @Test
   void destructionNumbers() {
      assertEquals(0.0F, WindowRules.fromBlocks(0), 0.0F);
      assertEquals(0.0F, WindowRules.fromBlocks(-100), 0.0F, "no negative destruction");
      assertEquals((float)(1.0 - Math.exp(-1.0)), WindowRules.fromBlocks(WindowRules.BLOCKS_SCALE), 1.0E-4F, "300 changed blocks: 1 - e^-1");
      assertTrue(WindowRules.fromBlocks(1.0E9) <= 1.0F, "saturating");
      assertEquals(0.12F, WindowRules.MIN_DESTRUCTION, 0.0F);
   }

   @Test
   void distanceWeightFadesFromNearToFar() {
      assertEquals(1.0F, WindowRules.distanceWeight(0), 0.0F);
      assertEquals(1.0F, WindowRules.distanceWeight(WindowRules.NEAR), 0.0F);
      assertEquals(0.5F, WindowRules.distanceWeight((WindowRules.NEAR + WindowRules.FAR) / 2.0), 1.0E-6F);
      assertEquals(0.0F, WindowRules.distanceWeight(WindowRules.FAR), 0.0F);
      assertEquals(0.0F, WindowRules.distanceWeight(1000.0), 0.0F);
   }

   @Test
   void approachChanceOnDegenerateInputsAndExactThresholds() {
      // below MIN_DESTRUCTION nobody goes anywhere, however crowded and however close the player
      assertEquals(0.0F, WindowRules.approachChance(WindowRules.MIN_DESTRUCTION - 1.0E-4F, 5, 10, 0.0F), 0.0F);
      assertEquals(0.0F, WindowRules.approachChance(-1.0F, 5, 10, 0.0F), 0.0F);
      // beyond FAR nobody would see it
      assertEquals(0.0F, WindowRules.approachChance(1.0F, 5, WindowRules.FAR + 1, 0.0F), 0.0F);
      // exact values of the code: 0.12 * destruction * crowd * calm * weight
      assertEquals(0.01296F, WindowRules.approachChance(WindowRules.MIN_DESTRUCTION, 0, 10, 0.5F), 1.0E-5F, "0.12 * 0.12 * 1 * 0.9");
      assertEquals(0.276F, WindowRules.approachChance(1.0F, 5, 10, 0.0F), 1.0E-5F, "0.12 * 1 * 2 * 1.15");
      assertEquals(0.156F, WindowRules.approachChance(1.0F, 5, 10, 1.0F), 1.0E-5F, "the brave stay put longer");
      // monotone in destruction, crowd and distance, bounded
      assertTrue(WindowRules.approachChance(0.12F, 0, 10, 0.5F) < WindowRules.approachChance(0.5F, 0, 10, 0.5F));
      assertTrue(WindowRules.approachChance(0.9F, 0, 10, 0.5F) < WindowRules.approachChance(0.9F, 5, 10, 0.5F), "a crowded floor panics more");
      assertTrue(WindowRules.approachChance(0.9F, 0, 90, 0.5F) < WindowRules.approachChance(0.9F, 0, 10, 0.5F), "farther from players: rarer");
      assertTrue(WindowRules.approachChance(1.0F, 5, 10, 0.0F) <= 0.3F, "bounded");
   }

   @Test
   void edgeOnDegenerateInputsAndBoundaries() {
      // a calm floor with nobody on it: only the rare slip can happen
      assertNull(WindowRules.edge(0.0F, 0, 0.0F, 0.026F), "no destruction, alone: just past the rare slip");
      assertEquals(WindowRules.Cause.SLIP, WindowRules.edge(0.0F, 0, 0.0F, 0.02F));
      assertNull(WindowRules.edge(0.0F, 0, 0.0F, 0.025F), "slip = 0.025: 0.025 is not below it");
      // a fresh destruction, alone: tremor up to 0.11, then the slip, then nothing
      assertEquals(WindowRules.Cause.TREMOR, WindowRules.edge(1.0F, 0, 0.0F, 0.10F));
      assertEquals(WindowRules.Cause.SLIP, WindowRules.edge(1.0F, 0, 0.0F, 0.12F));
      assertNull(WindowRules.edge(1.0F, 0, 0.0F, 0.135F), "tremor + slip = 0.135");
      // a shove needs somebody on the floor; more than 4 shove no harder
      assertEquals(WindowRules.Cause.SHOVE, WindowRules.edge(0.0F, 4, 0.0F, 0.10F));
      assertEquals(WindowRules.edge(0.0F, 4, 0.0F, 0.10F), WindowRules.edge(0.0F, 9, 0.0F, 0.10F), "the crowd counts to 4");
      assertNull(WindowRules.edge(0.0F, 0, 0.0F, 0.10F), "alone: nobody shoves");
      // the brave hold on: a steady 0.55 shrinks every chance
      assertEquals(WindowRules.Cause.TREMOR, WindowRules.edge(1.0F, 0, 1.0F, 0.05F));
      assertEquals(WindowRules.Cause.SLIP, WindowRules.edge(1.0F, 0, 1.0F, 0.07F));
      assertNull(WindowRules.edge(1.0F, 0, 1.0F, 0.08F));
   }

   @Test
   void timingsAreExactlyTheOnesOfTheCode() {
      assertEquals(10, WindowRules.edgeTicks(0.0F));
      assertEquals(17, WindowRules.edgeTicks(0.5F));
      assertEquals(24, WindowRules.edgeTicks(1.0F));
      assertEquals(60, WindowRules.windowTicks(0.0F));
      assertEquals(140, WindowRules.windowTicks(0.5F));
      assertEquals(220, WindowRules.windowTicks(1.0F));
      assertEquals(120, WindowRules.gap(true, 0.0F));
      assertEquals(360, WindowRules.gap(true, 1.0F));
      assertEquals(50, WindowRules.gap(false, 0.0F));
      assertEquals(160, WindowRules.gap(false, 1.0F));
      assertEquals(2, WindowRules.showTicks(false, true, 0.0F), "a blast throws out at once");
      assertEquals(6, WindowRules.showTicks(false, true, 1.0F));
      assertEquals(25, WindowRules.showTicks(true, false, 0.0F));
      assertEquals(65, WindowRules.showTicks(true, false, 1.0F));
      assertEquals(20, WindowRules.showTicks(false, false, 0.0F));
      assertEquals(60, WindowRules.showTicks(false, false, 1.0F));
   }

   @Test
   void quotasAndBlastPower() {
      // a fire needs 3 power, plain destruction 20; one more person per 15 / 70; at most 5
      assertEquals(0, WindowRules.quota(2.9, true));
      assertEquals(1, WindowRules.quota(3.0, true));
      assertEquals(1, WindowRules.quota(17.9, true));
      assertEquals(2, WindowRules.quota(18.0, true));
      assertEquals(5, WindowRules.quota(78.0, true));
      assertEquals(0, WindowRules.quota(19.9, false));
      assertEquals(1, WindowRules.quota(20.0, false));
      assertEquals(1, WindowRules.quota(89.9, false));
      assertEquals(2, WindowRules.quota(90.0, false));
      assertEquals(5, WindowRules.quota(1.0E5, false));
      // a shock wave counts as 12 blocks per radius, clamped to 0..64
      assertEquals(0.0, WindowRules.blastPower(0.0F), 0.0);
      assertEquals(0.0, WindowRules.blastPower(-5.0F), 0.0, "no negative power");
      assertEquals(384.0, WindowRules.blastPower(32.0F), 1.0E-9);
      assertEquals(768.0, WindowRules.blastPower(1000.0F), 1.0E-9, "clamped at 64 blocks");
      // people on a falling section: one or two, never more
      assertEquals(1, WindowRules.riders(19, 0.49F));
      assertEquals(0, WindowRules.riders(19, 0.5F));
      assertEquals(2, WindowRules.riders(20, 0.39F));
      assertEquals(1, WindowRules.riders(20, 0.41F));
   }

   @Test
   void onScreenOnDegenerateInputs() {
      Vec3 eye = new Vec3(10, 70, 10), north = new Vec3(0, 0, -1);
      assertTrue(WindowRules.onScreen(north, eye, eye, WindowRules.WINDOW_VIEW), "the eye itself: no direction, never rejected");
      assertFalse(WindowRules.onScreen(Vec3.ZERO, eye, new Vec3(3, 75, -40), WindowRules.WINDOW_VIEW), "a zero look sees nothing");
      assertTrue(WindowRules.onScreen(north, eye, new Vec3(10, 70, -30), WindowRules.WINDOW_VIEW), "straight ahead");
      assertFalse(WindowRules.onScreen(north, eye, new Vec3(0, 70, 30), WindowRules.PLACE_VIEW), "behind");
   }

   @Test
   void keysAndGateOnDegenerateInputs() {
      assertEquals(WindowRules.window(1, 80, 1), WindowRules.window(1, 80, 1));
      assertNotEquals(WindowRules.window(1, 80, 1), WindowRules.window(1, 80, 2), "height matters");
      assertNotEquals(WindowRules.window(-1, 80, -1), WindowRules.window(-2, 80, -1), "negative coordinates stay distinct");
      assertEquals(WindowRules.building(31, -1), WindowRules.building(17, -1), "one 16x16 column is one building");
      assertNotEquals(WindowRules.building(0, 0), WindowRules.building(16, 0));
      WindowRules.Gate g = new WindowRules.Gate();
      long b = WindowRules.building(0, 0), w = WindowRules.window(1, 64, 1);
      assertTrue(g.take(1000, b, w));
      assertFalse(g.take(1000, b, w), "the same tick is within the level gap");
   }

   @Test
   void destructionFieldOnDegenerateInputs() {
      WindowRules.Field f = new WindowRules.Field();
      f.add(Double.NaN, 0, 100, 0);
      f.add(0, Double.NaN, 100, 0);
      f.add(5, 5, 0, 0);
      f.add(5, 5, -3, 0);
      assertEquals(0, f.size(), "NaN coordinates and no blocks change nothing");
      assertTrue(f.quiet(0));
      assertEquals(0.0F, f.at(5, 5, 0), 0.0F);
   }

   @Test
   void theScanNumbersOfTheCode() {
      // open air under a cornice or a balcony: sky light, not a strict sees-the-sky test
      assertEquals(13, WindowRules.OUTSIDE_SKY_LIGHT);
      assertTrue(WindowRules.OUTSIDE_SKY_LIGHT < 15, "a cornice must not hide the street");
      assertEquals(9, WindowRules.SEARCH_R);
      assertEquals(6, WindowRules.CEILING_UP);
      assertEquals(20, WindowRules.SCAN_PERIOD);
      assertEquals(400, WindowRules.HALF_LIFE);
      assertEquals(300.0F, WindowRules.BLOCKS_SCALE, 0.0F);
      assertEquals(50, WindowRules.LEVEL_GAP);
      assertEquals(5, WindowRules.PER_MINUTE);
      assertEquals(500, WindowRules.BUILDING_GAP);
      assertEquals(2400, WindowRules.WINDOW_GAP);
      assertEquals(4, WindowRules.MAX_AT_WINDOWS);
      assertEquals(3, WindowRules.MAX_SHOWING);
      assertEquals(12, WindowRules.MAX_INCIDENTS);
      assertEquals(20.0, WindowRules.MERGE_R, 0.0);
      assertEquals(0.12F, WindowRules.BLAST_THROW, 0.0F);
      assertEquals(140, WindowRules.APPROACH_MAX);
   }
}

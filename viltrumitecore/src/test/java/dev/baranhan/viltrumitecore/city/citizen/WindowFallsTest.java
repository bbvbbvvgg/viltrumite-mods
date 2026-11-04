package dev.baranhan.viltrumitecore.city.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** People falling from broken windows during heavy destruction: scaling, causes, cooldowns, wiring. */
class WindowFallsTest {
   @Test
   void destructionSaturatesAndFades() {
      assertEquals(0.0F, WindowRules.fromBlocks(0), 0.0F);
      assertTrue(WindowRules.fromBlocks(30) < WindowRules.fromBlocks(300));
      assertTrue(WindowRules.fromBlocks(1.0E6) <= 1.0F);
      WindowRules.Field f = new WindowRules.Field();
      assertTrue(f.quiet(0));
      f.add(100, 100, 600, 0);
      float now = f.at(100, 100, 0);
      assertTrue(now > 0.8F, "a big collapse next door: " + now);
      assertTrue(f.at(100 + WindowRules.CELL, 100, 0) < now, "the neighbour cell feels it at half");
      assertEquals(0.0F, f.at(100 + WindowRules.CELL * 3, 100, 0), 0.0F, "far away: nothing");
      assertTrue(f.at(100, 100, WindowRules.HALF_LIFE * 4L) < now * 0.5F, "fades");
      assertFalse(f.quiet(10));
      assertTrue(f.quiet(WindowRules.HALF_LIFE * 7L), "the scan sleeps once all is calm");
      f.prune(WindowRules.HALF_LIFE * 20L);
      assertEquals(0, f.size());
   }

   @Test
   void moreDestructionMorePeopleCloserPlayersMeanMoreWindowVisits() {
      assertEquals(0.0F, WindowRules.approachChance(0.05F, 3, 20, 0.5F), 0.0F, "a quiet city: nobody goes to the windows");
      float small = WindowRules.approachChance(0.2F, 0, 20, 0.5F);
      float big = WindowRules.approachChance(0.9F, 0, 20, 0.5F);
      float crowded = WindowRules.approachChance(0.9F, 5, 20, 0.5F);
      assertTrue(small > 0.0F && small < big && big < crowded);
      assertTrue(crowded <= 0.3F, "bounded");
      assertTrue(WindowRules.approachChance(0.9F, 2, 100, 0.5F) < WindowRules.approachChance(0.9F, 2, 30, 0.5F), "farther from players: rarer");
      assertEquals(0.0F, WindowRules.approachChance(1.0F, 5, WindowRules.FAR + 1, 0.5F), 0.0F, "nobody would see it");
      assertTrue(WindowRules.approachChance(0.9F, 2, 30, 1.0F) < WindowRules.approachChance(0.9F, 2, 30, 0.0F), "the brave stay calmer");
   }

   @Test
   void everyFallAtTheEdgeHasACause() {
      assertEquals(WindowRules.Cause.TREMOR, WindowRules.edge(1.0F, 0, 0.0F, 0.0F));
      assertNull(WindowRules.edge(0.0F, 0, 0.5F, 0.99F), "most seconds nothing happens");
      // alone on the floor nobody shoves: only tremor and slip
      int shoves = 0, falls = 0;
      for (int i = 0; i < 1000; i++) {
         WindowRules.Cause c = WindowRules.edge(0.5F, 0, 0.5F, i / 1000.0F);
         if (c == WindowRules.Cause.SHOVE) {
            shoves++;
         }
         if (c != null) {
            falls++;
         }
      }
      assertEquals(0, shoves);
      assertTrue(falls > 0 && falls < 200, "rare per second: " + falls);
      assertEquals(WindowRules.Cause.SHOVE, WindowRules.edge(0.0F, 3, 0.0F, 0.05F), "a crowded floor: pushed past");
      for (WindowRules.Cause c : WindowRules.Cause.values()) {
         assertTrue(c.out >= 0.2, c + " clears the sill");
      }
      assertTrue(WindowRules.Cause.BLAST.out > WindowRules.Cause.SLIP.out);
   }

   @Test
   void cooldownsKeepItRare() {
      WindowRules.Gate g = new WindowRules.Gate();
      long b1 = WindowRules.building(0, 0), b2 = WindowRules.building(40, 0), b3 = WindowRules.building(80, 0);
      assertTrue(g.take(0, b1, WindowRules.window(1, 80, 1)));
      assertFalse(g.take(10, b2, WindowRules.window(41, 80, 1)), "level gap");
      assertTrue(g.take(WindowRules.LEVEL_GAP, b2, WindowRules.window(41, 80, 1)));
      assertFalse(g.take(WindowRules.LEVEL_GAP * 2L, b1, WindowRules.window(2, 90, 1)), "same building again too soon");
      assertTrue(g.take(WindowRules.LEVEL_GAP * 2L, b3, WindowRules.window(81, 70, 1)));
      long t = WindowRules.BUILDING_GAP + 10;
      assertFalse(g.take(t, b1, WindowRules.window(1, 80, 1)), "the same window waits longer");
      assertTrue(g.take(t, b1, WindowRules.window(5, 80, 1)), "another window of the building later");
      WindowRules.Gate h = new WindowRules.Gate();
      int started = 0;
      for (long now = 0; now < 1200; now += WindowRules.LEVEL_GAP) {
         if (h.take(now, WindowRules.building((int)now * 16, 0), now)) {
            started++;
         }
      }
      assertEquals(WindowRules.PER_MINUTE, started, "per-minute cap");
   }

   @Test
   void timingsVary() {
      assertTrue(WindowRules.windowTicks(0.0F) < WindowRules.windowTicks(1.0F));
      assertTrue(WindowRules.edgeTicks(0.0F) >= WindowRules.EDGE_MIN);
      assertTrue(WindowRules.MIN_DROP >= PerilRules.FALL_MIN_DROP, "an opening always means a real fall");
   }

   @Test
   void wiredIntoTheCityAndThePeril() throws Exception {
      String peril = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/citizen/CitizenPeril.java"));
      int tick = peril.indexOf("static boolean tick(CitizenEntity c, CityManager city)");
      int hook = peril.indexOf("WindowPeril.tick(c, city, now)", tick);
      int sw = peril.indexOf("switch (c.citizenState())", tick);
      assertTrue(tick > 0 && hook > tick && hook < sw, "the window plan runs before the peril states");
      assertTrue(peril.contains("WindowPeril.onDestruction(level, info.center()"), "a collapse shakes the buildings");
      assertTrue(peril.indexOf("WindowPeril.cancel(c);", peril.indexOf("static void onHeld(")) > 0);
      String city = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/CityManager.java"));
      assertTrue(city.contains("WindowPeril.scan(this, now)"));
      assertTrue(city.contains("WindowPeril.onDestruction(this.level, b[0] / b[3], b[2] / b[3], b[3], now)"), "block bursts count");
      String pop = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/CityPopulation.java"));
      assertTrue(pop.contains("WindowPeril.busy(c)"), "never trimmed mid-fall or at a window");
      assertTrue(pop.contains("seen(level, x + 0.5, fy + 1.0, z + 0.5)"), "nobody appears in plain sight");
      String wp = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/citizen/WindowPeril.java"));
      assertFalse(wp.contains("discard()"), "nobody vanishes");
      assertFalse(wp.contains("setNoGravity(true)"), "the fall is real gravity");
      assertNotNull(WindowRules.Cause.valueOf("BLAST"));
   }
}

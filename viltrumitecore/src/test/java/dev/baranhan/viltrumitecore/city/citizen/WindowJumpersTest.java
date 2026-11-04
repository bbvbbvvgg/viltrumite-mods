package dev.baranhan.viltrumitecore.city.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The background picture: people going out of the windows of damaged, collapsing and burning buildings. */
class WindowJumpersTest {
   @Test
   void aFewPeoplePerPlaceNeverARain() {
      assertEquals(0, WindowRules.quota(5.0, false), "a broken wall is not a disaster");
      assertEquals(0, WindowRules.quota(1.0, true), "one flame is not a burning building");
      assertTrue(WindowRules.quota(WindowRules.blastPower(4.0F) + 40.0, false) >= 1, "a TNT blast in a building: somebody goes out");
      assertTrue(WindowRules.quota(10.0, true) >= 1, "a burning floor: somebody jumps");
      for (double p = 0; p < 1.0E5; p += 37.0) {
         assertTrue(WindowRules.quota(p, false) <= 5 && WindowRules.quota(p, true) <= 5, "capped");
         assertTrue(WindowRules.quota(p, false) <= WindowRules.quota(p + 100.0, false), "more destruction, never fewer");
      }
      assertTrue(WindowRules.gap(true, 0.0F) >= 100, "a fire: now and then");
      assertTrue(WindowRules.gap(false, 0.0F) >= 40);
      assertTrue(WindowRules.gap(false, 1.0F) <= 200);
      assertTrue(WindowRules.showTicks(false, true, 1.0F) <= 6, "a blast throws out at once");
      assertTrue(WindowRules.showTicks(true, false, 0.0F) >= 20, "time to see the person panic in the window");
      assertTrue(WindowRules.riders(500, 0.0F) <= 2 && WindowRules.riders(500, 0.99F) >= 1);
      assertTrue(WindowRules.JUMP_MIN_DROP >= PerilRules.FALL_MIN_DROP, "going out of a window is a real fall");
      assertTrue(WindowRules.Cause.JUMP.out >= 0.2 && WindowRules.Cause.JUMP.up > WindowRules.Cause.SLIP.up, "a jump clears the sill");
   }

   @Test
   void onlyWindowsThePlayerLooksAt() {
      net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(0, 70, 0), north = new net.minecraft.world.phys.Vec3(0, 0, -1);
      assertTrue(WindowRules.onScreen(north, eye, new net.minecraft.world.phys.Vec3(3, 75, -40), WindowRules.WINDOW_VIEW), "ahead");
      assertFalse(WindowRules.onScreen(north, eye, new net.minecraft.world.phys.Vec3(30, 50, -20), WindowRules.WINDOW_VIEW), "far to the side and below");
      assertFalse(WindowRules.onScreen(north, eye, new net.minecraft.world.phys.Vec3(0, 70, 40), WindowRules.PLACE_VIEW), "behind");
      assertTrue(WindowRules.WINDOW_VIEW > WindowRules.PLACE_VIEW);
   }

   @Test
   void aRaisedSillNeedsTheThirdRowOpen() {
      assertEquals(22, WindowJumpers.bodyTop(21.0), "a flat floor: the body fits two rows");
      assertEquals(23, WindowJumpers.bodyTop(21.5), "on a wall block (1.5 tall) the head reaches the third row");
   }

   @Test
   void wiredIntoTheCity() throws Exception {
      String city = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/CityManager.java"));
      assertTrue(city.contains("WindowJumpers.onBlast(this.level, x, y, z, radius, this.now())"), "blasts");
      assertTrue(city.contains("WindowJumpers.onBurst(this.level, b[0] / b[3], b[1] / b[3], b[2] / b[3], b[3], now)"), "smashed buildings");
      assertTrue(city.contains("instanceof net.minecraft.world.level.block.BaseFireBlock") && city.contains("WindowJumpers.onFire(level, pos, m.now())"), "fires");
      assertTrue(city.contains("WindowJumpers.tick(this, now)"));
      String peril = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/citizen/CitizenPeril.java"));
      assertTrue(peril.contains("WindowJumpers.onCollapse(level, city, info, now)"), "collapses");
      int hurt = peril.indexOf("static boolean onHurt(CitizenEntity c)");
      assertTrue(peril.indexOf("c.windowPhase == WindowJumpers.SHOW", hurt) > hurt && peril.indexOf("c.windowPhase == WindowJumpers.SHOW", hurt) < peril.indexOf("WindowPeril.cancel(c)", hurt),
         "burning in the window makes the person go out sooner, never back into the room");
      assertTrue(peril.contains("WindowJumpers.forget(dimension)") && peril.contains("WindowJumpers.forgetAll()"));
      String wp = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/citizen/WindowPeril.java"));
      assertTrue(wp.contains("case WindowJumpers.SHOW -> WindowJumpers.show(c, city, now)"), "the window phase runs in the peril tick");
      assertTrue(wp.contains("c.setRemainingFireTicks(70)"), "out of a burning building still on fire");
      String j = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/citizen/WindowJumpers.java"));
      assertTrue(j.contains("level.clip(new ClipContext(eye, end"), "the window is the one the player looks at");
      assertTrue(j.contains("the sight line stopped at another building in front of the damaged one"));
      assertTrue(j.contains("level.destroyBlock(p, false, by)"), "the glass breaks (journaled, a reset restores it)");
      assertFalse(j.contains("setNoGravity(true)"), "the fall is real gravity");
      assertFalse(j.contains("discard()"), "nobody vanishes");
   }
}

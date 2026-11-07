package dev.baranhan.viltrumitecore.city.links;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T20: a collapse that is aborted (level unload, {@code /collapse clear}, {@code /city reset}) leaves no
 * danger zones behind, and {@code /city reset} clears the whole danger map — the map is whole again, so
 * citizens must not keep fleeing destruction that no longer exists. {@code DangerMap.clearNear} is pure
 * and tested directly; the server-bound wiring (the {@code Finished(aborted)} listener and
 * {@code CityDisasterLinks.onCityReset} inside {@code CityManager.startReset}) is pinned by source
 * checks, the way {@code CityDisasterLinksTest.systemsAreWiredTogether} does it.
 */
class CityDangerReconcileTest {
   @BeforeAll
   static void boot() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   // ---- DangerMap.clearNear (pure)

   @Test
   void clearNearRemovesEveryZoneKindInsideTheRadius() {
      DangerMap m = new DangerMap();
      m.add(0, 64, 0, DangerMap.FIGHT_RADIUS, 0, 600); // the start-of-collapse zone
      m.add(2, 64, 2, DangerMap.EXPLOSION_RADIUS, 10, 600); // merges into one cluster
      m.track(7, 6, 64, -3, 9.0F, 30.0F, 20, 600); // a tracked soldier of the event
      m.add(30, 64, 0, 0.0F, 28.0F, 30, 600); // a curiosity inside the area
      assertEquals(3, m.size());
      m.blast(1, 64, 1, 12.0F, 40);
      assertTrue(m.blastAt(1, 64, 1, 41) > 0.0F);

      int gone = m.clearNear(0.5, 64.0, 0.5, 40.0F);
      assertEquals(3, gone, "the cluster, the tracked zone and the curiosity all go");
      assertEquals(0, m.size());
      assertEquals(0.0F, m.blastAt(1, 64, 1, 41), "the fresh shock wave of the event goes too");
      assertNull(m.threat(0, 64, 0, 41, 4.0));
      assertNull(m.noticed(30, 64, 0, 41));
      assertEquals(0, m.clearNear(0.5, 64.0, 0.5, 40.0F), "nothing left to clear");
      assertEquals(0, m.clearNear(Double.NaN, 0, 0, 40.0F), "garbage input clears nothing");
      assertEquals(0, m.clearNear(0, 64, 0, 0.0F), "a zero radius clears nothing");
   }

   @Test
   void clearNearKeepsTheZonesOfOtherEvents() {
      DangerMap m = new DangerMap();
      m.add(0, 64, 0, DangerMap.EXPLOSION_RADIUS, 0, 600); // the aborted collapse
      m.add(90, 64, 10, DangerMap.FIGHT_RADIUS, 10, 600); // a kaiju fight two streets away
      m.track(9, 140, 64, 40, 12.0F, 50.0F, 20, 600); // its tracked walker
      assertEquals(3, m.size());

      assertEquals(1, m.clearNear(0, 64, 0, 40.0F));
      assertEquals(2, m.size());
      assertNotNull(m.threat(95, 64, 10, 41, 4.0), "the other event's danger stays");
      assertNotNull(m.threat(140, 64, 40, 41, 4.0), "the other event's tracked source stays");
      assertNull(m.threat(0, 64, 0, 41, 4.0));
   }

   // ---- wiring (source pins, as the CityDisasterLinksTest contract test does)

   @Test
   void anAbortedCollapseClearsItsDangerZones() throws Exception {
      String links = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/links/CityDisasterLinks.java"));
      int finished = links.indexOf("public static void onCollapseFinished(CityCollapseEvent.Finished event)");
      assertTrue(finished > 0, "the Finished listener exists");
      int abort = links.indexOf("event.aborted()", finished);
      int clear = links.indexOf("clearCollapseDanger(event.getLevel(), event.info())", abort);
      assertTrue(abort > finished && clear > abort, "Finished(aborted) drops the collapse's danger zones");
      assertTrue(links.contains("danger.clearNear(c.x, c.y, c.z, reach)"), "the collapse centre is cleared");
      assertTrue(links.contains("danger.clearNear(lc.x, lc.y, lc.z,"), "the landing zone is cleared too");
      // a collapse that ran to its end keeps its zones (the ruin is real)
      String guarded = links.substring(links.indexOf("if (event.aborted())"), links.indexOf("clearCollapseDanger(event.getLevel(), event.info())"));
      assertFalse(guarded.contains("else"), "the clear is bound to the abort branch only");
   }

   @Test
   void cityResetStopsCollapsesBeforeItClearsTheMap() throws Exception {
      String city = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/CityManager.java"));
      int reset = city.indexOf("void startReset()");
      int abort = city.indexOf("CollapseDirector.abortAll(this.level)", reset);
      int linksReset = city.indexOf("CityDisasterLinks.onCityReset(this.level)", abort);
      assertTrue(reset >= 0 && abort > reset && linksReset > abort,
         "/city reset stops the collapses first, then clears ruins and outages (their listeners clear the danger zones)");

      String links = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/links/CityDisasterLinks.java"));
      int method = links.indexOf("public static void onCityReset(ServerLevel level)");
      assertTrue(links.indexOf("dangerMap().clear()", method) > method,
         "the reset wipes the whole danger map (aborted collapses, other stopped events, stale zones)");
      assertTrue(links.contains("CityManager city = CityManager.of(level);"), "only the level's own city is touched");
   }

   @Test
   void resumeAfterRestartAlsoReconcilesTheDangerMap() throws Exception {
      String city = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/CityManager.java"));
      int resume = city.indexOf("this.restore.start(now)");
      assertTrue(resume > 0 && city.indexOf("CityDisasterLinks.onCityReset(this.level)", 0) < resume,
         "a reset resumed after a restart clears the danger map before the queue runs");
   }

   @Test
   void clearNearRadiusCoversTheZoneRadiiTheDirectorUses() {
      // the director marks radius+10 at start, radius+8 on the main fall and radius+6 at impact;
      // an abort must reach over all of them plus the drift of a merged cluster
      assertTrue(CityDisasterLinks.DANGER_CLEAR_MARGIN >= 16.0F, "margin covers the +10 start zone");
      float radius = DangerMap.MAX_RADIUS; // the biggest collapse zone possible (clamped)
      assertTrue(radius + CityDisasterLinks.DANGER_CLEAR_MARGIN > DangerMap.MAX_RADIUS,
         "an abort always clears beyond the clamped zone radius");
   }
}

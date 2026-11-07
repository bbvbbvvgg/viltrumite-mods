package dev.baranhan.viltrumitecore.city.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.city.profile.CityProfile.District;
import org.junit.jupiter.api.Test;

/**
 * T19: the district lookup and its boundaries. {@code districtAt} feeds subtitles, {@code /city here},
 * the grid's outage districts, the Chitauri war sectors and {@code /city status}, so its edge rules are
 * pinned at the exact distances the code uses: the half-open box (+1 on both max edges), the strict
 * 64-block nearest fallback ({@code ds < 64*64}, measured to {@code maxX}/{@code maxZ}, not {@code +1})
 * and the first-box-wins tie.
 */
class CityProfileDistrictsTest {
   /** A profile with the given districts in list order (arena big enough to hold them). */
   private static CityProfile profile(District... districts) {
      StringBuilder json = new StringBuilder("{\"id\":\"t\",\"arena\":{\"minX\":-100000,\"minZ\":-100000,\"maxX\":100000,\"maxZ\":100000},\"districts\":[");
      for (int i = 0; i < districts.length; i++) {
         District d = districts[i];
         if (i > 0) {
            json.append(',');
         }

         json.append("{\"name\":\"").append(d.name()).append("\",\"minX\":").append(d.minX()).append(",\"minZ\":").append(d.minZ())
            .append(",\"maxX\":").append(d.maxX()).append(",\"maxZ\":").append(d.maxZ()).append("}");
      }

      return CityProfile.parse(JsonParser.parseString(json.append("]}").toString()).getAsJsonObject());
   }

   private static District box(String name, int minX, int minZ, int maxX, int maxZ) {
      return new District(name, minX, minZ, maxX, maxZ, 0);
   }

   // ---- the box and its half-open edges

   @Test
   void boxContainsItsEdgesPlusOne() {
      District d = box("D", 0, 0, 100, 100);
      // contains is [minX..maxX+1] x [minZ..maxZ+1]: the block at maxX+1 still belongs to the box
      assertTrue(d.contains(0, 0));
      assertTrue(d.contains(100, 100));
      assertTrue(d.contains(101, 101), "the +1 edge on both axes");
      assertTrue(d.contains(101, 0));
      assertTrue(d.contains(0, 101));
      assertTrue(d.contains(50, 101));
      assertFalse(d.contains(102, 50), "one past the +1 edge");
      assertFalse(d.contains(50, 102));
      assertFalse(d.contains(-1, 50), "the min side is not half-open");
      assertFalse(d.contains(50, -1));
      assertFalse(d.contains(-1, -1));
      assertFalse(d.contains(102, 102));
   }

   @Test
   void distanceSqIsZeroInsideAndGrowsPastTheEdges() {
      District d = box("D", 0, 0, 100, 100);
      assertEquals(0.0, d.distanceSq(50, 50));
      assertEquals(0.0, d.distanceSq(0, 0));
      // distance is measured to [minX..maxX], not to the +1 containment edge
      assertEquals(1.0, d.distanceSq(101, 50), 1.0E-9, "at the containment edge: 1 to maxX");
      assertEquals(2.0, d.distanceSq(101, 101), 1.0E-9, "past both edges: 1+1");
      assertEquals(4.0, d.distanceSq(102, 50), 1.0E-9);
      assertEquals(4.0, d.distanceSq(50, 102), 1.0E-9);
      assertEquals(9.0, d.distanceSq(103, 50), 1.0E-9);
      assertEquals(25.0, d.distanceSq(-5, 50), 1.0E-9, "also on the min side");
      assertEquals(4.5, d.distanceSq(101.5, 101.5), 1.0E-9, "fractional positions work");
   }

   // ---- districtAt: containment, specificity, nearest fallback, null

   @Test
   void containingBoxWinsImmediately() {
      // walk.py sorts the boxes smallest first, so the small box comes first in the list
      CityProfile p = profile(box("Small", 400, 400, 500, 500), box("Big", 0, 0, 1000, 1000));
      assertEquals("Small", p.districtAt(450, 450).name(), "the smaller box inside the big one wins (specificity)");
      assertEquals("Big", p.districtAt(100, 100).name());
      assertEquals("Big", p.districtAt(450, 250).name(), "inside the big one, outside the small one");
   }

   @Test
   void nearestBoxIsFoundWithin64BlocksOnEachAxis() {
      CityProfile p = profile(box("West", 0, 0, 100, 100));
      assertEquals("West", p.districtAt(141, 50).name(), "41 past the edge: inside the 64 fallback");
      assertEquals("West", p.districtAt(163, 50).name(), "dx = 63 < 64");
      assertNull(p.districtAt(164, 50), "dx = 64 is not < 64: the fallback is strict at the boundary");
      assertNull(p.districtAt(165, 50), "dx = 65 > 64");
      // x = 100.5 is inside the containment box (maxX + 1 = 101): containment answers, no fallback
      assertEquals("West", p.districtAt(100.5, 50).name(), "fractional position on the +1 edge: still the box");
   }

   @Test
   void nearestBoxIsFoundByCornerDistance() {
      CityProfile p = profile(box("Corner", 0, 0, 100, 100));
      // a point off the south-east corner: the corner distance decides (dx=31, dz=40 -> ~50.6 < 64)
      assertEquals("Corner", p.districtAt(131, 140).name());
      assertNull(p.districtAt(150, 150), "corner distance ~70.7 > 64");
      // the containment edge itself (101.5 > maxX+1 = 101) falls back and is caught by distance
      assertEquals("Corner", p.districtAt(101.5, 101.5).name());
   }

   @Test
   void theFirstOfTwoEquallyNearBoxesWins() {
      // two boxes 49 away on each side of the point: the earlier list entry wins (strict <)
      CityProfile p = profile(box("Left", 0, 0, 100, 100), box("Right", 200, 0, 300, 100));
      assertEquals("Left", p.districtAt(150, 50).name());
   }

   @Test
   void farAwayAndEmptyProfilesAnswerNull() {
      CityProfile p = profile(box("D", 0, 0, 100, 100));
      assertNull(p.districtAt(900, 900), "a block in the unnamed rest of the map");
      assertNull(profile().districtAt(50, 50), "no districts at all");
      assertEquals(0, profile().districts.size());
   }

   @Test
   void theWholeChainMatchesTheAnchoredCityShape() {
      // the shape walk.py builds from the sign anchors: two boxes, the rest of the map is unnamed
      CityProfile p = profile(box("Downtown", 100, 100, 300, 300), box("Airport", -1200, -300, -900, 0));
      assertEquals("Downtown", p.districtAt(200, 200).name());
      assertEquals("Airport", p.districtAt(-1000, -150).name());
      assertEquals("Downtown", p.districtAt(305, 200).name(), "5 past the edge: the 64 fallback answers");
      assertNull(p.districtAt(-1500, -300), "outside every box and beyond 64");
   }

   // ---- the debug command is wired and reads the same lookup (source pin, as CityDisasterLinksTest does)

   @Test
   void cityDistrictsCommandIsWiredToTheLookup() throws Exception {
      String cmds = java.nio.file.Files.readString(
         java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/city/CityCommands.java"));
      int literal = cmds.indexOf("Commands.literal(\"districts\")");
      assertTrue(literal > 0, "/city districts exists");
      assertTrue(cmds.indexOf("p.districtAt(x, z)", literal) > literal, "the probe resolves through districtAt");
      assertTrue(cmds.contains("viltrumitecore.city.districts.line"), "the per-district line is translatable");
      // the help loop lists the subcommand by key: viltrumitecore.city.help.<name>
      assertTrue(cmds.contains("\"status\", \"districts\", \"here\""), "the help loop lists the new subcommand");
      String lang = java.nio.file.Files.readString(java.nio.file.Path.of(
         "src/main/resources/assets/viltrumite_city/lang/en_us.json"));
      assertTrue(lang.contains("\"viltrumitecore.city.help.districts\""), "the help line is translated");
      assertTrue(lang.contains("\"viltrumitecore.city.districts.unresolved\""), "the fallback line is translated");
   }
}

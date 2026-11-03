package dev.baranhan.viltrumitecore.scp.scp096;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regressions for the review of PR #146 (vertical chase). The chase needs a running server, so the wiring is pinned by source checks. */
class Scp096VerticalChaseReviewTest {
   private static final Path CHASE = Path.of("src/main/java/dev/baranhan/viltrumitecore/scp/scp096/Scp096Chase.java");

   private static String chase() throws IOException {
      return Files.readString(CHASE);
   }

   private static int count(String s, String needle) {
      int n = 0;
      for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + needle.length())) {
         n++;
      }
      return n;
   }

   @Test
   void aCollisionThatCancelsTheDashAlsoClearsTheSyncedFlag() throws IOException {
      String s = chase();
      int blocked = s.indexOf("boolean blocked = this.mob.horizontalCollision;");
      int stuck = s.indexOf("// ---- stuck:", blocked);
      assertTrue(blocked > 0 && stuck > blocked);
      String branch = s.substring(blocked, stuck);
      assertTrue(branch.contains("this.dashTicks = 0;\n         this.mob.setFlag(Scp096Entity.FLAG_DASH, false);"),
         "the smash/climb branch must clear FLAG_DASH together with dashTicks");
   }

   @Test
   void thePathCooldownAgesOncePerChaseTickOutsideFollowPath() throws IOException {
      String s = chase();
      assertEquals(1, count(s, "this.pathAge++;"), "exactly one increment of the path cooldown");
      int tick = s.indexOf("void tick(ServerLevel level, Entity target) {");
      int inc = s.indexOf("this.pathAge++;");
      int follow = s.indexOf("private Vec3 followPath(Vec3 tp) {");
      assertTrue(tick > 0 && inc > tick && inc < s.indexOf("// ---- attack:", tick), "the increment lives at the top of tick()");
      int followEnd = s.indexOf("\n   }\n", follow);
      assertFalse(s.substring(follow, followEnd).contains("pathAge++"), "followPath must not be the only thing that ages the cooldown");
   }

   @Test
   void aStalledFacadeChaseCanReplanAfterTheMinimumCooldown() {
      // FACADE/BORE/TUNNEL ticks never call followPath; with the per-tick clock the replan gate opens after REPATH_MIN_TICKS
      int pathAge = 0;
      for (int t = 0; t < Scp096Rules.REPATH_MIN_TICKS; t++) {
         pathAge++;
      }
      assertTrue(pathAge >= Scp096Rules.REPATH_MIN_TICKS);
      assertTrue(Scp096Rules.shouldRepath(false, false, pathAge, true));
   }
}

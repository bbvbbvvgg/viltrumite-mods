package dev.baranhan.viltrumitecore.hero.venom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Review of PR #144: pull lifecycle, throw landings, kill hunger, host super-jump, tendril frame. */
class VenomReviewTest {
   private static String src(String path) throws Exception {
      return Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/" + path));
   }

   @Test
   void aPullFromTheFarEndArrivesBeforeItTimesOut() throws Exception {
      int needed = (int)Math.ceil(VenomRules.TENDRIL_RANGE / VenomRules.PULL_SPEED);
      assertTrue(VenomRules.PULL_MAX_TICKS > needed);
      assertTrue(VenomMove.PULL.selfEnding() && !VenomMove.PULL.loops());
      assertTrue(src("hero/venom/VenomCombat.java").contains("!m.loops() && !m.selfEnding() && state.elapsed >= m.length"));
      String skills = src("hero/venom/VenomSkills.java");
      assertTrue(skills.contains("state.elapsed >= VenomRules.PULL_MAX_TICKS"));
      int finish = skills.indexOf("static void finish(");
      assertTrue(skills.indexOf("case PULL -> endPull(", finish) > finish, "finish() cleans a pull up");
   }

   @Test
   void aThrownBodyLandingIsSeenAfterVanillaResetsTheFall() {
      assertTrue(VenomThrows.hardLanding(true, 3.5F, 0.2, true), "fell far");
      assertTrue(VenomThrows.hardLanding(true, 0.5F, 0.9, true), "came down fast");
      assertFalse(VenomThrows.hardLanding(true, 3.5F, 0.9, false), "still in the air");
      assertFalse(VenomThrows.hardLanding(false, 0.0F, 0.0, true), "never left the ground");
      assertFalse(VenomThrows.hardLanding(true, 0.8F, 0.3, true), "a soft landing");
   }

   @Test
   void killsFeedTheSymbiote() throws Exception {
      String events = src("hero/venom/VenomEvents.java");
      assertTrue(events.contains("creditKill(event.getEntity(), event.getSource().getEntity())"));
      assertTrue(events.contains("VenomRules.feed(state.hunger, VenomRules.HUNGER_KILL)"));
   }

   @Test
   void aDrivenHostCannotSuperJump() throws Exception {
      assertTrue(src("client/hero/SuperJumpClient.java").contains("VenomPossessionClient.localHost()) return true;"));
   }

   @Test
   void tendrilEndpointsShareTheWorldFrame() throws Exception {
      String r = src("client/venom/VenomRenderer.java");
      assertTrue(r.contains("Matrix4f world = worldFrame(base, f.e(), partial);"));
      assertFalse(r.contains("stack.last().pose().set(base);"), "no world offsets drawn through the rotated base");
   }
}

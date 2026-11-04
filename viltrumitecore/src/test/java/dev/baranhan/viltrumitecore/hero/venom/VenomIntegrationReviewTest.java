package dev.baranhan.viltrumitecore.hero.venom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Review of PR #149: the slam's own blast, airborne wall impacts, PvP rules on Spider lines, transformation veins. */
class VenomIntegrationReviewTest {
   private static String src(String path) throws Exception {
      return Files.readString(Path.of("src/main/" + path));
   }

   @Test
   void theSlamsOwnBlastDoesNotStunItsCaster() throws Exception {
      UUID venom = UUID.randomUUID();
      UUID other = UUID.randomUUID();
      assertFalse(VenomWeakness.hears(venom, venom), "own cosmetic playback");
      assertTrue(VenomWeakness.hears(other, venom), "another symbiote still recoils");
      assertTrue(VenomWeakness.hears(venom, null), "an external sound still stuns");
      String combat = src("java/dev/baranhan/viltrumitecore/hero/venom/VenomCombat.java");
      assertTrue(combat.contains("VenomWeakness.playOwn(player, at, SoundEvents.GENERIC_EXPLODE"));
      assertFalse(combat.contains("playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE"));
   }

   @Test
   void anAirborneWallHitIsSeenAfterVanillaZeroesTheVelocity() {
      assertTrue(VenomThrows.wallImpact(true, 0.8, 0.0), "flying fast the tick before, stopped by the wall");
      assertTrue(VenomThrows.wallImpact(true, 0.0, 0.5), "still sliding along it");
      assertFalse(VenomThrows.wallImpact(true, 0.001, 0.0), "only brushed it");
      assertFalse(VenomThrows.wallImpact(false, 0.8, 0.8), "no collision");
   }

   @Test
   void spiderLinesAreCutOnlyWhereDamageIsAllowed() throws Exception {
      String spider = src("java/dev/baranhan/viltrumitecore/hero/venom/VenomSpider.java");
      assertTrue(spider.contains("|| !mayCut(venom, p)) {"), "claw/grab sweeps");
      assertTrue(spider.contains("VenomTargets.mayHit(sp, owner)"));
      String skills = src("java/dev/baranhan/viltrumitecore/hero/venom/VenomSkills.java");
      assertTrue(skills.contains("VenomSpider.mayCut(player, sp) && SpiderCompat.cutLine(sp)"), "the tendril-climb bite");
   }

   @Test
   void theTransformationVeinsRunThePostChainAndTheShader() throws Exception {
      String mixin = src("java/dev/baranhan/viltrumitecore/client/mixin/GameRendererDashMixin.java");
      assertTrue(mixin.contains("|| venomHost[1] > 0.005F"));
      String fsh = src("resources/assets/minecraft/shaders/program/dash_impact.fsh");
      assertTrue(fsh.contains("&& safeVenomHost <= 0.001 && safeVenomVeins <= 0.001) {"), "no early exit with veins only");
      assertTrue(fsh.contains("if (safeVenomHost > 0.001 || safeVenomVeins > 0.001) {"), "the block renders veins alone");
   }
}

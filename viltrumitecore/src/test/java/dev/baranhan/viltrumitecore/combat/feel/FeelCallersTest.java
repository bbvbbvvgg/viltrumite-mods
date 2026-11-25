package dev.baranhan.viltrumitecore.combat.feel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The wave1-22 feel API must be reachable from the game: every hero damage seam calls
 * {@code CombatFeel.hit(...)} with a meaningful weight, and the hit-stop has exactly one
 * source per hit (no doubling).
 */
class FeelCallersTest {
   private static final String MAIN = "../viltrumitecore/src/main/";
   private static final String JAVA = MAIN + "java/dev/baranhan/viltrumitecore/";
   private static final String HERO = JAVA + "hero/";

   private static String read(String path) throws Exception {
      return Files.readString(Path.of(path));
   }

   @Test
   void damageMapsToTheWeightLadder() {
      assertEquals(HitWeight.LIGHT, HitWeight.forDamage(0.0F));
      assertEquals(HitWeight.LIGHT, HitWeight.forDamage(4.99F));
      assertEquals(HitWeight.MEDIUM, HitWeight.forDamage(HitWeight.MEDIUM_DAMAGE));
      assertEquals(HitWeight.MEDIUM, HitWeight.forDamage(9.99F));
      assertEquals(HitWeight.HEAVY, HitWeight.forDamage(HitWeight.HEAVY_DAMAGE));
      assertEquals(HitWeight.HEAVY, HitWeight.forDamage(19.99F));
      assertEquals(HitWeight.CRUSHING, HitWeight.forDamage(HitWeight.CRUSHING_DAMAGE));
      assertEquals(HitWeight.CRUSHING, HitWeight.forDamage(200.0F));
      assertEquals(HitWeight.LIGHT, HitWeight.forDamage(-5.0F), "negative damage is LIGHT, never NaN");
   }

   @Test
   void everyHeroSeamCallsCombatFeel() throws Exception {
      assertTrue(read(HERO + "thor/combat/CombatHits.java").contains("CombatFeel.hit("), "Thor");
      assertTrue(read(HERO + "reinhard/ability/Strikes.java").contains("CombatFeel.hit("), "Reinhard");
      assertTrue(read(HERO + "redhulk/RedHulkTargets.java").contains("CombatFeel.hit("), "Red Hulk");
      assertTrue(read(HERO + "captain/CaptainTargets.java").contains("CombatFeel.hit("), "Captain");
      assertTrue(read(HERO + "jinwoo/JinwooCombat.java").contains("CombatFeel.hit("), "Jinwoo");
      assertTrue(read(HERO + "venom/VenomTargets.java").contains("CombatFeel.hit("), "Venom");
      assertTrue(read(HERO + "ghostrider/GhostRiderTargets.java").contains("CombatFeel.hit("), "Ghost Rider");
      assertTrue(read(HERO + "regulus/RegulusCombat.java").contains("CombatFeel.hit("), "Regulus");
   }

   @Test
   void everySeamPassesAWeight() throws Exception {
      assertTrue(read(HERO + "thor/combat/CombatHits.java").contains("CombatFeel.hit(player, target, thorWeight(move, damage), false)"));
      assertTrue(read(HERO + "reinhard/ability/Strikes.java").contains("CombatFeel.hit(player, target, HitWeight.forDamage(damage), false)"));
      assertTrue(read(HERO + "redhulk/RedHulkTargets.java").contains("CombatFeel.hit(attacker, target, HitWeight.forDamage(damage))"));
      assertTrue(read(HERO + "captain/CaptainTargets.java").contains("CombatFeel.hit(attacker, target, HitWeight.forDamage(damage))"));
      assertTrue(read(HERO + "jinwoo/JinwooCombat.java").contains("CombatFeel.hit(player, target, weight)"));
      assertTrue(read(HERO + "venom/VenomTargets.java").contains("CombatFeel.hit(attacker, target, HitWeight.forDamage(damage))"));
      assertTrue(read(HERO + "ghostrider/GhostRiderTargets.java").contains("CombatFeel.hit(attacker, target, HitWeight.forDamage(damage))"));
      assertTrue(read(HERO + "regulus/RegulusCombat.java").contains("CombatFeel.hit(self, target, weight)"));
   }

   @Test
   void ultimatesMapToCrushing() throws Exception {
      assertTrue(read(HERO + "jinwoo/MonarchArise.java").contains("HitWeight.CRUSHING"), "Jinwoo's ultimate");
      assertTrue(read(HERO + "jinwoo/JinwooSkills.java").contains("HitWeight.CRUSHING"), "Jinwoo's rush / charge finishers");
      assertTrue(read(HERO + "regulus/KingdomOfOne.java").contains("HitWeight.CRUSHING"), "Regulus's ultimate");
      assertTrue(read(HERO + "thor/combat/CombatHits.java").contains("ThorMove.WRATH ? HitWeight.CRUSHING"), "Thor's ultimate");
   }

   @Test
   void thorAndReinhardKeepTheirAuthoredHitStopAsTheSingleSource() throws Exception {
      // Their seams pass withHitStop = false, so CombatFeel adds shake / frame / ring only and the
      // authored stop (Thor's launch timing, Reinhard's melee first body) stays the one producer.
      assertTrue(read(HERO + "thor/combat/CombatHits.java").contains("thorWeight(move, damage), false)"));
      assertTrue(read(HERO + "reinhard/ability/Strikes.java").contains("HitWeight.forDamage(damage), false)"));
   }

   @Test
   void combatFeelReplacesTheLegacyDuplicateStops() throws Exception {
      // Where the authored stop only duplicated the seam hit, it is gone so CombatFeel owns the stop.
      assertFalse(read(HERO + "redhulk/RedHulkCombat.java").contains("HeroFx.hitStop(player, player.position(), RedHulkRules.HIT_STOP_HEAVY"));
      assertFalse(read(HERO + "captain/CaptainCombat.java").contains("HeroFx.hitStop(player, player.position(), 3, all)"));
      assertFalse(read(HERO + "captain/CaptainSkills.java").contains("HeroFx.hitStop(player, at, 2, all)"));
      assertFalse(read(HERO + "jinwoo/JinwooCombat.java").contains("HeroFx.hitStop(player, player.position(), JinwooRules.FINISHER_HIT_STOP"));
      assertFalse(read(HERO + "jinwoo/JinwooSkills.java").contains("HeroFx.hitStop("));
   }
}

package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusState;
import org.junit.jupiter.api.Test;

/** Divine targets and absolute invulnerability contract (spec §3.3). */
class DivineTargetsTest {
   @Test
   void verdictOrder() {
      assertSame(DivineTargets.Verdict.DIVINE, DivineTargets.Verdict.of(true, true));
      assertSame(DivineTargets.Verdict.DIVINE, DivineTargets.Verdict.of(true, false));
      assertSame(DivineTargets.Verdict.ABSOLUTE, DivineTargets.Verdict.of(false, true));
   }

   @Test
   void mortalByDefault() {
      assertSame(DivineTargets.Verdict.MORTAL, DivineTargets.Verdict.of(false, false));
   }

   @Test
   void defaultHeroNotDivine() {
      HeroDefinition human = new HumanHero();
      assertFalse(human.isDivine(null));
      assertFalse(human.isAbsolutelyInvulnerable(null));
   }

   @Test
   void regulusAbsoluteOnlyInLion() {
      RegulusState state = new RegulusState();
      assertFalse(RegulusHero.absolutelyInvulnerable(state));
      state.lionActive = true;
      assertTrue(RegulusHero.absolutelyInvulnerable(state));
      assertFalse(RegulusHero.absolutelyInvulnerable(null));
      assertFalse(new RegulusHero().isDivine(null));
   }

   /** Divine damage ignores armor/shield/enchantments/effects but never invulnerability: hero hooks still see it. */
   @Test
   void divineTagsWired() throws java.io.IOException {
      java.nio.file.Path data = java.nio.file.Path.of("src/main/resources/data");
      assertTrue(java.nio.file.Files.readString(data.resolve("viltrumitecore/tags/damage_type/divine.json")).contains("\"viltrumitecore:divine\""));
      assertTrue(java.nio.file.Files.readString(data.resolve("viltrumitecore/damage_type/divine.json")).contains("\"viltrumitecore.divine\""));
      for (String tag : new String[]{"bypasses_armor", "bypasses_shield", "bypasses_enchantments", "bypasses_effects"}) {
         assertTrue(java.nio.file.Files.readString(data.resolve("minecraft/tags/damage_type/" + tag + ".json")).contains("\"viltrumitecore:divine\""), tag);
      }
      // Divine damage never bypasses invulnerability (the tag exists since Stage 4 for the Reid strike only).
      java.nio.file.Path invulnerability = data.resolve("minecraft/tags/damage_type/bypasses_invulnerability.json");
      assertFalse(java.nio.file.Files.exists(invulnerability) && java.nio.file.Files.readString(invulnerability).contains("\"viltrumitecore:divine\""));
      assertTrue(java.nio.file.Files.exists(data.resolve("viltrumitecore/tags/entity_types/divine.json")));
   }
}

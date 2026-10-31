package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.budget.BudgetFaction;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import dev.baranhan.viltrumitecore.hero.budget.BudgetRoster;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Multiverse Collapse, Boss Rush, faction wars and chaos modifiers: pure-data contracts. */
class MultiverseSystemsTest {
   private static final Path CODE = Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/budget/multiverse");
   private static final Path LANG = Path.of("src/main/resources/assets/viltrumite_expansion/lang");
   private static final Pattern KEY = Pattern.compile("\"(multiverse\\.viltrumitecore\\.[a-z_.]*[a-z_])\"");

   @Test
   void eightBossesOnExistingHeroes() {
      assertEquals(8, MultiverseBoss.ALL.size());
      Set<String> ids = new HashSet<>();
      Set<MultiverseBoss.Mechanic> mechanics = EnumSet.noneOf(MultiverseBoss.Mechanic.class);
      for (MultiverseBoss boss : MultiverseBoss.ALL) {
         assertTrue(ids.add(boss.id()), "duplicate boss " + boss.id());
         assertTrue(mechanics.add(boss.mechanic()), "duplicate mechanic " + boss.mechanic());
         assertTrue(BudgetHeroes.isExpansion(HeroId.fromKey(boss.heroKey())), boss.id() + " uses unknown hero " + boss.heroKey());
         assertTrue(boss.health() > 0.0F && boss.health() <= 1024.0F, boss.id() + " health");
         assertTrue(boss.scale() >= 1.0F && boss.scale() <= 2.0F, boss.id() + " scale");
         assertTrue(boss.interval() >= 20, boss.id() + " special interval");
         assertSame(boss, MultiverseBoss.byId(boss.id()));
         assertSame(boss, MultiverseBoss.byIndex(MultiverseBoss.indexOf(boss)));
      }

      assertEquals(EnumSet.allOf(MultiverseBoss.Mechanic.class), mechanics);
      assertEquals(MultiverseBoss.FINAL_ID, MultiverseBoss.finalBoss().id());
      assertTrue(MultiverseBoss.finalBoss().isFinal());
      assertEquals(MultiverseBoss.ALL.size() - 1, MultiverseBoss.ALL.indexOf(MultiverseBoss.finalBoss()), "final boss closes the rush");
      assertFalse(MultiverseBoss.champions().contains(MultiverseBoss.finalBoss()));
      assertEquals(7, MultiverseBoss.champions().size());
   }

   @Test
   void fiveFactionsWithSymmetricRelations() {
      assertEquals(5, BudgetFaction.values().length);
      for (BudgetFaction a : BudgetFaction.values()) {
         assertEquals(BudgetFaction.Relation.ALLY, a.relationTo(a));
         assertTrue(a.hostileTo(null));
         assertSame(a, BudgetFaction.byName(a.name().toLowerCase()));
         for (BudgetFaction b : BudgetFaction.values()) {
            assertEquals(a.relationTo(b), b.relationTo(a), a + " vs " + b);
         }
      }

      assertTrue(BudgetFaction.DEMONS.hostileTo(BudgetFaction.ANIME));
      assertTrue(BudgetFaction.CHAOS.hostileTo(BudgetFaction.DEMONS));
      assertEquals(BudgetFaction.Relation.NEUTRAL, BudgetFaction.ANIME.relationTo(BudgetFaction.COMICS));
      for (BudgetFaction faction : BudgetFaction.values()) {
         long members = Stream.of(BudgetRoster.ALL).filter(spec -> spec.faction() == faction).count();
         assertTrue(members >= MultiverseRules.warGroupSize(Set.of()), faction + " is too small for a war group: " + members);
      }
   }

   @Test
   void legacyHeroesKeepAFactionWithoutHeroBranches() {
      assertSame(BudgetFaction.COMICS, BudgetFaction.ofHeroKey("viltrumite"));
      assertSame(BudgetFaction.COMICS, BudgetFaction.ofHeroKey("homelander"));
      assertSame(BudgetFaction.COMICS, BudgetFaction.ofHeroKey("ironman"));
      assertSame(BudgetFaction.GODS, BudgetFaction.ofHeroKey("thor"));
      assertSame(BudgetFaction.GODS, BudgetFaction.ofHeroKey("reinhard"));
      assertSame(BudgetFaction.CHAOS, BudgetFaction.ofHeroKey("regulus"));
   }

   @Test
   void chaosModifiersParse() {
      StringBuilder unknown = new StringBuilder();
      assertEquals(EnumSet.allOf(ChaosModifier.class), ChaosModifier.parse("all", unknown));
      assertEquals(EnumSet.of(ChaosModifier.DOUBLE_TROUBLE, ChaosModifier.BOSS_MAYHEM),
         ChaosModifier.parse("double_trouble, BOSS_MAYHEM", unknown));
      assertEquals(0, unknown.length());
      assertTrue(ChaosModifier.parse(null, unknown).isEmpty());
      assertTrue(ChaosModifier.parse("not_a_modifier", unknown).isEmpty());
      assertTrue(unknown.toString().contains("not_a_modifier"));
      for (ChaosModifier modifier : ChaosModifier.values()) {
         assertSame(modifier, ChaosModifier.byId(modifier.id()));
      }

      assertNull(ChaosModifier.byId("nope"));
   }

   @Test
   void rulesScaleWithModifiers() {
      Set<ChaosModifier> none = Set.of();
      Set<ChaosModifier> all = EnumSet.allOf(ChaosModifier.class);
      assertEquals(6, MultiverseRules.invaders(none));
      assertEquals(12, MultiverseRules.invaders(Set.of(ChaosModifier.DOUBLE_TROUBLE)));
      assertEquals(3, MultiverseRules.warGroups());
      assertEquals(4, MultiverseRules.warGroupSize(none));
      assertEquals(8, MultiverseRules.warGroupSize(all));
      assertEquals(2, MultiverseRules.championCount(none));
      assertEquals(3, MultiverseRules.championCount(Set.of(ChaosModifier.BOSS_MAYHEM)));
      assertTrue(MultiverseRules.phasePause(all) < MultiverseRules.phasePause(none));
      assertTrue(MultiverseRules.finalCountdown(all) < MultiverseRules.finalCountdown(none));
      assertTrue(MultiverseRules.bossSpecialScale(Set.of(ChaosModifier.BOSS_MAYHEM)) < MultiverseRules.bossSpecialScale(none));
      assertTrue(MultiverseRules.npcCooldownScale(Set.of(ChaosModifier.ULTIMATE_CHAOS)) < MultiverseRules.npcCooldownScale(none));
      assertTrue(MultiverseRules.invasionTimeout(none) > 0 && MultiverseRules.warTimeout(none) > 0);
   }

   @Test
   void championsAreDistinctNonFinalBosses() {
      for (long seed = 0; seed < 64; seed++) {
         for (Set<ChaosModifier> mods : List.of(Set.<ChaosModifier>of(), Set.of(ChaosModifier.BOSS_MAYHEM), Set.of(ChaosModifier.RANDOM_CHAMPIONS),
            (Set<ChaosModifier>) EnumSet.allOf(ChaosModifier.class))) {
            List<MultiverseBoss> champions = MultiverseRules.champions(mods, new Random(seed));
            assertEquals(MultiverseRules.championCount(mods), champions.size());
            assertEquals(champions.size(), new HashSet<>(champions).size(), "duplicate champion");
            assertFalse(champions.contains(MultiverseBoss.finalBoss()));
         }
      }
   }

   @Test
   void everyMultiverseStringIsTranslated() throws IOException {
      JsonObject en = JsonParser.parseString(Files.readString(LANG.resolve("en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
      JsonObject ru = JsonParser.parseString(Files.readString(LANG.resolve("ru_ru.json"), StandardCharsets.UTF_8)).getAsJsonObject();
      Set<String> keys = new TreeSet<>();
      try (Stream<Path> files = Files.list(CODE)) {
         for (Path file : (Iterable<Path>) files::iterator) {
            Matcher m = KEY.matcher(Files.readString(file));
            while (m.find()) {
               keys.add(m.group(1));
            }
         }
      }

      for (MultiverseBoss boss : MultiverseBoss.ALL) {
         keys.add(boss.langKey());
      }

      for (ChaosModifier modifier : ChaosModifier.values()) {
         keys.add(modifier.langKey());
      }

      assertTrue(keys.size() > 40, "scan found only " + keys.size() + " keys");
      for (String key : keys) {
         assertTrue(en.has(key), "en_us missing " + key);
         assertTrue(ru.has(key), "ru_ru missing " + key);
      }
   }

   @Test
   void bossHeroesExistInRoster() {
      for (MultiverseBoss boss : MultiverseBoss.ALL) {
         BudgetHeroSpec spec = BudgetHeroes.spec(HeroId.fromKey(boss.heroKey()));
         assertNotNull(spec, boss.id());
      }
   }

   @Test
   void warAlwaysHasAHostileSide() {
      for (int seed = 0; seed < 500; seed++) {
         List<BudgetFaction> war = MultiverseRules.warFactions(new Random(seed));
         assertEquals(MultiverseRules.warGroups(), war.size());
         assertEquals(war.size(), new HashSet<>(war).size(), "factions are distinct");
         assertTrue(war.stream().anyMatch(f -> !f.heroic()), "seed " + seed + ": three neutral hero factions never fight");
         assertFalse(MultiverseRules.warSettled(EnumSet.copyOf(war)), "a fresh war is not settled");
      }
   }

   @Test
   void warSettlesWhenNoHostilePairStands() {
      assertTrue(MultiverseRules.warSettled(EnumSet.noneOf(BudgetFaction.class)));
      assertTrue(MultiverseRules.warSettled(EnumSet.of(BudgetFaction.DEMONS)));
      assertTrue(MultiverseRules.warSettled(EnumSet.of(BudgetFaction.ANIME, BudgetFaction.GODS)));
      assertFalse(MultiverseRules.warSettled(EnumSet.of(BudgetFaction.ANIME, BudgetFaction.CHAOS)));
      assertFalse(MultiverseRules.warSettled(EnumSet.of(BudgetFaction.DEMONS, BudgetFaction.CHAOS)));
   }

   @Test
   void leashKeepsFightsInsideTheArena() {
      assertTrue(MultiverseRules.LEASH_RADIUS < MultiverseRules.ENGAGE_RADIUS);
      assertTrue(MultiverseRules.LEASH_RADIUS < MultiverseRules.ARENA_RADIUS);
      assertTrue(MultiverseRules.LEASH_RETURN < MultiverseRules.LEASH_RADIUS);
      assertFalse(MultiverseRules.beyondLeash(39.0 * 39.0));
      assertFalse(MultiverseRules.beyondLeash(40.0 * 40.0));
      assertTrue(MultiverseRules.beyondLeash(41.0 * 41.0));
      assertTrue(MultiverseRules.beyondLeash(300.0 * 300.0));
   }
}

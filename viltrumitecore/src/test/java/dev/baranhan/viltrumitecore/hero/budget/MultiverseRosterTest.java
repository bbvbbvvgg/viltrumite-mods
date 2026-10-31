package dev.baranhan.viltrumitecore.hero.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ultimate Multiverse Expansion roster contract: 48 budget heroes, 150 abilities,
 * 30 ultimates, 12 hero scenes; the 30 heroes of the first wave stay registered.
 */
class MultiverseRosterTest {
   private static final Path LANG = Path.of("src/main/resources/assets/viltrumite_expansion/lang");
   private static final Path ICONS = Path.of("src/main/binassets/assets/viltrumitecore/textures/gui/ability/budget");

   static final List<String> FIRST_WAVE = List.of("gojo", "sukuna", "itadori", "megumi", "toji", "geto", "nanami", "mahito",
      "yuta", "choso", "goku", "vegeta", "naruto", "sasuke", "madara", "luffy", "zoro", "sanji", "saitama", "genos", "superman",
      "batman", "flash", "green_lantern", "aquaman", "spider_man", "deadpool", "wolverine", "kratos", "shrek");
   static final List<String> SECOND_WAVE = List.of("aizen", "ichigo", "dante", "vergil", "guts", "sans", "eren", "levi", "denji",
      "makima", "power", "alucard", "rimuru", "ainz", "shadow", "mob", "escanor", "meliodas");

   private static List<String> keys() {
      List<String> keys = new ArrayList<>();
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         keys.add(spec.key());
      }

      return keys;
   }

   private static JsonObject lang(String name) throws IOException {
      return JsonParser.parseString(Files.readString(LANG.resolve(name + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
   }

   @Test
   void rosterHasFortyEightHeroesInWaveOrder() {
      List<String> expected = new ArrayList<>(FIRST_WAVE);
      expected.addAll(SECOND_WAVE);
      assertEquals(48, BudgetHeroes.count());
      assertEquals(expected, keys());
   }

   @Test
   void firstWaveHeroesAreUntouchedAndStillRegistered() {
      for (String key : FIRST_WAVE) {
         HeroId id = HeroId.fromKey(key);
         assertEquals(key, id.key(), "HeroId lost " + key);
         assertTrue(BudgetHeroes.isExpansion(id), key + " is no longer an expansion hero");
      }
   }

   @Test
   void everyNewHeroRegistersThroughTheSharedChassis() {
      List<HeroDefinition> registered = new ArrayList<>();
      BudgetHeroes.registerAll(registered::add);
      assertEquals(48, registered.size());
      Set<HeroId> ids = EnumSet.noneOf(HeroId.class);
      for (HeroDefinition definition : registered) {
         assertTrue(ids.add(definition.id()), "duplicate registration " + definition.id());
         BudgetHeroSpec spec = BudgetHeroes.spec(definition.id());
         assertNotNull(spec);
         assertSame(spec, ((BudgetHero) definition).spec());
         assertEquals(spec.moves().length, definition.heroInputSlots().length);
         for (BudgetMove move : spec.moves()) {
            assertTrue(definition.ownsAbility(spec.abilityId(move)), spec.abilityId(move));
         }
      }

      for (String key : SECOND_WAVE) {
         HeroId id = HeroId.fromKey(key);
         assertEquals(key, id.key(), "HeroId missing for " + key);
         assertTrue(ids.contains(id), key + " not registered");
      }

      assertFalse(BudgetHeroes.isExpansion(HeroId.HUMAN));
   }

   @Test
   void abilityUltimateAndSceneCounts() {
      int abilities = 0;
      int ultimates = 0;
      int newAbilities = 0;
      Set<BudgetScene> scenes = EnumSet.noneOf(BudgetScene.class);
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         abilities += spec.moves().length;
         if (SECOND_WAVE.contains(spec.key())) {
            newAbilities += spec.moves().length;
         }

         for (BudgetMove move : spec.moves()) {
            if (move.type() == BudgetMoveType.E) {
               ultimates++;
            }
         }

         if (spec.hasScene()) {
            assertTrue(scenes.add(spec.scene()), "scene shared by two heroes: " + spec.scene());
         }
      }

      assertEquals(150, abilities);
      assertEquals(60, newAbilities);
      assertEquals(30, ultimates);
      assertEquals(12, scenes.size());
      assertFalse(scenes.contains(BudgetScene.MULTIVERSE_RIFT));
      assertFalse(scenes.contains(BudgetScene.MULTIVERSE_FLASH));
   }

   @Test
   void movesAreWellFormed() {
      Set<String> abilityIds = new HashSet<>();
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         assertTrue(spec.key().matches("[a-z_]{2,32}"), spec.key());
         Set<BudgetMoveType> types = EnumSet.noneOf(BudgetMoveType.class);
         for (BudgetMove move : spec.moves()) {
            assertTrue(types.add(move.type()), spec.key() + " binds two moves to " + move.type());
            assertTrue(move.slug().matches("[a-z_]{2,32}"), move.slug());
            assertTrue(abilityIds.add(spec.abilityId(move)), "duplicate ability " + spec.abilityId(move));
            assertTrue(move.cooldown() >= 10 && move.damage() >= 0.0F && move.damage() <= 60.0F);
            if (move.type() == BudgetMoveType.D) {
               assertTrue(move.buff() != BudgetBuff.NONE, spec.abilityId(move) + " buff without effect");
            }
         }

         if (spec.hasScene()) {
            assertTrue(types.contains(BudgetMoveType.E), spec.key() + " has a scene but no ultimate");
         }
      }

      assertEquals(150, abilityIds.size());
   }

   @Test
   void everyHeroHasAFaction() {
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         assertNotNull(spec.faction(), spec.key());
         assertSame(spec.faction(), BudgetFaction.ofHeroKey(spec.key()));
      }
   }

   @Test
   void localizationCoversEveryHeroAbilityAndSceneInBothLanguages() throws IOException {
      JsonObject en = lang("en_us");
      JsonObject ru = lang("ru_ru");
      assertEquals(en.keySet(), ru.keySet(), "en_us and ru_ru keys differ");
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         assertTrue(en.has(spec.nameKey()), spec.nameKey());
         for (BudgetMove move : spec.moves()) {
            assertTrue(en.has(spec.abilityLangKey(move) + ".name"), spec.abilityLangKey(move) + ".name");
            assertTrue(en.has(spec.abilityLangKey(move) + ".desc"), spec.abilityLangKey(move) + ".desc");
         }
      }

      for (BudgetScene scene : BudgetScene.values()) {
         if (scene != BudgetScene.NONE) {
            assertTrue(en.has(scene.lineKey()), scene.lineKey());
         }
      }

      for (BudgetFaction faction : BudgetFaction.values()) {
         assertTrue(en.has(faction.langKey()), faction.langKey());
      }

      for (var entry : ru.entrySet()) {
         assertFalse(entry.getValue().getAsString().isBlank(), entry.getKey());
      }
   }

   @Test
   void everyAbilityHasAnIcon() {
      int icons = 0;
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         for (BudgetMove move : spec.moves()) {
            Path icon = ICONS.resolve(spec.key()).resolve(move.slug() + ".png.b64");
            assertTrue(Files.isRegularFile(icon), "missing icon " + icon);
            icons++;
         }
      }

      assertEquals(150, icons);
   }
}

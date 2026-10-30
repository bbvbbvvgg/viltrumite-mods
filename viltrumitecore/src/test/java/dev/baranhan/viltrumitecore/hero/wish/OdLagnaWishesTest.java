package dev.baranhan.viltrumitecore.hero.wish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Od Lagna's Wish: the registry, its text in every shipped locale and the scene sounds. */
class OdLagnaWishesTest {
   private static final Path OVERLAY = Path.of("src/main/lang-overlays/od_lagna");
   private static final String[] LOCALES = {"de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "ru_ru", "tr_tr", "zh_cn"};

   @BeforeEach
   void fresh() {
      OdLagnaWishes.clearForTests();
      OdLagnaWishes.registerDefaults();
   }

   @Test
   void everyWishGrantsAKnownHeroAndTheReturnIsLast() {
      List<Wish> all = OdLagnaWishes.all();
      assertTrue(all.size() >= 9, "the spec lists nine wishes");
      Set<String> ids = new HashSet<>();
      Set<String> heroes = new HashSet<>();
      for (Wish wish : all) {
         assertTrue(ids.add(wish.id()), "duplicate " + wish.id());
         HeroId hero = HeroId.fromKey(wish.heroKey());
         assertNotNull(hero, wish.id() + " grants an unknown hero " + wish.heroKey());
         assertEquals(wish.heroKey(), hero.key(), wish.id() + " must use the hero's own key, not an alias");
         heroes.add(wish.heroKey());
         assertEquals(wish, OdLagnaWishes.byId(wish.id()));
      }
      Wish last = all.get(all.size() - 1);
      assertEquals(OdLagnaWishes.RETURN, last.id());
      assertEquals(HeroId.HUMAN.key(), last.heroKey());
      for (String key : new String[]{"dio", "jinwoo", "thor", "sinclair", "ironman", "homelander", "reinhard", "regulus", "human"}) {
         assertTrue(heroes.contains(key), "no wish grants " + key);
      }
      assertNull(OdLagnaWishes.byId("nope"));
      assertNull(OdLagnaWishes.byId(null));
   }

   @Test
   void registrationIsIdempotentAndRejectsDuplicates() {
      int size = OdLagnaWishes.all().size();
      OdLagnaWishes.registerDefaults();
      assertEquals(size, OdLagnaWishes.all().size());
      assertThrows(IllegalStateException.class, () -> OdLagnaWishes.register(new Wish("time", "dio", "time", WishGrant.hero("dio"))));
   }

   @Test
   void badIdsAreRejected() {
      assertThrows(IllegalArgumentException.class, () -> new Wish("Bad Id", "dio", "time", WishGrant.hero("dio")));
      assertThrows(IllegalArgumentException.class, () -> new Wish("x".repeat(Wish.MAX_ID + 1), "dio", "time", WishGrant.hero("dio")));
      assertThrows(IllegalArgumentException.class, () -> new Wish("ok", "dio", "time", null));
   }

   @Test
   void everyLocaleHasTheSameKeysAndEveryWishIsTranslated() throws IOException {
      JsonObject en = read("en_us");
      JsonObject ru = read("ru_ru");
      for (String locale : LOCALES) {
         assertEquals(en.keySet(), read(locale).keySet(), locale + " differs from en_us");
      }
      for (Wish wish : OdLagnaWishes.all()) {
         for (String key : new String[]{wish.textKey(), wish.echoKey()}) {
            assertTrue(en.has(key), "en_us misses " + key);
            assertTrue(ru.has(key), "ru_ru misses " + key);
            assertFalse(ru.get(key).getAsString().equals(en.get(key).getAsString()), "ru_ru is not translated: " + key);
         }
      }
      // spec wording
      assertEquals("чего ты желаешь?", ru.get("gui.viltrumitecore.wish.title").getAsString());
      assertEquals("исполнить желание", ru.get("gui.viltrumitecore.wish.confirm").getAsString());
      assertEquals("твоё желание услышано.", ru.get("wish.viltrumitecore.granted").getAsString());
      assertEquals("я желаю вернуть свою прежнюю жизнь", ru.get("wish.viltrumitecore.return").getAsString().toLowerCase());
      assertEquals("Желания Од Лагуны", ru.get("itemgroup.viltrumitecore.od_lagna_tab").getAsString());
   }

   @Test
   void everyKeyTheCodeUsesExists() throws IOException {
      JsonObject en = read("en_us");
      List<Path> sources = new ArrayList<>();
      for (String dir : new String[]{"src/main/java/dev/baranhan/viltrumitecore/hero/wish", "src/main/java/dev/baranhan/viltrumitecore/client/wish",
         "src/main/java/dev/baranhan/viltrumitecore/item"}) {
         try (var walk = Files.walk(Path.of(dir))) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
         }
      }
      Pattern key = Pattern.compile("\"((?:wish|gui\\.viltrumitecore\\.wish|item\\.viltrumitecore\\.od_lagna_wish|itemgroup\\.viltrumitecore\\.od_lagna)[a-z0-9_.]*)\"");
      int found = 0;
      for (Path source : sources) {
         Matcher m = key.matcher(Files.readString(source));
         while (m.find()) {
            String k = m.group(1);
            if (k.endsWith(".") || k.equals("wish")) {
               continue;
            }
            found++;
            assertTrue(en.has(k), source.getFileName() + " uses a missing key " + k);
         }
      }
      assertTrue(found > 10, "the key scan found nothing: " + found);
   }

   @Test
   void everySceneSoundHasAudioAndASubtitle() throws IOException {
      JsonObject sounds = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/od_lagna/sounds.json"))).getAsJsonObject();
      JsonObject en = read("en_us");
      for (String name : new String[]{"call", "granted", "open", "select", "confirm", "return"}) {
         assertTrue(sounds.has(name), "sounds.json misses " + name);
         JsonObject event = sounds.getAsJsonObject(name);
         assertTrue(en.has(event.get("subtitle").getAsString()), "no subtitle text for " + name);
         assertTrue(Files.isRegularFile(Path.of("src/main/binassets/assets/od_lagna/sounds/" + name + ".ogg.b64")), "no audio for " + name);
      }
   }

   @Test
   void timelineFitsTheTwoToFourSecondScene() {
      assertTrue(WishTimeline.LENGTH >= 40 && WishTimeline.LENGTH <= 80, "2-4 s at 20 tps");
      assertTrue(0 < WishTimeline.CALL_END && WishTimeline.CALL_END < WishTimeline.SWAP && WishTimeline.SWAP < WishTimeline.FULFIL_END
         && WishTimeline.FULFIL_END < WishTimeline.LENGTH);
      assertEquals(WishTimeline.Phase.CALL, WishTimeline.phase(0));
      assertEquals(WishTimeline.Phase.FULFIL, WishTimeline.phase(WishTimeline.CALL_END));
      assertEquals(WishTimeline.Phase.FULFIL, WishTimeline.phase(WishTimeline.SWAP));
      assertEquals(WishTimeline.Phase.RESOLVE, WishTimeline.phase(WishTimeline.FULFIL_END));
      assertEquals(WishTimeline.Phase.DONE, WishTimeline.phase(WishTimeline.LENGTH));
      float last = 0;
      float peak = 0;
      for (float t = 0; t <= WishTimeline.LENGTH; t += 0.25F) {
         float e = WishTimeline.energy(t);
         float w = WishTimeline.cameraWeight(t);
         float a = WishTimeline.titleAlpha(t);
         assertTrue(e >= 0 && e <= 1 && w >= 0 && w <= 1 && a >= 0 && a <= 1, "out of range at " + t);
         assertTrue(Math.abs(e - last) < 0.06F, "energy jumps at " + t);
         last = e;
         peak = Math.max(peak, e);
      }
      assertEquals(1.0F, WishTimeline.energy(WishTimeline.SWAP), 1.0E-4F);
      assertEquals(1.0F, peak, 1.0E-4F);
      assertEquals(0.0F, WishTimeline.cameraWeight(0), 1.0E-6F);
      assertEquals(0.0F, WishTimeline.cameraWeight(WishTimeline.LENGTH), 1.0E-6F);
      assertEquals(1.0F, WishTimeline.cameraWeight(WishTimeline.SWAP), 1.0E-6F);
      assertTrue(WishTimeline.titleAlpha(WishTimeline.LENGTH + 4) > 0.9F, "the title outlives the camera");
      assertEquals(0.0F, WishTimeline.titleAlpha(WishTimeline.LENGTH + WishTimeline.TITLE_TAIL + 1));
   }

   private static JsonObject read(String locale) throws IOException {
      return JsonParser.parseString(Files.readString(OVERLAY.resolve(locale + ".json"))).getAsJsonObject();
   }
}

package dev.baranhan.viltrumitecore.client.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.codex.CodexEntry.Category;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Encyclopedia layout at every GUI size, the article table and its texts in every locale. */
class CodexClientMathTest {
   private static final int[][] SIZES = {{320, 240}, {300, 200}, {427, 240}, {480, 270}, {640, 360}, {683, 384}, {854, 480}, {960, 540}, {1920, 1080},
      {240, 180}};
   private static final Path OVERLAYS = Path.of("src/main/lang-overlays/codex");

   @Test
   void layoutFitsEveryScreen() {
      for (int[] size : SIZES) {
         CodexLayout l = new CodexLayout(size[0], size[1]);
         String at = size[0] + "x" + size[1];
         assertTrue(l.backX + CodexLayout.BACK_W <= size[0] / 2, "back button reaches the title " + at);
         assertTrue(l.tabsY >= l.backY + CodexLayout.BACK_H, "tabs overlap the back button " + at);
         assertTrue(l.tabX(0) >= 0 && l.tabX(CodexLayout.TABS - 1) + l.tabW <= size[0], "tabs off screen " + at);
         assertTrue(l.tabW >= 50, "tabs too narrow " + at);
         assertTrue(l.bodyTop >= l.tabsY + CodexLayout.TAB_H, "body overlaps tabs " + at);
         assertTrue(l.bodyBottom <= l.footerY && l.footerY + 9 <= size[1], "footer off screen " + at);
         assertTrue(l.listX >= 0 && l.listX + l.listW < l.detailX, "list overlaps the article " + at);
         assertTrue(l.detailX + l.detailW <= size[0], "article off screen " + at);
         assertTrue(l.portraitW + 60 <= l.detailW, "no room beside the portrait " + at);
         assertTrue(l.textTop < l.bodyBottom && l.textHeight() >= 20, "no room for text " + at);
         assertTrue(l.bodyHeight() >= 2 * CodexLayout.PITCH, "list too short " + at);
         // list hit testing and scrolling
         int n = 16;
         float max = l.maxListScroll(n);
         assertTrue(l.rowY(n - 1, max) + CodexLayout.ROW <= l.bodyBottom + 0.01F, "last row unreachable " + at);
         assertEquals(0, l.rowAt(l.listX + 2, l.bodyTop + 1, 0, n), at);
         assertEquals(-1, l.rowAt(l.detailX + 2, l.bodyTop + 1, 0, n), at);
         assertEquals(-1, l.rowAt(l.listX + 2, l.bodyTop + CodexLayout.ROW, 0, n), "gap between rows " + at);
         float s = l.scrollToShow(n - 1, 0, n);
         assertTrue(l.rowY(n - 1, s) + CodexLayout.ROW <= l.bodyBottom + 0.01F && l.rowY(n - 1, s) >= l.bodyTop - 0.01F, at);
         for (int t = 0; t < CodexLayout.TABS; t++) {
            assertEquals(t, l.tabAt(l.tabX(t) + 1, l.tabsY + 1), at);
         }
         assertTrue(l.overBack(l.backX + 1, l.backY + 1), at);
      }
   }

   @Test
   void freeTextParticipantsLinkEveryNamedArticle() {
      // participant strings in the style of docs/contextual-interactions.json (schema 1, free text)
      assertEquals(List.of("thor"), ids("Thor (Mjolnir in hand, striking)"));
      assertEquals(List.of("ironman", "iron_legion"), ids("Iron Man or an Iron Legion suit (repulsor bolt)"));
      assertEquals(List.of("thor", "avenger_thor"), ids("Thor or the summoned NPC Thor (lightning)"));
      assertEquals(List.of("avenger_hulk", "ironman", "homelander"), ids("NPC Hulk, Hulkbuster or a Viltrumite (heavy fist)"));
      assertEquals(List.of("chitauri_soldier"), ids("Chitauri riders"));
      assertEquals(List.of("iron_legion", "shadow_army"), ids("Hero summons (Iron Legion suits, Avengers, Jinwoo's shadows)"));
      assertEquals(List.of("meteor_fall"), ids("Meteor (world event, or thrown by a hero)"));
      assertEquals(List.of(), ids("Any lightning attacker"));
      assertEquals(List.of(), ids(null));
   }

   private static List<String> ids(String raw) {
      return CodexEntries.resolveAll(raw).stream().map(CodexEntry::id).toList();
   }

   @Test
   void articleTable() {
      List<CodexEntry> heroes = CodexEntries.of(Category.HEROES);
      List<CodexEntry> npcs = CodexEntries.of(Category.NPCS);
      List<CodexEntry> events = CodexEntries.of(Category.EVENTS);
      assertEquals(11, heroes.size());
      assertEquals(16, npcs.size());
      assertEquals(6, events.size());
      assertEquals(3, npcs.stream().filter(e -> "boss".equals(e.kind())).count(), "Conquest, the Leviathan and Thanos are the bosses");
      // every playable hero has exactly one article (Viltrumite is the legacy id of Homelander)
      Set<HeroId> covered = new HashSet<>();
      for (CodexEntry e : heroes) {
         HeroId id = HeroId.fromKey(e.id());
         assertNotNull(id, "hero article without a hero: " + e.id());
         assertEquals(e.id(), id.key());
         covered.add(id);
      }
      for (HeroId id : HeroId.values()) {
         assertTrue(covered.contains(id) || HeroId.fromKey(id.key()) != id, "no article for hero " + id);
      }
      Map<String, String> aliasOwner = new HashMap<>();
      for (CodexEntry e : CodexEntries.all()) {
         for (String rel : e.relations()) {
            assertNotNull(CodexEntries.get(rel), e.id() + " links to unknown " + rel);
            assertTrue(!rel.equals(e.id()), e.id() + " links to itself");
         }
         for (String alias : e.aliases()) {
            String prev = aliasOwner.put(alias, e.id());
            assertTrue(prev == null, "alias " + alias + " used by " + prev + " and " + e.id());
            assertEquals(e, CodexEntries.byAlias(alias));
         }
      }
      assertEquals("thanos", CodexEntries.byAlias("viltrumitecore:thanos").id());
      assertEquals("avenger_black_widow", CodexEntries.byAlias("Black Widow").id());
      // participant texts of the schema-1 catalog
      assertEquals("captain", CodexEntries.byAlias("Captain America (raised shield)").id());
      assertEquals("ironman", CodexEntries.byAlias("Iron Man (flight suit worn)").id());
      assertEquals("iron_legion", CodexEntries.byAlias("Iron Legion suit (summon)").id());
      assertEquals("meteor_fall", CodexEntries.byAlias("Meteor (world event)").id());
   }

   private static JsonObject overlay(String locale) throws IOException {
      return JsonParser.parseString(Files.readString(OVERLAYS.resolve(locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
   }

   @Test
   void everyArticleHasItsTextsInRussianAndEnglish() throws IOException {
      for (String locale : List.of("ru_ru", "en_us")) {
         JsonObject lang = overlay(locale);
         for (CodexEntry e : CodexEntries.all()) {
            assertTrue(lang.has(e.nameKey()) && lang.has(e.roleKey()), locale + " misses name/role of " + e.id());
            for (String section : e.sections()) {
               if (!section.startsWith("@")) {
                  String key = CodexEntry.key(e.id(), section);
                  assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), locale + " misses " + key);
                  assertTrue(lang.has(CodexEntry.gui("section." + section)), locale + " misses the header of " + section);
               }
            }
         }
      }
      assertTrue(overlay("ru_ru").get(CodexEntry.gui("title")).getAsString().matches(".*[А-Яа-я].*"), "Russian is the primary language");
   }

   @Test
   void everyLocaleHasTheSameKeys() throws IOException {
      Set<String> en = overlay("en_us").keySet();
      try (Stream<Path> files = Files.list(OVERLAYS)) {
         for (Path p : files.toList()) {
            String locale = p.getFileName().toString().replace(".json", "");
            assertEquals(new TreeSet<>(en), new TreeSet<>(overlay(locale).keySet()), "keys of " + locale);
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/viltrumitecore/lang/" + locale + ".json")), "no base lang " + locale);
         }
      }
   }

   /** Every gui key the screen, the wish button and the harness use exists. */
   @Test
   void guiKeysExist() throws IOException {
      JsonObject en = overlay("en_us");
      Pattern gui = Pattern.compile("CodexEntry\\.gui\\(\"([a-z_.]+)\"\\)");
      Pattern section = Pattern.compile("b\\.header\\(\"([a-z_]+)\"\\)|this\\.section\\(b, \"([a-z_]+)\"");
      String screen = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/codex/CodexScreen.java"));
      Matcher m = gui.matcher(screen);
      int found = 0;
      while (m.find()) {
         assertTrue(en.has(CodexEntry.gui(m.group(1))), "missing " + m.group(1));
         found++;
      }
      Matcher s = section.matcher(screen);
      while (s.find()) {
         String key = s.group(1) != null ? s.group(1) : s.group(2);
         assertTrue(en.has(CodexEntry.gui("section." + key)), "missing section header " + key);
      }
      for (Category c : Category.values()) {
         assertTrue(en.has(CodexEntry.gui("tab." + c.key)), "missing tab " + c.key);
      }
      for (String kind : List.of("boss", "enemy", "ally", "neutral", "cosmic", "event")) {
         assertTrue(en.has(CodexEntry.gui("kind." + kind)), kind);
      }
      assertTrue(en.has("codex.viltrumitecore.gui.open"), "wish button tooltip");
      assertTrue(found > 20, "the pattern still matches the screen source");
   }
}

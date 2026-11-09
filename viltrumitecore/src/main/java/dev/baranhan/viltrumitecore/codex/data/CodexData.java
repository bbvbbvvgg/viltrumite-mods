package dev.baranhan.viltrumitecore.codex.data;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * The Codex 2.0 data: entries ({@code assets/viltrumitecore/codex2/entries.json}) and hero cards
 * ({@code codex2/heroes.json}), shipped in the mod jar so server and client always read the same file.
 * Loaded once on first use; {@code /codex reload} re-reads it. {@link #load} is pure for tests.
 */
public final class CodexData {
   public static final String ROOT = "/assets/viltrumitecore/codex2/";
   public static final String ENTRIES = "entries.json";
   public static final String HEROES = "heroes.json";
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Comparator<CodexEntryDef> ORDER = Comparator.comparingInt((CodexEntryDef e) -> e.category().ordinal())
      .thenComparingInt(CodexEntryDef::order).thenComparing(CodexEntryDef::id);
   private static volatile CodexData instance;

   private final Map<String, CodexEntryDef> entries;
   private final Map<String, HeroCard> cards;
   private final List<String> errors;

   private CodexData(List<CodexEntryDef> entries, List<HeroCard> cards, List<String> errors) {
      Map<String, CodexEntryDef> byId = new LinkedHashMap<>();
      entries.stream().sorted(ORDER).forEach(e -> byId.put(e.id(), e));
      Map<String, HeroCard> byHero = new LinkedHashMap<>();
      cards.forEach(c -> byHero.put(c.hero(), c));
      List<String> problems = new ArrayList<>(errors);
      for (CodexEntryDef e : byId.values()) {
         for (String r : e.relations()) {
            if (!byId.containsKey(r)) {
               problems.add(e.id() + ": unknown relation " + r);
            }
         }
      }
      this.entries = Collections.unmodifiableMap(byId);
      this.cards = Collections.unmodifiableMap(byHero);
      this.errors = List.copyOf(problems);
   }

   public static CodexData empty() {
      return new CodexData(List.of(), List.of(), List.of());
   }

   public static CodexData load(Reader entries, Reader heroes, String source) {
      List<String> errors = new ArrayList<>();
      List<CodexEntryDef> e = CodexDataParser.parseEntries(entries, source + ENTRIES, errors);
      List<HeroCard> c = CodexDataParser.parseCards(heroes, source + HEROES, errors);
      return new CodexData(e, c, errors);
   }

   public static CodexData get() {
      CodexData data = instance;
      if (data == null) {
         synchronized (CodexData.class) {
            if (instance == null) {
               instance = loadBuiltin();
            }
            data = instance;
         }
      }
      return data;
   }

   public static synchronized CodexData reload() {
      instance = loadBuiltin();
      return instance;
   }

   private static CodexData loadBuiltin() {
      try (Reader entries = open(ENTRIES); Reader heroes = open(HEROES)) {
         CodexData data = load(entries, heroes, ROOT);
         LOGGER.info("[codex2] {} entries, {} hero cards, {} problems", data.entries.size(), data.cards.size(), data.errors.size());
         data.errors.forEach(p -> LOGGER.warn("[codex2] {}", p));
         return data;
      } catch (Exception e) {
         LOGGER.warn("[codex2] data unreadable: {}", e.toString());
         return new CodexData(List.of(), List.of(), List.of("unreadable: " + e));
      }
   }

   private static Reader open(String name) {
      InputStream in = CodexData.class.getResourceAsStream(ROOT + name);
      if (in == null) {
         LOGGER.warn("[codex2] {} missing in the jar", ROOT + name);
         return new StringReader("{}");
      }
      return new InputStreamReader(in, StandardCharsets.UTF_8);
   }

   /** All entries, ordered by category, then order, then id. */
   public Map<String, CodexEntryDef> entries() {
      return this.entries;
   }

   @Nullable
   public CodexEntryDef entry(@Nullable String id) {
      return id == null ? null : this.entries.get(id.trim().toLowerCase(Locale.ROOT));
   }

   public List<CodexEntryDef> byCategory(CodexCategory category) {
      return this.entries.values().stream().filter(e -> e.category() == category).toList();
   }

   public List<CodexEntryDef> withCondition(UnlockCondition.Type type) {
      return this.entries.values().stream().filter(e -> e.hasCondition(type)).toList();
   }

   public Map<String, HeroCard> cards() {
      return this.cards;
   }

   /** The hero's card, or a neutral fallback so an uncarded hero still shows on the screen. */
   public HeroCard card(@Nullable String hero) {
      String key = hero == null ? "human" : hero.trim().toLowerCase(Locale.ROOT);
      HeroCard card = this.cards.get(key);
      return card != null ? card : HeroCard.fallback(key);
   }

   public List<String> errors() {
      return this.errors;
   }
}

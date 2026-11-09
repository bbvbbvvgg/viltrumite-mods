package dev.baranhan.viltrumitecore.codex.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Reads the Codex 2.0 data files (pure, unit-tested). A broken entry is skipped with one line in
 * {@code errors}; the rest still loads, so one typo never empties the encyclopedia.
 *
 * <pre>
 * entries.json: {"entries": [{"id": "boss.thanos", "category": "bosses", "accent": "E0B040", "order": 40,
 *   "visual": {"type": "entity", "ref": "viltrumitecore:thanos"}, "sections": ["lore", "tactics"],
 *   "unlock": [{"type": "entity_seen", "target": "viltrumitecore:thanos"}], "relations": ["event.thanos_snap"]}]}
 * heroes.json:  {"heroes": [{"hero": "thor", "role": "brawler", "styles": ["melee", "area"], "difficulty": 3,
 *   "accent": "7FD8FF", "tutorial": [{"text": "heroselect.viltrumitecore.tutorial.thor.1", "key": "key.viltrumitecore.ability_1"}]}]}
 * </pre>
 */
public final class CodexDataParser {
   public static final Pattern ID = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");
   public static final Pattern TARGET = Pattern.compile("[a-z0-9_.:\\-/]{1,64}");
   public static final Pattern SECTION = Pattern.compile("[a-z0-9_]{1,32}");
   public static final Pattern KEY = Pattern.compile("[a-z0-9_.]{1,96}");
   public static final int MAX_SECTIONS = 8;
   public static final int MAX_CONDITIONS = 8;

   private CodexDataParser() {
   }

   public static List<CodexEntryDef> parseEntries(Reader reader, String source, List<String> errors) {
      List<CodexEntryDef> out = new ArrayList<>();
      JsonArray array = rootArray(reader, source, "entries", errors);
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < array.size(); i++) {
         String where = source + " entries[" + i + "]";
         try {
            CodexEntryDef entry = entry(array.get(i).getAsJsonObject(), where);
            if (!seen.add(entry.id())) {
               errors.add(where + ": duplicate id " + entry.id());
               continue;
            }
            out.add(entry);
         } catch (RuntimeException e) {
            errors.add(where + ": " + e.getMessage());
         }
      }
      return out;
   }

   public static List<HeroCard> parseCards(Reader reader, String source, List<String> errors) {
      List<HeroCard> out = new ArrayList<>();
      JsonArray array = rootArray(reader, source, "heroes", errors);
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < array.size(); i++) {
         String where = source + " heroes[" + i + "]";
         try {
            HeroCard card = card(array.get(i).getAsJsonObject());
            if (!seen.add(card.hero())) {
               errors.add(where + ": duplicate hero " + card.hero());
               continue;
            }
            out.add(card);
         } catch (RuntimeException e) {
            errors.add(where + ": " + e.getMessage());
         }
      }
      return out;
   }

   static CodexEntryDef entry(JsonObject o, String where) {
      String id = required(o, "id");
      if (!ID.matcher(id).matches() || id.length() > 64) {
         throw new IllegalArgumentException("bad id '" + id + "'");
      }
      CodexCategory category = CodexCategory.byKey(required(o, "category"));
      if (category == null) {
         throw new IllegalArgumentException("unknown category '" + o.get("category").getAsString() + "'");
      }
      int accent = color(o, "accent", 0xE8E8E8);
      int order = o.has("order") ? o.get("order").getAsInt() : 1000;
      CodexEntryDef.Visual visual = CodexEntryDef.Visual.glyph();
      if (o.has("visual")) {
         JsonObject v = o.getAsJsonObject("visual");
         String type = required(v, "type").toLowerCase(Locale.ROOT);
         if (!CodexEntryDef.Visual.TYPES.contains(type)) {
            throw new IllegalArgumentException("unknown visual type '" + type + "'");
         }
         String ref = v.has("ref") ? v.get("ref").getAsString().trim() : "";
         if ((type.equals("hero") || type.equals("skin") || type.equals("entity")) && ref.isEmpty()) {
            throw new IllegalArgumentException("visual " + type + " needs a ref");
         }
         visual = new CodexEntryDef.Visual(type, ref, v.has("slim") && v.get("slim").getAsBoolean());
      }
      List<String> sections = new ArrayList<>();
      for (JsonElement e : array(o, "sections")) {
         String s = e.getAsString().trim();
         if (!SECTION.matcher(s).matches()) {
            throw new IllegalArgumentException("bad section '" + s + "'");
         }
         if (!sections.contains(s)) {
            sections.add(s);
         }
      }
      if (sections.isEmpty() || sections.size() > MAX_SECTIONS) {
         throw new IllegalArgumentException("needs 1.." + MAX_SECTIONS + " sections");
      }
      List<UnlockCondition> unlock = new ArrayList<>();
      for (JsonElement e : array(o, "unlock")) {
         JsonObject c = e.getAsJsonObject();
         UnlockCondition.Type type = UnlockCondition.Type.byKey(required(c, "type"));
         if (type == null) {
            throw new IllegalArgumentException("unknown unlock type '" + c.get("type").getAsString() + "'");
         }
         String target = c.has("target") ? c.get("target").getAsString().trim().toLowerCase(Locale.ROOT) : "";
         if (type.needsTarget && !TARGET.matcher(target).matches()) {
            throw new IllegalArgumentException("unlock " + type.key + " needs a target");
         }
         unlock.add(new UnlockCondition(type, type.needsTarget ? target : ""));
      }
      if (unlock.size() > MAX_CONDITIONS) {
         throw new IllegalArgumentException("at most " + MAX_CONDITIONS + " unlock conditions");
      }
      List<String> relations = new ArrayList<>();
      for (JsonElement e : array(o, "relations")) {
         String r = e.getAsString().trim();
         if (ID.matcher(r).matches() && !r.equals(id)) {
            relations.add(r);
         }
      }
      return new CodexEntryDef(id, category, accent, order, visual, sections, unlock, relations);
   }

   static HeroCard card(JsonObject o) {
      String hero = required(o, "hero").toLowerCase(Locale.ROOT);
      if (!SECTION.matcher(hero).matches()) {
         throw new IllegalArgumentException("bad hero '" + hero + "'");
      }
      String role = required(o, "role").toLowerCase(Locale.ROOT);
      if (!HeroCard.ROLES.contains(role)) {
         throw new IllegalArgumentException("unknown role '" + role + "'");
      }
      List<String> styles = new ArrayList<>();
      for (JsonElement e : array(o, "styles")) {
         String s = e.getAsString().trim().toLowerCase(Locale.ROOT);
         if (!HeroCard.STYLES.contains(s)) {
            throw new IllegalArgumentException("unknown style '" + s + "'");
         }
         if (!styles.contains(s)) {
            styles.add(s);
         }
      }
      if (styles.size() > HeroCard.MAX_STYLES) {
         throw new IllegalArgumentException("at most " + HeroCard.MAX_STYLES + " styles");
      }
      int difficulty = o.has("difficulty") ? o.get("difficulty").getAsInt() : 2;
      if (difficulty < HeroCard.MIN_DIFFICULTY || difficulty > HeroCard.MAX_DIFFICULTY) {
         throw new IllegalArgumentException("difficulty must be " + HeroCard.MIN_DIFFICULTY + ".." + HeroCard.MAX_DIFFICULTY);
      }
      int accent = color(o, "accent", HeroCard.DEFAULT_ACCENT);
      List<HeroCard.TutorialStep> steps = new ArrayList<>();
      for (JsonElement e : array(o, "tutorial")) {
         JsonObject s = e.getAsJsonObject();
         String text = required(s, "text");
         String key = s.has("key") ? s.get("key").getAsString().trim() : "";
         if (!KEY.matcher(text).matches() || !key.isEmpty() && !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("bad tutorial step '" + text + "'");
         }
         steps.add(new HeroCard.TutorialStep(text, key));
      }
      if (!steps.isEmpty() && steps.size() != HeroCard.TUTORIAL_STEPS) {
         throw new IllegalArgumentException("a tutorial has exactly " + HeroCard.TUTORIAL_STEPS + " steps");
      }
      return new HeroCard(hero, role, styles, difficulty, accent, steps);
   }

   private static JsonArray rootArray(Reader reader, String source, String field, List<String> errors) {
      try {
         JsonElement root = JsonParser.parseReader(reader);
         if (root.isJsonObject() && root.getAsJsonObject().has(field) && root.getAsJsonObject().get(field).isJsonArray()) {
            return root.getAsJsonObject().getAsJsonArray(field);
         }
         errors.add(source + ": no \"" + field + "\" array");
      } catch (RuntimeException e) {
         errors.add(source + ": unreadable json: " + e.getMessage());
      }
      return new JsonArray();
   }

   private static String required(JsonObject o, String field) {
      if (!o.has(field) || !o.get(field).isJsonPrimitive() || o.get(field).getAsString().isBlank()) {
         throw new IllegalArgumentException("missing " + field);
      }
      return o.get(field).getAsString().trim();
   }

   private static JsonArray array(JsonObject o, String field) {
      return o.has(field) && o.get(field).isJsonArray() ? o.getAsJsonArray(field) : new JsonArray();
   }

   /** {@code "FF2A2A"} or {@code "#FF2A2A"}; the fallback when the field is absent. */
   static int color(JsonObject o, String field, int fallback) {
      if (!o.has(field)) {
         return fallback;
      }
      return parseColor(o.get(field).getAsString());
   }

   static int parseColor(@Nullable String hex) {
      String h = hex == null ? "" : hex.trim();
      if (h.startsWith("#")) {
         h = h.substring(1);
      }
      if (!h.matches("[0-9a-fA-F]{6}")) {
         throw new IllegalArgumentException("bad colour '" + hex + "'");
      }
      return Integer.parseInt(h, 16);
   }
}

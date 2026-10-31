package dev.baranhan.viltrumitecore.codex.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Reads docs/contextual-interactions.json (shipped as assets/viltrumitecore/codex/contextual_interactions.json).
 * The catalog is written by the contextual-interactions branch; this reader follows its contract loosely on
 * purpose, so new optional fields or a renamed synonym never break the encyclopedia:
 * <ul>
 * <li>top level: an array of entries, or an object with {@code interactions} (or {@code entries}/{@code items});</li>
 * <li>entry: {@code id} (required); {@code name}/{@code title}; {@code participants} (strings or objects with
 * {@code id}, {@code name}, {@code kind}/{@code type}/{@code role}); {@code description}/{@code summary};
 * {@code lore}; {@code conditions}/{@code trigger}/{@code activation} (string, list or object);
 * {@code mechanic}/{@code mechanics}/{@code effect}; {@code visual}/{@code visuals}/{@code vfx}; {@code hint};
 * {@code secret}/{@code hidden} or {@code visibility: "secret"}; {@code category}; {@code events}/{@code related}.</li>
 * <li>any text: a string (literal or lang key) or a per-locale object ({@code ru_ru}, {@code en_us}, {@code ru}, {@code en}).</li>
 * </ul>
 * Entries without an id, or with a duplicate id, are skipped and reported in {@link InteractionCatalog#problems()}.
 */
public final class CatalogParser {
   private static final String[] ID = {"id", "key", "slug"};
   private static final String[] NAME = {"name", "title", "displayName", "display_name", "nameKey", "name_key"};
   private static final String[] PARTICIPANTS = {"participants", "actors", "members", "characters", "heroes"};
   private static final String[] DESCRIPTION = {"description", "summary", "desc", "short"};
   private static final String[] LORE = {"lore", "flavor", "flavour", "story"};
   private static final String[] CONDITIONS = {"conditions", "condition", "trigger", "triggers", "activation", "requirements", "how"};
   private static final String[] MECHANIC = {"mechanic", "mechanics", "effect", "effects", "outcome", "result"};
   private static final String[] VISUALS = {"visual", "visuals", "vfx", "fx", "presentation"};
   private static final String[] HINT = {"hint", "teaser", "clue"};
   private static final String[] CATEGORY = {"category", "type", "group"};
   private static final String[] RELATED = {"events", "worldEvents", "world_events", "related", "links", "relatedEntries"};
   public static final int MAX_ENTRIES = 1024;

   private CatalogParser() {
   }

   public static InteractionCatalog parse(Reader reader, @Nullable String source, boolean testData) {
      JsonElement root;
      try {
         root = JsonParser.parseReader(reader);
      } catch (JsonParseException | IllegalStateException e) {
         return new InteractionCatalog(InteractionCatalog.Status.INVALID, List.of(), List.of("not JSON: " + e.getMessage()), source, testData);
      }
      return parse(root, source, testData);
   }

   public static InteractionCatalog parse(String json, @Nullable String source, boolean testData) {
      try (Reader r = new java.io.StringReader(json)) {
         return parse(r, source, testData);
      } catch (IOException e) {
         return new InteractionCatalog(InteractionCatalog.Status.INVALID, List.of(), List.of(e.toString()), source, testData);
      }
   }

   static InteractionCatalog parse(JsonElement root, @Nullable String source, boolean testData) {
      List<String> problems = new ArrayList<>();
      JsonArray array = null;
      if (root != null && root.isJsonArray()) {
         array = root.getAsJsonArray();
      } else if (root != null && root.isJsonObject()) {
         for (String key : new String[]{"interactions", "entries", "items", "catalog"}) {
            JsonElement e = root.getAsJsonObject().get(key);
            if (e != null && e.isJsonArray()) {
               array = e.getAsJsonArray();
               break;
            }
            if (e != null && e.isJsonObject()) {
               // {"interactions": {"<id>": {...}}}
               array = new JsonArray();
               for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
                  if (m.getValue().isJsonObject()) {
                     JsonObject copy = m.getValue().getAsJsonObject().deepCopy();
                     if (!copy.has("id")) {
                        copy.addProperty("id", m.getKey());
                     }
                     array.add(copy);
                  }
               }
               break;
            }
         }
      }
      if (array == null) {
         return new InteractionCatalog(InteractionCatalog.Status.INVALID, List.of(), List.of("no interaction list at the top level"), source, testData);
      }
      List<CatalogInteraction> out = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      int index = 0;
      for (JsonElement e : array) {
         index++;
         if (out.size() >= MAX_ENTRIES) {
            problems.add("more than " + MAX_ENTRIES + " entries: the rest is ignored");
            break;
         }
         if (!e.isJsonObject()) {
            problems.add("entry " + index + " is not an object");
            continue;
         }
         JsonObject o = e.getAsJsonObject();
         String id = slug(string(o, ID));
         if (id == null || id.isEmpty()) {
            problems.add("entry " + index + " has no id");
            continue;
         }
         if (!seen.add(id)) {
            problems.add("duplicate id " + id);
            continue;
         }
         out.add(new CatalogInteraction(
            id,
            text(o, NAME),
            participants(o),
            text(o, DESCRIPTION),
            text(o, LORE),
            texts(o, CONDITIONS),
            text(o, MECHANIC),
            text(o, VISUALS),
            text(o, HINT),
            secret(o),
            orEmpty(slug(string(o, CATEGORY))),
            related(o)
         ));
      }
      return new InteractionCatalog(InteractionCatalog.Status.LOADED, List.copyOf(out), List.copyOf(problems), source, testData);
   }

   /** Lowercase id with spaces and dashes as underscores; null for null. */
   @Nullable
   public static String slug(@Nullable String raw) {
      if (raw == null) {
         return null;
      }
      String s = raw.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
      int colon = s.indexOf(':');
      // "viltrumitecore:thor" and "hero:thor" both mean "thor"
      return colon >= 0 && colon < s.length() - 1 ? s.substring(colon + 1) : s;
   }

   private static String orEmpty(@Nullable String s) {
      return s == null ? "" : s;
   }

   @Nullable
   private static JsonElement first(JsonObject o, String[] keys) {
      for (String k : keys) {
         JsonElement e = o.get(k);
         if (e != null && !e.isJsonNull()) {
            return e;
         }
      }
      return null;
   }

   @Nullable
   private static String string(JsonObject o, String[] keys) {
      JsonElement e = first(o, keys);
      return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
   }

   static LocalText text(JsonObject o, String[] keys) {
      return toText(first(o, keys));
   }

   static LocalText toText(@Nullable JsonElement e) {
      if (e == null || e.isJsonNull()) {
         return LocalText.EMPTY;
      }
      if (e.isJsonPrimitive()) {
         return LocalText.of(e.getAsString());
      }
      if (e.isJsonArray()) {
         List<String> parts = new ArrayList<>();
         for (JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonPrimitive()) {
               parts.add(x.getAsString());
            }
         }
         return LocalText.of(String.join(" ", parts));
      }
      JsonObject obj = e.getAsJsonObject();
      if (obj.has("key") && obj.get("key").isJsonPrimitive()) {
         return LocalText.of(obj.get("key").getAsString());
      }
      Map<String, String> map = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> m : obj.entrySet()) {
         if (m.getValue().isJsonPrimitive()) {
            map.put(m.getKey(), m.getValue().getAsString());
         }
      }
      return LocalText.ofLocales(map);
   }

   /** Conditions: one text, a list of texts, or an object of named conditions (each value a text). */
   static List<LocalText> texts(JsonObject o, String[] keys) {
      JsonElement e = first(o, keys);
      List<LocalText> out = new ArrayList<>();
      if (e == null || e.isJsonNull()) {
         return out;
      }
      if (e.isJsonArray()) {
         for (JsonElement x : e.getAsJsonArray()) {
            LocalText t = toText(x);
            if (!t.isEmpty()) {
               out.add(t);
            }
         }
      } else if (e.isJsonObject() && !looksLocalized(e.getAsJsonObject())) {
         for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
            LocalText t = toText(m.getValue());
            if (!t.isEmpty()) {
               out.add(t);
            }
         }
      } else {
         LocalText t = toText(e);
         if (!t.isEmpty()) {
            out.add(t);
         }
      }
      return out;
   }

   private static boolean looksLocalized(JsonObject o) {
      for (String k : o.keySet()) {
         String n = LocalText.normalizeLocale(k);
         if (n.matches("[a-z]{2,3}_[a-z]{2,3}") || k.equals("key")) {
            return true;
         }
      }
      return false;
   }

   private static List<CatalogInteraction.Participant> participants(JsonObject o) {
      List<CatalogInteraction.Participant> out = new ArrayList<>();
      JsonElement e = first(o, PARTICIPANTS);
      if (e == null) {
         return out;
      }
      JsonArray list;
      if (e.isJsonArray()) {
         list = e.getAsJsonArray();
      } else {
         list = new JsonArray();
         list.add(e);
      }
      for (JsonElement x : list) {
         if (x.isJsonPrimitive()) {
            String id = slug(x.getAsString());
            out.add(new CatalogInteraction.Participant(id, LocalText.EMPTY, ""));
         } else if (x.isJsonObject()) {
            JsonObject p = x.getAsJsonObject();
            String id = slug(string(p, new String[]{"id", "key", "entity", "hero", "npc", "event"}));
            if (id == null) {
               continue;
            }
            String kind = orEmpty(slug(string(p, new String[]{"kind", "type", "role", "category"})));
            out.add(new CatalogInteraction.Participant(id, text(p, NAME), kind));
         }
      }
      return out;
   }

   private static List<String> related(JsonObject o) {
      List<String> out = new ArrayList<>();
      for (String k : RELATED) {
         JsonElement e = o.get(k);
         if (e == null) {
            continue;
         }
         if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
               if (x.isJsonPrimitive()) {
                  out.add(slug(x.getAsString()));
               } else if (x.isJsonObject() && x.getAsJsonObject().has("id")) {
                  out.add(slug(x.getAsJsonObject().get("id").getAsString()));
               }
            }
         } else if (e.isJsonPrimitive()) {
            out.add(slug(e.getAsString()));
         }
      }
      return out;
   }

   static boolean secret(JsonObject o) {
      for (String k : new String[]{"secret", "hidden", "isSecret", "is_secret"}) {
         JsonElement e = o.get(k);
         if (e instanceof JsonPrimitive p) {
            return p.isBoolean() ? p.getAsBoolean() : "true".equalsIgnoreCase(p.getAsString());
         }
      }
      for (String k : new String[]{"visibility", "discovery", "reveal"}) {
         JsonElement e = o.get(k);
         if (e instanceof JsonPrimitive p) {
            String v = p.getAsString().toLowerCase(Locale.ROOT);
            return v.contains("secret") || v.contains("hidden") || v.contains("discover");
         }
      }
      return false;
   }
}

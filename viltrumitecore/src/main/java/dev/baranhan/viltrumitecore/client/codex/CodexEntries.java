package dev.baranhan.viltrumitecore.client.codex;

import dev.baranhan.viltrumitecore.client.codex.CodexEntry.Category;
import dev.baranhan.viltrumitecore.client.codex.CodexEntry.Visual;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Every article of the encyclopedia: the heroes (in Od Lagna's wish order, the human last), the NPCs and
 * bosses, and the world events. Pure data (unit-tested): ids, the lang sections each one has, the portrait,
 * accent colour, links between articles and the ids a contextual-interaction catalog may use for them.
 * Hero articles are keyed by {@code HeroId.key()}; nothing here branches on a concrete hero.
 */
public final class CodexEntries {
   static final List<String> HERO_SECTIONS = List.of("lore", CodexEntry.LIVE_ABILITIES, "gameplay", CodexEntry.LIVE_KEYS, "relations",
      CodexEntry.INTERACTIONS);
   static final List<String> NPC_SECTIONS = List.of("look", "lore", "abilities", "behavior", "relations", CodexEntry.INTERACTIONS);
   static final List<String> EVENT_SECTIONS = List.of("lore", "stages", "participants", "features", "relations", CodexEntry.INTERACTIONS);
   private static final String SKINS = "viltrumitecore:textures/entity/skins/";
   private static final Map<String, CodexEntry> ALL = new LinkedHashMap<>();

   static {
      // Heroes: accent = the colour of the hero's wish (WishStyle.primary), filled in by CodexScreen at runtime.
      hero("homelander", 0xFF2A2A, rel("conquest", "viltrumite_squad", "conquest_arrival", "viltrumite_squad_event"), "viltrumite", "omni_man");
      hero("regulus", 0xE8EEF5, rel("reinhard", "od_lagna"), "regulus_corneas");
      hero("ironman", 0x5FE6FF, rel("iron_legion", "avenger_iron_man", "captain"), "iron_man", "tony_stark", "tony", "hulkbuster");
      hero("reinhard", 0xFFF1B8, rel("regulus", "thanos"), "reinhard_van_astrea");
      hero("thor", 0x7FD8FF, rel("avenger_thor", "captain"), "thor_odinson");
      hero("dio", 0xFFD34D, rel("thanos_snap", "regulus"), "dio_brando", "the_world");
      hero("sinclair", 0xFF3B5C, rel("sinclair_assistant"), "magnificent_sinclair", "the_magnificent_sinclair");
      hero("jinwoo", 0x8A4DFF, rel("shadow_army"), "sung_jinwoo", "shadow_monarch");
      hero("captain", 0x3A78FF, rel("avenger_iron_man", "avenger_thor", "avenger_hulk", "avenger_black_widow", "avenger_hawkeye", "chitauri_invasion"),
         "captain_america", "steve_rogers", "cap");
      hero("red_hulk", 0xE0301E, rel("avenger_hulk", "captain"), "thunderbolt_ross", "rulk");
      hero("human", 0xFFE3A3, rel("citizen", "od_lagna"), "player", "mortal");

      npc("conquest", "boss", 0xD23A3A, Visual.skin(SKINS + "skin_conquest.png", false), rel("viltrumite_squad", "homelander", "conquest_arrival"));
      npc("viltrumite_squad", "enemy", 0xC85A4B,
         Visual.skins(List.of(SKINS + "skin_thula.png", SKINS + "skin_lucan.png", SKINS + "skin_vidor.png"), List.of(true, false, false)),
         rel("conquest", "homelander", "viltrumite_squad_event"), "thula", "lucan", "vidor", "viltrumites", "squad");
      npc("chitauri_soldier", "enemy", 0x6E8FA8, Visual.entity("viltrumitecore:chitauri_soldier", null),
         rel("chitauri_chariot", "chitauri_leviathan", "chitauri_invasion"), "chitauri", "chitauri_creature", "chitauri_rider", "chitauri_riders");
      npc("chitauri_chariot", "enemy", 0x5F7F99, Visual.entity("viltrumitecore:chitauri_chariot", null),
         rel("chitauri_soldier", "chitauri_leviathan", "chitauri_invasion"), "chariot");
      npc("chitauri_leviathan", "boss", 0x4C6A85, Visual.entity("viltrumitecore:chitauri_leviathan", null),
         rel("chitauri_soldier", "chitauri_chariot", "chitauri_invasion"), "leviathan");
      npc("thanos", "boss", 0x9B59D0, Visual.entity("viltrumitecore:thanos", null), rel("thanos_snap", "od_lagna", "reinhard", "dio"),
         "mad_titan", "infinity_gauntlet");
      avenger("avenger_iron_man", "iron_man", 0xE0413A, "avenger_thor", "mark_42");
      avenger("avenger_thor", "thor", 0x7FD8FF, "avenger_hulk", "thor_2012", "npc_thor");
      avenger("avenger_hulk", "hulk", 0x5FBF4A, "avenger_black_widow", "hulk", "bruce_banner", "npc_hulk");
      avenger("avenger_black_widow", "black_widow", 0xC9302C, "avenger_hawkeye", "black_widow", "natasha_romanoff");
      avenger("avenger_hawkeye", "hawkeye", 0x8C5AC8, "avenger_black_widow", "hawkeye", "clint_barton");
      npc("iron_legion", "ally", 0xD0A040, Visual.entity("viltrumitecore:legion_suit", null), rel("ironman"), "legion_suit", "legion", "iron_legion_suit", "iron_legion_suits", "legion_suits");
      npc("shadow_army", "ally", 0x8A4DFF, Visual.entity("viltrumitecore:shadow_soldier", null), rel("jinwoo"), "shadow_soldier", "shadows",
         "igris", "beru", "tank");
      npc("sinclair_assistant", "ally", 0xFF6A8A, Visual.entity("viltrumitecore:sinclair_assistant", null), rel("sinclair"), "assistant");
      npc("citizen", "neutral", 0x9FB2C8, Visual.entity("viltrumitecore:citizen", null), rel("human", "meteor_fall"), "citizens", "city");
      npc("od_lagna", "cosmic", 0xFFD98A, Visual.halo(), rel("human", "thanos_snap"), "od_lagna_wish", "wish");

      event("conquest_arrival", 0xD23A3A, Visual.skin(SKINS + "skin_conquest.png", false), rel("conquest", "homelander", "viltrumite_squad_event"),
         "conquest_event");
      event("viltrumite_squad_event", 0xC85A4B,
         Visual.skins(List.of(SKINS + "skin_lucan.png", SKINS + "skin_thula.png", SKINS + "skin_vidor.png"), List.of(false, true, false)),
         rel("viltrumite_squad", "conquest_arrival", "homelander"), "squad_event", "viltrumite_squad_arrival");
      event("meteor_fall", 0xFF8A3A, Visual.entity("viltrumitecore:meteor", null), rel("meteor_rain", "citizen"), "meteor", "meteorite");
      event("meteor_rain", 0xFF5A2A, Visual.entity("viltrumitecore:meteor", null), rel("meteor_fall"), "meteor_shower");
      event("chitauri_invasion", 0x4FB4D8, Visual.entity("viltrumitecore:chitauri_soldier", null),
         rel("chitauri_soldier", "chitauri_chariot", "chitauri_leviathan", "captain"), "invasion", "chitauri_portal");
      event("thanos_snap", 0xE0B040, Visual.entity("viltrumitecore:thanos", null), rel("thanos", "od_lagna", "dio"), "snap", "the_snap",
         "thanos_event");
   }

   private CodexEntries() {
   }

   private static List<String> rel(String... ids) {
      return List.of(ids);
   }

   private static void hero(String key, int accent, List<String> relations, String... aliases) {
      put(new CodexEntry(key, Category.HEROES, null, accent, Visual.hero(key), HERO_SECTIONS, relations, aliases(key, aliases)));
   }

   private static void npc(String id, String kind, int accent, Visual visual, List<String> relations, String... aliases) {
      put(new CodexEntry(id, Category.NPCS, kind, accent, visual, NPC_SECTIONS, relations, aliases(id, aliases)));
   }

   private static void avenger(String id, String variant, int accent, String partner, String... aliases) {
      npc(id, "ally", accent, Visual.entity("viltrumitecore:avenger", variant), rel("captain", partner, "chitauri_invasion"), aliases);
   }

   private static void event(String id, int accent, Visual visual, List<String> relations, String... aliases) {
      put(new CodexEntry(id, Category.EVENTS, "event", accent, visual, EVENT_SECTIONS, relations, aliases(id, aliases)));
   }

   private static Set<String> aliases(String id, String... more) {
      Set<String> out = new LinkedHashSet<>();
      out.add(id);
      Collections.addAll(out, more);
      return Collections.unmodifiableSet(out);
   }

   private static void put(CodexEntry entry) {
      if (ALL.put(entry.id(), entry) != null) {
         throw new IllegalStateException("Duplicate codex entry " + entry.id());
      }
   }

   public static List<CodexEntry> all() {
      return List.copyOf(ALL.values());
   }

   public static List<CodexEntry> of(Category category) {
      List<CodexEntry> out = new ArrayList<>();
      for (CodexEntry e : ALL.values()) {
         if (e.category() == category) {
            out.add(e);
         }
      }
      return out;
   }

   @Nullable
   public static CodexEntry get(@Nullable String id) {
      return id == null ? null : ALL.get(id);
   }

   /** Leading words that only qualify a free-text participant ("the summoned NPC Thor", "an Iron Legion suit"). */
   private static final Set<String> FILLERS = Set.of("a", "an", "the", "any", "summoned", "npc", "other", "hero");

   /**
    * The articles a free-text participant names. One article when the whole text is known ("Thor (Mjolnir in hand)");
    * otherwise the text is split on "," / " or " / " and " / "/" and each part is looked up, first as written, then
    * without leading qualifiers ("NPC Hulk" → hulk, "Jinwoo's shadows" → shadows); when nothing outside parentheses is
    * known, the list inside them is used. Unknown parts are skipped; empty when nothing is known.
    */
   public static List<CodexEntry> resolveAll(@Nullable String raw) {
      CodexEntry whole = byAlias(raw);
      if (whole != null || raw == null) {
         return whole != null ? List.of(whole) : List.of();
      }
      List<CodexEntry> out = parts(raw.replaceAll("\\([^)]*\\)", " "));
      if (out.isEmpty()) {
         // "Hero summons (Iron Legion suits, Jinwoo's shadows)": the list in parentheses names them
         java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([^)]*)\\)").matcher(raw);
         while (m.find()) {
            for (CodexEntry e : parts(m.group(1))) {
               if (!out.contains(e)) {
                  out.add(e);
               }
            }
         }
      }
      return out;
   }

   private static List<CodexEntry> parts(String text) {
      List<CodexEntry> out = new ArrayList<>();
      for (String part : text.split("(?i),|/|\\s+or\\s+|\\s+and\\s+")) {
         List<String> words = new ArrayList<>(List.of(part.strip().split("\\s+")));
         CodexEntry found = null;
         while (!words.isEmpty() && found == null) {
            found = byAlias(String.join(" ", words));
            String first = words.get(0).toLowerCase(java.util.Locale.ROOT);
            if (found == null && (FILLERS.contains(first) || first.endsWith("'s"))) {
               words.remove(0);
            } else if (found == null) {
               break;
            }
         }
         if (found != null && !out.contains(found)) {
            out.add(found);
         }
      }
      return out;
   }

   /** The article a catalog id (participant, related) points to: by id, then by alias; null when unknown. */
   @Nullable
   public static CodexEntry byAlias(@Nullable String raw) {
      String id = dev.baranhan.viltrumitecore.codex.catalog.CatalogParser.slug(raw);
      if (id == null || id.isEmpty()) {
         return null;
      }
      CodexEntry direct = ALL.get(id);
      if (direct != null) {
         return direct;
      }
      for (CodexEntry e : ALL.values()) {
         if (e.aliases().contains(id)) {
            return e;
         }
      }
      return null;
   }
}

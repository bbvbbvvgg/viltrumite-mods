package dev.baranhan.viltrumitecore.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.integration.ShippedLang;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * docs/contextual-interactions.json is the player- and designer-facing catalog of every contextual interaction. It
 * must list exactly the registered built-ins (same ids, order, trigger, priority, timings), every entry must carry
 * all fields in both languages, and every code reference must point at a real file and method.
 */
class ContextualInteractionCatalogTest {
   static final Path REPO = Path.of("..");
   static final Path CATALOG = REPO.resolve("docs/contextual-interactions.json");
   static final List<String> TEXT_FIELDS = List.of("id", "title_ru", "title_en", "trigger", "gameplay_result", "visual_fx", "sound", "description_ru",
      "description_en", "lore_ru", "lore_en", "discovery_hint_ru", "discovery_hint_en");
   static final List<String> LIST_FIELDS = List.of("participants", "conditions", "code_refs");

   static JsonArray entries() throws IOException {
      return JsonParser.parseString(Files.readString(CATALOG)).getAsJsonObject().getAsJsonArray("interactions");
   }

   @Test
   void catalogListsExactlyTheRegisteredInteractions() throws IOException {
      List<String> catalog = new ArrayList<>();
      entries().forEach(e -> catalog.add(e.getAsJsonObject().get("id").getAsString()));
      List<String> registry = ContextualInteractions.builtins().stream().map(i -> i.spec().id()).toList();
      assertEquals(registry, catalog, "docs/contextual-interactions.json must list ContextualInteractions.builtins() in order");
      assertEquals(catalog.size(), new HashSet<>(catalog).size(), "duplicate ids");
   }

   @Test
   void catalogTimingsMatchTheSpecs() throws IOException {
      Map<String, InteractionSpec> specs = new java.util.HashMap<>();
      ContextualInteractions.builtins().forEach(i -> specs.put(i.spec().id(), i.spec()));
      for (JsonElement e : entries()) {
         JsonObject o = e.getAsJsonObject();
         InteractionSpec spec = specs.get(o.get("id").getAsString());
         assertEquals(spec.trigger().name(), o.get("trigger").getAsString(), spec.id());
         assertEquals(spec.priority(), o.get("priority").getAsInt(), spec.id());
         assertEquals(spec.cooldownTicks(), o.get("cooldown_ticks").getAsInt(), spec.id());
         assertEquals(spec.aftermathTicks(), o.get("aftermath_ticks").getAsInt(), spec.id());
      }
   }

   @Test
   void everyEntryCarriesEveryField() throws IOException {
      for (JsonElement e : entries()) {
         JsonObject o = e.getAsJsonObject();
         String id = o.get("id").getAsString();
         for (String field : TEXT_FIELDS) {
            assertTrue(o.has(field) && o.get(field).isJsonPrimitive() && !o.get(field).getAsString().isBlank(), id + " lacks " + field);
         }
         for (String field : LIST_FIELDS) {
            assertTrue(o.has(field) && o.get(field).isJsonArray() && o.getAsJsonArray(field).size() > 0, id + " lacks " + field);
            o.getAsJsonArray(field).forEach(v -> assertFalse(v.getAsString().isBlank(), id + " has a blank " + field));
         }
         assertTrue(o.get("description_ru").getAsString().codePoints().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC),
            id + ": description_ru is not Russian");
      }
   }

   @Test
   void codeRefsPointAtRealMethods() throws IOException {
      for (JsonElement e : entries()) {
         JsonObject o = e.getAsJsonObject();
         for (JsonElement r : o.getAsJsonArray("code_refs")) {
            String ref = r.getAsString();
            int hash = ref.indexOf('#');
            assertTrue(hash > 0, "ref must be path#method: " + ref);
            Path file = REPO.resolve(ref.substring(0, hash));
            assertTrue(Files.isRegularFile(file), "missing file " + ref);
            String method = ref.substring(hash + 1);
            assertTrue(java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(method) + "\\s*\\(").matcher(Files.readString(file)).find(),
               "no method " + ref);
         }
      }
   }

   @Test
   void everySoundIsShippedWithSubtitlesInEveryLocale() throws IOException {
      JsonObject sounds = JsonParser.parseString(Files.readString(Path.of("src/main/sound-overlays/interactions.json"))).getAsJsonObject();
      Set<String> events = new HashSet<>(List.of("interaction.shield_shatter", "interaction.storm_shield", "interaction.meteor_burst",
         "interaction.meteor_split", "interaction.overcharge", "interaction.divine_clash", "interaction.titan_fling", "interaction.storm_rebound",
         "interaction.vibranium_ring", "interaction.repulsor_split", "interaction.time_rewind", "interaction.lion_strain", "interaction.shield_crush",
         "interaction.meteor_cleave", "interaction.meteor_hurl", "interaction.shadow_exchange", "interaction.leviathan_impact",
         "interaction.legion_discharge", "interaction.spine_lightning", "interaction.leviathan_halt"));
      assertEquals(events, sounds.keySet());
      Map<String, JsonObject> locales = ShippedLang.merged();
      for (String event : events) {
         JsonObject def = sounds.getAsJsonObject(event);
         String file = def.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString().replace("viltrumitecore:", "");
         assertTrue(Files.isRegularFile(Path.of("src/main/binassets/assets/viltrumitecore/sounds/" + file + ".ogg.b64")), "missing ogg for " + event);
         String subtitle = def.get("subtitle").getAsString();
         for (Map.Entry<String, JsonObject> locale : locales.entrySet()) {
            assertTrue(locale.getValue().has(subtitle), locale.getKey() + " lacks " + subtitle);
         }
      }
      for (String key : List.of("subtitle.viltrumitecore.captain.shield_shattered", "subtitle.viltrumitecore.captain.storm_shield",
         "subtitle.viltrumitecore.captain.all_day")) {
         for (Map.Entry<String, JsonObject> locale : locales.entrySet()) {
            assertTrue(locale.getValue().has(key), locale.getKey() + " lacks " + key);
         }
      }
   }

   /** The catalog's sound and FX columns name only shipped events and registered presets (the other agent reads them). */
   @Test
   void catalogNamesOnlyShippedSoundsAndPresets() throws IOException {
      JsonObject sounds = JsonParser.parseString(Files.readString(Path.of("src/main/sound-overlays/interactions.json"))).getAsJsonObject();
      Set<String> presets = new HashSet<>();
      for (dev.baranhan.viltrumitecore.hero.fx.FxPreset preset : InteractionFx.PRESETS) {
         presets.add(preset.id());
      }
      java.util.regex.Pattern soundRef = java.util.regex.Pattern.compile("viltrumitecore:(interaction\\.[a-z_]+)");
      java.util.regex.Pattern fxRef = java.util.regex.Pattern.compile("viltrumitecore:interaction_[a-z_]+");
      Set<String> usedSounds = new HashSet<>();
      for (com.google.gson.JsonElement e : catalogJson().getAsJsonArray("interactions")) {
         JsonObject o = e.getAsJsonObject();
         String id = o.get("id").getAsString();
         java.util.regex.Matcher m = soundRef.matcher(o.get("sound").getAsString());
         while (m.find()) {
            assertTrue(sounds.has(m.group(1)), id + " names unknown sound " + m.group(1));
            usedSounds.add(m.group(1));
         }
         java.util.regex.Matcher f = fxRef.matcher(o.get("visual_fx").getAsString());
         boolean any = false;
         while (f.find()) {
            any = true;
            assertTrue(presets.contains(f.group()), id + " names unknown FX preset " + f.group());
         }
         assertTrue(any, id + " names no FX preset");
      }
      assertEquals(sounds.keySet(), usedSounds, "every shipped interaction sound is used by a catalog entry");
   }

   private static JsonObject catalogJson() throws IOException {
      return JsonParser.parseString(Files.readString(CATALOG)).getAsJsonObject();
   }
}

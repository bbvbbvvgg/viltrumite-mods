package dev.baranhan.viltrumitecore.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The locale files the jar ships. processResources merges every
 * {@code src/main/lang-overlays/<part>/<locale>.json} into
 * {@code assets/viltrumitecore/lang/<locale>.json} (parts in path order), so a test
 * that checks shipped strings must read both places, not only {@code resources/}.
 */
public final class ShippedLang {
   public static final Path RESOURCES = Path.of("src/main/resources/assets/viltrumitecore/lang");
   public static final Path OVERLAYS = Path.of("src/main/lang-overlays");

   private ShippedLang() {
   }

   /** Every source file of every locale: the resources file first, then its overlay parts in path order. */
   public static List<Path> sourceFiles() throws IOException {
      List<Path> files = new ArrayList<>(jsonFiles(RESOURCES));
      if (Files.isDirectory(OVERLAYS)) {
         for (Path part : sorted(OVERLAYS)) {
            if (Files.isDirectory(part)) {
               files.addAll(jsonFiles(part));
            }
         }
      }

      return files;
   }

   /** Locale file name (e.g. {@code ru_ru.json}) to its merged strings, as the jar ships them. */
   public static Map<String, JsonObject> merged() throws IOException {
      Map<String, JsonObject> locales = new TreeMap<>();
      for (Path file : sourceFiles()) {
         JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
         JsonObject target = locales.get(file.getFileName().toString());
         if (target == null) {
            if (!file.startsWith(RESOURCES)) {
               throw new IllegalStateException("lang overlay " + file + " has no base locale file");
            }

            locales.put(file.getFileName().toString(), json);
         } else {
            json.entrySet().forEach(entry -> target.add(entry.getKey(), entry.getValue()));
         }
      }

      return locales;
   }

   private static List<Path> jsonFiles(Path dir) throws IOException {
      return sorted(dir).stream().filter(path -> path.toString().endsWith(".json")).toList();
   }

   private static List<Path> sorted(Path dir) throws IOException {
      try (Stream<Path> stream = Files.list(dir)) {
         return stream.sorted().toList();
      }
   }
}

package dev.baranhan.viltrumitecore.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The sounds.json the jar ships. processResources merges every
 * {@code src/main/sound-overlays/<part>/sounds.json} (may replace whole base events) and every
 * {@code src/main/sound-overlays/<part>.json} (adds events only) into
 * {@code assets/viltrumitecore/sounds.json} (whole events, files in path order): the
 * base file is past the 64 KB GitHub MCP request cap, so new or reworked events live
 * in an overlay. A test that checks shipped events must read both places.
 */
public final class ShippedSounds {
   public static final Path BASE = Path.of("src/main/resources/assets/viltrumitecore/sounds.json");
   public static final Path OVERLAYS = Path.of("src/main/sound-overlays");

   private ShippedSounds() {
   }

   /** Event name to its definition, as the jar ships it. */
   public static JsonObject merged() throws IOException {
      return merged(BASE);
   }

   /** Merged view of {@code file}; only the viltrumitecore sounds.json has overlays. */
   public static JsonObject merged(Path file) throws IOException {
      JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
      if (!file.toAbsolutePath().normalize().equals(BASE.toAbsolutePath().normalize()) || !Files.isDirectory(OVERLAYS)) {
         return json;
      }

      List<Path> parts;
      try (Stream<Path> listing = Files.list(OVERLAYS)) {
         parts = listing.map(part -> Files.isDirectory(part) ? part.resolve("sounds.json") : part)
            .filter(part -> Files.isRegularFile(part) && part.getFileName().toString().endsWith(".json")).sorted().toList();
      }

      for (Path part : parts) {
         JsonParser.parseString(Files.readString(part)).getAsJsonObject().entrySet()
            .forEach(entry -> json.add(entry.getKey(), entry.getValue()));
      }

      return json;
   }
}

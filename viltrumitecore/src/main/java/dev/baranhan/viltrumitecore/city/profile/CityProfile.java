package dev.baranhan.viltrumitecore.city.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The prepared city of a world (pure data). Shipped inside the prepared world folder as
 * {@code viltrumite_city/profile.json} next to the pedestrian tiles in
 * {@code viltrumite_city/walk/}. A world without that folder simply has no city: every
 * /city command explains it and nothing spawns.
 */
public final class CityProfile {
   public static final String FOLDER = "viltrumite_city";
   public static final String FILE = "profile.json";
   public static final int FORMAT = 1;

   public record District(String name, int minX, int minZ, int maxX, int maxZ, int walkCells) {
      public boolean contains(double x, double z) {
         return x >= this.minX && x <= this.maxX + 1 && z >= this.minZ && z <= this.maxZ + 1;
      }

      public double distanceSq(double x, double z) {
         double dx = x < this.minX ? this.minX - x : x > this.maxX ? x - this.maxX : 0.0;
         double dz = z < this.minZ ? this.minZ - z : z > this.maxZ ? z - this.maxZ : 0.0;
         return dx * dx + dz * dz;
      }
   }

   public final String id;
   public final String name;
   public final String mapVersion;
   public final String dimension;
   /** Arena box: the journal and /city reset never touch blocks outside it. */
   public final int minX;
   public final int minY;
   public final int minZ;
   public final int maxX;
   public final int maxY;
   public final int maxZ;
   public final List<District> districts;
   public final double defaultDensity;
   /** Citizens around each player at density 1. */
   public final int perPlayer;
   /** Hard cap on citizens in the level. */
   public final int maxTotal;
   public final long walkCells;
   public final long preferredCells;

   public CityProfile(String id, String name, String mapVersion, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
      List<District> districts, double defaultDensity, int perPlayer, int maxTotal, long walkCells, long preferredCells) {
      this.id = id;
      this.name = name;
      this.mapVersion = mapVersion;
      this.dimension = dimension;
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
      this.maxX = maxX;
      this.maxY = maxY;
      this.maxZ = maxZ;
      this.districts = Collections.unmodifiableList(new ArrayList<>(districts));
      this.defaultDensity = defaultDensity;
      this.perPlayer = perPlayer;
      this.maxTotal = maxTotal;
      this.walkCells = walkCells;
      this.preferredCells = preferredCells;
   }

   public boolean inArena(int x, int y, int z) {
      return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ && y >= this.minY && y <= this.maxY;
   }

   public boolean chunkInArena(int cx, int cz) {
      return (cx << 4) + 15 >= this.minX && cx << 4 <= this.maxX && (cz << 4) + 15 >= this.minZ && cz << 4 <= this.maxZ;
   }

   /** The district at a position, else the nearest one within 64 blocks, else null. */
   @Nullable
   public District districtAt(double x, double z) {
      District best = null;
      double bestD = 64.0 * 64.0;
      for (District d : this.districts) {
         if (d.contains(x, z)) {
            return d;
         }

         double ds = d.distanceSq(x, z);
         if (ds < bestD) {
            bestD = ds;
            best = d;
         }
      }

      return best;
   }

   @Nullable
   public static CityProfile load(Path worldRoot) throws IOException {
      Path file = worldRoot.resolve(FOLDER).resolve(FILE);
      if (!Files.isRegularFile(file)) {
         return null;
      }

      try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         return parse(JsonParser.parseReader(r).getAsJsonObject());
      } catch (RuntimeException e) {
         throw new IOException("bad city profile " + file + ": " + e.getMessage(), e);
      }
   }

   public static CityProfile parse(JsonObject o) {
      int format = o.has("format") ? o.get("format").getAsInt() : 1;
      if (format > FORMAT) {
         throw new IllegalArgumentException("city profile format " + format + " is newer than this mod (" + FORMAT + ")");
      }

      JsonObject a = o.getAsJsonObject("arena");
      List<District> districts = new ArrayList<>();
      JsonArray ds = o.has("districts") ? o.getAsJsonArray("districts") : new JsonArray();
      for (JsonElement e : ds) {
         JsonObject d = e.getAsJsonObject();
         districts.add(new District(d.get("name").getAsString(), d.get("minX").getAsInt(), d.get("minZ").getAsInt(), d.get("maxX").getAsInt(),
            d.get("maxZ").getAsInt(), d.has("walkCells") ? d.get("walkCells").getAsInt() : 0));
      }

      JsonObject pop = o.has("population") ? o.getAsJsonObject("population") : new JsonObject();
      JsonObject st = o.has("stats") ? o.getAsJsonObject("stats") : new JsonObject();
      return new CityProfile(
         o.get("id").getAsString(),
         o.has("name") ? o.get("name").getAsString() : o.get("id").getAsString(),
         o.has("mapVersion") ? o.get("mapVersion").getAsString() : "?",
         o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld",
         a.get("minX").getAsInt(), a.has("minY") ? a.get("minY").getAsInt() : -64, a.get("minZ").getAsInt(),
         a.get("maxX").getAsInt(), a.has("maxY") ? a.get("maxY").getAsInt() : 1023, a.get("maxZ").getAsInt(),
         districts,
         pop.has("density") ? pop.get("density").getAsDouble() : 1.0,
         pop.has("perPlayer") ? pop.get("perPlayer").getAsInt() : 40,
         pop.has("maxTotal") ? pop.get("maxTotal").getAsInt() : 160,
         st.has("walkCells") ? st.get("walkCells").getAsLong() : 0L,
         st.has("preferredCells") ? st.get("preferredCells").getAsLong() : 0L);
   }
}

package dev.baranhan.viltrumitecore.client.cinematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * One JSON cinematic scene (wave1-22, task T4): how long the shot lasts in ticks, its keyframed
 * path, whether it letterboxes the screen and how far its clock is slowed. Parsing is pure (Gson and
 * {@link Vec3} only), clamps every number and returns {@code null} for a document with no usable
 * path, so a broken file can never crash the client: the loader logs it and the camera falls back to
 * the built-in orbit.
 */
public record CinematicScene(int ticks, CameraPath path, boolean letterbox, float slowmo) {
   public static final int MIN_TICKS = 5;
   public static final int MAX_TICKS = 1200;
   public static final int MIN_KEYS = 2;
   public static final int MAX_KEYS = 16;
   public static final int DEFAULT_TICKS = 60;
   public static final float MIN_FOV = 20.0F;
   public static final float MAX_FOV = 150.0F;
   public static final float MIN_SLOWMO = 0.15F;
   public static final double MAX_OFFSET = 64.0;
   /** Reduced camera motion: a slower clock and a smaller amplitude. */
   public static final float REDUCED_AMPLITUDE = 0.65F;
   public static final float REDUCED_CLOCK = 0.6F;

   /** Scene-clock ticks per client tick: the slow-mo factor, further eased by reduced motion. */
   public float clockRate(boolean reduced) {
      return Math.max(0.05F, this.slowmo * (reduced ? REDUCED_CLOCK : 1.0F));
   }

   /** How many client ticks the whole shot runs for. */
   public int duration(boolean reduced) {
      return Math.max(1, Math.round(this.ticks / this.clockRate(reduced)));
   }

   /** Position multiplier of the path under reduced camera motion. */
   public float amplitude(boolean reduced) {
      return reduced ? REDUCED_AMPLITUDE : 1.0F;
   }

   /** Parses a scene document; {@code null} when the text is not a scene with a usable path. */
   @Nullable
   public static CinematicScene parse(@Nullable String json) {
      if (json == null) {
         return null;
      }
      try {
         return parse(JsonParser.parseString(json));
      } catch (RuntimeException error) {
         return null;
      }
   }

   /** Parses a scene document; {@code null} when it is not an object with a usable path. */
   @Nullable
   public static CinematicScene parse(@Nullable JsonElement root) {
      if (root == null || !root.isJsonObject()) {
         return null;
      }
      try {
         return parse(root.getAsJsonObject());
      } catch (RuntimeException error) {
         return null;
      }
   }

   /** Parses a scene document; {@code null} when there is no usable path. Every number is clamped. */
   @Nullable
   public static CinematicScene parse(@Nullable JsonObject json) {
      if (json == null) {
         return null;
      }
      JsonElement raw = json.get("keys");
      if (raw == null || !raw.isJsonArray()) {
         return null;
      }
      List<CameraPath.Key> keys = new ArrayList<>();
      for (JsonElement element : raw.getAsJsonArray()) {
         if (keys.size() >= MAX_KEYS) {
            break;
         }
         if (element != null && element.isJsonObject()) {
            keys.add(key(element.getAsJsonObject()));
         }
      }
      if (keys.size() < MIN_KEYS) {
         return null;
      }
      int ticks = clamp(intOf(json, "ticks", DEFAULT_TICKS), MIN_TICKS, MAX_TICKS);
      boolean letterbox = !json.has("letterbox") || boolOf(json, "letterbox", true);
      float slowmo = clamp(floatOf(json, "slowmo", 1.0F), MIN_SLOWMO, 1.0F);
      return new CinematicScene(ticks, new CameraPath(keys), letterbox, slowmo);
   }

   private static CameraPath.Key key(JsonObject json) {
      // A frame may leave the lens to the player (0 = no fov of its own). A named fov, though, is a
      // real lens: anything under MIN_FOV renders as a whole-screen fisheye, so the floor is MIN_FOV.
      float fov = floatOf(json, "fov", 0.0F);
      return new CameraPath.Key(vector(json.get("offset"), Vec3.ZERO), vector(json.get("look"), CameraPath.DEFAULT_LOOK),
         fov > 0.0F ? clamp(fov, MIN_FOV, MAX_FOV) : 0.0F);
   }

   private static Vec3 vector(@Nullable JsonElement element, Vec3 fallback) {
      if (element == null || !element.isJsonArray()) {
         return fallback;
      }
      JsonArray array = element.getAsJsonArray();
      if (array.size() < 3) {
         return fallback;
      }
      return new Vec3(clamp(number(array.get(0), 0.0), -MAX_OFFSET, MAX_OFFSET), clamp(number(array.get(1), 0.0), -MAX_OFFSET, MAX_OFFSET),
         clamp(number(array.get(2), 0.0), -MAX_OFFSET, MAX_OFFSET));
   }

   private static int intOf(JsonObject json, String name, int fallback) {
      return (int)number(json.get(name), fallback);
   }

   private static float floatOf(JsonObject json, String name, float fallback) {
      return (float)number(json.get(name), fallback);
   }

   private static boolean boolOf(JsonObject json, String name, boolean fallback) {
      try {
         return json.get(name).getAsBoolean();
      } catch (RuntimeException error) {
         return fallback;
      }
   }

   /**
    * A finite number of the document, or the field's default. Gson happily turns the strings "NaN",
    * "Infinity" and "-Infinity" into those doubles and no clamp catches NaN, so a non-finite value is
    * treated as a wrong type: the field keeps its default.
    */
   private static double number(@Nullable JsonElement element, double fallback) {
      try {
         double value = element == null ? fallback : element.getAsDouble();
         return Double.isFinite(value) ? value : fallback;
      } catch (RuntimeException error) {
         return fallback;
      }
   }

   private static int clamp(int value, int min, int max) {
      return value < min ? min : (value > max ? max : value);
   }

   private static float clamp(float value, float min, float max) {
      return value < min ? min : (value > max ? max : value);
   }

   private static double clamp(double value, double min, double max) {
      return value < min ? min : (value > max ? max : value);
   }
}

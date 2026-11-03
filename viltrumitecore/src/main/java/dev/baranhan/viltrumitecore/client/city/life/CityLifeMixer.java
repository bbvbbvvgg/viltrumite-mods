package dev.baranhan.viltrumitecore.client.city.life;

import net.minecraft.util.Mth;

/**
 * Pure numbers of the street soundscape (issue #136, unit tested): kinds of citizen voices and how many of
 * each may sound at once, occlusion, distance curves, the loudness of every bed from what the listener has
 * around him (calm and panicking people, height over the street, indoors, time of day, weather) and the
 * slow memory of what happened ("trauma"), which quiets the street and lets it come back step by step.
 */
public final class CityLifeMixer {
   // voice kinds (sounds the server plays at citizens)
   public static final int TALK = 0;
   public static final int CALL = 1;
   public static final int SCREAM = 2;
   public static final int QUIET = 3;
   public static final int KINDS = 4;
   /** Voices of a kind that may sound together; the nearest win. */
   private static final int[] CAP = {3, 3, 3, 2};
   /** The same line (any voice) is not doubled within this many ticks... */
   public static final int DUPLICATE_TICKS = 30;
   /** ...closer than this (blocks) to the one already playing. */
   public static final double DUPLICATE_RANGE = 24.0;

   public static final float OPEN = 1.0F;
   public static final float THIN = 0.45F;
   public static final float THICK = 0.25F;
   public static final double THIN_WALL = 1.6;

   /** Ticks for the trauma memory of the client to halve (two minutes). */
   public static final double TRAUMA_HALF_LIFE = 2400.0;

   private CityLifeMixer() {
   }

   public static int cap(int kind) {
      return CAP[kind];
   }

   /**
    * Pure: kind of a city voice event path ({@code city_life.voice.<v>.<line>}, {@code city_life.scream.*},
    * {@code city_life.cough}, {@code city_life.sob.*}); -1 for anything else (beds, details).
    */
   public static int kindOf(String path) {
      if (!path.startsWith("city_life.")) {
         return -1;
      }

      if (path.startsWith("city_life.voice.")) {
         String line = lineOf(path);
         return line.startsWith("startle_") || line.startsWith("flee_") || line.startsWith("help_") || line.startsWith("survivor_") ? CALL : TALK;
      }

      if (path.startsWith("city_life.scream.")) {
         return SCREAM;
      }

      return path.equals("city_life.cough") || path.startsWith("city_life.sob.") ? QUIET : -1;
   }

   /** Pure: the line id of a voice path (the part after the voice), the path itself for other sounds. */
   public static String lineOf(String path) {
      if (!path.startsWith("city_life.voice.")) {
         return path;
      }

      int dot = path.indexOf('.', "city_life.voice.".length());
      return dot < 0 ? path : path.substring(dot + 1);
   }

   /** Pure: volume factor of a voice through a blocked or open line, by the solid thickness on it. */
   public static float occlusion(boolean blocked, double thickness) {
      if (!blocked) {
         return OPEN;
      }

      return thickness <= THIN_WALL ? THIN : THICK;
   }

   /** Pure: linear distance gain like the engine's own (1 at the source, 0 at {@code range}). */
   public static float linear(double distance, double range) {
      return range <= 0.0 ? 0.0F : (float)Mth.clamp(1.0 - distance / range, 0.0, 1.0);
   }

   /** Pure: a soft weight of a citizen at a distance for the beds (smooth, 0 past {@code range}). */
   public static float weight(double distance, double range) {
      if (distance >= range) {
         return 0.0F;
      }

      float t = (float)(1.0 - Math.max(0.0, distance) / range);
      return t * t * (3.0F - 2.0F * t);
   }

   /** Pure: soft saturation of a weighted count: {@code full} weighted people make 1. */
   public static float amount(float weighted, float full) {
      float t = Mth.clamp(weighted / full, 0.0F, 1.0F);
      return t * (2.0F - t);
   }

   /** Pure: daylight 0..1 of a day time (0 = 6:00): full by day, fading at dusk (17:30-19:30) and dawn (4:30-6:00). */
   public static float daylight(long dayTime) {
      int t = (int)Math.floorMod(dayTime, 24000L);
      if (t < 11500) {
         return 1.0F;
      }

      if (t < 13500) {
         return 1.0F - (t - 11500) / 2000.0F;
      }

      if (t < 22500) {
         return 0.0F;
      }

      return (t - 22500) / 1500.0F;
   }

   /** Pure: crowd and steps fade when the listener is high over the street (blocks above the people). */
   public static float heightFade(double above, double start, double span) {
      return (float)Mth.clamp(1.0 - (above - start) / span, 0.0, 1.0);
   }

   /** Pure: wind between the buildings: a little in the street, more up high and in ruins, less indoors. */
   public static float wind(double above, float ruin, boolean indoor) {
      float w = 0.12F + (float)Mth.clamp((above - 6.0) / 50.0, 0.0, 1.0) * 0.6F + ruin * 0.25F;
      return Math.min(0.9F, indoor ? w * 0.35F : w);
   }

   /** Pure: trauma after {@code ticks} of decay, raised to what the street shows now (panic, a ruin nearby). */
   public static float trauma(float current, int ticks, float now) {
      float decayed = current * (float)Math.pow(0.5, ticks / TRAUMA_HALF_LIFE);
      return Math.max(decayed, Mth.clamp(now, 0.0F, 1.0F));
   }

   /**
    * Pure: bed targets [crowd, crowdFar, steps, running, streetDay, streetNight, wind, panic, hush] from the
    * scene: calm/talking weight, far calm weight, walking weight, running weight, panicking weight, trauma,
    * daylight, rain, indoors, height over the people, ruin weight.
    */
   public static float[] beds(float calm, float far, float walking, float running, float panic, float trauma, float daylight, boolean rain,
      boolean indoor, double above, float ruin) {
      float in = indoor ? 0.3F : 1.0F;
      float wet = rain ? 0.7F : 1.0F;
      float hush = 1.0F - 0.85F * trauma;
      float p = amount(panic, 3.0F);
      float crowd = amount(calm, 6.0F) * hush * wet * in * heightFade(above, 8.0, 40.0) * (1.0F - 0.6F * p);
      float crowdFar = amount(far, 18.0F) * hush * wet * in * heightFade(above, 16.0, 60.0) * (1.0F - 0.5F * crowd) * (1.0F - 0.7F * p);
      float steps = amount(walking, 4.0F) * in * heightFade(above, 3.0, 14.0) * (1.0F - 0.5F * p);
      float run = amount(running, 3.0F) * in * heightFade(above, 4.0, 20.0);
      float street = (1.0F - 0.6F * trauma) * (1.0F - 0.5F * p) * (rain ? 0.6F : 1.0F) * in;
      float day = 0.55F * daylight * street;
      float night = 0.5F * (1.0F - daylight) * street;
      float w = wind(above, ruin, indoor);
      float panicBed = p * (indoor ? 0.45F : 1.0F) * heightFade(above, 10.0, 50.0);
      float hushBed = trauma > 0.15F ? Mth.clamp((trauma - 0.15F) / 0.5F, 0.0F, 1.0F) * 0.6F * (1.0F - p) * (indoor ? 0.5F : 1.0F) : 0.0F;
      return new float[]{crowd, crowdFar, steps, run, day, night, w, panicBed, hushBed};
   }
}
